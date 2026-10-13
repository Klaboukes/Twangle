package io.github.klaboukes.twangle.core.designsystem.dial

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.settings.VisualStyle
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * The strobe look of the wave view (WaveLook.STROBE): rows of stripes that drift the way the note
 * is off and stand still when it is in tune, like the striped disc of a mechanical strobe tuner.
 * WaveMotion moves them at the strobe's speed (StrobeMotion.driftSpeed) and drawWave passes its
 * phase here; this file only draws, in each style's own marks, the way SoundRibbon.kt draws the
 * ribbon.
 *
 * Every stripe of a row looks the same, so a pattern a whole period on looks exactly like the one
 * before: that is what lets the phase wrap from 1 back to 0 without a jump. Anything that sets a
 * row apart (a rule, a hairline) stays put while the stripes move, and how faded a mark is near the
 * ends depends only on where it is. Nothing here allocates, as it runs on every animation frame.
 */

/**
 * The strobe look in [style]'s marks, filling the draw area in [color]: [STROBE_ROWS] rows of even
 * stripes. [phase] (0..1, wrapping) is where the stripes are, in stripe periods; the top row sits
 * at [phase] and each row below moves twice as fast as the one above (`phase · 2`, `phase · 4`),
 * as the rings of a real strobe disc do. A growing [phase] moves them right (sharp), a shrinking
 * one left (flat), and one that stays put holds them still. [glow] (0..1) adds a soft halo while
 * the note is in tune. The marks fade out towards both ends ([strobeFade]), so they glide in and out
 * rather than being cut off.
 */
fun DrawScope.drawStrobe(style: VisualStyle, phase: Float, color: Color, glow: Float = 0f) {
    val gap = ROW_GAP.toPx()
    val height = (size.height - gap * (STROBE_ROWS - 1)) / STROBE_ROWS
    if (height <= 0f || size.width <= 0f) return
    val period = stripePeriod(style).toPx()
    clipRect {
        var multiple = 1f
        for (row in 0 until STROBE_ROWS) {
            val top = row * (height + gap)
            // One period further left as well, so marks that lean or glow still cover the left edge.
            val start = stripeOffset(phase * multiple, period) - period
            when (style) {
                // Fingertip dabs of terracotta, each with a smudge.
                VisualStyle.ANCIENT -> dabs(top, height, period, start, color, glow)
                // An engraved band: crisp bars between two hairlines.
                VisualStyle.CLASSIC -> engraved(top, height, period, start, color, glow)
                // A backlit drum: rounded bars that always glow a little, like phosphor.
                VisualStyle.RETRO -> backlit(top, height, period, start, color, glow)
                // Rounded bars, like a voice memo's.
                VisualStyle.CONTEMPORARY -> bars(top, height, period, start, color, glow)
                // Thin neon bars, always glowing softly.
                VisualStyle.FUTURISTIC -> neon(top, height, period, start, color, glow)
                // A row of twinkling stars over a row of glowing dots.
                VisualStyle.SPACE_AGE -> stars(row, top, height, period, start, color, glow)
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

/** How many rows of stripes the strobe look has. */
const val STROBE_ROWS = 2

/** From one stripe to the next in [style] (the approved mock-up's "period"): finer marks sit closer together. */
fun stripePeriod(style: VisualStyle): Dp = when (style) {
    VisualStyle.ANCIENT -> 13.dp
    VisualStyle.CLASSIC -> 12.dp
    VisualStyle.RETRO -> 12.dp
    VisualStyle.CONTEMPORARY -> 9.dp
    VisualStyle.FUTURISTIC -> 8.dp
    VisualStyle.SPACE_AGE -> 12.dp
}

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
// Marks that have faded out completely at the ends are skipped. The marks follow the approved
// mock-up (docs/design/theme-styles-preview.html, drawStrobe).

private fun DrawScope.dabs(top: Float, height: Float, period: Float, start: Float, color: Color, glow: Float) {
    val mid = top + height / 2f
    val r = min(period * 0.3f, height * 0.45f)
    val halo = halo(0f, glow)
    var x = start + period * 0.25f
    while (x < size.width + period) {
        val fade = fadeAt(x)
        if (fade > 0f) {
            if (halo > 0f) drawCircle(color.faded(halo * fade), r * 1.5f, Offset(x, mid))
            // A second, smudged dab beside each one, as in the wave view's ribbon.
            drawCircle(color.faded(0.35f * fade), r * 1.08f, Offset(x + r * 0.18f, mid + r * 0.14f))
            drawCircle(color.faded(fade), r, Offset(x, mid))
        }
        x += period
    }
}

private fun DrawScope.engraved(top: Float, height: Float, period: Float, start: Float, color: Color, glow: Float) {
    val line = 0.7.dp.toPx()
    val inset = 2.dp.toPx()
    val halo = halo(0f, glow)
    drawLine(color.faded(0.4f), Offset(0f, top + line / 2f), Offset(size.width, top + line / 2f), line)
    drawLine(color.faded(0.4f), Offset(0f, top + height - line / 2f), Offset(size.width, top + height - line / 2f), line)
    val barTop = top + inset
    val barHeight = (height - inset * 2f).coerceAtLeast(0f)
    val haloTop = top + inset * 0.75f
    val haloHeight = (height - inset * 1.5f).coerceAtLeast(0f)
    val width = period * 0.42f
    var x = start + period * 0.25f
    while (x < size.width + period) {
        val fade = fadeAt(x)
        if (fade > 0f) {
            if (halo > 0f) drawRect(color.faded(halo * fade), Offset(x - width, haloTop), Size(width * 2f, haloHeight))
            drawRect(color.faded(0.9f * fade), Offset(x - width / 2f, barTop), Size(width, barHeight))
        }
        x += period
    }
}

private fun DrawScope.backlit(top: Float, height: Float, period: Float, start: Float, color: Color, glow: Float) {
    val inset = 1.5.dp.toPx()
    val spread = 2.5.dp.toPx()
    val width = period * 0.45f
    val corner = CornerRadius(min(width, height) * 0.3f)
    val haloCorner = CornerRadius(min(width, height) * 0.3f + spread)
    val halo = halo(0.14f, glow)
    var x = start + period * 0.25f - width / 2f
    while (x < size.width + period) {
        val fade = fadeAt(x + width / 2f)
        if (fade > 0f) {
            drawRoundRect(
                color.faded(halo * fade),
                Offset(x - spread, top + inset - spread),
                Size(width + spread * 2f, height - inset * 2f + spread * 2f),
                haloCorner,
            )
            drawRoundRect(color.faded(fade), Offset(x, top + inset), Size(width, height - inset * 2f), corner)
        }
        x += period
    }
}

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

private fun DrawScope.neon(top: Float, height: Float, period: Float, start: Float, color: Color, glow: Float) {
    val width = max(1.5.dp.toPx(), period * 0.32f)
    val halo = halo(0.16f, glow)
    val barTop = top + width / 2f
    val barBottom = top + height - width / 2f
    var x = start + period * 0.25f
    while (x < size.width + period) {
        val fade = fadeAt(x)
        if (fade > 0f) {
            drawLine(color.faded(halo * fade), Offset(x, barTop), Offset(x, barBottom), width * 2.2f, StrokeCap.Round)
            drawLine(color.faded(fade), Offset(x, barTop), Offset(x, barBottom), width, StrokeCap.Square)
        }
        x += period
    }
}

private fun DrawScope.stars(row: Int, top: Float, height: Float, period: Float, start: Float, color: Color, glow: Float) {
    val mid = top + height / 2f
    var x = start + period * 0.25f
    if (row == 0) {
        // Four-pointed twinkles, lit softly round their middle in tune.
        val r = min(period * 0.32f, height * 0.45f)
        val halo = halo(0f, glow)
        val line = 1.1.dp.toPx()
        val dot = 1.3.dp.toPx()
        while (x < size.width + period) {
            val fade = fadeAt(x)
            if (fade > 0f) {
                val c = color.faded(fade)
                if (halo > 0f) drawCircle(color.faded(halo * fade), r * 1.2f, Offset(x, mid))
                drawLine(c, Offset(x - r, mid), Offset(x + r, mid), line, StrokeCap.Round)
                drawLine(c, Offset(x, mid - r), Offset(x, mid + r), line, StrokeCap.Round)
                drawCircle(c, dot, Offset(x, mid))
            }
            x += period
        }
    } else {
        val r = min(1.9.dp.toPx(), height * 0.3f)
        val halo = halo(0.14f, glow)
        while (x < size.width + period) {
            val fade = fadeAt(x)
            if (fade > 0f) {
                drawCircle(color.faded(halo * fade), r * 2.6f, Offset(x, mid))
                drawCircle(color.faded(fade), r, Offset(x, mid))
            }
            x += period
        }
    }
}
