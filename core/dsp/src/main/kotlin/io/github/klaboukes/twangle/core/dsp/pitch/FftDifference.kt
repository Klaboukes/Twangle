package io.github.klaboukes.twangle.core.dsp.pitch

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
 * (e.g. for a constant signal). Scratch arrays are allocated once; [compute] does not allocate.
 * Not thread-safe.
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

    private companion object {
        // Rounding error of e(0) + e(τ) − 2r(τ) in doubles is around 1e-15 of the energies.
        const val NOISE_FLOOR = 1e-12
    }
}
