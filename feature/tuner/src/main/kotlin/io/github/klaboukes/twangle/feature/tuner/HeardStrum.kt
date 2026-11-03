package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import io.github.klaboukes.twangle.core.dsp.strum.StrumKind
import io.github.klaboukes.twangle.core.dsp.strum.StrumVerdict
import io.github.klaboukes.twangle.core.dsp.strum.StrumWatcher
import io.github.klaboukes.twangle.core.settings.TunerMode
import kotlin.math.roundToInt

/**
 * What the tuner heard besides one string (2.0, Phase 74), shown without a mode: nothing, a strum of the open
 * strings with each string's verdict, or a fretted chord with its name. [HeardStrumInterpreter] keeps it until
 * something else is decided (a single string plucked after a strum leaves it shown, but no longer ringing: its
 * phase done, its chord faded); [HeardShown] decides how long the tuner shows it. [strum] is the count of the sudden
 * sound whose readings are shown, so a later strum (a higher count) is told from the same one ringing on.
 */
sealed interface HeardStrum {
    /** No strum heard since listening started, or since the tuning, the A4 or the mode changed. */
    data object None : HeardStrum

    /** A strum of the open strings, read as 1.1's strum check read it ([StrumCheckInterpreter]). */
    data class OpenStrings(val check: TunerUiState.StrumCheck, val strum: Int = 0) : HeardStrum

    /** A fretted chord, named as 1.1's chord name named it ([ChordNameInterpreter]). */
    data class FrettedChord(val name: TunerUiState.ChordName, val strum: Int = 0) : HeardStrum
}

/** The count of the sound whose readings [this] shows; 0 for nothing. */
val HeardStrum.strumCount: Int
    get() = when (this) {
        HeardStrum.None -> 0
        is HeardStrum.OpenStrings -> strum
        is HeardStrum.FrettedChord -> strum
    }

/**
 * Whether [this] still rings as the tuner shows it: a strum of the open strings being read ([StrumPhase.RINGING]),
 * or a chord named from the strum that still rings (not faded). While it does, the tuner's own note is set aside
 * ([HeardShown.setsAside]): it would be one of the strum's notes.
 */
val HeardStrum.rings: Boolean
    get() = when (this) {
        HeardStrum.None -> false
        is HeardStrum.OpenStrings -> check.phase == StrumPhase.RINGING && check.strings.isNotEmpty()
        is HeardStrum.FrettedChord -> name.phase == ChordPhase.NAMED && name.chord != null && !name.faded
    }

/**
 * What the strum and chord analysis beside the tuner listens for ([PitchPipeline.listen]); asked for every
 * microphone chunk, so none of it may allocate.
 */
interface StrumHearing {
    /** The concert pitch notes are placed at, or NaN while nothing is analysed (the 12th-fret check is on, a tone sounds, both brakes on). */
    fun a4Hz(): Float

    /** The open strings' frequencies in the tuning's order, or null while strums of open strings aren't checked. */
    fun openStrings(): FloatArray?

    /** Whether fretted chords are named. */
    fun namesChords(): Boolean

    /** Whether every sudden sound is a strum of the [openStrings], not decided (the strum check's button: the player said one comes). */
    fun everySoundIsAStrum(): Boolean = false

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
                    shown = HeardStrum.OpenStrings(check, reading.strumCount)
                }
            }
            chord != null -> {
                val name = chords.interpret(chord, options)
                val current = shown
                if (current !is HeardStrum.OpenStrings || name.chord != null || name.phase == ChordPhase.UNCLEAR) {
                    if (current is HeardStrum.OpenStrings) strings.waiting(options)
                    shown = HeardStrum.FrettedChord(name, reading.strumCount)
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
        when (val current = shown) {
            is HeardStrum.OpenStrings -> {
                shown = HeardStrum.OpenStrings(strings.interpret(StrumReading(reading.strumCount, ringing, readingsSinceStrum = 0), options), current.strum)
                if (reading.kind == StrumKind.CHORD) chords.interpret(ChordReading(reading.seconds, reading.strumCount, ringing), options)
            }
            is HeardStrum.FrettedChord -> {
                shown = HeardStrum.FrettedChord(chords.interpret(ChordReading(reading.seconds, reading.strumCount, ringing), options), current.strum)
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
 * How long the tuner shows what it heard besides one string, and how it sits with the tuner's own reading (2.0,
 * Phase 74; docs/architecture/ui-tuner.md, *The strum check*). Fed on the DSP thread with every
 * [HeardStrum] the [HeardStrumInterpreter] makes ([heard]) and every state of the tuner's own ([tuner], one a hop,
 * [frameSeconds] apart), it keeps [shown]:
 * - **While a strum rings** ([rings]: a strum of the open strings being read, a named chord still ringing) the
 *   tuner's own note is set aside ([setsAside]): the screen shows the strum, not one of its notes as a string.
 * - **The marks follow the strings tuned after the strum:** once the tuner reads one of the strum's strings on its
 *   own, that string's verdict becomes the tuner's (in tune once [TunerUiState.Detected.inTune], else the way its
 *   steady [TunerHint] points), so "4 of 6 in tune" counts up as the strings are tuned one by one, and a string the
 *   strum found in tune but now played off says so. The others keep the strum's verdicts.
 * - **A chord gives way to a note:** once its strum no longer rings, a note read for [CHORD_GIVES_WAY_SECONDS] (a
 *   string plucked, the chord's last note left) takes the ruler's place back and the chord goes.
 * - **Everything goes after a quiet spell:** [QUIET_SECONDS] in which the tuner hears no note and no sudden sound
 *   (the phone put down, the player gone) clear the marks, the count and the chord.
 * A strum or a chord gone this way comes back only with a new strum (a higher [HeardStrum.strumCount]); the
 * interpreter's [HeardStrum.None] (the tuning, the A4 or the mode changed, a brake, the 12th-fret check) clears
 * everything at once. Allocates only when what is shown changes. Not thread-safe; one per listening session.
 */
class HeardShown(private val frameSeconds: Float) {
    /** What the tuner shows now. */
    var shown: HeardStrum = HeardStrum.None
        private set

    /** The tuner's own note is set aside: what is shown rings ([rings]). */
    val setsAside: Boolean get() = shown.rings

    private var heard: HeardStrum = HeardStrum.None

    /** Sounds up to this count are no longer shown (0: none gone). */
    private var gone = 0

    /** The tuner's own verdicts on the strum's strings since it was read (null: the strum's), and that strum's count. */
    private val own = arrayOfNulls<StrumString>(MAX_STRINGS)
    private var ownStrum = 0

    private var quietFrames = 0
    private var noteFrames = 0

    /** Nothing shown, as when listening starts. */
    fun reset(): HeardStrum {
        heard = HeardStrum.None
        shown = HeardStrum.None
        gone = 0
        own.fill(null)
        ownStrum = 0
        quietFrames = 0
        noteFrames = 0
        return shown
    }

    /** The interpreter made [heard] of a step of the analysis (each one is a sound heard, or a reading of one). */
    fun heard(heard: HeardStrum): HeardStrum {
        if (heard === HeardStrum.None) return reset()
        if (heard.strumCount != this.heard.strumCount) noteFrames = 0
        this.heard = heard
        quietFrames = 0
        if (heard.strumCount != ownStrum || heard !is HeardStrum.OpenStrings) {
            own.fill(null)
            ownStrum = heard.strumCount
        }
        show()
        return shown
    }

    /** One state of the tuner's own, before it is set aside ([setsAside]); errors and the permission prompt are no reading. */
    fun tuner(state: TunerUiState): HeardStrum {
        val current = shown
        if (current === HeardStrum.None) return current
        when (state) {
            is TunerUiState.Detected -> {
                if (setsAside) return current
                quietFrames = 0
                noteFrames++
                when (current) {
                    is HeardStrum.OpenStrings -> followString(state)
                    is HeardStrum.FrettedChord ->
                        if (current.name.chord != null && noteFrames * frameSeconds >= CHORD_GIVES_WAY_SECONDS) goes(current)
                    HeardStrum.None -> Unit
                }
            }
            is TunerUiState.Listening -> {
                noteFrames = 0
                if (!setsAside && ++quietFrames * frameSeconds >= QUIET_SECONDS) goes(current)
            }
            else -> Unit
        }
        return shown
    }

    // The string the tuner reads on its own takes its verdict.
    private fun followString(state: TunerUiState.Detected) {
        val string = state.targetString ?: return
        if (state.mode != TunerMode.AUTO || string >= MAX_STRINGS) return
        val verdict = when {
            state.inTune -> StrumVerdict.IN_TUNE
            state.hint == TunerHint.TUNE_UP -> StrumVerdict.FLAT
            state.hint == TunerHint.TUNE_DOWN -> StrumVerdict.SHARP
            else -> return
        }
        if (own[string]?.verdict == verdict) return
        own[string] = StrumString(verdict, cents = state.cents.roundToInt().toFloat())
        show()
    }

    private fun goes(sound: HeardStrum) {
        gone = sound.strumCount
        own.fill(null)
        shown = HeardStrum.None
    }

    private fun show() {
        val heard = heard
        shown = when {
            heard.strumCount <= gone -> HeardStrum.None
            heard is HeardStrum.OpenStrings && own.any { it != null } -> {
                val strings = heard.check.strings.mapIndexed { i, string -> own.getOrNull(i) ?: string }
                heard.copy(check = heard.check.copy(strings = strings))
            }
            else -> heard
        }
    }

    companion object {
        /** A note read this long after a chord stopped ringing takes the ruler's place back. */
        const val CHORD_GIVES_WAY_SECONDS = 0.25f

        /** This long without a note or a sound clears what is shown. */
        const val QUIET_SECONDS = 30f

        /** Strings followed at most (the 12-string's twelve; a strum's tuning has fewer). */
        private const val MAX_STRINGS = 12
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
    private var everySound = false
    private var range: PitchRange? = null
    private var base = 0

    /** Whether the analysis would listen for something else now (also before it first started). */
    fun changed(strings: FloatArray?, a4Hz: Float, names: Boolean, range: PitchRange, everySound: Boolean = false): Boolean {
        val before = this.strings
        val sameStrings = if (strings == null || before == null) strings == before else strings === before || strings.contentEquals(before)
        return !sameStrings || a4Hz != this.a4Hz || names != this.names || range != this.range || everySound != this.everySound
    }

    /** [watcher] is about to start for these; its strums so far are counted on. */
    fun started(watcher: StrumWatcher, strings: FloatArray?, a4Hz: Float, names: Boolean, range: PitchRange, everySound: Boolean = false) {
        base += watcher.strumCount
        this.strings = strings
        this.a4Hz = a4Hz
        this.names = names
        this.range = range
        this.everySound = everySound
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
