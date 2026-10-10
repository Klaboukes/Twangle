package com.intune.core.dsp.music

/** The 12 equal-tempered pitch classes, in ascending order starting from C. */
enum class PitchClass(val symbol: String) {
    C("C"),
    C_SHARP("C#"),
    D("D"),
    D_SHARP("D#"),
    E("E"),
    F("F"),
    F_SHARP("F#"),
    G("G"),
    G_SHARP("G#"),
    A("A"),
    A_SHARP("A#"),
    B("B"),
}

/**
 * An equal-tempered note, identified by its MIDI note number.
 *
 * Octaves follow scientific pitch notation: MIDI 60 = C4 (middle C), MIDI 69 = A4.
 * This is a value class, so passing notes around in the pitch pipeline does not allocate.
 */
@JvmInline
value class Note(val midi: Int) {
    val pitchClass: PitchClass get() = PitchClass.entries[midi.mod(SEMITONES_PER_OCTAVE)]

    val octave: Int get() = Math.floorDiv(midi, SEMITONES_PER_OCTAVE) - 1

    override fun toString(): String = "${pitchClass.symbol}$octave"

    companion object {
        const val SEMITONES_PER_OCTAVE = 12

        fun of(pitchClass: PitchClass, octave: Int): Note =
            Note((octave + 1) * SEMITONES_PER_OCTAVE + pitchClass.ordinal)
    }
}
