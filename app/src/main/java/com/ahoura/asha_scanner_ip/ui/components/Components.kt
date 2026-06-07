package com.ahoura.asha_scanner_ip.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahoura.asha_scanner_ip.ui.i18n.Lang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalLang
import com.ahoura.asha_scanner_ip.ui.theme.Accent
import com.ahoura.asha_scanner_ip.ui.theme.AccentBorder
import com.ahoura.asha_scanner_ip.ui.theme.AccentMuted
import com.ahoura.asha_scanner_ip.ui.theme.Background
import com.ahoura.asha_scanner_ip.ui.theme.BlueC
import com.ahoura.asha_scanner_ip.ui.theme.BorderC
import com.ahoura.asha_scanner_ip.ui.theme.ShareTechMono
import com.ahoura.asha_scanner_ip.ui.theme.SurfaceC
import com.ahoura.asha_scanner_ip.ui.theme.displayFamily
import com.ahoura.asha_scanner_ip.ui.theme.monoFamily
import com.ahoura.asha_scanner_ip.ui.theme.TextFadedC
import com.ahoura.asha_scanner_ip.ui.theme.TextMutedC
import com.ahoura.asha_scanner_ip.ui.theme.TextPrimaryC
import com.ahoura.asha_scanner_ip.ui.theme.TextSecondaryC

/** Deep canvas + faint 20dp accent grid behind content. */
@Composable
fun GridBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Background)) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 20.dp.toPx()
            val line = Accent.copy(alpha = 0.03f)
            var x = 0f
            while (x <= size.width) {
                drawLine(line, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 1f)
                x += step
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(line, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1f)
                y += step
            }
        }
        content()
    }
}

/** Flat terminal card: surface bg + 0.5dp border. */
@Composable
fun CyberCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(12.dp),
    borderColor: Color = BorderC,
    bg: Color = SurfaceC,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(0.5.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(padding),
    ) { content() }
}

/** ALL-CAPS letter-spaced section header (Persian: no caps/letter-spacing). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = TextMutedC) {
    val fa = LocalLang.current == Lang.FA
    Text(
        if (fa) text else text.uppercase(),
        modifier = modifier,
        color = color,
        fontFamily = monoFamily(LocalLang.current),
        fontSize = if (fa) 11.sp else 9.sp,
        letterSpacing = if (fa) 0.sp else 1.5.sp,
    )
}

/** Small pill chip. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = Accent,
    bg: Color = AccentMuted,
    border: Color = AccentBorder,
) {
    val lang = LocalLang.current
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(0.5.dp, border, RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text, color = textColor, fontFamily = monoFamily(lang), fontSize = 9.sp,
            letterSpacing = if (lang == Lang.FA) 0.sp else 0.5.sp,
        )
    }
}

/** Transparent terminal-style top bar. */
@Composable
fun CyberAppBar(
    title: String,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val lang = LocalLang.current
        if (onBack != null) {
            // 40dp tap target around the 22dp glyph for comfortable back navigation.
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back",
                    tint = Accent, modifier = Modifier.size(22.dp),
                )
            }
            Box(Modifier.size(4.dp))
        }
        Text(
            if (lang == Lang.FA) title else title.uppercase(),
            color = TextPrimaryC, fontFamily = displayFamily(lang),
            fontWeight = FontWeight.Bold, fontSize = 18.sp,
            letterSpacing = if (lang == Lang.FA) 0.sp else 1.sp,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) trailing()
    }
}

/** Primary action: accentMuted fill, accentBorder, scale-on-press. */
@Composable
fun ScanButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "scale")
    // Breathing neon border so the primary action always feels alive.
    val glow by rememberInfiniteTransition(label = "btnGlow").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Reverse), label = "g",
    )
    val borderColor = if (enabled) Accent.copy(alpha = 0.25f + 0.5f * glow) else BorderC
    Box(
        modifier
            .scale(scale)
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (enabled) AccentMuted else SurfaceC)
            .border(if (enabled) 1.dp else 0.5.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val lang = LocalLang.current
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, null, tint = if (enabled) Accent else TextMutedC, modifier = Modifier.size(18.dp))
            Text(
                if (lang == Lang.FA) text else text.uppercase(),
                color = if (enabled) Accent else TextMutedC,
                fontFamily = displayFamily(lang), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                letterSpacing = if (lang == Lang.FA) 0.sp else 1.5.sp,
            )
        }
    }
}

/** Outline secondary button (blue tint by default). */
@Composable
fun GhostFileButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    color: Color = BlueC,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.05f))
            .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val lang = LocalLang.current
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Box(Modifier.size(8.dp))
        Text(
            if (lang == Lang.FA) text else text.uppercase(),
            color = color, fontFamily = displayFamily(lang), fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp, letterSpacing = if (lang == Lang.FA) 0.sp else 1.sp,
        )
    }
}

/** Home menu row: icon box | title+desc | chevron, with active left bar. */
@Composable
fun MenuItemCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    desc: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) AccentMuted else Color.Transparent)
            .border(0.5.dp, if (active) AccentBorder else BorderC, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
    ) {
        if (active) {
            Box(Modifier.width(2.dp).height(44.dp).align(Alignment.CenterStart).background(Accent))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(6.dp))
                    .background(iconColor.copy(alpha = 0.12f))
                    .border(0.5.dp, iconColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
            val lang = LocalLang.current
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    title, color = TextPrimaryC, fontFamily = displayFamily(lang),
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                )
                Box(Modifier.size(1.dp))
                Text(
                    if (lang == Lang.FA) desc else desc.uppercase(),
                    color = TextSecondaryC, fontFamily = monoFamily(lang),
                    fontSize = if (lang == Lang.FA) 11.sp else 9.sp,
                    letterSpacing = if (lang == Lang.FA) 0.sp else 1.sp,
                )
            }
            Icon(Icons.Filled.ChevronRight, null, tint = if (active) Accent else TextFadedC, modifier = Modifier.size(18.dp))
        }
    }
}

/** Blue colo badge. */
@Composable
fun ColoBadge(text: String, modifier: Modifier = Modifier) {
    if (text.isBlank()) {
        Text("·", color = TextFadedC, fontFamily = ShareTechMono, fontSize = 9.sp, modifier = modifier)
        return
    }
    Box(
        modifier
            .clip(RoundedCornerShape(3.dp))
            .background(BlueC.copy(alpha = 0.12f))
            .border(0.5.dp, BlueC.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(text, color = BlueC, fontFamily = ShareTechMono, fontSize = 8.sp)
    }
}
