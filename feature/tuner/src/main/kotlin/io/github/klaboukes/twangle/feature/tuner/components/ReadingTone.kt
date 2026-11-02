package io.github.klaboukes.twangle.feature.tuner.components

import kotlin.math.abs

/**
 * How far the reading's tone has eased from amber (far off, 0) toward green (in tune, 1) for a note [cents]
 * off with the in-tune [window] (docs/architecture/v2-plan.md, *The lens*, the reading's tone):
 * `0.7 · clamp(1 − (|cents| − window) / 14)`, so amber from about 19 cents out and never quite green until
 * the tuner says in tune, which then takes it the rest of the way. The ruler's pointer takes it (Phase 71);
 * the lens's border and the screen's tint take the same. NaN (no note) gives 0.
 */
fun readingToneAmount(cents: Float, window: Float): Float {
    if (cents.isNaN()) return 0f
    return TONE_NEAR_SHARE * (1f - (abs(cents) - window) / TONE_EASE_CENTS).coerceIn(0f, 1f)
}

/** The most of the way to green a note gets before it is in tune. */
private const val TONE_NEAR_SHARE = 0.7f

/** Over how many cents outside the window the tone eases from amber. */
private const val TONE_EASE_CENTS = 14f
