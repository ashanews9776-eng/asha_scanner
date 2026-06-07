package com.ahoura.asha_scanner_ip.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahoura.asha_scanner_ip.core.ipsrc.CloudflareRanges
import com.ahoura.asha_scanner_ip.ui.TELEGRAM_HANDLE
import com.ahoura.asha_scanner_ip.ui.TELEGRAM_URL
import com.ahoura.asha_scanner_ip.ui.components.GlitchText
import com.ahoura.asha_scanner_ip.ui.components.MenuItemCard
import com.ahoura.asha_scanner_ip.ui.components.Pill
import com.ahoura.asha_scanner_ip.ui.components.StaggerIn
import com.ahoura.asha_scanner_ip.ui.components.TypewriterText
import com.ahoura.asha_scanner_ip.ui.i18n.Lang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalLang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalStrings
import com.ahoura.asha_scanner_ip.ui.theme.Accent
import com.ahoura.asha_scanner_ip.ui.theme.BlueC
import com.ahoura.asha_scanner_ip.ui.theme.OrangeC
import com.ahoura.asha_scanner_ip.ui.theme.Rajdhani
import com.ahoura.asha_scanner_ip.ui.theme.ShareTechMono
import com.ahoura.asha_scanner_ip.ui.theme.TextFadedC
import com.ahoura.asha_scanner_ip.ui.theme.TextMutedC
import com.ahoura.asha_scanner_ip.ui.theme.TextSecondaryC
import com.ahoura.asha_scanner_ip.ui.theme.Vazirmatn
import com.ahoura.asha_scanner_ip.ui.theme.displayFamily

private val Teal = Color(0xFF14B8A6)

@Composable
fun HomeScreen(
    onQuick: () -> Unit,
    onCustom: () -> Unit,
    onTest: () -> Unit,
    onDiscover: () -> Unit,
    onAbout: () -> Unit,
    onToggleLang: () -> Unit,
) {
    val context = LocalContext.current
    val s = LocalStrings.current
    val lang = LocalLang.current

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Spacer(Modifier.size(24.dp))

        // ---- Header ----
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GlitchText(
                "ASHA SCANNER", color = Accent, fontSize = 14.sp, letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
            LangChip(lang, onToggleLang)
        }
        Spacer(Modifier.size(2.dp))
        // Persian joins cursively, so the char-by-char typewriter (and the Latin
        // mono font) is English-only; FA shows a plain Vazirmatn subtitle.
        if (lang == Lang.FA) {
            Text(s.homeSubtitle, color = TextMutedC, fontFamily = Vazirmatn, fontSize = 11.sp)
        } else {
            TypewriterText(s.homeSubtitle, color = TextMutedC, fontSize = 9.sp, letterSpacing = 2.sp)
        }
        Spacer(Modifier.size(8.dp))
        StaggerIn(0) { Pill("v0.3 · IR-OPTIMIZED") }

        Spacer(Modifier.size(24.dp))

        // ---- Menu ----
        StaggerIn(1) { MenuItemCard(Icons.Filled.Bolt, Accent, s.quickScan, s.quickScanDesc, active = true, onClick = onQuick) }
        StaggerIn(2) { MenuItemCard(Icons.Filled.Tune, BlueC, s.customScan, s.customScanDesc, active = false, onClick = onCustom) }
        StaggerIn(3) { MenuItemCard(Icons.Filled.CheckCircle, OrangeC, s.testIps, s.testIpsDesc, active = false, onClick = onTest) }
        StaggerIn(4) { MenuItemCard(Icons.Filled.Place, Teal, s.discoverColos, s.discoverColosDesc, active = false, onClick = onDiscover) }

        Spacer(Modifier.size(16.dp))

        // ---- Telegram join card ----
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                .background(BlueC.copy(alpha = 0.06f))
                .border(0.5.dp, BlueC.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                .clickable { openUrl(context, TELEGRAM_URL) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Send, null, tint = BlueC, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    if (lang == Lang.FA) s.telegramChannel else s.telegramChannel.uppercase(),
                    color = TextMutedC, fontFamily = if (lang == Lang.FA) Vazirmatn else ShareTechMono,
                    fontSize = if (lang == Lang.FA) 10.sp else 8.sp,
                    letterSpacing = if (lang == Lang.FA) 0.sp else 1.5.sp,
                )
                Text(TELEGRAM_HANDLE, color = BlueC, fontFamily = ShareTechMono, fontSize = 13.sp)
            }
            Text(
                s.join, color = BlueC, fontFamily = displayFamily(lang),
                fontWeight = FontWeight.Bold, fontSize = 12.sp,
                letterSpacing = if (lang == Lang.FA) 0.sp else 1.sp,
            )
        }

        Spacer(Modifier.weight(1f))

        // ---- Footer ----
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val footFont = if (lang == Lang.FA) Vazirmatn else ShareTechMono
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${s.cfRanges} ", color = TextFadedC, fontFamily = footFont, fontSize = 8.sp)
                Text("${CloudflareRanges.V4.size}", color = Accent, fontFamily = ShareTechMono, fontSize = 8.sp)
                Text(" ${s.cidrs}", color = TextFadedC, fontFamily = footFont, fontSize = 8.sp)
            }
            Text(
                if (lang == Lang.FA) s.license else s.license.uppercase(),
                color = TextSecondaryC, fontFamily = footFont, fontSize = 8.sp,
                letterSpacing = if (lang == Lang.FA) 0.sp else 1.sp,
                modifier = Modifier.clickable(onClick = onAbout),
            )
        }
    }
}

/** Tiny pill that switches to the other language (FA ⇄ EN). */
@Composable
private fun LangChip(lang: Lang, onToggle: () -> Unit) {
    // Label is the language you'll switch *to*, so a single tap is unambiguous.
    val label = if (lang == Lang.EN) "فارسی" else "EN"
    val font = if (lang == Lang.EN) Vazirmatn else ShareTechMono
    Box(
        Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(BlueC.copy(alpha = 0.06f))
            .border(0.5.dp, BlueC.copy(alpha = 0.4f), RoundedCornerShape(5.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, color = BlueC, fontFamily = font, fontSize = 12.sp)
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
