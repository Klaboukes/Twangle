package com.intune.core.dsp.pitch

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * In-place complex FFT of a fixed power-of-two [size]: iterative radix-2 Cooley–Tukey, decimation
 * in time (Cooley & Tukey 1965, "An algorithm for the machine calculation of complex Fourier
 * series"). Real and imaginary parts live in two separate arrays of [size] doubles.
 *
 * The twiddle factors and the bit-reversal order are computed once, so [forward] and [inverse] do
 * not allocate. The tables are read-only, so one instance can be shared.
 */
internal class Fft(val size: Int) {

    init {
        require(size >= 2 && size and (size - 1) == 0) { "size must be a power of two ≥ 2, was $size" }
    }

    // cos/sin(2π k / size) for k < size / 2.
    private val cosTable = DoubleArray(size / 2) { cos(2 * PI * it / size) }
    private val sinTable = DoubleArray(size / 2) { sin(2 * PI * it / size) }

    // reversed[i] = i with its log2(size) bits in reverse order.
    private val reversed = IntArray(size).also { table ->
        val bits = Integer.numberOfTrailingZeros(size)
        for (i in table.indices) table[i] = Integer.reverse(i) ushr (Int.SIZE_BITS - bits)
    }

    /** `X[k] = Σ_j x[j] · e^(−2πi·jk/N)`, in place. */
    fun forward(re: DoubleArray, im: DoubleArray) = transform(re, im, sign = -1.0)

    /** `x[j] = Σ_k X[k] · e^(+2πi·jk/N)`, in place and unscaled: the result is N times the original. */
    fun inverse(re: DoubleArray, im: DoubleArray) = transform(re, im, sign = 1.0)

    private fun transform(re: DoubleArray, im: DoubleArray, sign: Double) {
        require(re.size >= size && im.size >= size) { "Arrays must hold $size values" }
        for (i in 0 until size) {
            val j = reversed[i]
            if (j > i) {
                val r = re[i]
                re[i] = re[j]
                re[j] = r
                val m = im[i]
                im[i] = im[j]
                im[j] = m
            }
        }
        // Butterflies: combine pairs of half-length transforms, doubling the length each pass.
        var half = 1
        while (half < size) {
            val step = size / (2 * half)
            for (j in 0 until half) {
                val wr = cosTable[j * step]
                val wi = sign * sinTable[j * step]
                var a = j
                while (a < size) {
                    val b = a + half
                    val tr = wr * re[b] - wi * im[b]
                    val ti = wr * im[b] + wi * re[b]
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                    a += 2 * half
                }
            }
            half *= 2
        }
    }

    companion object {
        /** The smallest power of two ≥ [n] (and ≥ 2). */
        fun sizeFor(n: Int): Int {
            require(n in 1..(1 shl 30)) { "n out of range: $n" }
            return maxOf(2, Integer.highestOneBit(n - 1) shl 1)
        }
    }
}
