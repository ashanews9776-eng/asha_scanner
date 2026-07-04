package com.ahoura.asha_scanner_ip.ui.screens

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahoura.asha_scanner_ip.core.model.ScanConfig
import com.ahoura.asha_scanner_ip.ui.ScanViewModel
import com.ahoura.asha_scanner_ip.ui.components.CyberAppBar
import com.ahoura.asha_scanner_ip.ui.components.KvInput
import com.ahoura.asha_scanner_ip.ui.components.Pill
import com.ahoura.asha_scanner_ip.ui.components.PresetRow
import com.ahoura.asha_scanner_ip.ui.components.ScanButton
import com.ahoura.asha_scanner_ip.ui.components.SectionLabel
import com.ahoura.asha_scanner_ip.ui.i18n.Lang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalLang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalStrings
import com.ahoura.asha_scanner_ip.ui.theme.Accent
import com.ahoura.asha_scanner_ip.ui.theme.AccentBorder
import com.ahoura.asha_scanner_ip.ui.theme.AccentMuted
import com.ahoura.asha_scanner_ip.ui.theme.BorderC
import com.ahoura.asha_scanner_ip.ui.theme.ShareTechMono
import com.ahoura.asha_scanner_ip.ui.theme.SurfaceC
import com.ahoura.asha_scanner_ip.ui.theme.TextMutedC
import com.ahoura.asha_scanner_ip.ui.theme.TextSecondaryC
import com.ahoura.asha_scanner_ip.ui.theme.monoFamily

@Composable
fun QuickScanScreen(vm: ScanViewModel, onBack: () -> Unit, onStart: () -> Unit) {
    val state by vm.state.collectAsState()
    val cfg = state.scanConfig
    val s = LocalStrings.current

    Column(Modifier.fillMaxSize()) {
        CyberAppBar(title = s.quickScan, onBack = onBack, trailing = { Pill("RANDOM") })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScanSettings(vm = vm, cfg = cfg, showAdvancedDefault = false)
        }
        Column(Modifier.padding(12.dp)) {
            ScanButton(text = "▶  ${s.startScan}", icon = Icons.Filled.PlayArrow, onClick = onStart)
        }
    }
}

/** Shared settings block used by Quick + Custom screens. */
@Composable
fun ScanSettings(vm: ScanViewModel, cfg: ScanConfig, showAdvancedDefault: Boolean) {
    val s = LocalStrings.current
    var advanced by remember { mutableStateOf(showAdvancedDefault) }
    var customCount by remember { mutableStateOf(false) }
    var customWorkers by remember { mutableStateOf(false) }
    var customTimeout by remember { mutableStateOf(false) }

    val counts = listOf(5_000, 20_000, 100_000)
    val workers = listOf(50, 100, 200)
    val timeouts = listOf(3, 5, 10)

    Spacer(Modifier.size(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(s.count)
        Spacer(Modifier.size(8.dp))
        Text(
            if (LocalLang.current == Lang.FA) "(تعداد هدف آی‌پی‌های سالم)" else "(Target healthy IPs)",
            color = TextMutedC, fontFamily = monoFamily(LocalLang.current), fontSize = 8.sp
        )
    }
    Spacer(Modifier.size(6.dp))
    PresetRow(
        options = listOf("5K", "20K", "100K", "···"),
        selectedIndex = if (customCount) 3 else counts.indexOf(cfg.count).let { if (it < 0) 3 else it },
    ) { i ->
        if (i < 3) { customCount = false; vm.updateScanConfig { it.copy(count = counts[i]) } }
        else customCount = true
    }
    if (customCount) {
        Spacer(Modifier.size(6.dp))
        KvInput("count", cfg.count.toString(), { v ->
            v.toIntOrNull()?.let { n -> vm.updateScanConfig { it.copy(count = n.coerceIn(1, 2_000_000)) } }
        }, keyboardType = KeyboardType.Number)
    }

    Spacer(Modifier.size(6.dp))
    SectionLabel(s.workers)
    Spacer(Modifier.size(6.dp))
    PresetRow(
        options = listOf("50", "100", "200", "···"),
        selectedIndex = if (customWorkers) 3 else workers.indexOf(cfg.concurrency).let { if (it < 0) 3 else it },
    ) { i ->
        if (i < 3) { customWorkers = false; vm.updateScanConfig { it.copy(concurrency = workers[i]) } }
        else customWorkers = true
    }
    if (customWorkers) {
        Spacer(Modifier.size(6.dp))
        KvInput("workers", cfg.concurrency.toString(), { v ->
            v.toIntOrNull()?.let { n -> vm.updateScanConfig { it.copy(concurrency = n.coerceIn(1, 1000)) } }
        }, keyboardType = KeyboardType.Number)
    }

    Spacer(Modifier.size(6.dp))
    SectionLabel(s.timeout)
    Spacer(Modifier.size(6.dp))
    PresetRow(
        options = listOf("3s", "5s", "10s", "···"),
        selectedIndex = if (customTimeout) 3 else (cfg.timeoutMs / 1000).toInt().let { timeouts.indexOf(it) }.let { if (it < 0) 3 else it },
    ) { i ->
        if (i < 3) { customTimeout = false; vm.updateScanConfig { it.copy(timeoutMs = timeouts[i].toLong() * 1000) } }
        else customTimeout = true
    }
    if (customTimeout) {
        Spacer(Modifier.size(6.dp))
        KvInput("timeout", (cfg.timeoutMs / 1000).toString(), { v ->
            v.toIntOrNull()?.let { n -> vm.updateScanConfig { it.copy(timeoutMs = n.coerceIn(1, 60).toLong() * 1000) } }
        }, keyboardType = KeyboardType.Number)
    }

    Spacer(Modifier.size(10.dp))
    Row(
        Modifier.fillMaxWidth().clickable { advanced = !advanced }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(s.advancedOptions)
        Spacer(Modifier.weight(1f))
        Text(if (advanced) "—" else "+", color = Accent, fontFamily = ShareTechMono, fontSize = 14.sp)
    }

    if (advanced) {
        Spacer(Modifier.size(4.dp))
        SectionLabel(s.mode)
        Spacer(Modifier.size(6.dp))
        PresetRow(
            options = listOf("TCP", "TLS", "HTTP"),
            selectedIndex = listOf("tcp", "tls", "http").indexOf(cfg.mode.wire),
        ) { i ->
            val modes = listOf(com.ahoura.asha_scanner_ip.core.model.ProbeMode.TCP, com.ahoura.asha_scanner_ip.core.model.ProbeMode.TLS, com.ahoura.asha_scanner_ip.core.model.ProbeMode.HTTP)
            vm.updateScanConfig { it.copy(mode = modes[i]) }
        }

        Spacer(Modifier.size(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.speedTest, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Measure real download throughput", color = TextSecondaryC, fontSize = 10.sp)
            }
            ToggleSwitch(cfg.speedTest) { v -> vm.updateScanConfig { it.copy(speedTest = v) } }
        }

        Spacer(Modifier.size(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.smartStop, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(s.smartStopDesc, color = TextSecondaryC, fontSize = 10.sp, lineHeight = 14.sp)
            }
            ToggleSwitch(cfg.smartStop) { v -> vm.updateScanConfig { it.copy(smartStop = v) } }
        }

        Spacer(Modifier.size(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.openSiteFallback, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(s.openSiteFallbackHint, color = TextSecondaryC, fontSize = 10.sp, lineHeight = 14.sp)
            }
            ToggleSwitch(cfg.fallbackToDomains) { v -> vm.updateScanConfig { it.copy(fallbackToDomains = v) } }
        }
    }
}

@Composable
private fun ToggleSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(
        Modifier.size(38.dp, 20.dp).clip(RoundedCornerShape(10.dp))
            .background(if (checked) AccentMuted else SurfaceC)
            .border(0.5.dp, if (checked) AccentBorder else BorderC, RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(2.dp),
    ) {
        Box(
            Modifier.size(16.dp).clip(RoundedCornerShape(8.dp))
                .background(if (checked) Accent else TextMutedC)
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart),
        )
    }
}
