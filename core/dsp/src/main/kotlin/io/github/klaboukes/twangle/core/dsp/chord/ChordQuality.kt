package io.github.klaboukes.twangle.core.dsp.chord

/**
 * The chord types, the common ones first, in the order the chord charts' picker shows them, four to a
 * row. Each is a set of intervals in semitones above the root; [fifthOptional] chords are also named
 * and charted without their fifth, as guitar voicings often leave it out (C7 as x32310: C E B♭, no G;
 * C9 as x32333).
 *
 * [ChordDetector] names only the [detected] ones, the seven it was tuned on: the others share their
 * tones with them or with each other (C6 is Am7, Cm7♭5 is E♭m6, Cdim7 is also E♭dim7, F♯dim7 and
 * Adim7), so hearing them would hang on the bass alone. They are for the chord charts.
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
    ;

    /** The intervals as a 12-bit mask over pitch classes (bit i = i semitones above the root). */
    val mask: Int = intervals.fold(0) { m, i -> m or (1 shl i) }

    /** [mask] without the fifth, for [fifthOptional] chords. */
    val maskWithoutFifth: Int = mask and FIFTH_BIT.inv()
}

private const val FIFTH_BIT = 1 shl 7
