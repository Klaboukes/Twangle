package io.github.klaboukes.twangle.core.dsp.speech

/**
 * Which side of the note a reading is on, as TalkBack is told it: flat, sharp, in tune, or right on
 * the note before it has counted as in tune. Part of a spoken reading's key for
 * [SpokenReadingThrottle], with the note, on the phone and the watch alike: turning a string past
 * the note ("12 cents flat" to "5 cents sharp") is then a new situation, said after 400 ms like a new
 * note, not up to 2 s late as a change of the cents alone (Phase 65).
 */
enum class SpokenSide {
    FLAT,
    SHARP,
    IN_TUNE,
    ON_THE_NOTE,
    ;

    companion object {
        /** The side for whole [cents] (negative = flat) and whether the note counts as [inTune]. */
        fun of(cents: Int, inTune: Boolean): SpokenSide = when {
            inTune -> IN_TUNE
            cents < 0 -> FLAT
            cents > 0 -> SHARP
            else -> ON_THE_NOTE
        }
    }
}
