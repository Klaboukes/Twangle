package io.github.klaboukes.twangle.core.dsp.music

import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.pow

/** A frequency expressed as its nearest equal-tempered note plus the deviation from it. */
data class NoteReading(
    val frequencyHz: Float,
    val note: Note,
    /** Deviation from [note] in cents, in the range (−50, +50]. Positive means sharp. */
    val cents: Float,
)

/**
 * Converts between frequencies (Hz), fractional MIDI note numbers and notes, relative to a
 * configurable concert pitch [a4Hz].
 *
 * Uses 12-tone equal temperament as defined by the MIDI Tuning Standard:
 * `midi = 69 + 12 · log2(f / A4)` and `f = A4 · 2^((midi − 69) / 12)`.
 * Math is done in Double and returned as Float; nothing here allocates except [map].
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

    /** The equal-tempered note nearest to [frequencyHz]. Exactly half-way rounds down (to +50 cents). */
    fun nearestNote(frequencyHz: Float): Note = Note(nearestMidi(midiOf(frequencyHz).toDouble()))

    /** Deviation of [frequencyHz] from its nearest note, in cents, in the range (−50, +50]. */
    fun centsOff(frequencyHz: Float): Float {
        val midi = midiOf(frequencyHz).toDouble()
        return (CENTS_PER_SEMITONE * (midi - nearestMidi(midi))).toFloat()
    }

    /** Nearest note and the deviation from it. Allocates one small [NoteReading]. */
    fun map(frequencyHz: Float): NoteReading {
        val midi = midiOf(frequencyHz).toDouble()
        val nearest = nearestMidi(midi)
        return NoteReading(
            frequencyHz = frequencyHz,
            note = Note(nearest),
            cents = (CENTS_PER_SEMITONE * (midi - nearest)).toFloat(),
        )
    }

    // ceil(x − 0.5) instead of round(x) so a half-way value maps to the lower note at +50 cents,
    // giving the documented (−50, +50] cents range.
    private fun nearestMidi(midi: Double): Int = ceil(midi - 0.5).toInt()

    companion object {
        const val DEFAULT_A4_HZ = 440f
        const val MIN_A4_HZ = 415f
        const val MAX_A4_HZ = 466f

        private const val A4_MIDI = 69
        private const val SEMITONES_PER_OCTAVE = Note.SEMITONES_PER_OCTAVE
        private const val CENTS_PER_SEMITONE = 100.0
        private const val CENTS_PER_OCTAVE = 1200.0

        /**
         * Interval from [referenceHz] to [frequencyHz] in cents: `1200 · log2(f / ref)`
         * (Ellis 1885 definition of the cent). Positive means [frequencyHz] is higher.
         */
        fun centsBetween(frequencyHz: Float, referenceHz: Float): Float {
            requireValidFrequency(frequencyHz)
            requireValidFrequency(referenceHz)
            return (CENTS_PER_OCTAVE * log2(frequencyHz.toDouble() / referenceHz)).toFloat()
        }

        private fun requireValidFrequency(frequencyHz: Float) {
            require(frequencyHz > 0f && frequencyHz.isFinite()) {
                "Frequency must be positive and finite, was $frequencyHz"
            }
        }
    }
}
