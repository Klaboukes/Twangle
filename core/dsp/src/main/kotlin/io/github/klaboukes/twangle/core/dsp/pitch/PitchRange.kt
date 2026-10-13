package io.github.klaboukes.twangle.core.dsp.pitch

import kotlin.math.pow

/**
 * How low the detector listens, and so how long its window is. YIN needs a little over two
 * periods of the lowest note in the window, plus the low-pass filter's start-up and the longest
 * lag it searches (see [YinPitchDetector]).
 *
 * - [STANDARD]: 62–1500 Hz with a 4096-sample window (≈ 85 ms at 48 kHz). Covers the guitar
 *   (down to drop C) and keeps 60 Hz mains hum out.
 * - [LOW]: 28–1500 Hz with an 8192-sample window (≈ 171 ms), for instruments that go below that,
 *   down to B0 (30.9 Hz) on a five-string bass with room for a string tuned a semitone flat. It
 *   costs twice the CPU per frame and the reading lags a little more; 50/60 Hz hum is now in range.
 *
 * The hop stays the same, so readings still arrive ~47 times a second.
 */
enum class PitchRange(
    /** Lowest pitch reported, in Hz. */
    val minFrequencyHz: Float,
    /** Window length as a multiple of the standard ≈ 85 ms. */
    val windowMultiple: Int,
) {
    STANDARD(YinPitchDetector.DEFAULT_MIN_FREQUENCY_HZ, 1),
    LOW(LOW_MIN_FREQUENCY_HZ, 2),
    ;

    companion object {
        /**
         * The range that still reads [lowestNoteHz] (an instrument's or tuning's lowest open string,
         * at the current A4) when that string is up to a semitone flat. Every guitar preset stays
         * [STANDARD] (drop D at A4 = 415 Hz is 69 Hz, a semitone below 65 Hz); 7- and 8-string
         * guitars, basses and the cello (C2, 65.4 Hz) get [LOW].
         */
        fun forLowestNote(lowestNoteHz: Float): PitchRange =
            if (lowestNoteHz * SEMITONE_DOWN < STANDARD.minFrequencyHz) LOW else STANDARD

        // One equal-tempered semitone down: 2^(−1/12).
        private val SEMITONE_DOWN = 2.0.pow(-1.0 / 12).toFloat()
    }
}

/**
 * Below B0 at −50 cents (30.0 Hz) with room for the refinement around it. Still above A0 (27.5 Hz),
 * which no preset needs. At 48 kHz this is a longest lag of 1715 samples, which with the filter's
 * start-up and the fit's margin needs ≈ 8000 samples, so the window doubles to 8192.
 */
private const val LOW_MIN_FREQUENCY_HZ = 28f
