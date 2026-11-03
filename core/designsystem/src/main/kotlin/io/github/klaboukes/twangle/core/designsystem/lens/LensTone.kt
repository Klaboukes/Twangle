package io.github.klaboukes.twangle.core.designsystem.lens

import io.github.klaboukes.twangle.core.dsp.smoothing.InTuneDetector
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import kotlin.math.abs

/**
 * How far the reading's tone has eased from amber (far off, 0) toward green (in tune, 1) for a note [cents]
 * off with the in-tune [window] (docs/architecture/v2-plan.md, *The lens*, the reading's tone):
 * `0.7 · clamp(1 − (|cents| − window) / 14)`, so amber from about 19 cents out and never quite green until
 * the tuner says in tune, which then takes it the rest of the way ([readingToneShare]). NaN (no note) gives 0.
 */
fun readingToneAmount(cents: Float, window: Float): Float {
    if (cents.isNaN()) return 0f
    return TONE_NEAR_SHARE * (1f - (abs(cents) - window) / TONE_EASE_CENTS).coerceIn(0f, 1f)
}

/**
 * The whole way the reading's tone has gone from amber toward green (0..1), shared by the ruler's pointer, the
 * lens, the word and the screen's tint (Phase 71) and the Settings cards (72): [readingToneAmount] for a note
 * [cents] off, and [tuned] (the tuner's "in tune", eased in and out, 0..1) taking it the rest of the way to green.
 *
 * "In tune" fades out over 0.6 s once the tuner lets go of it, and a string bent or turned away moves faster
 * than that: on the phone the word read "Too low, 13 cents flat" still mostly green (Phase 72). So the in-tune
 * share also lets go with the reading: whole up to the tuner's own release, [exit] cents out (7 Relaxed, 3
 * Precise, [meterInTuneExit], so it never pales while the tuner still says in tune), gone [TUNED_LET_GO_CENTS]
 * further out, whatever the fade's clock says. A reading that far out is shown as the cents call for: about a
 * third of the way from amber at 13 cents with Relaxed. NaN (the note died away) keeps [tuned] as it fades.
 */
fun readingToneShare(cents: Float, window: Float, exit: Float, tuned: Float): Float {
    val near = readingToneAmount(cents, window)
    val letGo = if (cents.isNaN()) 1f else (1f - (abs(cents) - exit) / TUNED_LET_GO_CENTS).coerceIn(0f, 1f)
    return near + (1f - near) * tuned.coerceIn(0f, 1f) * letGo
}

/** How far past the tuner's release ([meterInTuneExit]) the in-tune green has gone from the tone. */
const val TUNED_LET_GO_CENTS = 4f

/** Where the tuner lets go of "in tune" with [precision], in cents each way: 7 Relaxed, 3 Precise (its [InTuneDetector]). */
fun meterInTuneExit(precision: InTunePrecision): Float = when (precision) {
    InTunePrecision.RELAXED -> InTuneDetector.DEFAULT_EXIT_CENTS
    InTunePrecision.PRECISE -> InTuneDetector.PRECISE_EXIT_CENTS
}

/** The most of the way to green a note gets before it is in tune. */
private const val TONE_NEAR_SHARE = 0.7f

/** Over how many cents outside the window the tone eases from amber. */
private const val TONE_EASE_CENTS = 14f
