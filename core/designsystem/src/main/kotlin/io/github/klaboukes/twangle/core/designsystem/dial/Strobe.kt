package io.github.klaboukes.twangle.core.designsystem.dial

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.max

/*
 * The strobe's stripes (1.1's strobe wave look; since Phase 72 the lens's strobe look, lens/StrobeLook.kt):
 * bands of stripes that drift the way the note is off and stand still when it is in tune, like the
 * striped disc of a mechanical strobe tuner. The look moves them at the strobe's speed
 * (StrobeMotion.driftSpeed) and passes each band's phase here; this file only draws, in rounded bars
 * (1.1's Contemporary marks).
 *
 * Every stripe of a band looks the same, so a pattern a whole period on looks exactly like the one
 * before: that is what lets the phase wrap from 1 back to 0 without a jump. Anything that sets a
 * band apart (its ground, the centre line) stays put while the stripes move, and how faded a mark is
 * near the ends depends only on where it is. Nothing here allocates, as it runs on every animation frame.
 */

/** How many bands of stripes the strobe has (the lens's three, Phase 72; 1.1's wave view had two). */
const val STROBE_ROWS = 3

/**
 * One band of the strobe in rounded bars, in the rectangle from ([left], [top]), [width] × [height] px, in
 * [color]: even stripes [period] px apart at [phase] (in stripe periods, any value; growing moves them right,
 * sharp, shrinking left, flat, and one that stays put holds them still). [glow] (0..1) adds a soft halo while
 * the note is in tune. The marks fade out towards both ends ([strobeFade]), so they glide in and out rather
 * than being cut off; nothing is drawn outside the rectangle.
 */
fun DrawScope.drawStrobeBand(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    period: Float,
    phase: Float,
    color: Color,
    glow: Float = 0f,
) {
    if (width <= 0f || height <= 0f || period <= 0f) return
    clipRect(left, top, left + width, top + height) {
        // One period further left as well, so marks that glow still cover the left edge.
        val start = left + stripeOffset(phase, period) - period
        bars(left, top, width, height, period, start, color, glow)
    }
}

/**
 * Where the first stripe of a row starts, in px, for a row at [phase] (in stripe periods, any
 * value) with stripes [period] px apart: from −[period] up to (not including) 0, so the row
 * always covers its left edge. Grows (stripes move right) as [phase] grows, and wraps every
 * whole period, so a phase of 1 looks exactly like 0.
 */
fun stripeOffset(phase: Float, period: Float): Float {
    val turn = phase - floor(phase)
    return (turn - 1f) * period
}

/** [this] colour made fainter by [share]. */
private fun Color.faded(share: Float): Color = copy(alpha = alpha * share)

/**
 * How visible a strobe mark at [u] across its band (0..1) is: fully over the middle, fading
 * smoothly to nothing over the outer [STROBE_EDGE] at each end, like stripes on a lit drum turning
 * out of sight, so marks glide in and out rather than being cut off.
 */
fun strobeFade(u: Float): Float = smooth(u / STROBE_EDGE) * smooth((1f - u) / STROBE_EDGE)

/** The share of a band at each end over which the strobe's marks fade out. */
private const val STROBE_EDGE = 0.2f

/** 0 below 0, 1 above 1, an S-curve between (Hermite smoothstep). */
private fun smooth(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** A mark's halo in tune (the wave view's, which went in Phase 67). */
private const val HALO = 0.3f

// The band is drawn from [start] (left of it) to past its right edge, one mark per [period]; a
// mark's centre sits a quarter period in, where a plain half-filled stripe's would. Marks that have
// faded out completely at the ends are skipped. The bars follow the approved mock-up
// (docs/design/1.x/theme-styles-preview.html, drawStrobe).
private fun DrawScope.bars(left: Float, top: Float, width: Float, height: Float, period: Float, start: Float, color: Color, glow: Float) {
    val barWidth = max(3.dp.toPx(), period * 0.32f).coerceAtMost(height)
    val halo = HALO * glow
    var x = start + period * 0.25f
    while (x < left + width + period) {
        val fade = strobeFade((x - left) / width)
        if (fade > 0f) {
            val upper = Offset(x, top + barWidth / 2f)
            val lower = Offset(x, top + height - barWidth / 2f)
            if (halo > 0f) drawLine(color.faded(halo * fade), upper, lower, barWidth * 2.2f, StrokeCap.Round)
            drawLine(color.faded(fade), upper, lower, barWidth, StrokeCap.Round)
        }
        x += period
    }
}
