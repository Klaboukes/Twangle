package io.github.klaboukes.twangle.core.dsp.metronome

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Band-limited resampling of a short sound by a Hann-windowed sinc, read from a table and
 * interpolated between its entries (J. O. Smith, "Digital Audio Resampling", ccrma.stanford.edu/~jos/resample,
 * 2002). Used only while [ClickSounds] renders, so it may allocate.
 */
internal object Resampler {

    // Zero crossings of the sinc on each side of the centre, and table entries per zero crossing.
    private const val ZEROS = 16
    private const val PER_ZERO = 512

    // The windowed sinc h(t) = sinc(t) · hann(t / ZEROS) for t = 0 .. ZEROS in zero crossings.
    private val kernel = DoubleArray(ZEROS * PER_ZERO + 2) { i ->
        val t = i.toDouble() / PER_ZERO
        if (t >= ZEROS || (i > 0 && i % PER_ZERO == 0)) {
            0.0 // the sinc's zeros exactly, so whole positions are kept as they are
        } else {
            val sinc = if (i == 0) 1.0 else sin(PI * t) / (PI * t)
            sinc * (0.5 + 0.5 * cos(PI * t / ZEROS))
        }
    }

    /**
     * [x] at [fromRate] as heard at [toRate]. The output's sample m is the band-limited value at
     * input position m · fromRate / toRate, with a cutoff at 0.95 × the lower rate's Nyquist
     * frequency when going down (so nothing folds back), at the input's own when going up. It
     * ends on or just before the input's last sample, so it is as long as the input, give or take
     * a sample. When going up by a whole factor the input's samples are kept exactly.
     */
    fun resample(x: DoubleArray, fromRate: Int, toRate: Int): DoubleArray {
        require(x.isNotEmpty() && fromRate > 0 && toRate > 0)
        // Cutoff in units of the input's Nyquist frequency.
        val fc = minOf(1.0, 0.95 * toRate / fromRate)
        val halfWidth = ZEROS / fc // in input samples
        val frames = ((x.size - 1).toLong() * toRate / fromRate).toInt() + 1
        return DoubleArray(frames) { m ->
            val p = m.toDouble() * fromRate / toRate
            val first = maxOf(0, ceil(p - halfWidth).toInt())
            val last = minOf(x.size - 1, floor(p + halfWidth).toInt())
            var sum = 0.0
            for (n in first..last) sum += x[n] * h(abs(p - n) * fc)
            fc * sum
        }
    }

    // The windowed sinc at t zero crossings from its centre, linearly interpolated in the table.
    private fun h(t: Double): Double {
        val at = t * PER_ZERO
        val i = at.toInt()
        if (i >= ZEROS * PER_ZERO) return 0.0
        val frac = at - i
        return kernel[i] + frac * (kernel[i + 1] - kernel[i])
    }
}
