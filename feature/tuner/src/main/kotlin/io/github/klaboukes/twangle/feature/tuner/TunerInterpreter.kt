package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.smoothing.PitchSmoother
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.TunerMode
import kotlin.math.abs

/**
 * Turns raw [PitchReading]s (one per hop) into [TunerUiState]s: smooths the pitch with
 * [PitchSmoother], then picks the target, either the nearest note ([TunerMode.CHROMATIC]) or the
 * closest string of the tuning ([TunerMode.AUTO]), or the string the user locked.
 *
 * It also tracks whether the note is in tune ([InTuneDetector], with the window of
 * [TunerOptions.inTunePrecision]), restarting whenever the target changes. [frameSeconds] is the
 * time between readings (hop / sample rate).
 *
 * The [TunerOptions] are passed with every reading, so switching mode or tuning takes effect on the
 * next frame without losing the smoothing history. A new A4 restarts the smoothing, because the
 * smoothed notes were measured against the old one. A new precision starts "in tune" over. Create
 * one per listening session; it keeps state and is not thread-safe. Apart from the returned state
 * nothing is allocated per frame.
 */
class TunerInterpreter(
    private val frameSeconds: Float,
    private val smoother: PitchSmoother = PitchSmoother(),
) {
    private var noteMapper = NoteMapper()
    private var inTunePrecision = InTunePrecision.DEFAULT
    private var inTuneDetector = InTuneDetector.forPrecision(inTunePrecision)
    private var stringIndex = NO_STRING
    private var stringTuning: Tuning? = null
    private var targetMidi = NO_TARGET

    fun interpret(reading: PitchReading, options: TunerOptions): TunerUiState {
        val mode = options.mode
        val tuning = options.tuning
        if (options.a4Hz != noteMapper.a4Hz) {
            noteMapper = NoteMapper(options.a4Hz)
            smoother.reset()
        }
        if (options.inTunePrecision != inTunePrecision) {
            inTunePrecision = options.inTunePrecision
            inTuneDetector = InTuneDetector.forPrecision(inTunePrecision)
        }
        if (reading.isPitched) {
            smoother.update(noteMapper.midiOf(reading.frequencyHz), reading.confidence)
        } else {
            smoother.noReading()
        }
        if (!smoother.hasReading) {
            stringIndex = NO_STRING
            targetMidi = NO_TARGET
            inTuneDetector.reset()
            return listening(options)
        }

        val midi = smoother.midi
        val frequencyHz = noteMapper.frequencyOf(midi)
        return when (mode) {
            TunerMode.CHROMATIC -> detected(options, smoother.note, frequencyHz, smoother.cents, null)
            TunerMode.AUTO -> {
                val locked = lockedString(options)
                val string = if (locked != null) {
                    // Tracking starts afresh once the lock is released.
                    stringIndex = NO_STRING
                    locked
                } else {
                    trackString(midi, tuning)
                }
                detected(options, tuning.strings[string], frequencyHz, tuning.centsFromString(midi, string), string)
            }
        }
    }

    /** The state while nothing usable is heard. */
    fun listening(options: TunerOptions): TunerUiState.Listening = TunerUiState.Listening(
        mode = options.mode,
        tuning = options.tuning,
        a4Hz = options.a4Hz,
        lockedString = lockedString(options),
        notation = options.notation,
    )

    /** The locked string in guitar mode, if it exists in the tuning; null means automatic. */
    private fun lockedString(options: TunerOptions): Int? =
        options.lockedString?.takeIf { options.mode == TunerMode.AUTO && it in 0 until options.tuning.stringCount }

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
        options: TunerOptions,
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
            mode = options.mode,
            tuning = options.tuning,
            a4Hz = options.a4Hz,
            noteName = options.notation.nameOf(target.pitchClass),
            octave = target.octave,
            frequencyHz = frequencyHz,
            cents = cents,
            targetString = targetString,
            lockedString = lockedString(options),
            notation = options.notation,
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
