package io.github.klaboukes.twangle.core.designsystem.meter

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
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
 * rest of the meter: no plate, no glass, no frame. Since 2026-11-11 (the owner's A from
 * docs/design/classic-meter-preview.html) the needle tapers to a fine tip with a short tail behind a ring hub, and the
 * scale has a tick for every cent near the note, where the tuning happens.
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

/** A mark on Classic's scale: the tens (numbered), the fives, and the single cents near the note. */
internal enum class ClassicTick { TEN, FIVE, ONE }

/**
 * The scale's mark at a whole [cents], or null: none at 0 (the centre mark is there) or past ±50; a tick for every
 * cent within [FINE_TICKS_CENTS] of the note, where the tuning happens, and every 5 cents beyond. Pure, so it is
 * tested on the JVM (`ClassicDialTest`).
 */
internal fun classicTickAt(cents: Int): ClassicTick? = when {
    cents == 0 || abs(cents) > METER_RANGE_CENTS -> null
    cents % 10 == 0 -> ClassicTick.TEN
    cents % 5 == 0 -> ClassicTick.FIVE
    abs(cents) < FINE_TICKS_CENTS -> ClassicTick.ONE
    else -> null
}

/** The single-cent ticks run this far each way from the note. */
internal const val FINE_TICKS_CENTS = 10

/**
 * Classic in the meter, from one [reading]: the dial under the needle (the arc, the scale's ticks and numbers,
 * static), the in-tune zone on the arc and the centre mark (in a layer of their own, fading to "in tune"), the
 * in-tune glow, the needle and its soft shadow (each drawn once pointing up in a layer that only turns, so a reading
 * redraws nothing), and the ring hub over the pivot. The needle swings a little past a new string before it settles
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
        // A touch stronger than the line colour, so the arc reads at a glance (the same ink in high contrast).
        val trackColour = mix(roles.track, roles.muted, TRACK_TOWARDS_MUTED)
        val scale = ClassicScale(dial, roles, measurer, this, numbers, locale)
        val draw: DrawScope.() -> Unit = {
            drawArc(
                trackColour,
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
            // The note's mark: a tick through the arc, longer than the tens.
            val markTop = Offset(dial.cx, dial.cy - dial.r - MARK_OUT_UNITS * dial.u)
            val markBottom = Offset(dial.cx, dial.cy - dial.r + MARK_IN_UNITS * dial.u)
            val markWidth = MARK_WIDTH_UNITS * roles.lineScale * dial.u
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
    // The hub, which doesn't turn: an ink ring with the needle's shadow, its hole cut through the needle too, so the
    // page shows through it (the page may be tinted, so the hole is never painted).
    Spacer(
        Modifier.matchParentSize().drawWithCache {
            val dial = ClassicDial(size)
            val hub = ClassicHub(dial, roles)
            val shadow = markerShadow(dial.u, roles.dark).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = hub.width
            }
            val ring = Stroke(hub.width)
            onDrawBehind {
                if (!roles.highContrast) {
                    drawIntoCanvas { it.nativeCanvas.drawCircle(dial.cx + SHADOW_X_UNITS * dial.u, dial.cy + SHADOW_Y_UNITS * dial.u, hub.middle, shadow) }
                }
                drawCircle(roles.ink, hub.middle, Offset(dial.cx, dial.cy), style = ring)
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
 * The hub's ring: [HUB_UNITS] round the pivot outside, a [HUB_HOLE_UNITS] hole inside ([HIGH_CONTRAST_HUB] times
 * both in high contrast), drawn as a stroke of [width] along its [middle].
 */
private class ClassicHub(dial: ClassicDial, roles: MeterRoles) {
    private val scale = (if (roles.highContrast) HIGH_CONTRAST_HUB else 1f) * dial.u
    val outer = HUB_UNITS * scale
    val hole = HUB_HOLE_UNITS * scale
    val width = outer - hole
    val middle = (outer + hole) / 2f
}

/**
 * The scale (1.1's `ScaleMarks` on the dial, finer near the note since 2026-11-11): the marks [classicTickAt] puts
 * on it, from [TICK_IN_UNITS] inside the arc inwards; the tens [TEN_TICK_UNITS] long and [TEN_TICK_WIDTH_UNITS] wide
 * in ink, the fives [FIVE_TICK_UNITS] and [FIVE_TICK_WIDTH_UNITS], ink at [FIVE_TICK_INK], the single cents
 * [ONE_TICK_UNITS] and [ONE_TICK_WIDTH_UNITS], ink at [ONE_TICK_INK]; the numbers 10 to 50 without a sign in the app
 * language's digits, extra bold, [NUMBER_SIZE_UNITS] high, ink at [NUMBER_INK], centred [NUMBER_IN_UNITS] inside their
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
    private val colours = arrayOfNulls<Color>(TICK_COUNT)
    private val ink = roles.ink
    private val fiveInk = roles.ink.copy(alpha = roles.fade(FIVE_TICK_INK))
    private val oneInk = roles.ink.copy(alpha = roles.fade(ONE_TICK_INK))
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
            fontWeight = FontWeight.ExtraBold,
            fontFeatureSettings = LINING_FIGURES,
        )
        val measured = (1..5).map { measurer.measure(localDigits(it * 10, locale), style) }
        val longer = if (roles.highContrast) HIGH_CONTRAST_TICK_LENGTH else 1f
        val base = dial.r - TICK_IN_UNITS * u
        var t = 0
        var n = 0
        for (whole in -METER_RANGE_CENTS.toInt()..METER_RANGE_CENTS.toInt()) {
            val tick = classicTickAt(whole) ?: continue
            val cents = whole.toFloat()
            val (length, width, colour) = when (tick) {
                ClassicTick.TEN -> Triple(TEN_TICK_UNITS, TEN_TICK_WIDTH_UNITS, ink)
                ClassicTick.FIVE -> Triple(FIVE_TICK_UNITS, FIVE_TICK_WIDTH_UNITS, fiveInk)
                ClassicTick.ONE -> Triple(ONE_TICK_UNITS, ONE_TICK_WIDTH_UNITS, oneInk)
            }
            ticks[t * 5] = dial.x(base, cents)
            ticks[t * 5 + 1] = dial.y(base, cents)
            ticks[t * 5 + 2] = dial.x(base - length * longer * u, cents)
            ticks[t * 5 + 3] = dial.y(base - length * longer * u, cents)
            ticks[t * 5 + 4] = width * roles.lineScale * u
            colours[t] = colour
            t++
            if (tick == ClassicTick.TEN) {
                val text = measured[abs(whole) / 10 - 1]
                texts[n] = text
                textAt[n * 2] = dial.x(base - NUMBER_IN_UNITS * u, cents) - text.size.width / 2f
                textAt[n * 2 + 1] = dial.y(base - NUMBER_IN_UNITS * u, cents) - text.size.height / 2f
                n++
            }
        }
    }

    fun draw(scope: DrawScope) = with(scope) {
        for (t in 0 until TICK_COUNT) {
            drawLine(
                colours[t] ?: continue,
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
        /** Thirty-six ticks: every cent from 1 to 9 each way, then every 5 to 50, none at 0. */
        const val TICK_COUNT = 36

        /** Ten numbers: 50, 40, … 10 and 10, 20, … 50. */
        const val NUMBER_COUNT = 10
    }
}

/**
 * The needle (since 2026-11-11): a spike tapering from [NEEDLE_BASE_UNITS] wide at the pivot to [NEEDLE_TIP_UNITS]
 * at the dial's [ClassicDial.needleLength], with a short tail [NEEDLE_TAIL_UNITS] behind the pivot ending in a round
 * end [NEEDLE_TAIL_END_UNITS] wide; the hub's hole cut through it. Drawn pointing up; its layer turns it.
 * [drawShadow] draws its silhouette blurred for the shadow layer under it.
 */
private class ClassicNeedle(dial: ClassicDial, roles: MeterRoles) {
    private val body = Path().apply {
        val scale = (if (roles.highContrast) HIGH_CONTRAST_NEEDLE else 1f) * dial.u / 2f
        val tip = NEEDLE_TIP_UNITS * scale
        val base = NEEDLE_BASE_UNITS * scale
        val end = NEEDLE_TAIL_END_UNITS * scale
        val tail = dial.cy + NEEDLE_TAIL_UNITS * dial.u
        moveTo(dial.cx - tip, dial.cy - dial.needleLength)
        lineTo(dial.cx + tip, dial.cy - dial.needleLength)
        lineTo(dial.cx + base, dial.cy)
        lineTo(dial.cx + end, tail)
        arcTo(Rect(Offset(dial.cx, tail), end), 0f, 180f, false)
        lineTo(dial.cx - base, dial.cy)
        close()
        val hole = Path().apply { addOval(Rect(Offset(dial.cx, dial.cy), ClassicHub(dial, roles).hole)) }
        op(this, hole, PathOperation.Difference)
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

/** The arc's line, this much of the way from the line colour to the muted text's. */
private const val TRACK_WIDTH_UNITS = 2f
private const val TRACK_TOWARDS_MUTED = 0.3f

/** The in-tune zone on the arc: muted at this opacity off the note, the "in tune" colour in full as it is reached. */
private const val ZONE_WIDTH_UNITS = 5f
private const val ZONE_ALPHA = 0.55f

/** The note's mark reaches this far outside the arc (kept inside the dial's box) and this far inside it. */
internal const val MARK_OUT_UNITS = 5f
private const val MARK_IN_UNITS = 22f
private const val MARK_WIDTH_UNITS = 3f

/** The ticks start this far inside the arc. */
private const val TICK_IN_UNITS = 4f
private const val TEN_TICK_UNITS = 14f
private const val TEN_TICK_WIDTH_UNITS = 2.2f
private const val FIVE_TICK_UNITS = 9f
private const val FIVE_TICK_WIDTH_UNITS = 1.6f
private const val ONE_TICK_UNITS = 5f
private const val ONE_TICK_WIDTH_UNITS = 1f

/** How strong the fives, the single cents and the numbers are, as ink (1.1's minor ticks were 0.5 and its numbers 0.8). */
private const val FIVE_TICK_INK = 0.72f
private const val ONE_TICK_INK = 0.5f
private const val NUMBER_INK = 0.92f
private const val NUMBER_SIZE_UNITS = 14f

/** The numbers' middles sit this far inside their tick's base. */
private const val NUMBER_IN_UNITS = 29f

/** The in-tune glow: this many units round a point this far under the arc's top. */
private const val GLOW_UNITS = 80f
private const val GLOW_DOWN_UNITS = 14f

/**
 * The needle's widths at its tip, at the pivot and at its tail's round end, and the tail's length behind the pivot:
 * with its round end and the shadow it stays inside the 17 units under the pivot.
 */
private const val NEEDLE_TIP_UNITS = 1.2f
private const val NEEDLE_BASE_UNITS = 6f
private const val NEEDLE_TAIL_END_UNITS = 4.4f
internal const val NEEDLE_TAIL_UNITS = 11.5f

/**
 * The high-contrast look (Phase 86, the mock-up's): the needle this much wider, the hub this much larger, the ticks
 * this much longer (and [MeterRoles.lineScale] thicker), so they read at arm's length and through a blur.
 */
private const val HIGH_CONTRAST_NEEDLE = 2.3f
private const val HIGH_CONTRAST_HUB = 1.6f
private const val HIGH_CONTRAST_TICK_LENGTH = 1.3f

/** In tune, the needle goes this far towards the "in tune" colour. */
private const val NEEDLE_GREEN = 0.9f

/** The hub's ring round the pivot, and the hole in it. */
private const val HUB_UNITS = 7f
private const val HUB_HOLE_UNITS = 2.6f
private const val SHADOW_X_UNITS = 1.5f
private const val SHADOW_Y_UNITS = 2.5f

/** A needle's swing may carry it a little past the end of the scale, as a real one would. */
private const val NEEDLE_LIMIT_CENTS = 53f
