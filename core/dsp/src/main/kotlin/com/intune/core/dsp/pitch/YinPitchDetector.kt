package com.intune.core.dsp.pitch

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The YIN fundamental-frequency estimator (de Cheveigné & Kawahara 2002, "YIN, a fundamental
 * frequency estimator for speech and music", JASA 111(4)), steps 2–5, plus a low-pass pre-filter
 * and a noise-robust period refinement:
 *
 * 1. pre-filter: 4th-order Butterworth low-pass at [lowPassHz] into a scratch buffer (the caller's
 *    frame is not modified). This keeps the dip of `d(τ)` smooth at the sample scale even for very
 *    bright tones, so sub-sample interpolation stays accurate. It also removes hiss above the cutoff.
 * 2. difference function `d(τ) = Σ_{j<W} (x_j − x_{j+τ})²`
 * 3. cumulative mean normalized difference `d'(τ) = d(τ) / ((1/τ) Σ_{k=1..τ} d(k))`, `d'(0) = 1`
 * 4. absolute threshold: the first τ whose `d'(τ)` dips below [threshold], followed down to its
 *    local minimum. Taking the *first* dip, not the deepest, is what avoids octave-too-low errors.
 * 5. sub-sample period from the raw `d(τ)` around that minimum (the paper notes `d` gives an
 *    unbiased abscissa where `d'` does not). This is a least-squares fit over the lags `τ ± k`
 *    whose `d` stays within [FIT_LEVEL] of the way from the dip to the mean of `d`. A sharp dip
 *    (bright tone) uses k = 1, the classic 3-point parabolic interpolation. A broad dip (pure, low
 *    note) uses up to τ/16 lags on each side, which averages out the noise that would otherwise
 *    swamp its very shallow curvature.
 *
 * Lags are searched from `sampleRate / maxFrequencyHz` to `sampleRate / minFrequencyHz`. A few
 * extra lags past that give the refinement room on the right. The integration window `W` is the
 * same for every lag and starts after the filter's start-up transient. Confidence is `1 − d'(τ)` at
 * the chosen dip. All scratch arrays are allocated once, so [detect] does not allocate. Instances
 * are not thread-safe.
 *
 * The difference function is the direct O(W·τmax) form. An FFT version could replace it behind
 * [PitchDetector] if profiling ever asks for it.
 */
class YinPitchDetector(
    override val sampleRate: Int = DEFAULT_SAMPLE_RATE,
    override val windowSize: Int = DEFAULT_WINDOW_SIZE,
    val minFrequencyHz: Float = DEFAULT_MIN_FREQUENCY_HZ,
    val maxFrequencyHz: Float = DEFAULT_MAX_FREQUENCY_HZ,
    val threshold: Float = DEFAULT_THRESHOLD,
    val lowPassHz: Float = DEFAULT_LOW_PASS_HZ,
) : PitchDetector {

    private val tauMin: Int
    private val tauMax: Int
    private val lastLag: Int
    private val settle: Int
    private val integrationLength: Int
    private val lowPass: LowPassFilter

    init {
        require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }
        require(minFrequencyHz > 0f && minFrequencyHz < maxFrequencyHz) {
            "Need 0 < minFrequencyHz < maxFrequencyHz, was $minFrequencyHz..$maxFrequencyHz"
        }
        require(maxFrequencyHz <= sampleRate / 8f) { "maxFrequencyHz must be at most sampleRate / 8" }
        require(threshold > 0f && threshold < 1f) { "threshold must be in (0, 1), was $threshold" }
        require(lowPassHz >= 2 * maxFrequencyHz && lowPassHz < sampleRate / 2f) {
            "lowPassHz must be in [2 × maxFrequencyHz, Nyquist), was $lowPassHz"
        }
        tauMin = max(2, floor(sampleRate / maxFrequencyHz).toInt())
        tauMax = ceil(sampleRate / minFrequencyHz).toInt()
        lastLag = tauMax + ceil(tauMax * FIT_MAX_FRACTION).toInt() + 1
        lowPass = LowPassFilter(sampleRate, lowPassHz)
        settle = lowPass.settleSamples
        integrationLength = windowSize - settle - lastLag
        // YIN needs at least two periods of the lowest note in the window.
        require(integrationLength >= tauMax) {
            "windowSize $windowSize is too short for $minFrequencyHz Hz; need at least ${settle + lastLag + tauMax}"
        }
    }

    private val filtered = FloatArray(windowSize)

    // d(τ) and d'(τ) for τ in 0..lastLag.
    private val difference = DoubleArray(lastLag + 1)
    private val normalized = DoubleArray(lastLag + 1)

    override fun detect(frame: FloatArray): PitchResult {
        require(frame.size >= windowSize) { "Frame must hold $windowSize samples, was ${frame.size}" }
        lowPass.process(frame, filtered, windowSize)
        computeDifference(filtered, settle)
        computeCumulativeMeanNormalized()
        val tau = findFirstDip()
        if (tau < 0) return PitchResult.NONE
        val period = tau + refineOffset(tau)
        val confidence = (1.0 - normalized[tau]).coerceIn(0.0, 1.0)
        return PitchResult.of((sampleRate / period).toFloat(), confidence.toFloat())
    }

    // Step 2, over x[start until start + W + τ]. Four independent accumulators let the CPU overlap
    // the additions (a single running sum is limited by add latency); it is the same sum, just
    // added in a different order.
    private fun computeDifference(x: FloatArray, start: Int) {
        val end = start + integrationLength
        val endUnrolled = end - integrationLength % 4
        difference[0] = 0.0
        for (tau in 1..lastLag) {
            var s0 = 0.0
            var s1 = 0.0
            var s2 = 0.0
            var s3 = 0.0
            var j = start
            while (j < endUnrolled) {
                val d0 = (x[j] - x[j + tau]).toDouble()
                val d1 = (x[j + 1] - x[j + 1 + tau]).toDouble()
                val d2 = (x[j + 2] - x[j + 2 + tau]).toDouble()
                val d3 = (x[j + 3] - x[j + 3 + tau]).toDouble()
                s0 += d0 * d0
                s1 += d1 * d1
                s2 += d2 * d2
                s3 += d3 * d3
                j += 4
            }
            while (j < end) {
                val d = (x[j] - x[j + tau]).toDouble()
                s0 += d * d
                j++
            }
            difference[tau] = (s0 + s1) + (s2 + s3)
        }
    }

    // Step 3.
    private fun computeCumulativeMeanNormalized() {
        normalized[0] = 1.0
        var runningSum = 0.0
        for (tau in 1..lastLag) {
            runningSum += difference[tau]
            normalized[tau] = if (runningSum > 0.0) difference[tau] * tau / runningSum else 1.0
        }
    }

    // Step 4. Returns the lag of the first dip below the threshold, or −1 if there is none.
    private fun findFirstDip(): Int {
        var tau = tauMin
        while (tau <= tauMax) {
            if (normalized[tau] < threshold) {
                while (tau < tauMax && normalized[tau + 1] < normalized[tau]) tau++
                return tau
            }
            tau++
        }
        return -1
    }

    // Step 5. Sub-sample offset of the true minimum of d around lag [tau].
    private fun refineOffset(tau: Int): Double {
        val dip = difference[tau]
        // d'(τ) = d(τ) · τ / Σd, so the mean of d over 1..τ is d(τ) / d'(τ).
        val mean = if (normalized[tau] > 0.0) dip / normalized[tau] else dip
        val level = dip + FIT_LEVEL * (mean - dip)
        val maxHalfWidth = minOf(tau - 1, lastLag - tau, max(1, (tau * FIT_MAX_FRACTION).toInt()))
        var k = 1
        while (k < maxHalfWidth && difference[tau - k - 1] <= level && difference[tau + k + 1] <= level) k++
        return fitMinimum(tau, k)
    }

    // Minimum of the least-squares cubic y = c + b·i + a·i² + e·i³ through d(tau + i), i in −k..k.
    // The offsets are symmetric, so the odd sums vanish and the normal equations split into an
    // even pair (c, a) and an odd pair (b, e). The cubic term absorbs the slight lopsidedness of a
    // wide dip; the integration window sees a slightly different slice of the waveform at each lag.
    // A plain parabola would turn that into a bias of a few tenths of a cent on low notes.
    // For k = 1 there are only three points, so e = 0 and this is the usual 3-point parabola
    // (y₋₁ − y₁) / (2(y₋₁ − 2y₀ + y₁)).
    private fun fitMinimum(tau: Int, k: Int): Double {
        val n = 2.0 * k + 1
        var s2 = 0.0
        var s4 = 0.0
        var s6 = 0.0
        var sy = 0.0
        var siy = 0.0
        var si2y = 0.0
        var si3y = 0.0
        val y0 = difference[tau]
        for (i in -k..k) {
            val y = difference[tau + i] - y0
            val i2 = (i * i).toDouble()
            s2 += i2
            s4 += i2 * i2
            s6 += i2 * i2 * i2
            sy += y
            siy += i * y
            si2y += i2 * y
            si3y += i * i2 * y
        }
        val a = (n * si2y - s2 * sy) / (n * s4 - s2 * s2)
        if (a <= 0.0) return 0.0
        val b: Double
        val e: Double
        if (k == 1) {
            b = siy / s2
            e = 0.0
        } else {
            val det = s2 * s6 - s4 * s4
            b = (s6 * siy - s4 * si3y) / det
            e = (s2 * si3y - s4 * siy) / det
        }
        // Root of y' = b + 2a·x + 3e·x² nearest −b / 2a, in the cancellation-free form
        // x = −b / (a + √(a² − 3be)).
        val discriminant = a * a - 3 * b * e
        val x = if (discriminant >= 0.0) -b / (a + sqrt(discriminant)) else -b / (2 * a)
        return x.coerceIn(-k.toDouble(), k.toDouble())
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 48_000
        const val DEFAULT_WINDOW_SIZE = 4096
        const val DEFAULT_MIN_FREQUENCY_HZ = 60f
        const val DEFAULT_MAX_FREQUENCY_HZ = 1500f

        /** The paper suggests 0.1–0.15. 0.15 keeps slightly noisy notes without admitting octave errors. */
        const val DEFAULT_THRESHOLD = 0.15f

        /** Well above the 1500 Hz top note, so it keeps the fundamental and the first harmonics. */
        const val DEFAULT_LOW_PASS_HZ = 4000f

        // Refinement fits lags whose d is within 5 % of the way from the dip up to the mean of d.
        // For a sine this is about ±5 % of the period.
        private const val FIT_LEVEL = 0.05

        // …but never more than ±1/16 of the period, where a parabola still matches a cosine dip
        // to within about 0.5 %.
        private const val FIT_MAX_FRACTION = 1.0 / 16
    }
}
