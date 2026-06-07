package com.ahoura.asha_scanner_ip.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.ahoura.asha_scanner_ip.ui.theme.ShareTechMono
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
            ScanButton(text = "▶  ${s.startScan}", icon = Icons.Filled.PlayArrow, onClick = onStart)
        }
    }
}
