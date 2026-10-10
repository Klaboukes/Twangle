package io.github.klaboukes.twangle.core.dsp.smoothing

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

    /**
     * Pulls the output towards [x]. A [weight] below 1 (0..1) trusts this value less: the step is
     * `alpha · weight` of the way instead of [alpha].
     */
    fun update(x: Float, weight: Float = 1f): Float {
        value = if (value.isNaN()) x else value + alpha * weight * (x - value)
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
