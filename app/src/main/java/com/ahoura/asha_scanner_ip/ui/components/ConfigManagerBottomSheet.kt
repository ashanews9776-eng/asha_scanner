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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    val warpKeys by vm.warpKeysStatus.collectAsState()
    val isRenewingKeys by vm.isRenewingWarpKeys.collectAsState()
    val renewalMessage by vm.warpKeyRenewalMessage.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showKeysCard by remember { mutableStateOf(false) }
    var autoAddedNotification by remember { mutableStateOf(false) }
    var addMode by remember { mutableIntStateOf(0) } // 0 = Link, 1 = StormDNS Manual
    var inputLink by remember { mutableStateOf("") }
    var inputName by remember { mutableStateOf("") }
    var stormDomain by remember { mutableStateOf("") }
    var stormKey by remember { mutableStateOf("") }
    var stormMethod by remember { mutableStateOf("1") }

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

            Spacer(Modifier.size(10.dp))

            // Quick Actions Bar (Auto-Generate Aether Profiles & WARP Keys Panel)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Accent.copy(alpha = 0.12f))
                        .border(0.5.dp, AccentBorder, RoundedCornerShape(6.dp))
                        .clickable {
                            vm.autoGenerateAetherConfigs()
                            autoAddedNotification = true
                        }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Bolt,
                            null,
                            tint = Accent,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            s.aetherAutoConfigs,
                            color = Accent,
                            fontFamily = if (fa) Vazirmatn else ShareTechMono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                        )
                    }
                }

                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (showKeysCard) AccentMuted else BlueC.copy(alpha = 0.12f))
                        .border(0.5.dp, if (showKeysCard) AccentBorder else BlueC.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .clickable {
                            showKeysCard = !showKeysCard
                            if (showKeysCard) vm.refreshWarpKeys()
                        }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.VpnKey,
                            null,
                            tint = if (showKeysCard) Accent else BlueC,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            if (fa) "کلیدها" else "Keys",
                            color = if (showKeysCard) Accent else BlueC,
                            fontFamily = if (fa) Vazirmatn else ShareTechMono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.size(2.dp))
                        Icon(
                            if (showKeysCard) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            null,
                            tint = if (showKeysCard) Accent else BlueC,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }

            if (autoAddedNotification) {
                Spacer(Modifier.size(6.dp))
                Text(
                    s.aetherAutoConfigsAdded,
                    color = Accent,
                    fontFamily = if (fa) Vazirmatn else ShareTechMono,
                    fontSize = 10.sp,
                )
            }

            // WARP Keys status card
            if (showKeysCard) {
                Spacer(Modifier.size(8.dp))
                CyberCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    s.aetherKeysTitle,
                                    color = Accent,
                                    fontFamily = if (fa) Vazirmatn else ShareTechMono,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                )
                                val activeCount = warpKeys.count { it.isPresent }
                                Text(
                                    if (fa) "$activeCount از ۵ هویت اختصاصی فعال است" else "$activeCount of 5 identities active",
                                    color = if (activeCount > 0) AccentDim else TextMutedC,
                                    fontFamily = if (fa) Vazirmatn else ShareTechMono,
                                    fontSize = 9.5.sp,
                                )
                            }

                            Spacer(Modifier.size(8.dp))

                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(if (isRenewingKeys) SurfaceC else Accent.copy(alpha = 0.15f))
                                    .border(0.5.dp, if (isRenewingKeys) BorderC else AccentBorder, RoundedCornerShape(5.dp))
                                    .clickable(enabled = !isRenewingKeys) { vm.renewAllWarpKeys() }
                                    .padding(horizontal = 9.dp, vertical = 6.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isRenewingKeys) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(11.dp),
                                            color = Accent,
                                            strokeWidth = 1.5.dp,
                                        )
                                        Spacer(Modifier.size(5.dp))
                                    } else {
                                        Icon(
                                            Icons.Filled.Refresh,
                                            null,
                                            tint = Accent,
                                            modifier = Modifier.size(12.dp),
                                        )
                                        Spacer(Modifier.size(3.dp))
                                    }
                                    Text(
                                        if (isRenewingKeys) (if (fa) "در حال استعلام…" else "Renewing…") else s.aetherRenewKeys,
                                        color = if (isRenewingKeys) TextMutedC else Accent,
                                        fontFamily = if (fa) Vazirmatn else ShareTechMono,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }

                        if (renewalMessage.isNotBlank()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (renewalMessage.contains("خطا") || renewalMessage.contains("failed") || renewalMessage.contains("Refused")) RedC.copy(alpha = 0.15f) else BlueC.copy(alpha = 0.12f))
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    renewalMessage,
                                    color = if (renewalMessage.contains("خطا") || renewalMessage.contains("failed") || renewalMessage.contains("Refused")) Color(0xFFFF8A80) else BlueC,
                                    fontFamily = if (fa) Vazirmatn else ShareTechMono,
                                    fontSize = 9.5.sp,
                                )
                            }
                        }

                        warpKeys.forEach { key ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SurfaceC.copy(alpha = 0.5f))
                                    .border(0.5.dp, BorderC, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (key.isPresent) Accent else Color(0xFFFFB300))
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    Text(
                                        key.type,
                                        color = TextPrimaryC,
                                        fontFamily = if (fa) Vazirmatn else ShareTechMono,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Text(
                                    if (key.isPresent) {
                                        if (key.ipv4.isNotBlank()) "${key.ipv4} ✓" else (key.deviceId.take(8) + "… ✓")
                                    } else {
                                        s.aetherKeyMissing
                                    },
                                    color = if (key.isPresent) Accent else Color(0xFFFFB300),
                                    fontFamily = if (fa && !key.isPresent) Vazirmatn else ShareTechMono,
                                    fontSize = 9.sp,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.size(10.dp))

            // Add new profile input
            if (showAddDialog) {
                CyberCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(4.dp)) {
                        // Mode Selector Tabs
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val tab0 = addMode == 0
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (tab0) AccentMuted else SurfaceC)
                                    .border(0.5.dp, if (tab0) AccentBorder else BorderC, RoundedCornerShape(4.dp))
                                    .clickable { addMode = 0 }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (fa) "🔗 لینک اشتراک" else "🔗 SHARE LINK",
                                    color = if (tab0) Accent else TextSecondaryC,
                                    fontFamily = if (fa) Vazirmatn else ShareTechMono,
                                    fontSize = 11.sp,
                                    fontWeight = if (tab0) FontWeight.Bold else FontWeight.Normal,
                                )
                            }

                            val tab1 = addMode == 1
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (tab1) AccentMuted else SurfaceC)
                                    .border(0.5.dp, if (tab1) AccentBorder else BorderC, RoundedCornerShape(4.dp))
                                    .clickable { addMode = 1 }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (fa) "⚡ تنظیم StormDNS" else "⚡ STORMDNS MANUAL",
                                    color = if (tab1) Accent else TextSecondaryC,
                                    fontFamily = if (fa) Vazirmatn else ShareTechMono,
                                    fontSize = 11.sp,
                                    fontWeight = if (tab1) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }

                        Spacer(Modifier.size(10.dp))

                        if (addMode == 0) {
                            // Mode 0: Paste share link (vless / trojan / stormdns)
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    if (fa) "پشتیبانی از vless، trojan، stormdns و aether://" else "Supports vless://, trojan://, stormdns://, aether://",
                                    color = TextMutedC,
                                    fontFamily = if (fa) Vazirmatn else ShareTechMono,
                                    fontSize = 10.sp,
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
                            Spacer(Modifier.size(6.dp))
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
                                text = s.saveConfig,
                                icon = Icons.Filled.Check,
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
                        } else {
                            // Mode 1: Manual StormDNS form
                            Text(
                                if (fa) "مشخصات سرور DNS Tunnel (StormDNS)" else "StormDNS Server Details",
                                color = Accent,
                                fontFamily = if (fa) Vazirmatn else ShareTechMono,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.size(6.dp))
                            KvInput(
                                label = "domain",
                                value = stormDomain,
                                onValueChange = { stormDomain = it },
                                placeholder = "ns1.example.com",
                            )
                            Spacer(Modifier.size(6.dp))
                            KvInput(
                                label = "key",
                                value = stormKey,
                                onValueChange = { stormKey = it },
                                placeholder = if (fa) "کلید رمزنگاری" else "Encryption Key",
                            )
                            Spacer(Modifier.size(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    KvInput(
                                        label = "method",
                                        value = stormMethod,
                                        onValueChange = { stormMethod = it.filter { c -> c.isDigit() } },
                                        placeholder = "1 (Default)",
                                    )
                                }
                                Column(Modifier.weight(1.5f)) {
                                    KvInput(
                                        label = "name",
                                        value = inputName,
                                        onValueChange = { inputName = it },
                                        placeholder = if (fa) "نام سرور (اختیاری)" else "Name (Optional)",
                                    )
                                }
                            }
                            Spacer(Modifier.size(10.dp))
                            val isManualValid = stormDomain.trim().isNotBlank() && stormKey.trim().isNotBlank()
                            ScanButton(
                                text = if (fa) "ذخیره کانفیگ StormDNS" else "SAVE STORMDNS PROFILE",
                                icon = Icons.Filled.Check,
                                active = isManualValid,
                                onClick = {
                                    if (isManualValid) {
                                        val link = ProxyParser.exportStormDns(
                                            name = inputName.ifBlank { stormDomain.trim() },
                                            domain = stormDomain.trim(),
                                            encryptionKey = stormKey.trim(),
                                            encryptionMethod = stormMethod.toIntOrNull() ?: 1,
                                        )
                                        vm.addProfile(link, inputName.ifBlank { stormDomain.trim() })
                                        stormDomain = ""
                                        stormKey = ""
                                        inputName = ""
                                        showAddDialog = false
                                    }
                                }
                            )
                        }
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
    val clipboard = LocalClipboardManager.current
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
                val tag = when (profile.proxy.protocol) {
                    com.ahoura.asha_scanner_ip.core.model.Protocol.STORMDNS -> "DNS TUNNEL"
                    com.ahoura.asha_scanner_ip.core.model.Protocol.AETHER -> "AETHER"
                    else -> profile.proxy.protocol.scheme.uppercase()
                }
                Pill(tag)
                if (profile.proxy.fingerprint.isNotBlank()) {
                    Spacer(Modifier.size(4.dp))
                    Pill(profile.proxy.fingerprint)
                }
                if (profile.proxy.dialMode.isNotBlank()) {
                    Spacer(Modifier.size(4.dp))
                    Pill(profile.proxy.dialMode.uppercase())
                }
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

        // Copy share link button
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(profile.raw))
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.ContentCopy, null, tint = TextMutedC, modifier = Modifier.size(15.dp))
        }

        Spacer(Modifier.size(4.dp))

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
