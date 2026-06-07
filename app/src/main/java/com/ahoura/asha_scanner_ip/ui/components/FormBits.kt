package com.ahoura.asha_scanner_ip.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import com.ahoura.asha_scanner_ip.ui.i18n.Lang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalLang
import com.ahoura.asha_scanner_ip.ui.theme.Accent
import com.ahoura.asha_scanner_ip.ui.theme.AccentBorder
import com.ahoura.asha_scanner_ip.ui.theme.AccentDim
import com.ahoura.asha_scanner_ip.ui.theme.AccentMuted
import com.ahoura.asha_scanner_ip.ui.theme.Background
import com.ahoura.asha_scanner_ip.ui.theme.BlueC
import com.ahoura.asha_scanner_ip.ui.theme.BorderC
import com.ahoura.asha_scanner_ip.ui.theme.ShareTechMono
import com.ahoura.asha_scanner_ip.ui.theme.SurfaceC
import com.ahoura.asha_scanner_ip.ui.theme.SurfaceAltC
import com.ahoura.asha_scanner_ip.ui.theme.TextMutedC
import com.ahoura.asha_scanner_ip.ui.theme.TextPrimaryC
import com.ahoura.asha_scanner_ip.ui.theme.TextSecondaryC

/** Row of preset chips; the last entry is styled as a blue "custom" chip. */
@Composable
fun PresetRow(
    options: List<String>,
    selectedIndex: Int,
    customIndex: Int = options.lastIndex,
    onSelect: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            val isCustom = i == customIndex
            val border = when {
                selected -> AccentBorder
                isCustom -> BlueC.copy(alpha = 0.4f)
                else -> BorderC
            }
            val bg = when {
                selected -> AccentMuted
                isCustom -> BlueC.copy(alpha = 0.05f)
                else -> SurfaceC
            }
            val fg = when {
                selected -> Accent
                isCustom -> BlueC
                else -> TextSecondaryC
            }
            Box(
                Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(5.dp))
                    .background(bg).border(0.5.dp, border, RoundedCornerShape(5.dp))
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = fg, fontFamily = ShareTechMono, fontSize = 12.sp)
            }
        }
    }
}

/** Key / value input row, 36dp tall, mono value. */
@Composable
fun KvInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(5.dp))
            .background(SurfaceC).border(0.5.dp, BorderC, RoundedCornerShape(5.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label.uppercase(), color = TextMutedC, fontFamily = ShareTechMono,
            fontSize = 9.sp, letterSpacing = 1.sp, modifier = Modifier.width(64.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier.weight(1f),
            textStyle = TextStyle(color = AccentDim, fontFamily = ShareTechMono, fontSize = 12.sp),
            cursorBrush = SolidColor(Accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, color = TextMutedC, fontFamily = ShareTechMono, fontSize = 12.sp)
                }
                inner()
            },
        )
    }
}

/** HTTP / TLS / TCP segmented selector. */
@Composable
fun ModeSegment(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp))
            .border(0.5.dp, BorderC, RoundedCornerShape(5.dp)).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selectedIndex
            Box(
                Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(4.dp))
                    .background(if (sel) AccentMuted else Background)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, color = if (sel) Accent else TextSecondaryC,
                    fontFamily = ShareTechMono, fontSize = 11.sp,
                )
            }
        }
    }
}

/** Pill toggle with a green dot when ON. */
@Composable
fun CyberToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier.size(width = 40.dp, height = 22.dp).clip(CircleShape)
            .background(if (checked) AccentMuted else SurfaceAltC)
            .border(0.5.dp, if (checked) AccentBorder else BorderC, CircleShape)
            .clickable { onChange(!checked) }
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(16.dp).clip(CircleShape).background(if (checked) Accent else TextMutedC))
    }
}

/** Label + toggle row. */
@Composable
fun ToggleRowCyber(label: String, checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    val lang = LocalLang.current
    Row(
        modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            if (lang == Lang.FA) label else label.uppercase(),
            color = TextPrimaryC,
            fontFamily = com.ahoura.asha_scanner_ip.ui.theme.monoFamily(lang),
            fontSize = if (lang == Lang.FA) 12.sp else 11.sp,
            letterSpacing = if (lang == Lang.FA) 0.sp else 1.sp,
        )
        CyberToggle(checked, onChange)
    }
}

/** Read-only chip used to display fixed settings. */
@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(4.dp)).background(SurfaceC)
            .border(0.5.dp, BorderC, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text, color = TextSecondaryC, fontFamily = ShareTechMono, fontSize = 9.sp)
    }
}
