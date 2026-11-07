package io.github.klaboukes.twangle.core.dsp.speech

/**
 * Which side of the note a reading is on, as TalkBack is told it: flat, sharp, in tune, or right on
 * the note before it has counted as in tune. The watch's spoken text picks its words by it ([of], from
 * the cents); a spoken reading's key ([SpokenReadingKey]) takes it from the word on screen instead
 * (Tighten → [FLAT], Loosen → [SHARP], In tune → [IN_TUNE]), which is held steady near the note, so a
 * reading wobbling across the note isn't a new situation for TalkBack (Phase 81).
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

/**
 * The situation a played note's spoken reading belongs to, for [SpokenReadingThrottle]: the [note]
 * (name and octave, e.g. "E2") and which way the word on screen says to turn the peg, [side] ([SpokenSide.FLAT]
 * for Tighten or Too low, [SpokenSide.SHARP] for Loosen or Too high, [SpokenSide.IN_TUNE]; null while the
 * word hasn't settled), and [words]: whatever else the reading says in words, each held steady where it comes
 * from (on the phone a tip, "Play a bit louder", and the pitch trail's motion, "wavering"; null for none).
 * A reading is said once per situation: the cents moving inside it never make TalkBack speak again, so a
 * reading it misheard can't set off the next one (Phase 81). The phone and the watch key their readings alike.
 */
data class SpokenReadingKey(val note: String, val side: SpokenSide?, val words: String? = null)
