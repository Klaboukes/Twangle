package com.intune.core.dsp.pitch

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Runs [inner] on audio upsampled by [factor], for low sample rates such as a Bluetooth headset's
 * 8 or 16 kHz. At those rates a guitar note's period spans so few samples that YIN's sub-sample
 * refinement misses the ±1 cent target (≈ 5 cents at 8 kHz). Interpolating to ≥ 32 kHz first
 * gives the detector the resolution it is tuned for; the pitch of a steady tone is unchanged.
 *
 * The interpolator is a polyphase windowed-sinc FIR (Kaiser window, β = [KAISER_BETA]) with
 * [halfTaps] input samples on each side, cut off at 0.45 × the input rate, so images of the input
 * spectrum above its Nyquist frequency are suppressed (e.g. Smith, "Digital Audio Resampling Home
 * Page", and Oppenheim & Schafer, ch. 4.6 and 7.5). The first and last [halfTaps] input samples
 * only feed the filter: [windowSize] = `inner.windowSize / factor + 2 · halfTaps`.
 *
 * Nothing allocates after construction. Not thread-safe.
 */
class UpsamplingPitchDetector(
    val factor: Int,
    private val inner: PitchDetector,
    val halfTaps: Int = DEFAULT_HALF_TAPS,
) : PitchDetector {

    init {
        require(factor >= 2) { "factor must be at least 2, was $factor" }
        require(halfTaps >= 1) { "halfTaps must be positive, was $halfTaps" }
        require(inner.sampleRate % factor == 0) { "inner rate ${inner.sampleRate} is not a multiple of $factor" }
        require(inner.windowSize % factor == 0) { "inner window ${inner.windowSize} is not a multiple of $factor" }
    }

    override val sampleRate: Int = inner.sampleRate / factor

    override val windowSize: Int = inner.windowSize / factor + 2 * halfTaps

    private val taps = 2 * halfTaps

    // coefficients[p * taps + t]: weight of input sample q + halfTaps − t in output phase p.
    private val coefficients = FloatArray(factor * taps).also { c ->
        val cutoff = CUTOFF_FRACTION_OF_NYQUIST / factor // cycles per output sample × 2
        val span = (halfTaps * factor).toDouble()
        for (p in 0 until factor) {
            for (t in 0 until taps) {
                // Output qL + p from input i = q + halfTaps − t is h[(q − i)·L + p].
                val n = (t - halfTaps) * factor + p
                val window = kaiser(n / span)
                c[p * taps + t] = (factor * cutoff * sinc(cutoff * n) * window).toFloat()
            }
        }
    }

    private val upsampled = FloatArray(inner.windowSize)

    override fun detect(frame: FloatArray): PitchResult {
        require(frame.size >= windowSize) { "Frame must hold $windowSize samples, was ${frame.size}" }
        upsample(frame)
        return inner.detect(upsampled)
    }

    // Ideal interpolation y[qL + p] = Σ_i x[i] · h[(q − i)·L + p], where h is non-zero for the
    // 2 · halfTaps inputs i in q − halfTaps + 1..q + halfTaps. Written for q in halfTaps until
    // windowSize − halfTaps, so every tap reads a real sample.
    private fun upsample(frame: FloatArray) {
        var out = 0
        for (q in halfTaps until windowSize - halfTaps) {
            val newest = q + halfTaps
            for (p in 0 until factor) {
                val base = p * taps
                var sum = 0f
                for (t in 0 until taps) sum += frame[newest - t] * coefficients[base + t]
                upsampled[out++] = sum
            }
        }
    }

    private companion object {
        const val DEFAULT_HALF_TAPS = 16
        const val KAISER_BETA = 8.0

        // Cut-off at 0.45 × the input rate (0.9 × its Nyquist frequency).
        const val CUTOFF_FRACTION_OF_NYQUIST = 0.9

        fun sinc(x: Double): Double = if (x == 0.0) 1.0 else sin(PI * x) / (PI * x)

        // Kaiser window at x in −1..1 (0 outside): I0(β·√(1 − x²)) / I0(β).
        fun kaiser(x: Double): Double = if (x <= -1.0 || x >= 1.0) 0.0 else besselI0(KAISER_BETA * sqrt(1 - x * x)) / besselI0(KAISER_BETA)

        // Modified Bessel function of the first kind, order 0, by its power series Σ ((x/2)^k / k!)².
        fun besselI0(x: Double): Double {
            var sum = 1.0
            var term = 1.0
            var k = 1
            while (term > 1e-12 * sum) {
                term *= (x / (2 * k)) * (x / (2 * k))
                sum += term
                k++
            }
            return sum
        }
    }
}
