package io.github.klaboukes.twangle.core.designsystem.meter

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.rememberTextMeasurer
import io.github.klaboukes.twangle.core.settings.InTuneEffect
import kotlin.math.exp

/*
 * 6. Light bars, as on the round-2 mock-up page (docs/design/meter-looks-round2-preview.html,
 * drawBars), after a pedal tuner's display: a row of bars mirrored up and down, with a soft peak of
 * light where the note is. The centre bar is taller and tinted; in tune the peak sits on it.
 */

/** Bars in the row, one every [LIGHT_BAR_STEP_CENTS] from −50 to +50, the middle one at 0. */
internal const val LIGHT_BARS = 41
internal const val LIGHT_BAR_STEP_CENTS = 2.5f
internal const val LIGHT_BAR_CENTRE = LIGHT_BARS / 2

/** Where bar [index] stands, in cents. */
internal fun lightBarCents(index: Int): Float = -METER_RANGE_CENTS + index * LIGHT_BAR_STEP_CENTS

/** How wide the peak is: its spread (σ, in cents) off the note, widening by [PEAK_WIDENING] as the note is reached. */
internal const val PEAK_SPREAD_CENTS = 4.5f
internal const val PEAK_WIDENING = 2f

/**
 * How strongly bar [index] is lit with the marker at [cents], [tuned] of the way to "in tune": a
 * Gaussian peak round the reading (the reading pinned just past the range, so the end bar stays lit),
 * 1 at the reading, half way 1.18 spreads away.
 */
internal fun lightBarPeak(index: Int, cents: Float, tuned: Float = 0f): Float {
    val reading = cents.coerceIn(-LIGHT_BARS_LIMIT_CENTS, LIGHT_BARS_LIMIT_CENTS)
    val spread = PEAK_SPREAD_CENTS + PEAK_WIDENING * tuned
    val off = lightBarCents(index) - reading
    return exp(-(off * off) / (2f * spread * spread))
}

/** The peak may sit this far out, a little past the end bar. */
private const val LIGHT_BARS_LIMIT_CENTS = 51.5f

/**
 * The row's geometry in a box [width] × [height]: the bars [SPAN_UNITS] each side of the middle,
 * mirrored about [centreY] (half way down). Each bar is [BASE_UNITS] long each way when dark (the centre
 * bar [CENTRE_BASE_UNITS]), and grows by up to [PEAK_UNITS] each way in the peak.
 */
internal class LightBarsGeometry(width: Float, height: Float) {
    val u = meterUnit(width)
    val cx = width / 2f
    val centreY = height / 2f
    val barWidth = BAR_WIDTH_UNITS * u

    /** The x of bar [index]. */
    fun x(index: Int): Float = cx + lightBarCents(index) / METER_RANGE_CENTS * SPAN_UNITS * u

    /** How far bar [index] reaches each way from the middle line when lit [peak] (0..1). */
    fun reach(index: Int, peak: Float): Float = ((if (index == LIGHT_BAR_CENTRE) CENTRE_BASE_UNITS else BASE_UNITS) + PEAK_UNITS * peak) * u

    companion object {
        const val SPAN_UNITS = 150f
        const val BAR_WIDTH_UNITS = 3.6f
        const val BASE_UNITS = 5f
        const val CENTRE_BASE_UNITS = 12f
        const val PEAK_UNITS = 62f
    }
}

/**
 * Light bars: the calm field, the bars dark (in the track colour, the centre bar part way to the
 * accent) and the ♭ and ♯ (static); the in-tune glow; and the peak, redrawn from the reading in its own
 * layer: a soft halo of the accent where the note is, and each lit bar grown and lit in the accent
 * (fading toward its tips), the bars near the middle blending to "in tune" as the note is reached.
 */
@Composable
internal fun BoxScope.LightBarsMeter(reading: MeterReading, effect: InTuneEffect) {
    val roles = meterRoles(effect)
    val measurer = rememberTextMeasurer()
    StaticArt {
        val bars = LightBarsGeometry(size.width, size.height)
        val u = bars.u
        val field = meterField(size, roles)
        val dark = roles.track.copy(alpha = if (roles.dark) 1f else 0.95f)
        val centreDark = mix(roles.track, roles.accent, 0.6f)
        val signY = size.height - SIGN_UP_UNITS * u
        val signs = SignsArt(
            measurer, this, u, roles,
            flatX = SIGN_IN_UNITS * u,
            sharpX = size.width - SIGN_IN_UNITS * u,
            y = signY,
            maxHeight = 2f * (size.height - u - signY),
        )
        val draw: DrawScope.() -> Unit = {
            field.draw(this)
            for (i in 0 until LIGHT_BARS) {
                val x = bars.x(i)
                val reach = bars.reach(i, 0f)
                drawLine(if (i == LIGHT_BAR_CENTRE) centreDark else dark, Offset(x, bars.centreY - reach), Offset(x, bars.centreY + reach), bars.barWidth, StrokeCap.Round)
            }
            signs.draw(this)
        }
        draw
    }
    TuneGlow(roles, reading, LIGHT_BARS_GLOW_UNITS, centre = { size -> Offset(size.width / 2f, size.height / 2f) })
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {
            reading.applyMarkerLook(this)
        }.drawWithCache {
            val bars = LightBarsGeometry(size.width, size.height)
            val top = bars.centreY - bars.reach(0, 1f)
            val bottom = bars.centreY + bars.reach(0, 1f)
            // One brush per colour over the tallest bar's length: a bar is brightest at the middle line.
            val accent = Brush.verticalGradient(
                0f to roles.accent.copy(alpha = TIP),
                0.5f to roles.accent,
                1f to roles.accent.copy(alpha = TIP),
                startY = top,
                endY = bottom,
            )
            val inTune = Brush.verticalGradient(
                0f to roles.inTune.copy(alpha = TIP),
                0.5f to roles.inTune,
                1f to roles.inTune.copy(alpha = TIP),
                startY = top,
                endY = bottom,
            )
            val halo = GlowArt(HALO_UNITS * bars.u, roles.accent)
            val haloAlpha = if (roles.dark) 0.26f else 0.14f
            val limit = LightBarsGeometry.SPAN_UNITS * bars.u * LIGHT_BARS_LIMIT_CENTS / METER_RANGE_CENTS
            onDrawBehind {
                val cents = reading.cents
                val tuned = reading.tuned
                val peakX = bars.cx + (cents / METER_RANGE_CENTS * LightBarsGeometry.SPAN_UNITS * bars.u).coerceIn(-limit, limit)
                halo.draw(this, peakX, bars.centreY, haloAlpha * (1f - tuned))
                for (i in 0 until LIGHT_BARS) {
                    val peak = lightBarPeak(i, cents, tuned)
                    if (peak < MIN_LIT) continue
                    val x = bars.x(i)
                    val reach = bars.reach(i, peak)
                    val start = Offset(x, bars.centreY - reach)
                    val end = Offset(x, bars.centreY + reach)
                    val lit = (peak * BRIGHTNESS).coerceAtMost(1f)
                    // Near the middle the peak turns "in tune" with the note.
                    val green = tuned * peak
                    if (green < 1f) drawLine(accent, start, end, bars.barWidth, StrokeCap.Round, alpha = lit * (1f - green))
                    if (green > 0f) drawLine(inTune, start, end, bars.barWidth, StrokeCap.Round, alpha = lit * green)
                }
            }
        },
    )
}

/** A bar's tips are lit this much of its middle. */
private const val TIP = 0.3f

/** A bar this faintly in the peak is left dark. */
private const val MIN_LIT = 0.01f

/** The peak's bars are lit this many times their peak share (so the few round the top are full). */
private const val BRIGHTNESS = 1.25f

/** The peak's halo reaches this many units. */
private const val HALO_UNITS = 72f

/** The ♭ and ♯ sit this many units in from the box's sides … */
private const val SIGN_IN_UNITS = 18f

/** … and this many up from its bottom. */
private const val SIGN_UP_UNITS = 12f

/** The light bars' in-tune glow reaches this many units from the middle. */
private const val LIGHT_BARS_GLOW_UNITS = 80f
