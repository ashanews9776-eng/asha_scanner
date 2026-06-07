package com.ahoura.asha_scanner_ip.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahoura.asha_scanner_ip.core.engine.ResultSort
import com.ahoura.asha_scanner_ip.core.model.ScanResult
import com.ahoura.asha_scanner_ip.core.output.ConfigLinkBuilder
import com.ahoura.asha_scanner_ip.core.output.Exporter
import com.ahoura.asha_scanner_ip.ui.ScanViewModel
import com.ahoura.asha_scanner_ip.ui.components.ColoBadge
import com.ahoura.asha_scanner_ip.ui.components.CyberAppBar
import com.ahoura.asha_scanner_ip.ui.components.LottieSonar
import com.ahoura.asha_scanner_ip.ui.components.ScanButton
import com.ahoura.asha_scanner_ip.ui.components.StaggerIn
import com.ahoura.asha_scanner_ip.ui.i18n.Lang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalLang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalStrings
import com.ahoura.asha_scanner_ip.ui.i18n.localizeDigits
import com.ahoura.asha_scanner_ip.ui.theme.Accent
import com.ahoura.asha_scanner_ip.ui.theme.AccentDim
import com.ahoura.asha_scanner_ip.ui.theme.AccentBorder
import com.ahoura.asha_scanner_ip.ui.theme.AccentMuted
import com.ahoura.asha_scanner_ip.ui.theme.BlueC
import com.ahoura.asha_scanner_ip.ui.theme.BorderC
import com.ahoura.asha_scanner_ip.ui.theme.GoldC
import com.ahoura.asha_scanner_ip.ui.theme.RedC
import com.ahoura.asha_scanner_ip.ui.theme.ShareTechMono
import com.ahoura.asha_scanner_ip.ui.theme.SilverC
import com.ahoura.asha_scanner_ip.ui.theme.SurfaceC
import com.ahoura.asha_scanner_ip.ui.theme.TextFadedC
import com.ahoura.asha_scanner_ip.ui.theme.TextMutedC
import com.ahoura.asha_scanner_ip.ui.theme.TextPrimaryC
import com.ahoura.asha_scanner_ip.ui.theme.TextSecondaryC

@Composable
fun ResultsScreen(vm: ScanViewModel, onAgain: () -> Unit, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val results = remember(state.progress.best) { ResultSort.bySpeed(state.progress.best) }
    val bestMs = results.filter { it.healthy }.minOfOrNull { it.avgLatencyMs }?.toInt() ?: 0
    // When the user pasted a vless/trojan link, we can hand back ready-to-use
    // config links (clean IP swapped in + sni/host auto-injected), not just IPs.
    val proxy = state.parsedProxy
    val s = LocalStrings.current
    val lang = LocalLang.current
    var snack by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(snack) { if (snack != null) { kotlinx.coroutines.delay(1800); snack = null } }

    fun share(content: String, label: String) {
        runCatching {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"; putExtra(Intent.EXTRA_TEXT, content)
            }, label))
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            CyberAppBar(title = s.results, onBack = onBack)
            Column(Modifier.padding(horizontal = 12.dp)) {
                // ---- Header numbers ----
                val labelFont = if (lang == Lang.FA) com.ahoura.asha_scanner_ip.ui.theme.Vazirmatn else ShareTechMono
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("${state.progress.found}".localizeDigits(lang), color = Accent, fontFamily = ShareTechMono, fontSize = 32.sp)
                        Text(
                            s.healthyIpsTopShown.format(results.size).localizeDigits(lang),
                            color = TextSecondaryC, fontFamily = labelFont, fontSize = if (lang == Lang.FA) 10.sp else 9.sp,
                            letterSpacing = if (lang == Lang.FA) 0.sp else 1.sp,
                        )
                    }
                    LottieSonar(Modifier.size(56.dp))
                    Box(Modifier.size(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if (bestMs > 0) "$bestMs".localizeDigits(lang) else "—", color = BlueC, fontFamily = ShareTechMono, fontSize = 22.sp)
                        Text(
                            if (lang == Lang.FA) s.bestMs else s.bestMs.uppercase(),
                            color = TextMutedC, fontFamily = labelFont, fontSize = if (lang == Lang.FA) 10.sp else 9.sp,
                            letterSpacing = if (lang == Lang.FA) 0.sp else 1.sp,
                        )
                    }
                }
                Spacer8()
                // ---- Ready-to-use config links (only when a config was pasted) ----
                if (proxy != null && results.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ActionBtn("⎘ ${s.copyConfigs.format(results.size)}", Accent, AccentMuted, AccentBorder, Modifier.weight(1f)) {
                            clipboard.setText(AnnotatedString(Exporter.configs(results, proxy)))
                            snack = s.copiedConfigs.format(results.size)
                        }
                        ActionBtn("↗ ${s.share}", BlueC, BlueC.copy(alpha = 0.06f), BlueC.copy(alpha = 0.4f), Modifier.weight(1f)) {
                            share(Exporter.configs(results, proxy), "Configs")
                        }
                    }
                    Spacer8()
                }
                // ---- Action buttons ----
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActionBtn("⎘ ${s.copy}", Accent, AccentMuted, AccentBorder, Modifier.weight(1f)) {
                        clipboard.setText(AnnotatedString(Exporter.endpoints(results))); snack = s.copiedIps.format(results.size)
                    }
                    ActionBtn("↓ CSV", BlueC, BlueC.copy(alpha = 0.06f), BlueC.copy(alpha = 0.4f), Modifier.weight(1f)) {
                        share(Exporter.csv(results), "CSV")
                    }
                    ActionBtn("↓ JSON", BlueC, BlueC.copy(alpha = 0.06f), BlueC.copy(alpha = 0.4f), Modifier.weight(1f)) {
                        share(Exporter.json(results), "JSON")
                    }
                    ActionBtn("↓ TXT", TextSecondaryC, SurfaceC, BorderC, Modifier.weight(1f)) {
                        share(Exporter.txt(results), "TXT")
                    }
                }
                Spacer8()
            }

            if (results.isEmpty()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("∅", color = TextMutedC, fontFamily = ShareTechMono, fontSize = 40.sp)
                    Spacer8()
                    Text(
                        s.noResults, color = TextPrimaryC,
                        fontFamily = if (lang == Lang.FA) com.ahoura.asha_scanner_ip.ui.theme.Vazirmatn else ShareTechMono,
                        fontSize = 13.sp, letterSpacing = if (lang == Lang.FA) 0.sp else 0.5.sp,
                    )
                    Spacer8()
                    Text(
                        s.noResultsHint, color = TextSecondaryC,
                        fontFamily = if (lang == Lang.FA) com.ahoura.asha_scanner_ip.ui.theme.Vazirmatn else ShareTechMono,
                        fontSize = 11.sp, lineHeight = 16.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            } else LazyColumn(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(results, key = { _, r -> r.endpoint }) { index, r ->
                    val rank = index + 1
                    StaggerIn(rank.coerceAtMost(12)) {
                        ResultRowFull(rank, r, hasConfig = proxy != null) {
                            if (proxy != null) {
                                clipboard.setText(AnnotatedString(ConfigLinkBuilder.withAddress(proxy, r.ip)))
                                snack = s.copiedConfigFor.format(r.ip)
                            } else {
                                clipboard.setText(AnnotatedString(r.ip)); snack = s.copiedIp.format(r.ip)
                            }
                        }
                    }
                }
                item { Spacer8() }
            }

            Column(Modifier.padding(12.dp)) {
                ScanButton(text = "↻  ${s.scanAgain}", onClick = onAgain)
            }
        }

        // ---- Snackbar overlay ----
        if (snack != null) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 84.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(SurfaceC)
                    .border(0.5.dp, AccentBorder, RoundedCornerShape(6.dp)).padding(12.dp),
            ) {
                Text(snack!!, color = Accent, fontFamily = ShareTechMono, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ActionBtn(text: String, fg: Color, bg: Color, border: Color, modifier: Modifier, onClick: () -> Unit) {
    val lang = LocalLang.current
    Box(
        modifier.height(38.dp).clip(RoundedCornerShape(5.dp)).background(bg)
            .border(0.5.dp, border, RoundedCornerShape(5.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = fg,
            fontFamily = com.ahoura.asha_scanner_ip.ui.theme.displayFamily(lang),
            fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
            letterSpacing = if (lang == Lang.FA) 0.sp else 0.5.sp, maxLines = 1,
        )
    }
}

@Composable
private fun ResultRowFull(rank: Int, r: ScanResult, hasConfig: Boolean, onCopy: () -> Unit) {
    val rankColor = when (rank) {
        1 -> GoldC
        2, 3 -> SilverC
        else -> TextFadedC
    }
    val ipColor = if (rank <= 3) Accent else TextPrimaryC
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).background(SurfaceC)
            .border(0.5.dp, BorderC, RoundedCornerShape(5.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            rank.toString().padStart(2, '0'), color = rankColor, fontFamily = ShareTechMono,
            fontSize = 12.sp, modifier = Modifier.width(24.dp),
        )
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(r.ip, color = ipColor, fontFamily = ShareTechMono, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${r.avgLatencyMs.toInt()}ms", color = latency(r.avgLatencyMs.toInt()), fontFamily = ShareTechMono, fontSize = 10.sp)
                Text("loss ${(r.loss * 100).toInt()}%", color = TextMutedC, fontFamily = ShareTechMono, fontSize = 10.sp)
                if (r.speedTested && r.throughputBytesPerSec > 0) {
                    Text("${(r.throughputBytesPerSec / 1024).toInt()}KB/s", color = AccentDim, fontFamily = ShareTechMono, fontSize = 10.sp)
                }
            }
        }
        ColoBadge(r.colo)
        Box(Modifier.size(8.dp))
        // Larger tap target (≥40dp) around the copy glyph; copies a ready config
        // link when a proxy was pasted, otherwise the bare IP.
        Box(
            Modifier.size(40.dp).clickable(onClick = onCopy),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = if (hasConfig) "copy config" else "copy IP",
                tint = Accent, modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun latency(ms: Int): Color = when {
    ms <= 0 -> TextFadedC
    ms < 100 -> Accent
    ms <= 200 -> com.ahoura.asha_scanner_ip.ui.theme.OrangeC
    else -> RedC
}

@Composable private fun Spacer8() = Box(Modifier.size(8.dp))
