package io.github.klaboukes.twangle.core.dsp.pitch

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The YIN fundamental-frequency estimator (de Cheveigné & Kawahara 2002, "YIN, a fundamental
 * frequency estimator for speech and music", JASA 111(4)), steps 2–5, plus a low-pass pre-filter,
 * a noise-robust period refinement and a second refinement on the lowest partials for stiff strings:
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
 * 6. the same refinement again on the note's lowest partials ([refineOnLowestPartials]). A real
 *    string is stiff: partial k sits at `k·f0·√(1 + B·k²)` (Fletcher 1964), sharp of `k·f0`, and
 *    step 5, which uses the whole waveform, is pulled sharp with them, by several cents just after a
 *    hard pick on a low string. So the frame is limited to below about 3.5·f0 (partials 1 and 2,
 *    and the 3rd faintly) through the spectrum [FftDifference] already holds, and the minimum of
 *    that band's difference function is found near τ, at a few lags, with the same cubic fit. The
 *    whole period τ and every octave decision of step 4 stay as they were. It counts in full for a
 *    note that rings on; less while the window still holds the attack, where it strays; and not
 *    where the band itself doesn't repeat at τ (a chord whose notes aren't all this note's
 *    partials, mains hum under a low string), which step 5 reads better.
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
    /**
     * Whether step 6 runs. Without it a sound reads at the period of its whole waveform, which suits
     * a sustained sound of detuned voices (a drone's reeds or chorus) better than a stiff string: a
     * few partials follow their slow beating, and lean towards the louder voice, more than all of them.
     */
    val refineOnLowestPartials: Boolean = true,
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

    // The band-limited refinement's gains per FFT bin and its d̃(τ) (only the lags it needs).
    private val bandGains = DoubleArray(differenceFunction.size / 2 + 1)
    private val bandDifference = DoubleArray(lastLag + 1)
    private var bandClarity = 0.0

    override fun detect(frame: FloatArray): PitchResult {
        require(frame.size >= windowSize) { "Frame must hold $windowSize samples, was ${frame.size}" }
        lowPass.process(frame, filtered, windowSize)
        differenceFunction.compute(filtered, settle, difference)
        computeCumulativeMeanNormalized()
        val firstDip = findFirstDip()
        if (firstDip < 0) return PitchResult.NONE
        val tau = checkLongerPeriods(checkShorterPeriods(firstDip))
        if (isAboveRange(tau)) return PitchResult.NONE
        val wholePeriod = tau + refineOffset(tau)
        val period = if (refineOnLowestPartials) lowestPartialsPeriod(tau, wholePeriod) else wholePeriod
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
        return fitMinimum(difference, tau, k)
    }

    // Step 6. The period from the note's lowest partials, counting as much as settledWeight and the
    // band's clarity allow; [whole], step 5's period, where it doesn't count at all.
    private fun lowestPartialsPeriod(tau: Int, whole: Double): Double {
        val settled = settledWeight()
        if (settled == 0.0) return whole
        val banded = bandLimitedPeriod(tau)
        return if (banded.isNaN()) whole else whole + settled * bandClarity * (banded - whole)
    }

    // How much the lowest partials' period counts as far as the attack goes: fully for a note that
    // decays, holds steady or swells a little over the window (its first half at least
    // SETTLED_HALF_RATIO of its second in energy), not at all once the first half is under
    // ATTACK_HALF_RATIO of the second (the window holds the attack, where that period strays by up
    // to 20 cents), and linearly in between.
    private fun settledWeight(): Double {
        val half = integrationLength / 2
        var first = 0.0
        var second = 0.0
        for (j in 0 until half) {
            val a = filtered[settle + j].toDouble()
            val b = filtered[settle + half + j].toDouble()
            first += a * a
            second += b * b
        }
        if (!(second > 0.0)) return 0.0
        return ((first / second - ATTACK_HALF_RATIO) / (SETTLED_HALF_RATIO - ATTACK_HALF_RATIO)).coerceIn(0.0, 1.0)
    }

    // Step 5 again, on the note's lowest partials: the sound limited to below about BAND_STOP × f0
    // (gains 1 up to BAND_PASS × f0, then a raised-cosine fall), its tapered difference function at
    // the lags around τ, and the sub-sample minimum of that by the same cubic fit. Sets bandClarity.
    // NaN where there is no such minimum near τ.
    private fun bandLimitedPeriod(tau: Int): Double {
        val n = differenceFunction.size
        val f0Bin = n.toDouble() / tau
        val passBin = BAND_PASS * f0Bin
        val stopBin = BAND_STOP * f0Bin
        val bins = minOf(n / 2 - FftDifference.TAPER_ORDER - 1, ceil(stopBin).toInt())
        for (k in 1 until bins) {
            bandGains[k] = if (k <= passBin) 1.0 else {
                val c = cos(PI / 2 * (k - passBin) / (stopBin - passBin))
                c * c
            }
        }
        differenceFunction.selectBand(bandGains, bins)
        // d_g(τ) = e_g(0) + e_g(τ) − 2 r_g(τ), the tapered difference function of the band-limited
        // sound. Its energy e_g(0) is r_g(0); how e_g changes with the lag (the sound's decay or onset)
        // is taken from the whole sound's tapered energy, at either end of the lags fitted and linear
        // in between.
        val e0 = differenceFunction.bandCorrelation(0)
        val reach = ceil(tau * (FIT_MAX_FRACTION + BAND_SEARCH_FRACTION)).toInt() + 2
        val lo = max(1, tau - reach)
        val hi = minOf(lastLag, tau + reach)
        val whole = differenceFunction.taperedEnergy(filtered, settle, 0)
        if (!(e0 > 0.0 && whole > 0.0)) return Double.NaN
        val eLo = e0 * differenceFunction.taperedEnergy(filtered, settle, lo) / whole
        val eHi = e0 * differenceFunction.taperedEnergy(filtered, settle, hi) / whole
        for (lag in lo..hi) {
            val shifted = eLo + (eHi - eLo) * (lag - lo) / (hi - lo)
            bandDifference[lag] = e0 + shifted - 2 * differenceFunction.bandCorrelation(lag)
        }
        var t = tau
        while (t > lo + 1 && bandDifference[t - 1] < bandDifference[t]) t--
        while (t < hi - 1 && bandDifference[t + 1] < bandDifference[t]) t++
        if (t <= lo + 1 || t >= hi - 1) return Double.NaN
        val dip = bandDifference[t]
        // d̃'s mean over a period: the energies, as the correlation averages out.
        val mean = e0 + eLo + (eHi - eLo) * (t - lo) / (hi - lo)
        // A band that doesn't repeat at this period has no clean dip, and its period is not this
        // note's: a chord or drone whose notes aren't all partials of this one, or mains hum under a
        // low string. A note's own band, even 10 dB above white noise, leaves well under 1 % of the
        // mean of d̃ at the dip; hum 10 dB under a low string, 10–20 %; a tanpura read at its root,
        // whose Pa isn't a partial of it, 30 %.
        bandClarity = ((CLARITY_OFF - dip / mean) / (CLARITY_OFF - CLARITY_ON)).coerceIn(0.0, 1.0)
        if (bandClarity == 0.0) return Double.NaN
        val level = dip + FIT_LEVEL * (mean - dip)
        val maxHalfWidth = minOf(t - lo - 1, hi - t - 1, max(1, (t * FIT_MAX_FRACTION).toInt()))
        var k = 1
        while (k < maxHalfWidth && bandDifference[t - k - 1] <= level && bandDifference[t + k + 1] <= level) k++
        return t + fitMinimum(bandDifference, t, k)
    }

    // Minimum of the least-squares cubic y = c + b·i + a·i² + e·i³ through d(tau + i), i in −k..k.
    // The offsets are symmetric, so the odd sums vanish and the normal equations split into an
    // even pair (c, a) and an odd pair (b, e). The cubic term absorbs the slight lopsidedness of a
    // wide dip; the integration window sees a slightly different slice of the waveform at each lag.
    // A plain parabola would turn that into a bias of a few tenths of a cent on low notes.
    // For k = 1 there are only three points, so e = 0 and this is the usual 3-point parabola
    // (y₋₁ − y₁) / (2(y₋₁ − 2y₀ + y₁)).
    private fun fitMinimum(difference: DoubleArray, tau: Int, k: Int): Double {
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

        // The lowest partials: gains 1 up to 2 × f0 and down to 0 at 3.5 × f0 (a raised cosine), so
        // partials 1 and 2 pass whole and the 3rd at a quarter of its level. A low E picked hard at three times
        // the usual stiffness reads +0.6 cents at 0.5 s from them, against +6 from the whole sound.
        private const val BAND_PASS = 2.0
        private const val BAND_STOP = 3.5

        // The band's minimum may lie a little from τ: a stiff string's whole-sound period is up to
        // ≈ 1 % short just after the pick. Lags that far plus the fit's are worked out.
        private const val BAND_SEARCH_FRACTION = 0.02

        // A window whose first half has under half the energy of its second (3 dB) holds the
        // attack; from 80 % (1 dB) the note counts as ringing on. A real string's level wobbles as
        // its two planes of vibration beat (Phase 46's recordings: a window's first half up to 15 %
        // quieter than its second, 0.7 dB, on a note ringing for seconds), which at a 10 % margin
        // handed a third of the frames back to the whole waveform, 2–4 cents sharp. The attack
        // itself leaves the first half far quieter: a tenth or less.
        private const val ATTACK_HALF_RATIO = 0.5
        private const val SETTLED_HALF_RATIO = 0.8

        // The band's dip floor, as a share of its mean, up to which its period counts in full
        // (CLARITY_ON) and from which it doesn't count (CLARITY_OFF).
        private const val CLARITY_ON = 0.03
        private const val CLARITY_OFF = 0.06
    }
}
