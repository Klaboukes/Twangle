package io.github.klaboukes.twangle.core.dsp.recordings

import io.github.klaboukes.twangle.core.dsp.pitch.Fft
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The partials of one stretch of a recorded string, measured from its spectrum, to compare the real
 * guitar with the test models (`StrumChords.stiffStrum`: partial k at `k·f₀·√(1 + B·k²)`, starting
 * at 1/k for a pick or 1/k² for a finger). A Hann window of [windowSize] samples from [from], zero-padded
 * eight times, and each partial read at the loudest bin near `k × partial 1` with a parabola over the
 * log magnitudes (Smith, *Spectral Audio Signal Processing*, "Quadratic interpolation of spectral peaks").
 */
class Partials(
    samples: FloatArray,
    val sampleRate: Int,
    from: Int,
    /** The nominal fundamental, to find partial 1 within ±3 % (±50 cents) of it. */
    nominalHz: Float,
    val count: Int = DEFAULT_COUNT,
    val windowSize: Int = DEFAULT_WINDOW,
) {
    /** Frequency of partial k (index k − 1) in Hz, or NaN when it isn't there. */
    val hz = DoubleArray(count) { Double.NaN }

    /** Level of partial k in dBFS (a full-scale sine is 0), or NaN. */
    val dbfs = DoubleArray(count) { Double.NaN }

    /** The level of the bins around each partial, in dBFS: what a partial has to rise above. */
    val floorDbfs = DoubleArray(count) { Double.NaN }

    init {
        val fftSize = windowSize * PAD
        val fft = Fft(fftSize)
        val re = DoubleArray(fftSize)
        val im = DoubleArray(fftSize)
        for (i in 0 until windowSize) {
            val s = if (from + i < samples.size) samples[from + i].toDouble() else 0.0
            re[i] = s * (0.5 - 0.5 * cos(2 * PI * i / windowSize))
        }
        fft.forward(re, im)
        // A sine of amplitude 1 under a Hann window of N samples peaks at N/4; dBFS as the gate has
        // it (a full-scale sine ≈ −3 dBFS in RMS terms, 0 here by its peak).
        val scale = 4.0 / windowSize
        val magnitudeDb = DoubleArray(fftSize / 2) { 20 * log10(max(1e-12, hypot(re[it], im[it]) * scale)) }
        val binHz = sampleRate.toDouble() / fftSize
        // The floor a partial must rise above: the bins half-way to the next partial, where only
        // noise and the window's side lobes are (a partial's main lobe is 4 bins of the window wide).
        find(magnitudeDb, binHz, 0, nominalHz * (1 - NOMINAL_SLACK), nominalHz * (1 + NOMINAL_SLACK), nominalHz * 1.3, nominalHz * 1.7)
        val f1 = hz[0]
        if (!f1.isNaN()) {
            // At least a main lobe (4 bins of the window) each side, so the search holds a whole peak.
            val lobeHz = 4.0 * sampleRate / windowSize
            for (k in 2..count) {
                // Stiffness puts partial k up to 0.5·B·k² sharp of k·f₁ (B up to 4·10⁻⁴ here).
                find(
                    magnitudeDb, binHz, k - 1,
                    min(k * f1 * (1 - PARTIAL_SLACK), k * f1 - lobeHz),
                    max(k * f1 * (1 + PARTIAL_SLACK + 0.5 * MAX_B * k * k), k * f1 + lobeHz),
                    (k + 0.3) * f1, (k + 0.7) * f1,
                )
            }
        }
    }

    /** Level of partial k relative to partial 1, in dB, or NaN. */
    fun relativeDb(k: Int): Double = dbfs[k - 1] - dbfs[0]

    /**
     * The stiff-string series through partials 3 and up (Fletcher 1964: `f_k = k·f₀·√(1 + B·k²)`,
     * so `(f_k / k)² = f₀² + f₀²·B·k²`, a straight line in k², fitted by least squares): [seriesHz]
     * is its `f₀`, where the upper partials say the fundamental is, and [inharmonicity] its B. The
     * lowest two partials are left out: on a real guitar they sit a few cents from the series, pulled
     * by the body's resonances near them. NaN with fewer than three partials.
     */
    val seriesHz: Double
    val inharmonicity: Double

    init {
        var n = 0
        var sx = 0.0
        var sy = 0.0
        var sxx = 0.0
        var sxy = 0.0
        for (k in 3..count) {
            val f = hz[k - 1]
            if (f.isNaN()) continue
            val x = (k * k).toDouble()
            val y = (f / k) * (f / k)
            n++
            sx += x
            sy += y
            sxx += x * x
            sxy += x * y
        }
        if (n < 3) {
            seriesHz = Double.NaN
            inharmonicity = Double.NaN
        } else {
            val slope = (n * sxy - sx * sy) / (n * sxx - sx * sx)
            val intercept = (sy - slope * sx) / n
            seriesHz = sqrt(intercept)
            inharmonicity = slope / intercept
        }
    }

    private fun find(magnitudeDb: DoubleArray, binHz: Double, index: Int, lowHz: Double, highHz: Double, floorLowHz: Double, floorHighHz: Double) {
        val low = max(1, (lowHz / binHz).roundToInt())
        val high = min(magnitudeDb.size - 2, (highHz / binHz).roundToInt())
        val floorLow = max(1, (floorLowHz / binHz).roundToInt())
        val floorHigh = min(magnitudeDb.size - 1, (floorHighHz / binHz).roundToInt())
        if (high - low < 3 || floorHigh - floorLow < 3) return
        var peak = low
        for (b in low..high) if (magnitudeDb[b] > magnitudeDb[peak]) peak = b
        val sorted = magnitudeDb.copyOfRange(floorLow, floorHigh + 1).sortedArray()
        floorDbfs[index] = sorted[sorted.size / 2]
        // A real partial stands well above the bins around it and isn't the range's edge.
        if (peak == low || peak == high || magnitudeDb[peak] < floorDbfs[index] + MIN_ABOVE_FLOOR_DB) return
        val a = magnitudeDb[peak - 1]
        val b = magnitudeDb[peak]
        val c = magnitudeDb[peak + 1]
        val p = 0.5 * (a - c) / (a - 2 * b + c)
        hz[index] = (peak + p) * binHz
        dbfs[index] = b - 0.25 * (a - c) * p
    }

    companion object {
        const val DEFAULT_COUNT = 12
        const val DEFAULT_WINDOW = 8192
        private const val PAD = 8
        private const val NOMINAL_SLACK = 0.03
        private const val PARTIAL_SLACK = 0.004
        private const val MAX_B = 4e-4
        private const val MIN_ABOVE_FLOOR_DB = 12.0

        /** Level of partial k in a pick's attack: 1/k. */
        fun pickModelDb(k: Int): Double = -20 * log10(k.toDouble())

        /** Level of partial k in a finger's pluck: 1/k². */
        fun fingerModelDb(k: Int): Double = -40 * log10(k.toDouble())

        /** The strum tests' inharmonicity B, low E to high E (`StrumDetectorTest`, `EdgeCaseTest`). */
        val MODEL_B = doubleArrayOf(1.5e-4, 1e-4, 6e-5, 4e-5, 3e-5, 2e-5)

        /** The cents partial 1 sits above `f₀` at inharmonicity [b]: `1200·log2(√(1 + B))`. */
        fun fundamentalStretchCents(b: Double): Double = 1200 * kotlin.math.log2(sqrt(1 + b))
    }
}
