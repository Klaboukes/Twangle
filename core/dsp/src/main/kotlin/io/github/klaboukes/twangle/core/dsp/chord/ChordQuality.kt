package io.github.klaboukes.twangle.core.dsp.chord

/**
 * The chord types, the common ones first, in the order the chord charts' picker shows them, four to a
 * row. Each is a set of intervals in semitones above the root; [fifthOptional] chords are also named
 * and charted without their fifth, as guitar voicings often leave it out (C7 as x32310: C E B♭, no G;
 * C9 as x32333).
 *
 * [ChordDetector] names only the [detected] ones: the seven it was tuned on, the power chord, the
 * 7♯9 and the m9 and maj9. The others share their tones with them or with each other (C6 is Am7,
 * Cm7♭5 is E♭m6, Cdim7 is also E♭dim7, F♯dim7 and Adim7), so hearing them would hang on the bass
 * alone. They are for the chord charts. The power chord is heard (root and fifth alone are only
 * ever C5), so a strummed C5 is named C5, never C or Cm from a third that isn't there. The 7♯9, m9
 * and maj9 share their tones with no other chord type and took no names from the chords heard
 * before them (`ChordAccuracyReport`); with a weak ninth they are heard as their 7th chord (Am9 as
 * Am7), as they were before.
 *
 * The four after [AUGMENTED] (Phase 49) are not in the Chords panel yet; [inPanel] says which are.
 */
enum class ChordQuality(vararg intervals: Int, val fifthOptional: Boolean = false, val detected: Boolean = true) {
    MAJOR(0, 4, 7),
    MINOR(0, 3, 7),
    DOMINANT_SEVENTH(0, 4, 7, 10, fifthOptional = true),
    MAJOR_SEVENTH(0, 4, 7, 11, fifthOptional = true),
    MINOR_SEVENTH(0, 3, 7, 10, fifthOptional = true),
    SUS2(0, 2, 7),
    SUS4(0, 5, 7),
    SEVENTH_SUS4(0, 5, 7, 10, fifthOptional = true, detected = false),
    SIXTH(0, 4, 7, 9, fifthOptional = true, detected = false),
    MINOR_SIXTH(0, 3, 7, 9, detected = false),
    ADD9(0, 2, 4, 7, detected = false),
    NINTH(0, 2, 4, 7, 10, fifthOptional = true, detected = false),
    DIMINISHED(0, 3, 6, detected = false),
    DIMINISHED_SEVENTH(0, 3, 6, 9, detected = false),
    HALF_DIMINISHED(0, 3, 6, 10, detected = false),
    AUGMENTED(0, 4, 8, detected = false),

    /** The power chord (C5): root and fifth, often with the root again an octave up. No third, so neither major nor minor. */
    POWER_CHORD(0, 7),

    /** The 7♯9 (the "Hendrix chord", E7♯9): a 7th chord with the minor third on top as a sharp ninth (3 semitones as a pitch class). */
    SEVENTH_SHARP_NINE(0, 3, 4, 7, 10, fifthOptional = true),
    MINOR_NINTH(0, 2, 3, 7, 10, fifthOptional = true),
    MAJOR_NINTH(0, 2, 4, 7, 11, fifthOptional = true),
    ;

    /** The intervals as a 12-bit mask over pitch classes (bit i = i semitones above the root). */
    val mask: Int = intervals.fold(0) { m, i -> m or (1 shl i) }

    /** [mask] without the fifth, for [fifthOptional] chords. */
    val maskWithoutFifth: Int = mask and FIFTH_BIT.inv()

    companion object {
        /** The chord types the Chords panel offers, in its order: the sixteen up to [AUGMENTED], four to a row. */
        val inPanel: List<ChordQuality> = entries.subList(0, AUGMENTED.ordinal + 1)
    }
}

private const val FIFTH_BIT = 1 shl 7
