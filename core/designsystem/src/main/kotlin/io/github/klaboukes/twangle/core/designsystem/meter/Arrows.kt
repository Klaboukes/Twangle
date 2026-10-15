package io.github.klaboukes.twangle.core.designsystem.meter

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.rememberTextMeasurer
import io.github.klaboukes.twangle.core.settings.InTuneEffect
import kotlin.math.abs

/*
 * 5. Arrows, as on the round-2 mock-up page (docs/design/meter-looks-round2-preview.html, drawArrows),
 * after the converging wedges of a pedal tuner: four chevrons each side point at a centre bar. On the
 * side the note is off they light one by one, the inner one first, so they point the way to turn the
 * peg and more lit means further off. In tune the inner pair and the bar take the "in tune" colour.
 * The mock-up's slow shimmer is left out: it would keep redrawing while a note holds, and the lit
 * arrows read the same without it.
 */

/** Chevrons on each side of the centre bar. */
internal const val ARROW_COUNT = 4

/** Each further chevron lights for this many more cents off … */
internal const val CENTS_PER_ARROW = 10.5f

/** … and the first starts lighting this far off. */
internal const val ARROWS_DEAD_CENTS = 2f

/**
 * How brightly chevron [index] (0 the inner one) on the [side] (−1 flat, 1 sharp) is lit with the
 * marker at [cents]: the chevrons on the side the note is off light one after the other, each over
 * [CENTS_PER_ARROW] cents, from [ARROWS_DEAD_CENTS] off; the other side stays dark.
 */
internal fun arrowLight(side: Int, index: Int, cents: Float): Float {
    val reading = cents.coerceIn(-METER_RANGE_CENTS, METER_RANGE_CENTS)
    if (reading == 0f || (reading < 0f) != (side < 0)) return 0f
    return ((abs(reading) - ARROWS_DEAD_CENTS) / CENTS_PER_ARROW - index).coerceIn(0f, 1f)
}

/**
 * The arrows' geometry in a box [width] × [height]: the centre bar ([barWidth] × [barHeight]) round
 * the middle, [centreY] half way down, and the chevrons' outlines, each pointing at it. Chevron k's tip
 * is [FIRST_TIP_UNITS] + k·[TIP_STEP_UNITS] from the middle; each further one is 7 % smaller.
 */
internal class ArrowsGeometry(width: Float, height: Float) {
    val u = meterUnit(width)
    val cx = width / 2f
    val centreY = height / 2f
    val barWidth = BAR_WIDTH_UNITS * u
    val barHeight = BAR_HEIGHT_UNITS * u

    /** The x of chevron [index]'s tip on the [side] (−1 flat, 1 sharp). */
    fun tipX(side: Int, index: Int): Float = cx + side * (FIRST_TIP_UNITS + TIP_STEP_UNITS * index) * u

    /** How large chevron [index] is, as a share of the inner one. */
    fun scale(index: Int): Float = 1f - SHRINK * index

    /** Chevron [index] on the [side]: two arms meeting at its tip, pointing at the centre. */
    fun chevron(side: Int, index: Int): Path = Path().apply {
        val tip = tipX(side, index)
        val depth = side * ARM_DEPTH_UNITS * u * scale(index)
        val half = ARM_HALF_HEIGHT_UNITS * u * scale(index)
        moveTo(tip + depth, centreY - half)
        lineTo(tip, centreY)
        lineTo(tip + depth, centreY + half)
    }

    /** Chevron [index]'s line width. */
    fun strokeWidth(index: Int): Float = STROKE_UNITS * u * (1f - 0.08f * index)

    companion object {
        const val BAR_WIDTH_UNITS = 9f
        const val BAR_HEIGHT_UNITS = 100f
        const val FIRST_TIP_UNITS = 26f
        const val TIP_STEP_UNITS = 30f
        const val ARM_DEPTH_UNITS = 17f
        const val ARM_HALF_HEIGHT_UNITS = 30f
        const val STROKE_UNITS = 7.5f
        const val SHRINK = 0.07f
    }
}

/**
 * Arrows: the calm field, the chevrons unlit (in the track colour) and the ♭ and ♯ (static); the
 * in-tune glow; the centre bar, outlined in the centre colour with a light fill that fills in as the
 * note is reached (its own layer); and the lit chevrons, in the accent with a soft wider stroke of
 * themselves as a halo, redrawn from the reading in their own layer.
 */
@Composable
internal fun BoxScope.ArrowsMeter(reading: MeterReading, effect: InTuneEffect) {
    val roles = meterRoles(effect)
    val measurer = rememberTextMeasurer()
    StaticArt {
        val arrows = ArrowsGeometry(size.width, size.height)
        val u = arrows.u
        val field = meterField(size, roles)
        val chevrons = ChevronsArt(arrows)
        val unlit = roles.track.copy(alpha = if (roles.dark) 1f else 0.95f)
        val signY = arrows.centreY + SIGN_BELOW_UNITS * u
        val outer = abs(arrows.tipX(1, ARROW_COUNT - 1) - arrows.cx) + ArrowsGeometry.ARM_DEPTH_UNITS * u * arrows.scale(ARROW_COUNT - 1) / 2f
        val signs = SignsArt(
            measurer, this, u, roles,
            flatX = arrows.cx - outer,
            sharpX = arrows.cx + outer,
            y = signY,
            maxHeight = 2f * (size.height - u - signY),
        )
        val draw: DrawScope.() -> Unit = {
            field.draw(this)
            for (side in ARROW_SIDES) for (k in 0 until ARROW_COUNT) chevrons.draw(this, side, k, unlit)
            signs.draw(this)
        }
        draw
    }
    TuneGlow(roles, reading, ARROWS_GLOW_UNITS, centre = { size -> Offset(size.width / 2f, size.height / 2f) })
    // The centre bar, in a layer of its own so its in-tune fill redraws nothing else.
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {}.drawWithCache {
            val arrows = ArrowsGeometry(size.width, size.height)
            val topLeft = Offset(arrows.cx - arrows.barWidth / 2f, arrows.centreY - arrows.barHeight / 2f)
            val barSize = Size(arrows.barWidth, arrows.barHeight)
            val corner = CornerRadius(arrows.barWidth / 2f)
            val outline = Stroke(1.8f * arrows.u)
            onDrawBehind {
                val color = roles.centre(reading.tuned)
                drawRoundRect(color.copy(alpha = BAR_FILL + (1f - BAR_FILL) * reading.tuned), topLeft, barSize, corner)
                drawRoundRect(color, topLeft, barSize, corner, style = outline)
            }
        },
    )
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {
            reading.applyMarkerLook(this)
        }.drawWithCache {
            val chevrons = ChevronsArt(ArrowsGeometry(size.width, size.height))
            val haloAlpha = if (roles.dark) 0.3f else 0.18f
            onDrawBehind {
                val cents = reading.cents
                val tuned = reading.tuned
                for (side in ARROW_SIDES) {
                    for (k in 0 until ARROW_COUNT) {
                        var light = arrowLight(side, k, cents)
                        var color = roles.accent
                        // In tune the inner pair closes in on the bar in the centre colour.
                        if (k == 0 && IN_TUNE_PAIR * tuned > light) {
                            light = IN_TUNE_PAIR * tuned
                            color = roles.centre(tuned)
                        }
                        if (light < MIN_LIT) continue
                        chevrons.drawHalo(this, side, k, color.copy(alpha = haloAlpha * light))
                        chevrons.draw(this, side, k, color.copy(alpha = light))
                    }
                }
            }
        },
    )
}

/** The chevrons' outlines and strokes, made once; a halo is the same chevron drawn 2.2 times as wide. */
private class ChevronsArt(arrows: ArrowsGeometry) {
    private val paths = Array(2 * ARROW_COUNT) { i -> arrows.chevron(if (i < ARROW_COUNT) -1 else 1, i % ARROW_COUNT) }
    private val strokes = Array(ARROW_COUNT) { k -> Stroke(arrows.strokeWidth(k), cap = StrokeCap.Round, join = StrokeJoin.Round) }
    private val halos = Array(ARROW_COUNT) { k -> Stroke(arrows.strokeWidth(k) * HALO_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round) }

    private fun path(side: Int, index: Int): Path = paths[(if (side < 0) 0 else ARROW_COUNT) + index]

    fun draw(scope: DrawScope, side: Int, index: Int, color: Color) =
        scope.drawPath(path(side, index), color, style = strokes[index])

    fun drawHalo(scope: DrawScope, side: Int, index: Int, color: Color) =
        scope.drawPath(path(side, index), color, style = halos[index])
}

/** Flat (−1) and sharp (1). */
private val ARROW_SIDES = intArrayOf(-1, 1)

/** Dimmer than this, a chevron is left unlit. */
private const val MIN_LIT = 0.01f

/** In tune the inner pair lights this strongly. */
private const val IN_TUNE_PAIR = 0.9f

/** The centre bar's fill off the note. */
private const val BAR_FILL = 0.18f

/** A lit chevron's halo is this many times as wide as its line. */
private const val HALO_WIDTH = 2.2f

/** The ♭ and ♯ sit this many units under the middle line, below the outer chevrons. */
private const val SIGN_BELOW_UNITS = 54f

/** The arrows' in-tune glow reaches this many units from the middle. */
private const val ARROWS_GLOW_UNITS = 100f
