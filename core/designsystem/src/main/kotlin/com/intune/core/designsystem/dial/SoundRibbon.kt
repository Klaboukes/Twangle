package com.intune.core.designsystem.dial

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.intune.core.settings.VisualStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * The wave view of every style: a soft ribbon of sound under the note, drawn in the style's own
 * marks and without a frame, so it stays in the background. Off pitch it swells into pulses that
 * drift along, like the beats you hear when two notes are close but not the same; in tune it
 * settles into one smooth, even band. It is eye candy, not a measurement: WaveMotion in
 * :feature:tuner decides how the pulses move.
 */

/**
 * Height of the ribbon at [u] (0 at the left, 1 at the right) in 0..1. It is widest in the middle
 * and narrows to nothing at both ends. [depth] (0..1) pinches it into [beats] pulses across the
 * width, moved along by [phase] pulses: a larger [phase] moves the pulses to the right.
 */
fun ribbonAt(u: Float, beats: Float, phase: Float, depth: Float): Float {
    val lens = max(0f, sin(PI.toFloat() * u.coerceIn(0f, 1f)))
    val pulse = 0.5f + 0.5f * cos(2f * PI.toFloat() * (beats * (u - 0.5f) - phase))
    return lens * (1f - depth.coerceIn(0f, 1f) * (1f - pulse))
}

/**
 * [style]'s sound ribbon in [color], filling the draw area. [beats], [phase] and [depth] shape it
 * (see [ribbonAt]); [level] scales it (0 is a faint line at rest: no note). [glow] (0..1) adds a
 * soft halo while the note is in tune. [path] is reused from frame to frame.
 */
fun DrawScope.drawSoundRibbon(
    style: VisualStyle,
    color: Color,
    beats: Float,
    phase: Float,
    depth: Float,
    level: Float,
    glow: Float,
    path: Path,
) {
    val ribbon = Ribbon(color, beats, phase, depth, level, glow)
    when (style) {
        // Dabs of paint that swell and shrink.
        VisualStyle.PREHISTORIC -> dots(ribbon)
        // Quill hatching, slanted like a broad nib.
        VisualStyle.MIDDLE_AGES -> bars(ribbon, spacing = 4.dp, width = 1.6.dp, cap = StrokeCap.Butt, slant = 0.22f)
        // An engraved outline with a hairline through it.
        VisualStyle.CLASSIC -> band(ribbon, path, fill = 0.14f, outline = 1.3.dp, haloBase = 0f, centreLine = true)
        // A phosphor trace that always glows a little.
        VisualStyle.RETRO -> band(ribbon, path, fill = 0.4f, outline = 1.6.dp, haloBase = 0.14f, centreLine = false)
        // Rounded bars, like a voice memo's waveform.
        VisualStyle.CONTEMPORARY -> bars(ribbon, spacing = 6.dp, width = 3.dp, cap = StrokeCap.Round)
        // Thin neon bars with a halo.
        VisualStyle.FUTURISTIC -> bars(ribbon, spacing = 4.dp, width = 1.5.dp, cap = StrokeCap.Square, haloBase = 0.16f)
        // Pairs of stars tracing the ribbon's edges.
        VisualStyle.SPACE_AGE -> stars(ribbon)
    }
}

/** What every style's ribbon needs, gathered so the skins below stay short. */
private class Ribbon(
    val color: Color,
    val beats: Float,
    val phase: Float,
    val depth: Float,
    val level: Float,
    val glow: Float,
) {
    /** The ribbon's height at [u] as a share of its full height (0..1). */
    fun at(u: Float): Float = level * ribbonAt(u, beats, phase, depth)

    /** [color] made fainter by [share]. */
    fun faded(share: Float): Color = color.copy(alpha = color.alpha * share)

    /** How strong the halo is: always [base], stronger in tune. */
    fun halo(base: Float): Float = base + (HALO - base * 0.5f) * glow
}

/** The halo's strength while in tune. */
private const val HALO = 0.3f

private fun DrawScope.bars(
    ribbon: Ribbon,
    spacing: Dp,
    width: Dp,
    cap: StrokeCap,
    slant: Float = 0f,
    haloBase: Float = 0f,
) {
    val gap = spacing.toPx()
    val w = width.toPx()
    val count = ((size.width - w) / gap).toInt()
    val left = (size.width - count * gap) / 2f
    val mid = size.height / 2f
    val reach = mid - w
    val halo = ribbon.halo(haloBase)
    for (i in 0..count) {
        val x = left + i * gap
        val h = max(ribbon.at(x / size.width) * reach, w * 0.3f)
        val top = Offset(x + slant * h, mid - h)
        val bottom = Offset(x - slant * h, mid + h)
        if (halo > 0f) drawLine(ribbon.faded(halo), top, bottom, w + gap * 0.5f, StrokeCap.Round)
        drawLine(ribbon.color, top, bottom, w, cap)
    }
}

private fun DrawScope.dots(ribbon: Ribbon) {
    val gap = 15.dp.toPx()
    val count = (size.width / gap).toInt()
    val left = (size.width - (count - 1) * gap) / 2f
    val mid = size.height / 2f
    val smallest = 1.5.dp.toPx()
    val largest = minOf(gap * 0.34f, mid)
    val halo = ribbon.halo(0f)
    for (i in 0 until count) {
        val x = left + i * gap
        val r = smallest + (largest - smallest) * ribbon.at(x / size.width)
        if (halo > 0f) drawCircle(ribbon.faded(halo), r * 1.35f, Offset(x, mid))
        // A second, smudged dab beside each one, as if pressed on by a fingertip.
        drawCircle(ribbon.faded(0.35f), r * 1.08f, Offset(x + r * 0.18f, mid + r * 0.14f))
        drawCircle(ribbon.color, r, Offset(x, mid))
    }
}

private fun DrawScope.stars(ribbon: Ribbon) {
    val gap = 9.dp.toPx()
    val count = (size.width / gap).toInt()
    val left = (size.width - (count - 1) * gap) / 2f
    val mid = size.height / 2f
    val r = 1.7.dp.toPx()
    val reach = mid - r * 2f
    val halo = ribbon.halo(0.14f)
    for (i in 0 until count) {
        val x = left + i * gap
        val h = ribbon.at(x / size.width) * reach
        for (y in floatArrayOf(mid - h, mid + h)) {
            drawCircle(ribbon.faded(halo), r * 2.6f, Offset(x, y))
            drawCircle(ribbon.color, r, Offset(x, y))
        }
    }
}

private fun DrawScope.band(
    ribbon: Ribbon,
    path: Path,
    fill: Float,
    outline: Dp,
    haloBase: Float,
    centreLine: Boolean,
) {
    val mid = size.height / 2f
    val reach = mid - 4.dp.toPx()
    val steps = (size.width / 3.dp.toPx()).toInt().coerceAtLeast(8)
    path.reset()
    for (i in 0..steps) {
        val u = i / steps.toFloat()
        val y = mid - ribbon.at(u) * reach
        if (i == 0) path.moveTo(0f, y) else path.lineTo(u * size.width, y)
    }
    for (i in steps downTo 0) {
        val u = i / steps.toFloat()
        path.lineTo(u * size.width, mid + ribbon.at(u) * reach)
    }
    path.close()
    val halo = ribbon.halo(haloBase)
    if (halo > 0f) drawPath(path, ribbon.faded(halo), style = Stroke(outline.toPx() * 4f, join = StrokeJoin.Round))
    drawPath(path, ribbon.faded(fill))
    if (centreLine) {
        drawLine(ribbon.faded(0.45f), Offset(0f, mid), Offset(size.width, mid), 0.8.dp.toPx())
    }
    drawPath(path, ribbon.color, style = Stroke(outline.toPx(), join = StrokeJoin.Round))
}
