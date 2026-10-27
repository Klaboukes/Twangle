package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.dsp.chord.ChordInstruments
import io.github.klaboukes.twangle.core.dsp.intonation.Intonation
import io.github.klaboukes.twangle.core.dsp.intonation.IntonationInstruments
import io.github.klaboukes.twangle.core.dsp.intonation.IntonationResult
import io.github.klaboukes.twangle.core.dsp.intonation.IntonationVerdict
import io.github.klaboukes.twangle.core.dsp.intonation.SettledPitch
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.pitch.SignalGate
import io.github.klaboukes.twangle.core.dsp.smoothing.PitchSmoother
import io.github.klaboukes.twangle.core.dsp.strum.StrumInstruments
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The tools the tuner's "more tools" button gathers (docs/architecture/ui-tuner.md, *More tools*),
 * each a mode that takes the meter's and the note's place while it is on. One at a time.
 */
enum class TunerTool {
    /** Strum once and every string shows flat, in tune or sharp (v1.0). */
    STRUM_CHECK,

    /** Phase 29: the 12th-fret check, string by string. */
    INTONATION_CHECK,

    /** Phase 30: strum a chord and its name shows. */
    CHORD_NAME,

    /**
     * Phase 31: the pitch trail, a line of the last seconds in the meter's place. Unlike the others it
     * doesn't take the microphone from the tuner: the note, its cents and the strings stay, and it
     * stays on in chromatic mode (singers, violins).
     */
    PITCH_TRAIL,
}

/**
 * The tools the tuner offers for [instrument] in a build with [tools] switched on, in the menu's
 * order: the strum check where it can tell the strings apart ([StrumInstruments.offers]: not on the
 * 12-string), the 12th-fret check where its switch is on and the instrument has
 * frets and a saddle ([IntonationInstruments.offers]), the chord name where its switch is on and the
 * instrument is strummed in chords ([ChordInstruments.offers]), and the pitch trail where its switch is on
 * (every instrument: it draws any note). With the strum check alone the tuner shows its
 * switch as in v1.0; with any other tool or more, one "more tools" button opens a menu of them.
 */
fun tunerToolsFor(tools: ToolSwitches, instrument: Instrument): List<TunerTool> = buildList {
    if (StrumInstruments.offers(instrument)) add(TunerTool.STRUM_CHECK)
    if (tools.intonationCheck && IntonationInstruments.offers(instrument)) add(TunerTool.INTONATION_CHECK)
    if (tools.chordNaming && ChordInstruments.offers(instrument)) add(TunerTool.CHORD_NAME)
    if (tools.pitchTrail) add(TunerTool.PITCH_TRAIL)
}

/** Where the 12th-fret check is: see [TunerUiState.IntonationCheck]. */
enum class IntonationStep {
    /** No string chosen yet (or every string has been gone through): tap a string. */
    CHOOSE,

    /** The chosen string, played open: wait until it is steady, then take a second of it. */
    OPEN,

    /** The same string pressed at the 12th fret, likewise. */
    FRET,

    /** The comparison's verdict, until the player checks again or goes on. */
    VERDICT,
}

/** Something that held a step up, which the line under the panel says until the next try. */
enum class IntonationSnag {
    NONE,

    /** The open note was too far from the chosen string ([TunerUiState.IntonationCheck.tuneFirstCents]). */
    TUNE_FIRST,

    /** The fretted note wasn't the open string's octave: another fret, or a misread. */
    NOT_THE_OCTAVE,
}

/** The 12th-fret check's results, one per string of [tuning] (null: not checked yet); kept by the ViewModel across listening sessions. */
class IntonationResults {
    @Volatile var tuning: Tuning? = null
    @Volatile var results: List<IntonationResult?> = emptyList()
}

/**
 * Turns the tuner's readings into [TunerUiState.IntonationCheck] states (Phase 29, the 12th-fret
 * check; docs/architecture/notes-and-audio.md, *Intonation check*). The ViewModel says which string
 * is checked ([TunerOptions.intonationString]) and starts a check afresh by counting
 * [TunerOptions.intonationAttempt] up; this goes through the steps:
 * - [IntonationStep.OPEN]: the pitch, smoothed like the tuner's ([PitchSmoother]) and folded onto the
 *   string where it reads an octave off, is collected by a [SettledPitch] (steady, then the median of
 *   a second). Within [OPEN_TOLERANCE_CENTS] of the string it is taken; further off the line asks to
 *   tune the string first ([IntonationSnag.TUNE_FIRST]) and the next steady note is taken instead.
 * - [IntonationStep.FRET]: likewise, ignoring the open string still ringing (within
 *   [OPEN_RINGING_CENTS] of it) and folding a reading two octaves up onto the octave. The two are
 *   compared with [Intonation.check]: a verdict ([IntonationStep.VERDICT]), or not the octave, which
 *   waits for the fret again ([IntonationSnag.NOT_THE_OCTAVE]).
 *
 * The results per string go in [memory]; a new tuning clears them. Nothing is allocated per frame
 * except a new state when what it shows changed. Not thread-safe; one per listening session.
 */
class IntonationInterpreter(
    frameSeconds: Float,
    private val memory: IntonationResults = IntonationResults(),
) {
    private val smoother = PitchSmoother()
    private val settled = SettledPitch(frameSeconds)
    private var noteMapper = NoteMapper()

    private var string: Int? = null
    private var attempt = Int.MIN_VALUE
    private var step = IntonationStep.CHOOSE
    private var openMidi = Float.NaN
    // The open note as heard, before folding an octave misread onto the string.
    private var openHeardMidi = Float.NaN
    // Whether a new pluck (a dropout, or the sound getting louder) came since the fret step began.
    private var plucked = false
    private var lastLevelDbfs = SignalGate.SILENCE_DBFS
    private var snag = IntonationSnag.NONE
    private var tuneFirstCents = 0
    private var result: IntonationResult? = null
    private var progress = 0f
    private var last: TunerUiState.IntonationCheck? = null

    /** The state for [options] as it is, without a new reading: at the start, or while the app's own tone sounds. */
    fun shown(options: TunerOptions): TunerUiState.IntonationCheck {
        follow(options)
        return state(options)
    }

    fun interpret(reading: PitchReading, options: TunerOptions): TunerUiState.IntonationCheck {
        follow(options)
        if (options.a4Hz != noteMapper.a4Hz) {
            noteMapper = NoteMapper(options.a4Hz)
            smoother.reset()
            settled.reset()
        }
        if (step == IntonationStep.FRET && (!smoother.hasReading || reading.levelDbfs > lastLevelDbfs + PLUCK_RISE_DB)) plucked = true
        lastLevelDbfs = reading.levelDbfs
        if (reading.isPitched) {
            smoother.update(noteMapper.midiOf(reading.frequencyHz), reading.confidence, reading.levelDbfs >= SignalGate.DEFAULT_THRESHOLD_DBFS)
        } else {
            smoother.noReading(reading.sustained)
        }
        if (step == IntonationStep.OPEN || step == IntonationStep.FRET) {
            val heard = if (smoother.hasReading) heard(smoother.midi, options.tuning) else Float.NaN
            if (settled.update(heard)) take(options)
            progress = if (step == IntonationStep.OPEN || step == IntonationStep.FRET) shownProgress(settled.progress) else 0f
        }
        return state(options)
    }

    /** Follows the tuning (a new one clears the results) and the ViewModel's string and attempt. */
    private fun follow(options: TunerOptions) {
        if (options.tuning != memory.tuning) {
            memory.tuning = options.tuning
            memory.results = List(options.tuning.stringCount) { null }
            attempt = Int.MIN_VALUE
        }
        val chosen = options.intonationString?.takeIf { it in 0 until options.tuning.stringCount }
        if (chosen != string || options.intonationAttempt != attempt) {
            string = chosen
            attempt = options.intonationAttempt
            step = if (chosen == null) IntonationStep.CHOOSE else IntonationStep.OPEN
            openMidi = Float.NaN
            openHeardMidi = Float.NaN
            snag = IntonationSnag.NONE
            result = null
            progress = 0f
            settled.reset()
        }
    }

    /** The open string as heard (before [heard] folds it), kept while the open step collects. */
    private var heardRaw = Float.NaN

    /** [midi] as this step hears it, or NaN for a reading it ignores. */
    private fun heard(midi: Float, tuning: Tuning): Float {
        val index = string ?: return Float.NaN
        return when (step) {
            IntonationStep.OPEN -> {
                // The open string read an octave off (a weak fundamental) is still the open string.
                val stringMidi = tuning.strings[index].midi.toFloat()
                heardRaw = midi
                when {
                    abs(midi - (stringMidi + OCTAVE)) <= HALF_SEMITONE -> midi - OCTAVE
                    abs(midi - (stringMidi - OCTAVE)) <= HALF_SEMITONE -> midi + OCTAVE
                    else -> midi
                }
            }
            IntonationStep.FRET -> when {
                // The open string still ringing (or played again) isn't the fretted note; nor, until a
                // new pluck, the open string where it was heard an octave up.
                abs(midi - openMidi) * CENTS <= OPEN_RINGING_CENTS -> Float.NaN
                !plucked && abs(midi - openHeardMidi) * CENTS <= OPEN_RINGING_CENTS -> Float.NaN
                abs(midi - (openMidi + 2 * OCTAVE)) <= HALF_SEMITONE -> midi - OCTAVE
                else -> midi
            }
            else -> Float.NaN
        }
    }

    /** The settled pitch of this step is in: take it, or say what held it up and wait for the next. */
    private fun take(options: TunerOptions) {
        val index = string ?: return
        val midi = settled.midi
        settled.reset()
        when (step) {
            IntonationStep.OPEN -> {
                val cents = (midi - options.tuning.strings[index].midi) * CENTS
                if (abs(cents) > OPEN_TOLERANCE_CENTS) {
                    snag = IntonationSnag.TUNE_FIRST
                    tuneFirstCents = cents.roundToInt()
                } else {
                    openMidi = midi
                    // An octave misread folds back by exactly 12: the open string as the mic heard it.
                    openHeardMidi = if (abs(heardRaw - midi) > HALF_SEMITONE) midi + (heardRaw - midi).roundToInt() else midi
                    snag = IntonationSnag.NONE
                    step = IntonationStep.FRET
                    plucked = false
                }
            }
            IntonationStep.FRET -> {
                val checked = Intonation.check(noteMapper.frequencyOf(openMidi), noteMapper.frequencyOf(midi))
                if (checked.verdict == IntonationVerdict.NOT_THE_OCTAVE) {
                    snag = IntonationSnag.NOT_THE_OCTAVE
                } else {
                    snag = IntonationSnag.NONE
                    result = checked
                    step = IntonationStep.VERDICT
                    memory.results = memory.results.toMutableList().also { if (index < it.size) it[index] = checked }
                }
            }
            else -> Unit
        }
    }

    /** The state, new only when something it shows changed. */
    private fun state(options: TunerOptions): TunerUiState.IntonationCheck {
        val shown = last
        val tuneFirst = if (snag == IntonationSnag.TUNE_FIRST) tuneFirstCents else 0
        if (shown != null &&
            shown.tuning == options.tuning &&
            shown.notation == options.notation &&
            shown.string == string &&
            shown.step == step &&
            shown.progress == progress &&
            shown.snag == snag &&
            shown.tuneFirstCents == tuneFirst &&
            shown.result === result &&
            shown.results === memory.results &&
            shown.scaleLengthMm == options.intonationScaleMm
        ) {
            return shown
        }
        return TunerUiState.IntonationCheck(
            tuning = options.tuning,
            notation = options.notation,
            string = string,
            step = step,
            progress = progress,
            snag = snag,
            tuneFirstCents = tuneFirst,
            result = result,
            results = memory.results,
            scaleLengthMm = options.intonationScaleMm,
        ).also { last = it }
    }

    companion object {
        /**
         * The open note is taken within this many cents of the chosen string. The comparison is
         * relative, so a string a little off still gets the right verdict, but its tension (which sets
         * the intonation) should be the tuned one: 10 cents is 1.2 % of tension.
         */
        const val OPEN_TOLERANCE_CENTS = 10f

        /** At the fret, a reading this close to the open note is the open string still ringing. */
        const val OPEN_RINGING_CENTS = 50f

        /** A frame this much louder than the one before is a new pluck. */
        const val PLUCK_RISE_DB = 6f

        /** The ring fills in steps of this, so a second of collecting sends 20 states, not 47. */
        const val PROGRESS_STEP = 0.05f

        private const val OCTAVE = 12f
        private const val HALF_SEMITONE = 0.5f
        private const val CENTS = 100f

        private fun shownProgress(progress: Float): Float = (progress / PROGRESS_STEP).toInt() * PROGRESS_STEP
    }
}
