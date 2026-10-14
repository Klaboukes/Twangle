package io.github.klaboukes.twangle.core.dsp.strum

/**
 * What [StrumDetector.detect] found for each expected string of one strum, filled in place so a
 * reading allocates nothing. Index `i` is string `i` of the expected frequencies passed in (the
 * tuning's order); only the first [stringCount] entries are meaningful.
 */
class StrumResult(val capacity: Int) {

    init {
        require(capacity > 0) { "capacity must be positive, was $capacity" }
    }

    /** How many strings the last [StrumDetector.detect] call looked for. */
    var stringCount: Int = 0
        internal set

    /** Measured frequency of each string in Hz, or NaN when the string was not heard. */
    val frequencyHz = FloatArray(capacity)

    /** Deviation of each string from its expected frequency in cents (positive = sharp), NaN when not heard. */
    val cents = FloatArray(capacity)

    /** Each string's strongest partial in dB relative to the loudest string's (0 for the loudest, ≤ 0 otherwise). */
    val levelDb = FloatArray(capacity)

    /** True when the string was heard: its partials stand out of the frame (see [StrumDetector]). */
    val present = BooleanArray(capacity)

    /**
     * True when every partial of the string sat on top of another string's partial, so the reading
     * comes from merged peaks: it is pulled towards that other string's harmonic, and the string
     * could also be missing (see ARCHITECTURE.md, *Strum check*).
     */
    val shadowed = BooleanArray(capacity)
}
