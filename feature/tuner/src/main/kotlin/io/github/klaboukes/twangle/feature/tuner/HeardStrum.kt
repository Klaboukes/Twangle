package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import io.github.klaboukes.twangle.core.dsp.strum.StrumKind
import io.github.klaboukes.twangle.core.dsp.strum.StrumWatcher

/**
 * What the tuner heard besides one string (2.0, Phase 74), shown without a mode: nothing, a strum of the open
 * strings with each string's verdict, or a fretted chord with its name. Kept until something else is decided:
 * a single string plucked after a strum leaves it shown, but no longer ringing (its phase done, its chord faded).
 */
sealed interface HeardStrum {
    /** No strum heard since listening started, or since the tuning, the A4 or the mode changed. */
    data object None : HeardStrum

    /** A strum of the open strings, read as 1.1's strum check reads it ([StrumCheckInterpreter]). */
    data class OpenStrings(val check: TunerUiState.StrumCheck) : HeardStrum

    /** A fretted chord, named as 1.1's chord name names it ([ChordNameInterpreter]). */
    data class FrettedChord(val name: TunerUiState.ChordName) : HeardStrum
}

/**
 * What the strum and chord analysis beside the tuner listens for ([PitchPipeline.listen]); asked for every
 * microphone chunk, so none of it may allocate.
 */
interface StrumHearing {
    /** The concert pitch notes are placed at, or NaN while nothing is analysed (a tool is on, a tone sounds, both brakes on). */
    fun a4Hz(): Float

    /** The open strings' frequencies in the tuning's order, or null while strums of open strings aren't checked. */
    fun openStrings(): FloatArray?

    /** Whether fretted chords are named. */
    fun namesChords(): Boolean

    /** The range the analysis' frames are sized for (the tuner's). */
    fun range(): PitchRange
}

/**
 * A step of the strum and chord analysis beside the tuner, from [PitchPipeline.listen]: [strumCount] 0 when it
 * starts afresh; else the latest sudden sound's count, whether it [ringing], its [kind] (null while undecided),
 * and with a reading of it either [strum] (a strum of the open strings) or [chord] (a fretted chord).
 */
data class HeardReading(
    val strumCount: Int,
    val ringing: Boolean = false,
    val kind: StrumKind? = null,
    val seconds: Double = 0.0,
    val strum: StrumReading? = null,
    val chord: ChordReading? = null,
) : PipelineReading {
    companion object {
        /** What the analysis listens for changed: nothing heard yet. */
        val FRESH = HeardReading(strumCount = 0)
    }
}

/**
 * Turns [HeardReading]s into the [HeardStrum] shown, through 1.1's two interpreters, so a strum is weighed and a
 * chord settled the way the tools did. A strum of open strings shows once a reading hears at least half the
 * strings; a chord replaces nothing until it is named (or found unclear), but shows from the first reading when
 * nothing else is shown. Not thread-safe; one per listening session.
 */
class HeardStrumInterpreter {
    private val strings = StrumCheckInterpreter()
    private val chords = ChordNameInterpreter()

    /** What is shown now. */
    var shown: HeardStrum = HeardStrum.None
        private set

    /** Back to nothing heard. */
    fun reset(options: TunerOptions): HeardStrum {
        strings.waiting(options)
        chords.waiting(options)
        shown = HeardStrum.None
        return shown
    }

    fun interpret(reading: HeardReading, options: TunerOptions): HeardStrum {
        if (reading.strumCount == 0) return reset(options)
        val strum = reading.strum
        val chord = reading.chord
        when {
            strum != null -> {
                val check = strings.interpret(strum, options)
                if (check.strings.isNotEmpty()) {
                    if (shown !is HeardStrum.OpenStrings) chords.waiting(options)
                    shown = HeardStrum.OpenStrings(check)
                }
            }
            chord != null -> {
                val name = chords.interpret(chord, options)
                val current = shown
                if (current !is HeardStrum.OpenStrings || name.chord != null || name.phase == ChordPhase.UNCLEAR) {
                    if (current is HeardStrum.OpenStrings) strings.waiting(options)
                    shown = HeardStrum.FrettedChord(name)
                }
            }
            else -> status(reading, options)
        }
        return shown
    }

    // A strum began, was decided or stopped ringing: the strum or chord shown hears of it (a new sound fades it;
    // one decided as no strum doesn't ring for it).
    private fun status(reading: HeardReading, options: TunerOptions) {
        val ringing = reading.ringing && reading.kind != StrumKind.NONE
        when (shown) {
            is HeardStrum.OpenStrings -> {
                shown = HeardStrum.OpenStrings(strings.interpret(StrumReading(reading.strumCount, ringing, readingsSinceStrum = 0), options))
                if (reading.kind == StrumKind.CHORD) chords.interpret(ChordReading(reading.seconds, reading.strumCount, ringing), options)
            }
            is HeardStrum.FrettedChord -> {
                shown = HeardStrum.FrettedChord(chords.interpret(ChordReading(reading.seconds, reading.strumCount, ringing), options))
                if (reading.kind == StrumKind.OPEN_STRINGS) strings.interpret(StrumReading(reading.strumCount, ringing, readingsSinceStrum = 0), options)
            }
            HeardStrum.None -> {
                strings.interpret(StrumReading(reading.strumCount, ringing, readingsSinceStrum = 0), options)
                chords.interpret(ChordReading(reading.seconds, reading.strumCount, ringing), options)
            }
        }
    }
}

/**
 * [PitchPipeline]'s side of the analysis beside the tuner: what [StrumWatcher] was last started for, and its
 * events as [HeardReading]s. Strums are counted on across pauses, so a strum after one never reads as the same.
 * Not thread-safe.
 */
internal class HeardSteps {
    private var strings: FloatArray? = null
    private var a4Hz = Float.NaN
    private var names = false
    private var range: PitchRange? = null
    private var base = 0

    /** Whether the analysis would listen for something else now (also before it first started). */
    fun changed(strings: FloatArray?, a4Hz: Float, names: Boolean, range: PitchRange): Boolean {
        val before = this.strings
        val sameStrings = if (strings == null || before == null) strings == before else strings === before || strings.contentEquals(before)
        return !sameStrings || a4Hz != this.a4Hz || names != this.names || range != this.range
    }

    /** [watcher] is about to start for these; its strums so far are counted on. */
    fun started(watcher: StrumWatcher, strings: FloatArray?, a4Hz: Float, names: Boolean, range: PitchRange) {
        base += watcher.strumCount
        this.strings = strings
        this.a4Hz = a4Hz
        this.names = names
        this.range = range
    }

    /** The analysis paused: the latest strum rings no longer for it (null before any strum). */
    fun paused(watcher: StrumWatcher): HeardReading? {
        val count = base + watcher.strumCount
        return if (count == 0) null else HeardReading(count, ringing = false, kind = watcher.kind ?: StrumKind.NONE, seconds = watcher.seconds)
    }

    fun step(watcher: StrumWatcher, event: StrumWatcher.Event): HeardReading {
        val count = base + watcher.strumCount
        val ringing = watcher.isRinging
        if (event != StrumWatcher.Event.READING) return HeardReading(count, ringing, watcher.kind, watcher.seconds)
        return if (watcher.kind == StrumKind.OPEN_STRINGS) {
            val result = watcher.strings
            val strings = List(watcher.stringCount) { i -> StrumStringReading(result.cents[i], result.present[i], result.shadowed[i]) }
            HeardReading(
                count, ringing, StrumKind.OPEN_STRINGS, watcher.readingSeconds,
                strum = StrumReading(count, ringing, watcher.readingsSinceStrum, strings, watcher.readingLevelDbfs),
            )
        } else {
            HeardReading(
                count, ringing, StrumKind.CHORD, watcher.readingSeconds,
                chord = ChordReading(watcher.readingSeconds, count, ringing, reading = true, chord = watcher.readingChord, bass = watcher.readingBass),
            )
        }
    }
}
