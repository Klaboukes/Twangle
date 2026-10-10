package com.intune.feature.tuner

import com.intune.core.dsp.music.Note
import com.intune.core.dsp.music.NoteMapper
import com.intune.core.dsp.music.Tuning
import com.intune.core.dsp.smoothing.PitchSmoother
import kotlin.math.abs

/**
 * Turns raw [PitchReading]s (one per hop) into [TunerUiState]s: smooths the pitch with
 * [PitchSmoother], then picks the target, either the nearest note ([TunerMode.CHROMATIC]) or the
 * closest string of the tuning ([TunerMode.AUTO]).
 *
 * It also tracks whether the note is in tune ([InTuneDetector]), restarting whenever the target
 * changes. [frameSeconds] is the time between readings (hop / sample rate).
 *
 * Mode and tuning are passed with every reading, so switching them takes effect on the next frame
 * without losing the smoothing history. Create one per listening session; it keeps state and is
 * not thread-safe. Apart from the returned state nothing is allocated per frame.
 */
class TunerInterpreter(
    private val frameSeconds: Float,
    private val noteMapper: NoteMapper = NoteMapper(),
    private val smoother: PitchSmoother = PitchSmoother(),
    private val inTuneDetector: InTuneDetector = InTuneDetector(),
) {
    private var stringIndex = NO_STRING
    private var stringTuning: Tuning? = null
    private var targetMidi = NO_TARGET

    fun interpret(reading: PitchReading, mode: TunerMode, tuning: Tuning): TunerUiState {
        if (reading.isPitched) {
            smoother.update(noteMapper.midiOf(reading.frequencyHz))
        } else {
            smoother.noReading()
        }
        if (!smoother.hasReading) {
            stringIndex = NO_STRING
            targetMidi = NO_TARGET
            inTuneDetector.reset()
            return TunerUiState.Listening(mode, tuning)
        }

        val midi = smoother.midi
        val frequencyHz = noteMapper.frequencyOf(midi)
        return when (mode) {
            TunerMode.CHROMATIC -> detected(mode, tuning, smoother.note, frequencyHz, smoother.cents, null)
            TunerMode.AUTO -> {
                val string = trackString(midi, tuning)
                detected(mode, tuning, tuning.strings[string], frequencyHz, tuning.centsFromString(midi, string), string)
            }
        }
    }

    /**
     * The nearest string, but sticky: the current string is kept until another one is closer by
     * more than [STRING_HYSTERESIS_SEMITONES], so a note half-way between two strings doesn't
     * make the target flicker. Same idea and margin as the note hysteresis in [PitchSmoother].
     */
    private fun trackString(midi: Float, tuning: Tuning): Int {
        val nearest = tuning.nearestStringIndex(midi)
        if (tuning != stringTuning || stringIndex == NO_STRING) {
            stringTuning = tuning
            stringIndex = nearest
        } else if (nearest != stringIndex) {
            val currentDistance = abs(midi - tuning.strings[stringIndex].midi)
            val nearestDistance = abs(midi - tuning.strings[nearest].midi)
            if (currentDistance - nearestDistance > STRING_HYSTERESIS_SEMITONES) stringIndex = nearest
        }
        return stringIndex
    }

    private fun detected(
        mode: TunerMode,
        tuning: Tuning,
        target: Note,
        frequencyHz: Float,
        cents: Float,
        targetString: Int?,
    ): TunerUiState.Detected {
        // A new target has to earn "in tune" on its own, even if it starts right on pitch.
        if (target.midi != targetMidi) {
            targetMidi = target.midi
            inTuneDetector.reset()
        }
        return TunerUiState.Detected(
            mode = mode,
            tuning = tuning,
            noteName = target.pitchClass.symbol,
            octave = target.octave,
            frequencyHz = frequencyHz,
            cents = cents,
            targetString = targetString,
            inTune = inTuneDetector.update(cents, frameSeconds),
        )
    }

    companion object {
        /** 15 cents, matching [PitchSmoother.DEFAULT_HYSTERESIS_CENTS]. */
        const val STRING_HYSTERESIS_SEMITONES = 0.15f

        private const val NO_STRING = -1
        private const val NO_TARGET = Int.MIN_VALUE
    }
}
