package io.github.klaboukes.twangle.core.designsystem.metronome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import io.github.klaboukes.twangle.core.designsystem.LINING_FIGURES
import io.github.klaboukes.twangle.core.designsystem.meter.FieldArt
import io.github.klaboukes.twangle.core.designsystem.meter.GlowArt
import io.github.klaboukes.twangle.core.designsystem.meter.JITTER_LENGTH
import io.github.klaboukes.twangle.core.designsystem.meter.JITTER_TURN
import io.github.klaboukes.twangle.core.designsystem.meter.JITTER_WIDTH
import io.github.klaboukes.twangle.core.designsystem.meter.MAJOR_TICK_UNITS
import io.github.klaboukes.twangle.core.designsystem.meter.MAJOR_TICK_WIDTH
import io.github.klaboukes.twangle.core.designsystem.meter.MARKER_SHADOW_X
import io.github.klaboukes.twangle.core.designsystem.meter.MARKER_SHADOW_Y
import io.github.klaboukes.twangle.core.designsystem.meter.MINOR_TICK_INK
import io.github.klaboukes.twangle.core.designsystem.meter.MINOR_TICK_UNITS
import io.github.klaboukes.twangle.core.designsystem.meter.MINOR_TICK_WIDTH
import io.github.klaboukes.twangle.core.designsystem.meter.MeterRoles
import io.github.klaboukes.twangle.core.designsystem.meter.NUMBER_SIZE_UNITS
import io.github.klaboukes.twangle.core.designsystem.meter.NeedleArt
import io.github.klaboukes.twangle.core.designsystem.meter.SCALE_NUMBER_INK
import io.github.klaboukes.twangle.core.designsystem.meter.TrackKind
import io.github.klaboukes.twangle.core.designsystem.meter.handJitter
import io.github.klaboukes.twangle.core.designsystem.meter.meterRoles
import io.github.klaboukes.twangle.core.dsp.metronome.ClickTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The metronome's tempo dial, from the approved mock-up (docs/design/metronome-nav-preview.html,
 * `drawDial`): frameless like the tuner's meter, drawn straight on the background over the same calm
 * field, with the meter's scale rules (a tick every 10 BPM, major ticks and numbers at 30 and every
 * 50) and the style's arc and needle finish. 30–250 BPM run clockwise over 300 degrees, the gap at
 * the bottom; the arc up to the tempo is lit in the accent and the style's needle points at it from
 * inside the arc. One soft accent glow in the middle, behind the tempo, on each beat. All lengths are
 * in u = the dial's width / 288 (1 dp at its usual 288 dp).
 */

/**
 * The tempo dial's art at [bpm] (a whole tempo, or one in between while it glides to a new one),
 * with the beat's glow behind its middle at [beatGlow] (0..1; the caller eases it out after each
 * beat). While Listen listens, [ringBpm] (0 for none) puts a small hollow ring on the arc at the
 * tempo it is settling on, at [ringAlpha]; [foundGlow] (0..1) is the one soft glow behind the tempo
 * when a found tempo is used (Phase 27, docs/design/metronome-listen-preview.html). All are read in
 * the draw phase only, so a moving tempo or glow redraws the dynamic layer without recomposing.
 * Square; the field fades out at its edge. The tempo's figures go over its middle (the caller's),
 * and the dial says nothing to TalkBack itself.
 */
@Composable
fun TempoDialArt(
    bpm: () -> Float,
    beatGlow: () -> Float,
    modifier: Modifier = Modifier,
    ringBpm: () -> Float = { 0f },
    ringAlpha: () -> Float = { 0f },
    foundGlow: () -> Float = { 0f },
) {
    val roles = meterRoles()
    val measurer = rememberTextMeasurer()
    Box(modifier) {
        // The field, the unlit arc, the ticks and numbers: made and drawn once per size and colours.
        Spacer(
            Modifier.matchParentSize().drawWithCache {
                val dial = DialShape(size)
                val field = FieldArt(dial.cx, dial.cy, FIELD_UNITS * dial.u, FIELD_UNITS * dial.u, roles)
                val track = DialTrack(dial, roles)
                val scale = DialScale(dial, roles, this) { text, style -> measurer.measure(text, style) }
                onDrawBehind {
                    field.draw(this)
                    track.drawUnlit(this)
                    scale.draw(this)
                }
            },
        )
        // The beat's glow, the lit arc and the needle, redrawn as the tempo or the glow change.
        Spacer(
            Modifier.matchParentSize().drawWithCache {
                val dial = DialShape(size)
                val glow = GlowArt(GLOW_UNITS * dial.u, roles.accent)
                val glowPeak = if (roles.dark) GLOW_DARK_ALPHA else GLOW_LIGHT_ALPHA
                val found = GlowArt(FOUND_GLOW_UNITS * dial.u, roles.accent)
                val foundPeak = if (roles.dark) FOUND_GLOW_DARK_ALPHA else FOUND_GLOW_LIGHT_ALPHA
                val ring = ListenRing(dial, roles)
                val track = DialTrack(dial, roles)
                val needle = NeedleArt(dial.cx, dial.cy - (dial.r - NEEDLE_IN_UNITS * dial.u), NEEDLE_UNITS * dial.u, dial.u, roles)
                val needleColor = roles.marker(0f)
                val tipColor = roles.centre(0f)
                val shadowX = MARKER_SHADOW_X * dial.u
                val shadowY = MARKER_SHADOW_Y * dial.u
                val centre = Offset(dial.cx, dial.cy)
                onDrawBehind {
                    glow.draw(this, dial.cx, dial.cy, beatGlow().coerceIn(0f, 1f) * glowPeak)
                    found.draw(this, dial.cx, dial.cy, foundGlow().coerceIn(0f, 1f) * foundPeak)
                    val tempo = bpm()
                    track.drawLit(this, tempo)
                    val degrees = dialDegrees(tempo)
                    // The shadow always falls down and to the right, however the needle turns.
                    translate(shadowX, shadowY) { rotate(degrees, centre) { needle.drawShadow(this) } }
                    rotate(degrees, centre) { needle.draw(this, needleColor, tipColor) }
                    val listening = ringBpm()
                    if (listening > 0f) ring.draw(this, listening, ringAlpha().coerceIn(0f, 1f))
                }
            },
        )
    }
}

/**
 * Where [bpm] sits on the dial, in degrees clockwise from straight up: −150 at 30 BPM, +150 at 250.
 * Tempos outside 30–250 are kept at the ends.
 */
fun dialDegrees(bpm: Int): Float = dialDegrees(bpm.toFloat())

/** [dialDegrees] for a tempo between whole ones (the dial gliding to a new tempo). */
fun dialDegrees(bpm: Float): Float {
    val clamped = bpm.coerceIn(ClickTrack.MIN_BPM.toFloat(), ClickTrack.MAX_BPM.toFloat())
    return -DIAL_HALF_SWEEP + (clamped - ClickTrack.MIN_BPM) / BPM_SPAN * 2f * DIAL_HALF_SWEEP
}

/**
 * The tempo a touch at ([dx], [dy]) from the dial's middle points to (y down, as on the screen),
 * rounded to whole BPM. A touch in the gap at the bottom keeps to the end nearer [current], so a drag
 * past an end never jumps across to the other one.
 */
fun bpmAtTouch(dx: Float, dy: Float, current: Int): Int {
    var degrees = (atan2(dx.toDouble(), -dy.toDouble()) * 180.0 / PI).toFloat()
    if (abs(degrees) > DIAL_HALF_SWEEP) {
        degrees = if (current > (ClickTrack.MIN_BPM + ClickTrack.MAX_BPM) / 2) DIAL_HALF_SWEEP else -DIAL_HALF_SWEEP
    }
    val bpm = ClickTrack.MIN_BPM + (degrees + DIAL_HALF_SWEEP) / (2f * DIAL_HALF_SWEEP) * BPM_SPAN
    return ClickTrack.clampBpm(bpm.roundToInt())
}

/**
 * Whether a touch at ([dx], [dy]) from the middle of a dial [width] wide lands on its ring: from
 * [RING_INNER_UNITS] out to the dial's edge, so the arc, its ticks and numbers and a finger's width
 * inside them, but not the tempo in the middle. Only such a touch starts a drag.
 */
fun isOnDialRing(dx: Float, dy: Float, width: Float): Boolean {
    val u = width / DIAL_UNITS
    val squared = dx * dx + dy * dy
    val inner = RING_INNER_UNITS * u
    val outer = DIAL_UNITS / 2f * u
    return squared >= inner * inner && squared <= outer * outer
}

/**
 * Whether a drag that began on the ring follows a finger at ([dx], [dy]) from the middle of a dial
 * [width] wide: anywhere but the middle ([RING_INNER_UNITS]), where the angle swings wildly with
 * every pixel. Past the dial's edge it still follows, as a knob does.
 */
fun isClearOfDialMiddle(dx: Float, dy: Float, width: Float): Boolean {
    val inner = RING_INNER_UNITS * width / DIAL_UNITS
    return dx * dx + dy * dy >= inner * inner
}

/**
 * Touches this far from the middle and further out are on the ring: 20 units inside the arc, just
 * clear of the tempo's figures and marking at their widest (≈ 52u across and 53u up or down).
 */
private const val RING_INNER_UNITS = 74f

/** The dial reaches this many degrees each way from straight up. */
private const val DIAL_HALF_SWEEP = 150f
private const val BPM_SPAN = (ClickTrack.MAX_BPM - ClickTrack.MIN_BPM).toFloat()

/** The dial is this many units wide (and high). */
private const val DIAL_UNITS = 288f

/** The arc's radius; the ticks start [TICK_OUT_UNITS] outside it, the numbers [NUMBER_GAP_UNITS] beyond their tick. */
private const val ARC_UNITS = 94f
private const val TICK_OUT_UNITS = 6f
private const val NUMBER_GAP_UNITS = 3f

/** The calm field's radius: it fades out just inside the dial's square. */
private const val FIELD_UNITS = 142f

/** The needle's pivot sits this far inside the arc and it is this long, so its tip meets the ticks. */
private const val NEEDLE_IN_UNITS = 16f
private const val NEEDLE_UNITS = 22f

/** The beat's glow: this radius, and this opacity at its middle just after a downbeat. */
private const val GLOW_UNITS = 90f
private const val GLOW_DARK_ALPHA = 0.30f
private const val GLOW_LIGHT_ALPHA = 0.16f

/** The found tempo's glow (Phase 27): a little wider than the beat's, and this opacity at its peak. */
private const val FOUND_GLOW_UNITS = 100f
private const val FOUND_GLOW_DARK_ALPHA = 0.32f
private const val FOUND_GLOW_LIGHT_ALPHA = 0.18f

/** The listening ring: 5.5 units across its line's middle, a 1.8 unit line in ink at 75 % over a 6 unit disc of the background. */
private const val RING_UNITS = 5.5f
private const val RING_LINE_UNITS = 1.8f
private const val RING_DISC_UNITS = 6f
private const val RING_INK = 0.75f

/** A tick every 10 BPM; major ticks (and numbers) at 30 and every 50. */
private const val TICK_STEP = 10
private const val MAJOR_STEP = 50

private const val DEGREES_PER_RADIAN = (180.0 / PI).toFloat()

/** The dial's middle, unit and arc radius in a square of [size]. */
internal class DialShape(size: Size) {
    val u = size.width / DIAL_UNITS
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = ARC_UNITS * u

    /** The point [radius] from the middle at [bpm]'s angle, into [out] (x, y), with the unit direction outwards (dx, dy). */
    fun at(bpm: Float, radius: Float, out: FloatArray) {
        val a = (-DIAL_HALF_SWEEP + (bpm - ClickTrack.MIN_BPM) / BPM_SPAN * 2f * DIAL_HALF_SWEEP) / DEGREES_PER_RADIAN
        out[2] = sin(a)
        out[3] = -cos(a)
        out[0] = cx + radius * out[2]
        out[1] = cy + radius * out[3]
    }
}

/**
 * While listening: a small hollow ring on the arc at the tempo the detector is settling on, quiet
 * beside the needle, which keeps the current tempo until a found one is used. Allocates nothing to draw.
 */
private class ListenRing(private val dial: DialShape, roles: MeterRoles) {
    private val at = FloatArray(4)
    private val disc = roles.background
    private val ink = roles.ink
    private val line = Stroke(RING_LINE_UNITS * dial.u)

    fun draw(scope: DrawScope, bpm: Float, alpha: Float) = with(scope) {
        if (alpha <= 0f) return@with
        dial.at(bpm.coerceIn(ClickTrack.MIN_BPM.toFloat(), ClickTrack.MAX_BPM.toFloat()), dial.r, at)
        val centre = Offset(at[0], at[1])
        drawCircle(disc, RING_DISC_UNITS * dial.u, centre, alpha = alpha)
        drawCircle(ink, RING_UNITS * dial.u, centre, alpha = RING_INK * alpha, style = line)
    }
}

/**
 * The dial's arc in the style's finish (the mock-up's `trackArc`), unlit over the whole range and lit
 * in the accent up to the tempo: a line (lit thicker, with round ends), a railway of two hairlines, a
 * broad band, a dashed line or a row of dots. Strokes and dot places are made once; drawing allocates
 * nothing.
 */
private class DialTrack(private val dial: DialShape, roles: MeterRoles) {
    private val kind = roles.finish.track
    private val u = dial.u
    private val radius = if (kind == TrackKind.BAND) dial.r - 2f * u else dial.r
    private val inner = dial.r - 6f * u
    private val startAngle = -90f - DIAL_HALF_SWEEP
    private val unlitColor = when (kind) {
        TrackKind.LINE, TrackKind.BAND -> roles.track
        TrackKind.RAIL -> roles.ink.copy(alpha = 0.4f)
        TrackKind.DASHED -> roles.accent.copy(alpha = 0.3f)
        TrackKind.DOTS -> roles.accent.copy(alpha = 0.35f)
    }
    private val litColor = if (kind == TrackKind.BAND) roles.accent.copy(alpha = 0.9f) else roles.accent
    private val unlitStroke = when (kind) {
        TrackKind.LINE -> Stroke(1.8f * u)
        TrackKind.RAIL -> Stroke(0.8f * u)
        TrackKind.BAND -> Stroke(6f * u)
        TrackKind.DASHED -> Stroke(2f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f * u, 3.5f * u)))
        TrackKind.DOTS -> Stroke(u)
    }
    private val litStroke = when (kind) {
        TrackKind.LINE -> Stroke(2.6f * u, cap = StrokeCap.Round)
        TrackKind.RAIL -> Stroke(1.5f * u)
        else -> unlitStroke
    }
    private val dots = FloatArray(if (kind == TrackKind.DOTS) DOT_COUNT * 2 else 0).also {
        val at = FloatArray(4)
        for (i in 0 until it.size / 2) {
            dial.at(ClickTrack.MIN_BPM + i * DOT_STEP, radius, at)
            it[i * 2] = at[0]
            it[i * 2 + 1] = at[1]
        }
    }

    fun drawUnlit(scope: DrawScope) = with(scope) {
        when (kind) {
            TrackKind.DOTS -> for (i in 0 until DOT_COUNT) drawCircle(unlitColor, 1.4f * u, Offset(dots[i * 2], dots[i * 2 + 1]))
            TrackKind.RAIL -> {
                arc(radius, 2f * DIAL_HALF_SWEEP, unlitColor, unlitStroke)
                arc(inner, 2f * DIAL_HALF_SWEEP, unlitColor, unlitStroke)
            }
            else -> arc(radius, 2f * DIAL_HALF_SWEEP, unlitColor, unlitStroke)
        }
    }

    fun drawLit(scope: DrawScope, bpm: Float) = with(scope) {
        val clamped = bpm.coerceIn(ClickTrack.MIN_BPM.toFloat(), ClickTrack.MAX_BPM.toFloat())
        if (clamped <= ClickTrack.MIN_BPM) return@with
        val sweep = dialDegrees(clamped) + DIAL_HALF_SWEEP
        when (kind) {
            TrackKind.DOTS -> {
                val last = ((clamped - ClickTrack.MIN_BPM) / DOT_STEP).toInt()
                for (i in 0..last.coerceAtMost(DOT_COUNT - 1)) drawCircle(litColor, 1.9f * u, Offset(dots[i * 2], dots[i * 2 + 1]))
            }
            TrackKind.RAIL -> {
                arc(radius, sweep, litColor, litStroke)
                arc(inner, sweep, litColor, litStroke)
            }
            else -> arc(radius, sweep, litColor, litStroke)
        }
    }

    private fun DrawScope.arc(r: Float, sweep: Float, color: Color, stroke: Stroke) {
        drawArc(color, startAngle, sweep, false, Offset(dial.cx - r, dial.cy - r), Size(r * 2f, r * 2f), style = stroke)
    }

    private companion object {
        /** A dot every 2.5 BPM, as the meter's dotted arc has one every 2.5 cents. */
        const val DOT_STEP = 2.5f
        val DOT_COUNT = (BPM_SPAN / DOT_STEP).toInt() + 1
    }
}

/**
 * The dial's scale with the meter's rules (docs/architecture/ui-meter-and-styles.md, Meter art language): a tick every
 * 10 BPM starting [TICK_OUT_UNITS] outside the arc and pointing out, major ones (30 and every 50)
 * 13u × 2.1u in ink, minor ones 7u × 1.3u at 50 %, in the style's caps, drawn by hand in Ancient;
 * the major ticks' tempos in the style's number face at 12.5u, ink at 80 %, set
 * [NUMBER_GAP_UNITS] clear of the tick's end whichever way it points.
 */
private class DialScale(dial: DialShape, roles: MeterRoles, density: Density, measure: (String, TextStyle) -> TextLayoutResult) {
    private val count = (ClickTrack.MAX_BPM - ClickTrack.MIN_BPM) / TICK_STEP + 1
    private val ticks = FloatArray(count * 5)
    private val major = BooleanArray(count)
    private val cap = roles.finish.caps
    private val ink = roles.ink
    private val minorInk = roles.ink.copy(alpha = MINOR_TICK_INK)
    private val numberInk = roles.ink.copy(alpha = SCALE_NUMBER_INK)
    private val numbers = ArrayList<TextLayoutResult>()
    private val numberAt = ArrayList<Offset>()

    init {
        val u = dial.u
        val at = FloatArray(4)
        val style = TextStyle(
            fontFamily = roles.numbers,
            fontSize = with(density) { (NUMBER_SIZE_UNITS * roles.numberScale * u).toSp() },
            fontFeatureSettings = LINING_FIGURES,
        )
        for (i in 0 until count) {
            val bpm = ClickTrack.MIN_BPM + i * TICK_STEP
            val isMajor = bpm == ClickTrack.MIN_BPM || bpm % MAJOR_STEP == 0
            dial.at(bpm.toFloat(), dial.r + TICK_OUT_UNITS * u, at)
            val jitter = if (roles.finish.jitter) handJitter(bpm.toFloat()) else 0f
            val length = (if (isMajor) MAJOR_TICK_UNITS else MINOR_TICK_UNITS) * u * (1f + JITTER_LENGTH * jitter)
            val width = (if (isMajor) MAJOR_TICK_WIDTH else MINOR_TICK_WIDTH) * u * (1f + JITTER_WIDTH * jitter)
            val turnCos = cos(JITTER_TURN * jitter)
            val turnSin = sin(JITTER_TURN * jitter)
            val dx = at[2] * turnCos - at[3] * turnSin
            val dy = at[2] * turnSin + at[3] * turnCos
            ticks[i * 5] = at[0]
            ticks[i * 5 + 1] = at[1]
            ticks[i * 5 + 2] = at[0] + dx * length
            ticks[i * 5 + 3] = at[1] + dy * length
            ticks[i * 5 + 4] = width
            major[i] = isMajor
            if (isMajor) {
                val text = measure(roles.digits(bpm), style)
                numbers += text
                // Out from the tick's end by a gap, plus the half of the number's box that faces the
                // tick, so "200" beside a sideways tick keeps as clear of it as "150" above an upright one.
                val out = length + NUMBER_GAP_UNITS * u + abs(at[2]) * text.size.width / 2f + abs(at[3]) * text.size.height / 2f
                numberAt += Offset(
                    at[0] + at[2] * out - text.size.width / 2f,
                    at[1] + at[3] * out - text.size.height / 2f,
                )
            }
        }
    }

    fun draw(scope: DrawScope) = with(scope) {
        for (i in 0 until count) {
            drawLine(
                if (major[i]) ink else minorInk,
                Offset(ticks[i * 5], ticks[i * 5 + 1]),
                Offset(ticks[i * 5 + 2], ticks[i * 5 + 3]),
                ticks[i * 5 + 4],
                cap,
            )
        }
        for (k in numbers.indices) drawText(numbers[k], color = numberInk, topLeft = numberAt[k])
    }
}
