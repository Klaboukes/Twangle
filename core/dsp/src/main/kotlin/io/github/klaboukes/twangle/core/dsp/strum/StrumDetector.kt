package io.github.klaboukes.twangle.core.dsp.strum

import io.github.klaboukes.twangle.core.dsp.pitch.Fft
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Finds the pitch of every string of one strum at once, when the strings to expect are known (the
 * selected tuning's open strings, at the current A4). Phase 18 research prototype: method, measured
 * accuracy and limits are in ARCHITECTURE.md, *Strum check (polyphonic)*.
 *
 * One long frame ([frameLength] samples, ≈ 341 ms at 48 kHz, see [frameLengthFor]) goes through a
 * Hann window and a 2× zero-padded FFT. Then, per expected string `s` at `f_s`:
 * 1. **Coarse search:** weighted harmonic summation (Hermes 1988, "Measurement of pitch by
 *    subharmonic summation"; Klapuri 2006, "Multiple fundamental frequency estimation by summing
 *    harmonic amplitudes") over a 1-cent grid within ±[searchCents] of `f_s`: the candidate with
 *    the largest `Σ_k |X(k·f)| / k` over its first [MAX_PARTIALS] harmonics wins. The 1/k weight
 *    keeps a candidate from winning on another string's strong fundamental landing on one of its
 *    upper harmonics. Searching only near the expected strings keeps the other fundamentals out.
 * 2. **Partial peaks:** each harmonic `k` of the estimate is followed to its spectral peak (within
 *    half a main lobe) and refined by quadratic interpolation of the log magnitude (Smith & Serra
 *    1987, PARSHL; J. O. Smith, *Spectral Audio Signal Processing*, "Quadratic Interpolation of
 *    Spectral Peaks"), which also gives its amplitude.
 * 3. **Isolation:** partials closer than a Hann main lobe merge into one peak somewhere between
 *    their frequencies. In standard tuning that is common: B3 ≈ 3 × E2 and E4 = 4 × E2 within
 *    2 cents. So partial `k` of `s` counts only as far as it is clear of every other heard string's
 *    partials (weight 0 within one main-lobe half-width, 1 from two).
 * 4. **Estimate:** `f_s = Σ w_k · (f_k / k) / Σ w_k` with `w_k = a_k² · isolation_k`. A string none
 *    of whose partials is clear is [StrumResult.shadowed]: its lowest partial is used as it is,
 *    since the other string's partial there is a higher, weaker harmonic of its own.
 * 5. **Presence:** a string is heard when its first two partials reach at most [PRESENCE_DB] below
 *    the loudest string's level, and [SILENCE_AMPLITUDE]. A clear partial counts with its
 *    amplitude, a shadowed one with what is left after the other strings' expected share there.
 *
 * Steps 2–5 run [REFINE_PASSES] times, since isolation and presence depend on the other strings.
 * Partials are taken as exact harmonics. String inharmonicity (Fletcher 1964, `f_k = k·f₀·√(1+Bk²)`)
 * makes high partials sharp, so the power weights favour low ones and nothing above
 * [MAX_PARTIAL_HZ] or past [MAX_PARTIALS] is used.
 *
 * [detect] allocates nothing. Not thread-safe: one thread detects.
 */
class StrumDetector(
    val sampleRate: Int,
    val frameLength: Int = frameLengthFor(sampleRate),
    val maxStrings: Int = MAX_STRINGS,
    val searchCents: Float = DEFAULT_SEARCH_CENTS,
) {

    init {
        require(sampleRate in 8_000..192_000) { "sampleRate out of range: $sampleRate" }
        require(frameLength >= 1024) { "frameLength must be at least 1024, was $frameLength" }
        require(maxStrings > 0) { "maxStrings must be positive, was $maxStrings" }
        require(searchCents in 1f..600f) { "searchCents must be in 1..600, was $searchCents" }
    }

    private val maxSearchRatio = 2.0.pow(searchCents / CENTS_PER_OCTAVE)

    private val fftSize = Fft.sizeFor(frameLength) * ZERO_PAD
    private val fft = Fft(fftSize)
    private val binHz = sampleRate.toDouble() / fftSize
    private val bins = fftSize / 2 + 1

    /** Half-width of the Hann window's main lobe (its first zero), in padded FFT bins. */
    private val lobeBins = 2.0 * fftSize / frameLength

    // Periodic Hann window (Harris 1978); its coherent gain is 1/2.
    private val window = DoubleArray(frameLength) { 0.5 - 0.5 * cos(2 * PI * it / frameLength) }
    private val re = DoubleArray(fftSize)
    private val im = DoubleArray(fftSize)
    private val magnitude = DoubleArray(bins)
    private val logMagnitude = DoubleArray(bins)

    // Per string: current estimate and its allowed range, harmonic count, the level that decides
    // presence, and flags.
    private val estimate = DoubleArray(maxStrings)
    private val partialCount = IntArray(maxStrings)
    private val lowestHz = DoubleArray(maxStrings)
    private val highestHz = DoubleArray(maxStrings)
    private val level = DoubleArray(maxStrings)
    private val heard = BooleanArray(maxStrings)
    private val order = IntArray(maxStrings)
    private val allShadowed = BooleanArray(maxStrings)

    // Per string and partial (row-major, MAX_PARTIALS per string): measured frequency (0 = no peak),
    // amplitude and isolation from the other strings' partials (0..1).
    private val partialHz = DoubleArray(maxStrings * MAX_PARTIALS)
    private val partialAmplitude = DoubleArray(maxStrings * MAX_PARTIALS)
    private val partialIsolation = DoubleArray(maxStrings * MAX_PARTIALS)

    /**
     * Finds each of the first [stringCount] strings of [expectedHz] (Hz, any order) in the last
     * [frameLength] samples of [frame] (−1..1) and writes the readings to [result].
     */
    fun detect(frame: FloatArray, expectedHz: FloatArray, stringCount: Int, result: StrumResult) {
        require(frame.size >= frameLength) { "frame holds ${frame.size} samples, needs $frameLength" }
        require(stringCount in 1..minOf(maxStrings, expectedHz.size, result.capacity)) { "bad stringCount $stringCount" }
        val start = frame.size - frameLength
        spectrum(frame, start)

        for (s in 0 until stringCount) {
            val f = expectedHz[s].toDouble()
            require(f > 0 && f * maxSearchRatio < sampleRate / 2.0) { "expected frequency out of range: $f" }
            partialCount[s] = floor(MAX_PARTIAL_HZ / f).toInt().coerceIn(1, MAX_PARTIALS)
            lowestHz[s] = f / maxSearchRatio
            highestHz[s] = f * maxSearchRatio
            estimate[s] = coarse(f, partialCount[s])
            heard[s] = true
        }
        sortByFrequency(expectedHz, stringCount)
        for (s in 0 until stringCount) level[s] = 0.0
        for (pass in 0 until REFINE_PASSES) {
            for (s in 0 until stringCount) measurePartials(s)
            for (s in 0 until stringCount) refine(s, stringCount)
            decidePresence(stringCount)
        }
        writeResult(expectedHz, stringCount, result)
    }

    private fun spectrum(frame: FloatArray, start: Int) {
        var mean = 0.0
        for (i in 0 until frameLength) mean += frame[start + i]
        mean /= frameLength
        for (i in 0 until fftSize) {
            re[i] = if (i < frameLength) (frame[start + i] - mean) * window[i] else 0.0
            im[i] = 0.0
        }
        fft.forward(re, im)
        for (b in 0 until bins) {
            val m = sqrt(re[b] * re[b] + im[b] * im[b])
            magnitude[b] = m
            logMagnitude[b] = ln(m + TINY)
        }
    }

    // Harmonic summation over a 1-cent grid around the expected frequency.
    private fun coarse(expected: Double, partials: Int): Double {
        val steps = searchCents.roundToInt()
        var best = expected
        var bestScore = -1.0
        for (c in -steps..steps) {
            val f = expected * 2.0.pow(c / CENTS_PER_OCTAVE)
            var score = 0.0
            for (k in 1..partials) score += magnitudeAt(k * f / binHz) / k
            if (score > bestScore) {
                bestScore = score
                best = f
            }
        }
        return best
    }

    // Linear interpolation of the magnitude spectrum at a fractional bin.
    private fun magnitudeAt(bin: Double): Double {
        val i = bin.toInt()
        if (i < 0 || i + 1 >= bins) return 0.0
        val t = bin - i
        return magnitude[i] * (1 - t) + magnitude[i + 1] * t
    }

    // Follows each harmonic of the current estimate to its peak (frequency 0 when there is none).
    private fun measurePartials(s: Int) {
        for (k in 1..partialCount[s]) {
            val index = s * MAX_PARTIALS + k - 1
            partialHz[index] = 0.0
            partialAmplitude[index] = 0.0
            val center = k * estimate[s] / binHz
            val from = maxOf(1, (center - lobeBins).toInt())
            val to = minOf(bins - 2, (center + lobeBins).toInt() + 1)
            var m = from
            for (b in from + 1..to) if (magnitude[b] > magnitude[m]) m = b
            if (m == from || m == to) continue // Still rising at the edge: not this partial's peak.
            val a = logMagnitude[m - 1]
            val b = logMagnitude[m]
            val c = logMagnitude[m + 1]
            val denominator = a - 2 * b + c
            val p = if (denominator < 0) 0.5 * (a - c) / denominator else 0.0
            // A peak further off belongs to something else (another string's partial).
            if (abs(m + p - center) > MAX_PEAK_OFFSET * lobeBins) continue
            partialHz[index] = (m + p) * binHz
            // A sinusoid of amplitude A peaks at A·N/4 after a Hann window of length N.
            partialAmplitude[index] = exp(b - 0.25 * (a - c) * p) * 4.0 / frameLength
        }
    }

    private fun refine(s: Int, stringCount: Int) {
        var sum = 0.0
        var weightSum = 0.0
        var powerSum = 0.0
        var lowest = 0
        for (k in 1..partialCount[s]) {
            val index = s * MAX_PARTIALS + k - 1
            partialIsolation[index] = 0.0
            if (partialHz[index] <= 0.0) continue
            if (lowest == 0) lowest = k
            val isolation = isolation(s, k * estimate[s], stringCount)
            partialIsolation[index] = isolation
            val power = partialAmplitude[index] * partialAmplitude[index]
            powerSum += power
            sum += power * isolation * partialHz[index] / k
            weightSum += power * isolation
        }
        allShadowed[s] = weightSum <= powerSum * SHADOWED_SHARE
        // Every partial merged with another string's: the lowest one is the least disturbed, since
        // the other string's partial there is a higher (weaker) harmonic of its own.
        val refined = when {
            !allShadowed[s] -> sum / weightSum
            lowest > 0 -> partialHz[s * MAX_PARTIALS + lowest - 1] / lowest
            else -> estimate[s]
        }
        if (refined in lowestHz[s]..highestHz[s]) estimate[s] = refined
    }

    // 0 when another heard string's partial lies within ISOLATION_FROM main-lobe half-widths of hz,
    // 1 from ISOLATION_TO.
    private fun isolation(s: Int, hz: Double, stringCount: Int): Double {
        var nearest = Double.MAX_VALUE
        for (t in 0 until stringCount) {
            if (t == s || !heard[t]) continue
            val j = (hz / estimate[t]).roundToInt().coerceAtLeast(1)
            nearest = minOf(nearest, abs(hz - j * estimate[t]))
        }
        val lobes = nearest / binHz / lobeBins
        return ((lobes - ISOLATION_FROM) / (ISOLATION_TO - ISOLATION_FROM)).coerceIn(0.0, 1.0)
    }

    // A string is heard when its clear partials, or what its shadowed partials hold beyond the
    // other strings' expected share, stand out of the frame. Lowest string first, so a shadowed
    // string's own share is known before the strings above it need it.
    private fun decidePresence(stringCount: Int) {
        var loudest = 0.0
        for (o in 0 until stringCount) {
            val s = order[o]
            var best = 0.0
            for (k in 1..minOf(partialCount[s], EVIDENCE_PARTIALS)) {
                val index = s * MAX_PARTIALS + k - 1
                if (partialHz[index] <= 0.0) continue
                val amplitude = partialAmplitude[index]
                best = if (partialIsolation[index] >= CLEAR) {
                    maxOf(best, amplitude)
                } else {
                    maxOf(best, amplitude - expectedLeak(s, k, stringCount))
                }
            }
            level[s] = best
            loudest = maxOf(loudest, best)
        }
        val floor = maxOf(SILENCE_AMPLITUDE, loudest * PRESENCE_RATIO)
        for (s in 0 until stringCount) heard[s] = level[s] >= floor
    }

    // What other heard strings' partials near harmonic k of s are expected to add there, or
    // +infinity when one of them is a lower harmonic of its own string than k is of s: then that
    // partial is no evidence for s (a missing string's search otherwise settles where its upper
    // harmonics land on other strings' fundamentals). Harmonic j of string t is taken as a_i · i / j
    // from t's clear partial i nearest to j: falling as 1/j, like a string plucked near its end
    // (Fletcher & Rossing 1998, ch. 2). A string without clear partials counts with its level.
    private fun expectedLeak(s: Int, k: Int, stringCount: Int): Double {
        val hz = k * estimate[s]
        var leak = 0.0
        val reach = ISOLATION_TO * lobeBins * binHz
        for (t in 0 until stringCount) {
            if (t == s || !heard[t]) continue
            val j = (hz / estimate[t]).roundToInt().coerceAtLeast(1)
            if (abs(hz - j * estimate[t]) > reach) continue
            if (j <= k) return Double.POSITIVE_INFINITY
            var reference = 0
            for (i in 1..partialCount[t]) {
                val index = t * MAX_PARTIALS + i - 1
                if (partialHz[index] <= 0.0) continue
                val clear = partialIsolation[index] >= CLEAR
                if (allShadowed[t] && reference == 0) reference = i
                if (clear && (reference == 0 || abs(i - j) < abs(reference - j))) reference = i
            }
            if (reference == 0) continue
            val amplitude = if (allShadowed[t]) level[t] else partialAmplitude[t * MAX_PARTIALS + reference - 1]
            leak += amplitude * reference / j
        }
        return leak
    }

    // order = string indices sorted by expected frequency, lowest first (insertion sort, no allocation).
    private fun sortByFrequency(expectedHz: FloatArray, stringCount: Int) {
        for (i in 0 until stringCount) {
            var j = i
            while (j > 0 && expectedHz[order[j - 1]] > expectedHz[i]) {
                order[j] = order[j - 1]
                j--
            }
            order[j] = i
        }
    }

    private fun writeResult(expectedHz: FloatArray, stringCount: Int, result: StrumResult) {
        var loudest = 0.0
        for (s in 0 until stringCount) loudest = maxOf(loudest, level[s])
        result.stringCount = stringCount
        for (s in 0 until stringCount) {
            result.present[s] = heard[s]
            result.shadowed[s] = heard[s] && allShadowed[s]
            result.levelDb[s] = if (loudest > 0 && level[s] > 0) (20 * log10(level[s] / loudest)).toFloat() else -120f
            if (heard[s]) {
                result.frequencyHz[s] = estimate[s].toFloat()
                result.cents[s] = (CENTS_PER_OCTAVE * log2(estimate[s] / expectedHz[s])).toFloat()
            } else {
                result.frequencyHz[s] = Float.NaN
                result.cents[s] = Float.NaN
            }
        }
    }

    companion object {
        /** Most strings one detector handles (8-string guitar). */
        const val MAX_STRINGS = 8

        /** Default search range around each expected string: a semitone either way. */
        const val DEFAULT_SEARCH_CENTS = 100f

        /**
         * Frame length in seconds for [PitchRange.STANDARD] (16 384 samples at 48 kHz, four
         * standard tuner windows); [PitchRange.LOW] doubles it, like the tuner's window.
         */
        const val FRAME_SECONDS = 16_384 / 48_000.0

        /** Harmonics used per string. */
        const val MAX_PARTIALS = 8

        /** No partial above this is used: higher ones are weak and read sharp from inharmonicity. */
        const val MAX_PARTIAL_HZ = 2_500.0

        /**
         * A string is heard when its level is at most this far below the loudest string's. Above a
         * Hann window's highest sidelobe (−31 dB), so another string's sidelobe never counts.
         */
        const val PRESENCE_DB = 25.0

        /** Below this partial amplitude (≈ −74 dBFS) nothing counts as heard. */
        const val SILENCE_AMPLITUDE = 2e-4

        /**
         * Frame length in samples at [sampleRate]: [FRAME_SECONDS] (≈ 341 ms), twice that for
         * [PitchRange.LOW] (instruments below the guitar, see [PitchRange.forLowestNote]).
         */
        fun frameLengthFor(sampleRate: Int, range: PitchRange = PitchRange.STANDARD): Int =
            (FRAME_SECONDS * range.windowMultiple * sampleRate).roundToInt()

        /** Times steps 2–5 run per frame. */
        const val REFINE_PASSES = 4

        private const val ZERO_PAD = 2
        private const val CENTS_PER_OCTAVE = 1200.0
        private const val TINY = 1e-12
        // Shadowed: the isolation-weighted power is at most this share of the partials' power.
        private const val SHADOWED_SHARE = 0.001

        // A partial is clear (counts for presence as it is) from this isolation.
        private const val CLEAR = 0.5

        // A peak further than this many main-lobe half-widths from k·f is not partial k.
        private const val MAX_PEAK_OFFSET = 0.5

        // Presence is judged on the first two partials, a string's strongest on a guitar.
        private const val EVIDENCE_PARTIALS = 2

        // Isolation 0 within ISOLATION_FROM main-lobe half-widths of another string's partial, 1 from ISOLATION_TO.
        private const val ISOLATION_FROM = 1.0
        private const val ISOLATION_TO = 2.0
        private val PRESENCE_RATIO = 10.0.pow(-PRESENCE_DB / 20)
    }
}
