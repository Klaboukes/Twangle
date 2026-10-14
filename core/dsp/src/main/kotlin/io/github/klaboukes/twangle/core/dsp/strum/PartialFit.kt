package io.github.klaboukes.twangle.core.dsp.strum

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Measures one partial that overlaps other strings' partials of known frequency, by fitting them
 * all at once to the complex spectrum: the spectrum near the partial is modelled as a sum of
 * Hann-windowed sinusoids, `X(b) ≈ Σ_p c_p · W(f_p − b)`, with `W` the window's exact transform.
 * The other partials' frequencies are fixed (their strings' estimates); for each candidate
 * frequency of ours the complex amplitudes `c_p` follow from linear least squares, and a
 * golden-section search finds the frequency with the smallest residual. This is the nonlinear
 * least-squares estimate of sinusoids in noise (Stoica & Moses, *Spectral Analysis of Signals*,
 * 2005, §4.3), with all frequencies but one known.
 *
 * Two partials closer than about half a main lobe can't be told apart this way (the system becomes
 * singular), so the caller leaves those out and reads them as one merged peak.
 *
 * Works on the spectrum of [StrumDetector]: a periodic Hann window of [frameLength] samples,
 * zero-padded to [fftSize]. Allocates nothing after construction.
 */
internal class PartialFit(
    private val frameLength: Int,
    private val fftSize: Int,
    private val sampleRate: Int,
    maxOthers: Int,
) {

    private val binHz = sampleRate.toDouble() / fftSize
    private val lobeBins = 2.0 * fftSize / frameLength
    private val maxColumns = maxOthers + 1
    private val reachBins = ceil(BIN_REACH_LOBES * lobeBins).toInt()
    private val maxBins = 2 * reachBins + 2

    // Model columns, column p at [p * maxBins, (p + 1) * maxBins): W(f_p − b) for the fitted bins.
    private val gRe = DoubleArray(maxColumns * maxBins)
    private val gIm = DoubleArray(maxColumns * maxBins)

    // Normal equations G^H G c = G^H X (Hermitian, maxColumns²), and a copy solve() works on.
    private val aRe = DoubleArray(maxColumns * maxColumns)
    private val aIm = DoubleArray(maxColumns * maxColumns)
    private val workRe = DoubleArray(maxColumns * maxColumns)
    private val workIm = DoubleArray(maxColumns * maxColumns)
    private val bRe = DoubleArray(maxColumns)
    private val bIm = DoubleArray(maxColumns)
    private val cRe = DoubleArray(maxColumns)
    private val cIm = DoubleArray(maxColumns)

    // G^H X; b is its copy that elimination changes.
    private val xRe = DoubleArray(maxColumns)
    private val xIm = DoubleArray(maxColumns)

    private var spectrumRe = EMPTY
    private var spectrumIm = EMPTY

    private var firstBin = 0
    private var binCount = 0
    private var columns = 0

    // W's constant phase factor for the ±1 shifted Dirichlet kernels: e^(iπ(N−1)/N).
    private val betaCos = cos(PI * (frameLength - 1) / frameLength)
    private val betaSin = sin(PI * (frameLength - 1) / frameLength)
    private val cosPiOverN = cos(PI / frameLength)
    private val sinPiOverN = sin(PI / frameLength)

    /** Frequency of the fitted partial in Hz, after a successful [fit]. */
    var frequencyHz = 0.0
        private set

    /** Amplitude of the fitted partial (a sinusoid's peak value), after a successful [fit]. */
    var amplitude = 0.0
        private set

    /**
     * Fits a partial between [fromHz] and [toHz] (Hz, at most about a main lobe apart) together
     * with the first [otherCount] partials of [othersHz] to the spectrum [re], [im] (bins 0..fftSize/2).
     * The fit uses the bins within [BIN_REACH_LOBES] main-lobe half-widths of [centerHz].
     * Returns false when the best frequency is at an end of the range: no partial of ours there.
     */
    fun fit(
        re: DoubleArray,
        im: DoubleArray,
        centerHz: Double,
        fromHz: Double,
        toHz: Double,
        othersHz: DoubleArray,
        otherCount: Int,
    ): Boolean {
        require(otherCount < maxColumns) { "too many other partials: $otherCount" }
        spectrumRe = re
        spectrumIm = im
        val center = centerHz / binHz
        firstBin = maxOf(1, ceil(center - reachBins).toInt())
        val last = minOf(fftSize / 2 - 1, floor(center + reachBins).toInt())
        binCount = last - firstBin + 1
        if (binCount < 3) return false
        columns = otherCount + 1
        for (p in 1 until columns) fillColumn(p, othersHz[p - 1])
        prepareOthers()

        // Golden-section search for the largest explained energy (the smallest residual).
        var a = fromHz
        var b = toHz
        var x1 = b - GOLDEN * (b - a)
        var x2 = a + GOLDEN * (b - a)
        var e1 = explained(x1)
        var e2 = explained(x2)
        repeat(GOLDEN_STEPS) {
            if (e1 > e2) {
                b = x2
                x2 = x1
                e2 = e1
                x1 = b - GOLDEN * (b - a)
                e1 = explained(x1)
            } else {
                a = x1
                x1 = x2
                e1 = e2
                x2 = a + GOLDEN * (b - a)
                e2 = explained(x2)
            }
        }
        val best = if (e1 > e2) x1 else x2
        val edge = EDGE_SHARE * (toHz - fromHz)
        if (best - fromHz < edge || toHz - best < edge) return false
        explained(best)
        frequencyHz = best
        amplitude = 2 * sqrt(cRe[0] * cRe[0] + cIm[0] * cIm[0])
        return true
    }

    // G^H X and G^H G entries among the other partials' columns, which stay fixed during a fit.
    private fun prepareOthers() {
        for (p in 1 until columns) {
            projectSpectrum(p)
            for (q in 1 until columns) gram(p, q)
        }
    }

    // Energy the model explains with our partial at hz: Re(b^H c) at the least-squares c, which is
    // ‖X‖² minus the residual. Only column 0's entries change with hz.
    private fun explained(hz: Double): Double {
        fillColumn(0, hz)
        projectSpectrum(0)
        for (q in 0 until columns) gram(0, q)
        for (p in 1 until columns) {
            // Hermitian: A[p][0] = conj(A[0][p]).
            aRe[p * maxColumns] = aRe[p]
            aIm[p * maxColumns] = -aIm[p]
        }
        for (p in 0 until columns) {
            bRe[p] = xRe[p]
            bIm[p] = xIm[p]
            for (q in 0 until columns) {
                workRe[p * maxColumns + q] = aRe[p * maxColumns + q]
                workIm[p * maxColumns + q] = aIm[p * maxColumns + q]
            }
        }
        solve()
        var e = 0.0
        for (p in 0 until columns) e += xRe[p] * cRe[p] + xIm[p] * cIm[p]
        return e
    }

    // xRe/xIm[p] = conj(g_p) · X over the fitted bins.
    private fun projectSpectrum(p: Int) {
        var sr = 0.0
        var si = 0.0
        val gp = p * maxBins
        for (i in 0 until binCount) {
            val xr = spectrumRe[firstBin + i]
            val xi = spectrumIm[firstBin + i]
            sr += gRe[gp + i] * xr + gIm[gp + i] * xi
            si += gRe[gp + i] * xi - gIm[gp + i] * xr
        }
        xRe[p] = sr
        xIm[p] = si
    }

    // A[p][q] = conj(g_p) · g_q over the fitted bins.
    private fun gram(p: Int, q: Int) {
        val gp = p * maxBins
        val gq = q * maxBins
        var ar = 0.0
        var ai = 0.0
        for (i in 0 until binCount) {
            ar += gRe[gp + i] * gRe[gq + i] + gIm[gp + i] * gIm[gq + i]
            ai += gRe[gp + i] * gIm[gq + i] - gIm[gp + i] * gRe[gq + i]
        }
        aRe[p * maxColumns + q] = ar
        aIm[p * maxColumns + q] = ai
    }

    // Column p = the window's transform at a sinusoid of hz, over the fitted bins.
    //
    // Hann = ½·D(Δ) − ¼·D(Δ + 2π/N) − ¼·D(Δ − 2π/N) with D the Dirichlet kernel
    // Σ_{n<N} e^(iΔn) = e^(iΔ(N−1)/2) · sin(NΔ/2) / sin(Δ/2) (Harris 1978), Δ = 2π(hz/rate − bin/M).
    // Since sin(N(Δ ± 2π/N)/2) = −sin(NΔ/2), each bin needs sin(NΔ/2), sin and cos of Δ/2, and the
    // phase e^(iΔ(N−1)/2). Δ falls by 2π/M from bin to bin, so these angles are stepped by rotation
    // instead of calling sin and cos per bin.
    private fun fillColumn(p: Int, hz: Double) {
        val delta0 = 2 * PI * (hz / sampleRate - firstBin.toDouble() / fftSize)
        val step = 2 * PI / fftSize
        val offset = p * maxBins
        // a = NΔ/2 (only its sine), b = Δ/2, c = Δ(N−1)/2.
        var sinA = sin(frameLength * delta0 / 2)
        var cosA = cos(frameLength * delta0 / 2)
        var sinB = sin(delta0 / 2)
        var cosB = cos(delta0 / 2)
        var sinC = sin(delta0 * (frameLength - 1) / 2)
        var cosC = cos(delta0 * (frameLength - 1) / 2)
        val stepA = frameLength * step / 2
        val sinStepA = sin(stepA)
        val cosStepA = cos(stepA)
        val sinStepB = sin(step / 2)
        val cosStepB = cos(step / 2)
        val stepC = step * (frameLength - 1) / 2
        val sinStepC = sin(stepC)
        val cosStepC = cos(stepC)
        for (i in 0 until binCount) {
            val r0: Double
            val rPlus: Double
            val rMinus: Double
            val plusDenominator = sinB * cosPiOverN + cosB * sinPiOverN
            val minusDenominator = sinB * cosPiOverN - cosB * sinPiOverN
            if (abs(sinB) < NEAR_ZERO || abs(plusDenominator) < NEAR_ZERO || abs(minusDenominator) < NEAR_ZERO) {
                // At a zero of a denominator the ratio tends to N: evaluate it directly.
                val delta = delta0 - i * step
                r0 = dirichlet(delta)
                rPlus = dirichlet(delta + 2 * PI / frameLength)
                rMinus = dirichlet(delta - 2 * PI / frameLength)
            } else {
                r0 = sinA / sinB
                rPlus = -sinA / plusDenominator
                rMinus = -sinA / minusDenominator
            }
            val wr = 0.5 * r0 - 0.25 * (rPlus + rMinus) * betaCos
            val wi = -0.25 * (rPlus - rMinus) * betaSin
            gRe[offset + i] = wr * cosC - wi * sinC
            gIm[offset + i] = wr * sinC + wi * cosC
            // Next bin: every angle falls by its step.
            val a = sinA * cosStepA - cosA * sinStepA
            cosA = cosA * cosStepA + sinA * sinStepA
            sinA = a
            val b = sinB * cosStepB - cosB * sinStepB
            cosB = cosB * cosStepB + sinB * sinStepB
            sinB = b
            val c = sinC * cosStepC - cosC * sinStepC
            cosC = cosC * cosStepC + sinC * sinStepC
            sinC = c
        }
    }

    // sin(Nα/2) / sin(α/2), N at α = 0.
    private fun dirichlet(alpha: Double): Double {
        val s = sin(alpha / 2)
        return if (abs(s) < 1e-12) frameLength.toDouble() else sin(frameLength * alpha / 2) / s
    }

    // Solves A c = b (complex, columns × columns) by Gaussian elimination with partial pivoting;
    // a tiny ridge keeps it defined when two columns nearly coincide.
    private fun solve() {
        val n = columns
        var trace = 0.0
        for (p in 0 until n) trace += workRe[p * maxColumns + p]
        val ridge = RIDGE * trace / n
        for (p in 0 until n) workRe[p * maxColumns + p] += ridge
        for (k in 0 until n) {
            var pivot = k
            var pivotSize = 0.0
            for (r in k until n) {
                val size = workRe[r * maxColumns + k] * workRe[r * maxColumns + k] + workIm[r * maxColumns + k] * workIm[r * maxColumns + k]
                if (size > pivotSize) {
                    pivotSize = size
                    pivot = r
                }
            }
            if (pivotSize == 0.0) {
                for (p in 0 until n) {
                    cRe[p] = 0.0
                    cIm[p] = 0.0
                }
                return
            }
            if (pivot != k) swapRows(pivot, k)
            val kk = k * maxColumns + k
            for (r in k + 1 until n) {
                val rk = r * maxColumns + k
                // factor = A[r][k] / A[k][k]
                val fr = (workRe[rk] * workRe[kk] + workIm[rk] * workIm[kk]) / pivotSize
                val fi = (workIm[rk] * workRe[kk] - workRe[rk] * workIm[kk]) / pivotSize
                for (q in k until n) {
                    val kq = k * maxColumns + q
                    val rq = r * maxColumns + q
                    workRe[rq] -= fr * workRe[kq] - fi * workIm[kq]
                    workIm[rq] -= fr * workIm[kq] + fi * workRe[kq]
                }
                bRe[r] -= fr * bRe[k] - fi * bIm[k]
                bIm[r] -= fr * bIm[k] + fi * bRe[k]
            }
        }
        for (k in n - 1 downTo 0) {
            var sr = bRe[k]
            var si = bIm[k]
            for (q in k + 1 until n) {
                val kq = k * maxColumns + q
                sr -= workRe[kq] * cRe[q] - workIm[kq] * cIm[q]
                si -= workRe[kq] * cIm[q] + workIm[kq] * cRe[q]
            }
            val kk = k * maxColumns + k
            val d = workRe[kk] * workRe[kk] + workIm[kk] * workIm[kk]
            cRe[k] = (sr * workRe[kk] + si * workIm[kk]) / d
            cIm[k] = (si * workRe[kk] - sr * workIm[kk]) / d
        }
    }

    private fun swapRows(r1: Int, r2: Int) {
        for (q in 0 until columns) {
            val i1 = r1 * maxColumns + q
            val i2 = r2 * maxColumns + q
            var t = workRe[i1]
            workRe[i1] = workRe[i2]
            workRe[i2] = t
            t = workIm[i1]
            workIm[i1] = workIm[i2]
            workIm[i2] = t
        }
        var t = bRe[r1]
        bRe[r1] = bRe[r2]
        bRe[r2] = t
        t = bIm[r1]
        bIm[r1] = bIm[r2]
        bIm[r2] = t
    }

    companion object {
        /** Bins within this many main-lobe half-widths of the partial take part in the fit. */
        const val BIN_REACH_LOBES = 1.5

        private const val GOLDEN = 0.6180339887498949
        private const val GOLDEN_STEPS = 18
        private const val EDGE_SHARE = 0.02
        private const val RIDGE = 1e-9
        private const val NEAR_ZERO = 1e-9
        private val EMPTY = DoubleArray(0)
    }
}
