package com.example.p2pmessenger.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.p2pmessenger.ChatMessage
import com.example.p2pmessenger.ChatUiState
import com.example.p2pmessenger.ChatViewModel
import com.example.p2pmessenger.R
import com.example.p2pmessenger.bluetooth.ChatLink
import com.example.p2pmessenger.settings.AppLanguage
import com.example.p2pmessenger.settings.AppTexts
import com.example.p2pmessenger.settings.ThemeMode
import com.example.p2pmessenger.ui.theme.LiveGradient
import com.example.p2pmessenger.ui.theme.liveBackground
import com.example.p2pmessenger.ui.theme.liveOrb
import com.example.p2pmessenger.ui.theme.Moss
import com.example.p2pmessenger.ui.theme.Muted
import com.example.p2pmessenger.ui.theme.MutedDark
import com.example.p2pmessenger.ui.theme.P2PMessengerTheme

@Composable
fun ChatScreen(
    themeMode: ThemeMode,
    language: AppLanguage,
    onThemeChange: (ThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    viewModel: ChatViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }

    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    // State<Float> НЕ разворачиваем в composition: читаем только в draw-фазе.
    val phase = LiveGradient.phase()
    val accentBrush = remember(dark) { LiveGradient.accent(dark) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refreshEnvironment() }

    val discoverableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.refreshEnvironment() }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.refreshEnvironment() }

    LaunchedEffect(Unit) {
        if (!viewModel.uiState.value.permissionsGranted) {
            permissionLauncher.launch(viewModel.requiredPermissions())
        } else {
            viewModel.refreshEnvironment()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshEnvironment() }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSnackbar()
        }
    }

    val enableBluetooth: () -> Unit = {
        try {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } catch (_: SecurityException) {
            Toast.makeText(context, AppTexts.get(R.string.toast_permission), Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .liveBackground(phase, dark),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            ) {
                Header(
                    state = state,
                    accent = accentBrush,
                    onSearch = { viewModel.toggleDevices() },
                    onDiscoverable = {
                        viewModel.requestDiscoverable { intent -> discoverableLauncher.launch(intent) }
                    },
                    onSettings = { showSettings = true },
                    onDisconnect = { viewModel.disconnect() },
                )

                Box(modifier = Modifier.weight(1f)) {
                    if (state.messages.isEmpty()) {
                        EmptyChat(
                            state = state,
                            phase = phase,
                            accent = accentBrush,
                            dark = dark,
                            onConnect = { viewModel.toggleDevices() },
                            onPermissions = { permissionLauncher.launch(viewModel.requiredPermissions()) },
                            onEnableBluetooth = enableBluetooth,
                        )
                    } else {
                        MessageList(state.messages, dark)
                    }
                }

                Composer(
                    state = state,
                    accent = accentBrush,
                    disabledAccent = remember { LiveGradient.disabledAccent() },
                    onDraftChange = viewModel::onDraftChange,
                    onSend = viewModel::send,
                )
            }
        }
    }

    if (state.showDevices) {
        DeviceSheet(
            state = state,
            accent = accentBrush,
            dark = dark,
            onDismiss = viewModel::closeDevices,
            onRefresh = {
                viewModel.closeDevices()
                viewModel.toggleDevices()
            },
            onConnect = viewModel::connect,
            onDiscoverable = {
                viewModel.requestDiscoverable { intent -> discoverableLauncher.launch(intent) }
            },
        )
    }

    if (showSettings) {
        SettingsSheet(
            themeMode = themeMode,
            language = language,
            accent = accentBrush,
            dark = dark,
            onDismiss = { showSettings = false },
            onThemeChange = onThemeChange,
            onLanguageChange = onLanguageChange,
        )
    }
}

@Composable
private fun Header(
    state: ChatUiState,
    accent: Brush,
    onSearch: () -> Unit,
    onDiscoverable: () -> Unit,
    onSettings: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val connected = state.status == ChatLink.Status.Connected
    val dotColor by animateColorAsState(
        targetValue = when (state.status) {
            ChatLink.Status.Connected -> Moss
            ChatLink.Status.Listening -> Color(0xFFEF8354)
            else -> Muted
        },
        label = "statusDot",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.title_nearby),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = when {
                        connected -> stringResource(
                            R.string.status_connected,
                            state.peerName ?: stringResource(R.string.peer_unknown),
                        )
                        state.status == ChatLink.Status.Connecting ->
                            stringResource(R.string.status_connecting)
                        state.status == ChatLink.Status.Listening ->
                            stringResource(R.string.status_listening)
                        else -> state.hint ?: stringResource(R.string.status_idle)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        IconButton(onClick = onSettings) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = stringResource(R.string.cd_settings),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDiscoverable, enabled = state.permissionsGranted) {
            Icon(
                Icons.Filled.Visibility,
                contentDescription = stringResource(R.string.cd_show_me),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GradientIconButton(brush = accent, onClick = onSearch) {
            Icon(
                Icons.AutoMirrored.Filled.BluetoothSearching,
                contentDescription = stringResource(R.string.cd_find_devices),
                tint = Color.White,
            )
        }
        if (connected) {
            IconButton(onClick = onDisconnect) {
                Icon(
                    Icons.Filled.LinkOff,
                    contentDescription = stringResource(R.string.cd_disconnect),
                    tint = Muted,
                )
            }
        }
    }
}

@Composable
private fun GradientIconButton(
    brush: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(brush)
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize(), content = content)
    }
}

@Composable
private fun EmptyChat(
    state: ChatUiState,
    phase: State<Float>,
    accent: Brush,
    dark: Boolean,
    onConnect: () -> Unit,
    onPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(168.dp)
                .liveOrb(phase, dark),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Forum,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = Color.White,
                )
            }
        }

        Spacer(Modifier.height(30.dp))
        Text(
            text = stringResource(R.string.empty_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(26.dp))

        GradientButton(
            brush = accent,
            text = when {
                !state.permissionsGranted -> stringResource(R.string.action_grant)
                !state.bluetoothReady -> stringResource(R.string.action_enable_bt)
                else -> stringResource(R.string.action_find)
            },
            onClick = when {
                !state.permissionsGranted -> onPermissions
                !state.bluetoothReady -> onEnableBluetooth
                else -> onConnect
            },
        )
    }
}

@Composable
private fun GradientButton(brush: Brush, text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(28.dp))
            .background(brush)
            .height(52.dp),
        contentAlignment = Alignment.Center,
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White,
            ),
            elevation = null,
        ) { Text(text, style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun MessageList(messages: List<ChatMessage>, dark: Boolean) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            if (message.system) {
                SystemNote(message)
            } else {
                MessageBubble(message, dark)
            }
        }
    }
}

@Composable
private fun SystemNote(message: ChatMessage) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, dark: Boolean) {
    val shape = if (message.mine) {
        RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp)
    } else {
        RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp)
    }
    val brush = if (message.mine) {
        LiveGradient.bubbleMine(dark)
    } else {
        LiveGradient.bubbleTheir(dark)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(brush)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f), shape),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = message.time,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (dark) MutedDark else Muted,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@Composable
private fun Composer(
    state: ChatUiState,
    accent: Brush,
    disabledAccent: Brush,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(color = Color.Transparent, shadowElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        stringResource(R.string.composer_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                shape = RoundedCornerShape(28.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    focusedBorderColor = Color(0xFFEF8354),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                ),
            )
            Spacer(Modifier.size(10.dp))
            GradientIconButton(
                brush = if (state.draft.isBlank()) disabledAccent else accent,
                onClick = onSend,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.cd_send),
                    tint = Color.White,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceSheet(
    state: ChatUiState,
    accent: Brush,
    dark: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onConnect: (String) -> Unit,
    onDiscoverable: () -> Unit,
) {
    SheetContainer(accent = accent, dark = dark, onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.cd_refresh),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_close),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.sheet_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        if (state.nearby.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Bluetooth,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.sheet_empty_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.sheet_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            state.nearby.forEach { device ->
                Surface(
                    onClick = { onConnect(device.address) },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(accent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Bluetooth,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.size(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(device.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                device.address,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onDiscoverable, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.Filled.Visibility, contentDescription = null, tint = Color(0xFFEF8354))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.action_show_me), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    themeMode: ThemeMode,
    language: AppLanguage,
    accent: Brush,
    dark: Boolean,
    onDismiss: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
) {
    SheetContainer(accent = accent, dark = dark, onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_close))
            }
        }

        Spacer(Modifier.height(22.dp))
        Text(
            stringResource(R.string.settings_theme),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = themeMode == mode,
                    onClick = { onThemeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                    icon = {
                        when (mode) {
                            ThemeMode.LIGHT -> Icon(Icons.Filled.LightMode, null, Modifier.size(18.dp))
                            ThemeMode.DARK -> Icon(Icons.Filled.DarkMode, null, Modifier.size(18.dp))
                            ThemeMode.SYSTEM -> Icon(Icons.Filled.Settings, null, Modifier.size(18.dp))
                        }
                    },
                    label = {
                        Text(
                            stringResource(
                                when (mode) {
                                    ThemeMode.SYSTEM -> R.string.theme_system
                                    ThemeMode.LIGHT -> R.string.theme_light
                                    ThemeMode.DARK -> R.string.theme_dark
                                },
                            )
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(26.dp))
        Text(
            stringResource(R.string.settings_language),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            AppLanguage.entries.forEachIndexed { index, lang ->
                SegmentedButton(
                    selected = language == lang,
                    onClick = { onLanguageChange(lang) },
                    shape = SegmentedButtonDefaults.itemShape(index, AppLanguage.entries.size),
                    label = {
                        Text(
                            stringResource(
                                if (lang == AppLanguage.RU) R.string.lang_ru else R.string.lang_en,
                            ),
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetContainer(
    accent: Brush,
    dark: Boolean,
    onDismiss: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        containerColor = Color.Transparent,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp)
                    .size(width = 42.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(LiveGradient.sheet(dark))
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            content = content,
        )
    }
}

@Preview(showBackground = true, name = "Пустой чат")
@Composable
private fun EmptyChatPreview() {
    P2PMessengerTheme(themeMode = ThemeMode.LIGHT) {
        val phase = LiveGradient.phase()
        val accent = remember { LiveGradient.accent(false) }
        Box(Modifier.fillMaxSize().liveBackground(phase, false)) {
            EmptyChat(
                state = ChatUiState(permissionsGranted = true, bluetoothReady = true),
                phase = phase,
                accent = accent,
                dark = false,
                onConnect = {},
                onPermissions = {},
                onEnableBluetooth = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Переписка")
@Composable
private fun MessagesPreview() {
    P2PMessengerTheme(themeMode = ThemeMode.LIGHT) {
        val phase = LiveGradient.phase()
        Box(Modifier.fillMaxSize().liveBackground(phase, false)) {
            MessageList(
                listOf(
                    ChatMessage(1, "Соединение установлено ☕", false, "14:02", system = true),
                    ChatMessage(2, "Привет! Ты рядом?", false, "14:02"),
                    ChatMessage(3, "Ага, за соседним столом 👋", true, "14:03"),
                    ChatMessage(4, "Без вайфая работает, магия", false, "14:03"),
                ),
                dark = false,
            )
        }
    }
}
