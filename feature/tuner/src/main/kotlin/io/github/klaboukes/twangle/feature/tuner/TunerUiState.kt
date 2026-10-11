package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.feature.tuner.components.needleCents
import kotlin.math.abs
import kotlin.math.roundToInt

/** Everything the tuner screen shows, as one immutable value. See ARCHITECTURE §6. */
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
         * [InTuneDetector].
         */
        val inTune: Boolean,
    ) : TunerUiState
}

/**
 * This state as the screen's text shows it: in [TunerUiState.Detected] the cents rounded to whole
 * cents and the frequency to 0.1 Hz, the precision `NoteDisplay` writes them with. Two readings whose
 * text looks the same then compare equal, so the screen recomposes only when the text changes. The
 * needle needs the exact cents and reads them from the unrounded state. Other states are returned as
 * they are.
 */
fun TunerUiState.asShown(): TunerUiState = when (this) {
    is TunerUiState.Detected -> copy(cents = shownCents(cents), frequencyHz = shownHz(frequencyHz))
    else -> this
}

/**
 * Whether [other] would look exactly like this state on screen: the same text ([asShown]) and the
 * needle less than [NEEDLE_STEP_CENTS] away (or pinned to the same edge of the gauge). The
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
        shownCents(cents) == shownCents(other.cents) &&
        shownHz(frequencyHz) == shownHz(other.frequencyHz) &&
        abs(needleCents(cents) - needleCents(other.cents)) < NEEDLE_STEP_CENTS
}

/**
 * Needle moves smaller than this aren't sent to the screen: about 0.2 dp at the needle's tip on the
 * widest (560 dp) gauge, below one pixel on phones and tablets. The needle's spring still glides
 * between the readings that are sent.
 */
internal const val NEEDLE_STEP_CENTS = 0.03f

private fun shownCents(cents: Float): Float = cents.roundToInt().toFloat()

/** The frequency is shown to 0.1 Hz (`tuner_frequency`). */
private fun shownHz(frequencyHz: Float): Float = (frequencyHz * 10f).roundToInt() / 10f
