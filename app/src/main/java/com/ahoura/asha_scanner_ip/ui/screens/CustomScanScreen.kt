package com.ahoura.asha_scanner_ip.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahoura.asha_scanner_ip.ui.ScanViewModel
import com.ahoura.asha_scanner_ip.ui.components.CyberAppBar
import com.ahoura.asha_scanner_ip.ui.components.KvInput
import com.ahoura.asha_scanner_ip.ui.components.Pill
import com.ahoura.asha_scanner_ip.ui.components.ScanButton
import com.ahoura.asha_scanner_ip.ui.components.SectionLabel
import com.ahoura.asha_scanner_ip.ui.i18n.Lang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalLang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalStrings
import com.ahoura.asha_scanner_ip.ui.theme.AccentDim
import com.ahoura.asha_scanner_ip.ui.theme.RedC
import com.ahoura.asha_scanner_ip.ui.theme.Accent
import com.ahoura.asha_scanner_ip.ui.theme.AccentBorder
import com.ahoura.asha_scanner_ip.ui.theme.AccentMuted
import com.ahoura.asha_scanner_ip.ui.theme.BorderC
import com.ahoura.asha_scanner_ip.ui.theme.SurfaceC
import com.ahoura.asha_scanner_ip.ui.theme.TextMutedC
import com.ahoura.asha_scanner_ip.ui.theme.monoFamily

@Composable
fun CustomScanScreen(vm: ScanViewModel, onBack: () -> Unit, onStart: () -> Unit) {
    val state by vm.state.collectAsState()
    val cfg = state.scanConfig
    val s = LocalStrings.current
    val lang = LocalLang.current

    Column(Modifier.fillMaxSize()) {
        CyberAppBar(title = s.customScan, onBack = onBack, trailing = { Pill(cfg.mode.wire.uppercase()) })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScanSettings(vm = vm, cfg = cfg, showAdvancedDefault = true)

            Spacer(Modifier.size(14.dp))
            SectionLabel(s.loadIsp)
            Spacer(Modifier.size(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IspChip(s.mci, Modifier.weight(1f)) { 
                    vm.onCustomRangesChange(com.ahoura.asha_scanner_ip.core.ipsrc.IspDatabase.MCI.joinToString("\n")) 
                }
                IspChip(s.irancell, Modifier.weight(1f)) { 
                    vm.onCustomRangesChange(com.ahoura.asha_scanner_ip.core.ipsrc.IspDatabase.IRANCELL.joinToString("\n")) 
                }
            }
            Spacer(Modifier.size(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IspChip(s.rightel, Modifier.weight(1f)) { 
                    vm.onCustomRangesChange(com.ahoura.asha_scanner_ip.core.ipsrc.IspDatabase.RIGHTEL.joinToString("\n")) 
                }
                IspChip(s.mokhaberat, Modifier.weight(1f)) { 
                    vm.onCustomRangesChange(com.ahoura.asha_scanner_ip.core.ipsrc.IspDatabase.MOKHABERAT.joinToString("\n")) 
                }
            }

            Spacer(Modifier.size(14.dp))
            SectionLabel(s.cfRanges)
            Spacer(Modifier.size(6.dp))
            KvInput(
                label = "cidr",
                value = state.customRangesText,
                onValueChange = vm::onCustomRangesChange,
                placeholder = "104.21.0.0/24\n172.67.0.0/16",
            )

            Spacer(Modifier.size(14.dp))
            SectionLabel(s.pasteIps + " (${s.configOptional})")
            Spacer(Modifier.size(6.dp))
            KvInput(
                label = "ips",
                value = state.testIpsText,
                onValueChange = vm::onTestIpsChange,
                placeholder = "1.1.1.1, 8.8.8.8 ...",
            )

            Spacer(Modifier.size(14.dp))
            SectionLabel(s.configOptional)
            Spacer(Modifier.size(6.dp))
            KvInput(
                label = "vless",
                value = state.configText,
                onValueChange = vm::onConfigChange,
                placeholder = s.configPlaceholder,
            )
            val proxy = state.parsedProxy
            when {
                proxy != null -> {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "✓ ${proxy.protocol.scheme.uppercase()} · ${s.configParsed}",
                        color = AccentDim, fontFamily = monoFamily(lang), fontSize = 9.sp,
                        letterSpacing = if (lang == Lang.FA) 0.sp else 0.5.sp, lineHeight = 14.sp,
                    )
                }
                state.parseError -> {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        s.configUnrecognized,
                        color = RedC, fontFamily = monoFamily(lang), fontSize = 9.sp,
                        letterSpacing = if (lang == Lang.FA) 0.sp else 0.5.sp, lineHeight = 14.sp,
                    )
                }
            }
            Spacer(Modifier.size(6.dp))
        }
        Column(Modifier.padding(12.dp)) {
            ScanButton(text = s.startScan, icon = Icons.Filled.PlayArrow, onClick = onStart)
        }
    }
}

@Composable
private fun IspChip(label: String, modifier: Modifier, onClick: () -> Unit) {
    val lang = LocalLang.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(SurfaceC)
            .border(0.5.dp, BorderC, RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Accent, fontFamily = com.ahoura.asha_scanner_ip.ui.theme.monoFamily(lang), fontSize = 10.sp)
    }
}
