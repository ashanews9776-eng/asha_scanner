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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.ahoura.asha_scanner_ip.core.vpn.VpnManager
import com.ahoura.asha_scanner_ip.core.vpn.VpnStatus
import com.ahoura.asha_scanner_ip.ui.ScanViewModel
import androidx.compose.foundation.layout.PaddingValues
import com.ahoura.asha_scanner_ip.ui.components.ConfigManagerBottomSheet
import com.ahoura.asha_scanner_ip.ui.components.CyberAppBar
import com.ahoura.asha_scanner_ip.ui.components.CyberCard
import com.ahoura.asha_scanner_ip.ui.components.CyberToggle
import com.ahoura.asha_scanner_ip.ui.components.KvInput
import com.ahoura.asha_scanner_ip.ui.components.LottieSonar
import com.ahoura.asha_scanner_ip.ui.components.Pill
import com.ahoura.asha_scanner_ip.ui.components.SectionLabel
import com.ahoura.asha_scanner_ip.ui.components.ZeroTrustBottomSheet
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
import com.ahoura.asha_scanner_ip.core.guard.CoreConfig
import com.ahoura.asha_scanner_ip.core.guard.IpFormatter
import com.ahoura.asha_scanner_ip.core.guard.PsiphonRegions
import com.ahoura.asha_scanner_ip.core.guard.TorManager
import com.ahoura.asha_scanner_ip.core.guard.TorRegions

@Composable
fun VpnScreen(
    vm: ScanViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val clipboard = LocalClipboardManager.current
    val s = LocalStrings.current
    val lang = LocalLang.current
    val fa = lang == Lang.FA

    val vpnStats by vm.vpnStats.collectAsState()
    val activeProfile by vm.activeProfile.collectAsState()
    val bypassIr by vm.vpnBypassIr.collectAsState()
    val bestScannedIps = vm.state.collectAsState().value.progress.best.filter { it.healthy }

    val selectedTransport by vm.selectedTransport.collectAsState()
    val tunnelMode by vm.tunnelMode.collectAsState()
    val lanSharing by vm.lanSharing.collectAsState()
    val psiphonRegion by vm.psiphonRegion.collectAsState()
    val psiphonChained by vm.psiphonChained.collectAsState()
    val torMode by vm.torMode.collectAsState()
    val torRegion by vm.torRegion.collectAsState()
    val torChained by vm.torChained.collectAsState()
    val chainOuterMode by vm.chainOuterMode.collectAsState()
    val chainOuterModeTor by vm.chainOuterModeTor.collectAsState()
    val killSwitch by vm.killSwitch.collectAsState()
    val masqueTransport by vm.masqueTransport.collectAsState()
    val upstreamProxy by vm.upstreamProxy.collectAsState()
    val wiwOuter by vm.wiwOuter.collectAsState()
    val wiwInner by vm.wiwInner.collectAsState()
    val manualEndpoint by vm.manualEndpoint.collectAsState()
    val zeroTrustToken by vm.zeroTrustToken.collectAsState()
    val zeroTrustTeam by vm.zeroTrustTeam.collectAsState()
    val zeroTrustClientId by vm.zeroTrustClientId.collectAsState()

    var showConfigManager by remember { mutableStateOf(false) }
    var showZeroTrustSheet by remember { mutableStateOf(false) }
    var showPsiphonCountryDialog by remember { mutableStateOf(false) }
    var showTorCountryDialog by remember { mutableStateOf(false) }
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
            if (selectedTransport == "custom") {
                activeProfile?.let { vm.startVpn(context, it) }
            } else {
                vm.startVpnTransport(context, selectedTransport)
            }
        }
    }

    fun beginConnect() {
        if (selectedTransport == "custom") {
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
        } else {
            if (activity != null) {
                val prepIntent = VpnManager.prepareVpn(activity)
                if (prepIntent != null) {
                    vpnLauncher.launch(prepIntent)
                } else {
                    vm.startVpnTransport(context, selectedTransport)
                }
            } else {
                vm.startVpnTransport(context, selectedTransport)
            }
        }
    }

    // Notification permission (Android 13+)
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        if (pendingConnect) {
            pendingConnect = false
            beginConnect()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && needsNotificationPermission()) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun toggleVpn() {
        if (vpnStats.status.isActive) {
            vm.stopVpn(context)
        } else {
            if (selectedTransport == "custom" && activeProfile == null) {
                showConfigManager = true
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && needsNotificationPermission()) {
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
                        Icon(Icons.AutoMirrored.Filled.List, null, tint = Accent, modifier = Modifier.size(13.dp))
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
            Spacer(Modifier.size(2.dp))

            // ---- Transport Rail Chips ----
            val transports = listOf(
                "wireguard" to s.transportWireguard,
                "masque" to s.transportMasque,
                "gool" to s.transportGool,
                "psiphon" to s.transportPsiphon,
                "tor" to s.transportTor,
                "shard" to s.transportShard,
                "custom" to s.transportCustom,
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                transports.forEach { (proto, label) ->
                    val isSelected = selectedTransport == proto
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) AccentMuted else SurfaceC)
                            .border(
                                1.dp,
                                if (isSelected) Accent else BorderC,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                vm.setSelectedTransport(proto)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            color = if (isSelected) Accent else TextSecondaryC,
                            fontFamily = if (fa) Vazirmatn else ShareTechMono,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

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
                            vpnStats.detailMessage?.let { detail ->
                                if (detail.isNotBlank() && detail != vpnStats.errorMessage) {
                                    Spacer(Modifier.size(4.dp))
                                    Text(
                                        detail.take(300),
                                        color = TextSecondaryC,
                                        fontFamily = ShareTechMono,
                                        fontSize = 9.sp,
                                        lineHeight = 12.sp,
                                    )
                                }
                            }
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

                    // Detail status line from native engine or Tor poller
                    vpnStats.detailMessage?.let { detail ->
                        if (detail.isNotBlank() && vpnStats.status != VpnStatus.DISCONNECTED) {
                            Spacer(Modifier.size(4.dp))
                            Text(
                                detail,
                                color = TextSecondaryC,
                                fontFamily = ShareTechMono,
                                fontSize = 11.sp,
                            )
                        }
                    }

                    // Progress bar for bootstrap (Tor/SHARD)
                    if (vpnStats.status == VpnStatus.CONNECTING && vpnStats.progress in 0..100) {
                        Spacer(Modifier.size(6.dp))
                        Column(
                            Modifier.fillMaxWidth(0.7f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(
                                progress = { vpnStats.progress / 100f },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = Accent,
                                trackColor = SurfaceC,
                            )
                            Spacer(Modifier.size(2.dp))
                            Text(
                                "${vpnStats.progress}%".localizeDigits(lang),
                                color = Accent,
                                fontFamily = ShareTechMono,
                                fontSize = 10.sp,
                            )
                        }
                    }

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

            // ---- Telemetry Stats Panel (Ping, Speed, Exit IP) ----
            // IntrinsicSize.Min stretches every sibling to the tallest box, so the
            // ping readout, the speed/exit card and the ping action button always
            // share one height instead of each hugging its own content.
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Ping card
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(SurfaceC)
                        .border(0.5.dp, BorderC, RoundedCornerShape(6.dp))
                        .padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val ping = vpnStats.pingMs ?: activeProfile?.pingMs
                        Text(
                            if (ping != null) "${ping}".localizeDigits(lang) else "—",
                            color = if (ping != null && ping < 200) Accent else OrangeC,
                            fontFamily = ShareTechMono,
                            fontSize = 18.sp,
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

                // Speed / Exit IP card
                Box(
                    Modifier
                        .weight(1.4f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(SurfaceC)
                        .border(0.5.dp, BorderC, RoundedCornerShape(6.dp))
                        .padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val exit = vpnStats.exitIp
                        val country = vpnStats.country
                        if (exit != null) {
                            val flag = if (country != null) IpFormatter.flag(country) + " " else ""
                            Text(
                                "$flag$exit",
                                color = BlueC,
                                fontFamily = ShareTechMono,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.size(2.dp))
                            Text(
                                s.exitIpLabel,
                                color = TextMutedC,
                                fontFamily = ShareTechMono,
                                fontSize = 9.sp,
                                letterSpacing = 1.sp
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("↓ ${formatSpeed(vpnStats.downloadBps)}", color = AccentDim, fontFamily = ShareTechMono, fontSize = 11.sp)
                                Spacer(Modifier.size(6.dp))
                                Text("↑ ${formatSpeed(vpnStats.uploadBps)}", color = BlueC, fontFamily = ShareTechMono, fontSize = 11.sp)
                            }
                            Spacer(Modifier.size(2.dp))
                            Text(
                                s.speedLabel,
                                color = TextMutedC,
                                fontFamily = ShareTechMono,
                                fontSize = 9.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                // Ping action button
                Box(
                    Modifier
                        .weight(0.9f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(BlueC.copy(alpha = 0.08f))
                        .border(0.5.dp, BlueC.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .clickable {
                            if (selectedTransport == "custom") {
                                activeProfile?.let { vm.measurePing(it) }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Speed, null, tint = BlueC, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(
                            s.testDelay,
                            color = BlueC,
                            fontFamily = monoFamily(lang),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            // ---- Contextual Transport Settings Card ----
            when (selectedTransport) {
                "wireguard" -> {
                    SectionLabel(s.transportWireguard)
                    CyberCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Bolt, null, tint = Accent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(s.transportWireguardDesc, color = TextPrimaryC, fontFamily = if (fa) Vazirmatn else ShareTechMono, fontSize = 12.sp)
                            }
                            Spacer(Modifier.size(4.dp))
                            Text(
                                "Direct UDP tunnel to Cloudflare Edge using modern Noise protocol handshakes.",
                                color = TextSecondaryC,
                                fontFamily = ShareTechMono,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                "masque" -> {
                    SectionLabel(s.transportMasque)
                    CyberCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.transportMasqueDesc, color = TextPrimaryC, fontFamily = if (fa) Vazirmatn else ShareTechMono, fontSize = 12.sp)
                                    Text("CONNECT-IP censorship bypass", color = TextSecondaryC, fontFamily = ShareTechMono, fontSize = 10.sp)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (masqueTransport == "h3") AccentMuted else SurfaceC)
                                            .border(0.5.dp, if (masqueTransport == "h3") AccentBorder else BorderC, RoundedCornerShape(4.dp))
                                            .clickable { vm.setMasqueTransport("h3") }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("H3 (QUIC)", color = if (masqueTransport == "h3") Accent else TextMutedC, fontFamily = ShareTechMono, fontSize = 10.sp)
                                    }
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (masqueTransport == "h2") AccentMuted else SurfaceC)
                                            .border(0.5.dp, if (masqueTransport == "h2") AccentBorder else BorderC, RoundedCornerShape(4.dp))
                                            .clickable { vm.setMasqueTransport("h2") }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("H2 (TCP)", color = if (masqueTransport == "h2") Accent else TextMutedC, fontFamily = ShareTechMono, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                "gool" -> {
                    SectionLabel(s.transportGool)
                    CyberCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Shield, null, tint = Accent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(s.transportGoolDesc, color = TextPrimaryC, fontFamily = if (fa) Vazirmatn else ShareTechMono, fontSize = 12.sp)
                            }
                            Text(
                                s.wiwDesc,
                                color = TextSecondaryC,
                                fontFamily = if (fa) Vazirmatn else ShareTechMono,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                            HorizontalDivider(color = BorderC, thickness = 0.5.dp)
                            KvInput(
                                label = s.wiwOuter,
                                value = wiwOuter,
                                onValueChange = { vm.setWiwOuter(it) },
                                placeholder = "IP:Port (auto fallback)",
                            )
                            KvInput(
                                label = s.wiwInner,
                                value = wiwInner,
                                onValueChange = { vm.setWiwInner(it) },
                                placeholder = "IP:Port (auto fallback)",
                            )
                        }
                    }
                }

                "psiphon" -> {
                    SectionLabel(s.transportPsiphon)
                    CyberCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Chained toggle
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.psiphonOverWarp, color = Accent, fontFamily = displayFamily(lang), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(s.psiphonOverWarpDesc, color = TextSecondaryC, fontFamily = if (fa) Vazirmatn else ShareTechMono, fontSize = 10.sp)
                                }
                                CyberToggle(checked = psiphonChained, onChange = { vm.setPsiphonChained(it) })
                            }

                            HorizontalDivider(color = BorderC, thickness = 0.5.dp)

                            // Outer transport ladder selector (if chained)
                            if (psiphonChained) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(s.outerTransport, color = TextPrimaryC, fontFamily = if (fa) Vazirmatn else ShareTechMono, fontSize = 11.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf("auto" to "Auto", "masque" to "MASQUE", "wireguard" to "WG", "gool" to "WoW").forEach { (mode, label) ->
                                            val isCur = chainOuterMode == mode
                                            Box(
                                                Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(if (isCur) AccentMuted else SurfaceC)
                                                    .border(0.5.dp, if (isCur) AccentBorder else BorderC, RoundedCornerShape(4.dp))
                                                    .clickable { vm.setChainOuterMode(mode) }
                                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                            ) {
                                                Text(label, color = if (isCur) Accent else TextMutedC, fontFamily = ShareTechMono, fontSize = 9.sp)
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider(color = BorderC, thickness = 0.5.dp)
                            }

                            // Egress Country Picker
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { showPsiphonCountryDialog = true },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(s.exitCountry, color = TextPrimaryC, fontFamily = if (fa) Vazirmatn else ShareTechMono, fontSize = 11.sp)
                                    val regionName = if (psiphonRegion == "auto") s.autoCountry else PsiphonRegions.label(psiphonRegion)
                                    Text(regionName, color = BlueC, fontFamily = if (fa) Vazirmatn else ShareTechMono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Pill(if (psiphonRegion == "auto") "AUTO" else psiphonRegion)
                            }
                        }
                    }
                }

                "tor" -> {
                    SectionLabel(s.transportTor)
                    CyberCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Chained toggle
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.torOverWarp, color = Accent, fontFamily = displayFamily(lang), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(s.torOverWarpDesc, color = TextSecondaryC, fontFamily = if (fa) Vazirmatn else ShareTechMono, fontSize = 10.sp)
                                }
                                CyberToggle(checked = torChained, onChange = { vm.setTorChained(it) })
                            }

                            HorizontalDivider(color = BorderC, thickness = 0.5.dp)

                            // Bridge mode chips
                            Column {
                                Text(s.torBridgeMode, color = TextPrimaryC, fontFamily = if (fa) Vazirmatn else ShareTechMono, fontSize = 11.sp)
                                Spacer(Modifier.size(6.dp))
                                Row(
                                    Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(
                                        "auto" to "Auto",
                                        "direct" to "Direct",
                                        "meek" to "Meek",
                                        "obfs4" to "obfs4",
                                        "snowflake" to "Snowflake"
                                    ).forEach { (mode, label) ->
                                        val isCur = torMode == mode
                                        Box(
                                            Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (isCur) AccentMuted else SurfaceC)
                                                .border(0.5.dp, if (isCur) AccentBorder else BorderC, RoundedCornerShape(4.dp))
                                                .clickable { vm.setTorMode(mode) }
                                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                        ) {
                                            Text(label, color = if (isCur) Accent else TextSecondaryC, fontFamily = ShareTechMono, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = BorderC, thickness = 0.5.dp)

                            // Exit Country Picker
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { showTorCountryDialog = true },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(s.exitCountry, color = TextPrimaryC, fontFamily = if (fa) Vazirmatn else ShareTechMono, fontSize = 11.sp)
                                    val regionName = if (torRegion == "auto") s.autoCountry else TorRegions.label(torRegion)
                                    Text(regionName, color = BlueC, fontFamily = if (fa) Vazirmatn else ShareTechMono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Pill(if (torRegion == "auto") "AUTO" else torRegion)
                            }
                        }
                    }
                }

                "shard" -> {
                    SectionLabel(s.transportShard)
                    CyberCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Hub, null, tint = Accent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(s.transportShardDesc, color = TextPrimaryC, fontFamily = if (fa) Vazirmatn else ShareTechMono, fontSize = 12.sp)
                            }
                            Spacer(Modifier.size(4.dp))
                            Text(
                                "Managed pool of resilient Cloudflare nodes running over Xray with client TLS packet fragmentation to defeat strict SNI filtering.",
                                color = TextSecondaryC,
                                fontFamily = ShareTechMono,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                "custom" -> {
                    // Custom scanned profile card
                    SectionLabel(s.activeConfig)
                    CyberCard(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showConfigManager = true },
                        padding = androidx.compose.foundation.layout.PaddingValues(12.dp)
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
                                    val tag = if (p.proxy.protocol == com.ahoura.asha_scanner_ip.core.model.Protocol.STORMDNS) "DNS TUNNEL" else p.proxy.protocol.scheme.uppercase()
                                    Pill(tag)
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

                    // Fast Clean IP Quick Switcher (if scan results available)
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
                }
            }

            // ---- Cloudflare Zero Trust Enrollment Card ----
            val isEnrolledZt = zeroTrustToken.isNotBlank() || zeroTrustClientId.isNotBlank()
            SectionLabel(s.zeroTrustTitle)
            CyberCard(
                Modifier
                    .fillMaxWidth()
                    .clickable { showZeroTrustSheet = true },
                padding = PaddingValues(10.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Shield,
                            null,
                            tint = if (isEnrolledZt) Accent else BlueC,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Column {
                            Text(
                                s.zeroTrustTitle,
                                color = if (isEnrolledZt) Accent else TextPrimaryC,
                                fontFamily = displayFamily(lang),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (isEnrolledZt) "${s.zeroTrustEnrolled} (${zeroTrustTeam.ifBlank { "Team" }})" else s.zeroTrustDesc,
                                color = TextSecondaryC,
                                fontFamily = if (fa) Vazirmatn else ShareTechMono,
                                fontSize = 10.sp,
                                maxLines = 1,
                            )
                        }
                    }
                    Pill(if (isEnrolledZt) "ACTIVE" else "SETUP")
                }
            }

            // ---- Tunnel Mode & LAN Sharing ----
            SectionLabel(s.tunnelType)
            CyberCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Mode selector row
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isVpn = tunnelMode == CoreConfig.TUNNEL_MODE_VPN
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isVpn) AccentMuted else SurfaceC)
                                .border(0.5.dp, if (isVpn) AccentBorder else BorderC, RoundedCornerShape(6.dp))
                                .clickable { vm.setTunnelMode(CoreConfig.TUNNEL_MODE_VPN) }
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.VpnKey, null, tint = if (isVpn) Accent else TextMutedC, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text(s.tunnelTypeVpn, color = if (isVpn) Accent else TextSecondaryC, fontFamily = if (fa) Vazirmatn else ShareTechMono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        val isProxy = tunnelMode == CoreConfig.TUNNEL_MODE_PROXY
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isProxy) AccentMuted else SurfaceC)
                                .border(0.5.dp, if (isProxy) AccentBorder else BorderC, RoundedCornerShape(6.dp))
                                .clickable { vm.setTunnelMode(CoreConfig.TUNNEL_MODE_PROXY) }
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.Router, null, tint = if (isProxy) Accent else TextMutedC, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text(s.tunnelTypeProxy, color = if (isProxy) Accent else TextSecondaryC, fontFamily = if (fa) Vazirmatn else ShareTechMono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Text(
                        if (tunnelMode == CoreConfig.TUNNEL_MODE_VPN) s.tunnelTypeVpnDesc else s.tunnelTypeProxyDesc,
                        color = TextSecondaryC,
                        fontFamily = if (fa) Vazirmatn else ShareTechMono,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )

                    // LAN Sharing & Hotspot options
                    HorizontalDivider(color = BorderC, thickness = 0.5.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.lanSharing, color = Accent, fontFamily = displayFamily(lang), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(s.lanSharingDesc, color = TextSecondaryC, fontFamily = if (fa) Vazirmatn else ShareTechMono, fontSize = 10.sp)
                        }
                        CyberToggle(checked = lanSharing, onChange = { vm.setLanSharing(it) })
                    }

                    // If LAN sharing enabled, show local IP & copy endpoint
                    if (lanSharing) {
                        val localIp = vpnStats.localLanIp ?: CoreConfig.localNetworkAddress(context) ?: "127.0.0.1"
                        val socksPort = CoreConfig.proxyListenPort(context)
                        val httpPort = CoreConfig.HTTP_PROXY_PORT
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(BlueC.copy(alpha = 0.08f))
                                .border(0.5.dp, BlueC.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("SOCKS5: $localIp:$socksPort", color = BlueC, fontFamily = ShareTechMono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("HTTP:   $localIp:$httpPort", color = BlueC, fontFamily = ShareTechMono, fontSize = 11.sp)
                                }
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(SurfaceC)
                                        .border(0.5.dp, BorderC, RoundedCornerShape(4.dp))
                                        .clickable {
                                            clipboard.setText(AnnotatedString("$localIp:$socksPort"))
                                        }
                                        .padding(6.dp)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, null, tint = BlueC, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }

            // ---- Routing & Security Settings (Smart Split & Kill Switch) ----
            SectionLabel(s.advancedOptions)
            CyberCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Bypass Iran sites (Smart Split)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.smartSplit, color = Accent, fontFamily = displayFamily(lang), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(s.smartSplitDesc, color = TextSecondaryC, fontFamily = if (fa) Vazirmatn else ShareTechMono, fontSize = 10.sp)
                        }
                        CyberToggle(checked = bypassIr, onChange = { vm.setVpnBypassIr(it) })
                    }

                    HorizontalDivider(color = BorderC, thickness = 0.5.dp)

                    // Kill Switch
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.killSwitch, color = Accent, fontFamily = displayFamily(lang), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(s.killSwitchDesc, color = TextSecondaryC, fontFamily = if (fa) Vazirmatn else ShareTechMono, fontSize = 10.sp)
                        }
                        CyberToggle(checked = killSwitch, onChange = { vm.setKillSwitch(it) })
                    }

                    HorizontalDivider(color = BorderC, thickness = 0.5.dp)

                    // Upstream Proxy (Chaining)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(s.upstreamProxy, color = Accent, fontFamily = displayFamily(lang), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(s.upstreamProxyDesc, color = TextSecondaryC, fontFamily = if (fa) Vazirmatn else ShareTechMono, fontSize = 10.sp)
                        Spacer(Modifier.size(2.dp))
                        KvInput(
                            label = "proxy",
                            value = upstreamProxy,
                            onValueChange = { vm.setUpstreamProxy(it) },
                            placeholder = s.upstreamProxyPlaceholder,
                        )
                    }

                    HorizontalDivider(color = BorderC, thickness = 0.5.dp)

                    // Manual Cloudflare Endpoint (WARP / MASQUE override)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(s.pinAsWarpEndpoint, color = Accent, fontFamily = displayFamily(lang), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("Custom IP:Port for Cloudflare tunnels", color = TextSecondaryC, fontFamily = if (fa) Vazirmatn else ShareTechMono, fontSize = 10.sp)
                        Spacer(Modifier.size(2.dp))
                        KvInput(
                            label = "endpoint",
                            value = manualEndpoint,
                            onValueChange = { vm.setManualEndpoint(it) },
                            placeholder = "e.g. 162.159.192.1:2408 (optional)",
                        )
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

        // Zero Trust Bottom Sheet
        if (showZeroTrustSheet) {
            ZeroTrustBottomSheet(
                vm = vm,
                onDismiss = { showZeroTrustSheet = false },
            )
        }

        // Psiphon Country Dialog
        if (showPsiphonCountryDialog) {
            val countries = PsiphonRegions.options(context)
            CountryPickerDialog(
                title = "${s.transportPsiphon} - ${s.exitCountry}",
                autoLabel = s.autoCountry,
                currentCode = psiphonRegion,
                countryCodes = countries,
                labelFor = { PsiphonRegions.label(it) },
                detailFor = { PsiphonRegions.detail(it) },
                onSelect = {
                    vm.setPsiphonRegion(it)
                    showPsiphonCountryDialog = false
                },
                onDismiss = { showPsiphonCountryDialog = false }
            )
        }

        // Tor Country Dialog
        if (showTorCountryDialog) {
            val countries = TorRegions.options()
            CountryPickerDialog(
                title = "${s.transportTor} - ${s.exitCountry}",
                autoLabel = s.autoCountry,
                currentCode = torRegion,
                countryCodes = countries,
                labelFor = { TorRegions.label(it) },
                detailFor = { TorRegions.detail(it) },
                onSelect = {
                    vm.setTorRegion(it)
                    showTorCountryDialog = false
                },
                onDismiss = { showTorCountryDialog = false }
            )
        }
    }
}

@Composable
private fun CountryPickerDialog(
    title: String,
    autoLabel: String,
    currentCode: String,
    countryCodes: List<String>,
    labelFor: (String) -> String,
    detailFor: (String) -> String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceC)
                .border(1.dp, BorderC, RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, color = Accent, fontFamily = ShareTechMono, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Icon(
                        Icons.Filled.Close,
                        null,
                        tint = TextMutedC,
                        modifier = Modifier.size(20.dp).clickable { onDismiss() }
                    )
                }

                Spacer(Modifier.size(12.dp))

                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Auto option
                    item {
                        val isAuto = currentCode == "auto"
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isAuto) AccentMuted else Color.Transparent)
                                .clickable { onSelect("auto") }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(autoLabel, color = if (isAuto) Accent else TextPrimaryC, fontFamily = ShareTechMono, fontSize = 13.sp)
                            if (isAuto) Icon(Icons.Filled.Check, null, tint = Accent, modifier = Modifier.size(16.dp))
                        }
                    }

                    // Country options
                    items(countryCodes) { code ->
                        val isSel = currentCode.equals(code, ignoreCase = true)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) AccentMuted else Color.Transparent)
                                .clickable { onSelect(code) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(labelFor(code), color = if (isSel) Accent else TextPrimaryC, fontFamily = ShareTechMono, fontSize = 13.sp)
                                Text(detailFor(code), color = TextMutedC, fontFamily = ShareTechMono, fontSize = 9.sp)
                            }
                            if (isSel) Icon(Icons.Filled.Check, null, tint = Accent, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
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
