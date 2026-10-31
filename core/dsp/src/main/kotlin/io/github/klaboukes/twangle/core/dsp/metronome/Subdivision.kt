package io.github.klaboukes.twangle.core.dsp.metronome

/**
 * Clicks between the beats (Phase 62): every beat split into [clicksPerBeat] even parts, the first
 * the beat's own click and the others the softest click, [BeatAccent.SUBDIVISION]. [ClickTrack]
 * places each on its exact sample.
 *
 * In the quarter-note meters (2/4 to 5/4) [TWO] plays eighths, [THREE] triplets and [FOUR]
 * sixteenths. In 6/8 and 7/8 a click is already an eighth, so they offer only [NONE] and [TWO]
 * (sixteenths): see [isOfferedIn]. A subdivision a meter doesn't offer plays as [NONE] there
 * ([within]).
 *
 * Saved in settings by [name] (the metronome's subdivision), so don't rename the entries.
 */
enum class Subdivision(val clicksPerBeat: Int) {
    /** One click per beat, as the metronome always did: the default. */
    NONE(1),

    /** Two clicks per beat: eighths in 2/4 to 5/4, sixteenths in 6/8 and 7/8. */
    TWO(2),

    /** Three clicks per beat: triplets (2/4 to 5/4 only). */
    THREE(3),

    /** Four clicks per beat: sixteenths (2/4 to 5/4 only). */
    FOUR(4),
    ;

    /** Whether [meter] offers it: every one in 2/4 to 5/4; in 6/8 and 7/8, where a click is already an eighth, [NONE] and [TWO]. */
    fun isOfferedIn(meter: Meter): Boolean = meter.noteValue == QUARTER || clicksPerBeat <= TWO.clicksPerBeat

    /** What plays in [meter]: this subdivision, or [NONE] where [meter] doesn't offer it. */
    fun within(meter: Meter): Subdivision = if (isOfferedIn(meter)) this else NONE

    companion object {
        /** The subdivision of a new metronome. */
        val DEFAULT = NONE

        private const val QUARTER = 4

        /** The subdivisions [meter] offers, in order. */
        fun offeredIn(meter: Meter): List<Subdivision> = entries.filter { it.isOfferedIn(meter) }
    }
}
