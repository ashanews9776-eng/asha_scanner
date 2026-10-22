package com.ahoura.asha_scanner_ip.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahoura.asha_scanner_ip.ui.i18n.LocalLang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalStrings
import com.ahoura.asha_scanner_ip.ui.theme.Accent
import com.ahoura.asha_scanner_ip.ui.theme.BlueC
import com.ahoura.asha_scanner_ip.ui.theme.OrangeC
import com.ahoura.asha_scanner_ip.ui.theme.RedC
import com.ahoura.asha_scanner_ip.ui.theme.Vazirmatn
import com.ahoura.asha_scanner_ip.ui.theme.displayFamily

/**
 * Human-friendly latency category badge that translates raw millisecond numbers
 * into clear everyday use-case guidance (Gaming, Streaming, Calls, Browsing).
 */
@Composable
fun HumanLatencyPill(
    latencyMs: Long,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val lang = LocalLang.current

    val (label, color) = when {
        latencyMs <= 0L -> "—" to Color.Gray
        latencyMs < 80L -> s.latencyUltra to Accent
        latencyMs < 155L -> s.latencyFast to BlueC
        latencyMs < 250 -> s.latencyNormal to OrangeC
        else -> s.latencyHigh to RedC
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.08f))
            .border(0.5.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = color,
                fontFamily = if (lang == com.ahoura.asha_scanner_ip.ui.i18n.Lang.FA) Vazirmatn else displayFamily(lang),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun HumanLatencyPill(
    latencyMs: Int,
    modifier: Modifier = Modifier,
) = HumanLatencyPill(latencyMs.toLong(), modifier)

