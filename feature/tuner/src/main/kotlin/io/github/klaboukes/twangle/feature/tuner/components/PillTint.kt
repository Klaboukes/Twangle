package io.github.klaboukes.twangle.feature.tuner.components

import kotlin.math.abs

/**
 * How much of the style's "in tune" colour the cents pill takes for a reading [cents] away that is not
 * (yet) in tune: none from [PILL_TINT_FROM_CENTS] off, rising smoothly to [PILL_NEAR_TINT] at the
 * target. Only being in tune fills it completely, so a full pill (with its check mark) keeps meaning
 * "in tune"; the tint just says "getting close", without a direction (that is the meter's job).
 * Smoothstep, so the colour starts and ends its change gently.
 */
internal fun pillTint(cents: Float): Float {
    val closeness = (1f - abs(cents) / PILL_TINT_FROM_CENTS).coerceIn(0f, 1f)
    return PILL_NEAR_TINT * closeness * closeness * (3f - 2f * closeness)
}

/** Further off than this, the pill shows no "in tune" colour. */
internal const val PILL_TINT_FROM_CENTS = 25f

/** The most "in tune" colour the pill takes before the note is in tune; low, so its normal text stays readable. */
internal const val PILL_NEAR_TINT = 0.4f
