package com.ahoura.asha_scanner_ip.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.ahoura.asha_scanner_ip.R
import com.ahoura.asha_scanner_ip.ui.theme.Accent
import com.ahoura.asha_scanner_ip.ui.theme.Background
import com.ahoura.asha_scanner_ip.ui.theme.BlueC
import com.ahoura.asha_scanner_ip.ui.theme.RedC
import com.ahoura.asha_scanner_ip.ui.theme.ShareTechMono
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
//  Cyberpunk animation toolkit. Everything here is GPU-cheap Canvas / Compose
//  animation (single shared frame clock where continuous motion is needed) so it
//  stays smooth on low-end devices common in the target audience. Lottie powers
//  the one celebratory accent (sonar ping).
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A monotonically increasing millisecond clock driven by the Compose frame loop.
 * When [running] is false the loop stops and the value freezes at its last tick —
 * so a backdrop can hold a static frame on idle screens instead of redrawing at
 * 60fps forever (a real battery saving on low-end devices). Flipping [running]
 * back to true resumes motion seamlessly from the frozen value.
 */
@Composable
fun rememberFrameClock(running: Boolean = true): State<Long> {
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val origin = withFrameMillis { it } - clock.longValue
        while (true) {
            withFrameMillis { now -> clock.longValue = now - origin }
        }
    }
    return clock
}

private fun frac(v: Float): Float = v - floor(v)

// ── Animated app background ───────────────────────────────────────────────────

/**
 * Full-screen living backdrop: a slowly scrolling neon grid, a drifting glow,
 * and a constellation of network nodes. Layered behind transparent content so it
 * breathes between the terminal cards.
 */
@Composable
fun CyberBackground(animated: Boolean = true, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Background)) {
        AnimatedGrid(Modifier.fillMaxSize(), animated)
        ParticleNetwork(Modifier.fillMaxSize(), animated = animated)
        content()
    }
}

@Composable
fun AnimatedGrid(modifier: Modifier = Modifier, animated: Boolean = true) {
    val time by rememberFrameClock(animated)
    Canvas(modifier) {
        val step = 30.dp.toPx()
        val off = (time / 45f) % step
        val line = Accent.copy(alpha = 0.035f)
        var x = -step + off
        while (x <= size.width) {
            drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f); x += step
        }
        var y = -step + off
        while (y <= size.height) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), 1f); y += step
        }
        // Drifting radial glow.
        val gx = size.width * (0.5f + 0.42f * sin(time / 3000f))
        val gy = size.height * (0.42f + 0.32f * cos(time / 3900f))
        val gr = size.minDimension * 0.55f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Accent.copy(alpha = 0.05f), Color.Transparent),
                center = Offset(gx, gy), radius = gr,
            ),
            radius = gr, center = Offset(gx, gy),
        )
    }
}

@Composable
fun ParticleNetwork(
    modifier: Modifier = Modifier,
    count: Int = 22,
    color: Color = Accent,
    animated: Boolean = true,
) {
    val time by rememberFrameClock(animated)
    val nodes = remember {
        List(count) {
            val rnd = Random(it * 911L + 7)
            Node(
                bx = rnd.nextFloat(), by = rnd.nextFloat(),
                vx = (rnd.nextFloat() - 0.5f) * 0.035f,
                vy = (rnd.nextFloat() - 0.5f) * 0.035f,
                r = rnd.nextFloat() * 1.6f + 1.1f,
            )
        }
    }
    Canvas(modifier) {
        val t = time / 1000f
        val pts = nodes.map {
            Offset(frac(it.bx + it.vx * t) * size.width, frac(it.by + it.vy * t) * size.height)
        }
        val linkDist = size.minDimension * 0.22f
        for (i in pts.indices) {
            for (j in i + 1 until pts.size) {
                val d = hypot(pts[i].x - pts[j].x, pts[i].y - pts[j].y)
                if (d < linkDist) {
                    val a = (1f - d / linkDist) * 0.16f
                    drawLine(color.copy(alpha = a), pts[i], pts[j], 1f)
                }
            }
        }
        pts.forEachIndexed { i, p ->
            drawCircle(color.copy(alpha = 0.5f), nodes[i].r, p)
        }
    }
}

private data class Node(val bx: Float, val by: Float, val vx: Float, val vy: Float, val r: Float)

// ── Radar sweep ──────────────────────────────────────────────────────────────

/**
 * Rotating radar with concentric rings, crosshair and a fading sweep trail.
 * [blips] light up as the beam crosses them — wire it to the live "found" count
 * to make every healthy IP register on the scope.
 */
@Composable
fun RadarSweep(
    modifier: Modifier = Modifier,
    color: Color = Accent,
    blips: Int = 0,
    active: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "radar")
    val angle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )
    val pulse by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val n = blips.coerceIn(0, 24)
    val spots = remember(n) {
        List(n) {
            val rnd = Random(it * 97L + 13)
            BlipSpot(rnd.nextFloat() * 360f, 0.22f + rnd.nextFloat() * 0.72f)
        }
    }
    Canvas(modifier) {
        val r = size.minDimension / 2f * 0.92f
        val c = center
        // Faint disc.
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = 0.06f), Color.Transparent), center = c, radius = r),
            r, c,
        )
        // Rings.
        for (i in 1..4) drawCircle(color.copy(alpha = 0.12f), r * i / 4f, c, style = Stroke(1f))
        // Crosshair.
        drawLine(color.copy(alpha = 0.10f), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1f)
        drawLine(color.copy(alpha = 0.10f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1f)
        // Outer ring brighter, gently pulsing.
        drawCircle(color.copy(alpha = 0.20f + 0.25f * pulse), r, c, style = Stroke(1.5f))

        if (active) {
            // Sweep trail (bright leading edge fading backwards).
            rotate(angle, c) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        0.0f to Color.Transparent,
                        0.72f to Color.Transparent,
                        0.97f to color.copy(alpha = 0.30f),
                        1.0f to color.copy(alpha = 0.55f),
                        center = c,
                    ),
                    radius = r, center = c,
                )
                drawLine(color.copy(alpha = 0.85f), c, Offset(c.x + r, c.y), 2f)
            }
        }

        // Blips — brightness decays with how long ago the beam passed.
        spots.forEach { s ->
            val rad = Math.toRadians(s.angle.toDouble())
            val pos = Offset(c.x + (cos(rad) * r * s.radiusFrac).toFloat(), c.y + (sin(rad) * r * s.radiusFrac).toFloat())
            val behind = ((angle - s.angle) % 360f + 360f) % 360f
            val bright = if (!active) 0.5f else (1f - (behind / 90f)).coerceIn(0f, 1f)
            val glow = 0.18f + 0.82f * bright
            drawCircle(color.copy(alpha = glow), 4.5f + 3f * bright, pos)
            if (bright > 0.2f) drawCircle(color.copy(alpha = 0.10f * bright), 12f, pos)
        }
        // Core.
        drawCircle(color.copy(alpha = 0.7f), 3f + 1.5f * pulse, c)
    }
}

private data class BlipSpot(val angle: Float, val radiusFrac: Float)

// ── Neon progress bar ────────────────────────────────────────────────────────

/** Glowing progress bar with a travelling shimmer highlight. */
@Composable
fun NeonProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Accent,
    trackColor: Color = Color(0xFF1A2530),
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(300), label = "p")
    val shimmer by rememberInfiniteTransition(label = "shimmer").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "s",
    )
    Canvas(modifier) {
        val h = size.height
        val radius = h / 2f
        // Track.
        drawRoundRect(trackColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius), size = size)
        val w = size.width * p
        if (w <= 0f) return@Canvas
        // Soft glow under the fill.
        drawRoundRect(
            color.copy(alpha = 0.25f),
            topLeft = Offset(0f, -h * 0.5f),
            size = androidx.compose.ui.geometry.Size(w, h * 2f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
        )
        // Fill gradient.
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color), endX = w.coerceAtLeast(1f)),
            size = androidx.compose.ui.geometry.Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
        )
        // Travelling shimmer.
        val sx = shimmer * w
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                startX = sx - h * 2f, endX = sx + h * 2f,
            ),
            topLeft = Offset((sx - h * 2f).coerceAtLeast(0f), 0f),
            size = androidx.compose.ui.geometry.Size((h * 4f).coerceAtMost(w), h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
        )
    }
}

// ── Glitch text ──────────────────────────────────────────────────────────────

/** RGB-split glitch title that fires brief aberration bursts on an interval. */
@Composable
fun GlitchText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Accent,
    fontSize: TextUnit = 14.sp,
    letterSpacing: TextUnit = 2.sp,
    fontFamily: FontFamily = ShareTechMono,
    fontWeight: FontWeight? = null,
) {
    val phase by rememberInfiniteTransition(label = "glitch").animateFloat(
        0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "ph",
    )
    val active = phase > 0.9f
    val seed = (phase * 1000f).toInt()
    val g = if (active) (((seed % 7) - 3).toFloat()) else 0f
    val style = TextStyle(
        color = color, fontSize = fontSize, letterSpacing = letterSpacing,
        fontFamily = fontFamily, fontWeight = fontWeight,
    )
    Box(modifier) {
        if (active) {
            Text(text, style = style.copy(color = RedC.copy(alpha = 0.7f)), modifier = Modifier.graphicsLayer { translationX = g * density })
            Text(text, style = style.copy(color = BlueC.copy(alpha = 0.7f)), modifier = Modifier.graphicsLayer { translationX = -g * density })
        }
        Text(text, style = style)
    }
}

// ── Typewriter ───────────────────────────────────────────────────────────────

/** Types [text] out char-by-char, then holds with a blinking block cursor. */
@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Accent,
    fontSize: TextUnit = 9.sp,
    letterSpacing: TextUnit = 2.sp,
    charDelayMs: Long = 38L,
) {
    var shown by remember(text) { mutableStateOf(0) }
    LaunchedEffect(text) {
        shown = 0
        for (i in 1..text.length) {
            shown = i
            kotlinx.coroutines.delay(charDelayMs)
        }
    }
    val blink by rememberInfiniteTransition(label = "cursor").animateFloat(
        1f, 0f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "b",
    )
    val done = shown >= text.length
    Box(modifier) {
        Text(
            text.take(shown) + if (!done || blink > 0.5f) "_" else " ",
            color = color, fontFamily = ShareTechMono, fontSize = fontSize, letterSpacing = letterSpacing,
        )
    }
}

// ── Staggered entrance ───────────────────────────────────────────────────────

/** Fades + lifts its content in, delayed by [index] for a cascade effect. */
@Composable
fun StaggerIn(
    index: Int,
    modifier: Modifier = Modifier,
    delayPerItemMs: Long = 65L,
    content: @Composable () -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * delayPerItemMs)
        shown = true
    }
    val appear by animateFloatAsState(
        if (shown) 1f else 0f, tween(420), label = "appear",
    )
    Box(
        modifier.graphicsLayer {
            alpha = appear
            translationY = (1f - appear) * 26.dp.toPx()
        },
    ) { content() }
}

// ── Lottie sonar accent ──────────────────────────────────────────────────────

/** Looping sonar-ping Lottie used as a celebratory / scanning accent. */
@Composable
fun LottieSonar(modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.sonar_ping))
    LottieAnimation(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        modifier = modifier,
    )
}
