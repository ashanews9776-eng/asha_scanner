package com.ahoura.asha_scanner_ip.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahoura.asha_scanner_ip.core.net.SpeedTestEngine
import com.ahoura.asha_scanner_ip.core.net.SpeedTestPhase
import com.ahoura.asha_scanner_ip.core.guard.TunnelStatus
import com.ahoura.asha_scanner_ip.ui.components.CyberAppBar
import com.ahoura.asha_scanner_ip.ui.components.CyberCard
import com.ahoura.asha_scanner_ip.ui.i18n.LocalStrings
import com.ahoura.asha_scanner_ip.ui.theme.Accent
import com.ahoura.asha_scanner_ip.ui.theme.AccentBorder
import com.ahoura.asha_scanner_ip.ui.theme.AccentDim
import com.ahoura.asha_scanner_ip.ui.theme.BlueC
import com.ahoura.asha_scanner_ip.ui.theme.BorderC
import com.ahoura.asha_scanner_ip.ui.theme.OrangeC
import com.ahoura.asha_scanner_ip.ui.theme.RedC
import com.ahoura.asha_scanner_ip.ui.theme.ShareTechMono
import com.ahoura.asha_scanner_ip.ui.theme.SurfaceC
import com.ahoura.asha_scanner_ip.ui.theme.TextMutedC
import com.ahoura.asha_scanner_ip.ui.theme.TextPrimaryC
import com.ahoura.asha_scanner_ip.ui.theme.TextSecondaryC
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SpeedTestScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()

    // If VPN active, route speedtest through local SOCKS port (1821 / 1819)
    val socksPort = if (TunnelStatus.isActive()) 1821 else null
    val engine = remember { SpeedTestEngine(socksPort) }
    val state by engine.state.collectAsState()

    DisposableEffect(engine) {
        onDispose {
            engine.cancel()
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = tween(150),
        label = "progress"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        CyberAppBar(title = s.speedTestTitle, onBack = onBack)

        Text(
            text = s.speedTestSubtitle,
            color = TextMutedC,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Main Speed Gauge Display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceC)
                .border(0.5.dp, BorderC, RoundedCornerShape(16.dp))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Phase pill
                val phaseLabel = when (state.phase) {
                    SpeedTestPhase.Idle -> "READY"
                    SpeedTestPhase.Ping -> "PING / JITTER"
                    SpeedTestPhase.Download -> "DOWNLOAD"
                    SpeedTestPhase.Upload -> "UPLOAD"
                    SpeedTestPhase.Done -> "FINISHED"
                    SpeedTestPhase.Stopped -> "STOPPED"
                    SpeedTestPhase.Error -> "ERROR"
                }
                val phaseColor = when (state.phase) {
                    SpeedTestPhase.Download -> Accent
                    SpeedTestPhase.Upload -> BlueC
                    SpeedTestPhase.Ping -> OrangeC
                    SpeedTestPhase.Done -> Accent
                    SpeedTestPhase.Error -> RedC
                    else -> TextMutedC
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(phaseColor.copy(alpha = 0.15f))
                        .border(0.5.dp, phaseColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = phaseLabel,
                        color = phaseColor,
                        fontFamily = ShareTechMono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Digital readout
                val mainMetricValue = when (state.phase) {
                    SpeedTestPhase.Ping -> String.format(Locale.US, "%.1f", state.pingMs ?: 0.0)
                    SpeedTestPhase.Download -> String.format(Locale.US, "%.2f", state.liveMbps)
                    SpeedTestPhase.Upload -> String.format(Locale.US, "%.2f", state.liveMbps)
                    SpeedTestPhase.Done -> String.format(Locale.US, "%.2f", state.downloadMbps ?: 0.0)
                    else -> "0.00"
                }

                val mainMetricUnit = when (state.phase) {
                    SpeedTestPhase.Ping -> "MS"
                    else -> "MBPS"
                }

                Text(
                    text = mainMetricValue,
                    color = TextPrimaryC,
                    fontFamily = ShareTechMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 44.sp,
                    letterSpacing = 1.sp
                )

                Text(
                    text = mainMetricUnit,
                    color = Accent,
                    fontFamily = ShareTechMono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    letterSpacing = 2.sp
                )

                Spacer(Modifier.height(14.dp))

                // Progress Bar
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = phaseColor,
                    trackColor = BorderC,
                )

                Spacer(Modifier.height(14.dp))

                // Waveform Canvas
                val samples = if (state.phase == SpeedTestPhase.Upload) state.upSamples else state.downSamples
                WaveformChart(
                    samples = samples.map { it.mbps.toFloat() },
                    color = phaseColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(45.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Grid Metrics Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                title = s.pingLatency,
                value = state.pingMs?.let { String.format(Locale.US, "%.0f ms", it) } ?: "--",
                icon = Icons.Default.NetworkCheck,
                color = OrangeC,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = s.jitter,
                value = state.jitterMs?.let { String.format(Locale.US, "%.0f ms", it) } ?: "--",
                icon = Icons.Default.NetworkCheck,
                color = OrangeC,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                title = s.downloadSpeed,
                value = state.downloadMbps?.let { String.format(Locale.US, "%.2f Mbps", it) } ?: "--",
                icon = Icons.Default.ArrowDownward,
                color = Accent,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = s.uploadSpeed,
                value = state.uploadMbps?.let { String.format(Locale.US, "%.2f Mbps", it) } ?: "--",
                icon = Icons.Default.ArrowUpward,
                color = BlueC,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                title = s.loadedPing,
                value = state.loadedPingMs?.let { String.format(Locale.US, "%.0f ms", it) } ?: "--",
                icon = Icons.Default.Speed,
                color = TextSecondaryC,
                modifier = Modifier.weight(1f)
            )
            val mbUsed = state.bytesUsed / (1024.0 * 1024.0)
            MetricCard(
                title = s.dataUsed,
                value = String.format(Locale.US, "%.1f MB", mbUsed),
                icon = Icons.Default.Speed,
                color = TextSecondaryC,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(20.dp))

        // Action Button
        if (state.running) {
            Button(
                onClick = { engine.cancel() },
                colors = ButtonDefaults.buttonColors(containerColor = RedC),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Stop, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(s.stopSpeedTest, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        } else {
            Button(
                onClick = {
                    scope.launch {
                        engine.run()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Speed, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(s.startSpeedTest, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceC)
            .border(0.5.dp, BorderC, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(text = title, color = TextMutedC, fontSize = 10.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                color = color,
                fontFamily = ShareTechMono,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun WaveformChart(
    samples: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (samples.size < 2) return@Canvas
        val maxVal = (samples.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val w = size.width
        val h = size.height

        val stepX = w / (samples.size - 1)
        val path = Path()
        samples.forEachIndexed { i, valSample ->
            val x = i * stepX
            val y = h - (valSample / maxVal) * (h * 0.85f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
