package io.github.klaboukes.twangle.core.designsystem.meter

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.core.graphics.withTranslation
import io.github.klaboukes.twangle.core.settings.InTuneEffect
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * 3. Pointers (Phase 60), as on the mock-up page docs/design/meter-precision-preview.html
 * (drawPointers), in Spirit level's place: a fixed pointer above the middle of a straight track
 * marks the note, and the note's own pointer slides along under it, on the ♭ or ♯ side, with a band
 * of light stretching back from it to the middle. In tune the two tips meet. The track is stretched
 * in the middle (meterStretch), so near the note a cent moves the pointer about 7.5u, and a few
 * fixed ticks without numbers (every cent up to 4, then 5, 10 and every 10 to 50) give the eye
 * something to compare it against; the tick at the in-tune window's edge (5 cents, or 2 with the
 * Precise setting) is one of the tall ones.
 */

/**
 * Where the note's pointer sits for [cents], in px from the middle of a track whose unit is [u]:
 * the stretched scale ([meterStretch]), [POINTER_SPAN_UNITS] at ±50 cents (about 7.5u per cent near
 * the note, 3u at the ends). Its swing may carry it to ±[POINTER_LIMIT_CENTS], no further.
 */
internal fun pointerOffset(cents: Float, u: Float): Float =
    meterStretch(cents.coerceIn(-POINTER_LIMIT_CENTS, POINTER_LIMIT_CENTS)) / METER_RANGE_CENTS * POINTER_SPAN_UNITS * u

/** The pointer may swing this far, a little past the end of the range (as Classic's needle). */
internal const val POINTER_LIMIT_CENTS = 53f

/** 50 cents carries the note's pointer this many units from the middle. */
internal const val POINTER_SPAN_UNITS = 150f

/**
 * Pointers' geometry in a box [width] × [height]: the straight track at [trackY] (52 % of the way
 * down) from [POINTER_SPAN_UNITS] left of the middle to as far right; the fixed pointer above it,
 * its tip [TIP_GAP_UNITS] over the track and pointing down at it, and the note's pointer below it,
 * its tip as far under the track and pointing up, both [POINTER_WIDTH_UNITS] wide and
 * [POINTER_HEIGHT_UNITS] tall; the band of light [BAND_HEIGHT_UNITS] high round the track.
 */
internal class PointersGeometry(width: Float, height: Float) {
    val u = meterUnit(width)
    val cx = width / 2f
    val trackY = height * TRACK_DOWN
    val span = POINTER_SPAN_UNITS * u

    /** The fixed pointer's tip, over the track. */
    val fixedTipY = trackY - TIP_GAP_UNITS * u

    /** The note's pointer's tip, under the track. */
    val noteTipY = trackY + TIP_GAP_UNITS * u

    /** The band's top and bottom, and the radius of its round ends. */
    val bandTop = trackY - BAND_HEIGHT_UNITS / 2f * u
    val bandBottom = trackY + BAND_HEIGHT_UNITS / 2f * u
    val bandRadius = BAND_HEIGHT_UNITS / 2f * u

    /** How far the note's pointer is from the middle for [cents] ([pointerOffset]). */
    fun offset(cents: Float): Float = pointerOffset(cents, u)

    /** The fixed pointer over the middle, pointing down. */
    fun fixedPointer(): Path = pointer(cx, fixedTipY, -1f)

    /** The note's pointer at rest under the middle, pointing up, moved by ([dx], [dy]) (its shadow's place). */
    fun notePointer(dx: Float = 0f, dy: Float = 0f): Path = pointer(cx + dx, noteTipY + dy, 1f)

    /** A triangle with its tip at ([x], [tipY]) and its base [POINTER_HEIGHT_UNITS] away, below ([dir] 1) or above (−1). */
    private fun pointer(x: Float, tipY: Float, dir: Float): Path = Path().apply {
        val half = POINTER_WIDTH_UNITS / 2f * u
        val base = tipY + dir * POINTER_HEIGHT_UNITS * u
        moveTo(x, tipY)
        lineTo(x - half, base)
        lineTo(x + half, base)
        close()
    }

    companion object {
        /** The track lies this share of the way down the box. */
        const val TRACK_DOWN = 0.52f

        /** Each pointer's tip is this many units from the track: in tune they are twice that apart, tip to tip. */
        const val TIP_GAP_UNITS = 7f

        const val POINTER_WIDTH_UNITS = 18f
        const val POINTER_HEIGHT_UNITS = 24f

        /** The band of light is this many units high, with round ends. */
        const val BAND_HEIGHT_UNITS = 8f

        /** The track's line is this many units wide. */
        const val TRACK_WIDTH_UNITS = 1.5f

        /** The ticks start this many units above the track and run up from there. */
        const val TICK_BASE_UNITS = 8f
    }
}

/** The cents Pointers' ticks mark on each side of the middle, without numbers. */
internal val POINTER_TICK_CENTS = floatArrayOf(1f, 2f, 3f, 4f, 5f, 10f, 20f, 30f, 40f, 50f)

/** The three kinds of Pointers' ticks: [lengthUnits] long, [widthUnits] wide, in ink at [ink]. */
internal enum class PointerTick(val lengthUnits: Float, val widthUnits: Float, val ink: Float) {
    /** Every cent up to 4: short and light, for the last few cents. */
    FINE(6f, 1.3f, 0.4f),

    /** 5 and 10 cents, and the in-tune window's edge: tall and strong. */
    MAIN(12f, 2f, 0.8f),

    /** 20 to 50 cents: where only the direction matters. */
    COARSE(9f, 1.3f, MINOR_TICK_INK),
}

/**
 * The kind of Pointers' tick at [cents] (one of [POINTER_TICK_CENTS]) with the in-tune window
 * [window] cents each way ([MeterReading.inTuneWindow]): 5 and 10 cents and the window's edge are
 * tall ones (so with Precise the 2-cent tick is too), the rest up to 4 cents short and light, from
 * 20 cents on of middle height.
 */
internal fun pointerTick(cents: Float, window: Float): PointerTick = when {
    cents == 5f || cents == 10f || cents == window -> PointerTick.MAIN
    cents < 5f -> PointerTick.FINE
    else -> PointerTick.COARSE
}

/**
 * The band of light's opacity [along] (0..1) of the way from the middle to the note's pointer: the
 * accent at [BAND_START_ALPHA] by the middle, growing to [bandEndAlpha] at the pointer, however long
 * the band is.
 */
internal fun bandAlpha(along: Float, dark: Boolean): Float =
    BAND_START_ALPHA + (bandEndAlpha(dark) - BAND_START_ALPHA) * along.coerceIn(0f, 1f)

/** The band's opacity at the note's pointer: 80 % in dark, 65 % in light. */
internal fun bandEndAlpha(dark: Boolean): Float = if (dark) 0.8f else 0.65f

internal const val BAND_START_ALPHA = 0.08f

/** A pointer closer to the middle than this many units shows no band. */
internal const val BAND_MIN_UNITS = 0.5f

/**
 * Pointers: the calm field (static); the in-tune glow round the middle of the track; the band of
 * light from the middle to the note's pointer, redrawn from the reading in its own layer; over it
 * the track, the ticks and the ♭ and ♯ under the track's ends (static, made again only when the
 * in-tune window changes); the fixed pointer in the centre colour (its own layer, so its fade to
 * "in tune" redraws nothing else); and the note's pointer in ink with the marker's soft shadow,
 * drawn once under the middle and only moved by its layer.
 */
@Composable
internal fun BoxScope.PointersMeter(reading: MeterReading, effect: InTuneEffect) {
    val roles = meterRoles(effect)
    val measurer = rememberTextMeasurer()
    StaticArt {
        val field = meterField(size, roles)
        val draw: DrawScope.() -> Unit = { field.draw(this) }
        draw
    }
    TuneGlow(roles, reading, POINTERS_GLOW_UNITS, centre = { size ->
        val pointers = PointersGeometry(size.width, size.height)
        Offset(pointers.cx, pointers.trackY)
    })
    // The band of light: it changes length (and so its brush) with the reading, so it is redrawn,
    // from a gradient made once along one unit and stretched to the pointer.
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {
            reading.applyMarkerLook(this)
        }.drawWithCache {
            val band = BandArt(PointersGeometry(size.width, size.height), roles)
            onDrawBehind { band.draw(this, reading.cents) }
        },
    )
    // The track, the ticks and the signs. Reading the window here makes them again when the setting changes.
    StaticArt {
        val pointers = PointersGeometry(size.width, size.height)
        val u = pointers.u
        val scale = PointersScaleArt(pointers, roles, reading.inTuneWindow)
        val signY = pointers.trackY + SIGN_BELOW_UNITS * u
        val signs = SignsArt(
            measurer, this, u, roles,
            flatX = pointers.cx - pointers.span + SIGN_IN_UNITS * u,
            sharpX = pointers.cx + pointers.span - SIGN_IN_UNITS * u,
            y = signY,
            maxHeight = 2f * (size.height - u - signY),
        )
        val draw: DrawScope.() -> Unit = {
            scale.draw(this)
            signs.draw(this)
        }
        draw
    }
    // The fixed pointer over the middle: muted at rest, the accent with a note, "in tune" on it.
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {}.drawWithCache {
            val pointer = PointersGeometry(size.width, size.height).fixedPointer()
            onDrawBehind { drawPath(pointer, roles.centre(reading.tuned, reading.presence)) }
        },
    )
    // The note's pointer and its shadow, drawn at rest and carried along the track by their layer.
    Spacer(
        modifier = Modifier.matchParentSize().graphicsLayer {
            translationX = pointerOffset(reading.cents, meterUnit(size.width))
            reading.applyMarkerLook(this)
        }.drawWithCache {
            val pointers = PointersGeometry(size.width, size.height)
            val u = pointers.u
            val pointer = pointers.notePointer()
            val shadow = pointers.notePointer(MARKER_SHADOW_X * u, MARKER_SHADOW_Y * u).asAndroidPath()
            val shadowPaint = markerShadowPaint(u, roles)
            onDrawBehind {
                drawIntoCanvas { it.nativeCanvas.drawPath(shadow, shadowPaint) }
                drawPath(pointer, roles.marker(reading.tuned))
            }
        },
    )
}

/**
 * The band of light along the track, from the middle to the note's pointer, [PointersGeometry.bandRadius]
 * round at both ends, in the accent fading from [BAND_START_ALPHA] by the middle to [bandEndAlpha]
 * at the pointer ([bandAlpha]). Its gradient is made once along one unit, from 0 to 1; drawing
 * stretches the canvas from the middle to the pointer (mirrored on the flat side) and narrows the
 * rounded ends' radius by as much, so the ends stay round and nothing is allocated.
 */
private class BandArt(private val pointers: PointersGeometry, roles: MeterRoles) {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        shader = android.graphics.LinearGradient(
            0f, 0f, 1f, 0f,
            roles.accent.copy(alpha = roles.accent.alpha * bandAlpha(0f, roles.dark)).toArgb(),
            roles.accent.copy(alpha = roles.accent.alpha * bandAlpha(1f, roles.dark)).toArgb(),
            android.graphics.Shader.TileMode.CLAMP,
        )
    }
    private val minimum = BAND_MIN_UNITS * pointers.u

    fun draw(scope: DrawScope, cents: Float) = with(scope) {
        val length = pointers.offset(cents)
        if (abs(length) <= minimum) return@with
        drawIntoCanvas {
            it.nativeCanvas.withTranslation(pointers.cx, 0f) {
                scale(length, 1f)
                drawRoundRect(0f, pointers.bandTop, 1f, pointers.bandBottom, pointers.bandRadius / abs(length), pointers.bandRadius, paint)
            }
        }
    }
}

/**
 * The track (a [PointersGeometry.TRACK_WIDTH_UNITS] line in the track colour across the scale) and
 * the ticks above it, each [POINTER_TICK_CENTS] on both sides at its stretched place, of the kind
 * [pointerTick] gives with the in-tune window [window]; in the style's tick caps, and drawn by hand
 * in Ancient ([MeterFinish.jitter]), as the open face's scale.
 */
private class PointersScaleArt(pointers: PointersGeometry, roles: MeterRoles, window: Float) {
    private val u = pointers.u
    private val cap = roles.finish.caps
    private val trackColor = roles.track
    private val trackStart = Offset(pointers.cx - pointers.span, pointers.trackY)
    private val trackEnd = Offset(pointers.cx + pointers.span, pointers.trackY)

    /** Each tick's ends and width: x1, y1, x2, y2, width. */
    private val ticks = FloatArray(TICKS * 5)
    private val colors = arrayOfNulls<Color>(TICKS)

    init {
        val base = pointers.trackY - PointersGeometry.TICK_BASE_UNITS * u
        var t = 0
        for (side in floatArrayOf(-1f, 1f)) {
            for (cents in POINTER_TICK_CENTS) {
                val kind = pointerTick(cents, window)
                val x = pointers.cx + pointers.offset(side * cents)
                val jitter = if (roles.finish.jitter) handJitter(side * cents) else 0f
                val length = kind.lengthUnits * u * (1f + JITTER_LENGTH * jitter)
                val turn = JITTER_TURN * jitter
                ticks[t * 5] = x
                ticks[t * 5 + 1] = base
                ticks[t * 5 + 2] = x + sin(turn) * length
                ticks[t * 5 + 3] = base - cos(turn) * length
                ticks[t * 5 + 4] = kind.widthUnits * u * (1f + JITTER_WIDTH * jitter)
                colors[t] = roles.ink.copy(alpha = kind.ink)
                t++
            }
        }
    }

    fun draw(scope: DrawScope) = with(scope) {
        drawLine(trackColor, trackStart, trackEnd, PointersGeometry.TRACK_WIDTH_UNITS * u, cap)
        for (t in 0 until TICKS) {
            drawLine(colors[t]!!, Offset(ticks[t * 5], ticks[t * 5 + 1]), Offset(ticks[t * 5 + 2], ticks[t * 5 + 3]), ticks[t * 5 + 4], cap)
        }
    }

    private companion object {
        val TICKS = POINTER_TICK_CENTS.size * 2
    }
}

/** The ♭ and ♯ sit this many units in from the track's ends … */
private const val SIGN_IN_UNITS = 4f

/** … and their middles this many units under it, below the note's pointer's base. */
private const val SIGN_BELOW_UNITS = 26f

/** Pointers' in-tune glow reaches this many units from the middle of the track. */
private const val POINTERS_GLOW_UNITS = 120f
