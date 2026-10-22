package com.ahoura.asha_scanner_ip.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahoura.asha_scanner_ip.ui.Route
import com.ahoura.asha_scanner_ip.ui.i18n.LocalLang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalStrings
import com.ahoura.asha_scanner_ip.ui.theme.Accent
import com.ahoura.asha_scanner_ip.ui.theme.BorderC
import com.ahoura.asha_scanner_ip.ui.theme.SurfaceC
import com.ahoura.asha_scanner_ip.ui.theme.TextMutedC
import com.ahoura.asha_scanner_ip.ui.theme.TextPrimaryC
import com.ahoura.asha_scanner_ip.ui.theme.displayFamily

data class DockItem(
    val route: Route,
    val icon: ImageVector,
    val title: String,
)

/**
 * Modern cyberpunk bottom navigation bar anchored directly to the bottom.
 * Spans edge-to-edge behind the Android system navigation bar with a crisp neon top border
 * and active tab indicators.
 */
@Composable
fun CyberNavBar(
    currentRoute: Route,
    visible: Boolean = true,
    onNavigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val haptic = LocalHapticFeedback.current

    val items = listOf(
        DockItem(Route.HOME, Icons.Filled.Home, s.dockHome),
        DockItem(Route.VPN, Icons.Filled.Bolt, s.dockVpn),
        DockItem(Route.QUICK, Icons.Filled.Search, s.dockScanner),
        DockItem(Route.ABOUT, Icons.Filled.Info, s.about),
    )

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
        exit = slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it } + fadeOut(),
        modifier = modifier.fillMaxWidth(),
    ) {
        Surface(
            color = SurfaceC.copy(alpha = 0.98f),
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                // Top hairline cyber border
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    BorderC.copy(alpha = 0.4f),
                                    Accent.copy(alpha = 0.35f),
                                    BorderC.copy(alpha = 0.4f),
                                )
                            )
                        )
                )

                // Tab items row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEach { item ->
                        val isSelected = when (item.route) {
                            Route.HOME -> currentRoute == Route.HOME || currentRoute == Route.DNS || currentRoute == Route.SPEEDTEST
                            Route.VPN -> currentRoute == Route.VPN || currentRoute == Route.SPLIT_TUNNEL
                            Route.QUICK -> currentRoute == Route.QUICK || currentRoute == Route.CUSTOM || currentRoute == Route.TEST || currentRoute == Route.DISCOVER
                            Route.ABOUT -> currentRoute == Route.ABOUT
                            else -> currentRoute == item.route
                        }

                        CyberNavItem(
                            item = item,
                            isSelected = isSelected,
                            onClick = {
                                CyberHaptics.tick(haptic)
                                onNavigate(item.route)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CyberNavItem(
    item: DockItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lang = LocalLang.current
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.06f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "tab_scale"
    )

    val iconTint = if (isSelected) Accent else TextMutedC
    val textColor = if (isSelected) TextPrimaryC else TextMutedC

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Top active neon accent indicator
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(2.5.dp)
                .clip(RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
                .background(if (isSelected) Accent else Color.Transparent)
        )

        // Center content: Icon + Label
        Column(
            modifier = Modifier
                .scale(scale)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) Accent.copy(alpha = 0.08f) else Color.Transparent)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = item.title,
                color = textColor,
                fontSize = 10.sp,
                fontFamily = displayFamily(lang),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )
        }

        // Bottom balance spacer so the center content remains vertically balanced
        Spacer(Modifier.height(2.5.dp))
    }
}

/**
 * Backward compatibility alias for CyberDock.
 */
@Composable
fun CyberDock(
    currentRoute: Route,
    visible: Boolean,
    onNavigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    CyberNavBar(
        currentRoute = currentRoute,
        visible = visible,
        onNavigate = onNavigate,
        modifier = modifier,
    )
}
