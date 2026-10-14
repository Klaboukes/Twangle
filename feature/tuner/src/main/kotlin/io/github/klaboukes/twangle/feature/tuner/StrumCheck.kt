package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import io.github.klaboukes.twangle.core.dsp.strum.StrumDetector
import io.github.klaboukes.twangle.core.dsp.strum.StrumListener
import io.github.klaboukes.twangle.core.dsp.strum.StrumResult
import io.github.klaboukes.twangle.core.dsp.strum.StrumVerdict
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import kotlin.math.roundToInt

/** Where the strum check is: see [TunerUiState.StrumCheck]. */
enum class StrumPhase {
    /** No strum heard yet (or the tuning, the A4 or a reference tone started it over). */
    WAITING,

    /** A strum was heard; its first reading comes about 0.4 s after it. Earlier readings, if any, are of the strum before. */
    HEARING,

    /** The strings ring and are read every 0.1 s. */
    RINGING,

    /** The strings have died away; the last reading stays until the next strum. */
    DONE,
}

/**
 * One string in the strum check: its [verdict], how far off it is in whole [cents] (0 when not
 * heard), and whether the reading is [sure]. A string whose every partial lies on another string's
 * (B3 and E4 in standard tuning, the upper string of an octave pair) is read from merged peaks
 * ([io.github.klaboukes.twangle.core.dsp.strum.StrumResult.shadowed]), so it is less sure.
 */
data class StrumString(
    val verdict: StrumVerdict,
    val cents: Float = 0f,
    val sure: Boolean = true,
)

/** What the pipeline hands on: a tuner reading per hop, or a step of the strum check. */
sealed interface PipelineReading

/**
 * A step of the strum check, from [PitchPipeline.listen]: when strum check starts (or starts over),
 * when a strum is heard, when its strings stop ringing, and with each reading of the strings.
 */
data class StrumReading(
    /** Strums heard since the strum check (re)started; 0 before the first. */
    val strumCount: Int,
    /** Whether the latest strum still rings. */
    val ringing: Boolean,
    /** Readings of the latest strum so far, this one included. */
    val readingsSinceStrum: Int,
    /** This reading's strings, in the tuning's order; null when only the status changed. */
    val strings: List<StrumStringReading>? = null,
) : PipelineReading

/** One string of a [StrumReading]: cents from its open note (NaN when not heard), and the detector's flags. */
data class StrumStringReading(val cents: Float, val present: Boolean, val shadowed: Boolean)

/**
 * Turns [StrumReading]s into [TunerUiState.StrumCheck] states: the [StrumPhase], and each string's
 * [StrumVerdict] at the tolerance of [TunerOptions.inTunePrecision] (5 cents Relaxed, 2 Precise; a
 * less sure string gets [StrumVerdict.SHADOWED_MARGIN_CENTS] more). Within one strum a string that
 * was in tune stays so a little further ([StrumVerdict.RELEASE_CENTS]), so readings every 0.1 s
 * don't flicker. A new strum keeps the last strum's strings on screen (faded by the view) until its
 * own first reading. Not thread-safe; one per listening session.
 */
class StrumCheckInterpreter {
    private var strumCount = 0
    private var strings: List<StrumString> = emptyList()
    private var phase = StrumPhase.WAITING

    /** The state shown before anything is heard, or after strum check starts over. */
    fun waiting(options: TunerOptions): TunerUiState.StrumCheck {
        strumCount = 0
        strings = emptyList()
        phase = StrumPhase.WAITING
        return shown(options)
    }

    /** The last state again, for [options] that may have changed (e.g. the notation). */
    fun shown(options: TunerOptions): TunerUiState.StrumCheck = TunerUiState.StrumCheck(
        tuning = options.tuning,
        notation = options.notation,
        phase = phase,
        strings = strings,
        toleranceCents = toleranceFor(options.inTunePrecision),
    )

    fun interpret(reading: StrumReading, options: TunerOptions): TunerUiState.StrumCheck {
        if (reading.strumCount == 0) return waiting(options)
        val newStrum = reading.strumCount != strumCount
        strumCount = reading.strumCount
        val readings = reading.strings
        if (readings != null) {
            val tolerance = toleranceFor(options.inTunePrecision)
            // The same strum's previous verdicts hold "in tune" a little longer.
            val previous = if (newStrum || readings.size != strings.size) null else strings
            strings = readings.mapIndexed { i, string ->
                StrumString(
                    verdict = StrumVerdict.of(string.cents, string.present, string.shadowed, tolerance, previous?.get(i)?.verdict),
                    cents = if (string.present && !string.cents.isNaN()) string.cents.roundToInt().toFloat() else 0f,
                    sure = !string.shadowed,
                )
            }
        }
        phase = when {
            readings != null -> if (reading.ringing) StrumPhase.RINGING else StrumPhase.DONE
            reading.readingsSinceStrum == 0 && reading.ringing -> StrumPhase.HEARING
            // Too short to read: what was shown stays, or else it waits for a proper strum.
            reading.readingsSinceStrum == 0 -> if (strings.isEmpty()) StrumPhase.WAITING else StrumPhase.DONE
            reading.ringing -> StrumPhase.RINGING
            else -> StrumPhase.DONE
        }
        return shown(options)
    }

    companion object {
        /** The strum check's in-tune tolerance for [precision]: the tuner's own 5 or 2 cents. */
        fun toleranceFor(precision: InTunePrecision): Float = when (precision) {
            InTunePrecision.RELAXED -> StrumVerdict.DEFAULT_TOLERANCE_CENTS
            InTunePrecision.PRECISE -> InTuneDetector.PRECISE_ENTER_CENTS
        }
    }
}

/**
 * The strum check's part of [PitchPipeline]: a [StrumListener] and [StrumDetector] for one range,
 * made the first time strum check starts (the detector's buffers are large) and again only for
 * another range. [start] begins afresh; after each [push], [next] gives the reading to hand on, if
 * anything changed. Not thread-safe.
 */
internal class StrumAnalysis(private val sampleRate: Int) {
    private var listener: StrumListener? = null
    private var detector: StrumDetector? = null
    private var frame = FloatArray(0)
    private val result = StrumResult(StrumDetector.MAX_STRINGS)
    private var expectedHz = FloatArray(0)
    private var stringCount = 0
    private var lastStrumCount = 0
    private var lastRinging = false

    /** The expected strings this analysis listens for (the array [start] was given). */
    val expected: FloatArray get() = expectedHz

    /** Starts listening for strums of strings at [hz] (Hz, in the tuning's order) in [range]. */
    fun start(hz: FloatArray, range: PitchRange): StrumReading {
        val current = listener
        if (current == null || current.range != range) {
            val made = StrumListener(sampleRate, range)
            listener = made
            detector = StrumDetector(sampleRate, made.frameLength)
            frame = FloatArray(made.frameLength)
        } else {
            current.reset()
        }
        expectedHz = hz
        stringCount = minOf(hz.size, StrumDetector.MAX_STRINGS)
        lastStrumCount = 0
        lastRinging = false
        return StrumReading(strumCount = 0, ringing = false, readingsSinceStrum = 0)
    }

    /** Takes samples like [StrumListener.push]: stops when a frame is ready, so call [next] after each push. */
    fun push(samples: FloatArray, offset: Int, length: Int): Int = listener!!.push(samples, offset, length)

    /** A reading of the strings when a frame was ready, a status when a strum began or stopped ringing, else null. */
    fun next(): StrumReading? {
        val listener = listener!!
        if (listener.isFrameReady) {
            listener.readFrame(frame)
            detector!!.detect(frame, expectedHz, stringCount, result)
            lastStrumCount = listener.strumCount
            lastRinging = listener.isRinging
            val strings = List(expectedHz.size) { i ->
                if (i < stringCount) {
                    StrumStringReading(result.cents[i], result.present[i], result.shadowed[i])
                } else {
                    StrumStringReading(Float.NaN, present = false, shadowed = false)
                }
            }
            return StrumReading(listener.strumCount, listener.isRinging, listener.readingsSinceStrum, strings)
        }
        if (listener.strumCount == lastStrumCount && listener.isRinging == lastRinging) return null
        lastStrumCount = listener.strumCount
        lastRinging = listener.isRinging
        return StrumReading(listener.strumCount, listener.isRinging, listener.readingsSinceStrum)
    }
}
