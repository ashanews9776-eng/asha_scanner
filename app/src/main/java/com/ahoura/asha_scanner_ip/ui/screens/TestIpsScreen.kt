package com.ahoura.asha_scanner_ip.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahoura.asha_scanner_ip.core.model.ProbeMode
import com.ahoura.asha_scanner_ip.ui.ScanViewModel
import com.ahoura.asha_scanner_ip.ui.components.CyberAppBar
import com.ahoura.asha_scanner_ip.ui.components.CyberCard
import com.ahoura.asha_scanner_ip.ui.components.GhostFileButton
import com.ahoura.asha_scanner_ip.ui.components.InfoChip
import com.ahoura.asha_scanner_ip.ui.components.Pill
import com.ahoura.asha_scanner_ip.ui.components.ScanButton
import com.ahoura.asha_scanner_ip.ui.components.SectionLabel
import com.ahoura.asha_scanner_ip.ui.i18n.LocalLang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalStrings
import com.ahoura.asha_scanner_ip.ui.i18n.localizeDigits
import com.ahoura.asha_scanner_ip.ui.theme.Accent
import com.ahoura.asha_scanner_ip.ui.theme.AccentBorder
import com.ahoura.asha_scanner_ip.ui.theme.AccentMuted
import com.ahoura.asha_scanner_ip.ui.theme.BorderC
import com.ahoura.asha_scanner_ip.ui.theme.RedC
import com.ahoura.asha_scanner_ip.ui.theme.ShareTechMono
import com.ahoura.asha_scanner_ip.ui.theme.SurfaceC
import com.ahoura.asha_scanner_ip.ui.theme.TextMutedC
import com.ahoura.asha_scanner_ip.ui.theme.TextSecondaryC
import com.ahoura.asha_scanner_ip.ui.theme.monoFamily

@Composable
fun TestIpsScreen(vm: ScanViewModel, onBack: () -> Unit, onStart: () -> Unit) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val cfg = state.scanConfig
    val count = cfg.explicitIps.size
    val s = LocalStrings.current
    val lang = LocalLang.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()?.let { vm.onPastedTargetsChange(it) }
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
                    value = cfg.pastedTargets,
                    onValueChange = vm::onPastedTargetsChange,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp),
                    textStyle = TextStyle(color = Accent, fontFamily = ShareTechMono, fontSize = 13.sp),
                    cursorBrush = SolidColor(Accent),
                    decorationBox = { inner ->
                        if (cfg.pastedTargets.isEmpty()) {
                            Text("104.21.0.5\n172.67.1.0/24\ncloudflare.com\n188.114.96.1-50", color = TextMutedC, fontFamily = ShareTechMono, fontSize = 13.sp)
                        }
                        inner()
                    },
                )
            }

            // Live Target Counter & Domain Resolution Status
            if (state.isResolvingTargets) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Box(Modifier.size(6.dp).background(Accent, RoundedCornerShape(1.dp)))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        s.resolvingTargets,
                        color = Accent,
                        fontFamily = monoFamily(lang),
                        fontSize = 10.sp,
                    )
                }
            } else if (state.targetPreview != null) {
                val preview = state.targetPreview!!
                val countText = s.targetPreviewCount
                    .replace("{0}", preview.count.toString().localizeDigits(lang))
                    .replace("{1}", preview.domains.toString().localizeDigits(lang))

                Column(Modifier.padding(vertical = 2.dp)) {
                    Text(
                        text = "✓ $countText",
                        color = Accent,
                        fontFamily = monoFamily(lang),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (preview.skipped.isNotEmpty()) {
                        val skippedText = s.skippedTargets.replace("{0}", preview.skipped.size.toString().localizeDigits(lang))
                        Text(
                            text = "⚠ $skippedText",
                            color = RedC,
                            fontFamily = monoFamily(lang),
                            fontSize = 10.sp,
                        )
                    }
                }
            }

            GhostFileButton(s.loadFile, icon = Icons.Filled.FileOpen) { picker.launch("text/*") }

            Spacer(Modifier.size(4.dp))
            SectionLabel(s.mode)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ProbeMode.entries.forEach { mode ->
                    val sel = cfg.mode == mode
                    Box(
                        Modifier.clip(RoundedCornerShape(4.dp))
                            .background(if (sel) Accent.copy(alpha = 0.15f) else Color.Transparent)
                            .border(0.5.dp, if (sel) Accent else BorderC, RoundedCornerShape(4.dp))
                            .clickable { vm.updateScanConfig { it.copy(mode = mode) } }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(mode.wire.uppercase(), color = if (sel) Accent else TextMutedC, fontFamily = ShareTechMono, fontSize = 11.sp)
                    }
                }
            }

            // Skip Reachability Probe (Direct Speed Test)
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(SurfaceC)
                    .border(0.5.dp, BorderC, RoundedCornerShape(4.dp))
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.skipReachability, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(s.skipReachabilityDesc, color = TextSecondaryC, fontSize = 10.sp, lineHeight = 13.sp)
                    }
                    Box(
                        Modifier
                            .size(38.dp, 20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (cfg.phase2Only) AccentMuted else SurfaceC)
                            .border(0.5.dp, if (cfg.phase2Only) AccentBorder else BorderC, RoundedCornerShape(4.dp))
                            .clickable { vm.setPhase2Only(!cfg.phase2Only) }
                            .padding(2.dp),
                    ) {
                        Box(
                            Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (cfg.phase2Only) Accent else TextMutedC)
                                .align(if (cfg.phase2Only) Alignment.CenterEnd else Alignment.CenterStart),
                        )
                    }
                }
            }

            Spacer(Modifier.size(4.dp))
            SectionLabel(s.fixedSettings)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val port = cfg.ports.firstOrNull() ?: 443
                InfoChip(cfg.mode.wire.uppercase())
                InfoChip("$port")
                InfoChip(if (cfg.gentle) "GENTLE" else "FAST")
                InfoChip("${cfg.timeoutMs / 1000}s")
                InfoChip("${cfg.concurrency} WORKERS")
            }
        }
        Column(Modifier.padding(12.dp)) {
            ScanButton(text = s.startTest, icon = Icons.Filled.PlayArrow, active = count > 0) {
                vm.updateScanConfig {
                    it.copy(
                        tries = if (it.phase2Only) 1 else 4,
                        timeoutMs = if (it.gentle) maxOf(it.timeoutMs, 6000L) else 5_000L,
                        speedTest = true,
                        top = 50,
                        smartStop = false
                    )
                }
                onStart()
            }
        }
    }
}
