package com.ahoura.asha_scanner_ip.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CyberColors = darkColorScheme(
    primary = Accent,
    onPrimary = Background,
    primaryContainer = AccentDim,
    onPrimaryContainer = Background,
    secondary = BlueC,
    onSecondary = Background,
    tertiary = OrangeC,
    background = Background,
    onBackground = TextPrimaryC,
    surface = SurfaceC,
    onSurface = TextPrimaryC,
    surfaceVariant = SurfaceAltC,
    onSurfaceVariant = TextSecondaryC,
    outline = BorderC,
    error = RedC,
    onError = Background,
)

@Composable
fun Asha_scanner_ipTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = CyberColors,
        typography = Typography,
        content = content,
    )
}
