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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahoura.asha_scanner_ip.ui.i18n.LocalLang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalStrings
import com.ahoura.asha_scanner_ip.ui.theme.Accent
import com.ahoura.asha_scanner_ip.ui.theme.BlueC
import com.ahoura.asha_scanner_ip.ui.theme.BorderC
import com.ahoura.asha_scanner_ip.ui.theme.SurfaceC
import com.ahoura.asha_scanner_ip.ui.theme.TextMutedC
import com.ahoura.asha_scanner_ip.ui.theme.TextPrimaryC
import com.ahoura.asha_scanner_ip.ui.theme.TextSecondaryC
import com.ahoura.asha_scanner_ip.ui.theme.Vazirmatn

/**
 * CyberTrafficHopHud displays the visual telemetry hop-path of encrypted traffic:
 * Device -> Anti-DPI Shield -> Cloudflare Edge -> Free Internet Exit.
 */
@Composable
fun CyberTrafficHopHud(
    isConnected: Boolean,
    carrierName: String,
    exitLocation: String = "🇩🇪 Germany",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val lang = LocalLang.current

    val transition = rememberInfiniteTransition(label = "hop_laser")
    val laserProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "laser"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceC)
            .border(0.5.dp, if (isConnected) Accent.copy(alpha = 0.4f) else BorderC, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) Accent else TextMutedC)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = s.trafficPathTitle,
                        color = TextPrimaryC,
                        fontFamily = Vazirmatn,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = s.tapForInspection,
                    color = TextMutedC,
                    fontFamily = Vazirmatn,
                    fontSize = 9.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            // 4 Hop Nodes with connecting lines
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hop 1: Device
                HopNode(
                    icon = Icons.Filled.Smartphone,
                    label = carrierName.take(12),
                    tint = if (isConnected) Accent else TextSecondaryC,
                    active = isConnected
                )

                // Laser Line 1
                HopConnector(progress = if (isConnected) laserProgress else -1f)

                // Hop 2: Anti-DPI Shield
                HopNode(
                    icon = Icons.Filled.Security,
                    label = "PoW Shield",
                    tint = if (isConnected) Accent else TextSecondaryC,
                    active = isConnected
                )

                // Laser Line 2
                HopConnector(progress = if (isConnected) ((laserProgress + 0.33f) % 1f) else -1f)

                // Hop 3: Cloudflare Edge
                HopNode(
                    icon = Icons.Filled.Cloud,
                    label = "CF Edge",
                    tint = if (isConnected) BlueC else TextSecondaryC,
                    active = isConnected
                )

                // Laser Line 3
                HopConnector(progress = if (isConnected) ((laserProgress + 0.66f) % 1f) else -1f)

                // Hop 4: Free Internet Exit
                HopNode(
                    icon = Icons.Filled.Public,
                    label = exitLocation.take(12),
                    tint = if (isConnected) Accent else TextSecondaryC,
                    active = isConnected
                )
            }
        }
    }
}

@Composable
private fun HopNode(
    icon: ImageVector,
    label: String,
    tint: Color,
    active: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (active) tint.copy(alpha = 0.12f) else Color.Transparent)
                .border(
                    width = 1.dp,
                    color = if (active) tint else BorderC,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            color = if (active) TextPrimaryC else TextMutedC,
            fontFamily = Vazirmatn,
            fontSize = 9.sp,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun HopConnector(progress: Float) {
    Box(
        modifier = Modifier
            .width(28.dp)
            .height(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
            val width = size.width
            val midY = size.height / 2f

            // Baseline track
            drawLine(
                color = BorderC.copy(alpha = 0.7f),
                start = Offset(0f, midY),
                end = Offset(width, midY),
                strokeWidth = 1.dp.toPx()
            )

            // Photon pulse dot & laser tail traveling across
            if (progress >= 0f) {
                val dotX = width * progress
                val tailLength = 10.dp.toPx()
                val tailStart = (dotX - tailLength).coerceAtLeast(0f)

                // Laser tail
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Accent.copy(alpha = 0.8f)),
                        startX = tailStart,
                        endX = dotX
                    ),
                    start = Offset(tailStart, midY),
                    end = Offset(dotX, midY),
                    strokeWidth = 2.dp.toPx()
                )

                // Outer energy glow
                drawCircle(
                    color = Accent.copy(alpha = 0.35f),
                    radius = 4.5.dp.toPx(),
                    center = Offset(dotX, midY)
                )

                // Inner photon core
                drawCircle(
                    color = Accent,
                    radius = 2.2.dp.toPx(),
                    center = Offset(dotX, midY)
                )
            }
        }
    }
}
