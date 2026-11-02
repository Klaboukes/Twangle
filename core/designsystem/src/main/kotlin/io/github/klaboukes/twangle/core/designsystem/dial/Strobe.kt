package io.github.klaboukes.twangle.core.designsystem.dial

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.max

/*
 * The strobe look of the wave view (WaveLook.STROBE): rows of stripes that drift the way the note
 * is off and stand still when it is in tune, like the striped disc of a mechanical strobe tuner.
 * WaveMotion moves them at the strobe's speed (StrobeMotion.driftSpeed) and drawWave passes its
 * phase here; this file only draws, in rounded bars (1.1's Contemporary marks; the other styles had
 * their own until Phase 67), the way SoundRibbon.kt draws the ribbon.
 *
 * Every stripe of a row looks the same, so a pattern a whole period on looks exactly like the one
 * before: that is what lets the phase wrap from 1 back to 0 without a jump. Anything that sets a
 * row apart (a rule, a hairline) stays put while the stripes move, and how faded a mark is near the
 * ends depends only on where it is. Nothing here allocates, as it runs on every animation frame.
 */

/**
 * The strobe look in rounded bars, filling the draw area in [color]: [STROBE_ROWS] rows of even
 * stripes. [phase] (0..1, wrapping) is where the stripes are, in stripe periods; the top row sits
 * at [phase] and each row below moves twice as fast as the one above (`phase · 2`, `phase · 4`),
 * as the rings of a real strobe disc do. A growing [phase] moves them right (sharp), a shrinking
 * one left (flat), and one that stays put holds them still. [glow] (0..1) adds a soft halo while
 * the note is in tune. The marks fade out towards both ends ([strobeFade]), so they glide in and out
 * rather than being cut off.
 */
fun DrawScope.drawStrobe(phase: Float, color: Color, glow: Float = 0f) {
    val gap = ROW_GAP.toPx()
    val height = (size.height - gap * (STROBE_ROWS - 1)) / STROBE_ROWS
    if (height <= 0f || size.width <= 0f) return
    val period = STRIPE_PERIOD.toPx()
    clipRect {
        var multiple = 1f
        for (row in 0 until STROBE_ROWS) {
            val top = row * (height + gap)
            // One period further left as well, so marks that lean or glow still cover the left edge.
            val start = stripeOffset(phase * multiple, period) - period
            // Rounded bars, like a voice memo's.
            bars(top, height, period, start, color, glow)
            multiple *= 2f
        }
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

/** How many rows of stripes the strobe look has. */
const val STROBE_ROWS = 2

/** From one stripe to the next (the approved mock-up's "period" for the bars). */
val STRIPE_PERIOD: Dp = 9.dp

/** Between two rows. */
private val ROW_GAP = 4.dp

/** [this] colour made fainter by [share]. */
private fun Color.faded(share: Float): Color = copy(alpha = alpha * share)

/** How strong a halo is: always [base], stronger in tune ([glow] 1). The wave's [HALO] in tune. */
private fun halo(base: Float, glow: Float): Float = base + (HALO - base * 0.5f) * glow

/** How visible a mark centred [x] px from the left is: faded out near both ends ([strobeFade]). */
private fun DrawScope.fadeAt(x: Float): Float = strobeFade(x / size.width)

/**
 * How visible a strobe mark at [u] across the view (0..1) is: fully over the middle, fading
 * smoothly to nothing over the outer [STROBE_EDGE] at each end, like stripes on a lit drum turning
 * out of sight, so marks glide in and out rather than being cut off.
 */
fun strobeFade(u: Float): Float = smooth(u / STROBE_EDGE) * smooth((1f - u) / STROBE_EDGE)

/** The share of the view at each end over which the strobe's marks fade out. */
private const val STROBE_EDGE = 0.2f

/** 0 below 0, 1 above 1, an S-curve between (Hermite smoothstep). */
private fun smooth(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

// Each row below is drawn from [start] (left of the view) to past its right edge, one mark per
// [period]; a mark's centre sits a quarter period in, where a plain half-filled stripe's would.
// Marks that have faded out completely at the ends are skipped. The bars follow the approved
// mock-up (docs/design/theme-styles-preview.html, drawStrobe).

private fun DrawScope.bars(top: Float, height: Float, period: Float, start: Float, color: Color, glow: Float) {
    val width = max(3.dp.toPx(), period * 0.32f)
    val halo = halo(0f, glow)
    var x = start + period * 0.25f
    while (x < size.width + period) {
        val fade = fadeAt(x)
        if (fade > 0f) {
            val upper = Offset(x, top + width / 2f)
            val lower = Offset(x, top + height - width / 2f)
            if (halo > 0f) drawLine(color.faded(halo * fade), upper, lower, width * 2.2f, StrokeCap.Round)
            drawLine(color.faded(fade), upper, lower, width, StrokeCap.Round)
        }
        x += period
    }
}
