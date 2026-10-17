package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.platform.InfiniteAnimationPolicy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import io.github.klaboukes.twangle.core.designsystem.MusicSignsFont
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.trail.PitchHistory
import io.github.klaboukes.twangle.feature.tuner.PitchTrailDescriber
import io.github.klaboukes.twangle.feature.tuner.R
import io.github.klaboukes.twangle.feature.tuner.SettledTrailDescription
import io.github.klaboukes.twangle.feature.tuner.TrailDescription
import io.github.klaboukes.twangle.feature.tuner.TrailMotion
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags
import kotlinx.coroutines.delay
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.exp

/**
 * The pitch trail (Phase 31, option A of docs/design/pitch-trail-preview.html; docs/architecture/ui-tuner.md,
 * *Pitch trail view*): a line of the last [TrailGeometry.SPAN_SECONDS] of [history] in the meter's
 * place, at its 2:1 shape, on a calm field like the meter's, sized by its width, without a frame.
 * Time runs from right to left, the newest reading a little in from the right edge with a dot and a
 * soft glow; up is sharp, down is flat, ±50 cents over the height (clamped there), faint hairlines at
 * ±25 with small ♯ and ♭ marks at the right end; the oldest third fades out. Round the centre line a
 * soft band of ±5 cents in the style's "in tune" colour glows a little brighter while the note is in
 * tune now (eased; at once with [animationsOff]). The parts of the line that were in tune take the
 * "in tune" colour, the rest the accent, and the head dot likewise. A gap (no note) breaks the line,
 * and so does a change of note.
 *
 * Like the strobe and the meters, the line is drawn in the draw phase only: a frame loop runs while
 * some reading is in view and only bumps a counter the drawing reads, so a new reading recomposes
 * nothing, and drawing allocates nothing (its paths, brushes and marks are made once per size and
 * colours). TalkBack hears it in words ([PitchTrailDescriber]: "Pitch trail, steady, in tune",
 * "wavering, 10 cents sharp", "bending up"), settled so it doesn't chatter ([SettledTrailDescription]);
 * the note under it keeps speaking the reading itself.
 */
@Composable
fun PitchTrailPanel(history: PitchHistory, modifier: Modifier = Modifier, animationsOff: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val tokens = MaterialTheme.styleTokens
    val colours = TrailColours(
        accent = scheme.primary,
        inTune = tokens.inTune,
        muted = scheme.onSurfaceVariant,
        background = scheme.background,
        dark = tokens.dark,
    )
    val measurer = rememberTextMeasurer()
    val clock = remember(history) { TrailClock() }
    val frames = remember(history) { mutableIntStateOf(0) }
    LaunchedEffect(clock, animationsOff) { runTrail(history, clock, frames, animationsOff) }

    var description by remember(history) { mutableStateOf(TrailDescription.NO_NOTE) }
    LaunchedEffect(history) {
        val settled = SettledTrailDescription()
        while (true) {
            description = settled.offer(PitchTrailDescriber.describe(history))
            delay(DESCRIBE_EVERY_MS)
        }
    }
    val spoken = stringResource(R.string.tuner_trail_spoken, trailWords(description))

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(TrailGeometry.ASPECT_RATIO)
            .preferredFrameRate(TRAIL_FRAME_RATE),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .testTag(TunerTestTags.TRAIL_PANEL)
                .clearAndSetSemantics { contentDescription = spoken }
                .drawWithCache {
                    val art = TrailArt(size.width, colours, measurer, this)
                    onDrawBehind {
                        // Redraws on every frame of the loop.
                        frames.intValue
                        art.draw(this, history, clock)
                    }
                },
        )
    }
}

/** The description's words after "Pitch trail, ": "steady, in tune", "wavering, 10 cents sharp", "bending up", "no note". */
@Composable
private fun trailWords(description: TrailDescription): String {
    val where = when {
        description.inTune -> stringResource(R.string.tuner_in_tune)
        description.cents > 0 -> pluralStringResource(R.plurals.tuner_cents_sharp, description.cents, description.cents)
        else -> pluralStringResource(R.plurals.tuner_cents_flat, -description.cents, -description.cents)
    }
    return when (description.motion) {
        TrailMotion.NO_NOTE -> stringResource(R.string.tuner_trail_no_note)
        TrailMotion.STEADY -> stringResource(R.string.tuner_trail_steady, where)
        TrailMotion.WAVERING -> stringResource(R.string.tuner_trail_wavering, where)
        TrailMotion.BENDING_UP -> stringResource(R.string.tuner_trail_bending_up)
        TrailMotion.BENDING_DOWN -> stringResource(R.string.tuner_trail_bending_down)
    }
}

/**
 * Where the trail is drawn, in units of 1/340 of the panel's width (about 1 dp on a phone), as in the
 * approved mock-up; pure, so the mapping is tested on the JVM ([TrailGeometryTest]).
 */
internal object TrailGeometry {
    const val ASPECT_RATIO = 2f
    const val UNITS_WIDE = 340f

    /** Seconds of the line in view (the history keeps 5). */
    const val SPAN_SECONDS = 4f

    /** Cents at the top (sharp) and, negative, the bottom (flat); readings further off are drawn there. */
    const val RANGE_CENTS = 50f

    /** The hairlines, this many cents either side of the centre. */
    const val HAIRLINE_CENTS = 25f

    /** The in-tune band, this many cents either side of the centre (the tuner's tolerance). */
    const val BAND_CENTS = 5f

    const val TOP = 14f
    const val BOTTOM = 156f
    const val MIDDLE = (TOP + BOTTOM) / 2f

    /** The oldest reading in view, at the left, and the newest, a little in from the right edge. */
    const val X_OLDEST = 16f
    const val X_NOW = 315f

    /** The oldest third of the line fades out to the left. */
    const val FADE_SHARE = 1f / 3f

    /** The y of [cents] (positive is sharp, up), clamped to ±[RANGE_CENTS]. */
    fun yOf(cents: Float): Float = MIDDLE - cents.coerceIn(-RANGE_CENTS, RANGE_CENTS) * (BOTTOM - TOP) / (2f * RANGE_CENTS)

    /** The x of a reading [age] seconds before the newest moment shown ([X_NOW]); older than [SPAN_SECONDS] lies left of [X_OLDEST]. */
    fun xOf(age: Float): Float = X_NOW - age / SPAN_SECONDS * (X_NOW - X_OLDEST)

    /** How much of the line shows at [x]: 0 at the oldest end, rising to 1 a third of the way in. */
    fun fadeAt(x: Float): Float = ((x - X_OLDEST) / (FADE_SHARE * (X_NOW - X_OLDEST))).coerceIn(0f, 1f)
}

/** The colours the trail is drawn in, from the current style. */
private data class TrailColours(val accent: Color, val inTune: Color, val muted: Color, val background: Color, val dark: Boolean)

/**
 * The trail's moment and glow, stepped by [runTrail] once a frame and read while drawing. [now] is the
 * moment drawn at [TrailGeometry.X_NOW], on the history's clock: it runs on with the screen's frames
 * and is pulled gently towards the newest reading, so the line scrolls smoothly although the readings
 * come in steps of about 21 ms (sometimes two at once).
 */
private class TrailClock {
    var now = Float.NaN
    var glow = 0f
    var lastEnd = -1L
    var endMovedAtNanos = 0L
    var lastNanos = 0L
}

/**
 * Steps [clock] on every frame while a reading is in view and new ones come, then waits (checking
 * [IDLE_POLL_MS] apart, without drawing) until a note shows again. Each step bumps [frames], which
 * the drawing reads.
 */
private suspend fun runTrail(history: PitchHistory, clock: TrailClock, frames: MutableIntState, animationsOff: Boolean) {
    if (coroutineContext[InfiniteAnimationPolicy] != null) return stillTrail(history, frames)
    while (true) {
        if (!history.hasNoteWithin(TrailGeometry.SPAN_SECONDS + EDGE_SECONDS) || history.end == clock.lastEnd && clock.stalled()) {
            // Nothing to scroll: draw the empty panel once and wait.
            clock.glow = 0f
            clock.now = Float.NaN
            frames.intValue++
            while (!history.hasNoteWithin(TrailGeometry.SPAN_SECONDS + EDGE_SECONDS) || history.end == clock.lastEnd) delay(IDLE_POLL_MS)
            clock.lastNanos = 0L
        }
        // An "infinite" animation, like the wave's (UI tests draw the still line instead, [stillTrail]).
        withInfiniteAnimationFrameNanos { nanos -> clock.step(history, nanos, animationsOff) }
        frames.intValue++
    }
}

/**
 * Where infinite animations don't run (UI tests, whose policy would end the frame loop at once): the
 * line is drawn still at the newest reading, redrawn whenever new readings came ([IDLE_POLL_MS] apart).
 */
private suspend fun stillTrail(history: PitchHistory, frames: MutableIntState) {
    var drawnEnd = -1L
    while (true) {
        val end = history.end
        if (end != drawnEnd) {
            drawnEnd = end
            frames.intValue++
        }
        delay(IDLE_POLL_MS)
    }
}

/** Whether no reading came for [STALL_NANOS]: the tuner stopped listening, so the line stops moving. */
private fun TrailClock.stalled(): Boolean = lastNanos != 0L && lastNanos - endMovedAtNanos > STALL_NANOS

private fun TrailClock.step(history: PitchHistory, nanos: Long, animationsOff: Boolean) {
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
    val target = if (!history.isGapAt(end - 1) && history.inTuneAt(end - 1)) 1f else 0f
    glow = if (animationsOff) target else glow + (target - glow) * (1f - exp(-dt / if (target > glow) GLOW_IN_SECONDS else GLOW_OUT_SECONDS))
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
 * The trail's pieces, made once per size and colours: the calm field, the band, the hairlines and the
 * signs, the two paths (in tune and not) that are refilled each frame, and their fading brushes.
 */
private class TrailArt(width: Float, colours: TrailColours, measurer: TextMeasurer, density: Density) {
    private val u = width / TrailGeometry.UNITS_WIDE
    private val inTuneColour = colours.inTune
    private val accent = colours.accent
    private val centre = Offset(width / 2f, TrailGeometry.MIDDLE * u)
    private val field = Brush.radialGradient(
        0f to colours.background.copy(alpha = FIELD_ALPHA),
        0.62f to colours.background.copy(alpha = FIELD_ALPHA * 0.75f),
        1f to colours.background.copy(alpha = 0f),
        center = centre,
        radius = FIELD_RX * u,
    )
    private val hairline = colours.muted.copy(alpha = if (colours.dark) HAIRLINE_DARK_ALPHA else HAIRLINE_LIGHT_ALPHA)
    private val bandLeft = TrailGeometry.X_OLDEST * u
    private val bandRight = (TrailGeometry.X_NOW + BAND_PAST_NOW) * u
    private val bandTop = TrailGeometry.yOf(TrailGeometry.BAND_CENTS) * u
    private val bandBottom = TrailGeometry.yOf(-TrailGeometry.BAND_CENTS) * u
    private val hairlines = floatArrayOf(TrailGeometry.yOf(TrailGeometry.HAIRLINE_CENTS) * u, TrailGeometry.yOf(-TrailGeometry.HAIRLINE_CENTS) * u)
    private val signStyle = TextStyle(fontFamily = MusicSignsFont, color = colours.muted.copy(alpha = SIGN_ALPHA), fontSize = (SIGN_UNITS * u).toSpNoScale(density))
    private val sharp = measurer.measure(AnnotatedString("♯"), signStyle)
    private val flat = measurer.measure(AnnotatedString("♭"), signStyle)
    private val sharpAt = Offset(SIGN_X * u - sharp.size.width / 2f, TrailGeometry.TOP * u - sharp.size.height * 0.2f)
    private val flatAt = Offset(SIGN_X * u - flat.size.width / 2f, TrailGeometry.BOTTOM * u - flat.size.height * 0.8f)
    private val stroke = Stroke(width = LINE_UNITS * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val fadeFrom = TrailGeometry.X_OLDEST * u
    private val fadeTo = (TrailGeometry.X_OLDEST + TrailGeometry.FADE_SHARE * (TrailGeometry.X_NOW - TrailGeometry.X_OLDEST)) * u
    private val inTuneBrush = fading(colours.inTune)
    private val accentBrush = fading(colours.accent)
    private val inTuneHalo = halo(colours.inTune)
    private val accentHalo = halo(colours.accent)
    private val inTunePath = Path()
    private val accentPath = Path()

    private fun fading(colour: Color) = Brush.horizontalGradient(
        0f to colour.copy(alpha = 0f),
        1f to colour.copy(alpha = LINE_ALPHA),
        startX = fadeFrom,
        endX = fadeTo,
    )

    private fun halo(colour: Color) = Brush.radialGradient(
        0f to colour.copy(alpha = HALO_ALPHA),
        1f to colour.copy(alpha = 0f),
        center = Offset.Zero,
        radius = HALO_UNITS * u,
    )

    fun draw(scope: DrawScope, history: PitchHistory, clock: TrailClock) = with(scope) {
        scale(1f, FIELD_RY / FIELD_RX, centre) { drawCircle(field, FIELD_RX * u, centre) }
        val end = history.end
        val newest = end - 1
        val hasNewest = end > history.firstReadable(end)
        // Before the first frame of the loop (or where it doesn't run, as in UI tests): still, at the newest reading.
        val still = clock.now.isNaN()
        val glow = if (still) (if (hasNewest && !history.isGapAt(newest) && history.inTuneAt(newest)) 1f else 0f) else clock.glow
        drawRect(
            inTuneColour.copy(alpha = BAND_ALPHA + BAND_GLOW_ALPHA * glow),
            topLeft = Offset(bandLeft, bandTop),
            size = Size(bandRight - bandLeft, bandBottom - bandTop),
        )
        for (y in hairlines) drawLine(hairline, Offset(bandLeft, y), Offset(TrailGeometry.X_NOW * u, y), strokeWidth = u)
        drawText(sharp, topLeft = sharpAt)
        drawText(flat, topLeft = flatAt)
        if (!hasNewest) return@with
        val now = if (still) history.timeAt(newest) else clock.now
        inTunePath.reset()
        accentPath.reset()
        val first = history.firstWithin(TrailGeometry.SPAN_SECONDS + EDGE_SECONDS, end)
        // The segment ending at each reading takes that reading's colour; a gap or a new note breaks the line.
        var lastX = Float.NaN
        var lastY = 0f
        var lastTarget = PitchHistory.NO_NOTE
        var lastInTune: Boolean? = null
        var headX = Float.NaN
        var headY = 0f
        var headInTune = false
        var index = first
        while (index < end) {
            val target = history.targetAt(index)
            if (target == PitchHistory.NO_NOTE) {
                lastX = Float.NaN
                lastInTune = null
                headX = Float.NaN
            } else {
                val x = TrailGeometry.xOf(now - history.timeAt(index)) * u
                val y = TrailGeometry.yOf(history.centsAt(index)) * u
                val tuned = history.inTuneAt(index)
                if (!lastX.isNaN() && target == lastTarget) {
                    val path = if (tuned) inTunePath else accentPath
                    // A new run (after a gap, or where the colour changes) starts at the last point.
                    if (lastInTune != tuned) path.moveTo(lastX, lastY)
                    path.lineTo(x, y)
                    lastInTune = tuned
                } else {
                    lastInTune = null
                }
                lastX = x
                lastY = y
                lastTarget = target
                headX = x
                headY = y
                headInTune = tuned
            }
            index++
        }
        // A reader that stalled long enough for the writer to lap it skips the line this frame.
        if (history.overwritten(first)) return@with
        drawPath(accentPath, accentBrush, style = stroke)
        drawPath(inTunePath, inTuneBrush, style = stroke)
        if (!headX.isNaN()) {
            translate(headX, headY) {
                drawCircle(if (headInTune) inTuneHalo else accentHalo, HALO_UNITS * u, Offset.Zero, alpha = HALO_REST + (1f - HALO_REST) * glow)
                drawCircle(if (headInTune) inTuneColour else accent, HEAD_UNITS * u, Offset.Zero)
            }
        }
    }
}

/** The frame rate the moving trail asks for, as the meter does (only while it draws). */
private const val TRAIL_FRAME_RATE = 120f

/** TalkBack's description is worked out this often (and settles over two of them). */
private const val DESCRIBE_EVERY_MS = 500L

/** While nothing is in view, the loop checks for a note this often instead of drawing. */
private const val IDLE_POLL_MS = 100L

/** Readings just older than the span are walked too, so the line runs out of the left edge. */
private const val EDGE_SECONDS = 0.2f

/** The trail's moment snaps to the newest reading when it is further off than this. */
private const val SNAP_SECONDS = 0.25f

/** How quickly the trail's moment is pulled towards the newest reading. */
private const val PULL_SECONDS = 0.15f

/** No new reading for this long: the line stops scrolling. */
private const val STALL_NANOS = 500_000_000L

/** The band's glow eases in and out about as the meter's in-tune colour does. */
private const val GLOW_IN_SECONDS = 0.15f
private const val GLOW_OUT_SECONDS = 0.25f

private const val NANOS_PER_SECOND = 1_000_000_000f
private const val MAX_STEP_SECONDS = 0.1f

private const val FIELD_ALPHA = 0.75f
private const val FIELD_RX = 200f
private const val FIELD_RY = 120f
private const val BAND_ALPHA = 0.08f
private const val BAND_GLOW_ALPHA = 0.14f
private const val BAND_PAST_NOW = 11f
private const val HAIRLINE_DARK_ALPHA = 0.18f
private const val HAIRLINE_LIGHT_ALPHA = 0.24f
private const val SIGN_UNITS = 13f
private const val SIGN_X = 328f
private const val SIGN_ALPHA = 0.8f
private const val LINE_UNITS = 2.7f
private const val LINE_ALPHA = 0.95f
private const val HEAD_UNITS = 4f
private const val HALO_UNITS = 16f
private const val HALO_ALPHA = 0.35f
private const val HALO_REST = 0.4f
