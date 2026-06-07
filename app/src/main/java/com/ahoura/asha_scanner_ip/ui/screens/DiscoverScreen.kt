package com.ahoura.asha_scanner_ip.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahoura.asha_scanner_ip.ui.ScanViewModel
import com.ahoura.asha_scanner_ip.ui.components.CyberAppBar
import com.ahoura.asha_scanner_ip.ui.components.CyberCard
import com.ahoura.asha_scanner_ip.ui.components.InfoChip
import com.ahoura.asha_scanner_ip.ui.components.ScanButton
import com.ahoura.asha_scanner_ip.ui.components.SectionLabel
import com.ahoura.asha_scanner_ip.ui.i18n.Lang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalLang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalStrings
import com.ahoura.asha_scanner_ip.ui.theme.ShareTechMono
import com.ahoura.asha_scanner_ip.ui.theme.TextSecondaryC
import com.ahoura.asha_scanner_ip.ui.theme.displayFamily
import com.ahoura.asha_scanner_ip.ui.theme.monoFamily

@Composable
fun DiscoverScreen(vm: ScanViewModel, onBack: () -> Unit, onStart: () -> Unit) {
    val s = LocalStrings.current
    val lang = LocalLang.current
    Column(Modifier.fillMaxSize()) {
        CyberAppBar(title = s.discoverColos, onBack = onBack)
        Column(
            Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.size(8.dp))
            CyberCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                Column {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Filled.Place, null, tint = com.ahoura.asha_scanner_ip.ui.theme.Accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(10.dp))
                        Text(
                            if (lang == Lang.FA) s.popDiscovery else s.popDiscovery.uppercase(),
                            color = com.ahoura.asha_scanner_ip.ui.theme.TextPrimaryC,
                            fontFamily = displayFamily(lang),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 16.sp,
                            letterSpacing = if (lang == Lang.FA) 0.sp else 1.sp,
                        )
                    }
                    Spacer(Modifier.size(10.dp))
                    Text(
                        s.popDiscoveryDesc,
                        color = TextSecondaryC, fontFamily = monoFamily(lang), fontSize = 12.sp, lineHeight = 19.sp,
                    )
                }
            }
            SectionLabel(s.specs)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                InfoChip("300 IPs"); InfoChip("HTTP"); InfoChip("2 TRIES"); InfoChip("5s"); InfoChip("GROUP BY COLO")
            }
        }
        Column(Modifier.padding(12.dp)) {
            ScanButton(text = "▶  ${s.startDiscovery}", icon = Icons.Filled.PlayArrow, onClick = onStart)
        }
    }
}
