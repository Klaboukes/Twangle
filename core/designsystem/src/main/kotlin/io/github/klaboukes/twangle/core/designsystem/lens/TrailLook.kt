package io.github.klaboukes.twangle.core.designsystem.lens

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.InfiniteAnimationPolicy
import io.github.klaboukes.twangle.core.designsystem.meter.METER_RANGE_CENTS
import io.github.klaboukes.twangle.core.designsystem.meter.meterCents
import io.github.klaboukes.twangle.core.designsystem.meter.meterStretch
import io.github.klaboukes.twangle.core.designsystem.meter.mix
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.trail.PitchHistory
import kotlinx.coroutines.delay
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.exp

/**
 * What the trail reads while drawing: a [LensReading] and the last seconds of readings it draws ([history], written
 * on the DSP thread, read while drawing). Its own reading since the trail left the tuner's meter (Phase 78).
 */
@Stable
interface TrailReading : LensReading {
    val history: PitchHistory
}

/**
 * The trail (2.0, Phase 71, the tuner's meter look until Phase 78 made it a Practice tool): the last seconds of
 * the reading, falling under the newest one
 * (docs/design/v2-tuner-screens-preview.html, `lensFall`; 1.1's pitch trail tool, turned). The newest reading
 * is a dot with a soft glow [TrailGeometry.NOW_FROM_TOP] under the top edge, and the older ones fall under it
 * at [TrailGeometry.FALL_PER_SECOND], fading out toward the bottom; cents run across, flat left and sharp
 * right, ±50 from end to end on the stretched scale ([meterStretch]). A tick every 10 cents stands on the top
 * edge, a hairline runs down the middle, and the in-tune zone (the tuner's window, [LensReading.inTuneWindow])
 * is a band of the "in tune" colour down the whole height, a little brighter while the in-tune glow is lit.
 * The line and the dot take the reading's tone ([LensReading.tone]); the parts that were in tune stay green.
 * A gap (no note) and a change of note break the line.
 *
 * As 1.1's trail: a frame loop runs only while some note is in view ([runTrail]) and bumps a counter the
 * drawing reads, so a reading recomposes nothing; the drawing allocates nothing (its paths, brushes and strokes
 * are made once per size and colours). Once the string dies the line falls out of the box and the loop rests.
 */
@Composable
fun BoxScope.TrailLook(reading: TrailReading) {
    val history = reading.history
    val green = MaterialTheme.styleTokens.inTune
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val clock = remember(history) { TrailClock() }
    val frames = remember(history) { mutableIntStateOf(0) }
    LaunchedEffect(clock) { runTrail(history, clock, frames) }
    Spacer(
        Modifier
            .matchParentSize()
            .drawWithCache {
                val art = TrailArt(TrailGeometry(size.width, size.height, density), green, muted)
                // Read by the frame loop: how many seconds of readings the box shows.
                clock.spanSeconds = art.geometry.spanSeconds
                onDrawBehind {
                    // Redraws on every frame of the loop.
                    frames.intValue
                    art.draw(this, reading, clock)
                }
            },
    )
}

/**
 * Where the trail is drawn in a box [width] × [height] px, with [dp] px to a dp; pure, so the mapping is tested
 * on the JVM (`TrailGeometryTest`).
 */
internal class TrailGeometry(val width: Float, val height: Float, private val dp: Float) {
    /** The middle, where the note is: the target, in tune. */
    val centreX = width / 2f

    /** From the middle to ±50 cents: the scale's ends [SCALE_INSET] in from the sides. */
    val half = (width / 2f - SCALE_INSET * dp).coerceAtLeast(0f)

    /** Where the newest reading is drawn, under the top edge. */
    val nowY = (NOW_FROM_TOP * dp).coerceAtMost(height)

    /** How fast the line falls, px a second. */
    val pxPerSecond = FALL_PER_SECOND * dp

    /** Seconds of readings between the newest and the bottom edge. */
    val spanSeconds = (height - nowY) / pxPerSecond

    /** The x of a reading [cents] off (positive is sharp, right), on the stretched scale, pinned at ±50. */
    fun xOf(cents: Float): Float = centreX + meterStretch(meterCents(cents)) / METER_RANGE_CENTS * half

    /** The y of a reading [age] seconds older than the newest moment shown. */
    fun yOf(age: Float): Float = nowY + age * pxPerSecond

    /** Half the in-tune band's width for a [window] of cents each way, on the stretched scale. */
    fun zoneHalfWidth(window: Float): Float = meterStretch(window.coerceIn(0f, METER_RANGE_CENTS)) / METER_RANGE_CENTS * half

    /** How long the tick at [cents] (a multiple of [TICK_STEP_CENTS]) is: the ends and the middle longer. */
    fun tickLength(cents: Int): Float = (if (cents % MAJOR_TICK_EVERY == 0) MAJOR_TICK else MINOR_TICK) * dp

    /**
     * How much of the line shows at [y]: all of it at the newest reading, [FADE_MIDDLE_ALPHA] at
     * [FADE_MIDDLE] of the way to the bottom, none at the bottom (the mock-up's `fade`).
     */
    fun fadeAt(y: Float): Float {
        val span = height - nowY
        if (span <= 0f) return 1f
        val t = ((y - nowY) / span).coerceIn(0f, 1f)
        return if (t <= FADE_MIDDLE) {
            1f + (FADE_MIDDLE_ALPHA - 1f) * t / FADE_MIDDLE
        } else {
            FADE_MIDDLE_ALPHA * (1f - (t - FADE_MIDDLE) / (1f - FADE_MIDDLE))
        }
    }

    companion object {
        /** The scale's ends, this many dp in from the box's sides (*The lens*). */
        const val SCALE_INSET = 24f

        /** The newest reading, this many dp under the top edge. */
        const val NOW_FROM_TOP = 28f

        /** The line falls this many dp a second (the mock-up's 80). */
        const val FALL_PER_SECOND = 80f

        /** A tick every this many cents along the top edge, the middle and the ends longer. */
        const val TICK_STEP_CENTS = 10
        const val MAJOR_TICK_EVERY = 50
        const val MAJOR_TICK = 12f
        const val MINOR_TICK = 5f

        /** The line's fade: [FADE_MIDDLE_ALPHA] of it at [FADE_MIDDLE] of the way down. */
        const val FADE_MIDDLE = 0.7f
        const val FADE_MIDDLE_ALPHA = 0.28f
    }
}

/**
 * The trail's moment, stepped by [runTrail] once a frame and read while drawing. [now] is the moment drawn at
 * [TrailGeometry.nowY], on the history's clock: it runs on with the screen's frames and is pulled gently
 * towards the newest reading, so the line falls smoothly although the readings come in steps of about 21 ms
 * (sometimes two at once). NaN draws the line still, at the newest reading (before the loop's first frame,
 * and in UI tests). [spanSeconds] is set by the drawing, from the box's height.
 */
private class TrailClock {
    var now = Float.NaN
    var spanSeconds = 4f
    var lastEnd = -1L
    var endMovedAtNanos = 0L
    var lastNanos = 0L
}

/**
 * Steps [clock] on every frame while a note is in view and new readings come, then waits (checking
 * [IDLE_POLL_MS] apart, without drawing) until a note shows again. Each step bumps [frames], which the
 * drawing reads.
 */
private suspend fun runTrail(history: PitchHistory, clock: TrailClock, frames: MutableIntState) {
    if (coroutineContext[InfiniteAnimationPolicy] != null) return stillTrail(history, clock, frames)
    while (true) {
        if (!history.hasNoteWithin(clock.spanSeconds + EDGE_SECONDS) || history.end == clock.lastEnd && clock.stalled()) {
            // Nothing to move: draw the empty look once and wait.
            clock.now = Float.NaN
            frames.intValue++
            while (!history.hasNoteWithin(clock.spanSeconds + EDGE_SECONDS) || history.end == clock.lastEnd) delay(IDLE_POLL_MS)
            clock.lastNanos = 0L
        }
        // An "infinite" animation, so UI tests (whose policy would end it at once) draw the still line instead.
        withInfiniteAnimationFrameNanos { nanos -> clock.step(history, nanos) }
        frames.intValue++
    }
}

/**
 * Where infinite animations don't run (UI tests): the line is drawn still at the newest reading, redrawn
 * whenever new readings came ([IDLE_POLL_MS] apart) while a note is in view or has just left it, so a quiet
 * tuner draws nothing new.
 */
private suspend fun stillTrail(history: PitchHistory, clock: TrailClock, frames: MutableIntState) {
    var drawnEnd = -1L
    var drewNote = true
    while (true) {
        val end = history.end
        if (end != drawnEnd) {
            val note = history.hasNoteWithin(clock.spanSeconds + EDGE_SECONDS)
            drawnEnd = end
            if (note || drewNote) frames.intValue++
            drewNote = note
        }
        delay(IDLE_POLL_MS)
    }
}

/** Whether no reading came for [STALL_NANOS]: the tuner stopped listening, so the line stops moving. */
private fun TrailClock.stalled(): Boolean = lastNanos != 0L && lastNanos - endMovedAtNanos > STALL_NANOS

private fun TrailClock.step(history: PitchHistory, nanos: Long) {
    val end = history.end
    val dt = if (lastNanos == 0L) 0f else ((nanos - lastNanos) / NANOS_PER_SECOND).coerceIn(0f, MAX_STEP_SECONDS)
    lastNanos = nanos
    if (end != lastEnd) {
        lastEnd = end
        endMovedAtNanos = nanos
    }
    if (end <= history.firstReadable(end)) return
    val newest = history.timeAt(end - 1)
    // On with the frames, pulled towards the newest reading; a jump (a new session, a long pause) snaps.
    now = if (now.isNaN() || abs(newest - (now + dt)) > SNAP_SECONDS) newest else (now + dt).let { it + (newest - it) * (1f - exp(-dt / PULL_SECONDS)) }
}

/** Whether a note (not a gap) lies within the last [seconds] of [this] history. Allocates nothing. */
private fun PitchHistory.hasNoteWithin(seconds: Float): Boolean {
    val end = end
    var index = firstWithin(seconds, end)
    while (index < end) {
        if (!isGapAt(index)) return true
        index++
    }
    return false
}

/**
 * The trail's pieces, made once per size and colours: the ticks' places, the band's and the hairline's, the two
 * paths (in tune and not) that are refilled each frame, the line's stroke, the fade drawn over it, and the layer
 * the fade is drawn into.
 */
private class TrailArt(val geometry: TrailGeometry, private val green: Color, private val muted: Color) {
    private val dp = geometry.pxPerSecond / TrailGeometry.FALL_PER_SECOND
    private val tickColour = muted.copy(alpha = TICK_ALPHA)
    private val hairlineColour = muted.copy(alpha = HAIRLINE_ALPHA)
    private val tickX = FloatArray(2 * (METER_RANGE_CENTS.toInt() / TrailGeometry.TICK_STEP_CENTS) + 1) { i ->
        geometry.xOf((i * TrailGeometry.TICK_STEP_CENTS - METER_RANGE_CENTS.toInt()).toFloat())
    }
    private val tickLength = FloatArray(tickX.size) { i -> geometry.tickLength(i * TrailGeometry.TICK_STEP_CENTS - METER_RANGE_CENTS.toInt()) }
    private val stroke = Stroke(width = LINE_WIDTH * dp, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val tonePath = Path()
    private val greenPath = Path()
    private val layerBounds = Rect(0f, 0f, geometry.width, geometry.height)
    private val layerPaint = Paint()
    private val fade = Brush.verticalGradient(
        0f to Color.White,
        TrailGeometry.FADE_MIDDLE to Color.White.copy(alpha = TrailGeometry.FADE_MIDDLE_ALPHA),
        1f to Color.Transparent,
        startY = geometry.nowY,
        endY = geometry.height,
    )

    fun draw(scope: DrawScope, reading: TrailReading, clock: TrailClock) = with(scope) {
        val glow = reading.glow.coerceIn(0f, 1f)
        val presence = reading.presence
        // The in-tune zone, down the whole height; a little brighter while the in-tune glow is lit.
        val zone = geometry.zoneHalfWidth(reading.inTuneWindow)
        drawRect(
            green.copy(alpha = BAND_ALPHA + BAND_GLOW_ALPHA * glow),
            topLeft = Offset(geometry.centreX - zone, 0f),
            size = Size(2f * zone, geometry.height),
        )
        drawLine(hairlineColour, Offset(geometry.centreX, 0f), Offset(geometry.centreX, geometry.height), strokeWidth = HAIRLINE_WIDTH * dp)
        for (i in tickX.indices) drawLine(tickColour, Offset(tickX[i], 0f), Offset(tickX[i], tickLength[i]), strokeWidth = TICK_WIDTH * dp)

        val history = reading.history
        val end = history.end
        if (end <= history.firstReadable(end)) return@with
        val newest = end - 1
        val now = if (clock.now.isNaN()) history.timeAt(newest) else clock.now
        tonePath.reset()
        greenPath.reset()
        val first = history.firstWithin(clock.spanSeconds + EDGE_SECONDS, end)
        // The segment ending at each reading takes that reading's colour; a gap or a new note breaks the line.
        var lastX = Float.NaN
        var lastY = 0f
        var lastTarget = PitchHistory.NO_NOTE
        var lastInTune: Boolean? = null
        var segments = 0
        var index = first
        while (index < end) {
            val target = history.targetAt(index)
            if (target == PitchHistory.NO_NOTE) {
                lastX = Float.NaN
                lastInTune = null
            } else {
                val x = geometry.xOf(history.centsAt(index))
                val y = geometry.yOf(now - history.timeAt(index))
                val tuned = history.inTuneAt(index)
                if (!lastX.isNaN() && target == lastTarget) {
                    val path = if (tuned) greenPath else tonePath
                    // A new run (after a gap, or where the colour changes) starts at the last point.
                    if (lastInTune != tuned) path.moveTo(lastX, lastY)
                    path.lineTo(x, y)
                    lastInTune = tuned
                    segments++
                } else {
                    lastInTune = null
                }
                lastX = x
                lastY = y
                lastTarget = target
            }
            index++
        }
        // A reader that stalled long enough for the writer to lap it skips the line this frame.
        if (history.overwritten(first)) return@with
        val tone = reading.tone
        if (segments > 0) {
            // The line fades toward the bottom: drawn in a layer of its own, then faded there by the fade's alpha.
            drawIntoCanvas { it.saveLayer(layerBounds, layerPaint) }
            drawPath(tonePath, tone, style = stroke)
            drawPath(greenPath, mix(muted, green, presence), style = stroke)
            drawRect(fade, blendMode = BlendMode.DstIn)
            drawIntoCanvas { it.restore() }
        }
        // The newest reading, when it is a note: a dot in the tone with a soft glow, stronger in tune.
        if (!history.isGapAt(newest)) {
            val x = geometry.xOf(history.centsAt(newest))
            val y = geometry.yOf(now - history.timeAt(newest))
            val reach = (GLOW_REACH + (GLOW_REACH_IN_TUNE - GLOW_REACH) * glow) * dp
            val strength = (GLOW_ALPHA + (GLOW_ALPHA_IN_TUNE - GLOW_ALPHA) * glow) / GLOW_RINGS
            val head = HEAD_RADIUS * dp
            val centre = Offset(x, y)
            // Rings from the outside in, each adding a little: a soft glow in any colour, without a brush per colour.
            for (ring in GLOW_RINGS downTo 1) drawCircle(tone, head + reach * ring / GLOW_RINGS, centre, alpha = strength)
            drawCircle(tone, head, centre)
        }
    }
}

/** While nothing is in view, the loop checks for a note this often instead of drawing. */
private const val IDLE_POLL_MS = 100L

/** Readings just older than the box shows are walked too, so the line runs out of the bottom edge. */
private const val EDGE_SECONDS = 0.2f

/** The trail's moment snaps to the newest reading when it is further off than this. */
private const val SNAP_SECONDS = 0.25f

/** How quickly the trail's moment is pulled towards the newest reading. */
private const val PULL_SECONDS = 0.15f

/** No new reading for this long: the line stops moving. */
private const val STALL_NANOS = 500_000_000L

private const val NANOS_PER_SECOND = 1_000_000_000f
private const val MAX_STEP_SECONDS = 0.1f

/** The mock-up's look, in dp and alphas. */
private const val BAND_ALPHA = 0.12f
private const val BAND_GLOW_ALPHA = 0.08f
private const val HAIRLINE_ALPHA = 0.45f
private const val HAIRLINE_WIDTH = 1f
private const val TICK_ALPHA = 0.7f
private const val TICK_WIDTH = 1.5f
private const val LINE_WIDTH = 3f
private const val HEAD_RADIUS = 7f
private const val GLOW_REACH = 10f
private const val GLOW_REACH_IN_TUNE = 20f
private const val GLOW_ALPHA = 0.2f
private const val GLOW_ALPHA_IN_TUNE = 0.3f
private const val GLOW_RINGS = 6
