package io.github.klaboukes.twangle.core.dsp.chord

import io.github.klaboukes.twangle.core.dsp.music.PitchClass

/**
 * A chord by its root and type, e.g. A minor. Naming it in the player's notation (A, La, a-moll …)
 * is the UI's job. Every chord is made once ([of]), so naming one in the audio loop allocates
 * nothing and two equal chords are the same object.
 */
data class Chord private constructor(val root: PitchClass, val quality: ChordQuality) {

    /** The same chord type [semitones] higher (lower when negative): G moved up 3 is B♭. */
    fun transposed(semitones: Int): Chord = of(root.transposed(semitones), quality)

    companion object {
        private val all = Array(PitchClass.entries.size * ChordQuality.entries.size) {
            Chord(PitchClass.entries[it / ChordQuality.entries.size], ChordQuality.entries[it % ChordQuality.entries.size])
        }

        /** The chord with [root] and [quality]. */
        fun of(root: PitchClass, quality: ChordQuality): Chord = all[root.ordinal * ChordQuality.entries.size + quality.ordinal]
    }
}
