package io.github.klaboukes.twangle.core.dsp.chord

import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.pitch.Fft
import io.github.klaboukes.twangle.core.dsp.strum.StrumDetector
import io.github.klaboukes.twangle.core.dsp.strum.StrumListener
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Names the chord of one strum from the notes in it, whatever the shape: open or barre, any
 * voicing, notes doubled or left out. Unlike [StrumDetector] it expects nothing: a chord can put
 * any note on any string. Frames come from [StrumListener] (same length). Method, measured
 * results and limits are in docs/architecture/strum-and-metronome.md, *Chord naming*.
 *
 * 1. **Spectrum:** a Hann window over the frame and a 2× zero-padded FFT; its peaks (local maxima
 *    that are the highest within [PEAK_SPACING_LOBES] main-lobe half-widths, so a Hann sidelobe is
 *    never one, within [PEAK_RANGE_DB] of the loudest and above [SILENCE_AMPLITUDE]), each refined by
 *    quadratic interpolation of the log magnitude (Smith & Serra 1987).
 * 2. **Tuning:** the peaks' power-weighted circular mean distance from the equal-tempered grid at
 *    the given A4 gives the strum's own A4, so a guitar tuned to itself but not to A4 still works.
 * 3. **Notes, by iterative estimation and cancellation** (Klapuri 2003, "Multiple fundamental
 *    frequency estimation based on harmonicity and spectral smoothness"; Klapuri 2006, "Multiple
 *    fundamental frequency estimation by summing harmonic amplitudes"): every semitone from
 *    [lowestMidi] to [highestMidi] gets a salience, the weighted harmonic sum `Σ_k r_k / √k` of the
 *    peaks within ±[TOLERANCE_CENTS] of its first [MAX_PARTIALS] partials (Hermes 1988), where `r`
 *    is what is left of each peak. (1/k weights picked the octave above a low string with a weak
 *    fundamental, and its odd partials were left over as false notes.) A candidate whose
 *    fundamental is under [FUNDAMENTAL_SHARE] of its strongest partial isn't a note: that keeps out
 *    a sub-octave whose harmonics are the chord's notes (C2 under C3 E3 G3, 4 : 5 : 6), but lets a
 *    low string with a weak fundamental through. The strongest candidate is a note, and its
 *    partials are taken out of the peaks: partial k no more than the geometric mean of its
 *    neighbours k ± 1 as first measured (spectral smoothness), so another note's partial on top of
 *    it stays. Again until the best is under [FLOOR] of the first note's salience. More than
 *    [MAX_NOTES] notes is noise or a muddle: [ChordResult.crowded], no chord.
 * 4. **Name:** a note under [LEFTOVER] of the first note's salience on a harmonic of another note
 *    found is that note's leftover and doesn't count. The rest go to [ChordNamer] as pitch classes,
 *    with the lowest note as the bass. Notes from [SURE] of the first note's salience are sure;
 *    weaker ones could be leftovers or a string played softly, so the chord is named only when it
 *    is the same with and without each of them (every subset): a strum that is nearly two chords,
 *    or nearly none, gets no name.
 *
 * [detect] allocates nothing. Not thread-safe: one thread detects.
 */
class ChordDetector(
    val sampleRate: Int,
    val frameLength: Int = StrumDetector.frameLengthFor(sampleRate),
    val lowestMidi: Int = DEFAULT_LOWEST_MIDI,
    val highestMidi: Int = DEFAULT_HIGHEST_MIDI,
) {

    init {
        require(sampleRate in 8_000..192_000) { "sampleRate out of range: $sampleRate" }
        require(frameLength >= 1024) { "frameLength must be at least 1024, was $frameLength" }
        require(lowestMidi in 12..highestMidi) { "bad note range $lowestMidi..$highestMidi" }
        require(midiHz(highestMidi + 1, 440.0) < sampleRate / 2.0) { "highestMidi $highestMidi is above Nyquist" }
    }

    private val fftSize = Fft.sizeFor(frameLength) * ZERO_PAD
    private val fft = Fft(fftSize)
    private val binHz = sampleRate.toDouble() / fftSize
    private val bins = fftSize / 2 + 1

    /** Half-width of the Hann window's main lobe (its first zero), in padded FFT bins. */
    private val lobeBins = 2.0 * fftSize / frameLength
    private val spacingBins = (PEAK_SPACING_LOBES * lobeBins).toInt().coerceAtLeast(1)

    // Periodic Hann window (Harris 1978); its coherent gain is 1/2.
    private val window = DoubleArray(frameLength) { 0.5 - 0.5 * cos(2 * PI * it / frameLength) }
    private val re = DoubleArray(fftSize)
    private val im = DoubleArray(fftSize)
    private val magnitude = DoubleArray(bins)

    // Spectral peaks, ascending in frequency: frequency, amplitude, and what is left of it once the
    // notes found so far have had their share.
    private val peakHz = DoubleArray(MAX_PEAKS)
    private val peakAmplitude = DoubleArray(MAX_PEAKS)
    private val residual = DoubleArray(MAX_PEAKS)
    private var peakCount = 0

    // The candidate being scored: its partials' peaks (−1 = none) and their residuals.
    private val partialPeak = IntArray(MAX_PARTIALS)
    private val partialResidual = DoubleArray(MAX_PARTIALS)
    private var partials = 0

    private val chosen = BooleanArray(highestMidi + 1)
    private val toleranceRatio = 2.0.pow(TOLERANCE_CENTS / CENTS_PER_OCTAVE)

    // Partial k's weight in the salience: 1/√k.
    private val weight = DoubleArray(MAX_PARTIALS) { 1.0 / sqrt(it + 1.0) }

    /**
     * Names the chord in the last [frameLength] samples of [frame] (−1..1), with notes on the
     * equal-tempered grid at [a4Hz] (moved by the strum's own [ChordResult.tuningOffsetCents]).
     * Fills [result] and returns its [ChordResult.chord].
     */
    fun detect(frame: FloatArray, result: ChordResult, a4Hz: Float = 440f): Chord? {
        require(frame.size >= frameLength) { "frame holds ${frame.size} samples, needs $frameLength" }
        require(a4Hz > 0f) { "a4Hz must be positive, was $a4Hz" }
        result.clear()
        spectrum(frame, frame.size - frameLength)
        findPeaks(midiHz(lowestMidi, a4Hz.toDouble()) / toleranceRatio)
        if (peakCount == 0) return null
        val offsetCents = tuningOffsetCents(a4Hz.toDouble())
        result.tuningOffsetCents = offsetCents.toFloat()
        val a4 = a4Hz * 2.0.pow(offsetCents / CENTS_PER_OCTAVE)
        findNotes(a4, result)
        if (result.crowded || result.noteCount == 0) return null
        result.chord = name(result)
        return result.chord
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
        for (b in 0 until bins) magnitude[b] = sqrt(re[b] * re[b] + im[b] * im[b])
    }

    // Peaks from lowestHz to MAX_PARTIAL_HZ, as amplitudes of the sinusoids behind them.
    private fun findPeaks(lowestHz: Double) {
        peakCount = 0
        val from = maxOf(1, (lowestHz / binHz).toInt())
        val to = minOf(bins - 2, (MAX_PARTIAL_HZ / binHz).toInt())
        var loudest = 0.0
        for (b in from..to) loudest = maxOf(loudest, magnitude[b])
        val floor = maxOf(SILENCE_AMPLITUDE * frameLength / 4.0, loudest * PEAK_RANGE)
        for (b in from..to) {
            val m = magnitude[b]
            if (m < floor || m <= magnitude[b - 1] || m < magnitude[b + 1]) continue
            if (!highestAround(b)) continue
            if (peakCount == MAX_PEAKS) break
            val a = ln(magnitude[b - 1] + TINY)
            val c = ln(magnitude[b + 1] + TINY)
            val l = ln(m + TINY)
            val denominator = a - 2 * l + c
            val p = if (denominator < 0) 0.5 * (a - c) / denominator else 0.0
            peakHz[peakCount] = (b + p) * binHz
            // A sinusoid of amplitude A peaks at A·N/4 after a Hann window of length N.
            peakAmplitude[peakCount] = exp(l - 0.25 * (a - c) * p) * 4.0 / frameLength
            residual[peakCount] = peakAmplitude[peakCount]
            peakCount++
        }
    }

    private fun highestAround(b: Int): Boolean {
        val m = magnitude[b]
        for (i in maxOf(0, b - spacingBins)..minOf(bins - 1, b + spacingBins)) if (magnitude[i] > m) return false
        return true
    }

    // Power-weighted circular mean of the peaks' distance from the semitone grid at a4, in cents.
    private fun tuningOffsetCents(a4: Double): Double {
        var x = 0.0
        var y = 0.0
        for (i in 0 until peakCount) {
            val semitones = SEMITONES_PER_OCTAVE * log2(peakHz[i] / a4)
            val angle = 2 * PI * (semitones - floor(semitones))
            val power = peakAmplitude[i] * peakAmplitude[i]
            x += power * cos(angle)
            y += power * sin(angle)
        }
        return atan2(y, x) / (2 * PI) * 100.0
    }

    private fun findNotes(a4: Double, result: ChordResult) {
        chosen.fill(false)
        var first = 0.0
        while (true) {
            var best = -1
            var bestSalience = 0.0
            for (midi in lowestMidi..highestMidi) {
                if (chosen[midi]) continue
                val s = salience(midiHz(midi, a4))
                if (s > bestSalience) {
                    bestSalience = s
                    best = midi
                }
            }
            if (best < 0 || bestSalience < FLOOR * first) break
            if (result.noteCount == MAX_NOTES) {
                result.crowded = true
                break
            }
            if (first == 0.0) first = bestSalience
            chosen[best] = true
            result.noteMidi[result.noteCount] = best
            result.noteStrength[result.noteCount] = (bestSalience / first).toFloat()
            result.noteCount++
            salience(midiHz(best, a4)) // Fills partialPeak / partialResidual for the note found.
            cancel(best, a4)
        }
    }

    // Weighted harmonic sum of what is left at f0's partials; 0 when its fundamental is too weak
    // to be a note. Leaves the partials' peaks and residuals in partialPeak / partialResidual.
    private fun salience(f0: Double): Double {
        partials = floor(MAX_PARTIAL_HZ / f0).toInt().coerceIn(1, MAX_PARTIALS)
        var sum = 0.0
        var strongest = 0.0
        for (k in 1..partials) {
            val peak = strongestPeakNear(k * f0)
            val r = if (peak < 0) 0.0 else residual[peak]
            partialPeak[k - 1] = peak
            partialResidual[k - 1] = r
            sum += r * weight[k - 1]
            strongest = maxOf(strongest, r)
        }
        val fundamental = partialResidual[0]
        return if (fundamental <= 0.0 || fundamental < FUNDAMENTAL_SHARE * strongest) 0.0 else sum
    }

    // The peak with the most left within TOLERANCE_CENTS of hz, or −1.
    private fun strongestPeakNear(hz: Double): Int {
        val low = hz / toleranceRatio
        val high = hz * toleranceRatio
        var lo = 0
        var hi = peakCount
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (peakHz[mid] < low) lo = mid + 1 else hi = mid
        }
        var best = -1
        var i = lo
        while (i < peakCount && peakHz[i] <= high) {
            if (best < 0 || residual[i] > residual[best]) best = i
            i++
        }
        return best
    }

    // Takes the note's partials (in partialPeak / partialResidual) out of the peaks. Partial k is
    // taken at most as large as the larger of the note's partials k ± 1 as first measured (spectral
    // smoothness, Klapuri 2003): what stands above that belongs to another note on top of it.
    private fun cancel(midi: Int, a4: Double) {
        val f0 = midiHz(midi, a4)
        for (k in 1..partials) {
            val peak = partialPeak[k - 1]
            if (peak < 0) continue
            val r = partialResidual[k - 1]
            val share = if (k == 1) r else minOf(r, envelope(k, f0))
            residual[peak] = maxOf(0.0, residual[peak] - share)
        }
    }

    // What the note's own partial k can be, from its neighbours as first measured: their geometric
    // mean, so one neighbour lifted by another note's partial lifts it only by the square root.
    // (The larger neighbour cancelled too much: a real G#4 over E2's 5th partial went with it,
    // since B4 sat on the 6th. The smaller one too little: leftovers of B2's 3rd turned G into Gmaj7.)
    private fun envelope(k: Int, f0: Double): Double {
        val below = originalNear(k - 1, f0)
        val above = if (k < partials) originalNear(k + 1, f0) else below
        return sqrt(below * above)
    }

    // The note's partial k as first measured: the largest peak near k·f0 (0 when none).
    private fun originalNear(k: Int, f0: Double): Double {
        if (k < 1 || k * f0 > MAX_PARTIAL_HZ) return 0.0
        val hz = k * f0
        val low = hz / toleranceRatio
        val high = hz * toleranceRatio
        var best = 0.0
        for (i in 0 until peakCount) {
            if (peakHz[i] > high) break
            if (peakHz[i] >= low) best = maxOf(best, peakAmplitude[i])
        }
        return best
    }

    private fun name(result: ChordResult): Chord? {
        var sure = 0
        var lowest = Int.MAX_VALUE
        for (n in 0 until result.noteCount) {
            val strength = result.noteStrength[n]
            if (strength < LEFTOVER && isHarmonicOfAnother(n, result)) continue
            val pc = result.noteMidi[n] % SEMITONES_PER_OCTAVE
            result.pitchClassStrength[pc] = maxOf(result.pitchClassStrength[pc], strength)
            if (strength >= SURE) sure = sure or (1 shl pc)
            lowest = minOf(lowest, result.noteMidi[n])
        }
        var unsure = 0
        for (pc in 0 until SEMITONES_PER_OCTAVE) {
            val strength = result.pitchClassStrength[pc]
            if (strength > 0f && strength < SURE) unsure = unsure or (1 shl pc)
        }
        if (lowest == Int.MAX_VALUE) return null
        val bass = lowest % SEMITONES_PER_OCTAVE
        result.bass = PitchClass.entries[bass]
        if (Integer.bitCount(unsure) > MAX_UNSURE) return null
        val chord = ChordNamer.name(sure, bass) ?: return null
        // Every subset of the unsure pitch classes, added to the sure ones, must give the same chord.
        var subset = unsure
        while (subset != 0) {
            if (ChordNamer.name(sure or subset, bass) !== chord) return null
            subset = (subset - 1) and unsure
        }
        return chord
    }

    // True when note n sits on a harmonic (2..LEFTOVER_HARMONICS) of another note found: then a
    // weak n is most likely what cancelling that note's partials left over (the harmonics of a note
    // it took in as its own octave, say), not a string of its own.
    private fun isHarmonicOfAnother(n: Int, result: ChordResult): Boolean {
        for (m in 0 until result.noteCount) {
            val d = result.noteMidi[n] - result.noteMidi[m]
            if (d > 0 && d < HARMONIC_INTERVALS.size && HARMONIC_INTERVALS[d]) return true
        }
        return false
    }

    companion object {
        /** Most notes a strum can hold: six strings and a few leftovers. More is noise or a muddle. */
        const val MAX_NOTES = 10

        /** Lowest note looked for by default: B1, a 7-string guitar's lowest string. */
        const val DEFAULT_LOWEST_MIDI = 35

        /** Highest note looked for by default: E6, the 24th fret of the high E string. */
        const val DEFAULT_HIGHEST_MIDI = 88

        /** Harmonics summed per note. */
        const val MAX_PARTIALS = 10

        /** No partial above this counts: higher ones are weak and read sharp from inharmonicity. */
        const val MAX_PARTIAL_HZ = 4_000.0

        /** A partial counts for a note within this many cents of its harmonic of the note. */
        const val TOLERANCE_CENTS = 30.0

        /** A note's fundamental must reach this share of its strongest partial. */
        const val FUNDAMENTAL_SHARE = 0.15

        /** Notes are looked for down to this share of the first note's salience. */
        const val FLOOR = 0.08

        /** A note from this share of the first note's salience is sure; weaker ones must not change the name. */
        const val SURE = 0.15

        /** Peaks within this range of the loudest (dB) are used. */
        const val PEAK_RANGE_DB = 50.0

        /** Below this peak amplitude (≈ −74 dBFS) nothing is a peak. */
        const val SILENCE_AMPLITUDE = 2e-4

        /** A peak is the highest within this many main-lobe half-widths (a Hann sidelobe sits 1.25 away). */
        const val PEAK_SPACING_LOBES = 0.75

        /**
         * A note under this share of the first note's salience that sits on a harmonic of a stronger
         * note is taken for that note's leftover and doesn't count.
         */
        const val LEFTOVER = 0.3

        /** Harmonics a weak note may be a leftover of (to 16, four octaves up). */
        const val LEFTOVER_HARMONICS = 16

        /** A weak note within this many cents of a harmonic of another is a leftover (the 7th sits 31 cents off). */
        const val LEFTOVER_CENTS = 35.0

        // HARMONIC_INTERVALS[d]: some harmonic 2..LEFTOVER_HARMONICS lies within LEFTOVER_CENTS of d semitones.
        private val HARMONIC_INTERVALS = BooleanArray(4 * 12 + 1) { d ->
            (2..LEFTOVER_HARMONICS).any { k -> kotlin.math.abs(12 * log2(k.toDouble()) - d) * 100 <= LEFTOVER_CENTS }
        }

        private const val MAX_PEAKS = 512
        private const val MAX_UNSURE = 3
        private const val ZERO_PAD = 2
        private const val TINY = 1e-12
        private const val CENTS_PER_OCTAVE = 1200.0
        private const val SEMITONES_PER_OCTAVE = 12
        private const val A4_MIDI = 69
        private val PEAK_RANGE = 10.0.pow(-PEAK_RANGE_DB / 20)

        private fun midiHz(midi: Int, a4: Double): Double = a4 * 2.0.pow((midi - A4_MIDI) / 12.0)
    }
}
