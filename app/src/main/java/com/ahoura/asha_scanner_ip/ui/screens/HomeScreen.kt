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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahoura.asha_scanner_ip.BuildConfig
import com.ahoura.asha_scanner_ip.core.guard.IpFormatter
import com.ahoura.asha_scanner_ip.core.ipsrc.CloudflareRanges
import com.ahoura.asha_scanner_ip.core.vpn.VpnStatus
import com.ahoura.asha_scanner_ip.ui.TELEGRAM_HANDLE
import com.ahoura.asha_scanner_ip.ui.TELEGRAM_URL
import com.ahoura.asha_scanner_ip.ui.components.CyberTrafficHopHud
import com.ahoura.asha_scanner_ip.ui.components.GlitchText
import com.ahoura.asha_scanner_ip.ui.components.HumanLatencyPill
import com.ahoura.asha_scanner_ip.ui.components.ModernFeatureCard
import com.ahoura.asha_scanner_ip.ui.components.Pill
import com.ahoura.asha_scanner_ip.ui.components.SectionLabel
import com.ahoura.asha_scanner_ip.ui.components.StaggerIn
import com.ahoura.asha_scanner_ip.ui.components.TypewriterText
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
import com.ahoura.asha_scanner_ip.ui.theme.GoldC
import com.ahoura.asha_scanner_ip.ui.theme.OrangeC
import com.ahoura.asha_scanner_ip.ui.theme.ShareTechMono
import com.ahoura.asha_scanner_ip.ui.theme.SurfaceC
import com.ahoura.asha_scanner_ip.ui.theme.TextFadedC
import com.ahoura.asha_scanner_ip.ui.theme.TextMutedC
import com.ahoura.asha_scanner_ip.ui.theme.TextPrimaryC
import com.ahoura.asha_scanner_ip.ui.theme.TextSecondaryC
import com.ahoura.asha_scanner_ip.ui.theme.Vazirmatn
import com.ahoura.asha_scanner_ip.ui.theme.displayFamily
import com.ahoura.asha_scanner_ip.ui.theme.monoFamily

private val Teal = Color(0xFF14B8A6)

@Composable
fun HomeScreen(
    vm: com.ahoura.asha_scanner_ip.ui.ScanViewModel,
    onQuick: () -> Unit,
    onCustom: () -> Unit,
    onTest: () -> Unit,
    onDiscover: () -> Unit,
    onVpn: () -> Unit,
    onDns: () -> Unit,
    onSpeedTest: () -> Unit,
    onAbout: () -> Unit,
    onToggleLang: () -> Unit,
) {
    val context = LocalContext.current
    val s = LocalStrings.current
    val lang = LocalLang.current
    val fa = lang == Lang.FA
    val state by vm.state.collectAsState()
    val vpnStats by vm.vpnStats.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(20.dp))

        // ---- Header ----
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                GlitchText(
                    "ASHA SCANNER",
                    color = Accent,
                    fontSize = 15.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                if (fa) {
                    Text(s.homeSubtitle, color = TextMutedC, fontFamily = Vazirmatn, fontSize = 11.sp)
                } else {
                    TypewriterText(s.homeSubtitle, color = TextMutedC, fontSize = 9.sp, letterSpacing = 2.sp)
                }
            }
            LangChip(lang, onToggleLang)
        }

        Spacer(Modifier.height(10.dp))

        // ---- Network Carrier Bar & Version Pill ----
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Carrier pill with live pulsing indicator
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceC)
                    .border(0.5.dp, BorderC, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (vpnStats.status.isConnected) Accent else BlueC)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = state.ispInfo.take(24),
                        color = TextSecondaryC,
                        fontFamily = if (fa) Vazirmatn else ShareTechMono,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Pill("v${BuildConfig.VERSION_NAME} · IR-OPTIMIZED")
        }

        Spacer(Modifier.height(16.dp))

        // ---- Resume scan banner if snapshot exists ----
        if (state.resumeSnapshot != null) {
            val snap = state.resumeSnapshot!!
            val bannerText = s.resumeScanBanner
                .replace("{0}", snap.testedCount.toString().localizeDigits(lang))
                .replace("{1}", snap.totalCount.toString().localizeDigits(lang))
                .replace("{2}", snap.healthyResults.size.toString().localizeDigits(lang))

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Accent.copy(alpha = 0.08f))
                    .border(1.dp, Accent.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(Accent, RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = bannerText,
                            color = Color.White,
                            fontFamily = if (fa) Vazirmatn else monoFamily(lang),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Accent)
                                .clickable {
                                    vm.resumeScan()
                                    onQuick()
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = s.resumeScanBtn.uppercase(),
                                color = Color.Black,
                                fontFamily = monoFamily(lang),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .border(0.5.dp, TextMutedC, RoundedCornerShape(4.dp))
                                .clickable { vm.discardResume() }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = s.discardResumeBtn,
                                color = TextMutedC,
                                fontFamily = monoFamily(lang),
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ==========================================
        // HERO CARD 1: ASHA SMART VPN (اتصال هوشمند آشا)
        // ==========================================
        StaggerIn(0) {
            val isConnected = vpnStats.status.isConnected
            val isConnecting = vpnStats.status == VpnStatus.CONNECTING
            val borderColor = when {
                isConnected -> Accent
                isConnecting -> OrangeC
                else -> AccentBorder.copy(alpha = 0.5f)
            }
            val glowBg = when {
                isConnected -> Accent.copy(alpha = 0.08f)
                isConnecting -> OrangeC.copy(alpha = 0.08f)
                else -> SurfaceC
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(glowBg)
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                    .clickable(onClick = onVpn)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Left Icon with animated status ring
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    isConnected -> Accent.copy(alpha = 0.18f)
                                    isConnecting -> OrangeC.copy(alpha = 0.18f)
                                    else -> AccentMuted
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isConnected) Icons.Filled.Shield else Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = if (isConnected) Accent else (if (isConnecting) OrangeC else Accent),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = s.smartVpnCardTitle,
                                color = TextPrimaryC,
                                fontFamily = displayFamily(lang),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            )
                            Spacer(Modifier.width(8.dp))
                            // Status Pill
                            val statusLabel = when (vpnStats.status) {
                                VpnStatus.CONNECTED -> s.connected
                                VpnStatus.CONNECTING -> s.connecting
                                VpnStatus.DISCONNECTING -> s.connecting
                                VpnStatus.ERROR -> "ERROR"
                                VpnStatus.DISCONNECTED -> s.disconnected
                            }
                            val statusColor = when (vpnStats.status) {
                                VpnStatus.CONNECTED -> Accent
                                VpnStatus.CONNECTING -> OrangeC
                                VpnStatus.ERROR -> Color(0xFFEF4444)
                                VpnStatus.DISCONNECTING -> OrangeC
                                VpnStatus.DISCONNECTED -> TextMutedC
                            }
                            Pill(
                                text = statusLabel,
                                textColor = statusColor,
                                borderColor = statusColor.copy(alpha = 0.4f),
                                bgColor = statusColor.copy(alpha = 0.1f)
                            )
                        }

                        Spacer(Modifier.height(3.dp))

                        if (isConnected) {
                            val sec = vpnStats.connectedDurationSeconds
                            val timeStr = "%02d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60)
                            val ping = vpnStats.pingMs
                            val pingPart = if (ping != null) " · ${ping}ms" else ""
                            Text(
                                text = "${timeStr.localizeDigits(lang)}$pingPart",
                                color = AccentDim,
                                fontFamily = ShareTechMono,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (ping != null && ping > 0) {
                                Spacer(Modifier.height(4.dp))
                                HumanLatencyPill(latencyMs = ping)
                            }
                        } else {
                            Text(
                                text = s.smartVpnCardSubtitle,
                                color = TextSecondaryC,
                                fontFamily = Vazirmatn,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = if (isConnected) Accent else TextMutedC,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (isConnected) {
                Spacer(Modifier.height(8.dp))
                CyberTrafficHopHud(
                    isConnected = true,
                    carrierName = state.ispInfo.ifBlank { "Mobile Network" },
                    exitLocation = vpnStats.country?.let { IpFormatter.flag(it) + " " + it } ?: "🇩🇪 Germany",
                    onClick = onVpn,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ==========================================
        // HERO CARD 2: SMART CLEAN IP SCANNER (اسکن هوشمند آی‌پی تمیز)
        // ==========================================
        StaggerIn(1) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceC)
                    .border(0.5.dp, BorderC, RoundedCornerShape(12.dp))
                    .clickable(onClick = onQuick)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BlueC.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = null,
                            tint = BlueC,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = s.cleanIpScanTitle,
                                color = TextPrimaryC,
                                fontFamily = displayFamily(lang),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Pill(
                                text = "1-TAP",
                                textColor = BlueC,
                                borderColor = BlueC.copy(alpha = 0.3f),
                                bgColor = BlueC.copy(alpha = 0.08f)
                            )
                        }

                        Spacer(Modifier.height(3.dp))

                        Text(
                            text = s.cleanIpScanSubtitle,
                            color = TextSecondaryC,
                            fontFamily = Vazirmatn,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextMutedC,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // ==========================================
        // SECTION: TOOLS & DIAGNOSTICS (ابزارها و قابلیت‌ها)
        // ==========================================
        SectionLabel(label = s.homeToolsTitle)
        Spacer(Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Speed Test
            StaggerIn(2) {
                ModernFeatureCard(
                    icon = Icons.Filled.Speed,
                    iconTint = Accent,
                    title = s.speedTestTitle,
                    subtitle = s.speedTestSubtitle,
                    onClick = onSpeedTest,
                )
            }

            // Test Custom IPs
            StaggerIn(3) {
                ModernFeatureCard(
                    icon = Icons.Filled.CheckCircle,
                    iconTint = OrangeC,
                    title = s.testIps,
                    subtitle = s.testIpsDesc,
                    onClick = onTest,
                )
            }

            // Custom Scan (Advanced Range Tuning)
            StaggerIn(4) {
                ModernFeatureCard(
                    icon = Icons.Filled.Tune,
                    iconTint = BlueC,
                    title = s.customScan,
                    subtitle = s.customScanDesc,
                    onClick = onCustom,
                )
            }

            // Discover Colos (Data Centers)
            StaggerIn(5) {
                ModernFeatureCard(
                    icon = Icons.Filled.Place,
                    iconTint = Teal,
                    title = s.discoverColos,
                    subtitle = s.discoverColosDesc,
                    onClick = onDiscover,
                )
            }

            // DNS & Anti-Sanction Tool
            StaggerIn(6) {
                ModernFeatureCard(
                    icon = Icons.Filled.Dns,
                    iconTint = GoldC,
                    title = s.dnsTool,
                    subtitle = s.dnsToolDesc,
                    onClick = onDns,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- Telegram Channel Card ----
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(BlueC.copy(alpha = 0.06f))
                .border(0.5.dp, BlueC.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .clickable { openUrl(context, TELEGRAM_URL) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, null, tint = BlueC, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (fa) s.telegramChannel else s.telegramChannel.uppercase(),
                    color = TextMutedC,
                    fontFamily = monoFamily(lang),
                    fontSize = if (fa) 10.sp else 9.sp,
                    letterSpacing = if (fa) 0.sp else 1.2.sp,
                )
                Text(TELEGRAM_HANDLE, color = BlueC, fontFamily = ShareTechMono, fontSize = 13.sp)
            }
            Text(
                s.join,
                color = BlueC,
                fontFamily = displayFamily(lang),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = if (fa) 0.sp else 1.sp,
            )
        }

        Spacer(Modifier.height(20.dp))

        // ---- Footer ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val footFont = if (fa) Vazirmatn else ShareTechMono
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${s.cfRanges} ", color = TextFadedC, fontFamily = footFont, fontSize = 9.sp)
                Text("${CloudflareRanges.V4.size}", color = Accent, fontFamily = ShareTechMono, fontSize = 9.sp)
                Text(" ${s.cidrs}", color = TextFadedC, fontFamily = footFont, fontSize = 9.sp)
            }
            Text(
                if (fa) s.license else s.license.uppercase(),
                color = TextSecondaryC,
                fontFamily = footFont,
                fontSize = 9.sp,
                letterSpacing = if (fa) 0.sp else 1.sp,
                modifier = Modifier.clickable(onClick = onAbout),
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

/** Tiny pill that switches to the other language (FA ⇄ EN). */
@Composable
private fun LangChip(lang: Lang, onToggle: () -> Unit) {
    val label = if (lang == Lang.EN) "فارسی" else "EN"
    val font = if (lang == Lang.EN) Vazirmatn else ShareTechMono
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(BlueC.copy(alpha = 0.08f))
            .border(0.5.dp, BlueC.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, color = BlueC, fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
