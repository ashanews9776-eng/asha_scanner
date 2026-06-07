package com.ahoura.asha_scanner_ip.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ahoura.asha_scanner_ip.R

/** Rajdhani — display / labels / titles. */
val Rajdhani = FontFamily(
    Font(R.font.rajdhani_regular, FontWeight.Normal),
    Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
    Font(R.font.rajdhani_bold, FontWeight.Bold),
)

/** Share Tech Mono — IPs, numbers, metrics, terminal text. */
val ShareTechMono = FontFamily(
    Font(R.font.sharetechmono_regular, FontWeight.Normal),
)

/** Vazirmatn — Persian text. Rajdhani / ShareTechMono have no Persian glyphs. */
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

/**
 * Language-aware family pickers. Persian prose must render in Vazirmatn (the
 * Latin display/mono fonts show tofu for Persian glyphs); English keeps the
 * cyberpunk Rajdhani / ShareTechMono look. Use these for any *translated* text;
 * leave purely technical mono text (IPs, metrics, codes) on ShareTechMono.
 */
fun displayFamily(lang: com.ahoura.asha_scanner_ip.ui.i18n.Lang): FontFamily =
    if (lang == com.ahoura.asha_scanner_ip.ui.i18n.Lang.FA) Vazirmatn else Rajdhani

fun monoFamily(lang: com.ahoura.asha_scanner_ip.ui.i18n.Lang): FontFamily =
    if (lang == com.ahoura.asha_scanner_ip.ui.i18n.Lang.FA) Vazirmatn else ShareTechMono

val Typography = Typography(
    displaySmall = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = 0.5.sp),
    headlineMedium = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.5.sp),
    headlineSmall = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    titleLarge = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.5.sp),
    titleMedium = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = ShareTechMono, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = ShareTechMono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontFamily = ShareTechMono, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.5.sp),
    labelMedium = TextStyle(fontFamily = ShareTechMono, fontWeight = FontWeight.Normal, fontSize = 10.sp, lineHeight = 14.sp),
    labelSmall = TextStyle(fontFamily = ShareTechMono, fontWeight = FontWeight.Normal, fontSize = 9.sp, lineHeight = 13.sp),
)
