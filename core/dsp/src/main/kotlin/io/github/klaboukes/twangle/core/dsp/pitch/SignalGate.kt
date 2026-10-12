package io.github.klaboukes.twangle.core.dsp.pitch

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Decides whether a frame is loud enough to analyse, after removing its DC offset.
 *
 * Levels are RMS in dBFS: `20 · log10(rms)`, where a full-scale square wave is 0 dBFS and a
 * full-scale sine is about −3 dBFS. The gate opens at [thresholdDbfs] and only closes again once
 * the level drops [hysteresisDb] below it, so a slowly decaying note doesn't flicker on and off
 * around the threshold. It also measures the frame's [peakDbfs] (before DC removal, as the mic
 * delivered it), which shows when the input clips. Nothing allocates.
 */
class SignalGate(
    val thresholdDbfs: Float = DEFAULT_THRESHOLD_DBFS,
    val hysteresisDb: Float = DEFAULT_HYSTERESIS_DB,
) {

    init {
        require(thresholdDbfs in SILENCE_DBFS..0f) { "thresholdDbfs must be in $SILENCE_DBFS..0, was $thresholdDbfs" }
        require(hysteresisDb >= 0f) { "hysteresisDb must not be negative, was $hysteresisDb" }
    }

    /** Level of the most recent frame passed to [process], in dBFS. */
    var levelDbfs: Float = SILENCE_DBFS
        private set

    /**
     * Peak of the most recent frame passed to [process], in dBFS: `20 · log10(max |sample|)`, measured
     * before DC removal. 0 dBFS means the input reached full scale, so it is probably clipping.
     */
    var peakDbfs: Float = SILENCE_DBFS
        private set

    /** Whether the most recent frame was loud enough to analyse. */
    var isOpen: Boolean = false
        private set

    /**
     * Removes the DC offset from the first [length] samples of [frame] (in place), measures their
     * level and updates the gate.
     *
     * @return true if the frame should be analysed.
     */
    fun process(frame: FloatArray, length: Int = frame.size): Boolean {
        peakDbfs = peakDbfs(frame, length)
        removeDc(frame, length)
        levelDbfs = rmsDbfs(frame, length)
        val closeBelow = if (isOpen) thresholdDbfs - hysteresisDb else thresholdDbfs
        isOpen = levelDbfs >= closeBelow
        return isOpen
    }

    /** Closes the gate and forgets the last level, e.g. when the microphone restarts. */
    fun reset() {
        levelDbfs = SILENCE_DBFS
        peakDbfs = SILENCE_DBFS
        isOpen = false
    }

    companion object {
        /** Below about −50 dBFS a phone mic picks up mostly room noise rather than a string. */
        const val DEFAULT_THRESHOLD_DBFS = -50f
        const val DEFAULT_HYSTERESIS_DB = 6f

        /** Floor reported for digital silence instead of −∞. */
        const val SILENCE_DBFS = -120f

        /** Subtracts the mean of the first [length] samples from each of them, in place. */
        fun removeDc(frame: FloatArray, length: Int = frame.size) {
            requireLength(frame, length)
            if (length == 0) return
            var sum = 0.0
            for (i in 0 until length) sum += frame[i]
            val mean = (sum / length).toFloat()
            for (i in 0 until length) frame[i] -= mean
        }

        /** RMS level of the first [length] samples in dBFS, floored at [SILENCE_DBFS]. */
        fun rmsDbfs(frame: FloatArray, length: Int = frame.size): Float {
            requireLength(frame, length)
            if (length == 0) return SILENCE_DBFS
            var sumSquares = 0.0
            for (i in 0 until length) {
                val s = frame[i].toDouble()
                sumSquares += s * s
            }
            val rms = sqrt(sumSquares / length)
            if (rms == 0.0) return SILENCE_DBFS
            return max(SILENCE_DBFS, (20.0 * log10(rms)).toFloat())
        }

        /** Peak level (largest absolute sample) of the first [length] samples in dBFS, floored at [SILENCE_DBFS]. */
        fun peakDbfs(frame: FloatArray, length: Int = frame.size): Float {
            requireLength(frame, length)
            var peak = 0f
            for (i in 0 until length) peak = max(peak, abs(frame[i]))
            if (peak == 0f) return SILENCE_DBFS
            return max(SILENCE_DBFS, (20.0 * log10(peak.toDouble())).toFloat())
        }

        private fun requireLength(frame: FloatArray, length: Int) {
            require(length in 0..frame.size) { "length must be in 0..${frame.size}, was $length" }
        }
    }
}
