package com.ahoura.asha_scanner_ip.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ahoura.asha_scanner_ip.ui.i18n.Lang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalLang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalStrings
import com.ahoura.asha_scanner_ip.ui.theme.Accent
import com.ahoura.asha_scanner_ip.ui.theme.AccentBorder
import com.ahoura.asha_scanner_ip.ui.theme.AccentMuted
import com.ahoura.asha_scanner_ip.ui.theme.BorderC
import com.ahoura.asha_scanner_ip.ui.theme.SurfaceC
import com.ahoura.asha_scanner_ip.ui.theme.TextMutedC
import com.ahoura.asha_scanner_ip.ui.theme.TextPrimaryC
import com.ahoura.asha_scanner_ip.ui.theme.TextSecondaryC
import com.ahoura.asha_scanner_ip.ui.theme.ShareTechMono
import com.ahoura.asha_scanner_ip.ui.theme.displayFamily
import com.ahoura.asha_scanner_ip.ui.theme.monoFamily

@Composable
fun UpdateDialog(
    version: String,
    url: String,
    changelog: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val s = LocalStrings.current
    val lang = LocalLang.current
    val fa = lang == Lang.FA

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceC)
                .border(1.dp, AccentBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = if (fa) Alignment.End else Alignment.Start) {
                Text(
                    text = s.updateAvailable.uppercase(),
                    color = Accent,
                    fontFamily = displayFamily(lang),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = if (fa) 0.sp else 1.5.sp
                )
                Box(Modifier.size(12.dp))
                Text(
                    text = s.updateDesc.format(version),
                    color = TextPrimaryC,
                    fontFamily = monoFamily(lang),
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )

                if (!changelog.isNullOrBlank()) {
                    Box(Modifier.size(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.2f))
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = changelog,
                            color = TextSecondaryC,
                            fontFamily = ShareTechMono,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Box(Modifier.size(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = s.later,
                        color = TextMutedC,
                        fontFamily = monoFamily(lang),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(12.dp)
                    )
                    Box(Modifier.size(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AccentMuted)
                            .border(0.5.dp, AccentBorder, RoundedCornerShape(4.dp))
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = if (fa) s.downloadNow else s.downloadNow.uppercase(),
                            color = Accent,
                            fontFamily = displayFamily(lang),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TracerouteDialog(
    ip: String,
    colo: String,
    onDismiss: () -> Unit
) {
    val lang = LocalLang.current
    val fa = lang == Lang.FA

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceC)
                .border(1.dp, AccentBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "VISUAL TRACEROUTE",
                    color = Accent,
                    fontFamily = ShareTechMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 2.sp
                )
                Box(Modifier.size(8.dp))
                Text(
                    text = "$ip · $colo",
                    color = TextMutedC,
                    fontFamily = ShareTechMono,
                    fontSize = 11.sp
                )
                
                Box(Modifier.size(24.dp))
                
                TracerouteAnimation(colo)

                Box(Modifier.size(24.dp))
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AccentMuted)
                        .border(0.5.dp, AccentBorder, RoundedCornerShape(4.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (fa) "بستن" else "CLOSE",
                        color = Accent,
                        fontFamily = displayFamily(lang),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun TracerouteAnimation(targetColo: String) {
    val transition = rememberInfiniteTransition(label = "trace")
    val progress by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "prog"
    )

    val hops = remember { listOf("Local", "ISP Gateway", "IXP", "CF Edge", targetColo) }
    
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        hops.forEachIndexed { i, name ->
            val active = progress > (i.toFloat() / (hops.size - 1))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (active) Accent else TextMutedC.copy(alpha = 0.3f))
                )
                Box(Modifier.size(12.dp))
                Text(
                    text = if (name.isBlank()) "???" else name.uppercase(),
                    color = if (active) TextPrimaryC else TextMutedC,
                    fontFamily = ShareTechMono,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                )
                if (active && i < hops.size - 1) {
                    Text(" ···", color = Accent, fontFamily = ShareTechMono)
                }
            }
        }
    }
}
