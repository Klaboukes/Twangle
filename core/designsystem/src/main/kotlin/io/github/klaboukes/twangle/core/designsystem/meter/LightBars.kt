package io.github.klaboukes.twangle.core.designsystem.meter

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.rememberTextMeasurer
import io.github.klaboukes.twangle.core.settings.InTuneEffect
import kotlin.math.abs
import kotlin.math.exp

/*
 * 6. Light bars, as on the round-2 mock-up page (docs/design/meter-looks-round2-preview.html,
 * drawBars), after a pedal tuner's display, sharpened in Phase 60 (docs/design/meter-precision-preview.html,
 * drawBars with `sharp`): a row of bars mirrored up and down, with a narrow peak of light where the
 * note is. The bars stand evenly on the stretched scale (meterStretch), so near the middle there is
 * one per cent; the bars of the in-tune window are a little taller and tinted; the centre bar is
 * wider, always taller than the peak, with a dot above and below it. In tune the peak sits on it,
 * and only it and its dots turn fully "in tune".
 */

/** Bars in the row, evenly spaced on the stretched scale ([lightBarPlace]), the middle one at 0. */
internal const val LIGHT_BARS = 41
internal const val LIGHT_BAR_CENTRE = LIGHT_BARS / 2

/** The bars stand this far apart on the stretched scale ([meterStretch], in cents of the plain scale). */
internal const val LIGHT_BAR_STEP = 2.5f

/**
 * Where bar [index] stands on the stretched scale ([meterStretch]): −50 to +50 in even steps, the
 * middle one at 0. Out from the middle they stand for about 1.0, 2.1, 3.3, 4.5, 5.9, 7.4 … cents:
 * one bar per cent near the note, wider apart toward the ends.
 */
internal fun lightBarPlace(index: Int): Float = -METER_RANGE_CENTS + index * LIGHT_BAR_STEP

/**
 * Whether bar [index] stands within the in-tune window of [window] cents each way
 * ([MeterReading.inTuneWindow], placed on the stretched scale as the mock-up does): the centre bar
 * and 4 each side with Relaxed's 5 cents, 1 each side with Precise's 2.
 */
internal fun lightBarInWindow(index: Int, window: Float): Boolean = abs(lightBarPlace(index)) <= meterStretch(window)

/** How wide the peak is: its spread (σ, on the stretched scale) off the note, widening by [LIGHT_BAR_PEAK_WIDENING] as the note is reached. */
internal const val LIGHT_BAR_PEAK_SPREAD = 2.6f
internal const val LIGHT_BAR_PEAK_WIDENING = 1f

/** Where the peak sits for [cents]: on the stretched scale, pinned just past the end bar so it stays lit. */
internal fun lightBarPeakPlace(cents: Float): Float = meterStretch(cents).coerceIn(-LIGHT_BARS_LIMIT, LIGHT_BARS_LIMIT)

/**
 * How strongly bar [index] is in the peak with the marker at [cents], [tuned] of the way to "in
 * tune": a Gaussian round the reading on the stretched scale ([lightBarPeakPlace]), 1 at the
 * reading, about 0.6 one bar away and 0.16 two away (a little more as the note is reached).
 */
internal fun lightBarPeak(index: Int, cents: Float, tuned: Float = 0f): Float = lightBarShare(index, lightBarPeakPlace(cents), tuned)

/** [lightBarPeak] with the peak's place already found. */
private fun lightBarShare(index: Int, place: Float, tuned: Float): Float {
    val spread = LIGHT_BAR_PEAK_SPREAD + LIGHT_BAR_PEAK_WIDENING * tuned
    val off = lightBarPlace(index) - place
    return exp(-(off * off) / (2f * spread * spread))
}

/** The peak may sit this far out on the stretched scale, a little past the end bar. */
private const val LIGHT_BARS_LIMIT = 51.5f

/**
 * How brightly bar [index] is lit at the middle line when [peak] of it is in the peak: the peak's
 * share 1.25 times (so the few round the top are full), the centre bar's 1.6 times; at most 1.
 */
internal fun lightBarLit(index: Int, peak: Float): Float =
    (peak * if (index == LIGHT_BAR_CENTRE) CENTRE_BRIGHTNESS else BRIGHTNESS).coerceAtMost(1f)

/**
 * How far bar [index]'s light has turned "in tune" with [peak] of it in the peak and the meter
 * [tuned] of the way there: the centre bar all the way with the meter, the bars beside it at most
 * [SIDE_IN_TUNE] (and only as much as they are in the peak), so only the centre reads "in tune".
 */
internal fun lightBarInTune(index: Int, peak: Float, tuned: Float): Float =
    if (index == LIGHT_BAR_CENTRE) tuned else tuned * peak * SIDE_IN_TUNE

/** The bars beside the centre turn at most this far to "in tune". */
internal const val SIDE_IN_TUNE = 0.35f

/**
 * The row's geometry in a box [width] × [height]: the bars [SPAN_UNITS] each side of the middle,
 * mirrored about [centreY] (half way down). Each bar reaches [BASE_UNITS] each way when dark
 * ([WINDOW_BASE_UNITS] in the in-tune window) and grows by up to [PEAK_UNITS] in the peak; the centre
 * bar is [CENTRE_WIDTH_UNITS] wide and reaches [CENTRE_BASE_UNITS] plus up to [CENTRE_GROWTH_UNITS],
 * so it always stands above the peak, with a dot [DOT_UNITS] above and below the middle. The
 * heights are the mock-up's at [ROW_HEIGHT]: its dots sat just outside the 170u box, which the
 * Settings cards clip.
 */
internal class LightBarsGeometry(width: Float, height: Float) {
    val u = meterUnit(width)
    val cx = width / 2f
    val centreY = height / 2f
    val barWidth = BAR_WIDTH_UNITS * u
    val centreWidth = CENTRE_WIDTH_UNITS * u

    /** The dots' middles are this far above and below the middle line. */
    val dotOffset = DOT_UNITS * ROW_HEIGHT * u
    val dotRadius = DOT_RADIUS_UNITS * u

    /** The x of the stretched scale's [place] (−50..50, as [lightBarPlace]). */
    fun xAt(place: Float): Float = cx + place / METER_RANGE_CENTS * SPAN_UNITS * u

    /** The x of bar [index]. */
    fun x(index: Int): Float = xAt(lightBarPlace(index))

    /** How wide bar [index] is. */
    fun width(index: Int): Float = if (index == LIGHT_BAR_CENTRE) centreWidth else barWidth

    /** How far bar [index] reaches each way from the middle line when [peak] (0..1) of it is in the peak, a bar of the in-tune window if [inWindow]. */
    fun reach(index: Int, peak: Float, inWindow: Boolean = false): Float = ROW_HEIGHT * u * when {
        index == LIGHT_BAR_CENTRE -> CENTRE_BASE_UNITS + CENTRE_GROWTH_UNITS * peak
        inWindow -> WINDOW_BASE_UNITS + PEAK_UNITS * peak
        else -> BASE_UNITS + PEAK_UNITS * peak
    }

    companion object {
        const val SPAN_UNITS = 150f
        const val BAR_WIDTH_UNITS = 3.6f
        const val BASE_UNITS = 5f
        const val WINDOW_BASE_UNITS = 8f
        const val PEAK_UNITS = 62f
        const val CENTRE_WIDTH_UNITS = 5.6f
        const val CENTRE_BASE_UNITS = 16f
        const val CENTRE_GROWTH_UNITS = 64f
        const val DOT_UNITS = 89f
        const val DOT_RADIUS_UNITS = 3.2f

        /** The mock-up's heights are drawn at this share, so the dots stay inside the box. */
        const val ROW_HEIGHT = 0.91f
    }
}

/**
 * Light bars: the calm field, the bars outside the in-tune window (dark, in the track colour) and
 * the ♭ and ♯ (static, made again only when the window changes); the in-tune glow; the middle in a
 * layer of its own (the window's bars, tinted toward ink and blending toward "in tune" as it is
 * reached, the centre bar part way to the centre colour and its two dots in it), redrawn only as a
 * note comes and goes and as it is reached; and the peak, redrawn from the reading in its own layer:
 * a soft halo of the accent where the note is and each bar in it grown and lit, fading toward its
 * tips over its own length, the centre bar turning "in tune" as the note is reached.
 */
@Composable
internal fun BoxScope.LightBarsMeter(reading: MeterReading, effect: InTuneEffect) {
    val roles = meterRoles(effect)
    val measurer = rememberTextMeasurer()
    StaticArt {
        val bars = LightBarsGeometry(size.width, size.height)
        val u = bars.u
        val field = meterField(size, roles)
        val colors = LightBarColors(roles)
        val window = reading.inTuneWindow
        val plain = (0 until LIGHT_BARS).filter { !lightBarInWindow(it, window) }.toIntArray()
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
            for (i in plain) {
                val x = bars.x(i)
                val reach = bars.reach(i, 0f)
                drawLine(colors.plain, Offset(x, bars.centreY - reach), Offset(x, bars.centreY + reach), bars.barWidth, StrokeCap.Round)
            }
            signs.draw(this)
        }
        draw
    }
    TuneGlow(roles, reading, LIGHT_BARS_GLOW_UNITS, centre = { size -> Offset(size.width / 2f, size.height / 2f) })
    // The middle. Reading the window here makes it again when the setting changes.
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {}.drawWithCache {
            val art = LightBarsMiddleArt(LightBarsGeometry(size.width, size.height), roles, reading.inTuneWindow)
            onDrawBehind { art.draw(this, reading.tuned, reading.presence) }
        },
    )
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {
            reading.applyMarkerLook(this)
        }.drawWithCache {
            val art = LightBarsPeakArt(LightBarsGeometry(size.width, size.height), roles, reading.inTuneWindow)
            onDrawBehind { art.draw(this, reading.cents, reading.tuned, reading.presence) }
        },
    )
}

/**
 * The bars' dark colours: in the track colour (a hair see-through in light), the in-tune window's
 * [WINDOW_INK] toward ink and blending [WINDOW_IN_TUNE] of the way to "in tune" as it is reached, the
 * centre bar [CENTRE_TINT] of the way to the centre colour (muted at rest, [MeterRoles.centre]).
 */
private class LightBarColors(private val roles: MeterRoles) {
    private val alpha = if (roles.dark) 1f else 0.95f
    val plain: Color = roles.track.copy(alpha = alpha)
    private val window = mix(roles.track, roles.ink, WINDOW_INK)

    /** A bar of the in-tune window with the meter [tuned] of the way to "in tune". */
    fun window(tuned: Float): Color = mix(window, roles.inTune, WINDOW_IN_TUNE * tuned).copy(alpha = alpha)

    /** The centre bar, with [presence] of a note. */
    fun centre(presence: Float): Color = mix(roles.track, roles.centre(0f, presence), CENTRE_TINT).copy(alpha = alpha)

    private companion object {
        const val WINDOW_INK = 0.14f
        const val WINDOW_IN_TUNE = 0.45f
        const val CENTRE_TINT = 0.85f
    }
}

/**
 * The middle of the row, at rest: the in-tune window's bars ([LightBarsGeometry.WINDOW_BASE_UNITS]),
 * the centre bar ([LightBarsGeometry.CENTRE_BASE_UNITS]) and its two dots in the centre colour, all
 * [tuned] of the way to "in tune" and muted without a note ([presence]).
 */
private class LightBarsMiddleArt(private val bars: LightBarsGeometry, private val roles: MeterRoles, window: Float) {
    private val colors = LightBarColors(roles)
    private val windowBars = (0 until LIGHT_BARS).filter { it != LIGHT_BAR_CENTRE && lightBarInWindow(it, window) }.toIntArray()
    private val above = Offset(bars.cx, bars.centreY - bars.dotOffset)
    private val below = Offset(bars.cx, bars.centreY + bars.dotOffset)

    fun draw(scope: DrawScope, tuned: Float, presence: Float) = with(scope) {
        val windowColor = colors.window(tuned)
        for (i in windowBars) {
            val x = bars.x(i)
            val reach = bars.reach(i, 0f, inWindow = true)
            drawLine(windowColor, Offset(x, bars.centreY - reach), Offset(x, bars.centreY + reach), bars.barWidth, StrokeCap.Round)
        }
        val reach = bars.reach(LIGHT_BAR_CENTRE, 0f)
        drawLine(colors.centre(presence), Offset(bars.cx, bars.centreY - reach), Offset(bars.cx, bars.centreY + reach), bars.centreWidth, StrokeCap.Round)
        val dot = roles.centre(tuned, presence)
        drawCircle(dot, bars.dotRadius, above)
        drawCircle(dot, bars.dotRadius, below)
    }
}

/**
 * The peak: a soft halo of the accent ([HALO_UNITS]) at the reading, giving way to the in-tune glow
 * as the note is reached; and each bar in it grown to its reach in its dark colour, then lit over
 * it, brightest at the middle line and fading to [TIP] of that at its ends, over its own length (the
 * mock-up's gradient per bar). Its light is the accent (muted without a note), turning "in tune" as
 * [lightBarInTune] says. One brush per colour is made once, from −1 to 1; each bar scales the canvas
 * to its own reach and narrows its rounded ends' height by as much, so the ends stay round and
 * nothing is allocated.
 */
private class LightBarsPeakArt(private val bars: LightBarsGeometry, roles: MeterRoles, window: Float) {
    private val colors = LightBarColors(roles)
    private val inWindow = BooleanArray(LIGHT_BARS) { it != LIGHT_BAR_CENTRE && lightBarInWindow(it, window) }
    private val muted = litBrush(roles.muted)
    private val accent = litBrush(roles.accent)
    private val inTune = litBrush(roles.inTune)
    private val halo = GlowArt(HALO_UNITS * bars.u, roles.accent)
    private val haloAlpha = (if (roles.dark) 0.26f else 0.14f) * HALO_SHARE

    fun draw(scope: DrawScope, cents: Float, tuned: Float, presence: Float) = with(scope) {
        val place = lightBarPeakPlace(cents)
        halo.draw(this, bars.xAt(place), bars.centreY, haloAlpha * (1f - tuned) * presence)
        val centreColor = colors.centre(presence)
        val windowColor = colors.window(tuned)
        for (i in 0 until LIGHT_BARS) {
            val peak = lightBarShare(i, place, tuned)
            if (peak < MIN_LIT) continue
            val x = bars.x(i)
            val reach = bars.reach(i, peak, inWindow[i])
            val width = bars.width(i)
            val dark = when {
                i == LIGHT_BAR_CENTRE -> centreColor
                inWindow[i] -> windowColor
                else -> colors.plain
            }
            drawLine(dark, Offset(x, bars.centreY - reach), Offset(x, bars.centreY + reach), width, StrokeCap.Round)
            val lit = lightBarLit(i, peak)
            val green = lightBarInTune(i, peak, tuned)
            // Each colour over the one before at its share, the first whole: exact at the middle line.
            if (green < 1f) {
                if (presence < 1f) drawLight(muted, x, reach, width, lit)
                if (presence > 0f) drawLight(accent, x, reach, width, if (presence < 1f) lit * presence else lit)
                if (green > 0f) drawLight(inTune, x, reach, width, lit * green)
            } else {
                drawLight(inTune, x, reach, width, lit)
            }
        }
    }

    /** A lit bar at [x], [reach] each way and [width] wide (round ends), in [brush] at [alpha]. */
    private fun DrawScope.drawLight(brush: Brush, x: Float, reach: Float, width: Float, alpha: Float) {
        val r = width / 2f
        val end = r / reach
        withTransform({
            translate(x, bars.centreY)
            scale(1f, reach, pivot = Offset.Zero)
        }) {
            drawRoundRect(brush, Offset(-r, -1f - end), Size(width, 2f + 2f * end), CornerRadius(r, end), alpha = alpha)
        }
    }
}

/** A bar's light in [color] along −1..1: full at 0, [TIP] at the ends and past them. */
private fun litBrush(color: Color): Brush = Brush.verticalGradient(
    0f to color.copy(alpha = color.alpha * TIP),
    0.5f to color,
    1f to color.copy(alpha = color.alpha * TIP),
    startY = -1f,
    endY = 1f,
)

/** A bar's tips are lit this much of its middle. */
private const val TIP = 0.3f

/** A bar this faintly in the peak is left dark. */
private const val MIN_LIT = 0.01f

/** The peak's bars are lit this many times their peak share (so the few round the top are full) … */
private const val BRIGHTNESS = 1.25f

/** … and the centre bar this many. */
private const val CENTRE_BRIGHTNESS = 1.6f

/** The peak's halo reaches this many units … */
private const val HALO_UNITS = 56f

/** … at this share of the old halo's strength (26 % dark, 14 % light) off the note. */
private const val HALO_SHARE = 0.6f

/** The ♭ and ♯ sit this many units in from the box's sides … */
private const val SIGN_IN_UNITS = 18f

/** … and this many up from its bottom. */
private const val SIGN_UP_UNITS = 12f

/** The light bars' in-tune glow reaches this many units from the middle. */
private const val LIGHT_BARS_GLOW_UNITS = 80f
