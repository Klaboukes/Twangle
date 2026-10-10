package com.intune.feature.tuner

import com.intune.core.dsp.music.Notation
import com.intune.core.dsp.music.NoteMapper
import com.intune.core.dsp.music.Tuning

/** What the tuner compares a note against. */
enum class TunerMode {
    /** Guitar mode: the target is the closest string of the selected tuning. */
    AUTO,

    /** Any note: the target is the nearest equal-tempered note. */
    CHROMATIC,
}

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
        /** Within 5 cents of the target for at least 300 ms (see [InTuneDetector]). */
        val inTune: Boolean,
    ) : TunerUiState
}
