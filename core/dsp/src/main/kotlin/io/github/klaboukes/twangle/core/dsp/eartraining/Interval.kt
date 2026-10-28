package io.github.klaboukes.twangle.core.dsp.eartraining

/**
 * The intervals Ear training asks for, minor second to octave, by their size in equal-tempered
 * [semitones]. The names shown to players (short "P5" and long "5th") come from the app's string
 * resources, not from here.
 */
enum class Interval(val semitones: Int) {
    MINOR_SECOND(1),
    MAJOR_SECOND(2),
    MINOR_THIRD(3),
    MAJOR_THIRD(4),
    PERFECT_FOURTH(5),
    TRITONE(6),
    PERFECT_FIFTH(7),
    MINOR_SIXTH(8),
    MAJOR_SIXTH(9),
    MINOR_SEVENTH(10),
    MAJOR_SEVENTH(11),
    OCTAVE(12),
}

/** How an interval's two notes are played: the lower first ([UP]), the higher first ([DOWN]), or both at once. */
enum class IntervalDirection { UP, DOWN, TOGETHER }
