package io.github.klaboukes.twangle.core.dsp.metronome

/** How strongly a beat (or a click between beats) is played, and so which click sounds on it (see [ClickSounds]). */
enum class BeatAccent {
    /** The first beat of a bar: the highest and loudest click. */
    DOWNBEAT,

    /** The first beat of a later group in a bar (5/4, 6/8, 7/8): a middle click. */
    GROUP,

    /** Every other beat. */
    PLAIN,

    /**
     * A click between the beats ([Subdivision]): the softest, at its own pitch. Never a beat's
     * accent: [Meter.accentOf] doesn't give it.
     */
    SUBDIVISION,
}

/**
 * The metronome's time signatures. The metronome clicks once per beat of the bar, so [beatsPerBar]
 * clicks make a bar, and the tempo (BPM) counts those clicks: in 6/8 and 7/8 that is one click per
 * eighth note, as most practice metronomes do.
 *
 * Accents (beat numbers counted from 1):
 * - 2/4, 3/4, 4/4: only beat 1 is accented.
 * - 5/4: grouped 3 + 2, the usual reading (e.g. Brubeck's *Take Five*): beat 1, and a group accent on 4.
 * - 6/8: compound duple, two groups of three eighths: beat 1, and a group accent on 4.
 * - 7/8: grouped 2 + 2 + 3, the most common grouping in Balkan and rock music: beat 1, and group
 *   accents on 3 and 5.
 *
 * Saved in settings by [name] (the metronome's meter), so don't rename the entries.
 *
 * @property groups lengths of the beat groups in a bar, in order; they add up to [beatsPerBar].
 */
enum class Meter(val beatsPerBar: Int, val noteValue: Int, private val groups: IntArray) {
    TWO_FOUR(2, 4, intArrayOf(2)),
    THREE_FOUR(3, 4, intArrayOf(3)),
    FOUR_FOUR(4, 4, intArrayOf(4)),
    FIVE_FOUR(5, 4, intArrayOf(3, 2)),
    SIX_EIGHT(6, 8, intArrayOf(3, 3)),
    SEVEN_EIGHT(7, 8, intArrayOf(2, 2, 3)),
    ;

    // One entry per beat of the bar, worked out once.
    private val accents: Array<BeatAccent> = Array(beatsPerBar) { BeatAccent.PLAIN }.also { accents ->
        check(groups.sum() == beatsPerBar) { "groups of $name must fill the bar" }
        var start = 0
        for (length in groups) {
            accents[start] = if (start == 0) BeatAccent.DOWNBEAT else BeatAccent.GROUP
            start += length
        }
    }

    /** The accent of beat [beatInBar], counted from 0 (the downbeat) to [beatsPerBar] − 1. */
    fun accentOf(beatInBar: Int): BeatAccent {
        require(beatInBar in 0 until beatsPerBar) { "beatInBar must be in 0 until $beatsPerBar, was $beatInBar" }
        return accents[beatInBar]
    }

    companion object {
        /** The meter a new metronome starts in. */
        val DEFAULT = FOUR_FOUR
    }
}
