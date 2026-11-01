package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.designsystem.meter.meterCents
import io.github.klaboukes.twangle.core.dsp.chord.SettledChord
import io.github.klaboukes.twangle.core.dsp.intonation.IntonationResult
import io.github.klaboukes.twangle.core.dsp.level.ReceptionTracker
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.smoothing.InTuneDetector
import io.github.klaboukes.twangle.core.dsp.strum.StrumVerdict
import io.github.klaboukes.twangle.core.settings.TunerMode
import kotlin.math.abs
import kotlin.math.roundToInt

/** Everything the tuner screen shows, as one immutable value. See docs/architecture/ui.md. */
sealed interface TunerUiState {

    /** The microphone permission isn't granted, so the mic stays closed. */
    data object PermissionRequired : TunerUiState

    /** The mic can't be used right now. Every [reason] can be retried. */
    data class Error(val reason: TunerError) : TunerUiState

    /** Listening, but there is no usable note (too quiet, or no clear pitch). */
    data class Listening(
        val mode: TunerMode,
        val tuning: Tuning,
        /** Concert pitch the notes are measured against. */
        val a4Hz: Float = NoteMapper.DEFAULT_A4_HZ,
        /** String the user locked in [TunerMode.AUTO], or null for automatic. */
        val lockedString: Int? = null,
        /** How note names (strings, hint) are written. */
        val notation: Notation = Notation.DEFAULT,
        /** A tip about the sound, e.g. "Too noisy, move the phone closer", shown instead of "Play a string"; null for none. */
        val tip: ReceptionTip? = null,
    ) : TunerUiState

    /** A steady note is being played. */
    data class Detected(
        val mode: TunerMode,
        val tuning: Tuning,
        /** Concert pitch the notes are measured against. */
        val a4Hz: Float = NoteMapper.DEFAULT_A4_HZ,
        /** Target note name without octave in the chosen [notation], e.g. "E", "C♯", "D♭" or "Re". */
        val noteName: String,
        /** Octave of the target note in scientific pitch notation, e.g. 2 for low E. */
        val octave: Int,
        /** Smoothed frequency of the played note in Hz. */
        val frequencyHz: Float,
        /**
         * Smoothed deviation from the target in cents; positive means sharp. In [TunerMode.AUTO]
         * this is the distance to the string and is not limited to ±50, so a string tuned far off
         * shows how far it has to go. In [TunerMode.CHROMATIC] it stays within about ±65.
         */
        val cents: Float,
        /** Index into [Tuning.strings] of the target string in [TunerMode.AUTO], otherwise null. */
        val targetString: Int?,
        /** String the user locked in [TunerMode.AUTO] (then also [targetString]), or null for automatic. */
        val lockedString: Int? = null,
        /** How note names (strings, hint) are written; [noteName] already uses it. */
        val notation: Notation = Notation.DEFAULT,
        /**
         * Within 5 cents of the target (2 in the Precise setting) for at least 300 ms, see
         * [InTuneDetector]; the same as [hint] being [TunerHint.IN_TUNE].
         */
        val inTune: Boolean,
        /**
         * The hint line's message: in tune, "Tune up" or "Tune down", held steady by [SteadyHint]
         * ([TunerInterpreter] sets it). null before the first direction of a note is clear.
         */
        val hint: TunerHint? = tunerHintFor(cents, inTune),
        /**
         * How well the note is heard, 0..1 ([ReceptionTracker.reception]: volume, noise and clarity,
         * smoothed): 1 draws the needle and the wave crisp and solid, towards 0 faint and soft.
         */
        val reception: Float = 1f,
        /**
         * A tip about the sound ("Play a bit louder", "Too noisy, move the phone closer", "Too loud, move the phone
         * back"), held steady by [SteadyHint]; null for none. [TunerInterpreter] sets it.
         */
        val tip: ReceptionTip? = null,
    ) : TunerUiState

    /**
     * Strum check (guitar mode, switched on above the strings): strum once and
     * every string of [tuning] shows flat, in tune or sharp at once. [strings] are in the tuning's
     * order, empty until the first reading; in [StrumPhase.HEARING] they are still the strum before.
     * [toleranceCents] is how far off "in tune" reaches (a less sure string gets a little more).
     */
    data class StrumCheck(
        val tuning: Tuning,
        val notation: Notation = Notation.DEFAULT,
        val phase: StrumPhase = StrumPhase.WAITING,
        val strings: List<StrumString> = emptyList(),
        val toleranceCents: Float = StrumVerdict.DEFAULT_TOLERANCE_CENTS,
    ) : TunerUiState

    /**
     * The 12th-fret check (Phase 29, guitar mode, switched on from the "more tools" menu): one
     * [string] of [tuning] at a time, played open, then pressed at the 12th fret, and the verdict.
     * [step] says where it is; [progress] (0..1) how far the current step's second of steady note
     * has got; [snag] what held the step up ([tuneFirstCents]: how far the open note was from the
     * string). [result] is the verdict in [IntonationStep.VERDICT]; [results] every string's last
     * one (null: not checked yet), in the tuning's order. [scaleLengthMm] is the scale the saddle's
     * millimetres assume, null where they aren't shown (not a guitar).
     */
    data class IntonationCheck(
        val tuning: Tuning,
        val notation: Notation = Notation.DEFAULT,
        val string: Int? = null,
        val step: IntonationStep = IntonationStep.CHOOSE,
        val progress: Float = 0f,
        val snag: IntonationSnag = IntonationSnag.NONE,
        val tuneFirstCents: Int = 0,
        val result: IntonationResult? = null,
        val results: List<IntonationResult?> = emptyList(),
        val scaleLengthMm: Float? = null,
    ) : TunerUiState

    /**
     * The chord name (Phase 30, guitar mode, switched on from the "more tools" menu): strum a chord
     * and its name shows, settled over several readings ([ChordNameInterpreter]). [chord] is the one
     * shown (null: none yet, or [ChordPhase.UNCLEAR]), [faded] once its strum has died away or while a
     * new strum is heard; [recent] the last three chords shown, the latest last. [tuning] is only for
     * the string row and the panel's names; [notation] writes the chord ("Am", "Lam", "Hm").
     */
    data class ChordName(
        val tuning: Tuning,
        val notation: Notation = Notation.DEFAULT,
        val phase: ChordPhase = ChordPhase.WAITING,
        val chord: SettledChord? = null,
        val faded: Boolean = false,
        val recent: List<SettledChord> = emptyList(),
    ) : TunerUiState
}

/**
 * This state as the screen's text shows it: in [TunerUiState.Detected] the cents rounded to whole
 * cents and the frequency to 0.1 Hz, the precision `NoteDisplay` writes them with. Two readings whose
 * text looks the same then compare equal, so the screen recomposes only when the text changes. The
 * needle needs the exact cents and reads them from the unrounded state, and the [TunerUiState.Detected.reception]
 * isn't text at all: it is set to 1 here, and the needle and the wave read it from the unrounded state
 * while drawing. Other states are returned as they are.
 */
fun TunerUiState.asShown(): TunerUiState = when (this) {
    is TunerUiState.Detected -> copy(cents = shownCents(cents), frequencyHz = shownHz(frequencyHz), reception = 1f)
    else -> this
}

/**
 * Whether [other] would look exactly like this state on screen: the same text ([asShown]), the
 * needle less than [NEEDLE_STEP_CENTS] away (or pinned to the same edge of the gauge) and the
 * reception less than [RECEPTION_STEP] away. The
 * ViewModel drops readings that look like the last one it sent, so they don't wake the main thread.
 * Allocates nothing, because it runs on every DSP frame.
 */
internal fun TunerUiState.looksLike(other: TunerUiState): Boolean {
    if (this !is TunerUiState.Detected || other !is TunerUiState.Detected) return this == other
    // Every field of Detected except cents and frequencyHz; TunerUiStateShownTest notices a new one.
    return mode == other.mode &&
        tuning == other.tuning &&
        a4Hz == other.a4Hz &&
        noteName == other.noteName &&
        octave == other.octave &&
        targetString == other.targetString &&
        lockedString == other.lockedString &&
        notation == other.notation &&
        inTune == other.inTune &&
        hint == other.hint &&
        tip == other.tip &&
        shownCents(cents) == shownCents(other.cents) &&
        shownHz(frequencyHz) == shownHz(other.frequencyHz) &&
        abs(meterCents(cents) - meterCents(other.cents)) < NEEDLE_STEP_CENTS &&
        abs(reception - other.reception) < RECEPTION_STEP
}

/**
 * Needle moves smaller than this aren't sent to the screen: about 0.2 dp at the needle's tip on the
 * widest (560 dp) gauge, below one pixel on phones and tablets. The needle's spring still glides
 * between the readings that are sent.
 */
internal const val NEEDLE_STEP_CENTS = 0.03f

/**
 * Reception changes smaller than this aren't sent to the screen: at most 0.8 % opacity and 0.04 dp
 * of blur on the needle and the wave (see `ReceptionLook`), too little to see.
 */
internal const val RECEPTION_STEP = 0.02f

private fun shownCents(cents: Float): Float = cents.roundToInt().toFloat()

/** The frequency is shown to 0.1 Hz (`tuner_frequency`). */
private fun shownHz(frequencyHz: Float): Float = (frequencyHz * 10f).roundToInt() / 10f
