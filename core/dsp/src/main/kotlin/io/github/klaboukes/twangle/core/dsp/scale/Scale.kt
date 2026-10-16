package io.github.klaboukes.twangle.core.dsp.scale

import io.github.klaboukes.twangle.core.dsp.music.Accidental
import io.github.klaboukes.twangle.core.dsp.music.KeySpelling
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass

/**
 * The scales the Practice tab shows, each as its steps in semitones above the root. [minorKey]
 * scales are written with the minor key's sign ([KeySpelling]), the others with the major key's.
 */
enum class ScaleType(vararg semitones: Int, val minorKey: Boolean) {
    MAJOR(0, 2, 4, 5, 7, 9, 11, minorKey = false),
    NATURAL_MINOR(0, 2, 3, 5, 7, 8, 10, minorKey = true),
    MAJOR_PENTATONIC(0, 2, 4, 7, 9, minorKey = false),
    MINOR_PENTATONIC(0, 3, 5, 7, 10, minorKey = true),

    /** The minor pentatonic with the blue note, the flat fifth: A C D E♭ E G. */
    BLUES(0, 3, 5, 6, 7, 10, minorKey = true),
    ;

    /** The steps from the root up, in semitones: 0 2 4 5 7 9 11 for the major scale. */
    val steps: List<Int> = semitones.toList()

    /** The steps as a 12-bit mask over pitch classes (bit i = i semitones above the root). */
    val mask: Int = semitones.fold(0) { m, i -> m or (1 shl i) }
}

/**
 * A scale on a root, e.g. A minor pentatonic. Its [notes] are written with the sign of its key
 * ([accidentalOf]): G major has an F♯, F major a B♭, E minor an F♯, D minor a B♭. The blues scale's
 * blue note is always written flat, as the flat fifth it is (E blues: E G A B♭ B D; A blues has an
 * E♭). As for chords ([io.github.klaboukes.twangle.core.dsp.chord.ChordSpelling]), a note the key
 * would write with a sign there is no name for (E♯ in F♯ major, C♭ in E♭ minor) takes the name of
 * the same note (F, B). Pure.
 */
data class Scale(val root: PitchClass, val type: ScaleType) {

    /** The scale's notes from the root up: A C D E G for A minor pentatonic. */
    val notes: List<PitchClass> = type.steps.map { PitchClass.entries[(root.ordinal + it) % 12] }

    private val keySign: Accidental =
        if (type.minorKey) KeySpelling.ofMinorKey(root) else KeySpelling.ofMajorKey(root)

    operator fun contains(pitchClass: PitchClass): Boolean = degreeOf(pitchClass) != null

    /** [pitchClass]'s place in the scale, 1 for the root, or null if the scale doesn't hold it. */
    fun degreeOf(pitchClass: PitchClass): Int? = notes.indexOf(pitchClass).takeIf { it >= 0 }?.plus(1)

    /** The sign [pitchClass] is written with in this scale (see the class comment). */
    fun accidentalOf(pitchClass: PitchClass): Accidental =
        if (type == ScaleType.BLUES && pitchClass.ordinal == (root.ordinal + BLUE_NOTE) % 12) Accidental.FLAT else keySign

    /** [pitchClass] as a note of this scale in [notation]: "B♭", "Si♭", "B" (German). */
    fun nameOf(pitchClass: PitchClass, notation: Notation): String =
        notation.nameOf(pitchClass, accidentalOf(pitchClass))

    private companion object {
        /** The blues scale's flat fifth, in semitones above the root. */
        const val BLUE_NOTE = 6
    }
}
