package com.ahoura.asha_scanner_ip.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
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
import com.ahoura.asha_scanner_ip.core.engine.QualityGrade
import com.ahoura.asha_scanner_ip.core.engine.ResultSort
import com.ahoura.asha_scanner_ip.core.model.ScanResult
import com.ahoura.asha_scanner_ip.core.output.ConfigLinkBuilder
import com.ahoura.asha_scanner_ip.core.output.Exporter
import com.ahoura.asha_scanner_ip.ui.ScanViewModel
import com.ahoura.asha_scanner_ip.ui.components.ColoBadge
import com.ahoura.asha_scanner_ip.ui.components.CyberAppBar
import com.ahoura.asha_scanner_ip.ui.components.CyberCard
import com.ahoura.asha_scanner_ip.ui.components.CyberHaptics
import com.ahoura.asha_scanner_ip.ui.components.HumanLatencyPill
import com.ahoura.asha_scanner_ip.ui.components.LottieSonar
import com.ahoura.asha_scanner_ip.ui.components.Pill
import com.ahoura.asha_scanner_ip.ui.components.ScanButton
import com.ahoura.asha_scanner_ip.ui.components.StaggerIn
import com.ahoura.asha_scanner_ip.ui.components.TracerouteDialog
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.ahoura.asha_scanner_ip.ui.theme.OrangeC
import com.ahoura.asha_scanner_ip.ui.theme.RedC
import com.ahoura.asha_scanner_ip.ui.theme.ShareTechMono
import com.ahoura.asha_scanner_ip.ui.theme.SilverC
import com.ahoura.asha_scanner_ip.ui.theme.SurfaceC
import com.ahoura.asha_scanner_ip.ui.theme.TextFadedC
import com.ahoura.asha_scanner_ip.ui.theme.TextMutedC
import com.ahoura.asha_scanner_ip.ui.theme.TextPrimaryC
import com.ahoura.asha_scanner_ip.ui.theme.TextSecondaryC
import com.ahoura.asha_scanner_ip.ui.theme.Vazirmatn
import com.ahoura.asha_scanner_ip.ui.theme.displayFamily
import com.ahoura.asha_scanner_ip.ui.theme.monoFamily
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.ahoura.asha_scanner_ip.core.vpn.VpnManager

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun ResultsScreen(
    vm: ScanViewModel,
    onAgain: () -> Unit,
    onBack: () -> Unit,
    onConnectVpn: ((cleanIp: String) -> Unit)? = null,
    onOpenVpn: (() -> Unit)? = null,
) {
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
    val haptic = LocalHapticFeedback.current
    var snack by remember { mutableStateOf<String?>(null) }
    var traceResult by remember { mutableStateOf<ScanResult?>(null) }
    var actionResult by remember { mutableStateOf<ScanResult?>(null) }
    var showLegend by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (results.isNotEmpty()) {
            CyberHaptics.success(context)
        }
    }

    var pendingWarp by remember { mutableStateOf<Triple<String, Int, String>?>(null) }
    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingWarp?.let { (ip, port, trans) ->
                vm.connectWithScannedIpAsWarp(context, ip, port, trans)
                onOpenVpn?.invoke()
            }
        }
        pendingWarp = null
    }

    fun startWarpTransport(ip: String, port: Int, transport: String) {
        val activity = context.findActivity()
        val prepIntent = activity?.let { VpnManager.prepareVpn(it) }
        if (prepIntent != null) {
            pendingWarp = Triple(ip, port, transport)
            vpnLauncher.launch(prepIntent)
        } else {
            vm.connectWithScannedIpAsWarp(context, ip, port, transport)
            onOpenVpn?.invoke()
        }
    }

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
            CyberAppBar(
                title = s.results,
                onBack = onBack,
                trailing = {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(6.dp))
                            .clickable { showLegend = !showLegend },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (showLegend) Icons.Default.Close else Icons.Default.HelpOutline,
                            contentDescription = "help",
                            tint = Accent, modifier = Modifier.size(20.dp),
                        )
                    }
                }
            )
            Column(Modifier.padding(horizontal = 12.dp)) {
                // ---- Header Telemetry Card ----
                CyberCard(
                    modifier = Modifier.fillMaxWidth(),
                    padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(8.dp).clip(CircleShape).background(Accent)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "${state.progress.found}".localizeDigits(lang),
                                    color = Accent,
                                    fontFamily = ShareTechMono,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                s.healthyIpsTopShown.format(results.size).localizeDigits(lang),
                                color = TextSecondaryC,
                                fontFamily = displayFamily(lang),
                                fontSize = 10.sp
                            )
                        }

                        LottieSonar(Modifier.size(46.dp))

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                if (bestMs > 0) "$bestMs ms".localizeDigits(lang) else "—",
                                color = BlueC,
                                fontFamily = ShareTechMono,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (lang == Lang.FA) s.bestMs else s.bestMs.uppercase(),
                                color = TextMutedC,
                                fontFamily = monoFamily(lang),
                                fontSize = 9.sp
                            )
                        }
                    }
                }

                Spacer8()

                // ---- Quality Legend ----
                if (showLegend) {
                    GradeLegend()
                    Spacer8()
                }

                // ---- One-Tap Connect with Best Clean IP ----
                if (results.isNotEmpty()) {
                    val best = results.first()
                    val bestIp = best.ip
                    CyberCard(
                        modifier = Modifier.fillMaxWidth(),
                        padding = PaddingValues(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Bolt,
                                        contentDescription = null,
                                        tint = Accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "${s.connectWithCleanIp}:",
                                        color = TextSecondaryC,
                                        fontFamily = displayFamily(lang),
                                        fontSize = 11.sp
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        bestIp,
                                        color = Accent,
                                        fontFamily = ShareTechMono,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Pill("${best.avgLatencyMs.toInt()}ms")
                            }

                            HumanLatencyPill(latencyMs = best.avgLatencyMs.toInt(), modifier = Modifier.fillMaxWidth())

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ActionBtn("⚡ Asha PoW", Accent, Accent.copy(alpha = 0.12f), AccentBorder, Modifier.weight(1f)) {
                                    startWarpTransport(best.ip, best.port, "pow")
                                }
                                ActionBtn("🚀 MASQUE", BlueC, BlueC.copy(alpha = 0.12f), BlueC.copy(alpha = 0.4f), Modifier.weight(1f)) {
                                    startWarpTransport(best.ip, best.port, "masque")
                                }
                                ActionBtn("🛡️ WARP", OrangeC, OrangeC.copy(alpha = 0.12f), OrangeC.copy(alpha = 0.4f), Modifier.weight(1f)) {
                                    startWarpTransport(best.ip, best.port, "wireguard")
                                }
                                if (proxy != null && onConnectVpn != null) {
                                    ActionBtn("⚙ CUSTOM", GoldC, GoldC.copy(alpha = 0.12f), GoldC.copy(alpha = 0.4f), Modifier.weight(1f)) {
                                        onConnectVpn(bestIp)
                                    }
                                }
                            }
                        }
                    }
                    Spacer8()
                }

                // ---- Local Subscription Link ----
                state.subUrl?.let { url ->
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                            .background(AccentMuted.copy(alpha = 0.1f))
                            .border(0.5.dp, AccentBorder.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .clickable {
                                clipboard.setText(AnnotatedString(url))
                                snack = s.localSub
                            }.padding(12.dp)
                    ) {
                        Column {
                            Text(s.localSub, color = Accent, fontFamily = monoFamily(lang), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text(url, color = TextPrimaryC, fontFamily = ShareTechMono, fontSize = 12.sp)
                            Text(s.localSubDesc, color = TextMutedC, fontFamily = monoFamily(lang), fontSize = 9.sp)
                        }
                    }
                    Spacer8()
                }

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
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(results, key = { _, r -> r.endpoint }) { index, r ->
                    val rank = index + 1
                    StaggerIn(rank.coerceAtMost(12)) {
                        ResultRowFull(
                            rank = rank,
                            r = r,
                            hasConfig = proxy != null,
                            onAction = { actionResult = r },
                            onGradeClick = { showLegend = true },
                            onCopy = {
                                CyberHaptics.copySuccess(context, haptic)
                                if (proxy != null) {
                                    clipboard.setText(AnnotatedString(ConfigLinkBuilder.withAddress(proxy, r.ip, r.port)))
                                    snack = s.copiedConfigFor.format(r.ip)
                                } else {
                                    clipboard.setText(AnnotatedString(r.ip)); snack = s.copiedIp.format(r.ip)
                                }
                            }
                        )
                    }
                }
                item { Spacer8() }
            }

            Column(Modifier.padding(12.dp)) {
                ScanButton(text = s.scanAgain, icon = Icons.Filled.Refresh, onClick = onAgain)
            }
        }

        traceResult?.let { r ->
            TracerouteDialog(ip = r.ip, colo = r.colo, onDismiss = { traceResult = null })
        }

        actionResult?.let { r ->
            CleanIpActionDialog(
                r = r,
                hasConfig = proxy != null,
                onConnectWarp = {
                    startWarpTransport(r.ip, r.port, "wireguard")
                    actionResult = null
                },
                onConnectMasque = {
                    startWarpTransport(r.ip, r.port, "masque")
                    actionResult = null
                },
                onConnectCustom = if (proxy != null && onConnectVpn != null) {
                    {
                        actionResult = null
                        onConnectVpn(r.ip)
                    }
                } else null,
                onPinEndpoint = {
                    vm.setManualEndpoint("${r.ip}:${r.port}")
                    snack = s.endpointPinnedSuccess
                    actionResult = null
                },
                onTrace = {
                    actionResult = null
                    traceResult = r
                },
                onCopy = {
                    if (proxy != null) {
                        clipboard.setText(AnnotatedString(ConfigLinkBuilder.withAddress(proxy, r.ip, r.port)))
                        snack = s.copiedConfigFor.format(r.ip)
                    } else {
                        clipboard.setText(AnnotatedString(r.ip))
                        snack = s.copiedIp.format(r.ip)
                    }
                    actionResult = null
                },
                onDismiss = { actionResult = null }
            )
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
private fun ResultRowFull(
    rank: Int,
    r: ScanResult,
    hasConfig: Boolean,
    onAction: () -> Unit,
    onGradeClick: () -> Unit,
    onCopy: () -> Unit
) {
    val s = LocalStrings.current
    val grade = com.ahoura.asha_scanner_ip.core.engine.QualityEvaluator.evaluate(r)
    val rankColor = when (rank) {
        1 -> GoldC
        2, 3 -> SilverC
        else -> TextFadedC
    }
    val ipColor = if (rank <= 3) Accent else TextPrimaryC

    CyberCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAction),
        padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Row 1: Rank, Grade, IP, Colo, Copy button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rank Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(rankColor.copy(alpha = 0.12f))
                        .border(0.5.dp, rankColor.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "#${rank.toString().padStart(2, '0')}",
                        color = rankColor,
                        fontFamily = ShareTechMono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Quality Grade Circle
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(gradeColor(grade))
                        .clickable(onClick = onGradeClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        grade.label,
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ShareTechMono
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Clean IP Address
                Text(
                    r.ip,
                    color = ipColor,
                    fontFamily = ShareTechMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )

                // Colo badge (e.g. FRA, AMS)
                ColoBadge(r.colo)

                Spacer(Modifier.width(6.dp))

                // Copy Button with proper tap target
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SurfaceC)
                        .clickable(onClick = onCopy),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = if (hasConfig) "copy config" else "copy IP",
                        tint = Accent,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Row 2: Metrics Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(latency(r.avgLatencyMs.toInt()))
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${r.avgLatencyMs.toInt()} ms",
                        color = latency(r.avgLatencyMs.toInt()),
                        fontFamily = ShareTechMono,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    "• loss ${(r.loss * 100).toInt()}%",
                    color = if (r.loss > 0) OrangeC else TextMutedC,
                    fontFamily = ShareTechMono,
                    fontSize = 10.sp
                )

                if (r.speedTested && r.throughputBytesPerSec > 0) {
                    Text(
                        "• ${String.format(java.util.Locale.US, "%.1f", r.throughputMbps)} Mbps",
                        color = AccentDim,
                        fontFamily = ShareTechMono,
                        fontSize = 10.sp
                    )
                }

                if (r.passRate < 1.0) {
                    Text(
                        "• pass ${(r.passRate * 100).toInt()}%",
                        color = OrangeC,
                        fontFamily = ShareTechMono,
                        fontSize = 10.sp
                    )
                }
            }

            // Row 3: Quality Badges
            val showBadges = rank == 1 ||
                com.ahoura.asha_scanner_ip.core.engine.QualityEvaluator.isGoodForGaming(r) ||
                com.ahoura.asha_scanner_ip.core.engine.QualityEvaluator.isGoodForStreaming(r) ||
                com.ahoura.asha_scanner_ip.core.engine.QualityEvaluator.isStable(r)

            if (showBadges) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (rank == 1) {
                        Pill(
                            s.bestMatchForYou,
                            textColor = GoldC,
                            borderColor = GoldC.copy(alpha = 0.5f),
                            bgColor = GoldC.copy(alpha = 0.12f)
                        )
                    }
                    if (com.ahoura.asha_scanner_ip.core.engine.QualityEvaluator.isGoodForGaming(r)) {
                        QualityBadge(s.gaming, GoldC)
                    }
                    if (com.ahoura.asha_scanner_ip.core.engine.QualityEvaluator.isGoodForStreaming(r)) {
                        QualityBadge(s.streaming, Accent)
                    }
                    if (com.ahoura.asha_scanner_ip.core.engine.QualityEvaluator.isStable(r)) {
                        QualityBadge(s.stability, BlueC)
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityBadge(label: String, color: Color) {
    val lang = LocalLang.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.12f))
            .border(0.5.dp, color.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            label,
            color = color,
            fontFamily = if (lang == Lang.FA) Vazirmatn else ShareTechMono,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun gradeColor(grade: com.ahoura.asha_scanner_ip.core.engine.QualityGrade): Color = when (grade) {
    com.ahoura.asha_scanner_ip.core.engine.QualityGrade.S -> GoldC
    com.ahoura.asha_scanner_ip.core.engine.QualityGrade.A -> Accent
    com.ahoura.asha_scanner_ip.core.engine.QualityGrade.B -> BlueC
    com.ahoura.asha_scanner_ip.core.engine.QualityGrade.C -> com.ahoura.asha_scanner_ip.ui.theme.OrangeC
    com.ahoura.asha_scanner_ip.core.engine.QualityGrade.F -> RedC
}

private fun latency(ms: Int): Color = when {
    ms <= 0 -> TextFadedC
    ms < 100 -> Accent
    ms <= 200 -> com.ahoura.asha_scanner_ip.ui.theme.OrangeC
    else -> RedC
}

@Composable private fun Spacer8() = Box(Modifier.size(8.dp))

@Composable
private fun GradeLegend() {
    val s = LocalStrings.current
    val lang = LocalLang.current
    val grades = listOf(
        QualityGrade.S to s.gradeS,
        QualityGrade.A to s.gradeA,
        QualityGrade.B to s.gradeB,
        QualityGrade.C to s.gradeC
    )

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceC.copy(alpha = 0.6f))
            .border(0.5.dp, BorderC, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Text(
            s.gradeLegend, color = Accent, fontFamily = displayFamily(lang),
            fontWeight = FontWeight.Bold, fontSize = 10.sp,
            letterSpacing = if (lang == Lang.FA) 0.sp else 1.sp
        )
        Box(Modifier.size(8.dp))
        grades.forEach { (g, desc) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 3.dp)
            ) {
                Box(
                    Modifier.size(18.dp).clip(CircleShape).background(gradeColor(g)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(g.label, color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = ShareTechMono)
                }
                Box(Modifier.size(10.dp))
                Text(
                    desc, color = TextSecondaryC, fontSize = 11.sp,
                    fontFamily = monoFamily(lang),
                    lineHeight = 14.sp
                )
            }
        }

        Box(Modifier.size(8.dp))
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(BorderC))
        Box(Modifier.size(8.dp))

        listOf(
            s.gaming to (s.gamingDesc to GoldC),
            s.streaming to (s.streamingDesc to Accent),
            s.stability to (s.stabilityDesc to BlueC)
        ).forEach { (label, data) ->
            val (desc, color) = data
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(color.copy(alpha = 0.1f))
                        .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(label, color = color, fontFamily = ShareTechMono, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.size(10.dp))
                Text(
                    desc, color = TextSecondaryC, fontSize = 11.sp,
                    fontFamily = monoFamily(lang),
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun CleanIpActionDialog(
    r: ScanResult,
    hasConfig: Boolean,
    onConnectWarp: () -> Unit,
    onConnectMasque: () -> Unit,
    onConnectCustom: (() -> Unit)?,
    onPinEndpoint: () -> Unit,
    onTrace: () -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    val lang = LocalLang.current
    val fa = lang == Lang.FA

    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceC)
                .border(1.dp, BorderC, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Header
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "⚡ ${r.ip}:${r.port}",
                                color = Accent,
                                fontFamily = ShareTechMono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.size(6.dp))
                            Pill(r.colo ?: "CF")
                        }
                        Text(
                            "${r.avgLatencyMs.toInt()}ms | loss ${(r.loss * 100).toInt()}%",
                            color = TextSecondaryC,
                            fontFamily = ShareTechMono,
                            fontSize = 10.sp
                        )
                    }
                    Icon(
                        Icons.Filled.Close,
                        null,
                        tint = TextMutedC,
                        modifier = Modifier.size(20.dp).clickable { onDismiss() }
                    )
                }

                Spacer(Modifier.size(4.dp))

                // Action items
                ActionDialogItem(
                    icon = Icons.Filled.Bolt,
                    iconTint = Accent,
                    title = s.connectWarp,
                    subtitle = "WireGuard Tunnel",
                    onClick = onConnectWarp
                )

                ActionDialogItem(
                    icon = Icons.Filled.Shield,
                    iconTint = BlueC,
                    title = s.connectMasque,
                    subtitle = "HTTP/3 CONNECT-IP",
                    onClick = onConnectMasque
                )

                if (hasConfig && onConnectCustom != null) {
                    ActionDialogItem(
                        icon = Icons.Filled.VpnKey,
                        iconTint = GoldC,
                        title = s.connectCustom,
                        subtitle = "VLESS / Trojan / StormDNS",
                        onClick = onConnectCustom
                    )
                }

                ActionDialogItem(
                    icon = Icons.Filled.PushPin,
                    iconTint = AccentDim,
                    title = s.pinAsWarpEndpoint,
                    subtitle = "Save as Cloudflare manual endpoint",
                    onClick = onPinEndpoint
                )

                ActionDialogItem(
                    icon = Icons.Filled.Speed,
                    iconTint = TextSecondaryC,
                    title = "Traceroute",
                    subtitle = "Inspect hop latency and BGP route",
                    onClick = onTrace
                )

                ActionDialogItem(
                    icon = Icons.Filled.ContentCopy,
                    iconTint = TextMutedC,
                    title = s.copy,
                    subtitle = if (hasConfig) "Config link" else "IP address",
                    onClick = onCopy
                )
            }
        }
    }
}

@Composable
private fun ActionDialogItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val lang = LocalLang.current
    val fa = lang == Lang.FA
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceC)
            .border(0.5.dp, BorderC, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = TextPrimaryC,
                fontFamily = if (fa) Vazirmatn else ShareTechMono,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Text(
                subtitle,
                color = TextMutedC,
                fontFamily = ShareTechMono,
                fontSize = 9.sp
            )
        }
    }
}

