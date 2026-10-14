package io.github.klaboukes.twangle.core.dsp.strum

import kotlin.math.abs

/**
 * What the strum check shows for one string: not heard, or flat, in tune or sharp of its expected
 * note. See ARCHITECTURE.md, *Strum check (polyphonic)*.
 */
enum class StrumVerdict {
    NOT_HEARD,
    FLAT,
    IN_TUNE,
    SHARP,
    ;

    companion object {
        /** The tuner's Relaxed in-tune tolerance (§3, *Key numbers*); Precise is 2. */
        const val DEFAULT_TOLERANCE_CENTS = 5f

        /**
         * Extra tolerance for a [StrumResult.shadowed] string, whose reading comes from peaks merged
         * with another string's harmonics: such readings were measured up to 2.3 cents off on
         * synthetic strums (341 ms frame), so a string is only called flat or sharp when that
         * can't be the merge alone.
         */
        const val SHADOWED_MARGIN_CENTS = 2.5f

        /**
         * A string that was [IN_TUNE] in the previous reading stays so until this much past the
         * tolerance, so a reading hovering at the edge doesn't flicker (like the tuner's 5/7 cents).
         */
        const val RELEASE_CENTS = 1f

        /**
         * The verdict for a string [cents] off its expected note (NaN when not heard). [previous]
         * is this string's verdict from the last reading of the same strum, if any.
         */
        fun of(
            cents: Float,
            present: Boolean,
            shadowed: Boolean,
            toleranceCents: Float = DEFAULT_TOLERANCE_CENTS,
            previous: StrumVerdict? = null,
        ): StrumVerdict {
            require(toleranceCents >= 0f) { "toleranceCents must not be negative, was $toleranceCents" }
            if (!present || cents.isNaN()) return NOT_HEARD
            var limit = toleranceCents
            if (shadowed) limit += SHADOWED_MARGIN_CENTS
            if (previous == IN_TUNE) limit += RELEASE_CENTS
            return when {
                abs(cents) <= limit -> IN_TUNE
                cents < 0f -> FLAT
                else -> SHARP
            }
        }

        /** The verdict for string [index] of [result]. */
        fun of(
            result: StrumResult,
            index: Int,
            toleranceCents: Float = DEFAULT_TOLERANCE_CENTS,
            previous: StrumVerdict? = null,
        ): StrumVerdict {
            require(index in 0 until result.stringCount) { "no string $index in a result of ${result.stringCount}" }
            return of(result.cents[index], result.present[index], result.shadowed[index], toleranceCents, previous)
        }
    }
}
