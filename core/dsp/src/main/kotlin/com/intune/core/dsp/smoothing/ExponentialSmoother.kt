package com.intune.core.dsp.smoothing

/**
 * Exponential moving average: `y ← y + alpha · (x − y)`. Each new value pulls the output
 * [alpha] of the way towards it, so small jitter is averaged out while a steady change is
 * followed within a few updates. The first value is taken as is.
 */
class ExponentialSmoother(val alpha: Float) {

    init {
        require(alpha > 0f && alpha <= 1f) { "alpha must be in (0, 1], was $alpha" }
    }

    /** Current smoothed value, or NaN before the first [update]. */
    var value: Float = Float.NaN
        private set

    fun update(x: Float): Float {
        value = if (value.isNaN()) x else value + alpha * (x - value)
        return value
    }

    /** Jumps straight to [x], e.g. when a new note starts and sliding to it would mislead. */
    fun snapTo(x: Float) {
        value = x
    }

    fun reset() {
        value = Float.NaN
    }
}
