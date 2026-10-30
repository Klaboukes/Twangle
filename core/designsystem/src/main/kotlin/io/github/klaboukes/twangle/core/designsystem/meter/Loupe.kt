package io.github.klaboukes.twangle.core.designsystem.meter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.klaboukes.twangle.core.settings.InTuneEffect
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * 4. Loupe (Phase 60), as on the mock-up page docs/design/meter-precision-preview.html (drawLoupe),
 * in Eclipse's place: a straight scale (3u per cent, a tick every 5 cents as on the open face, no
 * numbers) with a slim marker, and over the scale's middle a round magnifying glass lying on the
 * screen. Through the glass the middle shows three times as large, easing off toward the rim, where
 * it meets the plain scale again, so nothing hides under it: from 18 cents flat to 18 sharp the
 * marker is seen through the glass, about 9u per cent near the note (twice Classic's needle tip),
 * with a tick per cent where they stand far enough apart, the centre mark, and the in-tune window
 * (5 cents each way, or 2 with the Precise setting) as a quiet band.
 */

/** 50 cents carries the marker this many units from the middle of the plain scale: 3u per cent. */
internal const val LOUPE_SPAN_UNITS = 150f

/** The plain scale's units per cent. */
internal const val LOUPE_UNITS_PER_CENT = LOUPE_SPAN_UNITS / METER_RANGE_CENTS

/** The marker may swing this far, a little past the end of the range (as Classic's needle). */
internal const val LOUPE_LIMIT_CENTS = 53f

/** The glass's radius, in units. */
internal const val LOUPE_RADIUS_UNITS = 54f

/** How strongly the glass magnifies (the k of [stretchedFraction]): 1 + k = 3 times at its centre. */
internal const val LOUPE_GLASS_STRETCH = 2f

/**
 * Where the marker sits on the plain scale for [cents], in units from the middle: [LOUPE_UNITS_PER_CENT]
 * per cent, [LOUPE_SPAN_UNITS] at ±50 cents. Its swing may carry it to ±[LOUPE_LIMIT_CENTS], no further.
 */
internal fun loupeOffset(cents: Float): Float =
    cents.coerceIn(-LOUPE_LIMIT_CENTS, LOUPE_LIMIT_CENTS) * LOUPE_UNITS_PER_CENT

/**
 * Where a point of the scale [dx] units from the glass's centre shows through it, in units from the
 * centre: x' = R · h(x / R), h the stretch's shape ([stretchedFraction]) with k = [LOUPE_GLASS_STRETCH].
 * Three times as far out near the centre, the same point at the rim (|[dx]| = R), so the view through
 * the glass meets the plain scale round it without a step and nothing under the glass is hidden. Only
 * meant for points under the glass (|[dx]| up to R).
 */
internal fun loupeMagnified(dx: Float): Float =
    LOUPE_RADIUS_UNITS * stretchedFraction(dx / LOUPE_RADIUS_UNITS, LOUPE_GLASS_STRETCH)

/**
 * How much the glass magnifies [dx] units from its centre: the slope of [loupeMagnified],
 * (1 + k) / (1 + k |x| / R)², 3 at the centre and 1/3 at the rim, where the glass gathers the
 * scale it spread out in the middle.
 */
internal fun loupeMagnification(dx: Float): Float {
    val d = 1f + LOUPE_GLASS_STRETCH * abs(dx) / LOUPE_RADIUS_UNITS
    return (1f + LOUPE_GLASS_STRETCH) / (d * d)
}

/** How much larger a mark is drawn through the glass [dx] units from its centre: its magnification, within 0.6..1.8. */
internal fun loupeGrowth(dx: Float): Float = loupeMagnification(dx).coerceIn(LOUPE_MIN_GROWTH, LOUPE_MAX_GROWTH)

internal const val LOUPE_MIN_GROWTH = 0.6f
internal const val LOUPE_MAX_GROWTH = 1.8f

/** Whether the marker for [cents] is under the glass (within 18 cents), and so seen through it. */
internal fun underTheGlass(cents: Float): Boolean = abs(loupeOffset(cents)) < LOUPE_RADIUS_UNITS

/**
 * The three kinds of tick seen through the glass, before it grows them: [lengthUnits] long, [widthUnits]
 * wide, in ink at [ink].
 */
internal enum class LoupeTick(val lengthUnits: Float, val widthUnits: Float, val ink: Float) {
    /** 10 cents. */
    TEN(17f, 2f, 1f),

    /** 5 and 15 cents. */
    FIVE(13f, 2f, 0.75f),

    /** Every other cent near the note, where there is room. */
    CENT(7f, 1.2f, 0.45f),
}

/**
 * A tick seen through the glass: at [cents] on the scale, seen [at] units from the middle, of [kind],
 * drawn [growth] times its size ([loupeGrowth]): its foot [baseUnits] over the track, [lengthUnits]
 * long, [widthUnits] wide (widened at most [GLASS_TICK_MAX_WIDEN] times).
 */
internal class LoupeGlassTick(val cents: Float, val at: Float, val kind: LoupeTick, val growth: Float) {
    val baseUnits: Float get() = GLASS_TICK_BASE_UNITS * growth
    val lengthUnits: Float get() = kind.lengthUnits * growth
    val widthUnits: Float get() = kind.widthUnits * min(growth, GLASS_TICK_MAX_WIDEN)
}

/** A tick through the glass stands this many units (times its growth) over the track … */
internal const val GLASS_TICK_BASE_UNITS = 5f

/** … and is widened at most this many times. */
internal const val GLASS_TICK_MAX_WIDEN = 1.4f

/** A tick per cent is drawn only where the glass sets the cents at least this many units apart. */
internal const val MIN_CENT_TICK_GAP_UNITS = 4f

/**
 * The ticks seen through the glass, flat side first: every cent under it (up to 17 cents each way),
 * 10 cents a [LoupeTick.TEN], 5 and 15 a [LoupeTick.FIVE], and the other cents a [LoupeTick.CENT]
 * only where the glass sets them at least [MIN_CENT_TICK_GAP_UNITS] apart (1 to 4 cents). Made once
 * per size, never per frame.
 */
internal fun loupeGlassTicks(): List<LoupeGlassTick> = buildList {
    for (side in intArrayOf(-1, 1)) {
        var cents = 1
        while (underTheGlass(cents.toFloat())) {
            val dx = loupeOffset(side * cents.toFloat())
            val kind = when {
                cents % 10 == 0 -> LoupeTick.TEN
                cents % 5 == 0 -> LoupeTick.FIVE
                else -> LoupeTick.CENT
            }
            val roomy = LOUPE_UNITS_PER_CENT * loupeMagnification(dx) >= MIN_CENT_TICK_GAP_UNITS
            if (kind != LoupeTick.CENT || roomy) add(LoupeGlassTick(side * cents.toFloat(), loupeMagnified(dx), kind, loupeGrowth(dx)))
            cents++
        }
    }
}

/**
 * The in-tune window's half width seen through the glass, in units, for a window of [window] cents
 * each way ([MeterReading.inTuneWindow]): about 29u for 5 cents, 15u for 2 (Precise). Its edges
 * fall on the ticks for those cents.
 */
internal fun loupeWindowHalfWidth(window: Float): Float = loupeMagnified(loupeOffset(window))

/**
 * Loupe's geometry in a box [width] × [height]: the straight scale at [cy] (half way down) from
 * [LOUPE_SPAN_UNITS] left of the middle to as far right, and the glass ([LOUPE_RADIUS_UNITS]) round
 * its middle, from [glassTop] to [glassBottom].
 */
internal class LoupeGeometry(width: Float, height: Float) {
    val u = meterUnit(width)
    val cx = width / 2f
    val cy = height * SCALE_DOWN
    val span = LOUPE_SPAN_UNITS * u
    val radius = LOUPE_RADIUS_UNITS * u
    val glassTop = cy - radius
    val glassBottom = cy + radius

    /** How far the marker on the plain scale is from the middle for [cents], in px ([loupeOffset]). */
    fun offset(cents: Float): Float = loupeOffset(cents) * u

    /** How far the marker seen through the glass is from the middle for [cents], in px. */
    fun magnifiedOffset(cents: Float): Float = loupeMagnified(loupeOffset(cents)) * u

    /** The glass's outline. */
    fun glass(): Path = Path().apply { addOval(Rect(Offset(cx, cy), radius)) }

    companion object {
        /** The scale and the glass's centre lie this share of the way down the box. */
        const val SCALE_DOWN = 0.5f

        /** The scale's line is this many units wide, and through the glass [GLASS_TRACK_WIDEN] times as wide. */
        const val TRACK_WIDTH_UNITS = 1.5f
        const val GLASS_TRACK_WIDEN = 1.4f

        /** The plain scale's ticks start this many units over its line (as the open face's 4u inside its arc). */
        const val TICK_BASE_UNITS = 4f

        /** The marker is this many units wide, round-ended, and reaches this far over and under the line … */
        const val MARKER_WIDTH_UNITS = 3f
        const val MARKER_REACH_UNITS = 22f

        /** … and seen through the glass this far, and up to [GLASS_MARKER_MAX_WIDEN] times as wide. */
        const val GLASS_MARKER_REACH_UNITS = 34f
        const val GLASS_MARKER_MAX_WIDEN = 1.5f

        /** The centre mark through the glass: from this many units over the line to this many, this wide. */
        const val CENTRE_FROM_UNITS = 8f
        const val CENTRE_TO_UNITS = 36f
        const val CENTRE_WIDTH_UNITS = 3.5f
    }
}

/**
 * Loupe: the calm field, the plain scale and the ♭ and ♯ under its ends (static); the in-tune glow
 * round the scale's middle; the marker on the plain scale, drawn once and moved by its layer; the
 * glass's contact shadow and face over them (static, the art language's glass lying on the screen);
 * then, clipped to the glass by one layer's outline: the in-tune window and the centre mark (redrawn
 * only while they fade to "in tune", or from rest), the magnified track and ticks (static), the glow
 * seen through the glass, and the marker seen through it, drawn once and moved and widened by its
 * layer; over it all the glass's sheen and rim (static).
 */
@Composable
internal fun BoxScope.LoupeMeter(reading: MeterReading, effect: InTuneEffect) {
    val roles = meterRoles(effect)
    val measurer = rememberTextMeasurer()
    StaticArt {
        val loupe = LoupeGeometry(size.width, size.height)
        val u = loupe.u
        val field = meterField(size, roles)
        val scale = LoupeScaleArt(loupe, roles)
        val signY = loupe.cy + SIGN_BELOW_UNITS * u
        val signs = SignsArt(
            measurer, this, u, roles,
            flatX = loupe.cx - loupe.span + SIGN_IN_UNITS * u,
            sharpX = loupe.cx + loupe.span - SIGN_IN_UNITS * u,
            y = signY,
            maxHeight = 2f * (size.height - u - signY),
        )
        val draw: DrawScope.() -> Unit = {
            field.draw(this)
            scale.draw(this)
            signs.draw(this)
        }
        draw
    }
    TuneGlow(roles, reading, LOUPE_GLOW_UNITS, centre = { size ->
        val loupe = LoupeGeometry(size.width, size.height)
        Offset(loupe.cx, loupe.cy)
    })
    // The marker on the plain scale, drawn at rest in the middle and carried along by its layer. Near
    // the note it slides under the glass, whose face hides it there.
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {
            translationX = loupeOffset(reading.cents) * meterUnit(size.width)
            reading.applyMarkerLook(this)
        }.drawWithCache {
            val loupe = LoupeGeometry(size.width, size.height)
            val marker = LoupeMarkerArt(loupe, roles, LoupeGeometry.MARKER_REACH_UNITS)
            onDrawBehind { marker.draw(this, roles.marker(reading.tuned)) }
        },
    )
    StaticArt {
        val loupe = LoupeGeometry(size.width, size.height)
        val face = ContactShadowArt(loupe.glass(), loupe.u, roles, loupeFace(roles))
        val draw: DrawScope.() -> Unit = { face.draw(this) }
        draw
    }
    // What is seen through the glass, clipped to it by this layer's outline (no clip is drawn per frame).
    Box(
        modifier = Modifier.matchParentSize().graphicsLayer {
            shape = LoupeGlassShape
            clip = true
        },
    ) {
        // The in-tune window and the centre mark. Reading the window here makes them again when the setting changes.
        Spacer(
            modifier = Modifier.matchParentSize().graphicsLayer {}.drawWithCache {
                val window = LoupeWindowArt(LoupeGeometry(size.width, size.height), roles, reading.inTuneWindow)
                onDrawBehind { window.draw(this, reading.tuned, reading.presence) }
            },
        )
        StaticArt {
            val ticks = LoupeGlassScaleArt(LoupeGeometry(size.width, size.height), roles)
            val draw: DrawScope.() -> Unit = { ticks.draw(this) }
            draw
        }
        // The glow seen through the glass (whose face hides the one round it), under the marker.
        Spacer(
            modifier = Modifier.matchParentSize().graphicsLayer {
                alpha = reading.glow.coerceIn(0f, 1f)
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }.drawWithCache {
                val loupe = LoupeGeometry(size.width, size.height)
                val glow = GlowArt(GLASS_GLOW_REACH * loupe.radius * roles.tuneGlowSize, roles.inTune)
                val peak = roles.tuneGlowAlpha * GLASS_GLOW_STRENGTH
                onDrawBehind { glow.draw(this, loupe.cx, loupe.cy, peak) }
            },
        )
        // The marker seen through the glass: drawn once in the middle, its layer carries it to where the
        // glass shows it and widens it as the glass magnifies there; gone once the marker leaves the glass.
        Spacer(
            modifier = Modifier.matchParentSize().graphicsLayer {
                val dx = loupeOffset(reading.cents)
                translationX = loupeMagnified(dx) * meterUnit(size.width)
                scaleX = min(loupeGrowth(dx), LoupeGeometry.GLASS_MARKER_MAX_WIDEN)
                reading.applyMarkerLook(this)
                if (abs(dx) >= LOUPE_RADIUS_UNITS) alpha = 0f
            }.drawWithCache {
                val marker = LoupeMarkerArt(LoupeGeometry(size.width, size.height), roles, LoupeGeometry.GLASS_MARKER_REACH_UNITS)
                onDrawBehind { marker.draw(this, roles.marker(reading.tuned)) }
            },
        )
    }
    StaticArt {
        val loupe = LoupeGeometry(size.width, size.height)
        val glass = GlassArt(loupe.glass(), size, loupe.glassTop, loupe.glassBottom, loupe.u, roles, loupeFace(roles))
        val draw: DrawScope.() -> Unit = { glass.drawOver(this) }
        draw
    }
}

/**
 * The glass's face: the stage 70 % of the way to the surface colour, a little lighter than the
 * screen round it in light and dark, as a lens lying on it (not sunk into it, so without the
 * recessed glass's inner shadow).
 */
internal fun loupeFace(roles: MeterRoles): Color = mix(roles.background, roles.plate, LOUPE_FACE_TO_SURFACE)

private const val LOUPE_FACE_TO_SURFACE = 0.7f

/** The glass's outline as a layer's shape: a circle round the scale's middle (a round rectangle, so every Android clips to it). */
private object LoupeGlassShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val loupe = LoupeGeometry(size.width, size.height)
        return Outline.Rounded(
            RoundRect(loupe.cx - loupe.radius, loupe.glassTop, loupe.cx + loupe.radius, loupe.glassBottom, CornerRadius(loupe.radius)),
        )
    }
}

/**
 * The plain scale: a [LoupeGeometry.TRACK_WIDTH_UNITS] line in the track colour across it and a tick
 * every 5 cents over it, none at 0, as the open face's (major every 10, [MAJOR_TICK_UNITS] ×
 * [MAJOR_TICK_WIDTH] in ink; minor [MINOR_TICK_UNITS] × [MINOR_TICK_WIDTH] in ink at 50 %), without
 * numbers, from [LoupeGeometry.TICK_BASE_UNITS] over the line; in the style's caps, and drawn by hand
 * in Ancient ([MeterFinish.jitter]).
 */
private class LoupeScaleArt(loupe: LoupeGeometry, roles: MeterRoles) {
    private val u = loupe.u
    private val cap = roles.finish.caps
    private val trackColor = roles.track
    private val trackStart = Offset(loupe.cx - loupe.span, loupe.cy)
    private val trackEnd = Offset(loupe.cx + loupe.span, loupe.cy)

    /** Each tick's ends and width: x1, y1, x2, y2, width. */
    private val ticks = FloatArray(TICKS * 5)
    private val major = BooleanArray(TICKS)
    private val majorColor = roles.ink
    private val minorColor = roles.ink.copy(alpha = MINOR_TICK_INK)

    init {
        val base = loupe.cy - LoupeGeometry.TICK_BASE_UNITS * u
        var t = 0
        var cents = -METER_RANGE_CENTS
        while (cents <= METER_RANGE_CENTS) {
            if (cents != 0f) {
                val isMajor = cents.toInt() % 10 == 0
                val jitter = if (roles.finish.jitter) handJitter(cents) else 0f
                val length = (if (isMajor) MAJOR_TICK_UNITS else MINOR_TICK_UNITS) * u * (1f + JITTER_LENGTH * jitter)
                val turn = JITTER_TURN * jitter
                val x = loupe.cx + loupe.offset(cents)
                ticks[t * 5] = x
                ticks[t * 5 + 1] = base
                ticks[t * 5 + 2] = x + sin(turn) * length
                ticks[t * 5 + 3] = base - cos(turn) * length
                ticks[t * 5 + 4] = (if (isMajor) MAJOR_TICK_WIDTH else MINOR_TICK_WIDTH) * u * (1f + JITTER_WIDTH * jitter)
                major[t] = isMajor
                t++
            }
            cents += 5f
        }
    }

    fun draw(scope: DrawScope) = with(scope) {
        drawLine(trackColor, trackStart, trackEnd, LoupeGeometry.TRACK_WIDTH_UNITS * u, cap)
        for (t in 0 until TICKS) {
            drawLine(
                if (major[t]) majorColor else minorColor,
                Offset(ticks[t * 5], ticks[t * 5 + 1]),
                Offset(ticks[t * 5 + 2], ticks[t * 5 + 3]),
                ticks[t * 5 + 4],
                cap,
            )
        }
    }

    private companion object {
        /** Every 5 cents from −50 to 50, none at 0. */
        const val TICKS = 20
    }
}

/**
 * The scale seen through the glass: its line, [LoupeGeometry.GLASS_TRACK_WIDEN] times as wide, and
 * the [loupeGlassTicks] at their magnified places, each grown as the glass grows it there; in the
 * style's caps, and drawn by hand in Ancient.
 */
private class LoupeGlassScaleArt(loupe: LoupeGeometry, roles: MeterRoles) {
    private val u = loupe.u
    private val cap = roles.finish.caps
    private val trackColor = roles.track
    private val trackStart = Offset(loupe.cx - loupe.radius, loupe.cy)
    private val trackEnd = Offset(loupe.cx + loupe.radius, loupe.cy)
    private val trackWidth = LoupeGeometry.TRACK_WIDTH_UNITS * LoupeGeometry.GLASS_TRACK_WIDEN * u
    private val count: Int

    /** Each tick's ends and width: x1, y1, x2, y2, width. */
    private val ticks: FloatArray
    private val colors: Array<Color?>

    init {
        val glassTicks = loupeGlassTicks()
        count = glassTicks.size
        ticks = FloatArray(count * 5)
        colors = arrayOfNulls(count)
        for ((t, tick) in glassTicks.withIndex()) {
            val jitter = if (roles.finish.jitter) handJitter(tick.cents) else 0f
            val length = tick.lengthUnits * u * (1f + JITTER_LENGTH * jitter)
            val turn = JITTER_TURN * jitter
            val x = loupe.cx + tick.at * u
            val base = loupe.cy - tick.baseUnits * u
            ticks[t * 5] = x
            ticks[t * 5 + 1] = base
            ticks[t * 5 + 2] = x + sin(turn) * length
            ticks[t * 5 + 3] = base - cos(turn) * length
            ticks[t * 5 + 4] = tick.widthUnits * u * (1f + JITTER_WIDTH * jitter)
            colors[t] = roles.ink.copy(alpha = tick.kind.ink)
        }
    }

    fun draw(scope: DrawScope) = with(scope) {
        drawLine(trackColor, trackStart, trackEnd, trackWidth, cap)
        for (t in 0 until count) {
            drawLine(colors[t]!!, Offset(ticks[t * 5], ticks[t * 5 + 1]), Offset(ticks[t * 5 + 2], ticks[t * 5 + 3]), ticks[t * 5 + 4], cap)
        }
    }
}

/**
 * Under the glass's ticks: the in-tune window, [window] cents each way ([loupeWindowHalfWidth]), as a
 * quiet band in the track colour across the glass (at [loupeBandAlpha] by the line, fading to nothing toward
 * the glass's top and bottom), its edges 1u hairlines in ink at 18 %; and the centre mark, a
 * [LoupeGeometry.CENTRE_WIDTH_UNITS] rounded bar over the line in the centre colour (muted at rest,
 * [MeterRoles.centre]). As the note is reached the band takes on 40 % of "in tune" (a second band in
 * that colour, made once, laid over it at the in-tune share), its edges blend to "in tune" and the
 * centre mark turns "in tune"; at rest none of it is green.
 */
private class LoupeWindowArt(loupe: LoupeGeometry, private val roles: MeterRoles, window: Float) {
    private val u = loupe.u
    private val half = loupeWindowHalfWidth(window) * u
    private val bandTopLeft = Offset(loupe.cx - half, loupe.glassTop)
    private val bandSize = Size(2f * half, loupe.glassBottom - loupe.glassTop)
    private val peak = loupeBandAlpha(roles.dark)
    private val quiet = band(roles.track, peak, loupe)
    private val tunedBand = band(roles.inTune, peak * BAND_TO_IN_TUNE, loupe)
    private val leftTop = Offset(loupe.cx - half, loupe.glassTop)
    private val leftBottom = Offset(loupe.cx - half, loupe.glassBottom)
    private val rightTop = Offset(loupe.cx + half, loupe.glassTop)
    private val rightBottom = Offset(loupe.cx + half, loupe.glassBottom)
    private val centreFrom = Offset(loupe.cx, loupe.cy - LoupeGeometry.CENTRE_FROM_UNITS * u)
    private val centreTo = Offset(loupe.cx, loupe.cy - LoupeGeometry.CENTRE_TO_UNITS * u)

    private fun band(colour: Color, alpha: Float, loupe: LoupeGeometry) = Brush.verticalGradient(
        0f to colour.copy(alpha = 0f),
        0.5f to colour.copy(alpha = alpha),
        1f to colour.copy(alpha = 0f),
        startY = loupe.glassTop,
        endY = loupe.glassBottom,
    )

    fun draw(scope: DrawScope, tuned: Float, presence: Float) = with(scope) {
        val t = tuned.coerceIn(0f, 1f)
        drawRect(quiet, bandTopLeft, bandSize)
        if (t > 0f) drawRect(tunedBand, bandTopLeft, bandSize, alpha = t)
        val edge = mix(roles.ink, roles.inTune, t).copy(alpha = EDGE_ALPHA)
        drawLine(edge, leftTop, leftBottom, u, StrokeCap.Butt)
        drawLine(edge, rightTop, rightBottom, u, StrokeCap.Butt)
        drawLine(roles.centre(t, presence), centreFrom, centreTo, LoupeGeometry.CENTRE_WIDTH_UNITS * u, StrokeCap.Round)
    }

    private companion object {
        /** The window's edges are ink (or "in tune") at this opacity. */
        const val EDGE_ALPHA = 0.18f

        /** In tune the band takes on this share of "in tune". */
        const val BAND_TO_IN_TUNE = 0.4f
    }
}

/** The in-tune window's band at its strongest, by the scale's line: 38 % in dark, 45 % in light. */
internal fun loupeBandAlpha(dark: Boolean): Float = if (dark) 0.38f else 0.45f

/**
 * A marker at rest in the middle of the scale: a [LoupeGeometry.MARKER_WIDTH_UNITS] round-ended
 * bar reaching [reachUnits] over and under the line, with the marker's soft shadow; drawn once, its
 * layer moves it.
 */
private class LoupeMarkerArt(loupe: LoupeGeometry, roles: MeterRoles, reachUnits: Float) {
    private val u = loupe.u
    private val width = LoupeGeometry.MARKER_WIDTH_UNITS * u
    private val top = Offset(loupe.cx, loupe.cy - reachUnits * u)
    private val bottom = Offset(loupe.cx, loupe.cy + reachUnits * u)
    private val shadow = Path().apply {
        val half = width / 2f
        val dx = MARKER_SHADOW_X * u
        val dy = MARKER_SHADOW_Y * u
        addRoundRect(RoundRect(top.x - half + dx, top.y - half + dy, top.x + half + dx, bottom.y + half + dy, CornerRadius(half)))
    }.asAndroidPath()
    private val shadowPaint = markerShadowPaint(u, roles)

    fun draw(scope: DrawScope, colour: Color) = with(scope) {
        drawIntoCanvas { it.nativeCanvas.drawPath(shadow, shadowPaint) }
        drawLine(colour, top, bottom, width, StrokeCap.Round)
    }
}

/** The ♭ and ♯ sit this many units in from the scale's ends … */
private const val SIGN_IN_UNITS = 4f

/** … and their middles this many units under it, below the marker. */
private const val SIGN_BELOW_UNITS = 26f

/** Loupe's in-tune glow reaches this many units from the middle of the scale. */
private const val LOUPE_GLOW_UNITS = 120f

/** Through the glass the glow reaches this many times its radius … */
private const val GLASS_GLOW_REACH = 1.1f

/** … at this share of the usual glow's strength. */
private const val GLASS_GLOW_STRENGTH = 0.8f
