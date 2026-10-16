package io.github.klaboukes.twangle.core.dsp.chord

/**
 * The chord types [ChordDetector] names, the common ones first. Each is a set of intervals in
 * semitones above the root; [fifthOptional] chords are also named without their fifth, as guitar
 * voicings often leave it out (C7 as x32310: C E B♭, no G).
 */
enum class ChordQuality(vararg intervals: Int, val fifthOptional: Boolean = false) {
    MAJOR(0, 4, 7),
    MINOR(0, 3, 7),
    DOMINANT_SEVENTH(0, 4, 7, 10, fifthOptional = true),
    MAJOR_SEVENTH(0, 4, 7, 11, fifthOptional = true),
    MINOR_SEVENTH(0, 3, 7, 10, fifthOptional = true),
    SUS2(0, 2, 7),
    SUS4(0, 5, 7),
    ;

    /** The intervals as a 12-bit mask over pitch classes (bit i = i semitones above the root). */
    val mask: Int = intervals.fold(0) { m, i -> m or (1 shl i) }

    /** [mask] without the fifth, for [fifthOptional] chords. */
    val maskWithoutFifth: Int = mask and FIFTH_BIT.inv()
}

private const val FIFTH_BIT = 1 shl 7
