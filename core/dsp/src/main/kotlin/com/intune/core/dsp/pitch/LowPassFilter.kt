package com.intune.core.dsp.pitch

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * 4th-order Butterworth low-pass filter, built from two biquad sections using the coefficient
 * formulas of R. Bristow-Johnson's "Audio EQ Cookbook". Section Qs `1 / (2·cos(π/8))` and
 * `1 / (2·cos(3π/8))` together give the maximally flat Butterworth response.
 *
 * [process] filters one frame starting from zero state, so frames are independent of each other.
 * The first samples of each output are a start-up transient; [settleSamples] says how many to
 * skip. Nothing allocates after construction.
 */
internal class LowPassFilter(sampleRate: Int, cutoffHz: Float) {

    init {
        require(cutoffHz > 0f && cutoffHz < sampleRate / 2f) { "cutoffHz must be in (0, Nyquist), was $cutoffHz" }
    }

    /**
     * Samples after which the start-up transient has decayed below 10⁻⁸ (e^−18.5). The slower,
     * high-Q section decays by `e^(−ω0 / (2Q))` per sample, where `1 / (2Q) = cos(3π/8)`.
     * 92 samples at the default 4 kHz / 48 kHz.
     */
    val settleSamples: Int = ceil(18.5 / (2 * PI * cutoffHz / sampleRate * cos(3 * PI / 8))).toInt()

    private val first = Section(sampleRate, cutoffHz, q = 1.0 / (2 * cos(PI / 8)))
    private val second = Section(sampleRate, cutoffHz, q = 1.0 / (2 * cos(3 * PI / 8)))

    /** Writes the filtered first [length] samples of [input] into [output]. */
    fun process(input: FloatArray, output: FloatArray, length: Int) {
        first.reset()
        second.reset()
        for (i in 0 until length) {
            output[i] = second.next(first.next(input[i].toDouble())).toFloat()
        }
    }

    // Transposed direct form II biquad (Cookbook LPF coefficients, normalised by a0).
    private class Section(sampleRate: Int, cutoffHz: Float, q: Double) {
        private val b0: Double
        private val b1: Double
        private val b2: Double
        private val a1: Double
        private val a2: Double
        private var z1 = 0.0
        private var z2 = 0.0

        init {
            val w0 = 2 * PI * cutoffHz / sampleRate
            val alpha = sin(w0) / (2 * q)
            val a0 = 1 + alpha
            b1 = (1 - cos(w0)) / a0
            b0 = b1 / 2
            b2 = b0
            a1 = -2 * cos(w0) / a0
            a2 = (1 - alpha) / a0
        }

        fun reset() {
            z1 = 0.0
            z2 = 0.0
        }

        fun next(x: Double): Double {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }
    }
}
