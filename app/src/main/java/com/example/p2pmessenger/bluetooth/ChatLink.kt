package com.example.p2pmessenger.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.p2pmessenger.R
import com.example.p2pmessenger.settings.AppTexts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.UUID

/**
 * Очень простой P2P-канал поверх Bluetooth Classic (RFCOMM).
 *
 * Каждое устройство одновременно слушает входящие подключения и может само
 * подключиться к соседу. Один сокет = один собеседник = один чат.
 * Сообщения передаются строками, разделёнными '\n'.
 */
class ChatLink(private val context: Context) {

    companion object {
        // Один и тот же UUID обязателен на обоих устройствах.
        private val SERVICE_UUID: UUID = UUID.fromString("a7c4e910-3b2f-4d68-9f31-6c0e8b5a2d74")
        private const val SERVICE_NAME = "P2P Messenger"
        private const val MAX_DISCOVERABLE_SECONDS = 300

        // Строковые литералы: поля Manifest.permission.* доступны только с API 31,
        // а проверять их приходится и на более старых устройствах.
        private const val P_CONNECT = "android.permission.BLUETOOTH_CONNECT"
        private const val P_SCAN = "android.permission.BLUETOOTH_SCAN"
        private const val P_ADVERTISE = "android.permission.BLUETOOTH_ADVERTISE"
    }

    enum class Status { Idle, Listening, Connecting, Connected }

    data class State(
        val status: Status = Status.Idle,
        val peerName: String? = null,
        val hint: String? = null,
    )

    sealed interface Event {
        data class Incoming(val text: String) : Event
        data class Problem(val text: String) : Event
        data object Disconnected : Event
    }

    data class NearbyDevice(val address: String, val name: String)

    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    private val linkJob = SupervisorJob()
    private val scope = CoroutineScope(linkJob + Dispatchers.IO)
    private val socketMutex = Mutex()
    private val writeMutex = Mutex()

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 16)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private val _devices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    val devices: StateFlow<List<NearbyDevice>> = _devices.asStateFlow()

    private var serverSocket: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null
    private var listenJob: Job? = null
    private var readJob: Job? = null
    private var connectJob: Job? = null
    private var discoveryReceiver: BroadcastReceiver? = null

    /** Разрешения проверяются перед каждым вызовом, без кеширования. */
    private fun granted(permission: String): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ||
            (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)

    private fun hasConnect() = granted(P_CONNECT)
    private fun hasScan() = granted(P_SCAN)
    private fun hasAdvertise() = granted(P_ADVERTISE)

    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(P_CONNECT, P_SCAN, P_ADVERTISE)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun hasAllPermissions(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> hasConnect() && hasScan() && hasAdvertise()
        else -> ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isBluetoothAvailable(): Boolean = try {
        adapter?.isEnabled == true
    } catch (_: SecurityException) {
        false
    }

    /** Запускаем сервер и обновляем список спаренных устройств. */
    @Suppress("MissingPermission")
    fun start() {
        if (adapter == null) {
            emitProblem(R.string.err_no_bluetooth)
            return
        }
        if (!isBluetoothAvailable()) {
            emitProblem(R.string.err_enable_bluetooth)
            return
        }
        if (!hasAllPermissions()) {
            emitProblem(R.string.err_permission)
            return
        }

        refreshDevices()
        if (_state.value.status == Status.Connected) return
        startListening()
    }

    @Suppress("MissingPermission")
    fun refreshDevices() {
        if (!hasConnect()) return
        val bonded = try {
            adapter?.bondedDevices.orEmpty()
        } catch (_: SecurityException) {
            emitProblem(R.string.err_bonded_list)
            return
        }
        _devices.value = bonded.map { device ->
            NearbyDevice(
                address = device.address,
                name = safeDeviceName(device) ?: AppTexts.get(R.string.device_unknown),
            )
        }.sortedBy { it.name.lowercase() }
    }

    /** Системный диалог «Сделать устройство видимым». */
    fun makeDiscoverable(launcher: (Intent) -> Unit) {
        if (!hasAdvertise()) {
            emitProblem(R.string.err_visibility)
            return
        }
        @Suppress("MissingPermission")
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, MAX_DISCOVERABLE_SECONDS)
        }
        try {
            launcher(intent)
        } catch (_: SecurityException) {
            emitProblem(R.string.err_visibility_failed)
        }
    }

    /** Поиск рядом: дополняет список спаренных устройств. */
    @Suppress("MissingPermission")
    fun startDiscovery() {
        if (!hasScan()) {
            emitProblem(R.string.err_scan_permission)
            return
        }
        stopDiscovery()
        refreshDevices()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != BluetoothDevice.ACTION_FOUND) return
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                } ?: return

                val name = safeDeviceName(device) ?: return
                val entry = NearbyDevice(device.address, name)
                _devices.value = (_devices.value + entry)
                    .distinctBy { it.address }
                    .sortedBy { it.name.lowercase() }
            }
        }
        discoveryReceiver = receiver
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothDevice.ACTION_FOUND),
            // ACTION_FOUND приходит от системы, поэтому ресивер должен быть EXPORTED.
            // Команд извне он не принимает — только публичный список устройств.
            ContextCompat.RECEIVER_EXPORTED,
        )
        try {
            if (adapter?.isDiscovering == true) adapter.cancelDiscovery()
            if (adapter?.startDiscovery() == false) emitProblem(R.string.err_scan_failed)
        } catch (e: SecurityException) {
            emitProblem(R.string.err_scan_lost)
            stopDiscovery()
        }
    }

    private fun stopDiscovery() {
        discoveryReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
                // Уже снят с регистрации — это нормально.
            }
        }
        discoveryReceiver = null
        @Suppress("MissingPermission")
        try {
            if (hasScan() && (adapter?.isDiscovering == true)) adapter.cancelDiscovery()
        } catch (_: SecurityException) {
            // Разрешение могли отозвать прямо во время поиска.
        }
    }

    fun connect(address: String) {
        if (!hasConnect()) {
            emitProblem(R.string.err_connect_permission)
            return
        }
        connectJob?.cancel()
        connectJob = scope.launch {
            val device = try {
                adapter?.getRemoteDevice(address)
            } catch (e: IllegalArgumentException) {
                emitProblem(R.string.err_bad_address)
                return@launch
            } ?: return@launch

            _state.value = State(Status.Connecting, hint = "Подключаемся…")
            @Suppress("MissingPermission")
            try {
                if (hasScan()) adapter?.cancelDiscovery()
                val candidate = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                candidate.connect()
                adoptSocket(candidate, safeDeviceName(device) ?: AppTexts.get(R.string.peer_unknown))
            } catch (_: IOException) {
                // Если входящее соединение уже принято, это не ошибка, а гонка двух
                // телефонов, одновременно нажавших «подключиться».
                if (_state.value.status == Status.Connected) return@launch
                emitProblem(R.string.err_connect_failed)
                _state.value = State(Status.Listening)
                restartListening()
            } catch (_: SecurityException) {
                if (_state.value.status == Status.Connected) return@launch
                emitProblem(R.string.err_connect_revoked)
                _state.value = State(Status.Idle)
            }
        }
    }

    /** Полный перезапуск слушателя после обрыва связи. */
    private fun restartListening() {
        listenJob?.cancel()
        listenJob = null
        startListening()
    }

    private fun startListening() {
        if (listenJob?.isActive == true) return
        listenJob = scope.launch {
            while (true) {
                if (_state.value.status == Status.Connected) return@launch
                val server = try {
                    if (!hasConnect()) return@launch
                    @Suppress("MissingPermission")
                    adapter?.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                } catch (_: IOException) {
                    emitProblem(R.string.err_server)
                    return@launch
                } catch (_: SecurityException) {
                    emitProblem(R.string.err_permission)
                    return@launch
                } ?: return@launch

                serverSocket = server
                _state.value = State(Status.Listening, hint = "Ждём соседа рядом")

                val incoming = try {
                    server.accept()
                } catch (_: IOException) {
                    null // Сервер закрыли при подключении или при выходе.
                }

                closeServer()
                if (incoming != null) {
                    connectJob?.cancel()
                    val peer = try {
                        @Suppress("MissingPermission")
                        safeDeviceName(incoming.remoteDevice) ?: AppTexts.get(R.string.peer_unknown)
                    } catch (_: SecurityException) {
                        AppTexts.get(R.string.peer_unknown)
                    }
                    adoptSocket(incoming, peer)
                    return@launch
                } else if (_state.value.status == Status.Connecting) {
                    return@launch // Идёт исходящее подключение, не мешаем.
                }
            }
        }
    }

    private suspend fun adoptSocket(candidate: BluetoothSocket, peerName: String) {
        val accepted = socketMutex.withLock {
            if (socket != null) {
                false
            } else {
                socket = candidate
                true
            }
        }
        if (!accepted) {
            try {
                candidate.close()
            } catch (_: IOException) {
            }
            return
        }

        closeServer()
        connectJob?.cancel()
        stopDiscovery()
        _state.value = State(Status.Connected, peerName = peerName)
        startReading(candidate)
    }

    private fun startReading(active: BluetoothSocket) {
        readJob?.cancel()
        readJob = scope.launch {
            val reader = BufferedReader(InputStreamReader(active.inputStream))
            try {
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isNotBlank()) _events.tryEmit(Event.Incoming(line))
                }
            } catch (_: IOException) {
                // Соединение закрыто — это штатный выход.
            } finally {
                releaseSocket(active)
            }
        }
    }

    private fun releaseSocket(closed: BluetoothSocket) {
        scope.launch {
            val wasActive = socketMutex.withLock {
                if (socket === closed) {
                    socket = null
                    true
                } else {
                    false
                }
            }
            if (!wasActive) return@launch
            _events.tryEmit(Event.Disconnected)
            _state.value = State(Status.Idle)
            restartListening()
        }
    }

    fun send(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (_state.value.status != Status.Connected) return false
        val payload = trimmed.replace('\n', ' ') + "\n"
        scope.launch {
            val active = socketMutex.withLock { socket } ?: return@launch
            writeMutex.withLock {
                @Suppress("MissingPermission")
                try {
                    active.outputStream.write(payload.toByteArray(Charsets.UTF_8))
                    active.outputStream.flush()
                } catch (e: IOException) {
                    emitProblem(R.string.err_send_failed)
                    try {
                        active.close()
                    } catch (_: IOException) {
                    }
                }
            }
        }
        return true
    }

    fun disconnect() {
        scope.launch {
            val active = socketMutex.withLock {
                val current = socket
                socket = null
                current
            }
            try {
                active?.close()
            } catch (_: IOException) {
            }
            if (active != null) {
                _events.tryEmit(Event.Disconnected)
                _state.value = State(Status.Idle)
            }
            restartListening()
        }
    }

    @Suppress("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice): String? = try {
        device.name?.takeIf { it.isNotBlank() }
    } catch (_: SecurityException) {
        null
    }

    private fun closeServer() {
        try {
            serverSocket?.close()
        } catch (_: IOException) {
        }
        serverSocket = null
    }

    private fun emitProblem(resId: Int) {
        _events.tryEmit(Event.Problem(AppTexts.get(resId)))
    }

    fun close() {
        stopDiscovery()
        listenJob?.cancel()
        readJob?.cancel()
        connectJob?.cancel()
        closeServer()
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        socket = null
        linkJob.cancel()
    }
}
