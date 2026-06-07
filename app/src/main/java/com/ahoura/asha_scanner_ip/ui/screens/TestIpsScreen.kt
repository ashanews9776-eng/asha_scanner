package com.ahoura.asha_scanner_ip.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahoura.asha_scanner_ip.core.model.ProbeMode
import com.ahoura.asha_scanner_ip.ui.ScanViewModel
import com.ahoura.asha_scanner_ip.ui.i18n.LocalStrings
import com.ahoura.asha_scanner_ip.ui.components.CyberAppBar
import com.ahoura.asha_scanner_ip.ui.components.CyberCard
import com.ahoura.asha_scanner_ip.ui.components.GhostFileButton
import com.ahoura.asha_scanner_ip.ui.components.InfoChip
import com.ahoura.asha_scanner_ip.ui.components.Pill
import com.ahoura.asha_scanner_ip.ui.components.ScanButton
import com.ahoura.asha_scanner_ip.ui.components.SectionLabel
import com.ahoura.asha_scanner_ip.ui.theme.Accent
import com.ahoura.asha_scanner_ip.ui.theme.ShareTechMono
import com.ahoura.asha_scanner_ip.ui.theme.TextMutedC

@Composable
fun TestIpsScreen(vm: ScanViewModel, onBack: () -> Unit, onStart: () -> Unit) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val count = state.scanConfig.explicitIps.size
    val s = LocalStrings.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()?.let { vm.onTestIpsChange(it) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        CyberAppBar(title = s.testIps, onBack = onBack, trailing = { Pill("$count ${s.ipsSuffix}") })
        Column(
            Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.size(4.dp))
            SectionLabel(s.pasteIps)
            CyberCard(Modifier.fillMaxWidth()) {
                BasicTextField(
                    value = state.testIpsText,
                    onValueChange = vm::onTestIpsChange,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                    textStyle = TextStyle(color = Accent, fontFamily = ShareTechMono, fontSize = 13.sp),
                    cursorBrush = SolidColor(Accent),
                    decorationBox = { inner ->
                        if (state.testIpsText.isEmpty()) {
                            Text("104.21.0.5\n172.67.1.9\n188.114.96.3", color = TextMutedC, fontFamily = ShareTechMono, fontSize = 13.sp)
                        }
                        inner()
                    },
                )
            }
            GhostFileButton(s.loadFile, icon = Icons.Filled.FileOpen) { picker.launch("text/*") }

            Spacer(Modifier.size(4.dp))
            SectionLabel(s.fixedSettings)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                InfoChip("HTTP"); InfoChip("443"); InfoChip("6 TRIES")
                InfoChip("10s"); InfoChip("20 WORKERS"); InfoChip("speed.cloudflare.com")
            }
        }
        Column(Modifier.padding(12.dp)) {
            ScanButton(text = "▶  ${s.startTest}", icon = Icons.Filled.PlayArrow, enabled = count > 0) {
                vm.updateScanConfig {
                    it.copy(mode = ProbeMode.HTTP, ports = listOf(443), tries = 6, timeoutMs = 10_000, concurrency = 20, speedTest = true, top = 50)
                }
                onStart()
            }
        }
    }
}
