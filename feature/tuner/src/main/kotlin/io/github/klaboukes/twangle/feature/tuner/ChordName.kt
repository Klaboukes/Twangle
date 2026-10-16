package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordDetector
import io.github.klaboukes.twangle.core.dsp.chord.ChordResult
import io.github.klaboukes.twangle.core.dsp.chord.ChordSettler
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.strum.StrumListener

/** What the chord name's line says: see [TunerUiState.ChordName]. */
enum class ChordPhase {
    /** Nothing to show yet: "Strum a chord". */
    WAITING,

    /** A strum is heard and hasn't been named yet: "Listening…". */
    LISTENING,

    /** A chord is shown (bright, or faded as the strum died away): "Strum again for the next". */
    NAMED,

    /** The last strum named nothing: "Not a clear chord". */
    UNCLEAR,
}

/**
 * A step of the chord name, from [PitchPipeline.listen]: when it starts (or starts over, [strumCount]
 * 0), when a strum is heard or stops ringing, with each reading of a strum ([reading], about every
 * 0.1 s while it rings: its [chord], null when it isn't clearly one, and its [bass]), and in between
 * about every 0.1 s, so time passes while nothing rings. [seconds] is the audio's own time since the
 * start.
 */
data class ChordReading(
    val seconds: Double,
    val strumCount: Int,
    val ringing: Boolean,
    val reading: Boolean = false,
    val chord: Chord? = null,
    val bass: PitchClass? = null,
) : PipelineReading

/**
 * Turns [ChordReading]s into [TunerUiState.ChordName] states, settled by a [ChordSettler] (a name
 * shows once it wins 4 of the last 6 readings, stays at least 1.5 s, fades as the strum dies away;
 * "not a clear chord" after 1.5 s without one). Not thread-safe; one per listening session.
 */
class ChordNameInterpreter {
    private val settler = ChordSettler()

    /** The state before anything is heard, or after the chord name starts over. */
    fun waiting(options: TunerOptions): TunerUiState.ChordName {
        settler.reset()
        return shown(options)
    }

    /** The last state again, for [options] that may have changed (e.g. the notation). */
    fun shown(options: TunerOptions): TunerUiState.ChordName = TunerUiState.ChordName(
        tuning = options.tuning,
        notation = options.notation,
        phase = when {
            settler.listening -> ChordPhase.LISTENING
            settler.unclear -> ChordPhase.UNCLEAR
            settler.shown != null -> ChordPhase.NAMED
            else -> ChordPhase.WAITING
        },
        chord = settler.shown,
        faded = settler.faded,
        recent = settler.recent,
    )

    fun interpret(reading: ChordReading, options: TunerOptions): TunerUiState.ChordName {
        settler.update(reading.seconds, reading.strumCount, reading.ringing, reading.reading, reading.chord, reading.bass)
        return shown(options)
    }
}

/**
 * The chord name's part of [PitchPipeline]: a [StrumListener] and a [ChordDetector], made the first
 * time the chord name starts (the detector's buffers are large). [start] begins afresh; after each
 * [push], [next] gives the reading to hand on, if any. Not thread-safe.
 */
internal class ChordAnalysis(private val sampleRate: Int) {
    private val listener = StrumListener(sampleRate)
    private val detector = ChordDetector(sampleRate, listener.frameLength)
    private val frame = FloatArray(listener.frameLength)
    private val result = ChordResult()
    private val tickSamples = (TICK_SECONDS * sampleRate).toLong()
    private var samples = 0L
    private var lastReport = 0L
    private var lastStrumCount = 0
    private var lastRinging = false

    /** Starts listening afresh. */
    fun start(): ChordReading {
        listener.reset()
        samples = 0L
        lastReport = 0L
        lastStrumCount = 0
        lastRinging = false
        return ChordReading(seconds = 0.0, strumCount = 0, ringing = false)
    }

    /** Takes samples like [StrumListener.push]: stops when a frame is ready, so call [next] after each push. */
    fun push(samples: FloatArray, offset: Int, length: Int): Int {
        val used = listener.push(samples, offset, length)
        this.samples += used
        return used
    }

    /**
     * A reading of the strum named at [a4Hz] when a frame was ready, a status when a strum began or
     * stopped ringing, or when [TICK_SECONDS] have passed since the last thing handed on; else null.
     */
    fun next(a4Hz: Float): ChordReading? {
        val seconds = samples.toDouble() / sampleRate
        if (listener.isFrameReady) {
            listener.readFrame(frame)
            val chord = detector.detect(frame, result, a4Hz)
            remember()
            return ChordReading(seconds, listener.strumCount, listener.isRinging, reading = true, chord = chord, bass = result.bass)
        }
        if (listener.strumCount == lastStrumCount && listener.isRinging == lastRinging && samples - lastReport < tickSamples) return null
        remember()
        return ChordReading(seconds, listener.strumCount, listener.isRinging)
    }

    private fun remember() {
        lastStrumCount = listener.strumCount
        lastRinging = listener.isRinging
        lastReport = samples
    }

    private companion object {
        /** Time passes for the settler at least this often while nothing rings. */
        const val TICK_SECONDS = 0.1
    }
}
