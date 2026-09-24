package com.ahoura.asha_scanner_ip.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahoura.asha_scanner_ip.core.dns.DnsProbe
import com.ahoura.asha_scanner_ip.ui.ScanViewModel
import com.ahoura.asha_scanner_ip.ui.components.CyberAppBar
import com.ahoura.asha_scanner_ip.ui.components.CyberCard
import com.ahoura.asha_scanner_ip.ui.components.Pill
import com.ahoura.asha_scanner_ip.ui.components.ScanButton
import com.ahoura.asha_scanner_ip.ui.i18n.Lang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalLang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalStrings
import com.ahoura.asha_scanner_ip.ui.theme.Accent
import com.ahoura.asha_scanner_ip.ui.theme.AccentBorder
import com.ahoura.asha_scanner_ip.ui.theme.AccentDim
import com.ahoura.asha_scanner_ip.ui.theme.AccentMuted
import com.ahoura.asha_scanner_ip.ui.theme.BlueC
import com.ahoura.asha_scanner_ip.ui.theme.BorderC
import com.ahoura.asha_scanner_ip.ui.theme.GoldC
import com.ahoura.asha_scanner_ip.ui.theme.OrangeC
import com.ahoura.asha_scanner_ip.ui.theme.RedC
import com.ahoura.asha_scanner_ip.ui.theme.ShareTechMono
import com.ahoura.asha_scanner_ip.ui.theme.SurfaceC
import com.ahoura.asha_scanner_ip.ui.theme.TextFadedC
import com.ahoura.asha_scanner_ip.ui.theme.TextMutedC
import com.ahoura.asha_scanner_ip.ui.theme.TextPrimaryC
import com.ahoura.asha_scanner_ip.ui.theme.TextSecondaryC
import com.ahoura.asha_scanner_ip.ui.theme.Vazirmatn

@Composable
fun DnsScreen(
    vm: ScanViewModel,
    onBack: () -> Unit,
    onOpenVpn: () -> Unit = {},
) {
    val s = LocalStrings.current
    val lang = LocalLang.current
    val fa = lang == Lang.FA
    val clipboard = LocalClipboardManager.current

    val dns by vm.dnsState.collectAsState()
    val appliedIp by vm.vpnDnsIp.collectAsState()
    var snack by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadDnsResolvers() }
    LaunchedEffect(snack) { if (snack != null) { kotlinx.coroutines.delay(2000); snack = null } }

    val fastest = vm.fastestDns()
    val isRunning = dns.isRunning

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            CyberAppBar(
                title = s.dnsTool,
                onBack = onBack,
                trailing = { Pill(appliedIp) },
            )

            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Spacer(Modifier.size(4.dp))

                // ---- WhiteDNS-style Live Metrics Card ----
                CyberCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(4.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            MetricItem(label = s.dnsTotal, value = "${dns.total}", color = TextPrimaryC)
                            MetricItem(label = s.dnsValid, value = "${dns.valid}", color = Accent)
                            MetricItem(label = s.dnsRejected, value = "${dns.rejected}", color = RedC)
                        }

                        Spacer(Modifier.size(8.dp))
                        LinearProgressIndicator(
                            progress = { dns.fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Accent,
                            trackColor = BorderC,
                        )

                        Spacer(Modifier.size(6.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            val statusLabel = when (dns.status) {
                                ScanViewModel.DnsScanStatus.RUNNING -> "${s.dnsTesting} ${dns.done}/${dns.total}"
                                ScanViewModel.DnsScanStatus.PAUSED -> s.dnsStop
                                ScanViewModel.DnsScanStatus.COMPLETED -> "✓"
                                ScanViewModel.DnsScanStatus.IDLE -> ""
                            }
                            Text(statusLabel, color = BlueC, fontFamily = ShareTechMono, fontSize = 10.sp)
                            Text("${(dns.fraction * 100).toInt()}%", color = TextSecondaryC, fontFamily = ShareTechMono, fontSize = 10.sp)
                        }
                    }
                }

                Spacer(Modifier.size(8.dp))

                // ---- Scan Controls (Start / Stop / Resume) ----
                when {
                    isRunning -> {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(RedC.copy(alpha = 0.2f))
                                .border(1.dp, RedC, RoundedCornerShape(6.dp))
                                .clickable { vm.stopDnsScan() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Stop, null, tint = RedC, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(s.dnsStop, color = RedC, fontFamily = ShareTechMono, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                    dns.canResume -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(GoldC.copy(alpha = 0.15f))
                                    .border(1.dp, GoldC, RoundedCornerShape(6.dp))
                                    .clickable { vm.startDnsScan(resume = true) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.PlayArrow, null, tint = GoldC, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(4.dp))
                                    Text(s.dnsResume, color = GoldC, fontFamily = ShareTechMono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Accent.copy(alpha = 0.15f))
                                    .border(1.dp, AccentBorder, RoundedCornerShape(6.dp))
                                    .clickable { vm.startDnsScan(resume = false) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Refresh, null, tint = Accent, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(4.dp))
                                    Text(s.dnsStart, color = Accent, fontFamily = ShareTechMono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    else -> {
                        ScanButton(
                            text = s.dnsTestAll,
                            icon = Icons.Filled.Bolt,
                            active = dns.resolvers.isNotEmpty(),
                        ) {
                            vm.startDnsScan(resume = false)
                        }
                    }
                }

                Spacer(Modifier.size(8.dp))

                // ---- DNS Tunnel (StormDNS) Quick Launch Card ----
                CyberCard(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenVpn),
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text("⚡", fontSize = 16.sp)
                            Spacer(Modifier.size(8.dp))
                            Column {
                                Text(
                                    if (fa) "اتصال از طریق DNS Tunnel (StormDNS)" else "Connect via DNS Tunnel (StormDNS)",
                                    color = Accent,
                                    fontFamily = if (fa) Vazirmatn else ShareTechMono,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                )
                                Text(
                                    if (fa) "تونل کردن ترافیک کل دستگاه از طریق پورت ۵۳" else "Tunnel full-device traffic over UDP:53 via resolvers",
                                    color = TextMutedC,
                                    fontFamily = if (fa) Vazirmatn else ShareTechMono,
                                    fontSize = 10.sp,
                                )
                            }
                        }
                        Pill(if (fa) "اتصال VPN" else "OPEN VPN")
                    }
                }

                Spacer(Modifier.size(8.dp))

                // ---- StormDNS Auto-Tune (measured MTU profiles) ----
                StormAutoTuneCard(vm = vm, fa = fa, onOpenVpn = onOpenVpn)

                Spacer(Modifier.size(8.dp))

                // ---- Worker Concurrency & Action Chips ----
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${s.dnsWorkers}:",
                            color = TextMutedC,
                            fontFamily = if (fa) Vazirmatn else ShareTechMono,
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.size(6.dp))
                        listOf(4, 8, 16, 32).forEach { w ->
                            val selected = dns.workerCount == w
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (selected) Accent.copy(alpha = 0.2f) else SurfaceC)
                                    .border(0.5.dp, if (selected) Accent else BorderC, RoundedCornerShape(4.dp))
                                    .clickable(enabled = !isRunning) { vm.setDnsWorkerCount(w) }
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                            ) {
                                Text("$w", color = if (selected) Accent else TextSecondaryC, fontFamily = ShareTechMono, fontSize = 10.sp)
                            }
                            Spacer(Modifier.size(4.dp))
                        }
                    }

                    // Utility Action Icons
                    Row {
                        IconButton(onClick = { showAddDialog = true }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Add, s.dnsAddCustom, tint = Accent, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { showImportDialog = true }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.FileUpload, s.dnsImport, tint = BlueC, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = {
                                val exported = vm.exportWorkingResolversText()
                                if (exported.isNotBlank()) {
                                    clipboard.setText(AnnotatedString(exported))
                                    snack = s.dnsCopiedValid
                                }
                            },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(Icons.Filled.ContentCopy, s.dnsExport, tint = GoldC, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(Modifier.size(6.dp))

                // ---- Category Filter Tabs ----
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CategoryFilterChip(
                        label = s.dnsCatAll,
                        selected = dns.selectedCategory == null,
                        fa = fa,
                        onClick = { vm.setDnsCategoryFilter(null) },
                    )
                    CategoryFilterChip(
                        label = s.dnsCatAntiSanction,
                        selected = dns.selectedCategory == DnsProbe.ResolverCategory.ANTI_SANCTION,
                        fa = fa,
                        color = GoldC,
                        onClick = { vm.setDnsCategoryFilter(DnsProbe.ResolverCategory.ANTI_SANCTION) },
                    )
                    CategoryFilterChip(
                        label = s.dnsCatGlobal,
                        selected = dns.selectedCategory == DnsProbe.ResolverCategory.GLOBAL,
                        fa = fa,
                        color = BlueC,
                        onClick = { vm.setDnsCategoryFilter(DnsProbe.ResolverCategory.GLOBAL) },
                    )
                    CategoryFilterChip(
                        label = s.dnsCatCustom,
                        selected = dns.selectedCategory == DnsProbe.ResolverCategory.CUSTOM,
                        fa = fa,
                        color = OrangeC,
                        onClick = { vm.setDnsCategoryFilter(DnsProbe.ResolverCategory.CUSTOM) },
                    )
                }

                Spacer(Modifier.size(8.dp))

                // ---- Resolvers List ----
                val sorted = remember(dns.results, dns.filteredResolvers) {
                    dns.filteredResolvers.sortedWith(
                        compareBy<DnsProbe.ResolverDef> { r -> dns.results[r.ip]?.latencyMs ?: Long.MAX_VALUE }
                            .thenBy { r -> r.name },
                    )
                }

                LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(sorted, key = { it.ip }) { r ->
                        val res = dns.results[r.ip]
                        ResolverRow(
                            resolver = r,
                            result = res,
                            isFastest = fastest?.ip == r.ip,
                            isApplied = appliedIp == r.ip,
                            fa = fa,
                            onClick = {
                                vm.applyDns(r.ip)
                                snack = s.dnsApplied
                            },
                            onDelete = if (r.category == DnsProbe.ResolverCategory.CUSTOM) {
                                { vm.deleteCustomResolver(r.ip) }
                            } else null,
                        )
                    }
                    item { Spacer(Modifier.size(16.dp)) }
                }
            }
        }

        // Bottom Notification Snackbar
        if (snack != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceC)
                    .border(0.5.dp, AccentBorder, RoundedCornerShape(6.dp))
                    .padding(12.dp),
            ) {
                Text(snack!!, color = Accent, fontFamily = ShareTechMono, fontSize = 12.sp)
            }
        }
    }

    // ---- Dialog: Add Custom Resolver ----
    if (showAddDialog) {
        var nameInput by remember { mutableStateOf("") }
        var ipInput by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = SurfaceC,
            title = { Text(s.dnsAddCustom, color = Accent, fontFamily = if (fa) Vazirmatn else ShareTechMono, fontSize = 14.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text(s.dnsCustomName, color = TextSecondaryC) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = BorderC,
                            focusedTextColor = TextPrimaryC,
                            unfocusedTextColor = TextPrimaryC,
                        ),
                    )
                    OutlinedTextField(
                        value = ipInput,
                        onValueChange = { ipInput = it; errorMsg = false },
                        label = { Text(s.dnsCustomIp, color = TextSecondaryC) },
                        isError = errorMsg,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = BorderC,
                            focusedTextColor = TextPrimaryC,
                            unfocusedTextColor = TextPrimaryC,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (DnsProbe.isValidIp(ipInput)) {
                        vm.addCustomResolver(nameInput, ipInput)
                        showAddDialog = false
                    } else {
                        errorMsg = true
                    }
                }) {
                    Text(s.dnsSave, color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(s.scanAgain, color = TextMutedC)
                }
            },
        )
    }

    // ---- Dialog: Import Resolvers List ----
    if (showImportDialog) {
        var textInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            containerColor = SurfaceC,
            title = { Text(s.dnsImport, color = BlueC, fontFamily = if (fa) Vazirmatn else ShareTechMono, fontSize = 14.sp) },
            text = {
                Column {
                    Text(
                        s.dnsImportPrompt,
                        color = TextMutedC,
                        fontSize = 11.sp,
                        fontFamily = if (fa) Vazirmatn else ShareTechMono,
                    )
                    Spacer(Modifier.size(8.dp))
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BlueC,
                            unfocusedBorderColor = BorderC,
                            focusedTextColor = TextPrimaryC,
                            unfocusedTextColor = TextPrimaryC,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val count = vm.importResolvers(textInput)
                    showImportDialog = false
                    if (count > 0) snack = "+$count resolvers"
                }) {
                    Text(s.dnsImport, color = BlueC)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(s.scanAgain, color = TextMutedC)
                }
            },
        )
    }
}

@Composable
private fun MetricItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontFamily = ShareTechMono, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextMutedC, fontFamily = ShareTechMono, fontSize = 9.sp)
    }
}

/**
 * Auto-Tune card: benchmarks the measured MTU profiles (WhiteDNS approach)
 * through the user's real resolvers and persists the fastest one.
 */
@Composable
private fun StormAutoTuneCard(vm: ScanViewModel, fa: Boolean, onOpenVpn: () -> Unit) {
    val s = LocalStrings.current
    val tune by vm.stormTune.collectAsState()
    val appliedId by vm.stormPresetId.collectAsState()
    val presets = if (tune.includeAggressive) {
        com.ahoura.asha_scanner_ip.core.storm.StormAutoTunePresets.all
    } else {
        com.ahoura.asha_scanner_ip.core.storm.StormAutoTunePresets.stable
    }

    CyberCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (fa) s.stormTune else s.stormTune.uppercase(),
                    color = Accent,
                    fontFamily = if (fa) Vazirmatn else ShareTechMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${s.stormTuneActive}: ${appliedId ?: "-"}",
                    color = BlueC,
                    fontFamily = ShareTechMono,
                    fontSize = 9.sp,
                )
            }
            Spacer(Modifier.size(4.dp))
            Text(
                s.stormTuneDesc,
                color = TextSecondaryC,
                fontFamily = if (fa) Vazirmatn else ShareTechMono,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
            Spacer(Modifier.size(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Aggressive profiles toggle
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (tune.includeAggressive) OrangeC.copy(alpha = 0.15f) else SurfaceC)
                        .border(0.5.dp, if (tune.includeAggressive) OrangeC else BorderC, RoundedCornerShape(4.dp))
                        .clickable(enabled = !tune.running) { vm.setStormAggressive(!tune.includeAggressive) }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Text(
                        s.stormTuneAggressive,
                        color = if (tune.includeAggressive) OrangeC else TextSecondaryC,
                        fontFamily = if (fa) Vazirmatn else ShareTechMono,
                        fontSize = 10.sp,
                    )
                }
                Spacer(Modifier.weight(1f))
                // Run / Stop control
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (tune.running) RedC.copy(alpha = 0.12f) else AccentMuted)
                        .border(0.5.dp, if (tune.running) RedC else AccentBorder, RoundedCornerShape(4.dp))
                        .clickable {
                            if (tune.running) vm.stopStormAutoTune() else vm.startStormAutoTune()
                        }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(
                        if (tune.running) {
                            "${s.stormTuneStop} (${tune.done}/${tune.total})"
                        } else {
                            s.stormTuneRun
                        },
                        color = if (tune.running) RedC else Accent,
                        fontFamily = ShareTechMono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                    )
                }
            }

            tune.error?.let { err ->
                Spacer(Modifier.size(6.dp))
                Text(
                    when (err) {
                        "tune_needs_profile" -> s.stormTuneNeedProfile
                        "tune_needs_idle" -> s.stormTuneNeedsIdle
                        else -> err
                    },
                    color = RedC,
                    fontFamily = if (fa) Vazirmatn else ShareTechMono,
                    fontSize = 10.sp,
                )
            }

            if (tune.results.isNotEmpty()) {
                Spacer(Modifier.size(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    presets.forEach { preset ->
                        val kbps = tune.results[preset.id]
                        val isWinner = tune.winnerId == preset.id
                        val isApplied = appliedId == preset.id
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isWinner) Accent.copy(alpha = 0.08f) else Color.Transparent)
                                .clickable(enabled = !tune.running) {
                                    vm.applyStormPreset(preset.id)
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                (if (fa) preset.label else preset.label.uppercase()) +
                                    if (isWinner) " ⚡" else "",
                                color = when {
                                    isWinner -> Accent
                                    isApplied -> BlueC
                                    else -> TextPrimaryC
                                },
                                fontFamily = ShareTechMono,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            val errText = tune.errors[preset.id]
                            Text(
                                when {
                                    kbps != null -> "$kbps KB/s"
                                    tune.results.containsKey(preset.id) && errText != null -> "×"
                                    else -> "·"
                                },
                                color = when {
                                    kbps != null && kbps >= 20 -> AccentDim
                                    kbps != null -> OrangeC
                                    tune.results.containsKey(preset.id) -> RedC
                                    else -> TextMutedC
                                },
                                fontFamily = ShareTechMono,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
                if (tune.winnerId != null && !tune.running) {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        s.stormTuneApplied,
                        color = AccentDim,
                        fontFamily = if (fa) Vazirmatn else ShareTechMono,
                        fontSize = 9.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterChip(
    label: String,
    selected: Boolean,
    fa: Boolean,
    color: Color = Accent,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) color.copy(alpha = 0.15f) else SurfaceC)
            .border(0.5.dp, if (selected) color else BorderC, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            label,
            color = if (selected) color else TextSecondaryC,
            fontFamily = if (fa) Vazirmatn else ShareTechMono,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ResolverRow(
    resolver: DnsProbe.ResolverDef,
    result: DnsProbe.ProbeResult?,
    isFastest: Boolean,
    isApplied: Boolean,
    fa: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val latency = result?.latencyMs
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(if (isFastest) Accent.copy(alpha = 0.08f) else SurfaceC)
            .border(
                0.5.dp,
                when {
                    isFastest -> AccentBorder
                    isApplied -> BlueC.copy(alpha = 0.5f)
                    else -> BorderC
                },
                RoundedCornerShape(5.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Category Tag
        val tagColor = when (resolver.category) {
            DnsProbe.ResolverCategory.ANTI_SANCTION -> GoldC
            DnsProbe.ResolverCategory.CUSTOM -> OrangeC
            DnsProbe.ResolverCategory.GLOBAL -> BlueC
        }
        val tagText = when (resolver.category) {
            DnsProbe.ResolverCategory.ANTI_SANCTION -> "IR"
            DnsProbe.ResolverCategory.CUSTOM -> "CU"
            DnsProbe.ResolverCategory.GLOBAL -> "GL"
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(tagColor.copy(alpha = 0.12f))
                .border(0.5.dp, tagColor.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        ) {
            Text(tagText, color = tagColor, fontFamily = ShareTechMono, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.size(8.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    resolver.name,
                    color = if (isFastest) Accent else TextPrimaryC,
                    fontFamily = ShareTechMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
                if (isFastest) {
                    Spacer(Modifier.size(6.dp))
                    Text("⚡", fontSize = 10.sp)
                }
                if (isApplied) {
                    Spacer(Modifier.size(6.dp))
                    Text("●", color = BlueC, fontSize = 10.sp)
                }
            }
            Text(resolver.ip, color = TextSecondaryC, fontFamily = ShareTechMono, fontSize = 10.sp)
        }

        // Delete button for custom resolvers
        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Delete, null, tint = RedC.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.size(4.dp))
        }

        val msText = when {
            latency != null -> "$latency ms"
            result?.nativeValid == true -> "✓"
            result != null -> "—"
            else -> "·"
        }
        Text(
            msText,
            color = when {
                latency != null && latency < 60 -> AccentDim
                latency != null && latency < 150 -> OrangeC
                latency != null -> RedC
                result?.nativeValid == true -> AccentDim
                result != null -> RedC
                else -> TextFadedC
            },
            fontFamily = ShareTechMono,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
