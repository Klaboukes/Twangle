package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordSettler
import io.github.klaboukes.twangle.core.dsp.music.PitchClass

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
 * A step of a fretted chord, as [HeardStrumInterpreter] hands it to [ChordNameInterpreter]: when the analysis
 * starts (or starts over, [strumCount] 0), when a strum is heard or stops ringing, and with each reading of a
 * strum ([reading], about every 0.1 s while it rings: its [chord], null when it isn't clearly one, and its
 * [bass]). [seconds] is the audio's own time since the start.
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
