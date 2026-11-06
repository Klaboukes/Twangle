package io.github.klaboukes.twangle.core.dsp.metronome

import kotlin.math.roundToInt

/**
 * Tap tempo: the tempo of the last few taps. Each [tap] gives the average beat of the taps so far
 * (at most [MAX_TAPS], so the last five intervals), as whole BPM within 30–250
 * ([ClickTrack.clampBpm]); the first tap gives nothing. A pause of more than [RESET_AFTER_MILLIS]
 * starts afresh, so the next tap is a first one again. The average is the time from the oldest kept
 * tap to the newest divided by the intervals between them, so one early or late tap only shifts the
 * result by its share. Allocates nothing.
 */
class TapTempo {
    // The kept taps' times, oldest first, in a ring.
    private val times = LongArray(MAX_TAPS)
    private var first = 0
    private var count = 0

    /** Notes a tap at [atMillis] (any steady millisecond clock) and returns the new tempo, or null. */
    fun tap(atMillis: Long): Int? {
        if (count > 0) {
            val last = times[(first + count - 1) % MAX_TAPS]
            // A tap at the same moment (or a clock going back) can't be timed: start afresh from it.
            if (atMillis - last > RESET_AFTER_MILLIS || atMillis <= last) count = 0
        }
        if (count == MAX_TAPS) {
            first = (first + 1) % MAX_TAPS
            count--
        }
        if (count == 0) first = 0
        times[(first + count) % MAX_TAPS] = atMillis
        count++
        if (count < 2) return null
        val span = atMillis - times[first]
        return ClickTrack.clampBpm((MILLIS_PER_MINUTE * (count - 1) / span).roundToInt())
    }

    /** Forgets every tap. */
    fun reset() {
        count = 0
    }

    companion object {
        /** The taps kept: the average is over the last five intervals. */
        const val MAX_TAPS = 6

        /** A longer pause than this (the beat of 30 BPM) starts afresh. */
        const val RESET_AFTER_MILLIS = 2_000L

        private const val MILLIS_PER_MINUTE = 60_000.0
    }
}

/**
 * The Italian tempo marking a tempo is usually given, from the slowest: each covers the tempos from
 * the previous one's [below] up to just under its own. The bounds are those of the approved mock-up
 * (docs/design/1.x/metronome-nav-preview.html), close to a common metronome's printed scale (Grave under
 * 40, Largo 40–59, Larghetto 60–65, Adagio 66–75, Andante 76–107, Moderato 108–119, Allegro 120–167,
 * Presto 168–199, Prestissimo 200 and up). The words are the same in every language.
 */
enum class TempoMarking(val below: Int) {
    GRAVE(40),
    LARGO(60),
    LARGHETTO(66),
    ADAGIO(76),
    ANDANTE(108),
    MODERATO(120),
    ALLEGRO(168),
    PRESTO(200),
    PRESTISSIMO(Int.MAX_VALUE),
    ;

    companion object {
        /** The marking for [bpm]. */
        fun of(bpm: Int): TempoMarking = entries.first { bpm < it.below }
    }
}
