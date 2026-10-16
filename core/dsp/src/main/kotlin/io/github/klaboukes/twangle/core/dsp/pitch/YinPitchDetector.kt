package io.github.klaboukes.twangle.core.dsp.pitch

import kotlin.math.abs
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
 * 2. difference function `d(τ) = Σ_{j<W} (x_j − x_{j+τ})²`, computed with the FFT ([FftDifference])
 * 3. cumulative mean normalized difference `d'(τ) = d(τ) / ((1/τ) Σ_{k=1..τ} d(k))`, `d'(0) = 1`
 * 4. absolute threshold: the first τ whose `d'(τ)` dips below [threshold], followed down to its
 *    local minimum. Taking the *first* dip, not the deepest, is what avoids octave-too-low errors.
 *    A dip that is still falling at the longest lag lies outside the range and is ignored (e.g.
 *    60 Hz mains hum just below [minFrequencyHz]). Two checks then guard against the remaining
 *    whole-number period errors (see [checkShorterPeriods] and [checkLongerPeriods]), and a third
 *    ([isAboveRange]) drops tones above the range:
 *    - in heavy noise the dip at the true period can miss the threshold, because `d'` is inflated
 *      at short lags, so the first accepted dip is at 2 or 3 periods. If `d` at τ/k is about as low
 *      as at τ, τ/k is the period.
 *    - a high note with a strong 2nd harmonic and weak odd harmonics dips below the threshold at
 *      half its period. If `d` at 2τ or 3τ is far lower than at τ, that is the period.
 *    - if the period found is a whole number of periods shorter than the shortest lag searched,
 *      the tone is above [maxFrequencyHz] and the frame has no pitch: a 2 kHz whistle or a tick
 *      ringing at 3 kHz would otherwise read as 1000 or 1500 Hz, a multiple of its period in range.
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
 * The difference function used to be the direct O(W·τmax) sum. On the Galaxy S26 that took about
 * 7 ms of CPU per frame (a third of a core while a note sounds), so it now goes through the FFT,
 * O(N log N); see docs/architecture/performance.md. `FftDifferenceTest` checks it against the direct sum.
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
    private val differenceFunction = FftDifference(integrationLength, lastLag)
    private val normalized = DoubleArray(lastLag + 1)

    override fun detect(frame: FloatArray): PitchResult {
        require(frame.size >= windowSize) { "Frame must hold $windowSize samples, was ${frame.size}" }
        lowPass.process(frame, filtered, windowSize)
        differenceFunction.compute(filtered, settle, difference)
        computeCumulativeMeanNormalized()
        val firstDip = findFirstDip()
        if (firstDip < 0) return PitchResult.NONE
        val tau = checkLongerPeriods(checkShorterPeriods(firstDip))
        if (isAboveRange(tau)) return PitchResult.NONE
        val period = tau + refineOffset(tau)
        val confidence = (1.0 - normalized[tau]).coerceIn(0.0, 1.0)
        return PitchResult.of((sampleRate / period).toFloat(), confidence.toFloat())
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

    // Step 4. Returns the lag of the first dip below the threshold, or −1 if there is none in range.
    private fun findFirstDip(): Int {
        var tau = tauMin
        while (tau <= tauMax) {
            if (normalized[tau] < threshold) {
                while (tau < tauMax && normalized[tau + 1] < normalized[tau]) tau++
                // Still falling at the range edge: the real minimum is below minFrequencyHz.
                return if (tau == tauMax && normalized[tau + 1] < normalized[tau]) -1 else tau
            }
            tau++
        }
        return -1
    }

    // Heavy noise: the first accepted dip may be a multiple of the period. Returns the shortest
    // lag τ/k whose d is within SHORTER_PERIOD_TOLERANCE of d(τ), or τ itself. For a stationary
    // tone plus noise, d at every multiple of the period has the same expected value (the noise
    // energy), while at τ/k of a true period τ the out-of-phase harmonics add a large residue.
    private fun checkShorterPeriods(tau: Int): Int {
        val limit = SHORTER_PERIOD_TOLERANCE * difference[tau]
        for (k in tau / tauMin downTo 2) {
            val candidate = localMinimum((tau + k / 2) / k)
            if (candidate in tauMin until tau && difference[candidate] <= limit && normalized[candidate] < MAX_SHORTER_PERIOD_DIP) {
                return candidate
            }
        }
        return tau
    }

    // True when the period found, τ, is really a whole number of periods of a tone above
    // maxFrequencyHz, so the frame has no pitch in range:
    // - the dip at τ = tauMin is still falling towards shorter lags, so its minimum lies below, or
    // - the test of checkShorterPeriods holds for a dip of d below tauMin that divides τ a whole
    //   number of times: about as deep as d(τ). A period of only 10–30 lags falls well between two
    //   whole lags, which alone can make d there several times d(τ) on a clean tone, so this
    //   compares the parabola-interpolated floors of the two dips, allowing CLEAN_DIP of the mean
    //   of d on top. Lags below MIN_PERIOD_LAG are not tried: the low-pass leaves nothing that
    //   repeats that fast.
    // Runs after checkLongerPeriods, which may have taken such a tone's 2nd period for its first
    // (a 1600 Hz sine dips at 30 and 60 lags), and which takes a note an octave lower with a strong
    // 2nd harmonic back down to its own period.
    private fun isAboveRange(tau: Int): Boolean {
        if (tau == tauMin && normalized[tauMin - 1] < normalized[tauMin]) return true
        var limit = -1.0
        for (lag in MIN_PERIOD_LAG until tauMin) {
            val isDip = difference[lag] < difference[lag - 1] && difference[lag] <= difference[lag + 1]
            if (!isDip || normalized[lag] >= MAX_SHORTER_PERIOD_DIP) continue
            // τ is k periods of about `lag` each, every one off by at most half a lag either way.
            val k = (tau + lag / 2) / lag
            if (k < 2 || abs(tau - k * lag) > k) continue
            if (limit < 0.0) limit = SHORTER_PERIOD_TOLERANCE * dipFloor(tau) + CLEAN_DIP * meanDifference(tau)
            if (dipFloor(lag) <= limit) return true
        }
        return false
    }

    // Lowest value of d around the dip at lag tau, between whole lags (d itself if it isn't a dip).
    private fun dipFloor(tau: Int): Double {
        val left = difference[tau - 1]
        val mid = difference[tau]
        val right = difference[tau + 1]
        val curvature = left - 2 * mid + right
        if (curvature <= 0.0) return mid
        val offset = (left - right) / (2 * curvature)
        if (tau < 2 || tau + 2 > lastLag) return (mid - curvature * offset * offset / 2).coerceAtLeast(0.0)
        // The quartic through d at tau − 2 … tau + 2 (central differences), its minimum found by a
        // few Newton steps from the parabola's. A parabola misjudges a sharp dip's floor by up to
        // ≈ 0.3 % of the mean of d at a low analysis rate (a high note in 11 kHz audio), as much as
        // a G string's faint odd harmonics leave; the quartic, a tenth of that.
        val far = difference[tau - 2]
        val farRight = difference[tau + 2]
        val c1 = (far - 8 * left + 8 * right - farRight) / 12
        val c2 = (-far + 16 * left - 30 * mid + 16 * right - farRight) / 24
        val c3 = (-far + 2 * left - 2 * right + farRight) / 12
        val c4 = (far - 4 * left + 6 * mid - 4 * right + farRight) / 24
        var x = offset.coerceIn(-1.0, 1.0)
        repeat(DIP_FLOOR_NEWTON_STEPS) {
            val slope = c1 + x * (2 * c2 + x * (3 * c3 + x * 4 * c4))
            val bend = 2 * c2 + x * (6 * c3 + x * 12 * c4)
            if (bend > 0.0) x = (x - slope / bend).coerceIn(-1.0, 1.0)
        }
        return (mid + x * (c1 + x * (c2 + x * (c3 + x * c4)))).coerceAtLeast(0.0)
    }

    // Strong 2nd (or 3rd) harmonic with weak neighbours: the first accepted dip may be a fraction
    // of the period. If d at 2τ or 3τ is below LONGER_PERIOD_RATIO of d(τ), the tone only *nearly*
    // repeats at τ and truly repeats there. A G3 string heard through a phone, its fundamental and
    // odd harmonics faint, leaves only about 1 % of the mean of d at its half period: so clean that
    // d(τ) at whole lags mostly reflects where the period falls between two lags. So this compares
    // the parabola-interpolated floors of the dips, which don't depend on that, and only leaves
    // alone a dip whose floor is under LONGER_PERIOD_MIN_FLOOR of the mean of d (a tone that does
    // repeat at τ, give or take rounding).
    private fun checkLongerPeriods(tau: Int): Int {
        val floor = dipFloor(tau)
        if (floor < LONGER_PERIOD_MIN_FLOOR * meanDifference(tau)) return tau
        val limit = LONGER_PERIOD_RATIO * floor
        for (m in 2..3) {
            if (m * tau > tauMax) break
            val candidate = localMinimum(m * tau)
            if (candidate in tauMin..tauMax && dipFloor(candidate) < limit) return candidate
        }
        return tau
    }

    // Mean of d over lags 1..tau.
    private fun meanDifference(tau: Int): Double {
        var sum = 0.0
        for (lag in 1..tau) sum += difference[lag]
        return sum / tau
    }

    // Follows d downhill from [start] to the nearest local minimum, staying inside 1 until lastLag.
    private fun localMinimum(start: Int): Int {
        var tau = start.coerceIn(1, lastLag - 1)
        while (tau > 1 && difference[tau - 1] < difference[tau]) tau--
        while (tau < lastLag - 1 && difference[tau + 1] < difference[tau]) tau++
        return tau
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
        /**
         * Just above 60 Hz mains hum, so a humming amplifier or fridge alone reads nothing; still
         * below drop C (C2, 65.4 Hz), the lowest guitar tuning.
         */
        const val DEFAULT_MIN_FREQUENCY_HZ = 62f
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

        // τ/k counts as the same period as τ if its d is at most 15 % higher (noise makes the
        // sums at different lags differ by a few per cent), and it is still a fairly clear dip.
        private const val SHORTER_PERIOD_TOLERANCE = 1.15
        private const val MAX_SHORTER_PERIOD_DIP = 0.5

        // Shortest lag isAboveRange tries as a period: 12 kHz at 48 kHz.
        private const val MIN_PERIOD_LAG = 4

        private const val DIP_FLOOR_NEWTON_STEPS = 3

        // A dip whose floor is within 1 % of the mean of d (d' ≈ 0.01) is clean, whatever d(τ).
        private const val CLEAN_DIP = 0.01

        // 2τ or 3τ replaces τ if its d is under a quarter of d(τ), and only when d'(τ) is not
        // already near zero.
        private const val LONGER_PERIOD_RATIO = 0.25
        // A dip floor under 0.2 % of the mean of d is a tone repeating at that lag: the interpolated
        // floor of a clean tone's dip is accurate to about 0.02 % (4 kHz low-pass, at 48 kHz).
        private const val LONGER_PERIOD_MIN_FLOOR = 0.002
    }
}
