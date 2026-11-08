package io.github.klaboukes.twangle.core.designsystem.lens

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.designsystem.dial.STROBE_ROWS
import io.github.klaboukes.twangle.core.designsystem.dial.drawStrobeBand
import io.github.klaboukes.twangle.core.dsp.strobe.StrobeMotion
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.floor

/**
 * The strobe look (2.0, Phase 72; docs/design/v2-tuner-screens-preview.html, `lensStrobe`; 1.1's strobe wave
 * look): [STROBE_ROWS] bands of stripes across the lens that drift left while the note is flat and right while
 * it is sharp, faster the further off, and stand dead still within half a cent ([StrobeMotion.driftSpeed]), like
 * the striped disc of a mechanical strobe tuner. The bands' stripes get finer going down (a fifth of the lens's
 * width apart in the top band, 24 to 64 dp, halved in each band below) and each band drifts twice as fast as the
 * one above. Each band lies on the background at 55 % with 8 dp corners, the stripes Contemporary's rounded bars
 * in the reading's tone at 85 % ([LensReading.tone]), a 2 dp centre line in the ink at 60 % across them; the
 * room under the bands is the lens's ♭ and ♯. In tune the stripes take the green (no halo of their own: on the
 * phone it drew a box round each bar, and the lens's border already glows); the strobe shows no zone of its own,
 * its bands coming to rest is the cue.
 *
 * A frame loop runs only while there is a reading ([LensReading.presence] above 0), stepping a [StrobeDrift] from
 * the reading ([LensReading.cents]) and bumping a counter the drawing reads, so nothing recomposes; a frame where
 * the stripes stand still draws nothing new. Once the string dies the stripes stop where they are, muted at
 * 30 %. An "infinite" animation, so UI tests (which stop those) see the stripes still. Nothing is allocated per
 * frame.
 */
@Composable
internal fun BoxScope.StrobeLook(reading: LensReading) {
    val drift = remember(reading) { StrobeDrift() }
    val frames = remember(reading) { mutableIntStateOf(0) }
    LaunchedEffect(drift) { runStrobe(reading, drift, frames) }
    val background = MaterialTheme.colorScheme.background
    val ink = MaterialTheme.colorScheme.onSurface
    val highContrast = MaterialTheme.styleTokens.highContrast
    Spacer(
        Modifier
            .matchParentSize()
            .drawWithCache {
                val art = StrobeArt(StrobeGeometry(size.width, size.height, density), background, ink, highContrast)
                onDrawBehind {
                    // Redraws on every frame the stripes move.
                    frames.intValue
                    art.draw(this, drift, reading)
                }
            },
    )
}

/**
 * Steps [drift] on every frame while there is a reading, from the reading's cents, and bumps [frames] when the
 * stripes moved; waits, without frames, while there is none, and while the stripes stand still on the note
 * (Phase 72: the Settings card holding still in tune with Android's "Remove animations", and the tuner held in tune,
 * asked for every frame and drew none).
 */
private suspend fun runStrobe(reading: LensReading, drift: StrobeDrift, frames: MutableIntState) {
    snapshotFlow { reading.presence > 0f }.collectLatest { hearing ->
        if (!hearing) return@collectLatest
        var last = 0L
        while (true) {
            if (drift.speed == 0f && abs(reading.cents) <= StrobeMotion.STILL_CENTS) {
                snapshotFlow { reading.cents }.first { abs(it) > StrobeMotion.STILL_CENTS }
                last = 0L
            }
            withInfiniteAnimationFrameNanos { now ->
                val seconds = if (last == 0L) 0f else (now - last) / NANOS_PER_SECOND
                last = now
                if (drift.advance(seconds, reading.cents)) frames.intValue++
            }
        }
    }
}

/**
 * How the strobe's stripes drift (Phase 72): [StrobeMotion]'s speed, eased to the reading as 1.1's wave view
 * did, moving the top band [TOP_BAND_SHARE] of a stripe per second for every stripe per second the strobe asks
 * for ([StrobeMotion.speed]), and each band below [BAND_SPEEDUP] times as many of its own (halved) stripes, so
 * twice as fast across the screen. [phase] is the top band's, in its stripe periods (0..1, wrapping); a band's
 * is a whole multiple of it ([bandPhase]), so every band wraps without a jump. Plain Kotlin, JVM-tested
 * (`StrobeLookTest`); nothing allocated per step.
 */
internal class StrobeDrift {
    private val motion = StrobeMotion()

    /** Where the top band's stripes are, in its stripe periods (0..1, wrapping): grows when sharp, shrinks when flat. */
    var phase = 0f
        private set

    /** How fast the top band drifts now, in its stripe periods per second (0 within half a cent of the note). */
    val speed: Float get() = motion.speed * TOP_BAND_SHARE

    /** Moves the stripes on by [seconds] for a reading of [cents] (positive is sharp); true when they moved. */
    fun advance(seconds: Float, cents: Float): Boolean {
        motion.advance(seconds, cents)
        val step = speed * seconds.coerceIn(0f, MAX_STEP_SECONDS)
        if (step == 0f) return false
        val next = phase + step
        phase = next - floor(next)
        return true
    }

    /** Band [band]'s phase (0 the top), in its own stripe periods. */
    fun bandPhase(band: Int): Float {
        var multiple = 1f
        repeat(band) { multiple *= BAND_SPEEDUP }
        return phase * multiple
    }

    companion object {
        /** The top band moves this share of the strobe's speed in its own stripes. */
        const val TOP_BAND_SHARE = 1f / 8f

        /** Each band below moves this many times as many of its own stripes a second: twice as fast across, its stripes half as far apart. */
        const val BAND_SPEEDUP = 4f

        private const val MAX_STEP_SECONDS = 0.1f
    }
}

/**
 * Where the strobe is drawn in a lens [width] × [height] px, with [dp] px to a dp (the mock-up's `lensStrobe`):
 * [STROBE_ROWS] bands [PAD] dp in from the lens's sides and top, [GAP] dp apart, leaving [SIGN_ROOM] dp under them
 * for ♭ and ♯; the top band's stripes a fifth of the lens's width apart ([PERIOD_SHARE], at least [MIN_PERIOD] and
 * at most [MAX_PERIOD] dp), halved in each band below; the centre line [LINE_OVERHANG] dp past the bands. Pure,
 * so it is tested on the JVM (`StrobeLookTest`).
 */
internal class StrobeGeometry(val width: Float, val height: Float, private val dp: Float) {
    /** The top band's stripes, px apart. */
    val topPeriod = (width / dp * PERIOD_SHARE).coerceIn(MIN_PERIOD, MAX_PERIOD) * dp

    /** Band [band]'s stripes (0 the top), px apart: half as far in each band below. */
    fun period(band: Int): Float = topPeriod / (1 shl band)

    val left = PAD * dp
    val bandWidth = (width - 2f * PAD * dp).coerceAtLeast(0f)
    val bandHeight = ((height - 2f * PAD * dp - (STROBE_ROWS - 1) * GAP * dp - SIGN_ROOM * dp) / STROBE_ROWS).coerceAtLeast(0f)
    val corner = CORNER * dp

    /** Band [band]'s top edge (0 the top band). */
    fun bandTop(band: Int): Float = PAD * dp + band * (bandHeight + GAP * dp)

    /** The centre line, from just above the top band to just under the bottom one. */
    val centreX = width / 2f
    val lineTop = (PAD - LINE_OVERHANG) * dp
    val lineBottom = bandTop(STROBE_ROWS - 1) + bandHeight + LINE_OVERHANG * dp
    val lineWidth = LINE_WIDTH * dp

    companion object {
        const val PAD = 14f
        const val GAP = 10f
        const val SIGN_ROOM = 20f
        const val PERIOD_SHARE = 1f / 5f
        const val MIN_PERIOD = 24f
        const val MAX_PERIOD = 64f
        const val CORNER = 8f
        const val LINE_OVERHANG = 4f
        const val LINE_WIDTH = 2f
    }
}

/**
 * The strobe's pieces for one size and colours: the bands' ground and the centre line's colour. In [highContrast]
 * (Phase 86) the centre line is the ink in full and twice as wide, the stripes are in full, and each band is edged in
 * the ink, so the bands keep their shape on a pure black or white page.
 */
private class StrobeArt(private val geometry: StrobeGeometry, background: Color, ink: Color, highContrast: Boolean) {
    private val ground = background.copy(alpha = GROUND_ALPHA)
    private val line = ink.copy(alpha = if (highContrast) 1f else LINE_ALPHA)
    private val lineWidth = geometry.lineWidth * (if (highContrast) 2f else 1f)
    private val edge = if (highContrast) ink else null
    private val edgeStroke = Stroke(geometry.lineWidth)
    private val liveAlpha = if (highContrast) 1f else LIVE_ALPHA
    private val bandSize = Size(geometry.bandWidth, geometry.bandHeight)
    private val corner = CornerRadius(geometry.corner)

    fun draw(scope: DrawScope, drift: StrobeDrift, reading: LensReading) = with(scope) {
        val presence = reading.presence.coerceIn(0f, 1f)
        val stripes = reading.tone.copy(alpha = REST_ALPHA + (liveAlpha - REST_ALPHA) * presence)
        for (band in 0 until STROBE_ROWS) {
            val top = geometry.bandTop(band)
            drawRoundRect(ground, topLeft = Offset(geometry.left, top), size = bandSize, cornerRadius = corner)
            drawStrobeBand(
                left = geometry.left,
                top = top,
                width = geometry.bandWidth,
                height = geometry.bandHeight,
                period = geometry.period(band),
                phase = drift.bandPhase(band),
                color = stripes,
            )
            if (edge != null) drawRoundRect(edge, topLeft = Offset(geometry.left, top), size = bandSize, cornerRadius = corner, style = edgeStroke)
        }
        drawLine(line, Offset(geometry.centreX, geometry.lineTop), Offset(geometry.centreX, geometry.lineBottom), lineWidth)
    }

    private companion object {
        /** The bands' ground: the background at 55 %. */
        const val GROUND_ALPHA = 0.55f

        /** The centre line: the ink at 60 %. */
        const val LINE_ALPHA = 0.6f

        /** The stripes: the tone at 85 % with a reading, muted at 30 % without. */
        const val LIVE_ALPHA = 0.85f
        const val REST_ALPHA = 0.3f
    }
}

private const val NANOS_PER_SECOND = 1_000_000_000f
