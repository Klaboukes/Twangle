package io.github.klaboukes.twangle.core.dsp.music

import kotlin.math.log2
import kotlin.math.pow

/**
 * Converts between frequencies (Hz), fractional MIDI note numbers and notes, relative to a
 * configurable concert pitch [a4Hz]. The nearest note and the cents from it come from
 * `PitchSmoother`, which keeps the shown note steady between semitones.
 *
 * Uses 12-tone equal temperament as defined by the MIDI Tuning Standard:
 * `midi = 69 + 12 · log2(f / A4)` and `f = A4 · 2^((midi − 69) / 12)`.
 * Math is done in Double and returned as Float; nothing here allocates.
 */
class NoteMapper(val a4Hz: Float = DEFAULT_A4_HZ) {

    init {
        require(a4Hz in MIN_A4_HZ..MAX_A4_HZ) { "A4 must be in $MIN_A4_HZ..$MAX_A4_HZ Hz, was $a4Hz" }
    }

    /** Fractional MIDI note number of [frequencyHz], e.g. 440 Hz → 69.0 when A4 = 440 Hz. */
    fun midiOf(frequencyHz: Float): Float {
        requireValidFrequency(frequencyHz)
        return (A4_MIDI + SEMITONES_PER_OCTAVE * log2(frequencyHz.toDouble() / a4Hz)).toFloat()
    }

    /** Frequency in Hz of a (possibly fractional) MIDI note number. */
    fun frequencyOf(midi: Float): Float =
        (a4Hz * 2.0.pow((midi.toDouble() - A4_MIDI) / SEMITONES_PER_OCTAVE)).toFloat()

    /** Exact equal-tempered frequency of [note], e.g. E2 → 82.41 Hz when A4 = 440 Hz. */
    fun frequencyOf(note: Note): Float = frequencyOf(note.midi.toFloat())

    companion object {
        const val DEFAULT_A4_HZ = 440f
        const val MIN_A4_HZ = 415f
        const val MAX_A4_HZ = 466f

        private const val A4_MIDI = 69
        private const val SEMITONES_PER_OCTAVE = Note.SEMITONES_PER_OCTAVE

        private fun requireValidFrequency(frequencyHz: Float) {
            require(frequencyHz > 0f && frequencyHz.isFinite()) {
                "Frequency must be positive and finite, was $frequencyHz"
            }
        }
    }
}
