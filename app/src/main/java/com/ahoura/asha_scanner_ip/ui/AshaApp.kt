package com.ahoura.asha_scanner_ip.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import com.ahoura.asha_scanner_ip.ui.components.CyberBackground
import com.ahoura.asha_scanner_ip.ui.i18n.Lang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalLang
import com.ahoura.asha_scanner_ip.ui.i18n.LocalStrings
import com.ahoura.asha_scanner_ip.ui.i18n.stringsFor
import com.ahoura.asha_scanner_ip.ui.screens.AboutScreen
import com.ahoura.asha_scanner_ip.ui.screens.CustomScanScreen
import com.ahoura.asha_scanner_ip.ui.screens.DiscoverScreen
import com.ahoura.asha_scanner_ip.ui.screens.HomeScreen
import com.ahoura.asha_scanner_ip.ui.screens.QuickScanScreen
import com.ahoura.asha_scanner_ip.ui.screens.ResultsScreen
import com.ahoura.asha_scanner_ip.ui.screens.ScanLiveScreen
import com.ahoura.asha_scanner_ip.ui.screens.TestIpsScreen

enum class Route { HOME, QUICK, CUSTOM, TEST, DISCOVER, LIVE, RESULTS, ABOUT }

const val TELEGRAM_HANDLE = "@asha_news2"
const val TELEGRAM_URL = "https://t.me/asha_news2"

@Composable
fun AshaApp(vm: ScanViewModel) {
    val stack = remember { mutableStateListOf(Route.HOME) }
    val current = stack.last()

    fun push(r: Route) { stack.add(r) }
    fun replaceTop(r: Route) { stack[stack.lastIndex] = r }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun home() { stack.clear(); stack.add(Route.HOME) }

    val lang by vm.language.collectAsState()
    val direction = if (lang == Lang.FA) LayoutDirection.Rtl else LayoutDirection.Ltr

    // Keep the living backdrop in motion only where it earns its keep — the Home
    // first-impression and the active scan. On form/results/about screens (where
    // the user reads and lingers) it freezes to a static frame to save battery.
    val animatedBg = current == Route.HOME || current == Route.LIVE

    CompositionLocalProvider(
        LocalLayoutDirection provides direction,
        LocalLang provides lang,
        LocalStrings provides stringsFor(lang),
    ) {
        CyberBackground(animated = animatedBg) {
            Scaffold(modifier = Modifier.fillMaxSize(), containerColor = Color.Transparent) { inner ->
                Box(Modifier.fillMaxSize().padding(inner)) {
                    AnimatedContent(
                        targetState = current,
                        transitionSpec = {
                            (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 20 })
                                .togetherWith(fadeOut(tween(160)))
                        },
                        label = "route",
                    ) { route ->
                        when (route) {
                            Route.HOME -> HomeScreen(
                                onQuick = { push(Route.QUICK) },
                                onCustom = { push(Route.CUSTOM) },
                                onTest = { push(Route.TEST) },
                                onDiscover = { vm.prepareDiscover(); push(Route.DISCOVER) },
                                onAbout = { push(Route.ABOUT) },
                                onToggleLang = { vm.toggleLanguage() },
                            )
                            Route.QUICK -> QuickScanScreen(
                                vm = vm, onBack = ::back,
                                onStart = { vm.start(); replaceTop(Route.LIVE) },
                            )
                            Route.CUSTOM -> CustomScanScreen(
                                vm = vm, onBack = ::back,
                                onStart = { vm.start(); replaceTop(Route.LIVE) },
                            )
                            Route.TEST -> TestIpsScreen(
                                vm = vm, onBack = ::back,
                                onStart = { vm.start(); replaceTop(Route.LIVE) },
                            )
                            Route.DISCOVER -> DiscoverScreen(
                                vm = vm, onBack = ::back,
                                onStart = { vm.start(); replaceTop(Route.LIVE) },
                            )
                            Route.LIVE -> ScanLiveScreen(
                                vm = vm,
                                onCancel = { vm.stop() },
                                onFinished = { replaceTop(Route.RESULTS) },
                            )
                            Route.RESULTS -> ResultsScreen(
                                vm = vm,
                                onAgain = { vm.reset(); home() },
                                onBack = { vm.reset(); home() },
                            )
                            Route.ABOUT -> AboutScreen(onBack = ::back)
                        }
                    }
                }
            }
        }
    }

    BackHandler(enabled = current != Route.HOME) {
        if (current == Route.RESULTS || current == Route.LIVE) { vm.reset(); home() } else back()
    }
}
