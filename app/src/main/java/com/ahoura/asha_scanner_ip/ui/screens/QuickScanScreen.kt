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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
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
import com.ahoura.asha_scanner_ip.ui.components.ExpandableCyberCard
import com.ahoura.asha_scanner_ip.ui.components.KvInput
import com.ahoura.asha_scanner_ip.ui.components.Pill
import com.ahoura.asha_scanner_ip.ui.components.PresetRow
import com.ahoura.asha_scanner_ip.ui.components.ScanButton
import com.ahoura.asha_scanner_ip.ui.components.SectionLabel
import com.ahoura.asha_scanner_ip.ui.components.TierSelector
import com.ahoura.asha_scanner_ip.ui.i18n.Lang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalLang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalStrings
import com.ahoura.asha_scanner_ip.ui.i18n.localizeDigits
import com.ahoura.asha_scanner_ip.ui.theme.Accent
import com.ahoura.asha_scanner_ip.ui.theme.AccentBorder
import com.ahoura.asha_scanner_ip.ui.theme.AccentMuted
import com.ahoura.asha_scanner_ip.ui.theme.BorderC
import com.ahoura.asha_scanner_ip.ui.theme.RedC
import com.ahoura.asha_scanner_ip.ui.theme.ShareTechMono
import com.ahoura.asha_scanner_ip.ui.theme.SurfaceC
import com.ahoura.asha_scanner_ip.ui.theme.TextMutedC
import com.ahoura.asha_scanner_ip.ui.theme.TextSecondaryC
import com.ahoura.asha_scanner_ip.ui.theme.Vazirmatn
import com.ahoura.asha_scanner_ip.ui.theme.displayFamily
import com.ahoura.asha_scanner_ip.ui.theme.monoFamily

@Composable
fun QuickScanScreen(vm: ScanViewModel, onBack: () -> Unit, onStart: () -> Unit) {
    val state by vm.state.collectAsState()
    val cfg = state.scanConfig
    val s = LocalStrings.current

    Column(Modifier.fillMaxSize()) {
        CyberAppBar(title = s.quickScan, onBack = onBack, trailing = { Pill("TARGETED") })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScanSettings(vm = vm, cfg = cfg, showAdvancedDefault = false)
        }
        Column(Modifier.padding(12.dp)) {
            ScanButton(text = s.startScan, icon = Icons.Filled.PlayArrow, onClick = onStart)
        }
    }
}

@Composable
fun CyberSectionHeader(title: String, modifier: Modifier = Modifier) {
    val lang = LocalLang.current
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 14.dp)
                .background(Accent, RoundedCornerShape(1.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title.uppercase(),
            color = Color.White,
            fontFamily = displayFamily(lang),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = if (lang == Lang.FA) 0.sp else 1.2.sp,
        )
    }
}

/** Shared settings block used by Quick + Custom screens. */
@Composable
fun ScanSettings(vm: ScanViewModel, cfg: ScanConfig, showAdvancedDefault: Boolean) {
    val state by vm.state.collectAsState()
    val s = LocalStrings.current
    val lang = LocalLang.current

    var customCount by remember(cfg.tier) { mutableStateOf(false) }
    var customWorkers by remember(cfg.tier) { mutableStateOf(false) }
    var customTimeout by remember(cfg.tier) { mutableStateOf(false) }
    var customPort by remember(cfg.tier) { mutableStateOf(false) }

    val counts = listOf(500, 1000, 5000)
    val workers = listOf(50, 100, 200)
    val timeouts = listOf(3, 5, 10)
    val ports = listOf(443, 2053, 2083, 2086, 2096, 8443)

    // ==========================================
    // 01 / ESSENTIALS & CONFIG
    // ==========================================
    CyberSectionHeader(s.secEssentials)

    KvInput(
        label = s.configOptional,
        value = state.configText,
        onValueChange = vm::onConfigChange,
        placeholder = s.configPlaceholder,
    )
    if (state.parsedProxy != null) {
        Text(
            s.configParsed,
            color = Accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = monoFamily(lang),
            modifier = Modifier.padding(start = 2.dp, top = 2.dp)
        )
    } else if (state.parseError) {
        Text(
            s.configUnrecognized,
            color = RedC,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = monoFamily(lang),
            modifier = Modifier.padding(start = 2.dp, top = 2.dp)
        )
    }

    Spacer(Modifier.height(4.dp))
    SectionLabel(s.mode)
    TierSelector(selected = cfg.tier, onSelect = vm::setTier)

    Spacer(Modifier.height(10.dp))

    ExpandableCyberCard(
        title = s.advancedOptions,
        subtitle = if (lang == Lang.FA) "اهداف دستی، دور زدن DPI، پورت‌ها و تنظیمات دقیق" else "Custom targets, anti-DPI, ports & fine-tuning",
        initiallyExpanded = showAdvancedDefault,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // ==========================================
            // 02 / TARGETS & IP POOL
            // ==========================================
            CyberSectionHeader(s.secTargets)

    KvInput(
        label = s.pastedTargetsTitle,
        value = cfg.pastedTargets,
        onValueChange = vm::onPastedTargetsChange,
        placeholder = s.pastedTargetsPlaceholder,
    )

    // Live Target Counter & Domain Resolution Status
    if (state.isResolvingTargets) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp)
        ) {
            Box(Modifier.size(6.dp).background(Accent, RoundedCornerShape(1.dp)))
            Spacer(Modifier.width(6.dp))
            Text(
                s.resolvingTargets,
                color = Accent,
                fontFamily = monoFamily(lang),
                fontSize = 10.sp,
            )
        }
    } else if (state.targetPreview != null) {
        val preview = state.targetPreview!!
        val countText = s.targetPreviewCount
            .replace("{0}", preview.count.toString().localizeDigits(lang))
            .replace("{1}", preview.domains.toString().localizeDigits(lang))

        Column(Modifier.padding(vertical = 4.dp, horizontal = 2.dp)) {
            Text(
                text = "✓ $countText",
                color = Accent,
                fontFamily = monoFamily(lang),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (preview.skipped.isNotEmpty()) {
                val skippedText = s.skippedTargets.replace("{0}", preview.skipped.size.toString().localizeDigits(lang))
                Text(
                    text = "⚠ $skippedText",
                    color = RedC,
                    fontFamily = monoFamily(lang),
                    fontSize = 10.sp,
                )
            }
        }
    }

    // Skip Reachability Probe (Phase 2 direct throughput test)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(SurfaceC)
            .border(0.5.dp, BorderC, RoundedCornerShape(4.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.skipReachability, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(s.skipReachabilityDesc, color = TextSecondaryC, fontSize = 10.sp, lineHeight = 13.sp)
            }
            ToggleSwitch(cfg.phase2Only) { v -> vm.setPhase2Only(v) }
        }
    }

    // ==========================================
    // 03 / ANTI-DPI & EVASION
    // ==========================================
    CyberSectionHeader(s.secAntiDpi)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(SurfaceC)
            .border(0.5.dp, if (cfg.antiDpi.enabled) AccentBorder else BorderC, RoundedCornerShape(4.dp))
            .padding(10.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.antiDpiTitle, color = if (cfg.antiDpi.enabled) Accent else Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(s.antiDpiSubtitle, color = TextSecondaryC, fontSize = 10.sp, lineHeight = 13.sp)
                }
                ToggleSwitch(cfg.antiDpi.enabled) { v -> vm.setAntiDpiEnabled(v) }
            }

            if (cfg.antiDpi.enabled) {
                Spacer(Modifier.height(10.dp))
                KvInput(
                    label = s.antiDpiRecipeLabel,
                    value = cfg.antiDpi.finalmask,
                    onValueChange = vm::onAntiDpiRecipeChange,
                )

                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val validationError = cfg.antiDpi.validate()
                    if (validationError != null) {
                        Text(
                            text = validationError,
                            color = RedC,
                            fontFamily = monoFamily(lang),
                            fontSize = 9.sp,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                    } else {
                        Text(
                            text = "✓ Valid recipe",
                            color = Accent,
                            fontFamily = monoFamily(lang),
                            fontSize = 9.sp,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                    }

                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Accent.copy(alpha = 0.15f))
                            .border(0.5.dp, Accent, RoundedCornerShape(4.dp))
                            .clickable { vm.resetAntiDpiToDefaults() }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Refresh, null, tint = Accent, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = s.suggestedValues,
                                color = Accent,
                                fontFamily = monoFamily(lang),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // 04 / SCAN PROFILE & PARAMS
    // ==========================================
    CyberSectionHeader(s.secProfile)

    // Fast vs Gentle Profile Switch
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(SurfaceC)
            .border(0.5.dp, BorderC, RoundedCornerShape(4.dp))
            .padding(10.dp)
    ) {
        Column {
            Text(
                s.scanProfile.uppercase(),
                color = TextMutedC,
                fontFamily = monoFamily(lang),
                fontSize = 9.sp,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Fast Option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (!cfg.gentle) Accent else SurfaceC)
                        .border(0.5.dp, if (!cfg.gentle) Accent else BorderC, RoundedCornerShape(4.dp))
                        .clickable { vm.setGentle(false) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = s.scanProfileFast,
                        color = if (!cfg.gentle) Color.Black else TextSecondaryC,
                        fontFamily = monoFamily(lang),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Gentle Option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (cfg.gentle) Accent else SurfaceC)
                        .border(0.5.dp, if (cfg.gentle) Accent else BorderC, RoundedCornerShape(4.dp))
                        .clickable { vm.setGentle(true) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = s.scanProfileGentle,
                        color = if (cfg.gentle) Color.Black else TextSecondaryC,
                        fontFamily = monoFamily(lang),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = if (cfg.gentle) s.scanProfileGentleDesc else s.scanProfileFastDesc,
                color = if (cfg.gentle) Accent else TextMutedC,
                fontFamily = if (lang == Lang.FA) Vazirmatn else monoFamily(lang),
                fontSize = 10.sp,
                lineHeight = 13.sp,
            )
        }
    }

    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(s.count)
        Spacer(Modifier.size(8.dp))
        Text(
            if (lang == Lang.FA) "(تعداد هدف آی‌پی‌های سالم)" else "(Target healthy IPs)",
            color = TextMutedC, fontFamily = monoFamily(lang), fontSize = 8.sp
        )
    }
    PresetRow(
        options = listOf("500", "1K", "5K", "···"),
        selectedIndex = if (customCount) 3 else counts.indexOf(cfg.count).let { if (it < 0) 3 else it },
    ) { i ->
        if (i < 3) {
            customCount = false
            vm.updateScanConfig { it.copy(count = counts[i], tier = com.ahoura.asha_scanner_ip.core.model.ScanTier.CUSTOM) }
        } else customCount = true
    }
    if (customCount) {
        Spacer(Modifier.size(4.dp))
        KvInput("count", cfg.count.toString(), { v ->
            v.toIntOrNull()?.let { n -> vm.updateScanConfig { it.copy(count = n.coerceIn(1, 2_000_000)) } }
        }, keyboardType = KeyboardType.Number)
    }

    Spacer(Modifier.size(6.dp))
    SectionLabel(s.workers)
    PresetRow(
        options = listOf("50", "100", "200", "···"),
        selectedIndex = if (customWorkers) 3 else workers.indexOf(cfg.concurrency).let { if (it < 0) 3 else it },
    ) { i ->
        if (i < 3) {
            customWorkers = false
            vm.updateScanConfig { it.copy(concurrency = workers[i], tier = com.ahoura.asha_scanner_ip.core.model.ScanTier.CUSTOM) }
        } else customWorkers = true
    }
    if (customWorkers) {
        Spacer(Modifier.size(4.dp))
        KvInput("workers", cfg.concurrency.toString(), { v ->
            v.toIntOrNull()?.let { n -> vm.updateScanConfig { it.copy(concurrency = n.coerceIn(1, 1000)) } }
        }, keyboardType = KeyboardType.Number)
    }

    Spacer(Modifier.size(6.dp))
    SectionLabel(s.timeout)
    PresetRow(
        options = listOf("3s", "5s", "10s", "···"),
        selectedIndex = if (customTimeout) 3 else (cfg.timeoutMs / 1000).toInt().let { timeouts.indexOf(it) }.let { if (it < 0) 3 else it },
    ) { i ->
        if (i < 3) {
            customTimeout = false
            vm.updateScanConfig { it.copy(timeoutMs = timeouts[i].toLong() * 1000, tier = com.ahoura.asha_scanner_ip.core.model.ScanTier.CUSTOM) }
        } else customTimeout = true
    }
    if (customTimeout) {
        Spacer(Modifier.size(4.dp))
        KvInput("timeout", (cfg.timeoutMs / 1000).toString(), { v ->
            v.toIntOrNull()?.let { n -> vm.updateScanConfig { it.copy(timeoutMs = n.coerceIn(1, 60).toLong() * 1000) } }
        }, keyboardType = KeyboardType.Number)
    }

    Spacer(Modifier.size(6.dp))
    SectionLabel(s.port)
    PresetRow(
        options = ports.map { it.toString() } + "···",
        selectedIndex = if (customPort) ports.size else cfg.ports.firstOrNull()?.let { ports.indexOf(it) }.let { if (it == null || it < 0) ports.size else it },
    ) { i ->
        if (i < ports.size) { customPort = false; vm.updateScanConfig { it.copy(ports = listOf(ports[i])) } }
        else customPort = true
    }
    if (customPort) {
        Spacer(Modifier.size(4.dp))
        KvInput("port", (cfg.ports.firstOrNull() ?: 443).toString(), { v ->
            v.toIntOrNull()?.let { n -> vm.updateScanConfig { it.copy(ports = listOf(n.coerceIn(1, 65535))) } }
        }, keyboardType = KeyboardType.Number)
    }

        Spacer(Modifier.size(8.dp))
        SectionLabel(s.mode)
        PresetRow(
            options = listOf("TCP", "TLS", "HTTP"),
            selectedIndex = listOf("tcp", "tls", "http").indexOf(cfg.mode.wire),
        ) { i ->
            val modes = listOf(com.ahoura.asha_scanner_ip.core.model.ProbeMode.TCP, com.ahoura.asha_scanner_ip.core.model.ProbeMode.TLS, com.ahoura.asha_scanner_ip.core.model.ProbeMode.HTTP)
            vm.updateScanConfig { it.copy(mode = modes[i]) }
        }

        Spacer(Modifier.size(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(SurfaceC)
                .border(0.5.dp, BorderC, RoundedCornerShape(4.dp))
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.speedTest, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Measure real download throughput", color = TextSecondaryC, fontSize = 10.sp)
                }
                ToggleSwitch(cfg.speedTest) { v -> vm.updateScanConfig { it.copy(speedTest = v) } }
            }
        }

        Spacer(Modifier.size(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(SurfaceC)
                .border(0.5.dp, BorderC, RoundedCornerShape(4.dp))
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.fragment, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(s.fragmentDesc, color = TextSecondaryC, fontSize = 10.sp, lineHeight = 13.sp)
                }
                ToggleSwitch(cfg.fragment) { v -> vm.updateScanConfig { it.copy(fragment = v) } }
            }
        }

        Spacer(Modifier.size(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(SurfaceC)
                .border(0.5.dp, BorderC, RoundedCornerShape(4.dp))
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.smartStop, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(s.smartStopDesc, color = TextSecondaryC, fontSize = 10.sp, lineHeight = 13.sp)
                }
                ToggleSwitch(cfg.smartStop) { v -> vm.updateScanConfig { it.copy(smartStop = v) } }
            }
        }

        Spacer(Modifier.size(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(SurfaceC)
                .border(0.5.dp, BorderC, RoundedCornerShape(4.dp))
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.openSiteFallback, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(s.openSiteFallbackHint, color = TextSecondaryC, fontSize = 10.sp, lineHeight = 13.sp)
                }
                ToggleSwitch(cfg.fallbackToDomains) { v -> vm.updateScanConfig { it.copy(fallbackToDomains = v) } }
            }
        }
        }
    }
}

@Composable
private fun ToggleSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(38.dp, 20.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (checked) AccentMuted else SurfaceC)
            .border(0.5.dp, if (checked) AccentBorder else BorderC, RoundedCornerShape(4.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (checked) Accent else TextMutedC)
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart),
        )
    }
}
