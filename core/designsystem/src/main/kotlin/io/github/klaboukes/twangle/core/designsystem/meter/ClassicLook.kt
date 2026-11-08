package io.github.klaboukes.twangle.core.designsystem.meter

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import io.github.klaboukes.twangle.core.designsystem.LINING_FIGURES
import io.github.klaboukes.twangle.core.designsystem.appLocale
import io.github.klaboukes.twangle.core.designsystem.lens.LensReading
import io.github.klaboukes.twangle.core.designsystem.localDigits
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * Classic (Phase 78; 1.1's Classic meter look, docs/design/1.x/meter-looks-preview.html, in Ink's plain finish, which
 * was 1.1 Contemporary's): a needle on a half-circle dial, the most familiar tuner face and the most precise, the
 * meter's default since the trail left it. The scale is plain (no stretch: a numbered scale must stay even), so a
 * cent turns the needle 1.8° everywhere, about 4.7 dp at its tip on a phone. It sits straight on the page like the
 * rest of the meter: no plate, no glass, no frame.
 */

/**
 * The dial's geometry in a box of [size]: 1.1's dial, [DIAL_WIDE_UNITS] × [DIAL_HIGH_UNITS] units (a half circle of
 * [DIAL_RADIUS_UNITS] round a pivot [DIAL_PIVOT_UNITS] above its bottom), made as large as fits the box both ways and
 * centred in it; [u] is its unit, about 1 dp on a phone upright. Angles are clockwise from straight up, so a positive
 * angle is to the right (sharp). Pure, so it is tested on the JVM (`ClassicDialTest`).
 */
internal class ClassicDial(size: Size) {
    val u = min(size.width / DIAL_WIDE_UNITS, size.height / DIAL_HIGH_UNITS).coerceAtLeast(0f)
    val cx = size.width / 2f

    /** The pivot: [DIAL_PIVOT_UNITS] above the bottom of the dial, which is centred in the box's height. */
    val cy = (size.height + DIAL_HIGH_UNITS * u) / 2f - DIAL_PIVOT_UNITS * u
    val r = DIAL_RADIUS_UNITS * u

    /** The x of the point [distance] from the pivot towards [cents]. */
    fun x(distance: Float, cents: Float): Float = cx + distance * sin(meterAngle(cents))

    /** The y of the point [distance] from the pivot towards [cents]. */
    fun y(distance: Float, cents: Float): Float = cy - distance * cos(meterAngle(cents))

    /** The needle's tip, [NEEDLE_SHORT_UNITS] inside the arc, over the ticks' outer ends. */
    val needleLength = r - NEEDLE_SHORT_UNITS * u

    companion object {
        /** 1.1's open-faced dial, 2 : 1: 340 units across and 170 high. */
        const val DIAL_WIDE_UNITS = 340f
        const val DIAL_HIGH_UNITS = 170f
        const val DIAL_RADIUS_UNITS = 146f
        const val DIAL_PIVOT_UNITS = 17f
        const val NEEDLE_SHORT_UNITS = 8f
    }
}

/**
 * Classic in the meter, from one [reading]: the dial under the needle (the arc, the scale's ticks and numbers,
 * static), the in-tune zone on the arc and the centre mark (in a layer of their own, fading to "in tune"), the
 * in-tune glow, the needle and its soft shadow (each drawn once pointing up in a layer that only turns, so a reading
 * redraws nothing), and the hub over the pivot. The needle swings a little past a new string before it settles
 * ([MeterFeels.CLASSIC]) and may go [NEEDLE_LIMIT_CENTS] past the end, as a real one would. The meter draws ♭ and ♯.
 */
@Composable
internal fun BoxScope.ClassicLook(reading: LensReading) {
    val roles = meterRoles()
    val measurer = rememberTextMeasurer()
    val numbers = MaterialTheme.typography.labelMedium.fontFamily ?: FontFamily.Default
    val locale = appLocale()
    StaticArt {
        val dial = ClassicDial(size)
        val track = Stroke(TRACK_WIDTH_UNITS * roles.lineScale * dial.u)
        val scale = ClassicScale(dial, roles, measurer, this, numbers, locale)
        val draw: DrawScope.() -> Unit = {
            drawArc(
                roles.track,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(dial.cx - dial.r, dial.cy - dial.r),
                size = Size(dial.r * 2f, dial.r * 2f),
                style = track,
            )
            scale.draw(this)
        }
        draw
    }
    TuneGlow(roles, reading) {
        val dial = ClassicDial(size)
        Offset(dial.cx, dial.cy - dial.r + GLOW_DOWN_UNITS * dial.u) to GLOW_UNITS * dial.u
    }
    // The in-tune zone and the centre mark, in a layer of their own so their fade redraws nothing else. Reading
    // the window here draws the zone again when the setting changes.
    Spacer(
        Modifier.matchParentSize().graphicsLayer {}.drawWithCache {
            val dial = ClassicDial(size)
            // In high contrast the zone is wider and drawn in full: the ink off the note, green in tune.
            val zone = Stroke(ZONE_WIDTH_UNITS * roles.lineScale * dial.u, cap = StrokeCap.Round)
            val markTop = Offset(dial.cx, dial.cy - dial.r - 2f * roles.lineScale * dial.u)
            val markBottom = Offset(dial.cx, dial.cy - dial.r + 16f * dial.u)
            val markWidth = 3.5f * roles.lineScale * dial.u
            val zoneAlpha = roles.fade(ZONE_ALPHA)
            onDrawBehind {
                val tuned = reading.tuned
                val colour = roles.centre(tuned)
                val window = reading.inTuneWindow
                val sweep = window / METER_RANGE_CENTS * 90f
                drawArc(
                    colour.copy(alpha = zoneAlpha + (1f - zoneAlpha) * tuned),
                    startAngle = -90f - sweep,
                    sweepAngle = 2f * sweep,
                    useCenter = false,
                    topLeft = Offset(dial.cx - dial.r, dial.cy - dial.r),
                    size = Size(dial.r * 2f, dial.r * 2f),
                    style = zone,
                )
                drawLine(colour, markBottom, markTop, markWidth, StrokeCap.Round)
            }
        },
    )
    // The needle's shadow, turned with it and then shifted down and right, so it always falls the same way. None in
    // high contrast, where the needle stands alone on the page.
    if (!roles.highContrast) Spacer(
        Modifier.matchParentSize().graphicsLayer {
            turnAboutPivot(reading.cents)
            val u = ClassicDial(size).u
            translationX = SHADOW_X_UNITS * u
            translationY = SHADOW_Y_UNITS * u
            reading.applyMarkerLook(this)
        }.drawWithCache {
            val needle = ClassicNeedle(ClassicDial(size), roles)
            onDrawBehind { needle.drawShadow(this) }
        },
    )
    Spacer(
        Modifier.matchParentSize().graphicsLayer {
            turnAboutPivot(reading.cents)
            reading.applyMarkerLook(this)
        }.drawWithCache {
            val needle = ClassicNeedle(ClassicDial(size), roles)
            // In high contrast the needle stays the ink: in tune shows by the zone, the mark and the word.
            val green = if (roles.highContrast) 0f else NEEDLE_GREEN
            onDrawBehind { needle.draw(this, mix(roles.ink, roles.inTune, reading.tuned * green)) }
        },
    )
    // The hub, which doesn't turn: a plain ink dot with the needle's shadow.
    Spacer(
        Modifier.matchParentSize().drawWithCache {
            val dial = ClassicDial(size)
            val shadow = markerShadow(dial.u, roles.dark)
            val radius = HUB_UNITS * (if (roles.highContrast) HIGH_CONTRAST_HUB else 1f) * dial.u
            onDrawBehind {
                if (!roles.highContrast) {
                    drawIntoCanvas { it.nativeCanvas.drawCircle(dial.cx + SHADOW_X_UNITS * dial.u, dial.cy + SHADOW_Y_UNITS * dial.u, radius, shadow) }
                }
                drawCircle(roles.ink, radius, Offset(dial.cx, dial.cy))
            }
        },
    )
}

/** Turns a layer drawn pointing up about the dial's pivot to [cents]. */
private fun GraphicsLayerScope.turnAboutPivot(cents: Float) {
    val dial = ClassicDial(size)
    transformOrigin = TransformOrigin(if (size.width > 0f) dial.cx / size.width else 0.5f, if (size.height > 0f) dial.cy / size.height else 1f)
    rotationZ = needleDegrees(cents.coerceIn(-NEEDLE_LIMIT_CENTS, NEEDLE_LIMIT_CENTS))
}

/**
 * The scale (1.1's `ScaleMarks` on the dial): a tick every 5 cents, none at 0, from [TICK_IN_UNITS] inside the arc
 * inwards; major ticks (every 10) [MAJOR_TICK_UNITS] long and [MAJOR_TICK_WIDTH_UNITS] wide in ink, minor ones
 * [MINOR_TICK_UNITS] and [MINOR_TICK_WIDTH_UNITS], ink at [MINOR_TICK_INK]; the numbers 10 to 50 without a sign in
 * the app language's digits, [NUMBER_SIZE_UNITS] high, ink at [NUMBER_INK], centred [NUMBER_IN_UNITS] inside their
 * tick's base. Made once per size and colours.
 */
private class ClassicScale(
    private val dial: ClassicDial,
    roles: MeterRoles,
    measurer: TextMeasurer,
    density: Density,
    numbers: FontFamily,
    locale: Locale,
) {
    /** Each tick's ends and width: x1, y1, x2, y2, width. */
    private val ticks = FloatArray(TICK_COUNT * 5)
    private val major = BooleanArray(TICK_COUNT)
    private val ink = roles.ink
    private val minorInk = roles.ink.copy(alpha = roles.fade(MINOR_TICK_INK))
    private val numberInk = roles.ink.copy(alpha = roles.fade(NUMBER_INK))

    /** High contrast's ticks are squared off, as the mock-up's, so their thicker ends don't run together. */
    private val cap = if (roles.highContrast) StrokeCap.Butt else StrokeCap.Round
    private val texts = arrayOfNulls<TextLayoutResult>(NUMBER_COUNT)
    private val textAt = FloatArray(NUMBER_COUNT * 2)

    init {
        val u = dial.u
        val style = TextStyle(
            fontFamily = numbers,
            fontSize = with(density) { (NUMBER_SIZE_UNITS * u).toSp() },
            fontWeight = if (roles.highContrast) FontWeight.ExtraBold else null,
            fontFeatureSettings = LINING_FIGURES,
        )
        val measured = (1..5).map { measurer.measure(localDigits(it * 10, locale), style) }
        var t = 0
        var n = 0
        var cents = -METER_RANGE_CENTS
        while (cents <= METER_RANGE_CENTS) {
            if (cents != 0f) {
                val isMajor = cents.toInt() % 10 == 0
                val base = dial.r - TICK_IN_UNITS * u
                val length = (if (isMajor) MAJOR_TICK_UNITS else MINOR_TICK_UNITS) * (if (roles.highContrast) HIGH_CONTRAST_TICK_LENGTH else 1f) * u
                ticks[t * 5] = dial.x(base, cents)
                ticks[t * 5 + 1] = dial.y(base, cents)
                ticks[t * 5 + 2] = dial.x(base - length, cents)
                ticks[t * 5 + 3] = dial.y(base - length, cents)
                ticks[t * 5 + 4] = (if (isMajor) MAJOR_TICK_WIDTH_UNITS else MINOR_TICK_WIDTH_UNITS) * roles.lineScale * u
                major[t] = isMajor
                t++
                if (isMajor) {
                    val text = measured[(abs(cents) / 10f).toInt() - 1]
                    texts[n] = text
                    textAt[n * 2] = dial.x(base - NUMBER_IN_UNITS * u, cents) - text.size.width / 2f
                    textAt[n * 2 + 1] = dial.y(base - NUMBER_IN_UNITS * u, cents) - text.size.height / 2f
                    n++
                }
            }
            cents += 5f
        }
    }

    fun draw(scope: DrawScope) = with(scope) {
        for (t in 0 until TICK_COUNT) {
            drawLine(
                if (major[t]) ink else minorInk,
                Offset(ticks[t * 5], ticks[t * 5 + 1]),
                Offset(ticks[t * 5 + 2], ticks[t * 5 + 3]),
                ticks[t * 5 + 4],
                cap,
            )
        }
        for (k in texts.indices) {
            val text = texts[k] ?: continue
            drawText(text, color = numberInk, topLeft = Offset(textAt[k * 2], textAt[k * 2 + 1]))
        }
    }

    private companion object {
        /** Twenty ticks: every 5 cents from −50 to 50, none at 0. */
        const val TICK_COUNT = 20

        /** Ten numbers: 50, 40, … 10 and 10, 20, … 50. */
        const val NUMBER_COUNT = 10
    }
}

/**
 * The needle: Ink's plain rounded bar (1.1 Contemporary's), [NEEDLE_WIDTH_UNITS] wide, from the pivot up to the
 * dial's [ClassicDial.needleLength], drawn pointing up; its layer turns it. [drawShadow] draws its silhouette blurred
 * for the shadow layer under it.
 */
private class ClassicNeedle(dial: ClassicDial, roles: MeterRoles) {
    private val half = NEEDLE_WIDTH_UNITS * (if (roles.highContrast) HIGH_CONTRAST_NEEDLE else 1f) * dial.u / 2f
    private val body = Path().apply {
        addRoundRect(
            RoundRect(dial.cx - half, dial.cy - dial.needleLength - half, dial.cx + half, dial.cy + half, CornerRadius(half)),
        )
    }
    private val nativeBody = body.asAndroidPath()
    private val shadow = markerShadow(dial.u, roles.dark)

    fun draw(scope: DrawScope, colour: Color) = with(scope) { drawPath(body, colour) }

    fun drawShadow(scope: DrawScope) = with(scope) {
        drawIntoCanvas { it.nativeCanvas.drawPath(nativeBody, shadow) }
    }
}

/**
 * The needle's and the hub's soft shadow (1.1's marker shadow): a 3-unit blur, black at 45 % in dark and 22 % in light,
 * drawn [SHADOW_X_UNITS] right and [SHADOW_Y_UNITS] down. Android draws the blur with hardware acceleration from
 * Android 9; on Android 8 the needle just has no shadow.
 */
private fun markerShadow(u: Float, dark: Boolean): android.graphics.Paint =
    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.Black.copy(alpha = if (dark) 0.45f else 0.22f).toArgb()
        // A blur's sigma from its size, as CSS draws it (1.1's blurRadius).
        maskFilter = BlurMaskFilter(((3f * u / 2f - 0.5f) / 0.57735f).coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
    }

/** The arc's line. */
private const val TRACK_WIDTH_UNITS = 1.8f

/** The in-tune zone on the arc: muted at this opacity off the note, the "in tune" colour in full as it is reached. */
private const val ZONE_WIDTH_UNITS = 4f
private const val ZONE_ALPHA = 0.55f

/** The ticks start this far inside the arc. */
private const val TICK_IN_UNITS = 4f
private const val MAJOR_TICK_UNITS = 13f
private const val MAJOR_TICK_WIDTH_UNITS = 2.1f
private const val MINOR_TICK_UNITS = 7f
private const val MINOR_TICK_WIDTH_UNITS = 1.3f

/** How strong the minor ticks and the numbers are, as ink: 1.1's, readable at 4.5 : 1 and 3 : 1. */
private const val MINOR_TICK_INK = 0.5f
private const val NUMBER_INK = 0.8f
private const val NUMBER_SIZE_UNITS = 12.5f

/** The numbers' middles sit this far inside their tick's base. */
private const val NUMBER_IN_UNITS = 27f

/** The in-tune glow: this many units round a point this far under the arc's top. */
private const val GLOW_UNITS = 80f
private const val GLOW_DOWN_UNITS = 14f

private const val NEEDLE_WIDTH_UNITS = 2.6f

/**
 * The high-contrast look (Phase 86, the mock-up's): the needle 6 units wide instead of 2.6, the hub this much larger,
 * the ticks this much longer (and [MeterRoles.lineScale] thicker), so they read at arm's length and through a blur.
 */
private const val HIGH_CONTRAST_NEEDLE = 2.3f
private const val HIGH_CONTRAST_HUB = 1.6f
private const val HIGH_CONTRAST_TICK_LENGTH = 1.3f

/** In tune, the needle goes this far towards the "in tune" colour. */
private const val NEEDLE_GREEN = 0.9f
private const val HUB_UNITS = 5f
private const val SHADOW_X_UNITS = 1.5f
private const val SHADOW_Y_UNITS = 2.5f

/** A needle's swing may carry it a little past the end of the scale, as a real one would. */
private const val NEEDLE_LIMIT_CENTS = 53f
