package com.ahoura.asha_scanner_ip.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahoura.asha_scanner_ip.core.guard.ConnectionStepsStore
import com.ahoura.asha_scanner_ip.core.guard.StepState
import com.ahoura.asha_scanner_ip.core.vpn.VpnStats
import com.ahoura.asha_scanner_ip.core.vpn.VpnStatus
import com.ahoura.asha_scanner_ip.ui.i18n.Lang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalLang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalStrings
import com.ahoura.asha_scanner_ip.ui.i18n.localizeDigits
import com.ahoura.asha_scanner_ip.ui.theme.Accent
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * High-tech Cyberpunk Live Telemetry Console BottomSheet.
 * Provides transparent, crystal-clear diagnostic visibility into the native engine events,
 * route failover, MTU, and packet telemetry without cluttering the main screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTelemetryBottomSheet(
    vpnStats: VpnStats,
    selectedTransport: String,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    val lang = LocalLang.current
    val fa = lang == Lang.FA
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val stepsStatus by ConnectionStepsStore.status.collectAsState()

    var copiedToast by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0D0F14),
        contentColor = TextPrimaryC,
        dragHandle = null,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Accent.copy(alpha = 0.12f))
                            .border(0.5.dp, Accent.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Terminal,
                            contentDescription = null,
                            tint = Accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = s.telemetryTitle,
                            color = Accent,
                            fontFamily = ShareTechMono,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = s.telemetrySubtitle,
                            color = TextSecondaryC,
                            fontFamily = if (fa) Vazirmatn else ShareTechMono,
                            fontSize = 10.sp,
                            lineHeight = 13.sp
                        )
                    }
                }

                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(SurfaceC)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = TextSecondaryC,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Quick Diagnostic Badges Grid
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Transport Badge
                TelemetryStatBox(
                    label = "TRANSPORT",
                    value = selectedTransport.uppercase(),
                    valueColor = Accent,
                    modifier = Modifier.weight(1f)
                )

                // Ping Badge
                val ping = vpnStats.pingMs
                TelemetryStatBox(
                    label = "LATENCY",
                    value = if (ping != null) "${ping} ms" else "—",
                    valueColor = if (ping != null && ping < 200) Accent else OrangeC,
                    modifier = Modifier.weight(1f)
                )

                // Status Badge
                TelemetryStatBox(
                    label = "TUNNEL STATE",
                    value = when (vpnStats.status) {
                        VpnStatus.CONNECTED -> "SECURE"
                        VpnStatus.CONNECTING -> "SYNCING"
                        VpnStatus.ERROR -> "FAULT"
                        else -> "STANDBY"
                    },
                    valueColor = when (vpnStats.status) {
                        VpnStatus.CONNECTED -> Accent
                        VpnStatus.CONNECTING -> OrangeC
                        VpnStatus.ERROR -> RedC
                        else -> TextMutedC
                    },
                    modifier = Modifier.weight(1.1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            // Diagnostic Console Screen
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 280.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF07090C))
                    .border(0.5.dp, BorderC, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                if (stepsStatus.steps.isEmpty() && vpnStats.detailMessage.isNullOrBlank()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "[ No telemetry events logged yet · Tap connect to stream ]",
                            color = TextMutedC,
                            fontFamily = ShareTechMono,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(stepsStatus.steps) { step ->
                            TelemetryLogRow(step = step, lang = lang)
                        }

                        // Raw Engine Detail Line if available
                        vpnStats.detailMessage?.let { detail ->
                            if (detail.isNotBlank()) {
                                item {
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(BlueC.copy(alpha = 0.08f))
                                            .padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "RAW > $detail",
                                            color = BlueC,
                                            fontFamily = ShareTechMono,
                                            fontSize = 10.sp,
                                            lineHeight = 13.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Raw Engine Error Line if present
                        vpnStats.errorMessage?.let { error ->
                            if (error.isNotBlank()) {
                                item {
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(RedC.copy(alpha = 0.12f))
                                            .padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "ERR > $error",
                                            color = RedC,
                                            fontFamily = ShareTechMono,
                                            fontSize = 10.sp,
                                            lineHeight = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Action Row: Copy Report Button
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Accent.copy(alpha = 0.12f))
                    .border(0.8.dp, Accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .clickable {
                        CyberHaptics.click(haptic)
                        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                        val reportBuilder = StringBuilder()
                        reportBuilder.appendLine("=== ASHA SCANNER TELEMETRY REPORT ===")
                        reportBuilder.appendLine("Timestamp: $timestamp")
                        reportBuilder.appendLine("Transport: $selectedTransport")
                        reportBuilder.appendLine("Status: ${vpnStats.status}")
                        reportBuilder.appendLine("Ping: ${vpnStats.pingMs ?: "N/A"} ms")
                        reportBuilder.appendLine("Rx: ${vpnStats.downloadBps} B/s | Tx: ${vpnStats.uploadBps} B/s")
                        reportBuilder.appendLine("Detail: ${vpnStats.detailMessage.orEmpty()}")
                        reportBuilder.appendLine("Error: ${vpnStats.errorMessage.orEmpty()}")
                        reportBuilder.appendLine("--- STEPS LOG ---")
                        stepsStatus.steps.forEachIndexed { idx, st ->
                            reportBuilder.appendLine("[$idx] [${st.state}] ${st.text}")
                        }
                        reportBuilder.appendLine("=====================================")
                        clipboard.setText(AnnotatedString(reportBuilder.toString()))
                        copiedToast = true
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (copiedToast) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (copiedToast) "✓ Telemetry Copied to Clipboard!" else s.copyTelemetry,
                        color = Accent,
                        fontFamily = if (fa) Vazirmatn else displayFamily(lang),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun TelemetryStatBox(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceC)
            .border(0.5.dp, BorderC, RoundedCornerShape(6.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                color = valueColor,
                fontFamily = ShareTechMono,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                color = TextMutedC,
                fontFamily = ShareTechMono,
                fontSize = 8.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun TelemetryLogRow(
    step: com.ahoura.asha_scanner_ip.core.guard.ConnectionStep,
    lang: Lang,
) {
    val (tag, tagColor) = when (step.state) {
        StepState.DONE -> "[OK]" to Accent
        StepState.RUNNING -> "[SYNC]" to BlueC
        StepState.RETRYING -> "[RETRY]" to OrangeC
        StepState.FAILED -> "[FAIL]" to RedC
    }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = tag,
            color = tagColor,
            fontFamily = ShareTechMono,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(48.dp)
        )
        Text(
            text = step.text.localizeDigits(lang),
            color = if (step.state == StepState.FAILED) RedC.copy(alpha = 0.9f) else TextPrimaryC,
            fontFamily = ShareTechMono,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
