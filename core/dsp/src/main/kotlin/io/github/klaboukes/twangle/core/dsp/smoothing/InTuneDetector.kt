package io.github.klaboukes.twangle.core.dsp.smoothing

import kotlin.math.abs

/**
 * Decides when a note counts as "in tune": within [enterCents] of the target, held for at least
 * [holdSeconds] (docs/architecture/signal-pipeline.md: |cents| ≤ 5 for ≥ 300 ms, or ≤ 2 in the phone's
 * Precise setting).
 * Once in tune it stays in tune until the reading drifts past [exitCents], so a note hovering right
 * at the edge doesn't flicker between the two states. The phone's tuner and the watch both use it.
 *
 * Time is counted in analysis frames ([update] takes the frame duration), so it is exact and
 * doesn't depend on the clock. Allocation-free and not thread-safe.
 */
class InTuneDetector(
    val enterCents: Float = DEFAULT_ENTER_CENTS,
    val exitCents: Float = DEFAULT_EXIT_CENTS,
    val holdSeconds: Float = DEFAULT_HOLD_SECONDS,
) {
    init {
        require(enterCents > 0f) { "enterCents must be positive, was $enterCents" }
        require(exitCents >= enterCents) { "exitCents must be at least enterCents, was $exitCents" }
        require(holdSeconds >= 0f) { "holdSeconds must not be negative, was $holdSeconds" }
    }

    private var heldSeconds = 0f

    var inTune: Boolean = false
        private set

    /** Feeds one smoothed reading lasting [frameSeconds] and returns [inTune]. */
    fun update(cents: Float, frameSeconds: Float): Boolean {
        val deviation = abs(cents)
        if (inTune) {
            if (deviation > exitCents) reset()
        } else if (deviation <= enterCents) {
            heldSeconds += frameSeconds
            // A small epsilon so 300 ms made of float frame durations still counts as 300 ms.
            if (heldSeconds >= holdSeconds - EPSILON_SECONDS) inTune = true
        } else {
            heldSeconds = 0f
        }
        return inTune
    }

    /** Starts over, e.g. when the note stops or the target changes. */
    fun reset() {
        heldSeconds = 0f
        inTune = false
    }

    companion object {
        /** The default (the phone's Relaxed setting): in tune within 5 cents, released past 7. */
        const val DEFAULT_ENTER_CENTS = 5f
        const val DEFAULT_EXIT_CENTS = 7f
        const val DEFAULT_HOLD_SECONDS = 0.3f

        /** The phone's Precise setting: in tune within 2 cents, released past 3. */
        const val PRECISE_ENTER_CENTS = 2f
        const val PRECISE_EXIT_CENTS = 3f

        private const val EPSILON_SECONDS = 1e-4f
    }
}
