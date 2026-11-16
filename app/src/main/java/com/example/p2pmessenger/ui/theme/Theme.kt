package com.example.p2pmessenger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.example.p2pmessenger.settings.ThemeMode

// Намеренно без Dynamic Color: у приложения свой характер.
private val LightColors = lightColorScheme(
    primary = Coral,
    onPrimary = Cream,
    primaryContainer = Sand,
    onPrimaryContainer = Ink,
    secondary = Plum,
    onSecondary = Cream,
    secondaryContainer = Sand,
    onSecondaryContainer = Ink,
    tertiary = Moss,
    background = Cream,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    surfaceVariant = Sand,
    onSurfaceVariant = Muted,
    outline = Muted,
)

private val DarkColors = darkColorScheme(
    primary = CoralDark,
    onPrimary = CreamDark,
    primaryContainer = SandDark,
    onPrimaryContainer = InkDark,
    secondary = Plum,
    onSecondary = CreamDark,
    secondaryContainer = BubbleTheirDark,
    onSecondaryContainer = InkDark,
    tertiary = MossDark,
    background = CreamDark,
    onBackground = InkDark,
    surface = CreamDark,
    onSurface = InkDark,
    surfaceVariant = SandDark,
    onSurfaceVariant = MutedDark,
    outline = MutedDark,
)

@Composable
fun P2PMessengerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
