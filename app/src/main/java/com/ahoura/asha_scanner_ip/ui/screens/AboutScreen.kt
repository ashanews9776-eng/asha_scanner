package com.ahoura.asha_scanner_ip.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahoura.asha_scanner_ip.BuildConfig
import com.ahoura.asha_scanner_ip.R
import com.ahoura.asha_scanner_ip.ui.TELEGRAM_HANDLE
import com.ahoura.asha_scanner_ip.ui.TELEGRAM_URL
import com.ahoura.asha_scanner_ip.ui.components.CyberAppBar
import com.ahoura.asha_scanner_ip.ui.components.CyberCard
import com.ahoura.asha_scanner_ip.ui.components.Pill
import com.ahoura.asha_scanner_ip.ui.i18n.Lang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalLang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalStrings
import com.ahoura.asha_scanner_ip.ui.theme.Accent
import com.ahoura.asha_scanner_ip.ui.theme.BlueC
import com.ahoura.asha_scanner_ip.ui.theme.Rajdhani
import com.ahoura.asha_scanner_ip.ui.theme.ShareTechMono
import com.ahoura.asha_scanner_ip.ui.theme.TextMutedC
import com.ahoura.asha_scanner_ip.ui.theme.TextPrimaryC
import com.ahoura.asha_scanner_ip.ui.theme.TextSecondaryC
import com.ahoura.asha_scanner_ip.ui.theme.Vazirmatn
import com.ahoura.asha_scanner_ip.ui.theme.displayFamily
import com.ahoura.asha_scanner_ip.ui.theme.monoFamily

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val s = LocalStrings.current
    val lang = LocalLang.current
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    Column(Modifier.fillMaxSize()) {
        CyberAppBar(title = s.about, onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(Modifier.size(8.dp))
            Text("ASHA SCANNER", color = Accent, fontFamily = ShareTechMono, fontSize = 16.sp, letterSpacing = 2.sp)
            Pill("v${BuildConfig.VERSION_NAME} · IR-OPTIMIZED")
            Text(
                s.aboutBlurb,
                color = TextSecondaryC,
                fontFamily = monoFamily(lang),
                fontSize = 12.sp, lineHeight = if (lang == Lang.FA) 20.sp else 18.sp,
            )

            LinkRow(Icons.AutoMirrored.Filled.Send, BlueC, s.telegram, TELEGRAM_HANDLE) { open(TELEGRAM_URL) }

            DonateCard(
                onCopy = { addr ->
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("asha-donation", addr))
                    android.widget.Toast.makeText(context, s.donateCopied, android.widget.Toast.LENGTH_SHORT).show()
                },
            )

            CyberCard(Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        if (lang == Lang.FA) s.license else "LICENSE",
                        color = TextMutedC,
                        fontFamily = monoFamily(lang),
                        fontSize = if (lang == Lang.FA) 10.sp else 8.sp,
                        letterSpacing = if (lang == Lang.FA) 0.sp else 1.5.sp,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text("MIT", color = TextPrimaryC, fontFamily = ShareTechMono, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, color: Color, label: String, value: String, onClick: () -> Unit) {
    val lang = LocalLang.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.06f))
            .border(0.5.dp, color.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                if (lang == Lang.FA) label else label.uppercase(),
                color = TextMutedC,
                fontFamily = monoFamily(lang),
                fontSize = if (lang == Lang.FA) 10.sp else 8.sp,
                letterSpacing = if (lang == Lang.FA) 0.sp else 1.5.sp,
            )
            Text(value, color = color, fontFamily = ShareTechMono, fontSize = 12.sp)
        }
        Text("→", color = color, fontFamily = displayFamily(lang), fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

// ── Donations ────────────────────────────────────────────────────────────────

private data class WalletEntry(
    val iconRes: Int,
    val label: String,
    val address: String,
)

// Donation endpoints. TRC20 = Tether on Tron, BEP20 = BNB Smart Chain,
// TON = Tether on The Open Network.
private val WALLETS = listOf(
    WalletEntry(R.drawable.ic_donate_trx, "USDT · TRC20", "TLfuejS4FnCaudqAveCQ9ZgdNPkoyZQ1xp"),
    WalletEntry(R.drawable.ic_donate_bnb, "BNB · BEP20", "0x2172b3dec78ba72dc2dec031c803c98902d8e3cc"),
    WalletEntry(R.drawable.ic_donate_ton, "USDT · TON", "UQD9343F1_0cn-pV3GrogoAnUQk953xn93koe7HAgrVy8Wcs"),
)

@Composable
private fun DonateCard(onCopy: (String) -> Unit) {
    val s = LocalStrings.current
    val lang = LocalLang.current

    CyberCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (lang == Lang.FA) s.supportTitle else s.supportTitle.uppercase(),
                    color = Accent,
                    fontFamily = displayFamily(lang),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = if (lang == Lang.FA) 0.sp else 1.5.sp,
                    modifier = Modifier.weight(1f),
                )
                Text("❤", color = Color(0xFFFF5F6D), fontSize = 14.sp)
            }
            Text(
                s.supportHint,
                color = TextMutedC,
                fontFamily = monoFamily(lang),
                fontSize = 9.sp,
            )

            WALLETS.forEach { wallet ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                        .clickable { onCopy(wallet.address) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(wallet.iconRes),
                        contentDescription = wallet.label,
                        modifier = Modifier.size(26.dp),
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(
                            wallet.label,
                            color = Accent,
                            fontFamily = ShareTechMono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                        )
                        Text(
                            wallet.address,
                            color = TextSecondaryC,
                            fontFamily = ShareTechMono,
                            fontSize = 9.sp,
                            lineHeight = 12.sp,
                        )
                    }
                    Text("⎘", color = Accent, fontFamily = ShareTechMono, fontSize = 14.sp)
                }
            }
        }
    }
}
