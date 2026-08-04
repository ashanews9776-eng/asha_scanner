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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahoura.asha_scanner_ip.core.model.ScanTier
import com.ahoura.asha_scanner_ip.ui.i18n.Lang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalLang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalStrings
import com.ahoura.asha_scanner_ip.ui.theme.Accent
import com.ahoura.asha_scanner_ip.ui.theme.AccentBorder
import com.ahoura.asha_scanner_ip.ui.theme.AccentDim
import com.ahoura.asha_scanner_ip.ui.theme.AccentMuted
import com.ahoura.asha_scanner_ip.ui.theme.Background
import com.ahoura.asha_scanner_ip.ui.theme.BlueC
import com.ahoura.asha_scanner_ip.ui.theme.BorderC
import com.ahoura.asha_scanner_ip.ui.theme.ShareTechMono
import com.ahoura.asha_scanner_ip.ui.theme.SurfaceC
import com.ahoura.asha_scanner_ip.ui.theme.TextFadedC
import com.ahoura.asha_scanner_ip.ui.theme.TextMutedC
import com.ahoura.asha_scanner_ip.ui.theme.TextPrimaryC
import com.ahoura.asha_scanner_ip.ui.theme.TextSecondaryC
import com.ahoura.asha_scanner_ip.ui.theme.Vazirmatn
import com.ahoura.asha_scanner_ip.ui.theme.displayFamily
import com.ahoura.asha_scanner_ip.ui.theme.monoFamily

@Composable
fun TierSelector(
    selected: ScanTier,
    onSelect: (ScanTier) -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalStrings.current
    val lang = LocalLang.current
    val fa = lang == Lang.FA
    val tiers = ScanTier.entries.filter { it != ScanTier.CUSTOM }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tiers.forEach { tier ->
                val sel = tier == selected
                val name = when (tier) {
                    ScanTier.TURBO -> s.tierTurbo
                    ScanTier.BALANCED -> s.tierBalanced
                    ScanTier.THOROUGH -> s.tierThorough
                    ScanTier.STEALTH -> s.tierStealth
                    ScanTier.IRONCLAD -> s.tierIronclad
                    else -> ""
                }
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(if (sel) AccentMuted else SurfaceC)
                        .border(0.5.dp, if (sel) AccentBorder else BorderC, RoundedCornerShape(6.dp))
                        .clickable { onSelect(tier) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name, color = if (sel) Accent else TextSecondaryC,
                        fontFamily = monoFamily(lang), fontSize = 11.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        letterSpacing = if (fa) 0.sp else 0.5.sp
                    )
                }
            }
            // Custom option if needed
            val customSel = selected == ScanTier.CUSTOM
            Box(
                Modifier.clip(RoundedCornerShape(6.dp))
                    .background(if (customSel) AccentMuted else SurfaceC)
                    .border(0.5.dp, if (customSel) AccentBorder else BorderC, RoundedCornerShape(6.dp))
                    .clickable { onSelect(ScanTier.CUSTOM) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    s.tierCustom, color = if (customSel) Accent else TextSecondaryC,
                    fontFamily = monoFamily(lang), fontSize = 11.sp,
                    fontWeight = if (customSel) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
        
        // Tier Description
        val desc = when (selected) {
            ScanTier.TURBO -> s.tierTurboDesc
            ScanTier.BALANCED -> s.tierBalancedDesc
            ScanTier.THOROUGH -> s.tierThoroughDesc
            ScanTier.STEALTH -> s.tierStealthDesc
            ScanTier.IRONCLAD -> s.tierIroncladDesc
            ScanTier.CUSTOM -> s.tierCustomDesc
        }
        Text(
            desc, color = TextMutedC, fontFamily = if (fa) Vazirmatn else ShareTechMono,
            fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp, start = 4.dp),
            lineHeight = 12.sp
        )
    }
}

/** Deep canvas + faint 20dp accent grid behind content. */
@Composable
fun GridBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Background)) {
        val stroke = BorderC.copy(alpha = 0.08f)
        Canvas(Modifier.fillMaxSize()) {
            val step = 20.dp.toPx()
            for (x in 0..(size.width / step).toInt()) {
                drawLine(stroke, start = androidx.compose.ui.geometry.Offset(x * step, 0f), end = androidx.compose.ui.geometry.Offset(x * step, size.height))
            }
            for (y in 0..(size.height / step).toInt()) {
                drawLine(stroke, start = androidx.compose.ui.geometry.Offset(0f, y * step), end = androidx.compose.ui.geometry.Offset(size.width, y * step))
            }
        }
        content()
    }
}

@Composable
fun CyberCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(12.dp),
    bgColor: Color = SurfaceC,
    borderColor: Color = BorderC,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(bgColor)
            .border(0.5.dp, borderColor, RoundedCornerShape(8.dp)).padding(padding)
    ) {
        content()
    }
}

@Composable
fun SectionLabel(label: String, modifier: Modifier = Modifier, color: Color = AccentDim) {
    val lang = LocalLang.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 3.dp, height = 12.dp).background(color))
        Box(Modifier.size(8.dp))
        Text(
            label.uppercase(),
            color = color, fontFamily = monoFamily(lang), fontSize = 10.sp, letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    bgColor: Color = AccentMuted,
    borderColor: Color = AccentBorder,
    textColor: Color = Accent,
) {
    val lang = LocalLang.current
    Box(
        modifier.clip(RoundedCornerShape(4.dp)).background(bgColor)
            .border(0.5.dp, borderColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text, color = textColor, fontFamily = monoFamily(lang),
            fontSize = 9.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun CyberAppBar(
    title: String,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val lang = LocalLang.current
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)).background(SurfaceC)
                    .border(0.5.dp, BorderC, RoundedCornerShape(6.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimaryC, modifier = Modifier.size(18.dp))
            }
            Box(Modifier.size(12.dp))
        }
        Text(
            title.uppercase(),
            color = TextPrimaryC, fontFamily = displayFamily(lang), fontSize = 16.sp, letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun ScanButton(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f, label = "scale")
    val lang = LocalLang.current
    
    Box(
        modifier.fillMaxWidth().height(52.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Accent else Accent.copy(alpha = 0.2f))
            .clickable(enabled = active, interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                Box(Modifier.size(10.dp))
            }
            Text(
                text.uppercase(),
                color = Color.Black, fontFamily = displayFamily(lang), fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold, letterSpacing = if (lang == Lang.FA) 0.sp else 2.sp,
            )
        }
    }
}

@Composable
fun GhostFileButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    color: Color = BlueC,
    onClick: () -> Unit,
) {
    val lang = LocalLang.current
    Box(
        modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.08f))
            .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Box(Modifier.size(8.dp))
            Text(label.uppercase(), color = color, fontFamily = monoFamily(lang), fontSize = 11.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
fun MenuItemCard(
    icon: ImageVector,
    color: Color,
    title: String,
    desc: String,
    active: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceC)
            .border(0.5.dp, if (active) BorderC else BorderC.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .clickable(enabled = active, onClick = onClick).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            }
            Box(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title, color = if (active) TextPrimaryC else TextPrimaryC.copy(alpha = 0.5f),
                    fontFamily = displayFamily(LocalLang.current), fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                )
                Text(
                    desc, color = if (active) TextSecondaryC else TextSecondaryC.copy(alpha = 0.5f),
                    fontFamily = Vazirmatn, fontSize = 10.sp, lineHeight = 14.sp,
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = TextMutedC, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun ColoBadge(colo: String, modifier: Modifier = Modifier) {
    if (colo.isBlank()) return
    Box(
        modifier.clip(RoundedCornerShape(3.dp)).background(AccentMuted.copy(alpha = 0.4f))
            .border(0.5.dp, AccentBorder.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            colo, color = Accent, fontFamily = ShareTechMono, fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun KvInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
) {
    val lang = LocalLang.current
    Column(modifier.fillMaxWidth()) {
        Text(
            label.uppercase(),
            color = TextMutedC, fontFamily = monoFamily(lang), fontSize = 10.sp, letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
        )
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(SurfaceC)
                .border(0.5.dp, BorderC, RoundedCornerShape(6.dp)).padding(10.dp),
            textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimaryC, fontFamily = ShareTechMono, fontSize = 13.sp),
            cursorBrush = Brush.verticalGradient(listOf(Accent, Accent)),
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(placeholder, color = TextMutedC, fontFamily = monoFamily(lang), fontSize = 13.sp)
                }
                innerTextField()
            }
        )
    }
}
