package io.github.klaboukes.twangle.core.dsp.scale

import io.github.klaboukes.twangle.core.dsp.music.Accidental
import io.github.klaboukes.twangle.core.dsp.music.KeySpelling
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass

/**
 * The key a scale is written in, [tonicStep] semitones above its root: a major key (a mode's parent
 * key: Dorian's is 10 above, D Dorian is written in C major) or, with [minor], the minor key on it.
 */
data class SpellingKey(val tonicStep: Int, val minor: Boolean) {
    companion object {
        /** The major key [tonicStep] semitones above the root. */
        fun major(tonicStep: Int = 0) = SpellingKey(tonicStep, minor = false)

        /** The minor key on the root. */
        val MINOR = SpellingKey(0, minor = true)
    }
}

/**
 * The scales the Practice tab shows, in its order (the owner's, 2026-10-26: the most used first, two to a
 * row: major and minor, the pentatonics, blues and Dorian, Mixolydian and harmonic minor, Phrygian and
 * Lydian, melodic minor and Locrian), each as its steps in semitones above the root, the [key] it is written in ([Scale.accidentalOf]), any step written with a sign of its own
 * whatever the key ([signs]: the blues scale's flat fifth, the raised 6th and 7th of the minors), and
 * the step of its colour note ([colourStep]), the note that makes a mode sound as it does and the neck
 * marks with a ring (the owner's pick F, Phase 47): Dorian's 6th, Phrygian's ♭2, Lydian's ♯4,
 * Mixolydian's ♭7, Locrian's ♭5, and harmonic minor's raised 7th. Ionian and Aeolian, the major and
 * natural minor the others are heard against, have none; nor has melodic minor, which differs from
 * natural minor in two notes (its 6th and 7th) rather than one.
 *
 * Saved in settings by [name] (MAJOR and NATURAL_MINOR from before the modes), so don't rename them;
 * their order may change. The modes' steps are the major scale's from its 1st (Ionian) to its 7th note
 * (Locrian); NATURAL_MINOR is Aeolian, the minor scale.
 */
enum class ScaleType(
    vararg semitones: Int,
    val key: SpellingKey,
    val colourStep: Int? = null,
    val signs: Map<Int, Accidental> = emptyMap(),
) {
    /** Ionian. */
    MAJOR(0, 2, 4, 5, 7, 9, 11, key = SpellingKey.major(0)),

    /** Aeolian, the minor scale: written in its relative major, which [KeySpelling.ofMode] makes the minor key's own sign. */
    NATURAL_MINOR(0, 2, 3, 5, 7, 8, 10, key = SpellingKey.major(3)),
    MAJOR_PENTATONIC(0, 2, 4, 7, 9, key = SpellingKey.major(0)),
    MINOR_PENTATONIC(0, 3, 5, 7, 10, key = SpellingKey.MINOR),

    /** The minor pentatonic with the blue note, the flat fifth, always written flat: A C D E♭ E G. */
    BLUES(0, 3, 5, 6, 7, 10, key = SpellingKey.MINOR, signs = mapOf(6 to Accidental.FLAT)),
    DORIAN(0, 2, 3, 5, 7, 9, 10, key = SpellingKey.major(10), colourStep = 9),
    MIXOLYDIAN(0, 2, 4, 5, 7, 9, 10, key = SpellingKey.major(5), colourStep = 10),

    /** Natural minor with a raised 7th, the leading note: A B C D E F G♯. */
    HARMONIC_MINOR(
        0, 2, 3, 5, 7, 8, 11,
        key = SpellingKey.MINOR, colourStep = 11, signs = mapOf(11 to Accidental.SHARP),
    ),
    PHRYGIAN(0, 1, 3, 5, 7, 8, 10, key = SpellingKey.major(8), colourStep = 1),
    LYDIAN(0, 2, 4, 6, 7, 9, 11, key = SpellingKey.major(7), colourStep = 6),

    /** The ascending form, as jazz plays it both ways: natural minor with a raised 6th and 7th, A B C D E F♯ G♯. */
    MELODIC_MINOR(
        0, 2, 3, 5, 7, 9, 11,
        key = SpellingKey.MINOR, signs = mapOf(9 to Accidental.SHARP, 11 to Accidental.SHARP),
    ),
    LOCRIAN(0, 1, 3, 5, 6, 8, 10, key = SpellingKey.major(1), colourStep = 6),
    ;

    /** The steps from the root up, in semitones: 0 2 4 5 7 9 11 for the major scale. */
    val steps: List<Int> = semitones.toList()

    /** The steps as a 12-bit mask over pitch classes (bit i = i semitones above the root). */
    val mask: Int = semitones.fold(0) { m, i -> m or (1 shl i) }
}

/**
 * A scale on a root, e.g. A minor pentatonic. Its [notes] are written with the sign of the key its
 * type is written in ([ScaleType.key], [accidentalOf]): G major has an F♯, F major a B♭, E minor an
 * F♯, D minor a B♭; a mode its parent major key's ([KeySpelling.ofMode]): F♯ Dorian E major's sharps,
 * B♭ Mixolydian E♭ major's flats. A step with a sign of its own ([ScaleType.signs]) keeps it where
 * it falls on a black key: the blues scale's flat fifth (E blues: E G A B♭ B D; A blues has an E♭),
 * harmonic minor's raised 7th (D harmonic minor: D E F G A B♭ C♯). As for chords
 * ([io.github.klaboukes.twangle.core.dsp.chord.ChordSpelling]), a note the key would write with a sign
 * there is no name for (E♯ in F♯ major, C♭ in E♭ minor) takes the name of the same note (F, B). Pure.
 */
data class Scale(val root: PitchClass, val type: ScaleType) {

    /** The scale's notes from the root up: A C D E G for A minor pentatonic. */
    val notes: List<PitchClass> = type.steps.map { above(it) }

    /** The colour note the neck marks ([ScaleType.colourStep]): B in D Dorian, F♯ in C Lydian; null if none. */
    val colourNote: PitchClass? = type.colourStep?.let { above(it) }

    private val keySign: Accidental = type.key.let { key ->
        when {
            key.minor -> KeySpelling.ofMinorKey(above(key.tonicStep))
            else -> KeySpelling.ofMode(root, parent = above(key.tonicStep), minorThird = MINOR_THIRD in type.steps)
        }
    }

    operator fun contains(pitchClass: PitchClass): Boolean = degreeOf(pitchClass) != null

    /** [pitchClass]'s place in the scale, 1 for the root, or null if the scale doesn't hold it. */
    fun degreeOf(pitchClass: PitchClass): Int? = notes.indexOf(pitchClass).takeIf { it >= 0 }?.plus(1)

    /** The sign [pitchClass] is written with in this scale (see the class comment). */
    fun accidentalOf(pitchClass: PitchClass): Accidental =
        type.signs[(pitchClass.ordinal - root.ordinal).mod(12)] ?: keySign

    /** [pitchClass] as a note of this scale in [notation]: "B♭", "Si♭", "B" (German). */
    fun nameOf(pitchClass: PitchClass, notation: Notation): String =
        notation.nameOf(pitchClass, accidentalOf(pitchClass))

    private fun above(semitones: Int): PitchClass = PitchClass.entries[(root.ordinal + semitones) % 12]

    private companion object {
        const val MINOR_THIRD = 3
    }
}
