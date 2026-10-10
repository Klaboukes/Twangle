package io.github.klaboukes.twangle.core.designsystem.dial

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.settings.WaveLook
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The looks the user can pick for the wave view (WaveLook). They all share the same motion: off
 * pitch, pulses drift right (sharp) or left (flat); in tune, everything settles into one calm shape.
 * The ribbon is drawn in each style's own marks (SoundRibbon.kt); the other looks are the same in
 * every style, in the style's colours.
 */

/**
 * The wave view in [look], filling the draw area in [color]. [WaveLook.RIBBON] is [style]'s own
 * sound ribbon.
 *
 * - [beats], [phase] and [depth] (0..1) shape the pulses (see [ribbonAt]).
 * - [level] scales it: 0 is a faint line at rest (no note).
 * - [glow] (0..1) adds a soft halo while the note is in tune.
 * - [lean] is how far off the note is and which way: −1 very flat, 0 in tune, 1 very sharp.
 * - [breath] (0..1, wrapping) is a slow clock that makes the fireflies twinkle.
 *
 * [path] is reused from frame to frame.
 */
fun DrawScope.drawWave(
    look: WaveLook,
    style: VisualStyle,
    color: Color,
    beats: Float,
    phase: Float,
    depth: Float,
    level: Float,
    glow: Float,
    lean: Float,
    breath: Float,
    path: Path,
) {
    val ribbon = Ribbon(color, beats, phase, depth, level, glow, lean, breath)
    when (look) {
        WaveLook.RIBBON -> drawStyleRibbon(style, ribbon, path)
        WaveLook.STRIPES -> stripes(ribbon)
        WaveLook.DOTS -> lights(ribbon)
        WaveLook.FIREFLIES -> fireflies(ribbon)
        WaveLook.LOOP -> loop(ribbon, path)
        WaveLook.RIPPLES -> ripples(ribbon)
    }
}

/**
 * Where the loop is at [t] (0..1 once round): x and y in −1..1. In tune ([depth] 0) it is an even,
 * open ring. Off pitch it tumbles like a spinning coin as [phase] goes round, closing to a line and
 * opening again: the figure an oscilloscope draws for two nearly equal notes.
 */
fun loopAt(t: Float, phase: Float, depth: Float): Offset {
    val angle = 2f * PI.toFloat() * t
    val tumble = depth.coerceIn(0f, 1f)
    val y = (1f - tumble) * cos(angle) + tumble * sin(angle + 2f * PI.toFloat() * phase)
    return Offset(sin(angle), y)
}

/**
 * How far ripple [index] of [count] has spread (0 in the middle, 1 at the edge). A larger [phase]
 * spreads them outwards, a smaller one draws them in; a whole ripple of [phase] looks the same.
 */
fun rippleAt(index: Int, count: Int, phase: Float): Float {
    val spread = index / count.toFloat() + phase
    return spread - floor(spread)
}

/**
 * Full-height stripes that drift with the pulses and thicken on them. They lean right when the note
 * is sharp and left when it is flat, and stand straight up in tune.
 */
private fun DrawScope.stripes(ribbon: Ribbon) {
    val gap = 8.dp.toPx()
    val inset = 3.dp.toPx()
    val thin = 0.8.dp.toPx()
    val thick = 4.dp.toPx()
    // A square root, so a little off already leans visibly.
    val tilt = sign(ribbon.lean) * sqrt(abs(ribbon.lean)) * size.height * STRIPE_TILT
    val halo = ribbon.halo(0f)
    var x = ribbon.phase * gap - 2 * gap
    while (x < size.width + 2 * gap) {
        val u = x / size.width
        val lens = max(0f, sin(PI.toFloat() * u.coerceIn(0f, 1f)))
        if (lens > 0f) {
            val pulse = pulseAt(u, ribbon.beats, ribbon.phase, ribbon.depth)
            val width = thin + (thick - thin) * pulse * ribbon.level
            val top = Offset(x + tilt, inset)
            val bottom = Offset(x - tilt, size.height - inset)
            val alpha = lens.pow(0.7f) * (0.35f + 0.65f * pulse)
            if (halo > 0f) drawLine(ribbon.faded(halo * alpha), top, bottom, width + gap * 0.5f, StrokeCap.Round)
            drawLine(ribbon.faded(alpha), top, bottom, width, StrokeCap.Round)
        }
        x += gap
    }
}

/** How far the stripes lean at the edge of the range, as a share of their height. */
private const val STRIPE_TILT = 0.35f

/** A grid of little lights, like an old stereo's meter: each column lights up as far as the ribbon reaches. */
private fun DrawScope.lights(ribbon: Ribbon) {
    val gap = 7.dp.toPx()
    val r = 1.3.dp.toPx()
    val columns = (size.width / gap).toInt()
    val left = (size.width - (columns - 1) * gap) / 2f
    val mid = size.height / 2f
    val rows = ((mid - r) / gap).toInt().coerceAtLeast(0)
    val reach = rows * gap + gap * 0.5f
    val halo = ribbon.halo(0f)
    for (i in 0 until columns) {
        val x = left + i * gap
        val h = ribbon.at(x / size.width) * reach
        for (k in -rows..rows) {
            // The middle row is always lit; the others fade in as the ribbon reaches them.
            val lit = if (k == 0) 1f else ((h - abs(k) * gap) / gap).coerceIn(0f, 1f)
            val centre = Offset(x, mid + k * gap)
            if (halo > 0f && lit > 0f) drawCircle(ribbon.faded(halo * lit), r * 2.6f, centre)
            drawCircle(ribbon.faded(UNLIT + (1f - UNLIT) * lit), r * (0.8f + 0.4f * lit), centre)
        }
    }
}

/** How bright a light is while off. */
private const val UNLIT = 0.12f

/**
 * Glowing specks that drift with the pulses and twinkle. Off pitch they swarm up and down; in tune
 * they gather in a calm line. The same handful repeats in every pulse, so they drift on smoothly.
 */
private fun DrawScope.fireflies(ribbon: Ribbon) {
    val mid = size.height / 2f
    val reach = mid - 3.dp.toPx()
    val spread = 0.25f + 0.75f * ribbon.depth
    val small = 1.dp.toPx()
    val halo = 0.25f + ribbon.halo(0f)
    val span = ceil(ribbon.beats / 2f).toInt() + 1
    for (j in -span..span) {
        for (f in FIREFLIES.indices step 3) {
            val u = 0.5f + (j + FIREFLIES[f] + ribbon.phase) / ribbon.beats
            if (u <= 0f || u >= 1f) continue
            val h = ribbon.at(u)
            val y = mid + FIREFLIES[f + 1] * reach * max(h, 0.08f) * spread
            val twinkle = 0.5f + 0.5f * cos(2f * PI.toFloat() * (2f * ribbon.breath + FIREFLIES[f + 2]))
            // Faded in near the ends, so they don't pop in and out there.
            val edge = (sin(PI.toFloat() * u) * 3f).coerceAtMost(1f)
            val alpha = (0.35f + 0.65f * twinkle) * (0.4f + h).coerceAtMost(1f) * edge
            val r = small * (1f + 1.4f * h)
            drawCircle(ribbon.faded(alpha * halo), r * 2.4f, Offset(u * size.width, y))
            drawCircle(ribbon.faded(alpha), r, Offset(u * size.width, y))
        }
    }
}

/** Each firefly in a pulse: where along it (0..1), how far up or down (−1..1), and when it twinkles. */
private val FIREFLIES = floatArrayOf(
    0.07f, 0.62f, 0.3f,
    0.21f, -0.35f, 0.8f,
    0.33f, 0.9f, 0.1f,
    0.48f, -0.8f, 0.55f,
    0.6f, 0.2f, 0.9f,
    0.71f, -0.1f, 0.4f,
    0.85f, 0.5f, 0.7f,
    0.93f, -0.6f, 0.2f,
)

/** A flat ring with a fainter one inside it, tumbling off pitch (see [loopAt]). */
private fun DrawScope.loop(ribbon: Ribbon, path: Path) {
    val centre = Offset(size.width / 2f, size.height / 2f)
    val across = centre.x - 8.dp.toPx()
    val high = (centre.y - 3.dp.toPx()) * ribbon.level
    val halo = ribbon.halo(0f)
    for (ring in 0..1) {
        val scale = if (ring == 0) 1f else INNER_LOOP
        path.reset()
        for (i in 0..LOOP_STEPS) {
            val p = loopAt(i / LOOP_STEPS.toFloat(), ribbon.phase, ribbon.depth)
            val x = centre.x + p.x * across * scale
            val y = centre.y + p.y * high * scale
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        if (ring == 0) {
            if (halo > 0f) drawPath(path, ribbon.faded(halo), style = Stroke(6.dp.toPx(), join = StrokeJoin.Round))
            drawPath(path, ribbon.color, style = Stroke(1.8.dp.toPx(), join = StrokeJoin.Round))
        } else {
            drawPath(path, ribbon.faded(0.35f), style = Stroke(1.2.dp.toPx(), join = StrokeJoin.Round))
        }
    }
}

private const val LOOP_STEPS = 96
private const val INNER_LOOP = 0.9f

/**
 * Rings spreading out from the middle like ripples on water when the note is sharp, and drawing in
 * when it is flat. In tune they hold still. Each fades in from the middle and out at the edge.
 */
private fun DrawScope.ripples(ribbon: Ribbon) {
    val centre = Offset(size.width / 2f, size.height / 2f)
    val smallX = 6.dp.toPx()
    val smallY = 4.dp.toPx()
    val tallest = centre.y - 5.dp.toPx()
    val squash = max(0.15f, ribbon.level)
    val line = 1.5.dp.toPx()
    val halo = ribbon.halo(0f)
    for (k in 0 until RIPPLES) {
        val f = rippleAt(k, RIPPLES, ribbon.phase)
        val rx = smallX + f * (centre.x - smallX)
        val ry = (smallY + f * (tallest - smallY)) * squash
        val alpha = sin(PI.toFloat() * f) * (1f - 0.35f * ribbon.depth)
        val topLeft = Offset(centre.x - rx, centre.y - ry)
        val oval = Size(rx * 2f, ry * 2f)
        if (halo > 0f) drawOval(ribbon.faded(halo * alpha), topLeft, oval, style = Stroke(line * 3.5f))
        drawOval(ribbon.faded(alpha), topLeft, oval, style = Stroke(line))
    }
}

private const val RIPPLES = 5
