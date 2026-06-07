package com.ahoura.asha_scanner_ip.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ── Terminal / cyberpunk palette ─────────────────────────────────────────────
val Background = Color(0xFF0A0D12)   // scaffold, deepest
val SurfaceC = Color(0xFF0D1520)     // cards, inputs
val SurfaceAltC = Color(0xFF111820)  // dividers
val BorderC = Color(0xFF1A2530)      // all card/input borders (0.5dp)

val Accent = Color(0xFF00FF99)       // primary green
val AccentDim = Color(0xFF00CC77)    // good values
val AccentMuted = Color(0x1400FF99)  // ~0.08 alpha — active item bg
val AccentBorder = Color(0x4000FF99) // ~0.25 alpha — active item border

val BlueC = Color(0xFF38B6FF)        // secondary, colo badges
val OrangeC = Color(0xFFFFA500)      // warnings, medium latency
val RedC = Color(0xFFE05050)         // errors, high loss

val TextPrimaryC = Color(0xFFC8D8E8)
val TextSecondaryC = Color(0xFF6A8496)   // was 4A6070 — lifted for readability
val TextMutedC = Color(0xFF4A6B7E)       // was 2D4A5A — hint text was near-invisible
val TextFadedC = Color(0xFF35506B)       // was 1E2F3D — faintest tier, still subdued

val GoldC = Color(0xFFFFA500)        // rank 1
val SilverC = Color(0xFF8898A8)      // rank 2,3

val ProgressGradient = Brush.horizontalGradient(listOf(AccentDim, Accent))

// ── Back-compat aliases (older widgets/screens) ──────────────────────────────
val Bg = Background
val BgBase = Background
val BgElevated = SurfaceC
val Surface = SurfaceC
val SurfaceCard = SurfaceC
val SurfaceAlt = SurfaceAltC
val SurfaceCardAlt = SurfaceAltC
val Border = BorderC
val Outline = BorderC
val NeonCyan = Accent
val NeonBlue = BlueC
val NeonViolet = BlueC
val NeonPink = Accent
val SuccessGreen = Accent
val WarnAmber = OrangeC
val DangerRed = RedC
val Amber = OrangeC
val TextPrimary = TextPrimaryC
val TextSecondary = TextSecondaryC
val TextMuted = TextMutedC
val AccentGradient = ProgressGradient
val BrandGradient = ProgressGradient
val BrandGradientSoft = ProgressGradient
val SuccessGradient = ProgressGradient
