package com.ahoura.asha_scanner_ip.ui.screens

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahoura.asha_scanner_ip.core.engine.ResultSort
import com.ahoura.asha_scanner_ip.core.engine.SortKey
import com.ahoura.asha_scanner_ip.core.model.ScanPhase
import com.ahoura.asha_scanner_ip.core.model.ScanResult
import com.ahoura.asha_scanner_ip.ui.ScanViewModel
import com.ahoura.asha_scanner_ip.ui.i18n.Lang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalLang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalStrings
import com.ahoura.asha_scanner_ip.ui.i18n.localizeDigits
import com.ahoura.asha_scanner_ip.ui.theme.Vazirmatn
import com.ahoura.asha_scanner_ip.ui.theme.displayFamily
import com.ahoura.asha_scanner_ip.ui.theme.monoFamily
import com.ahoura.asha_scanner_ip.ui.components.ColoBadge
import com.ahoura.asha_scanner_ip.ui.components.NeonProgressBar
import com.ahoura.asha_scanner_ip.ui.components.RadarSweep
import com.ahoura.asha_scanner_ip.ui.theme.Accent
import com.ahoura.asha_scanner_ip.ui.theme.AccentDim
import com.ahoura.asha_scanner_ip.ui.theme.BlueC
import com.ahoura.asha_scanner_ip.ui.theme.AccentBorder
import com.ahoura.asha_scanner_ip.ui.theme.AccentMuted
import com.ahoura.asha_scanner_ip.ui.theme.BorderC
import com.ahoura.asha_scanner_ip.ui.theme.OrangeC
import com.ahoura.asha_scanner_ip.ui.theme.ProgressGradient
import com.ahoura.asha_scanner_ip.ui.theme.Rajdhani
import com.ahoura.asha_scanner_ip.ui.theme.RedC
import com.ahoura.asha_scanner_ip.ui.theme.ShareTechMono
import com.ahoura.asha_scanner_ip.ui.theme.SurfaceC
import com.ahoura.asha_scanner_ip.ui.theme.TextFadedC
import com.ahoura.asha_scanner_ip.ui.theme.TextMutedC
import com.ahoura.asha_scanner_ip.ui.theme.TextPrimaryC
import com.ahoura.asha_scanner_ip.ui.theme.TextSecondaryC

private val SORT_TABS = listOf("AVG↑", "LOSS", "JITTER", "SPEED", "COLO")

@Composable
fun ScanLiveScreen(vm: ScanViewModel, onCancel: () -> Unit, onFinished: () -> Unit) {
    val state by vm.state.collectAsState()
    val p = state.progress
    val s = LocalStrings.current
    val lang = LocalLang.current
    val fa = lang == Lang.FA
    var sortTab by remember { mutableIntStateOf(0) }

    val validating = p.phase == ScanPhase.VALIDATING
    val resolving = p.phase == ScanPhase.RESOLVING
    val hasConfig = state.parsedProxy != null

    // Auto-advance to results once the scan settles; when the speed phase begins,
    // jump the table to the SPEED sort since that's what the user now cares about.
    LaunchedEffect(p.phase) {
        if (validating) sortTab = 3
        if (p.phase == ScanPhase.DONE || p.phase == ScanPhase.CANCELLED) onFinished()
    }

    val results = remember(p.best, sortTab) {
        when (sortTab) {
            1 -> ResultSort.by(p.best, SortKey.LOSS)
            2 -> ResultSort.by(p.best, SortKey.JITTER)
            3 -> ResultSort.by(p.best, SortKey.SPEED)
            4 -> p.best.sortedWith(compareByDescending<ScanResult> { it.healthy }.thenBy { it.colo }.thenBy { it.ip })
            else -> ResultSort.by(p.best, SortKey.LATENCY)
        }
    }

    val frac = when (p.phase) {
        ScanPhase.VALIDATING -> if (p.validateTotal > 0) p.validated.toFloat() / p.validateTotal else 1f
        else -> if (p.total > 0) p.tested.toFloat() / p.total else 0f
    }
    val probed by animateIntAsState(p.tested, label = "probed")
    val healthy by animateIntAsState(p.found, label = "healthy")
    val elapsedS = p.elapsedMs / 1000

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Spacer16()
        // ---- Header ----
        val title = when {
            validating -> s.measuringSpeed
            resolving -> s.resolvingOpenSites
            else -> "${s.probing} · ${state.scanConfig.mode.wire.uppercase()}"
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = TextPrimaryC, fontFamily = displayFamily(lang), fontWeight = FontWeight.Bold,
                    fontSize = 18.sp, letterSpacing = if (fa) 0.sp else 1.sp,
                )
                Box(Modifier.size(2.dp))
                val counts = "${p.validated}/${p.validateTotal}".localizeDigits(lang)
                val phaseHint = when {
                    validating && hasConfig -> "$counts ${s.phaseThroughConfig}"
                    validating -> "$counts ${s.phaseEdgesMeasured}"
                    resolving -> s.phaseDnsLookup
                    p.usingFallback -> s.phaseFallbackEdges
                    else -> s.phaseHandshake
                }
                Text(
                    phaseHint,
                    color = if (validating) AccentDim else TextSecondaryC,
                    fontFamily = monoFamily(lang), fontSize = 9.sp, letterSpacing = if (fa) 0.sp else 1.sp,
                )
            }
            Box(
                Modifier.clip(RoundedCornerShape(4.dp)).background(RedC.copy(alpha = 0.08f))
                    .border(0.5.dp, RedC.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .clickable(onClick = onCancel).padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text("✕ ${s.cancel}", color = RedC, fontFamily = monoFamily(lang), fontSize = 10.sp, letterSpacing = if (fa) 0.sp else 0.5.sp)
            }
        }

        if (resolving || p.usingFallback) {
            Spacer8()
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                    .background(OrangeC.copy(alpha = 0.08f))
                    .border(0.5.dp, OrangeC.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Text(
                    "⚠ ${s.fallbackBanner}",
                    color = OrangeC, fontFamily = monoFamily(lang), fontSize = 10.sp,
                    letterSpacing = if (fa) 0.sp else 0.3.sp, lineHeight = 14.sp,
                )
            }
        }

        // ---- Radar hero (probe / resolve phases) ----
        if (p.phase == ScanPhase.PROBING || resolving) {
            Spacer8()
            Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                RadarSweep(
                    modifier = Modifier.size(150.dp),
                    color = if (resolving) OrangeC else Accent,
                    blips = p.found,
                    active = true,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${p.found}".localizeDigits(lang), color = Accent, fontFamily = ShareTechMono, fontSize = 26.sp)
                    Text(
                        if (fa) s.healthy else s.healthy.uppercase(),
                        color = TextMutedC, fontFamily = if (fa) Vazirmatn else ShareTechMono,
                        fontSize = if (fa) 9.sp else 8.sp, letterSpacing = if (fa) 0.sp else 2.sp,
                    )
                }
            }
        }

        Spacer8()
        // ---- Progress bar (neon, color shifts to blue while measuring) ----
        NeonProgressBar(
            progress = frac,
            color = if (validating) BlueC else if (resolving) OrangeC else Accent,
            modifier = Modifier.fillMaxWidth().height(5.dp),
        )

        Spacer8()
        // ---- Stats (third box reflects the active phase) ----
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatBox(s.probed, probed.toString(), Accent, lang, Modifier.weight(1f))
            StatBox(s.healthy, healthy.toString(), AccentDim, lang, Modifier.weight(1f))
            if (validating) {
                StatBox(s.tested, "${p.validated}/${p.validateTotal}", BlueC, lang, Modifier.weight(1f))
            } else {
                StatBox(s.done, "${(frac * 100).toInt()}%", OrangeC, lang, Modifier.weight(1f))
            }
            StatBox(s.elapsed, "%02d:%02d".format(elapsedS / 60, elapsedS % 60), BlueC, lang, Modifier.weight(1f))
        }

        Spacer8()
        // ---- Sort tabs ----
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SORT_TABS.forEachIndexed { i, label ->
                val sel = i == sortTab
                Box(
                    Modifier.clip(RoundedCornerShape(4.dp))
                        .background(if (sel) AccentMuted else SurfaceC)
                        .border(0.5.dp, if (sel) AccentBorder else BorderC, RoundedCornerShape(4.dp))
                        .clickable { sortTab = i }.padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(label, color = if (sel) Accent else TextSecondaryC, fontFamily = ShareTechMono, fontSize = 11.sp, letterSpacing = 0.5.sp)
                }
            }
        }

        Spacer8()
        // ---- Column headers ----
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
            HeaderCell("IP", Modifier.weight(1f), TextAlign.Start)
            HeaderCell("MS", Modifier.width(48.dp), TextAlign.End)
            HeaderCell("LOS%", Modifier.width(44.dp), TextAlign.End)
            HeaderCell("DL", Modifier.width(52.dp), TextAlign.End)
            HeaderCell("COLO", Modifier.width(48.dp), TextAlign.End)
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(BorderC))

        // ---- Results table ----
        var detail by remember { mutableStateOf<ScanResult?>(null) }
        Box(Modifier.weight(1f)) {
            if (results.isEmpty()) {
                BlinkingCursor()
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(results, key = { it.endpoint }) { r ->
                        ResultRow(r) { detail = r }
                    }
                }
            }
        }

        if (detail != null) {
            DetailSheet(detail!!) { detail = null }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailSheet(r: ScanResult, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState()
    val clipboard = LocalClipboardManager.current
    val s = LocalStrings.current
    val lang = LocalLang.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = SurfaceC) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(r.ip, color = Accent, fontFamily = ShareTechMono, fontSize = 18.sp)
            Spacer8()
            DetailLine("PORT", r.port.toString())
            DetailLine("AVG", "${r.avgLatencyMs.toInt()} ms")
            DetailLine("MIN", "${r.minLatencyMs} ms")
            DetailLine("JITTER", "${r.jitterMs} ms")
            DetailLine("LOSS", "${(r.loss * 100).toInt()}%")
            if (r.speedTested && r.throughputBytesPerSec > 0) {
                DetailLine("DOWNLOAD", "${(r.throughputBytesPerSec / 1024).toInt()} KB/s  ·  ${"%.1f".format(r.throughputMbps)} Mbps")
            } else if (r.speedTested) {
                DetailLine("DOWNLOAD", "—")
            }
            DetailLine("COLO", r.colo.ifBlank { "—" })
            DetailLine("TLS", if (r.tlsOk) "OK" else "—")
            DetailLine("HTTP", if (r.httpStatus > 0) r.httpStatus.toString() else "—")
            Spacer8()
            Box(
                Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(6.dp))
                    .background(AccentMuted).border(0.5.dp, AccentBorder, RoundedCornerShape(6.dp))
                    .clickable { clipboard.setText(AnnotatedString(r.ip)) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "⎘ ${if (lang == Lang.FA) s.copyIp else s.copyIp.uppercase()}",
                    color = Accent, fontFamily = displayFamily(lang), fontWeight = FontWeight.Bold,
                    fontSize = 13.sp, letterSpacing = if (lang == Lang.FA) 0.sp else 1.sp,
                )
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondaryC, fontFamily = ShareTechMono, fontSize = 11.sp, letterSpacing = 0.5.sp)
        Text(value, color = TextPrimaryC, fontFamily = ShareTechMono, fontSize = 13.sp)
    }
}

@Composable
private fun ResultRow(r: ScanResult, onClick: () -> Unit) {
    val avg = r.avgLatencyMs.toInt()
    val lossPct = (r.loss * 100).toInt()
    val dl = (r.throughputBytesPerSec / 1024).toInt()
    val ipColor = when {
        r.loss == 0.0 && avg < 100 -> Accent
        r.loss < 0.05 && avg < 150 -> TextSecondaryC
        else -> TextFadedC
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(r.ip, color = ipColor, fontFamily = ShareTechMono, fontSize = 12.sp, modifier = Modifier.weight(1f))
        MetricCell(avg.toString(), latencyColor(avg), Modifier.width(48.dp))
        MetricCell("$lossPct", lossColor(lossPct), Modifier.width(44.dp))
        MetricCell(if (r.speedTested) dl.toString() else "·", dlColor(dl, r.speedTested), Modifier.width(52.dp))
        Box(Modifier.width(48.dp), contentAlignment = Alignment.CenterEnd) { ColoBadge(r.colo) }
    }
}

@Composable
private fun MetricCell(text: String, color: Color, modifier: Modifier) {
    Text(text, color = color, fontFamily = ShareTechMono, fontSize = 12.sp, textAlign = TextAlign.End, modifier = modifier)
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier, align: TextAlign) {
    Text(text, color = TextMutedC, fontFamily = ShareTechMono, fontSize = 9.sp, letterSpacing = 0.5.sp, textAlign = align, modifier = modifier)
}

@Composable
private fun StatBox(label: String, value: String, color: Color, lang: Lang, modifier: Modifier) {
    val fa = lang == Lang.FA
    Box(
        modifier.clip(RoundedCornerShape(4.dp)).background(SurfaceC)
            .border(0.5.dp, BorderC, RoundedCornerShape(4.dp)).padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.localizeDigits(lang), color = color, fontFamily = ShareTechMono, fontSize = 16.sp)
            Box(Modifier.size(2.dp))
            Text(
                if (fa) label else label.uppercase(),
                color = TextMutedC, fontFamily = if (fa) Vazirmatn else ShareTechMono,
                fontSize = if (fa) 9.sp else 8.sp, letterSpacing = if (fa) 0.sp else 1.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BlinkingCursor() {
    val alpha by androidx.compose.animation.core.rememberInfiniteTransition(label = "cur").animateFloat(
        1f, 0f, androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse,
        ), label = "blink",
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "_", color = Accent.copy(alpha = alpha), fontFamily = ShareTechMono, fontSize = 28.sp,
        )
    }
}

private fun latencyColor(ms: Int): Color = when {
    ms <= 0 -> TextFadedC
    ms < 100 -> AccentDim
    ms <= 200 -> OrangeC
    else -> RedC
}

private fun lossColor(pct: Int): Color = when {
    pct == 0 -> AccentDim
    pct <= 5 -> OrangeC
    else -> RedC
}

private fun dlColor(kbps: Int, tested: Boolean): Color = when {
    !tested -> TextFadedC
    kbps > 300 -> AccentDim
    kbps >= 50 -> OrangeC
    else -> RedC
}

@Composable private fun Spacer8() = Box(Modifier.size(8.dp))
@Composable private fun Spacer16() = Box(Modifier.size(16.dp))
