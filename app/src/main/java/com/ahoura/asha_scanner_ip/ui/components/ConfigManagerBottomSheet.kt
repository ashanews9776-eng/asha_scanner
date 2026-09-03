package com.ahoura.asha_scanner_ip.ui.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahoura.asha_scanner_ip.core.parser.ProxyParser
import com.ahoura.asha_scanner_ip.core.vpn.VpnProfile
import com.ahoura.asha_scanner_ip.ui.ScanViewModel
import com.ahoura.asha_scanner_ip.ui.i18n.Lang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalLang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalStrings
import com.ahoura.asha_scanner_ip.ui.i18n.localizeDigits
import com.ahoura.asha_scanner_ip.ui.theme.Accent
import com.ahoura.asha_scanner_ip.ui.theme.AccentBorder
import com.ahoura.asha_scanner_ip.ui.theme.AccentDim
import com.ahoura.asha_scanner_ip.ui.theme.AccentMuted
import com.ahoura.asha_scanner_ip.ui.theme.BlueC
import com.ahoura.asha_scanner_ip.ui.theme.BorderC
import com.ahoura.asha_scanner_ip.ui.theme.RedC
import com.ahoura.asha_scanner_ip.ui.theme.ShareTechMono
import com.ahoura.asha_scanner_ip.ui.theme.SurfaceC
import com.ahoura.asha_scanner_ip.ui.theme.TextMutedC
import com.ahoura.asha_scanner_ip.ui.theme.TextPrimaryC
import com.ahoura.asha_scanner_ip.ui.theme.TextSecondaryC
import com.ahoura.asha_scanner_ip.ui.theme.Vazirmatn
import com.ahoura.asha_scanner_ip.ui.theme.displayFamily
import com.ahoura.asha_scanner_ip.ui.theme.monoFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigManagerBottomSheet(
    vm: ScanViewModel,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current
    val s = LocalStrings.current
    val lang = LocalLang.current
    val fa = lang == Lang.FA

    val profiles by vm.savedProfiles.collectAsState()
    val activeProfile by vm.activeProfile.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var inputLink by remember { mutableStateOf("") }
    var inputName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = SurfaceC,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (fa) s.manageConfigs else s.manageConfigs.uppercase(),
                    color = TextPrimaryC,
                    fontFamily = displayFamily(lang),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = if (fa) 0.sp else 1.sp,
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(AccentMuted)
                        .border(0.5.dp, AccentBorder, RoundedCornerShape(5.dp))
                        .clickable { showAddDialog = !showAddDialog }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (showAddDialog) Icons.Filled.Check else Icons.Filled.Add,
                            null,
                            tint = Accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            if (showAddDialog) (if (fa) "بستن" else "CLOSE") else s.importConfig,
                            color = Accent,
                            fontFamily = monoFamily(lang),
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.size(12.dp))

            // Add new profile input
            if (showAddDialog) {
                CyberCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(4.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                s.importConfig,
                                color = Accent,
                                fontFamily = displayFamily(lang),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(BlueC.copy(alpha = 0.1f))
                                    .clickable {
                                        clipboard.getText()?.text?.let { inputLink = it }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.ContentPaste, null, tint = BlueC, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.size(4.dp))
                                Text(
                                    if (fa) "چسباندن" else "PASTE",
                                    color = BlueC,
                                    fontFamily = ShareTechMono,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Spacer(Modifier.size(8.dp))
                        KvInput(
                            label = "link",
                            value = inputLink,
                            onValueChange = { inputLink = it },
                            placeholder = s.pasteConfigPrompt,
                        )
                        Spacer(Modifier.size(6.dp))
                        KvInput(
                            label = "name",
                            value = inputName,
                            onValueChange = { inputName = it },
                            placeholder = if (fa) "نام دلخواه (اختیاری)" else "Profile Name (Optional)",
                        )
                        Spacer(Modifier.size(10.dp))
                        val isValid = ProxyParser.isSupported(inputLink)
                        ScanButton(
                            text = "✓  ${s.saveConfig}",
                            active = isValid,
                            onClick = {
                                if (isValid) {
                                    vm.addProfile(inputLink, inputName.ifBlank { null })
                                    inputLink = ""
                                    inputName = ""
                                    showAddDialog = false
                                }
                            }
                        )
                    }
                }
                Spacer(Modifier.size(12.dp))
            }

            // Profile list
            if (profiles.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("∅", color = TextMutedC, fontFamily = ShareTechMono, fontSize = 28.sp)
                        Spacer(Modifier.size(6.dp))
                        Text(
                            s.noActiveConfig,
                            color = TextSecondaryC,
                            fontFamily = if (fa) Vazirmatn else ShareTechMono,
                            fontSize = 12.sp,
                        )
                    }
                }
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(profiles, key = { it.id }) { p ->
                        val isSelected = p.id == activeProfile?.id
                        ProfileRow(
                            profile = p,
                            isSelected = isSelected,
                            onSelect = { vm.setActiveProfile(p.id) },
                            onDelete = { vm.deleteProfile(p.id) },
                            onTestPing = { vm.measurePing(p) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(
    profile: VpnProfile,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onTestPing: () -> Unit,
) {
    val lang = LocalLang.current
    val fa = lang == Lang.FA
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) AccentMuted else SurfaceC)
            .border(
                0.5.dp,
                if (isSelected) AccentBorder else BorderC,
                RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Selection radio/check
        Box(
            Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (isSelected) Accent else Color.Transparent)
                .border(1.dp, if (isSelected) Accent else TextMutedC, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(Icons.Filled.Check, null, tint = SurfaceC, modifier = Modifier.size(12.dp))
            }
        }

        Spacer(Modifier.size(10.dp))

        // Info
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    profile.name,
                    color = if (isSelected) Accent else TextPrimaryC,
                    fontFamily = ShareTechMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.size(6.dp))
                Pill(profile.proxy.protocol.scheme.uppercase())
            }
            Spacer(Modifier.size(2.dp))
            Text(
                profile.displayAddress,
                color = TextSecondaryC,
                fontFamily = ShareTechMono,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }

        // Ping measurement badge
        Box(
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(BlueC.copy(alpha = 0.08f))
                .clickable(onClick = onTestPing)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Speed, null, tint = BlueC, modifier = Modifier.size(12.dp))
                Spacer(Modifier.size(3.dp))
                Text(
                    if (profile.pingMs != null) "${profile.pingMs}".localizeDigits(lang) + " ms" else "PING",
                    color = BlueC,
                    fontFamily = ShareTechMono,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(Modifier.size(8.dp))

        // Delete button
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Delete, null, tint = RedC.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
        }
    }
}
