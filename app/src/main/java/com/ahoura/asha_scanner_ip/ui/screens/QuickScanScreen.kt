package com.ahoura.asha_scanner_ip.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahoura.asha_scanner_ip.core.model.ProbeMode
import com.ahoura.asha_scanner_ip.ui.ScanViewModel
import com.ahoura.asha_scanner_ip.ui.i18n.LocalStrings
import com.ahoura.asha_scanner_ip.ui.components.CyberAppBar
import com.ahoura.asha_scanner_ip.ui.components.KvInput
import com.ahoura.asha_scanner_ip.ui.components.ModeSegment
import com.ahoura.asha_scanner_ip.ui.components.Pill
import com.ahoura.asha_scanner_ip.ui.components.PresetRow
import com.ahoura.asha_scanner_ip.ui.components.ScanButton
import com.ahoura.asha_scanner_ip.ui.components.SectionLabel
import com.ahoura.asha_scanner_ip.ui.components.ToggleRowCyber
import androidx.compose.runtime.collectAsState
import com.ahoura.asha_scanner_ip.core.model.ScanConfig

@Composable
fun QuickScanScreen(vm: ScanViewModel, onBack: () -> Unit, onStart: () -> Unit) {
    val state by vm.state.collectAsState()
    val cfg = state.scanConfig
    val s = LocalStrings.current

    Column(Modifier.fillMaxSize()) {
        CyberAppBar(
            title = s.quickScan,
            onBack = onBack,
            trailing = { Pill(cfg.mode.wire.uppercase()) },
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScanSettings(vm = vm, cfg = cfg, showAdvancedDefault = false)
            Spacer(Modifier.size(4.dp))
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
    SectionLabel(s.count)
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

    Spacer(Modifier.size(10.dp))
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
            v.toIntOrNull()?.let { n -> vm.updateScanConfig { it.copy(concurrency = n.coerceIn(1, 512)) } }
        }, keyboardType = KeyboardType.Number)
    }

    Spacer(Modifier.size(10.dp))
    SectionLabel(s.timeout)
    Spacer(Modifier.size(6.dp))
    PresetRow(
        options = listOf("3s", "5s", "10s", "···"),
        selectedIndex = if (customTimeout) 3 else timeouts.indexOf((cfg.timeoutMs / 1000).toInt()).let { if (it < 0) 3 else it },
    ) { i ->
        if (i < 3) { customTimeout = false; vm.updateScanConfig { it.copy(timeoutMs = timeouts[i] * 1000L) } }
        else customTimeout = true
    }
    if (customTimeout) {
        Spacer(Modifier.size(6.dp))
        KvInput("seconds", (cfg.timeoutMs / 1000).toString(), { v ->
            v.toIntOrNull()?.let { n -> vm.updateScanConfig { it.copy(timeoutMs = n.coerceIn(1, 60) * 1000L) } }
        }, keyboardType = KeyboardType.Number)
    }

    Spacer(Modifier.size(14.dp))
    ToggleRowCyber(s.advancedOptions, advanced) { advanced = it }

    if (advanced) {
        Spacer(Modifier.size(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KvInput("tries", cfg.tries.toString(), { v ->
                v.toIntOrNull()?.let { n -> vm.updateScanConfig { it.copy(tries = n.coerceIn(1, 10)) } }
            }, keyboardType = KeyboardType.Number)
            KvInput("port", cfg.ports.firstOrNull()?.toString() ?: "443", { v ->
                v.toIntOrNull()?.let { n -> vm.updateScanConfig { it.copy(ports = listOf(n.coerceIn(1, 65535))) } }
            }, keyboardType = KeyboardType.Number)
            KvInput("cidr", vm.state.value.customRangesText, vm::onCustomRangesChange, placeholder = "all")
            KvInput("sni", cfg.sniOverride, { v -> vm.updateScanConfig { it.copy(sniOverride = v) } }, placeholder = "auto")
        }
        Spacer(Modifier.size(10.dp))
        SectionLabel(s.mode)
        Spacer(Modifier.size(6.dp))
        val modes = listOf(ProbeMode.HTTP, ProbeMode.TLS, ProbeMode.TCP)
        ModeSegment(
            options = listOf("HTTP", "TLS", "TCP"),
            selectedIndex = modes.indexOf(cfg.mode).coerceAtLeast(0),
        ) { i -> vm.updateScanConfig { it.copy(mode = modes[i]) } }
        Spacer(Modifier.size(12.dp))
        ToggleRowCyber("IPv6", cfg.useV6) { c -> vm.updateScanConfig { it.copy(useV6 = c, useV4 = if (!c) true else it.useV4) } }
        Spacer(Modifier.size(8.dp))
        ToggleRowCyber(s.speedTest, cfg.speedTest) { c -> vm.updateScanConfig { it.copy(speedTest = c) } }
        Spacer(Modifier.size(8.dp))
        ToggleRowCyber(s.openSiteFallback, cfg.fallbackToDomains) { c -> vm.updateScanConfig { it.copy(fallbackToDomains = c) } }
        if (cfg.fallbackToDomains) {
            Spacer(Modifier.size(4.dp))
            Text(
                s.openSiteFallbackHint,
                color = com.ahoura.asha_scanner_ip.ui.theme.TextMutedC,
                fontFamily = com.ahoura.asha_scanner_ip.ui.theme.monoFamily(com.ahoura.asha_scanner_ip.ui.i18n.LocalLang.current),
                fontSize = 9.sp, letterSpacing = 0.3.sp, lineHeight = 14.sp,
            )
            Spacer(Modifier.size(8.dp))
            KvInput(
                "domains",
                vm.state.value.fallbackDomainsText,
                vm::onFallbackDomainsChange,
                placeholder = "add open CF sites (e.g. discord.com)",
            )
        }
    }
}
