package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import io.github.klaboukes.twangle.core.dsp.strum.StrumKind
import io.github.klaboukes.twangle.core.dsp.strum.StrumVerdict
import io.github.klaboukes.twangle.core.dsp.strum.StrumWatcher
import io.github.klaboukes.twangle.core.settings.TunerMode
import kotlin.math.roundToInt

/**
 * What the tuner heard besides one string (2.0, Phase 74): nothing, or a strum of the open strings with each
 * string's verdict. [HeardStrumInterpreter] keeps it until the next strum (a single string plucked after one leaves
 * it shown, its phase done); [HeardShown] decides how long the tuner shows it. [strum] is the count of the sudden
 * sound whose readings are shown, so a later strum (a higher count) is told from the same one ringing on.
 */
sealed interface HeardStrum {
    /** No strum heard since listening started, or since the tuning, the A4 or the mode changed. */
    data object None : HeardStrum

    /** A strum of the open strings, read as 1.1's strum check read it ([StrumCheckInterpreter]). */
    data class OpenStrings(val check: TunerUiState.StrumCheck, val strum: Int = 0) : HeardStrum
}

/** The count of the sound whose readings [this] shows; 0 for nothing. */
val HeardStrum.strumCount: Int
    get() = when (this) {
        HeardStrum.None -> 0
        is HeardStrum.OpenStrings -> strum
    }

/**
 * Whether [this] still rings as the tuner shows it: a strum of the open strings being read ([StrumPhase.RINGING]).
 * While it does, the tuner's own note is set aside ([HeardShown.setsAside]): it would be one of the strum's notes.
 */
val HeardStrum.rings: Boolean
    get() = when (this) {
        HeardStrum.None -> false
        is HeardStrum.OpenStrings -> check.phase == StrumPhase.RINGING && check.strings.isNotEmpty()
    }

/**
 * What the strum analysis beside the tuner listens for ([PitchPipeline.listen]); asked for every microphone chunk,
 * so none of it may allocate.
 */
interface StrumHearing {
    /** The concert pitch notes are placed at, or NaN while nothing is analysed (the strum check's button off, the 12th-fret check on, a tone sounds). */
    fun a4Hz(): Float

    /**
     * The open strings' frequencies in the tuning's order, or null while strums of open strings aren't checked. Every
     * sudden sound is a strum of them: the strum check's button said one comes.
     */
    fun openStrings(): FloatArray?

    /** The range the analysis' frames are sized for (the tuner's). */
    fun range(): PitchRange
}

/**
 * A step of the strum analysis beside the tuner, from [PitchPipeline.listen]: [strumCount] 0 when it starts afresh;
 * else the latest sudden sound's count, whether it [ringing], its [kind] (null while undecided), and with a reading
 * of a strum of the open strings its [strum].
 */
data class HeardReading(
    val strumCount: Int,
    val ringing: Boolean = false,
    val kind: StrumKind? = null,
    val seconds: Double = 0.0,
    val strum: StrumReading? = null,
) : PipelineReading {
    companion object {
        /** What the analysis listens for changed: nothing heard yet. */
        val FRESH = HeardReading(strumCount = 0)
    }
}

/**
 * Turns [HeardReading]s into the [HeardStrum] shown, through 1.1's interpreter, so a strum is weighed the way the
 * strum check did. A strum of open strings shows once a reading hears at least half the strings. Not thread-safe;
 * one per listening session.
 */
class HeardStrumInterpreter {
    private val strings = StrumCheckInterpreter()

    /** What is shown now. */
    var shown: HeardStrum = HeardStrum.None
        private set

    /** Back to nothing heard. */
    fun reset(options: TunerOptions): HeardStrum {
        strings.waiting(options)
        shown = HeardStrum.None
        return shown
    }

    fun interpret(reading: HeardReading, options: TunerOptions): HeardStrum {
        if (reading.strumCount == 0) return reset(options)
        val strum = reading.strum
        if (strum != null) {
            val check = strings.interpret(strum, options)
            if (check.strings.isNotEmpty()) shown = HeardStrum.OpenStrings(check, reading.strumCount)
        } else {
            status(reading, options)
        }
        return shown
    }

    // A strum began, was decided or stopped ringing: the strum shown hears of it (a new sound fades it; one decided
    // as no strum doesn't ring for it).
    private fun status(reading: HeardReading, options: TunerOptions) {
        val ringing = reading.ringing && reading.kind != StrumKind.NONE
        val check = strings.interpret(StrumReading(reading.strumCount, ringing, readingsSinceStrum = 0), options)
        val current = shown
        if (current is HeardStrum.OpenStrings) shown = HeardStrum.OpenStrings(check, current.strum)
    }
}

/**
 * How long the tuner shows what it heard besides one string, and how it sits with the tuner's own reading (2.0,
 * Phase 74; docs/architecture/ui-tuner.md, *The strum check*). Fed on the DSP thread with every
 * [HeardStrum] the [HeardStrumInterpreter] makes ([heard]) and every state of the tuner's own ([tuner], one a hop,
 * [frameSeconds] apart), it keeps [shown]:
 * - **While a strum rings** ([rings]: a strum of the open strings being read) the tuner's own note is set aside
 *   ([setsAside]): the screen shows the strum, not one of its notes as a string.
 * - **The marks follow the strings tuned after the strum:** once the tuner reads one of the strum's strings on its
 *   own, that string's verdict becomes the tuner's (in tune once [TunerUiState.Detected.inTune], else the way its
 *   steady [TunerHint] points), so "4 of 6 in tune" counts up as the strings are tuned one by one, and a string the
 *   strum found in tune but now played off says so. The others keep the strum's verdicts.
 * - **Everything goes after a quiet spell:** [QUIET_SECONDS] in which the tuner hears no note and no sudden sound
 *   (the phone put down, the player gone) clear the marks and the count.
 * A strum gone this way comes back only with a new strum (a higher [HeardStrum.strumCount]); the
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

    /** Nothing shown, as when listening starts. */
    fun reset(): HeardStrum {
        heard = HeardStrum.None
        shown = HeardStrum.None
        gone = 0
        own.fill(null)
        ownStrum = 0
        quietFrames = 0
        return shown
    }

    /** The interpreter made [heard] of a step of the analysis (each one is a sound heard, or a reading of one). */
    fun heard(heard: HeardStrum): HeardStrum {
        if (heard === HeardStrum.None) return reset()
        this.heard = heard
        quietFrames = 0
        if (heard.strumCount != ownStrum) {
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
                if (current is HeardStrum.OpenStrings) followString(state)
            }
            is TunerUiState.Listening -> {
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
        // The panel's column moves with the string as it is tuned: a new verdict or a new whole cent.
        val cents = state.cents.roundToInt().toFloat()
        val before = own[string]
        if (before != null && before.verdict == verdict && before.cents == cents) return
        own[string] = StrumString(verdict, cents = cents)
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
    private var range: PitchRange? = null
    private var base = 0

    /** Whether the analysis would listen for something else now (also before it first started). */
    fun changed(strings: FloatArray?, a4Hz: Float, range: PitchRange): Boolean {
        val before = this.strings
        val sameStrings = if (strings == null || before == null) strings == before else strings === before || strings.contentEquals(before)
        return !sameStrings || a4Hz != this.a4Hz || range != this.range
    }

    /** [watcher] is about to start for these; its strums so far are counted on. */
    fun started(watcher: StrumWatcher, strings: FloatArray?, a4Hz: Float, range: PitchRange) {
        base += watcher.strumCount
        this.strings = strings
        this.a4Hz = a4Hz
        this.range = range
    }

    /** The analysis paused: the latest strum rings no longer for it (null before any strum). */
    fun paused(watcher: StrumWatcher): HeardReading? {
        val count = base + watcher.strumCount
        return if (count == 0) null else HeardReading(count, ringing = false, kind = watcher.kind, seconds = watcher.seconds)
    }

    fun step(watcher: StrumWatcher, event: StrumWatcher.Event): HeardReading {
        val count = base + watcher.strumCount
        val ringing = watcher.isRinging
        if (event != StrumWatcher.Event.READING || watcher.kind != StrumKind.OPEN_STRINGS) {
            return HeardReading(count, ringing, watcher.kind, watcher.seconds)
        }
        val result = watcher.strings
        val strings = List(watcher.stringCount) { i -> StrumStringReading(result.cents[i], result.present[i], result.shadowed[i]) }
        return HeardReading(
            count, ringing, StrumKind.OPEN_STRINGS, watcher.readingSeconds,
            strum = StrumReading(count, ringing, watcher.readingsSinceStrum, strings, watcher.readingLevelDbfs),
        )
    }
}
