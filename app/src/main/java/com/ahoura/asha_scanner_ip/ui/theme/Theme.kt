package com.ahoura.asha_scanner_ip.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.ahoura.asha_scanner_ip.ui.i18n.Lang

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
    lang: Lang = Lang.EN,
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val typography = if (lang == Lang.FA) {
        Typography.copy(
            displaySmall = Typography.displaySmall.copy(fontFamily = Vazirmatn),
            headlineMedium = Typography.headlineMedium.copy(fontFamily = Vazirmatn),
            headlineSmall = Typography.headlineSmall.copy(fontFamily = Vazirmatn),
            titleLarge = Typography.titleLarge.copy(fontFamily = Vazirmatn),
            titleMedium = Typography.titleMedium.copy(fontFamily = Vazirmatn),
            bodyLarge = Typography.bodyLarge.copy(fontFamily = Vazirmatn),
            bodyMedium = Typography.bodyMedium.copy(fontFamily = Vazirmatn),
            bodySmall = Typography.bodySmall.copy(fontFamily = Vazirmatn),
            labelLarge = Typography.labelLarge.copy(fontFamily = Vazirmatn),
            labelMedium = Typography.labelMedium.copy(fontFamily = Vazirmatn),
            labelSmall = Typography.labelSmall.copy(fontFamily = Vazirmatn),
        )
    } else Typography

    MaterialTheme(
        colorScheme = CyberColors,
        typography = typography,
        content = content,
    )
}
