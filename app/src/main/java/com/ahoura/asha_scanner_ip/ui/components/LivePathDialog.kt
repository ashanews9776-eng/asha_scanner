package com.ahoura.asha_scanner_ip.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ahoura.asha_scanner_ip.core.vpn.VpnStats
import com.ahoura.asha_scanner_ip.core.vpn.VpnStatus
import com.ahoura.asha_scanner_ip.ui.i18n.Lang
import com.ahoura.asha_scanner_ip.ui.i18n.localizeDigits
import com.ahoura.asha_scanner_ip.ui.theme.Accent
import com.ahoura.asha_scanner_ip.ui.theme.AccentDim
import com.ahoura.asha_scanner_ip.ui.theme.BorderC
import com.ahoura.asha_scanner_ip.ui.theme.BlueC
import com.ahoura.asha_scanner_ip.ui.theme.OrangeC
import com.ahoura.asha_scanner_ip.ui.theme.RedC
import com.ahoura.asha_scanner_ip.ui.theme.ShareTechMono
import com.ahoura.asha_scanner_ip.ui.theme.SurfaceC
import com.ahoura.asha_scanner_ip.ui.theme.TextMuted
import com.ahoura.asha_scanner_ip.ui.theme.TextPrimary
import com.ahoura.asha_scanner_ip.ui.theme.TextSecondary
import com.ahoura.asha_scanner_ip.ui.theme.Vazirmatn

enum class HopState { ACTIVE, CONNECTING, BYPASSED, FAILED, IDLE }

data class HopNode(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val tag: String,
    val state: HopState,
    val details: List<Pair<String, String>>,
)

private fun HopState.color(): Color = when (this) {
    HopState.ACTIVE -> Color(0xFF00FF99)       // Cyberpunk Neon Green
    HopState.CONNECTING -> Color(0xFFFFB300)   // Amber
    HopState.FAILED -> Color(0xFFFF3366)       // Neon Red
    HopState.BYPASSED -> Color(0xFF5A7084)     // Muted Blue-Grey
    HopState.IDLE -> Color(0xFF38B6FF)         // Electric Blue
}

private fun HopState.label(isFa: Boolean): String = when (this) {
    HopState.ACTIVE -> if (isFa) "فعال" else "ACTIVE"
    HopState.CONNECTING -> if (isFa) "در حال اتصال" else "CONNECTING"
    HopState.FAILED -> if (isFa) "خطا" else "FAILED"
    HopState.BYPASSED -> if (isFa) "رد شده" else "BYPASSED"
    HopState.IDLE -> if (isFa) "آماده" else "IDLE"
}

@Composable
fun LivePathButton(
    vpnStats: VpnStats,
    lang: Lang,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isFa = lang == Lang.FA
    val isConnected = vpnStats.status == VpnStatus.CONNECTED
    val isConnecting = vpnStats.status == VpnStatus.CONNECTING
    val dotColor = when {
        isConnected -> Color(0xFF00FF99)
        isConnecting -> Color(0xFFFFB300)
        vpnStats.status == VpnStatus.ERROR -> Color(0xFFFF3366)
        else -> TextMuted
    }

    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF0D1722),
                        Color(0xFF091018),
                    )
                )
            )
            .border(1.dp, BorderC, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(Accent.copy(alpha = 0.12f), CircleShape)
                .border(1.dp, Accent.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Route, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isFa) "مسیر زنده شبکه" else "LIVE TRAFFIC PATH",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = if (isFa) Vazirmatn else ShareTechMono,
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(7.dp).background(dotColor, CircleShape))
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (isFa) "مشاهده جریان بسته‌ها از دستگاه تا اینترنت" else "Inspect hop-by-hop packet flow from device to internet",
                color = TextSecondary,
                fontSize = 11.sp,
                fontFamily = if (isFa) Vazirmatn else ShareTechMono,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun LivePathDialog(
    visible: Boolean,
    vpnStats: VpnStats,
    lang: Lang,
    cleanIp: String?,
    onDismissRequest: () -> Unit,
) {
    if (!visible) return
    val isFa = lang == Lang.FA
    val isConnected = vpnStats.status == VpnStatus.CONNECTED
    val isConnecting = vpnStats.status == VpnStatus.CONNECTING
    val isFailed = vpnStats.status == VpnStatus.ERROR

    fun stateFor(used: Boolean): HopState = when {
        !used -> HopState.BYPASSED
        isFailed -> HopState.FAILED
        isConnected -> HopState.ACTIVE
        isConnecting -> HopState.CONNECTING
        else -> HopState.IDLE
    }

    val transport = vpnStats.transport
    val isChain = transport.lowercase() in listOf("chain", "psiphon-over-warp", "tor-over-warp", "pow") ||
        vpnStats.detailMessage?.contains("chain", ignoreCase = true) == true
    val isCustom = transport.lowercase() == "custom" || vpnStats.activeProfile != null

    val outerTransportName = when (transport.lowercase()) {
        "masque", "pow" -> "MASQUE (CONNECT-IP)"
        "wom", "wg-over-masque" -> "WOM (WireGuard over MASQUE)"
        "wireguard", "wg" -> "WireGuard (Noise UDP)"
        "gool", "wow" -> "WoW (WARP-in-WARP)"
        "mim" -> "MIM (MASQUE-in-MASQUE)"
        "custom" -> vpnStats.activeProfile?.proxy?.protocol?.name?.uppercase() ?: "XRAY PROXY"
        else -> transport.uppercase()
    }

    val effectiveCleanPeer = cleanIp?.takeIf { it.isNotBlank() }
        ?: vpnStats.activeProfile?.targetAddress
        ?: "Anycast Edge"

    val nodes = listOf(
        HopNode(
            key = "device",
            icon = Icons.Filled.Smartphone,
            title = if (isFa) "گوشی و برنامه‌ها" else "Device & Applications",
            subtitle = if (isFa) "ترافیک محلی سیستم‌عامل" else "Local OS Traffic & Apps",
            tag = "LOCAL",
            state = if (isFailed) HopState.FAILED else if (isConnected) HopState.ACTIVE else HopState.CONNECTING,
            details = listOf(
                (if (isFa) "حالت تانل" else "Tunnel Mode") to vpnStats.tunnelMode.uppercase(),
                (if (isFa) "آی‌پی شبکه محلی" else "LAN IP") to (vpnStats.localLanIp ?: "127.0.0.1"),
                (if (isFa) "مسیربندی برنامه‌ها" else "App Routing") to (if (isFa) "تمام برنامه‌ها" else "All Apps"),
            ),
        ),
        HopNode(
            key = "tun",
            icon = Icons.Filled.Hub,
            title = if (isFa) "رابط شبکه و فیلتر پکت" else "TUN Layer (Packet Filter)",
            subtitle = if (isFa) "حفاظت ضد نشت و مسدودساز آنی QUIC" else "Anti-Leak Splice & QUIC Rejector",
            tag = "GUARD",
            state = stateFor(true),
            details = listOf(
                (if (isFa) "مسدودساز UDP/443" else "QUIC Blocker") to (if (isFa) "پاسخ فوری ICMP Unreachable" else "Instant ICMP Port Unreachable"),
                (if (isFa) "حفاظت نشت WebRTC" else "WebRTC Leak") to (if (isFa) "مسدودسازی STUN/TURN" else "STUN/TURN Drop Protection"),
                (if (isFa) "ردیاب و تبلیغات" else "DNS Sinkhole") to (if (isFa) "مسدودسازی محلی سینک‌هول" else "Local Ad/Tracker Sinkhole"),
                (if (isFa) "حفاظت نشت IPv6" else "IPv6 Leak") to (if (isFa) "مسدودسازی پکت‌های IPv6" else "IPv6 Packet Drop"),
            ),
        ),
        HopNode(
            key = "outer",
            icon = Icons.Filled.Cloud,
            title = outerTransportName,
            subtitle = if (isFa) "لایه بیرونی ارتباط با سرور" else "Outer Tunnel Leg to Cloudflare Edge",
            tag = "OUTER",
            state = stateFor(true),
            details = listOf(
                (if (isFa) "پروتکل بیرونی" else "Transport") to outerTransportName,
                (if (isFa) "آی‌پی تمیز کلودفلر" else "Clean Endpoint") to effectiveCleanPeer,
                (if (isFa) "شکستن هدر TLS (Anti-DPI)" else "Anti-DPI ClientHello") to (if (isFa) "خردسازی هوشمند SNI" else "Intelligent SNI Shaper"),
                (if (isFa) "پورت پراکسی محلی" else "Local SOCKS") to "127.0.0.1:1820",
            ),
        ),
        HopNode(
            key = "inner",
            icon = Icons.Filled.Shield,
            title = if (isChain) (if (isFa) "زنجیره امن عبور (لایه دوم)" else "Chained Inner Leg")
                    else if (isCustom) (if (isFa) "پروکسی اختصاصی Xray" else "Xray Proxy Engine")
                    else (if (isFa) "خروجی مستقیم کلودفلر" else "Direct Cloudflare Egress"),
            subtitle = if (isChain) (if (isFa) "سایفون / تور رمزنگاری‌شده روی وارپ" else "Psiphon / Tor inside WARP")
                       else if (isCustom) (vpnStats.activeProfile?.name ?: "VLESS/Trojan")
                       else (if (isFa) "ارتباط مستقیم تک‌لایه‌ای" else "Single Layer Clean Hop"),
            tag = if (isChain) "CHAIN" else if (isCustom) "XRAY" else "DIRECT",
            state = stateFor(true),
            details = if (isChain) listOf(
                (if (isFa) "موتور لایه داخلی" else "Inner Engine") to "Psiphon / Tor",
                (if (isFa) "پورت پراکسی داخلی" else "Internal SOCKS") to "127.0.0.1:1819",
                (if (isFa) "مسیر خروج" else "Route") to (if (isFa) "رمزنگاری‌شده از روی WARP" else "Encrypted over WARP"),
            ) else listOf(
                (if (isFa) "نوع تانل" else "Tunnel Type") to (if (isCustom) "Custom Outbound" else "Native WireGuard / MASQUE"),
                (if (isFa) "مسیر داده" else "Data Path") to (if (isFa) "مستقیم به اینترنت" else "Direct Single Hop"),
            ),
        ),
        HopNode(
            key = "internet",
            icon = Icons.Filled.Language,
            title = if (isFa) "اینترنت و مقصد نهایی" else "Internet & Egress",
            subtitle = vpnStats.exitIp ?: (if (isFa) "در انتظار خروجی..." else "Resolving egress..."),
            tag = vpnStats.country ?: "EXIT",
            state = if (isConnected) HopState.ACTIVE else if (isFailed) HopState.FAILED else HopState.CONNECTING,
            details = listOf(
                (if (isFa) "آی‌پی عمومی خروجی" else "Exit Public IP") to (vpnStats.exitIp ?: "—"),
                (if (isFa) "کشور سرور خروجی" else "Egress Country") to (vpnStats.country ?: "Cloudflare Anycast"),
                (if (isFa) "تأخیر رفت و برگشت" else "RTT Latency") to if (vpnStats.pingMs != null) "${vpnStats.pingMs} ms".localizeDigits(lang) else "—",
            ),
        ),
    )

    Dialog(onDismissRequest = onDismissRequest) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0A1017))
                .border(1.dp, BorderC, RoundedCornerShape(16.dp))
                .padding(16.dp),
        ) {
            Column(Modifier.fillMaxSize()) {
                // Sleek Terminal Header
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .background(Accent.copy(alpha = 0.12f), CircleShape)
                                .border(1.dp, Accent.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Route, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isFa) "مسیر زنده شبکه" else "LIVE TRAFFIC PATH",
                                    color = Accent,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = if (isFa) Vazirmatn else ShareTechMono,
                                )
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    Modifier
                                        .background(Accent.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isConnected) (if (isFa) "برقرار" else "LIVE") else (if (isFa) "آماده" else "STANDBY"),
                                        color = if (isConnected) Accent else OrangeC,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = ShareTechMono,
                                    )
                                }
                            }
                            Text(
                                text = if (isFa) "جریان بسته‌ها گام‌به‌گام از مبدأ تا مقصد" else "Hop-by-hop packet inspection",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = if (isFa) Vazirmatn else ShareTechMono,
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFF14202C), CircleShape)
                            .border(1.dp, BorderC, CircleShape),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(Modifier.height(4.dp))

                var openKey by remember { mutableStateOf<String?>("tun") }
                val flowing = isConnected

                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    nodes.forEachIndexed { index, node ->
                        HopCard(
                            node = node,
                            expanded = openKey == node.key,
                            isFa = isFa,
                        ) {
                            if (node.details.isNotEmpty()) {
                                openKey = if (openKey == node.key) null else node.key
                            }
                        }
                        if (index < nodes.lastIndex) {
                            val nextNode = nodes[index + 1]
                            val isBypassed = node.state == HopState.BYPASSED || nextNode.state == HopState.BYPASSED
                            HopConnector(
                                active = flowing && !isBypassed,
                                color = if (isBypassed) HopState.BYPASSED.color() else nextNode.state.color(),
                                dashed = isBypassed || !flowing,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    PathLegend(isFa)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun HopCard(
    node: HopNode,
    expanded: Boolean,
    isFa: Boolean,
    onClick: () -> Unit,
) {
    val tint = node.state.color()
    val isBypassed = node.state == HopState.BYPASSED
    val shape = RoundedCornerShape(14.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        tint.copy(alpha = if (isBypassed) 0.03f else 0.09f),
                        Color(0xFF0C141E),
                    )
                )
            )
            .border(1.dp, tint.copy(alpha = if (isBypassed) 0.15f else 0.38f), shape)
            .clickable(enabled = node.details.isNotEmpty(), onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Glowing circular icon container
            Box(
                Modifier
                    .size(38.dp)
                    .drawBehind {
                        if (!isBypassed) {
                            drawCircle(
                                Brush.radialGradient(listOf(tint.copy(alpha = 0.35f), Color.Transparent)),
                                radius = size.minDimension * 0.75f,
                            )
                        }
                    }
                    .background(tint.copy(alpha = if (isBypassed) 0.08f else 0.15f), CircleShape)
                    .border(1.dp, tint.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = node.icon,
                    contentDescription = null,
                    tint = if (isBypassed) TextMuted else tint,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = node.title,
                        color = if (isBypassed) TextMuted else TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = if (isFa) Vazirmatn else ShareTechMono,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .background(tint.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .border(0.5.dp, tint.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = node.tag,
                            color = tint,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = ShareTechMono,
                            softWrap = false,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.height(1.dp))
                Text(
                    text = node.subtitle,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = if (isFa) Vazirmatn else ShareTechMono,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusChip(node.state, isFa)
            if (node.details.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // Expanded details with structured 2-column layout (NO OVERLAPPING!)
        AnimatedVisibility(
            visible = expanded && node.details.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF070F18))
                    .border(0.5.dp, BorderC, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                node.details.forEach { (label, value) ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = label,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = if (isFa) Vazirmatn else ShareTechMono,
                            modifier = Modifier.weight(0.44f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = value,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = ShareTechMono,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(0.56f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(state: HopState, isFa: Boolean) {
    val c = state.color()
    Row(
        Modifier
            .background(c.copy(alpha = 0.12f), RoundedCornerShape(50))
            .border(1.dp, c.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).background(c, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(
            text = state.label(isFa),
            color = c,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = if (isFa) Vazirmatn else ShareTechMono,
            softWrap = false,
            maxLines = 1,
        )
    }
}

@Composable
private fun HopConnector(active: Boolean, color: Color, dashed: Boolean) {
    val phase = if (active) {
        val transition = rememberInfiniteTransition(label = "packetFlow")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
            label = "packetFlowPhase",
        ).value
    } else 0f

    Box(
        Modifier
            .fillMaxWidth()
            .height(20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(20.dp)) {
            // Align the connector line exactly under the center of the 38.dp icon (12.dp card padding + 19.dp center = 31.dp)
            val x = 31.dp.toPx()
            drawLine(
                color = color.copy(alpha = if (dashed) 0.25f else 0.55f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f) else null,
            )
            if (active) {
                val y = size.height * phase
                drawCircle(color.copy(alpha = 0.35f), radius = 5.dp.toPx(), center = Offset(x, y))
                drawCircle(color, radius = 2.5.dp.toPx(), center = Offset(x, y))
            }
        }
    }
}

@Composable
private fun PathLegend(isFa: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF070F18), RoundedCornerShape(8.dp))
            .border(0.5.dp, BorderC, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            HopState.ACTIVE,
            HopState.CONNECTING,
            HopState.BYPASSED,
            HopState.FAILED,
        ).forEach { state ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(state.color(), CircleShape))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = state.label(isFa),
                    color = TextSecondary,
                    fontSize = 10.5.sp,
                    fontFamily = if (isFa) Vazirmatn else ShareTechMono,
                    softWrap = false,
                )
            }
        }
    }
}
