package io.github.klaboukes.twangle.core.dsp.smoothing

/**
 * Running median of the last [size] values. A single wild value can't move the median, which
 * makes it the right first stage against one-frame glitches. Nothing allocates after construction.
 */
class MedianFilter(val size: Int) {

    init {
        require(size > 0) { "size must be positive, was $size" }
    }

    private val history = FloatArray(size)
    private val sorted = FloatArray(size)
    private var next = 0

    /** Number of values held, up to [size]. */
    var count: Int = 0
        private set

    /**
     * Adds [value], dropping the oldest once [size] values are held, and returns the new median.
     * With an even number of values the median is the mean of the two middle ones.
     */
    fun add(value: Float): Float {
        history[next] = value
        next = (next + 1) % size
        if (count < size) count++
        return median
    }

    /** Median of the values held, or NaN when empty. */
    val median: Float
        get() {
            if (count == 0) return Float.NaN
            // Insertion sort: at most a handful of values, so this beats anything fancier.
            for (i in 0 until count) {
                val v = history[i]
                var j = i - 1
                while (j >= 0 && sorted[j] > v) {
                    sorted[j + 1] = sorted[j]
                    j--
                }
                sorted[j + 1] = v
            }
            val mid = count / 2
            return if (count % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
        }

    fun reset() {
        count = 0
        next = 0
    }
}
