package com.ahoura.asha_scanner_ip.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ahoura.asha_scanner_ip.core.vpn.VpnManager
import com.ahoura.asha_scanner_ip.core.vpn.VpnStatus
import com.ahoura.asha_scanner_ip.ui.ScanViewModel
import com.ahoura.asha_scanner_ip.ui.components.ConfigManagerBottomSheet
import com.ahoura.asha_scanner_ip.ui.components.CyberAppBar
import com.ahoura.asha_scanner_ip.ui.components.CyberCard
import com.ahoura.asha_scanner_ip.ui.components.CyberToggle
import com.ahoura.asha_scanner_ip.ui.components.LottieSonar
import com.ahoura.asha_scanner_ip.ui.components.Pill
import com.ahoura.asha_scanner_ip.ui.components.SectionLabel
import com.ahoura.asha_scanner_ip.ui.i18n.Lang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalLang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalStrings
import com.ahoura.asha_scanner_ip.ui.i18n.localizeDigits
import com.ahoura.asha_scanner_ip.ui.theme.Accent
import com.ahoura.asha_scanner_ip.ui.theme.AccentBorder
import com.ahoura.asha_scanner_ip.ui.theme.AccentDim
import com.ahoura.asha_scanner_ip.ui.theme.AccentMuted
import com.ahoura.asha_scanner_ip.ui.theme.BlueC
import com.ahoura.asha_scanner_ip.ui.theme.BorderC
import com.ahoura.asha_scanner_ip.ui.theme.OrangeC
import com.ahoura.asha_scanner_ip.ui.theme.RedC
import com.ahoura.asha_scanner_ip.ui.theme.ShareTechMono
import com.ahoura.asha_scanner_ip.ui.theme.SurfaceC
import com.ahoura.asha_scanner_ip.ui.theme.TextMutedC
import com.ahoura.asha_scanner_ip.ui.theme.TextPrimaryC
import com.ahoura.asha_scanner_ip.ui.theme.TextSecondaryC
import com.ahoura.asha_scanner_ip.ui.theme.Vazirmatn
import com.ahoura.asha_scanner_ip.ui.theme.displayFamily
import com.ahoura.asha_scanner_ip.ui.theme.monoFamily

@Composable
fun VpnScreen(
    vm: ScanViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val s = LocalStrings.current
    val lang = LocalLang.current
    val fa = lang == Lang.FA

    val vpnStats by vm.vpnStats.collectAsState()
    val activeProfile by vm.activeProfile.collectAsState()
    val bypassIr by vm.vpnBypassIr.collectAsState()
    val bestScannedIps = vm.state.collectAsState().value.progress.best.filter { it.healthy }

    var showConfigManager by remember { mutableStateOf(false) }
    // True only while a connect attempt is paused behind the notification
    // permission dialog — the entry-time prompt must NOT auto-connect.
    var pendingConnect by remember { mutableStateOf(false) }

    fun needsNotificationPermission() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    // VPN Permission Launcher
    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            activeProfile?.let { vm.startVpn(context, it) }
        }
    }

    fun beginConnect() {
        val profile = activeProfile
        if (profile == null) {
            showConfigManager = true
            return
        }
        if (activity != null) {
            val prepIntent = VpnManager.prepareVpn(activity)
            if (prepIntent != null) {
                vpnLauncher.launch(prepIntent)
            } else {
                vm.startVpn(context, profile)
            }
        } else {
            vm.startVpn(context, profile)
        }
    }

    // Notification permission (Android 13+): without it the foreground-service
    // notification — and its Disconnect action — is hidden. The VPN itself runs
    // fine without it, so a connect attempt resumes whether the user granted or
    // denied.
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        if (pendingConnect) {
            pendingConnect = false
            beginConnect()
        }
    }

    // Ask once when the screen opens (also covers the one-tap "Connect with
    // Clean IP" path, which starts the service before landing here). Read the
    // flag directly in the callback above rather than recomposing, so an
    // instantly-resolved (double-denied) dialog can't drop the resume.
    LaunchedEffect(Unit) {
        if (needsNotificationPermission()) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun toggleVpn() {
        if (vpnStats.status.isActive) {
            vm.stopVpn(context)
        } else {
            if (activeProfile == null) {
                showConfigManager = true
                return
            }
            // Re-ask at the point of use in case the entry-time prompt was
            // denied; after a system double-deny the dialog resolves instantly
            // and we proceed anyway.
            if (needsNotificationPermission()) {
                pendingConnect = true
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                beginConnect()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        CyberAppBar(
            title = s.vpnMode,
            onBack = onBack,
            trailing = {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AccentMuted)
                        .border(0.5.dp, AccentBorder, RoundedCornerShape(4.dp))
                        .clickable { showConfigManager = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.List, null, tint = Accent, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("PROFILES", color = Accent, fontFamily = ShareTechMono, fontSize = 10.sp)
                    }
                }
            }
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.size(4.dp))

            // ---- Center Power Button ----
            VpnPowerButton(
                status = vpnStats.status,
                onClick = ::toggleVpn,
            )

            // ---- Status Readout ----
            val statusColor = when (vpnStats.status) {
                VpnStatus.CONNECTED -> Accent
                VpnStatus.CONNECTING -> OrangeC
                VpnStatus.ERROR -> RedC
                else -> TextMutedC
            }
            val statusText = when (vpnStats.status) {
                VpnStatus.CONNECTED -> s.connected
                VpnStatus.CONNECTING -> s.connecting
                VpnStatus.DISCONNECTING -> s.connecting
                VpnStatus.ERROR -> vpnStats.errorMessage ?: "ERROR"
                VpnStatus.DISCONNECTED -> s.disconnected
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (vpnStats.status == VpnStatus.ERROR) {
                    CyberCard(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        padding = androidx.compose.foundation.layout.PaddingValues(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⚠ ERROR", color = RedC, fontFamily = ShareTechMono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Spacer(Modifier.size(4.dp))
                            Text(
                                vpnStats.errorMessage ?: "Unknown error",
                                color = TextPrimaryC,
                                fontFamily = ShareTechMono,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                            )
                        }
                    }
                } else {
                    Text(
                        statusText,
                        color = statusColor,
                        fontFamily = displayFamily(lang),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = if (fa) 0.sp else 1.5.sp,
                    )
                    if (vpnStats.status.isConnected) {
                        val sec = vpnStats.connectedDurationSeconds
                        val timeStr = "%02d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60)
                        Spacer(Modifier.size(4.dp))
                        Text(
                            "${s.connectedDuration}: $timeStr".localizeDigits(lang),
                            color = TextSecondaryC,
                            fontFamily = ShareTechMono,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            // ---- Active Profile Card ----
            SectionLabel(s.activeConfig)
            CyberCard(
                Modifier
                    .fillMaxWidth()
                    .clickable { showConfigManager = true },
                padding = androidx.compose.foundation.layout.PaddingValues(14.dp)
            ) {
                if (activeProfile != null) {
                    val p = activeProfile!!
                    Column {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                p.name,
                                color = Accent,
                                fontFamily = ShareTechMono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                            Pill(p.proxy.protocol.scheme.uppercase())
                        }
                        Spacer(Modifier.size(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (p.cleanIp != null) "⚡ ${s.cleanIpActive}: ${p.cleanIp}"
                                else "• ${s.directHost}: ${p.proxy.address}",
                                color = if (p.cleanIp != null) BlueC else TextSecondaryC,
                                fontFamily = ShareTechMono,
                                fontSize = 11.sp,
                            )
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(s.noActiveConfig, color = TextPrimaryC, fontFamily = if (fa) Vazirmatn else ShareTechMono, fontSize = 13.sp)
                            Text(s.tapToSelect, color = TextMutedC, fontFamily = if (fa) Vazirmatn else ShareTechMono, fontSize = 10.sp)
                        }
                        Pill("+ ADD")
                    }
                }
            }

            // ---- Latency & Stats Panel ----
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Ping card
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SurfaceC)
                        .border(0.5.dp, BorderC, RoundedCornerShape(6.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val ping = vpnStats.pingMs ?: activeProfile?.pingMs
                        Text(
                            if (ping != null) "${ping}".localizeDigits(lang) else "—",
                            color = if (ping != null && ping < 200) Accent else OrangeC,
                            fontFamily = ShareTechMono,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.size(2.dp))
                        Text(
                            s.testDelay,
                            color = TextMutedC,
                            fontFamily = ShareTechMono,
                            fontSize = 9.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Ping action button
                Box(
                    Modifier
                        .weight(1f)
                        .height(64.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(BlueC.copy(alpha = 0.08f))
                        .border(0.5.dp, BlueC.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .clickable { activeProfile?.let { vm.measurePing(it) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Speed, null, tint = BlueC, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(
                            s.testDelay,
                            color = BlueC,
                            fontFamily = monoFamily(lang),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            // ---- Iranian split tunneling toggle ----
            CyberCard(
                Modifier.fillMaxWidth(),
                padding = androidx.compose.foundation.layout.PaddingValues(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (fa) s.vpnBypassIr else s.vpnBypassIr.uppercase(),
                            color = Accent,
                            fontFamily = displayFamily(lang),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            letterSpacing = if (fa) 0.sp else 0.5.sp,
                        )
                        Spacer(Modifier.size(2.dp))
                        Text(
                            s.vpnBypassIrDesc,
                            color = TextSecondaryC,
                            fontFamily = if (fa) Vazirmatn else ShareTechMono,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                        )
                    }
                    Spacer(Modifier.size(10.dp))
                    CyberToggle(checked = bypassIr, onChange = { vm.setVpnBypassIr(it) })
                }
            }

            // ---- Live traffic (rates are refreshed by the service each second) ----
            if (vpnStats.status.isConnected) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpeedBox("↓", vpnStats.downloadBps, AccentDim, Modifier.weight(1f))
                    SpeedBox("↑", vpnStats.uploadBps, BlueC, Modifier.weight(1f))
                }
            }

            // ---- Fast Clean IP Quick Switcher (if scan results available) ----
            if (activeProfile != null && bestScannedIps.isNotEmpty()) {
                Spacer(Modifier.size(2.dp))
                SectionLabel(s.cleanIpOption)
                CyberCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(4.dp)) {
                        Text(
                            s.cleanIpOptionDesc,
                            color = TextSecondaryC,
                            fontFamily = if (fa) Vazirmatn else ShareTechMono,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                        Spacer(Modifier.size(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Direct Host option
                            val isDirect = activeProfile?.cleanIp == null
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isDirect) AccentMuted else SurfaceC)
                                    .border(0.5.dp, if (isDirect) AccentBorder else BorderC, RoundedCornerShape(4.dp))
                                    .clickable { activeProfile?.let { vm.updateCleanIp(it.id, null) } }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("DIRECT", color = if (isDirect) Accent else TextMutedC, fontFamily = ShareTechMono, fontSize = 10.sp)
                            }

                            // Top 3 Clean IPs
                            bestScannedIps.take(3).forEach { clean ->
                                val isCurrent = activeProfile?.cleanIp == clean.ip
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isCurrent) BlueC.copy(alpha = 0.15f) else SurfaceC)
                                        .border(0.5.dp, if (isCurrent) BlueC else BorderC, RoundedCornerShape(4.dp))
                                        .clickable { activeProfile?.let { vm.updateCleanIp(it.id, clean.ip) } }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        "⚡ ${clean.ip} (${clean.avgLatencyMs.toInt()}ms)",
                                        color = if (isCurrent) BlueC else TextSecondaryC,
                                        fontFamily = ShareTechMono,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.size(16.dp))
        }

        // Config Manager Bottom Sheet
        if (showConfigManager) {
            ConfigManagerBottomSheet(
                vm = vm,
                onDismiss = { showConfigManager = false },
            )
        }
    }
}

@Composable
private fun SpeedBox(arrow: String, bps: Long, color: Color, modifier: Modifier) {
    Box(
        modifier
            .height(64.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceC)
            .border(0.5.dp, BorderC, RoundedCornerShape(6.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(arrow, color = color, fontFamily = ShareTechMono, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(6.dp))
            Text(
                formatSpeed(bps),
                color = if (bps > 0) color else TextMutedC,
                fontFamily = ShareTechMono,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Live per-second rate in compact human units; — when idle. */
private fun formatSpeed(bps: Long): String = when {
    bps >= 1_000_000 -> "%.1f MB/s".format(bps / 1_000_000.0)
    bps >= 1_000 -> "${bps / 1_000} KB/s"
    bps > 0 -> "$bps B/s"
    else -> "—"
}

@Composable
private fun VpnPowerButton(
    status: VpnStatus,
    onClick: () -> Unit,
) {
    val isConnected = status == VpnStatus.CONNECTED
    val isConnecting = status == VpnStatus.CONNECTING || status == VpnStatus.DISCONNECTING

    val primaryColor = when {
        isConnected -> Accent
        isConnecting -> OrangeC
        status == VpnStatus.ERROR -> RedC
        else -> RedC.copy(alpha = 0.8f)
    }

    val transition = rememberInfiniteTransition(label = "power_spin")
    val angle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "spin",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )

    Box(
        Modifier
            .size(170.dp)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Outer rotating neon dashed ring
        if (isConnected || isConnecting) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = pulse; scaleY = pulse }
                    .rotate(angle)
                    .clip(CircleShape)
                    .border(
                        1.5.dp,
                        Brush.sweepGradient(listOf(primaryColor, Color.Transparent, primaryColor)),
                        CircleShape
                    )
            )
        }

        // Middle aura
        Box(
            Modifier
                .size(128.dp)
                .clip(CircleShape)
                .background(primaryColor.copy(alpha = if (isConnected) 0.12f else 0.05f))
                .border(1.dp, primaryColor.copy(alpha = 0.4f), CircleShape)
        )

        // Inner Power button circle
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            primaryColor.copy(alpha = if (isConnected) 0.35f else 0.15f),
                            SurfaceC,
                        )
                    )
                )
                .border(1.5.dp, primaryColor, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PowerSettingsNew,
                contentDescription = "VPN Power",
                tint = primaryColor,
                modifier = Modifier.size(42.dp),
            )
        }
    }
}
