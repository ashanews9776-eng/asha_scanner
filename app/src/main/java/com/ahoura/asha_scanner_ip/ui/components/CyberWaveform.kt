package com.ahoura.asha_scanner_ip.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahoura.asha_scanner_ip.ui.i18n.LocalLang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalStrings
import com.ahoura.asha_scanner_ip.ui.i18n.localizeDigits
import com.ahoura.asha_scanner_ip.ui.theme.Accent
import com.ahoura.asha_scanner_ip.ui.theme.BlueC
import com.ahoura.asha_scanner_ip.ui.theme.BorderC
import com.ahoura.asha_scanner_ip.ui.theme.ShareTechMono
import com.ahoura.asha_scanner_ip.ui.theme.SurfaceC
import com.ahoura.asha_scanner_ip.ui.theme.TextMutedC
import com.ahoura.asha_scanner_ip.ui.theme.TextSecondaryC
import com.ahoura.asha_scanner_ip.ui.theme.Vazirmatn
import kotlin.math.PI
import kotlin.math.sin

/**
 * Live animated traffic oscilloscope showing real-time network pulse
 * with flowing waveforms and speed telemetry.
 */
@Composable
fun CyberTrafficOscilloscope(
    isConnected: Boolean,
    downloadBytesPerSec: Long,
    uploadBytesPerSec: Long,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val lang = LocalLang.current

    val transition = rememberInfiniteTransition(label = "traffic_wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(if (isConnected) 2400 else 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceC)
            .border(0.5.dp, BorderC, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isConnected) Accent else TextMutedC)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = s.liveTrafficMonitor,
                        color = TextSecondaryC,
                        fontFamily = Vazirmatn,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (isConnected) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "↓ ${formatSpeed(downloadBytesPerSec).localizeDigits(lang)}",
                            color = Accent,
                            fontFamily = ShareTechMono,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "↑ ${formatSpeed(uploadBytesPerSec).localizeDigits(lang)}",
                            color = BlueC,
                            fontFamily = ShareTechMono,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Text(
                        text = "STANDBY",
                        color = TextMutedC,
                        fontFamily = ShareTechMono,
                        fontSize = 9.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Wave Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                val width = size.width
                val height = size.height
                val midY = height / 2f

                // Subtle oscilloscope grid
                val gridSpacing = 36.dp.toPx()
                var gx = gridSpacing
                while (gx < width) {
                    drawLine(
                        color = BorderC.copy(alpha = 0.18f),
                        start = androidx.compose.ui.geometry.Offset(gx, 0f),
                        end = androidx.compose.ui.geometry.Offset(gx, height),
                        strokeWidth = 0.5.dp.toPx()
                    )
                    gx += gridSpacing
                }
                drawLine(
                    color = BorderC.copy(alpha = 0.25f),
                    start = androidx.compose.ui.geometry.Offset(0f, midY),
                    end = androidx.compose.ui.geometry.Offset(width, midY),
                    strokeWidth = 0.5.dp.toPx()
                )

                if (!isConnected) {
                    // Dim static baseline with subtle pulse
                    drawLine(
                        color = TextMutedC.copy(alpha = 0.35f),
                        start = androidx.compose.ui.geometry.Offset(0f, midY),
                        end = androidx.compose.ui.geometry.Offset(width, midY),
                        strokeWidth = 1.dp.toPx()
                    )
                    return@Canvas
                }

                // Dynamic speed-reactive amplitude
                val rxRatio = (downloadBytesPerSec / 2_000_000f).coerceIn(0f, 1f)
                val txRatio = (uploadBytesPerSec / 1_000_000f).coerceIn(0f, 1f)
                val ampRx = (height * (0.22f + 0.24f * rxRatio)).coerceIn(6f, height * 0.46f)
                val ampTx = (height * (0.16f + 0.20f * txRatio)).coerceIn(4f, height * 0.36f)

                // Wave 1: Download (Green Accent)
                val pathRx = Path()
                val fillRx = Path()
                fillRx.moveTo(0f, height)
                pathRx.moveTo(0f, midY)

                val points = 60

                for (i in 0..points) {
                    val x = (i / points.toFloat()) * width
                    val normalizedX = (i / points.toFloat()) * 4f * PI.toFloat()
                    val y = midY + sin(normalizedX + phase) * ampRx + sin(normalizedX * 0.5f + phase * 1.3f) * (ampRx * 0.3f)
                    pathRx.lineTo(x, y)
                    fillRx.lineTo(x, y)
                }

                fillRx.lineTo(width, height)
                fillRx.close()

                // Draw translucent gradient fill
                drawPath(
                    path = fillRx,
                    brush = Brush.verticalGradient(
                        colors = listOf(Accent.copy(alpha = 0.15f), Color.Transparent),
                        startY = 0f,
                        endY = height
                    )
                )

                // Draw stroke
                drawPath(
                    path = pathRx,
                    color = Accent,
                    style = Stroke(width = 1.8.dp.toPx())
                )

                // Wave 2: Upload (BlueC)
                val pathTx = Path()
                pathTx.moveTo(0f, midY)
                for (i in 0..points) {
                    val x = (i / points.toFloat()) * width
                    val normalizedX = (i / points.toFloat()) * 3f * PI.toFloat()
                    val y = midY + sin(normalizedX - phase * 0.9f) * ampTx + sin(normalizedX * 0.7f - phase) * (ampTx * 0.25f)
                    pathTx.lineTo(x, y)
                }

                drawPath(
                    path = pathTx,
                    color = BlueC.copy(alpha = 0.85f),
                    style = Stroke(width = 1.4.dp.toPx())
                )
            }
        }
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    return when {
        bytesPerSec >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
        bytesPerSec >= 1024 -> String.format(java.util.Locale.US, "%d KB/s", bytesPerSec / 1024)
        bytesPerSec > 0 -> "$bytesPerSec B/s"
        else -> "0 KB/s"
    }
}
