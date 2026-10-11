package io.github.klaboukes.twangle.core.designsystem.dial

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import kotlin.math.floor

/*
 * The strobe view: rows of stripes that drift the way the note is off and stand still when it is
 * in tune, like the striped disc of a mechanical strobe tuner. The tuner moves them with
 * StrobeMotion (in :core:dsp) and passes its phase here; this file only draws.
 */

/**
 * The strobe view, filling the draw area in [color]: [STROBE_ROWS] rows of even stripes. [phase]
 * (0..1, wrapping) is where the stripes are, in stripe periods; the top row sits at [phase] and
 * each row below moves twice as fast as the one above (`phase · 2`, `phase · 4`), as the rings of
 * a real strobe disc do. A growing [phase] moves them right (sharp), a shrinking one left (flat),
 * and one that stays put holds them still. Allocates nothing.
 */
fun DrawScope.drawStrobe(phase: Float, color: Color) {
    val period = STRIPE_PERIOD.toPx()
    val stripe = period * STRIPE_FILL
    val gap = ROW_GAP.toPx()
    val rowHeight = (size.height - gap * (STROBE_ROWS - 1)) / STROBE_ROWS
    if (rowHeight <= 0f || size.width <= 0f) return
    clipRect {
        var multiple = 1f
        for (row in 0 until STROBE_ROWS) {
            val top = row * (rowHeight + gap)
            var x = stripeOffset(phase * multiple, period)
            while (x < size.width) {
                drawRect(color, topLeft = Offset(x, top), size = Size(stripe, rowHeight))
                x += period
            }
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

/** How many rows of stripes the strobe view has. */
const val STROBE_ROWS = 2

/** From one stripe to the next. */
private val STRIPE_PERIOD = 16.dp

/** How much of a period a stripe fills; the rest is gap. */
private const val STRIPE_FILL = 0.5f

/** Between two rows. */
private val ROW_GAP = 4.dp
