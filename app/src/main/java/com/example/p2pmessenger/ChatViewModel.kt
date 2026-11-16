package com.example.p2pmessenger

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.p2pmessenger.bluetooth.ChatLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import com.example.p2pmessenger.settings.AppTexts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class ChatMessage(
    val id: Long,
    val text: String,
    val mine: Boolean,
    val time: String,
    val system: Boolean = false,
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val status: ChatLink.Status = ChatLink.Status.Idle,
    val peerName: String? = null,
    val hint: String? = null,
    val nearby: List<ChatLink.NearbyDevice> = emptyList(),
    val showDevices: Boolean = false,
    val draft: String = "",
    val permissionsGranted: Boolean = false,
    val bluetoothReady: Boolean = false,
    val snackbar: String? = null,
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private val ids = AtomicLong(0)
    }

    private val link = ChatLink(application)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var wasConnected = false

    init {
        combine(link.state, link.devices) { linkState, devices ->
            val justConnected = linkState.status == ChatLink.Status.Connected && !wasConnected
            wasConnected = linkState.status == ChatLink.Status.Connected
            _uiState.update { current ->
                current.copy(
                    status = linkState.status,
                    peerName = linkState.peerName,
                    hint = linkState.hint,
                    nearby = devices,
                )
            }
            if (justConnected) addSystemMessage(AppTexts.get(R.string.system_connected))
        }.launchIn(viewModelScope)

        link.events.onEach { event ->
            when (event) {
                is ChatLink.Event.Incoming -> addMessage(event.text, mine = false)
                is ChatLink.Event.Problem -> _uiState.update { it.copy(snackbar = event.text) }
                ChatLink.Event.Disconnected -> {
                    _uiState.update { it.copy(snackbar = AppTexts.get(R.string.info_disconnected)) }
                }
            }
        }.launchIn(viewModelScope)

        refreshEnvironment()
    }

    /** Вызывается после возврата из системных диалогов и при выходе на передний план. */
    fun refreshEnvironment() {
        val granted = link.hasAllPermissions()
        val ready = link.isBluetoothAvailable()
        _uiState.update {
            it.copy(permissionsGranted = granted, bluetoothReady = ready)
        }
        if (granted && ready) {
            link.start()
        } else if (!granted) {
            _uiState.update {
                it.copy(status = ChatLink.Status.Idle, hint = AppTexts.get(R.string.hint_permission))
            }
        } else {
            _uiState.update {
                it.copy(status = ChatLink.Status.Idle, hint = AppTexts.get(R.string.hint_bluetooth_off))
            }
        }
    }

    fun requiredPermissions(): Array<String> = link.requiredPermissions()

    fun requestDiscoverable(launcher: (Intent) -> Unit) = link.makeDiscoverable(launcher)

    fun onDraftChange(value: String) {
        // Одно сообщение = одна строка: перенос строки служит разделителем кадров.
        _uiState.update { it.copy(draft = value.replace('\n', ' ')) }
    }

    fun send() {
        val text = _uiState.value.draft.trim()
        if (text.isEmpty()) return
        if (link.send(text)) {
            addMessage(text, mine = true)
            _uiState.update { it.copy(draft = "") }
        } else {
            _uiState.update { it.copy(snackbar = AppTexts.get(R.string.err_connect_first)) }
        }
    }

    fun toggleDevices() {
        val opening = !_uiState.value.showDevices
        _uiState.update { it.copy(showDevices = opening) }
        if (opening) {
            link.refreshDevices()
            link.startDiscovery()
        }
    }

    fun closeDevices() = _uiState.update { it.copy(showDevices = false) }

    fun connect(address: String) {
        _uiState.update { it.copy(showDevices = false) }
        link.connect(address)
    }

    fun disconnect() = link.disconnect()

    fun consumeSnackbar() = _uiState.update { it.copy(snackbar = null) }

    private fun addSystemMessage(text: String) {
        val message = ChatMessage(
            id = ids.incrementAndGet(),
            text = text,
            mine = false,
            time = formatTime(),
            system = true,
        )
        _uiState.update { it.copy(messages = it.messages + message) }
    }

    private fun addMessage(text: String, mine: Boolean) {
        val message = ChatMessage(
            id = ids.incrementAndGet(),
            text = text,
            mine = mine,
            time = formatTime(),
        )
        _uiState.update { it.copy(messages = it.messages + message) }
    }

    private fun formatTime(): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    override fun onCleared() {
        link.close()
    }
}
