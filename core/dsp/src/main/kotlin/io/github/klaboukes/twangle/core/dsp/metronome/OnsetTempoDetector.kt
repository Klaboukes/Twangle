package io.github.klaboukes.twangle.core.dsp.metronome

import io.github.klaboukes.twangle.core.dsp.pitch.Fft
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Tempo from music, in three steps (the classic pipeline of Scheirer 1998, "Tempo and beat analysis
 * of acoustic musical signals", and Ellis 2007, "Beat tracking by dynamic programming"):
 *
 * 1. **Onset strength.** 200 times a second, a Hann-windowed spectrum of the last ≈ 21 ms is
 *    summed into log-spaced bands (three per octave, 40 Hz–12 kHz, each at least two bins) in dB;
 *    the envelope is the sum over bands of each band's rise since the previous frame, half-wave
 *    rectified (spectral flux on a log spectrum: Bello et al. 2005, "A tutorial on onset detection
 *    in music signals"; Ellis 2007 §3.1). It is then high-passed at 0.4 Hz so it is locally
 *    zero-mean, and smoothed over ≈ 25 ms (as in Ellis 2007).
 * 2. **Periodicity.** Every 0.25 s, the autocorrelation of the last [ENVELOPE_SECONDS] of envelope
 *    (through an FFT: Wiener–Khinchin) is scored at every whole tempo by its peaks at the beat lag
 *    and its multiples (a comb over the autocorrelation, as Scheirer's comb filters), weighted by a
 *    broad log-Gaussian preference for 120 BPM (Ellis 2007's tempo weighting, 1.5 octaves wide).
 *    The best tempo's period is then refined from the autocorrelation's peaks at up to 16
 *    multiples (parabolic peak interpolation, least-squares slope through the origin), so it is
 *    far finer than a whole BPM.
 * 3. **Which beat.** Half and double tempo repeat just as well, so the level is picked from the
 *    envelope itself: from the slowest level in range, the tempo doubles while the in-between
 *    beats are nearly as strong as the beats (the grid at twice the tempo that fits best, its two
 *    interleaved halves compared: [offBeatRatio]). "Nearly" is lenient below 100 BPM and stricter
 *    towards 250 ([doublingThreshold]), as listeners mostly tap near 100–120 BPM (van Noorden &
 *    Moelants 1999, "Resonance in the perception of musical pulse"). So a click every 2 s is
 *    30 BPM, not 60, while kick, snare and hi-hat eighths at 120 stay 120, not 240.

 * Steadiness: the reported estimate is the median of the last 5 updates (1.25 s), and [bpm] only
 * moves once the estimate is 0.75 BPM away from it. The beat grid's phase comes from the same
 * envelope: the phase whose beats over the last [PHASE_SECONDS] are the strongest.
 *
 * Any sample rate from 8 kHz works: the hop is a whole number of samples close to 1/200 s and the
 * spectrum's window the power of two closest above 21 ms. Everything is allocated in the
 * constructor; [process] allocates nothing. One update costs about two 4096-point FFTs.
 */
class OnsetTempoDetector(override val sampleRate: Int) : TempoDetector {

    init {
        require(sampleRate >= 8_000) { "sampleRate must be at least 8000, was $sampleRate" }
    }

    // ---- Onset strength -------------------------------------------------------------------------

    private val hop = max(1, (sampleRate.toDouble() / ENVELOPE_RATE_HZ).roundToInt())

    /** Envelope samples per second: sampleRate / hop (exactly 200 at 48 kHz, 199.55 at 44.1 kHz). */
    private val envelopeRate = sampleRate.toDouble() / hop

    private val spectrumSize = Fft.sizeFor((SPECTRUM_SECONDS * sampleRate).roundToInt())
    private val spectrumFft = Fft(spectrumSize)
    private val hann = DoubleArray(spectrumSize) { 0.5 - 0.5 * cos(2 * PI * it / spectrumSize) }
    private val spectrumRe = DoubleArray(spectrumSize)
    private val spectrumIm = DoubleArray(spectrumSize)

    // The latest spectrumSize samples, in a ring; writePos is the oldest.
    private val input = FloatArray(spectrumSize)
    private var writePos = 0
    private var untilFrame = hop

    // Band b covers FFT bins bandEdges[b] until bandEdges[b + 1].
    private val bandEdges: IntArray = bandEdges(sampleRate, spectrumSize)
    private val bandCount = bandEdges.size - 1
    private val previousDb = DoubleArray(bandCount)
    private var hasPreviousFrame = false

    // High-pass (DC blocker, y = x − x₋₁ + a·y₋₁) and the 5-tap smoothing [1 2 3 2 1] / 9.
    private val highPassPole = exp(-2 * PI * HIGH_PASS_HZ / envelopeRate)
    private var highPassX = 0.0
    private var highPassY = 0.0
    private val smoothing = DoubleArray(SMOOTHING_TAPS.size)

    // The envelope, in a ring of the last ENVELOPE_SECONDS; envelopeCount samples made so far.
    private val envelopeCapacity = (ENVELOPE_SECONDS * envelopeRate).roundToInt()
    private val envelope = FloatArray(envelopeCapacity)
    private var envelopeCount = 0L

    // The input sample at the end of the spectrum frame that made envelope sample 0 (−1 until then).
    private var envelopeZeroEnd = -1L

    // ---- Periodicity ----------------------------------------------------------------------------

    private val maxLagCap = (MAX_LAG_SECONDS * envelopeRate).toInt()
    private val acfSize = Fft.sizeFor(envelopeCapacity + maxLagCap + 1)
    private val acfFft = Fft(acfSize)
    private val acfRe = DoubleArray(acfSize)
    private val acfIm = DoubleArray(acfSize)

    // Normalised, unbiased autocorrelation: acf[0] = 1. Lags 0..maxLag are valid.
    private val acf = DoubleArray(maxLagCap + 1)
    private var maxLag = 0

    private val updateEvery = max(1, (UPDATE_SECONDS * envelopeRate).roundToInt())
    private val minEnvelope = (MIN_SECONDS * envelopeRate).roundToInt()

    // Preference for tempos near 120 BPM, per whole tempo from MIN_BPM.
    private val prior = DoubleArray(ClickTrack.MAX_BPM - ClickTrack.MIN_BPM + 1) {
        val octaves = ln((ClickTrack.MIN_BPM + it) / PRIOR_CENTRE_BPM) / ln(2.0)
        exp(-0.5 * (octaves / PRIOR_WIDTH_OCTAVES).pow(2))
    }

    // The last updates' estimates, for the median.
    private val recent = DoubleArray(MEDIAN_OF)
    private val sorted = DoubleArray(MEDIAN_OF)
    private var recentCount = 0
    private var recentNext = 0

    // Phase search result: the strongest phase in envelope samples back from the newest.
    private var bestPhase = 0.0

    private var hasBeat = false
    private var steadyAnchor = 0.0
    private var steadyUpdates = 0

    // ---- Public state ---------------------------------------------------------------------------

    override var samplesProcessed: Long = 0L
        private set
    override var status: TempoStatus = TempoStatus.LISTENING
        private set
    override var bpm: Int = 0
        private set
    override var exactBpm: Float = 0f
        private set
    override var halfBpm: Int = 0
        private set
    override var doubleBpm: Int = 0
        private set
    override var alternativeBpm: Int = 0
        private set
    override var confidence: Float = 0f
        private set
    override var steadySeconds: Float = 0f
        private set
    override var lastBeatSample: Long = -1L
        private set
    override var beatIntervalSamples: Float = 0f
        private set

    override fun process(samples: FloatArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset + length <= samples.size) {
            "Range $offset+$length is outside an array of ${samples.size}"
        }
        var pos = offset
        val end = offset + length
        while (pos < end) {
            val n = min(end - pos, untilFrame)
            var copied = 0
            while (copied < n) {
                val chunk = min(n - copied, spectrumSize - writePos)
                System.arraycopy(samples, pos + copied, input, writePos, chunk)
                writePos = (writePos + chunk) % spectrumSize
                copied += chunk
            }
            pos += n
            samplesProcessed += n
            untilFrame -= n
            if (untilFrame == 0) {
                untilFrame = hop
                if (samplesProcessed >= spectrumSize) spectrumFrame()
            }
        }
    }

    override fun reset() {
        input.fill(0f)
        writePos = 0
        untilFrame = hop
        hasPreviousFrame = false
        highPassX = 0.0
        highPassY = 0.0
        smoothing.fill(0.0)
        envelopeCount = 0L
        envelopeZeroEnd = -1L
        recentCount = 0
        recentNext = 0
        hasBeat = false
        steadyAnchor = 0.0
        steadyUpdates = 0
        samplesProcessed = 0L
        clearEstimate(TempoStatus.LISTENING)
    }

    // One spectrum frame → one envelope sample, and every updateEvery samples a new estimate.
    private fun spectrumFrame() {
        for (i in 0 until spectrumSize) {
            spectrumRe[i] = input[(writePos + i) % spectrumSize] * hann[i]
        }
        spectrumIm.fill(0.0)
        spectrumFft.forward(spectrumRe, spectrumIm)
        var flux = 0.0
        for (b in 0 until bandCount) {
            var energy = 0.0
            for (k in bandEdges[b] until bandEdges[b + 1]) energy += spectrumRe[k] * spectrumRe[k] + spectrumIm[k] * spectrumIm[k]
            val db = 10 * log10(energy + ENERGY_FLOOR)
            if (hasPreviousFrame && db > previousDb[b]) flux += db - previousDb[b]
            previousDb[b] = db
        }
        hasPreviousFrame = true

        val highPassed = flux - highPassX + highPassPole * highPassY
        highPassX = flux
        highPassY = highPassed
        System.arraycopy(smoothing, 1, smoothing, 0, smoothing.size - 1)
        smoothing[smoothing.size - 1] = highPassed
        var smoothed = 0.0
        for (i in smoothing.indices) smoothed += SMOOTHING_TAPS[i] * smoothing[i]

        if (envelopeZeroEnd < 0) envelopeZeroEnd = samplesProcessed
        envelope[(envelopeCount % envelopeCapacity).toInt()] = smoothed.toFloat()
        envelopeCount++
        if (envelopeCount >= minEnvelope && envelopeCount % updateEvery == 0L) estimate()
    }

    // ---- Estimate -------------------------------------------------------------------------------

    private fun estimate() {
        val n = min(envelopeCount, envelopeCapacity.toLong()).toInt()
        if (!autocorrelate(n)) {
            noBeat(0f)
            return
        }
        // Step 2: the best whole tempo by its comb score, then its exact period.
        var bestBpm = 0
        var bestScore = 0.0
        for (b in ClickTrack.MIN_BPM..ClickTrack.MAX_BPM) {
            val period = SECONDS_PER_MINUTE * envelopeRate / b
            if (period > maxLag) continue
            val multiples = min(COARSE_MULTIPLES, (maxLag / period).toInt())
            var sum = 0.0
            for (k in 1..multiples) {
                val lag = k * period
                sum += acfMax(lag, 1.0 + lag * 0.5 / b) // ±½ BPM around the tempo
            }
            val score = sum / multiples * prior[b - ClickTrack.MIN_BPM]
            if (score > bestScore) {
                bestScore = score
                bestBpm = b
            }
        }
        if (bestBpm == 0) {
            noBeat(0f)
            return
        }
        val refined = SECONDS_PER_MINUTE * envelopeRate / refinePeriod(SECONDS_PER_MINUTE * envelopeRate / bestBpm)

        // Step 3: the level, from the slowest in range up.
        val current = exactBpm.toDouble()
        var level = refined
        while (level / 2 >= ClickTrack.MIN_BPM - 0.5) level /= 2
        var downMargin = Double.MAX_VALUE // How near the last doubling came to not happening.
        var upMargin = Double.MAX_VALUE // How near the next one came to happening.
        while (level * 2 <= ClickTrack.MAX_BPM + 0.5) {
            val offBeats = offBeatRatio(level, n)
            var threshold = doublingThreshold(level * 2)
            if (current > 0 && abs(current / level - 1) < STICKY_RANGE) threshold += STICKINESS
            if (current > 0 && abs(current / (2 * level) - 1) < STICKY_RANGE) threshold -= STICKINESS
            if (offBeats < threshold) {
                upMargin = threshold - offBeats
                break
            }
            downMargin = offBeats - threshold
            level *= 2
        }

        // How well the chosen level's beats repeat: the comb score at its exact period.
        val period = SECONDS_PER_MINUTE * envelopeRate / level
        val multiples = max(1, min(COARSE_MULTIPLES, (maxLag / period).toInt()))
        var combSum = 0.0
        for (k in 1..multiples) {
            val lag = k * period
            combSum += if (lag <= maxLag) acfMax(lag, 1.5 + 0.01 * lag) else 0.0
        }
        val strength = (combSum / multiples).coerceIn(0.0, 1.0)
        hasBeat = strength >= (if (hasBeat) LEAVE_CONFIDENCE else ENTER_CONFIDENCE)
        if (!hasBeat) {
            noBeat(strength.toFloat())
            return
        }

        // Median of the last updates, then the shown tempo with hysteresis.
        recent[recentNext] = level
        recentNext = (recentNext + 1) % MEDIAN_OF
        if (recentCount < MEDIAN_OF) recentCount++
        val estimate = median()
        if (bpm == 0 || abs(estimate - bpm) >= SHOWN_HYSTERESIS_BPM) {
            bpm = estimate.roundToInt().coerceIn(ClickTrack.MIN_BPM, ClickTrack.MAX_BPM)
        }
        exactBpm = estimate.toFloat()
        confidence = strength.toFloat()
        val half = (estimate / 2).roundToInt()
        val double = (estimate * 2).roundToInt()
        halfBpm = if (half >= ClickTrack.MIN_BPM) half else 0
        doubleBpm = if (double <= ClickTrack.MAX_BPM) double else 0
        alternativeBpm = when {
            halfBpm != 0 && doubleBpm != 0 -> if (downMargin <= upMargin) halfBpm else doubleBpm
            else -> halfBpm + doubleBpm
        }

        // Steadiness: updates in a row within ±1 BPM of where the run started.
        if (steadyAnchor > 0 && abs(estimate - steadyAnchor) <= STEADY_BPM) {
            steadyUpdates++
        } else {
            steadyAnchor = estimate
            steadyUpdates = 0
        }
        steadySeconds = (steadyUpdates * updateEvery / envelopeRate).toFloat()
        status = if (steadySeconds >= STEADY_SECONDS) TempoStatus.STEADY else TempoStatus.SETTLING

        // The beat grid: the strongest phase at the estimate's period.
        val beatPeriod = SECONDS_PER_MINUTE * envelopeRate / estimate
        findPhase(beatPeriod, n)
        val newest = envelopeCount - 1
        lastBeatSample = (envelopeZeroEnd + (newest - bestPhase) * hop - onsetDelaySamples()).roundToLong()
        beatIntervalSamples = (SECONDS_PER_MINUTE * sampleRate / estimate).toFloat()
    }

    // Fills acf from the last n envelope samples; false if they are all the same (silence).
    private fun autocorrelate(n: Int): Boolean {
        val first = envelopeCount - n
        var mean = 0.0
        for (i in 0 until n) mean += envelopeAt(first + i)
        mean /= n
        for (i in 0 until n) acfRe[i] = envelopeAt(first + i) - mean
        acfRe.fill(0.0, n, acfSize)
        acfIm.fill(0.0)
        acfFft.forward(acfRe, acfIm)
        for (i in 0 until acfSize) {
            acfRe[i] = acfRe[i] * acfRe[i] + acfIm[i] * acfIm[i]
            acfIm[i] = 0.0
        }
        acfFft.inverse(acfRe, acfIm)
        val zero = acfRe[0]
        if (zero <= n * SILENT_ENVELOPE) return false
        maxLag = min(maxLagCap, (n * MAX_LAG_FRACTION).toInt())
        // Unbiased: each lag's sum over the n − lag pairs it has, relative to lag 0's over n.
        for (lag in 0..maxLag) acf[lag] = acfRe[lag] / zero * n / (n - lag)
        return true
    }

    // The largest acf within [centre − tolerance, centre + tolerance] (lags 1..maxLag).
    private fun acfMax(centre: Double, tolerance: Double): Double {
        val lo = max(1, (centre - tolerance).roundToInt())
        val hi = min(maxLag, (centre + tolerance).roundToInt())
        var best = -1.0
        for (lag in lo..hi) if (acf[lag] > best) best = acf[lag]
        return best
    }

    // The period (in envelope samples) from the acf's peaks near its multiples: each multiple's
    // peak is interpolated (parabola through three lags) and the period is the least-squares slope
    // through the origin, Σ w·k·p / Σ w·k², weighted by peak height. Each fit narrows the next search.
    private fun refinePeriod(initial: Double): Double {
        var period = initial
        var sumKP = 0.0
        var sumKK = 0.0
        val multiples = min(REFINE_MULTIPLES, (maxLag / initial).toInt())
        for (k in 1..multiples) {
            val predicted = k * period
            val tolerance = 1.5 + predicted * (if (k <= 2) 0.02 else 0.006)
            val lo = max(1, (predicted - tolerance).roundToInt())
            val hi = min(maxLag - 1, (predicted + tolerance).roundToInt())
            if (hi - lo < 2) continue
            var m = lo
            for (lag in lo..hi) if (acf[lag] > acf[m]) m = lag
            if (m == lo || m == hi || acf[m] <= 0) continue
            val a = acf[m - 1]
            val b = acf[m]
            val c = acf[m + 1]
            val curvature = a - 2 * b + c
            val peak = if (curvature < 0) m + 0.5 * (a - c) / curvature else m.toDouble()
            sumKP += b * k * peak
            sumKK += b * k * k
            period = sumKP / sumKK
        }
        return period
    }

    // How strong the off-beats are against the beats at tempo [bpm]: the grid at twice the tempo
    // that fits the last PHASE_SECONDS best, split into its two interleaved halves (every other
    // point), and the weaker half's mean envelope over the stronger's. Fitting the faster grid,
    // not the slower one, keeps the phase from favouring either half: a loose but even beat gives
    // about 1, a click every other point about 0.
    private fun offBeatRatio(bpm: Double, n: Int): Double {
        val period = SECONDS_PER_MINUTE * envelopeRate / (2 * bpm)
        findPhase(period, n)
        val window = min(n, (PHASE_SECONDS * envelopeRate).roundToInt())
        val newest = envelopeCount - 1
        var evenSum = 0.0
        var oddSum = 0.0
        var evenCount = 0
        var oddCount = 0
        var back = bestPhase
        var k = 0
        while (back <= window - 1 - POOL) {
            if (k % 2 == 0) {
                evenSum += pooled(newest - back)
                evenCount++
            } else {
                oddSum += pooled(newest - back)
                oddCount++
            }
            k++
            back += period
        }
        if (oddCount == 0) return 1.0 // Not one off-beat heard yet: can't tell, so the faster level.
        val even = evenSum / evenCount
        val odd = oddSum / oddCount
        val strong = max(even, odd)
        if (strong <= 0) return 0.0
        return (min(even, odd) / strong).coerceIn(0.0, 1.0)
    }

    // Sets bestPhase (envelope samples back from the newest, 0 until period) to where the beats of
    // a grid at [period] are the strongest on average over the last PHASE_SECONDS.
    private fun findPhase(period: Double, n: Int) {
        val window = min(n, (PHASE_SECONDS * envelopeRate).roundToInt())
        var best = -Double.MAX_VALUE
        var bestIndex = 0
        val phases = max(1, floor(period).toInt())
        for (phase in 0 until phases) {
            val mean = gridMean(phase.toDouble(), period, window)
            if (mean.isNaN()) break
            if (mean > best) {
                best = mean
                bestIndex = phase
            }
        }
        bestPhase = bestIndex.toDouble()
        // Between envelope samples: a parabola through the best phase and its neighbours.
        if (bestIndex in 1 until phases - 1) {
            val a = gridMean(bestIndex - 1.0, period, window)
            val c = gridMean(bestIndex + 1.0, period, window)
            val curvature = a - 2 * best + c
            if (!a.isNaN() && !c.isNaN() && curvature < 0) bestPhase += 0.5 * (a - c) / curvature
        }
    }

    // The mean pooled envelope on the grid points [phase], [phase] + [period], … samples back from
    // the newest, within the last [window] samples; NaN if there are none.
    private fun gridMean(phase: Double, period: Double, window: Int): Double {
        val newest = envelopeCount - 1
        var sum = 0.0
        var count = 0
        var back = phase
        while (back <= window - 1 - POOL) {
            sum += pooled(newest - back)
            count++
            back += period
        }
        return if (count == 0) Double.NaN else sum / count
    }

    // The mean envelope within ±POOL samples of [at] (an absolute, possibly fractional, index):
    // a beat played a little early or late still counts.
    private fun pooled(at: Double): Double {
        val centre = at.roundToLong()
        val lo = max(envelopeCount - envelopeCapacity, centre - POOL)
        val hi = min(envelopeCount - 1, centre + POOL)
        var sum = 0.0
        for (i in lo..hi) sum += envelopeAt(i)
        return if (hi >= lo) sum / (hi - lo + 1) else 0.0
    }

    private fun envelopeAt(index: Long): Double = envelope[(index % envelopeCapacity).toInt()].toDouble()

    private fun median(): Double {
        System.arraycopy(recent, 0, sorted, 0, recentCount)
        for (i in 1 until recentCount) {
            val v = sorted[i]
            var j = i - 1
            while (j >= 0 && sorted[j] > v) {
                sorted[j + 1] = sorted[j]
                j--
            }
            sorted[j + 1] = v
        }
        return if (recentCount % 2 == 1) sorted[recentCount / 2] else 0.5 * (sorted[recentCount / 2 - 1] + sorted[recentCount / 2])
    }

    // From an envelope sample's frame end back to the moment of the hit it shows: the smoothing's
    // two-sample delay, and the flux peaking once the hit is ≈ 0.3 of a window in (measured on
    // clicks, drums and plucks at 45–250 BPM: the grid was 5–10 ms late without it).
    private fun onsetDelaySamples(): Double = SMOOTHING_DELAY * hop + ONSET_DELAY_WINDOWS * spectrumSize

    private fun noBeat(strength: Float) {
        hasBeat = false
        recentCount = 0
        recentNext = 0
        steadyAnchor = 0.0
        steadyUpdates = 0
        clearEstimate(TempoStatus.NO_BEAT)
        confidence = strength
    }

    private fun clearEstimate(newStatus: TempoStatus) {
        status = newStatus
        bpm = 0
        exactBpm = 0f
        halfBpm = 0
        doubleBpm = 0
        alternativeBpm = 0
        confidence = 0f
        steadySeconds = 0f
        lastBeatSample = -1L
        beatIntervalSamples = 0f
    }

    companion object {
        /** Audio heard before the first estimate, in seconds. */
        const val MIN_SECONDS = 3.0

        /** How long the estimate must stay within ±[STEADY_BPM] to be [TempoStatus.STEADY], in seconds. */
        const val STEADY_SECONDS = 4.0f

        /** The steadiness band, in BPM either way. */
        const val STEADY_BPM = 1.0

        /** Envelope samples per second (the hop is the whole number of samples closest to this). */
        const val ENVELOPE_RATE_HZ = 200

        /** Envelope the estimate looks at, in seconds: 4 beats at 30 BPM, and a change shows within a few seconds. */
        const val ENVELOPE_SECONDS = 8.0

        /** Seconds between estimates. */
        const val UPDATE_SECONDS = 0.25

        /** The beat grid's phase is fitted over this many seconds of the newest envelope. */
        const val PHASE_SECONDS = 6.0

        private const val SPECTRUM_SECONDS = 0.021
        private const val BAND_LOW_HZ = 40.0
        private const val BAND_HIGH_HZ = 12_000.0
        private const val BANDS_PER_OCTAVE = 3
        private const val MIN_BAND_BINS = 2
        private const val ENERGY_FLOOR = 1e-7 // −70 dB per band: digital silence reads as steady
        private const val HIGH_PASS_HZ = 0.4
        private val SMOOTHING_TAPS = doubleArrayOf(1.0 / 9, 2.0 / 9, 3.0 / 9, 2.0 / 9, 1.0 / 9)
        private const val SMOOTHING_DELAY = 2.0
        private const val ONSET_DELAY_WINDOWS = 0.3

        private const val MAX_LAG_SECONDS = 4.5
        private const val MAX_LAG_FRACTION = 0.6
        private const val SILENT_ENVELOPE = 1e-12
        private const val SECONDS_PER_MINUTE = 60.0
        private const val COARSE_MULTIPLES = 4
        private const val REFINE_MULTIPLES = 16
        private const val PRIOR_CENTRE_BPM = 120.0
        private const val PRIOR_WIDTH_OCTAVES = 1.5
        private const val POOL = 2L
        private const val STICKY_RANGE = 0.04
        private const val STICKINESS = 0.07
        private const val ENTER_CONFIDENCE = 0.35
        private const val LEAVE_CONFIDENCE = 0.25
        private const val MEDIAN_OF = 5
        private const val SHOWN_HYSTERESIS_BPM = 0.75

        // How strong the off-beats must be, against the beats, to count the faster of two levels
        // ([fasterBpm] and half of it) as the beat: lenient up to 80 BPM, strict from 240.
        private val THRESHOLD_BPM = doubleArrayOf(80.0, 120.0, 160.0, 200.0, 250.0)
        private val THRESHOLD_RATIO = doubleArrayOf(0.15, 0.35, 0.5, 0.55, 0.6)

        internal fun doublingThreshold(fasterBpm: Double): Double {
            if (fasterBpm <= THRESHOLD_BPM[0]) return THRESHOLD_RATIO[0]
            for (i in 1 until THRESHOLD_BPM.size) {
                if (fasterBpm <= THRESHOLD_BPM[i]) {
                    val t = (fasterBpm - THRESHOLD_BPM[i - 1]) / (THRESHOLD_BPM[i] - THRESHOLD_BPM[i - 1])
                    return THRESHOLD_RATIO[i - 1] + t * (THRESHOLD_RATIO[i] - THRESHOLD_RATIO[i - 1])
                }
            }
            return THRESHOLD_RATIO.last()
        }

        // Log-spaced band edges in FFT bins, BANDS_PER_OCTAVE per octave from BAND_LOW_HZ up to
        // 8 kHz (or 0.9 × Nyquist), each band at least MIN_BAND_BINS wide.
        private fun bandEdges(sampleRate: Int, fftSize: Int): IntArray {
            val binHz = sampleRate.toDouble() / fftSize
            val high = min(BAND_HIGH_HZ, 0.45 * sampleRate)
            val lastBin = min(fftSize / 2, (high / binHz).roundToInt())
            val edges = ArrayList<Int>()
            edges.add(max(1, (BAND_LOW_HZ / binHz).roundToInt()))
            val step = 2.0.pow(1.0 / BANDS_PER_OCTAVE)
            var f = BAND_LOW_HZ
            while (true) {
                f *= step
                val bin = (f / binHz).roundToInt()
                if (bin >= lastBin) break
                if (bin - edges.last() >= MIN_BAND_BINS) edges.add(bin)
            }
            if (lastBin - edges.last() >= 1) edges.add(lastBin)
            return edges.toIntArray()
        }
    }
}
