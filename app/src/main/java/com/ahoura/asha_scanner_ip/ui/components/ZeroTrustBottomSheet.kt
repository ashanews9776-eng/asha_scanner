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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahoura.asha_scanner_ip.ui.ScanViewModel
import com.ahoura.asha_scanner_ip.ui.i18n.Lang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalLang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalStrings
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
fun ZeroTrustBottomSheet(
    vm: ScanViewModel,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val s = LocalStrings.current
    val lang = LocalLang.current
    val fa = lang == Lang.FA

    val team by vm.zeroTrustTeam.collectAsState()
    val email by vm.zeroTrustEmail.collectAsState()
    val token by vm.zeroTrustToken.collectAsState()
    val clientId by vm.zeroTrustClientId.collectAsState()
    val clientSecret by vm.zeroTrustClientSecret.collectAsState()
    val gateway by vm.zeroTrustGateway.collectAsState()
    val otpSent by vm.zeroTrustOtpSent.collectAsState()
    val busy by vm.zeroTrustBusy.collectAsState()
    val message by vm.zeroTrustMessage.collectAsState()

    var localTeam by remember(team) { mutableStateOf(team) }
    var localEmail by remember(email) { mutableStateOf(email) }
    var localCode by remember { mutableStateOf("") }
    var localClientId by remember(clientId) { mutableStateOf(clientId) }
    var localSecret by remember(clientSecret) { mutableStateOf(clientSecret) }
    var tabIndex by remember { mutableIntStateOf(0) } // 0 = Email OTP, 1 = Service Token

    val isEnrolled = token.isNotBlank() || (clientId.isNotBlank() && clientSecret.isNotBlank())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = SurfaceC,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 36.dp)
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Shield,
                        null,
                        tint = Accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (fa) s.zeroTrustTitle else s.zeroTrustTitle.uppercase(),
                        color = TextPrimaryC,
                        fontFamily = displayFamily(lang),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = if (fa) 0.sp else 1.sp,
                    )
                }
                Icon(
                    Icons.Filled.Close,
                    null,
                    tint = TextMutedC,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { onDismiss() }
                )
            }

            Spacer(Modifier.size(6.dp))
            Text(
                s.zeroTrustDesc,
                color = TextSecondaryC,
                fontFamily = if (fa) Vazirmatn else ShareTechMono,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )

            Spacer(Modifier.size(12.dp))

            // Enrolled status card (if already active)
            if (isEnrolled) {
                CyberCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(6.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Accent)
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    Text(
                                        s.zeroTrustEnrolled,
                                        color = Accent,
                                        fontFamily = monoFamily(lang),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                    )
                                }
                                if (team.isNotBlank()) {
                                    Spacer(Modifier.size(2.dp))
                                    Text(
                                        "${s.zeroTrustTeam}: $team",
                                        color = TextSecondaryC,
                                        fontFamily = ShareTechMono,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(RedC.copy(alpha = 0.1f))
                                    .border(0.5.dp, RedC.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .clickable { vm.clearZeroTrust() }
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Delete, null, tint = RedC, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.size(4.dp))
                                    Text(
                                        s.zeroTrustClear,
                                        color = RedC,
                                        fontFamily = monoFamily(lang),
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.size(12.dp))
            }

            // Mode Selector Tabs
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val tab0 = tabIndex == 0
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (tab0) AccentMuted else SurfaceC)
                        .border(0.5.dp, if (tab0) AccentBorder else BorderC, RoundedCornerShape(5.dp))
                        .clickable { tabIndex = 0 }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Mail, null, tint = if (tab0) Accent else TextMutedC, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(
                            s.zeroTrustOtpMode,
                            color = if (tab0) Accent else TextSecondaryC,
                            fontFamily = if (fa) Vazirmatn else ShareTechMono,
                            fontSize = 11.sp,
                            fontWeight = if (tab0) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }

                val tab1 = tabIndex == 1
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (tab1) AccentMuted else SurfaceC)
                        .border(0.5.dp, if (tab1) AccentBorder else BorderC, RoundedCornerShape(5.dp))
                        .clickable { tabIndex = 1 }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.VpnKey, null, tint = if (tab1) Accent else TextMutedC, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(
                            s.zeroTrustServiceToken,
                            color = if (tab1) Accent else TextSecondaryC,
                            fontFamily = if (fa) Vazirmatn else ShareTechMono,
                            fontSize = 11.sp,
                            fontWeight = if (tab1) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }

            Spacer(Modifier.size(12.dp))

            // Form Content
            CyberCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Common Team Name input
                    KvInput(
                        label = s.zeroTrustTeam,
                        value = localTeam,
                        onValueChange = { localTeam = it },
                        placeholder = "e.g. myteam",
                    )

                    if (tabIndex == 0) {
                        // Email OTP Flow
                        KvInput(
                            label = s.zeroTrustEmail,
                            value = localEmail,
                            onValueChange = { localEmail = it },
                            placeholder = "user@company.com",
                            keyboardType = KeyboardType.Email,
                        )

                        if (otpSent) {
                            KvInput(
                                label = s.zeroTrustCode,
                                value = localCode,
                                onValueChange = { localCode = it },
                                placeholder = "6-digit code",
                                keyboardType = KeyboardType.Number,
                            )

                            Spacer(Modifier.size(4.dp))
                            ScanButton(
                                text = s.zeroTrustVerify,
                                icon = Icons.Filled.Check,
                                active = localCode.trim().length >= 4 && !busy,
                                onClick = {
                                    vm.confirmZeroTrustCode(localCode.trim())
                                }
                            )
                        } else {
                            Spacer(Modifier.size(4.dp))
                            ScanButton(
                                text = s.zeroTrustSendCode,
                                icon = Icons.Filled.Mail,
                                active = localTeam.isNotBlank() && localEmail.isNotBlank() && !busy,
                                onClick = {
                                    vm.requestZeroTrustCode(localTeam.trim(), localEmail.trim())
                                }
                            )
                        }
                    } else {
                        // Service Token Flow
                        KvInput(
                            label = s.zeroTrustClientId,
                            value = localClientId,
                            onValueChange = { localClientId = it },
                            placeholder = "CF-Access-Client-Id",
                        )
                        KvInput(
                            label = s.zeroTrustClientSecret,
                            value = localSecret,
                            onValueChange = { localSecret = it },
                            placeholder = "CF-Access-Client-Secret",
                        )

                        Spacer(Modifier.size(4.dp))
                        ScanButton(
                            text = if (fa) "ذخیره توکن دسترسی" else "SAVE SERVICE TOKEN",
                            icon = Icons.Filled.Check,
                            active = localTeam.isNotBlank() && localClientId.isNotBlank() && localSecret.isNotBlank() && !busy,
                            onClick = {
                                vm.setZeroTrustServiceToken(localTeam.trim(), localClientId.trim(), localSecret.trim())
                            }
                        )
                    }

                    // Gateway DNS toggle
                    HorizontalDivider(color = BorderC, thickness = 0.5.dp)
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.zeroTrustGateway,
                                color = Accent,
                                fontFamily = displayFamily(lang),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                s.zeroTrustGatewayDesc,
                                color = TextSecondaryC,
                                fontFamily = if (fa) Vazirmatn else ShareTechMono,
                                fontSize = 10.sp,
                            )
                        }
                        CyberToggle(checked = gateway, onChange = { vm.setZeroTrustGateway(it) })
                    }
                }
            }

            // Busy loader
            if (busy) {
                Spacer(Modifier.size(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Accent,
                    trackColor = BorderC,
                )
            }

            // Message readout
            message?.let { msg ->
                Spacer(Modifier.size(8.dp))
                val isErr = msg.startsWith("Error", ignoreCase = true) || msg.startsWith("Verification failed", ignoreCase = true)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (isErr) RedC.copy(alpha = 0.1f) else AccentMuted)
                        .border(0.5.dp, if (isErr) RedC else AccentBorder, RoundedCornerShape(5.dp))
                        .padding(8.dp),
                ) {
                    Text(
                        msg,
                        color = if (isErr) RedC else Accent,
                        fontFamily = ShareTechMono,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}
