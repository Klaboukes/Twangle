package io.github.klaboukes.twangle.core.dsp.pitch

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * YIN step 2, the difference function `d(τ) = Σ_{j<W} (x[s+j] − x[s+j+τ])²` for τ in 0..[lastLag],
 * computed with the FFT instead of the direct O(W · lastLag) double sum.
 *
 * Expanding the square gives `d(τ) = e(0) + e(τ) − 2·r(τ)` (de Cheveigné & Kawahara 2002, eq. 7),
 * where `e(τ) = Σ_{j<W} x[s+τ+j]²` is the energy of the window shifted by τ, kept as a running
 * sum, and `r(τ) = Σ_{j<W} x[s+j] · x[s+j+τ]` is the cross-correlation of the W-sample window with
 * the W + lastLag samples it is compared against. `r` comes from the cross-power spectrum
 * `conj(A)·B` (Wiener–Khinchin): one complex FFT carries both real inputs (window as real part,
 * span as imaginary part, split again by conjugate symmetry) and one inverse FFT gives r. That is
 * O(N log N) with N the next power of two ≥ W + lastLag, so the circular correlation never wraps
 * for τ ≤ lastLag.
 *
 * Doubles throughout: near a dip `d` is a small difference of large terms. Values below
 * [NOISE_FLOOR] of the energies are rounding residue and are set to 0, as the direct sum would give
 * (e.g. for a constant signal).
 *
 * It keeps A and B from the last [compute] for YIN's refinement on the lowest partials: the same
 * cross-correlation, band-limited, at a few lags ([selectBand], [bandCorrelation]), costs one
 * multiply-add per bin of the band and lag instead of another inverse FFT. The window is band-limited
 * first (`H·A`) and then tapered smoothly to 0 at both ends ([taperedEnergy]'s `g`, a 7-tap
 * convolution of its spectrum), so filtering leaves each partial its own frequency and decay (a
 * decaying partial is an eigenfunction of any filter) and the cut-off window's ends, which hold
 * every partial, don't ring into the band.
 *
 * Scratch arrays are allocated once; [compute], [selectBand], [bandCorrelation] and
 * [taperedEnergy] do not allocate. Not thread-safe.
 */
internal class FftDifference(
    /** W: how many samples each lag compares. */
    private val integrationLength: Int,
    /** The longest lag computed. */
    private val lastLag: Int,
) {
    init {
        require(integrationLength > 0 && lastLag > 0) { "Need W > 0 and lastLag > 0, was $integrationLength, $lastLag" }
    }

    /** Samples read from the start index on: W + lastLag. */
    val span: Int = integrationLength + lastLag

    private val fft = Fft(Fft.sizeFor(span))
    private val re = DoubleArray(fft.size)
    private val im = DoubleArray(fft.size)

    /** N, the FFT length: bin k of [selectBand]'s gains is at `k · sampleRate / size` Hz. */
    val size: Int get() = fft.size

    // The window's spectrum A and the span's B for k in 0..N/2, from the last compute.
    private val windowRe = DoubleArray(fft.size / 2 + 1)
    private val windowIm = DoubleArray(fft.size / 2 + 1)
    private val spanRe = DoubleArray(fft.size / 2 + 1)
    private val spanIm = DoubleArray(fft.size / 2 + 1)

    // H·A, the band-limited window's spectrum, before the taper (H = selectBand's gains).
    private val filteredRe = DoubleArray(fft.size / 2 + 1)
    private val filteredIm = DoubleArray(fft.size / 2 + 1)

    // The band's cross-power spectrum H[k]·conj(A_g[k])·B[k] for k below bandBins, from selectBand,
    // A_g the spectrum of the band-limited, tapered window.
    private val bandRe = DoubleArray(fft.size / 2 + 1)
    private val bandIm = DoubleArray(fft.size / 2 + 1)
    private var bandBins = 0

    // The taper g over the window: with u = 2π(j − W/2)/N, θ = πW/N and v = (cos u − cos θ)/(1 − cos θ),
    // which is 1 at the window's middle and 0 at its ends, g = 3v² − 2v³ (smoothstep: flat at both).
    // A polynomial of degree TAPER_ORDER in cos u is a sum of cos(m·u) for m ≤ TAPER_ORDER, so
    // g(j) = Σ_m γ_m e^(2πi·mj/N) with γ_{−m} = conj γ_m, and A_g[k] = Σ_m γ_m · (H·A)[k − m].
    private val theta = PI * integrationLength / fft.size
    private val taperRe = DoubleArray(TAPER_ORDER + 1)
    private val taperIm = DoubleArray(TAPER_ORDER + 1)
    private val taper = DoubleArray(integrationLength)

    init {
        // g(u) = Σ_m a_m cos(m·u), its coefficients from 32 points (exact for so low a degree).
        val points = 32
        val c = cos(theta)
        val samples = DoubleArray(points) { i ->
            val v = (cos(2 * PI * i / points) - c) / (1 - c)
            v * v * (3 - 2 * v)
        }
        for (m in 0..TAPER_ORDER) {
            var a = 0.0
            for (i in 0 until points) a += samples[i] * cos(2 * PI * m * i / points)
            a *= (if (m == 0) 1.0 else 2.0) / points
            // γ_m = a_m/2 · e^(−i·m·θ) for m ≥ 1, γ_0 = a_0: cos(m·u) = cos(2π·mj/N − m·θ).
            val half = if (m == 0) a else a / 2
            taperRe[m] = half * cos(m * theta)
            taperIm[m] = -half * sin(m * theta)
            for (j in 0 until integrationLength) taper[j] += a * cos(m * (2 * PI * j / fft.size - theta))
        }
    }

    /** Writes `d(τ)` for τ in 0..lastLag into [out], reading `x[start until start + span]`. */
    fun compute(x: FloatArray, start: Int, out: DoubleArray) {
        require(start >= 0 && start + span <= x.size) { "Need $span samples from $start, have ${x.size}" }
        require(out.size > lastLag) { "out must hold ${lastLag + 1} values" }
        val n = fft.size
        for (j in 0 until n) {
            val v = if (j < span) x[start + j].toDouble() else 0.0
            re[j] = if (j < integrationLength) v else 0.0
            im[j] = v
        }
        fft.forward(re, im)
        crossPowerInPlace(n)
        fft.inverse(re, im)
        // re[τ] / n is now r(τ).

        var e0 = 0.0
        for (j in 0 until integrationLength) {
            val v = x[start + j].toDouble()
            e0 += v * v
        }
        var shifted = e0
        out[0] = 0.0
        val scale = 2.0 / n
        for (tau in 1..lastLag) {
            val leaving = x[start + tau - 1].toDouble()
            val entering = x[start + tau - 1 + integrationLength].toDouble()
            shifted += entering * entering - leaving * leaving
            val d = e0 + shifted - scale * re[tau]
            out[tau] = if (d > NOISE_FLOOR * (e0 + shifted)) d else 0.0
        }
    }

    /**
     * Picks the band for [bandCorrelation] from the last [compute]: zero-phase gains [gains] (H) for
     * the bins 1 until [bins], DC and everything from [bins] up left out. Costs about 2·TAPER_ORDER + 2
     * multiply-adds per bin.
     */
    fun selectBand(gains: DoubleArray, bins: Int) {
        require(bins in 2..fft.size / 2 - taperRe.size) { "bins must be in 2..${fft.size / 2 - taperRe.size}, was $bins" }
        // The band-limited window first (H·A), so its partials keep their own frequency and decay.
        val taps = taperRe.size - 1
        for (k in 0..bins + taps) {
            val gain = if (k in 1 until bins) gains[k] else 0.0
            filteredRe[k] = gain * windowRe[k]
            filteredIm[k] = gain * windowIm[k]
        }
        for (k in 1 until bins) {
            // A_g[k] = γ0·(H·A)[k] + Σ_m (γ_m·(H·A)[k−m] + conj(γ_m)·(H·A)[k+m]), with
            // (H·A)[−m] = conj (H·A)[m] for the real window.
            var gr = taperRe[0] * filteredRe[k]
            var gi = taperRe[0] * filteredIm[k]
            for (m in 1 until taperRe.size) {
                val tr = taperRe[m]
                val ti = taperIm[m]
                val backRe = if (k >= m) filteredRe[k - m] else filteredRe[m - k]
                val backIm = if (k >= m) filteredIm[k - m] else -filteredIm[m - k]
                gr += tr * backRe - ti * backIm
                gi += tr * backIm + ti * backRe
                gr += tr * filteredRe[k + m] + ti * filteredIm[k + m]
                gi += tr * filteredIm[k + m] - ti * filteredRe[k + m]
            }
            // H·conj(A_g)·B.
            val br = spanRe[k]
            val bi = spanIm[k]
            bandRe[k] = gains[k] * (gr * br + gi * bi)
            bandIm[k] = gains[k] * (gr * bi - gi * br)
        }
        bandBins = bins
    }

    /**
     * `Σ_{j<W} g(j) · x[start + lag + j]²`: the energy of the window shifted by [lag], under the taper
     * [selectBand] gives the window. W multiply-adds.
     */
    fun taperedEnergy(x: FloatArray, start: Int, lag: Int): Double {
        var sum = 0.0
        val from = start + lag
        for (j in 0 until integrationLength) {
            val v = x[from + j].toDouble()
            sum += taper[j] * v * v
        }
        return sum
    }

    /**
     * The band-limited cross-correlation at lag [tau], `r_g(τ) = (2/N) Σ_{1≤k<bins} H[k] ·
     * Re(conj(A_g[k])·B[k] · e^(2πi·kτ/N))`: `Σ_j g(j)·y[j]·y[j + τ]`, y the span filtered by
     * [selectBand]'s gains (applied once on each side, so H²) and g its taper. One multiply-add per bin.
     */
    fun bandCorrelation(tau: Int): Double {
        val mask = fft.size - 1
        var angle = 0
        var sum = 0.0
        for (k in 1 until bandBins) {
            angle = (angle + tau) and mask
            sum += bandRe[k] * fft.cosOf(angle) - bandIm[k] * fft.sinOf(angle)
        }
        return 2 * sum / fft.size
    }

    // With Z = FFT(a + i·b) for real a and b: A[k] = (Z[k] + conj Z[n−k]) / 2 and
    // B[k] = (Z[k] − conj Z[n−k]) / 2i. Replaces Z by P = conj(A)·B, the spectrum of the
    // cross-correlation. P[n−k] = conj P[k] because the correlation is real.
    private fun crossPowerInPlace(n: Int) {
        for (k in 0..n / 2) {
            val m = if (k == 0) 0 else n - k
            val zr = re[k]
            val zi = im[k]
            val mr = re[m]
            val mi = im[m]
            val ar = 0.5 * (zr + mr)
            val ai = 0.5 * (zi - mi)
            val br = 0.5 * (zi + mi)
            val bi = 0.5 * (mr - zr)
            windowRe[k] = ar
            windowIm[k] = ai
            spanRe[k] = br
            spanIm[k] = bi
            val pr = ar * br + ai * bi
            val pi = ar * bi - ai * br
            re[k] = pr
            re[m] = pr
            if (m == k) {
                im[k] = 0.0
            } else {
                im[k] = pi
                im[m] = -pi
            }
        }
    }

    internal companion object {
        /** The taper's degree in cos u: its spectrum has 2·TAPER_ORDER + 1 lines. */
        const val TAPER_ORDER = 3

        // Rounding error of e(0) + e(τ) − 2r(τ) in doubles is around 1e-15 of the energies.
        const val NOISE_FLOOR = 1e-12
    }
}
