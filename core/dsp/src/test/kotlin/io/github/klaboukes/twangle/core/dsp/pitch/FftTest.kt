package io.github.klaboukes.twangle.core.dsp.pitch

import io.github.klaboukes.twangle.core.dsp.allocatedBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class FftTest {

    @Test
    fun `forward matches a direct DFT`() {
        for (n in listOf(2, 8, 64, 512)) {
            val random = Random(n)
            val re = DoubleArray(n) { random.nextDouble(-1.0, 1.0) }
            val im = DoubleArray(n) { random.nextDouble(-1.0, 1.0) }
            val (expectedRe, expectedIm) = dft(re, im)
            Fft(n).forward(re, im)
            for (k in 0 until n) {
                assertEquals("n=$n re[$k]", expectedRe[k], re[k], 1e-9)
                assertEquals("n=$n im[$k]", expectedIm[k], im[k], 1e-9)
            }
        }
    }

    @Test
    fun `inverse undoes forward, scaled by the size`() {
        val n = 4096
        val random = Random(7)
        val re = DoubleArray(n) { random.nextDouble(-1.0, 1.0) }
        val im = DoubleArray(n) { random.nextDouble(-1.0, 1.0) }
        val originalRe = re.copyOf()
        val originalIm = im.copyOf()
        val fft = Fft(n)
        fft.forward(re, im)
        fft.inverse(re, im)
        for (j in 0 until n) {
            assertEquals(originalRe[j], re[j] / n, 1e-12)
            assertEquals(originalIm[j], im[j] / n, 1e-12)
        }
    }

    @Test
    fun `a cosine lands in its two bins`() {
        val n = 256
        val re = DoubleArray(n) { cos(2 * PI * 10 * it / n) }
        val im = DoubleArray(n)
        Fft(n).forward(re, im)
        for (k in 0 until n) {
            val expected = if (k == 10 || k == n - 10) n / 2.0 else 0.0
            assertEquals("bin $k", expected, re[k], 1e-9)
            assertEquals("bin $k", 0.0, im[k], 1e-9)
        }
    }

    @Test
    fun `sizeFor rounds up to a power of two`() {
        assertEquals(2, Fft.sizeFor(1))
        assertEquals(2, Fft.sizeFor(2))
        assertEquals(4, Fft.sizeFor(3))
        assertEquals(4096, Fft.sizeFor(4096))
        assertEquals(8192, Fft.sizeFor(4097))
    }

    @Test
    fun `rejects sizes that are not powers of two`() {
        assertThrows(IllegalArgumentException::class.java) { Fft(12) }
        assertThrows(IllegalArgumentException::class.java) { Fft(1) }
    }

    @Test
    fun `does not allocate`() {
        val fft = Fft(4096)
        val re = DoubleArray(4096) { sin(it * 0.1) }
        val im = DoubleArray(4096)
        repeat(1_000) { fft.forward(re, im) }
        assertEquals(0L, allocatedBytes { repeat(100) { fft.forward(re, im); fft.inverse(re, im) } })
    }

    private fun dft(re: DoubleArray, im: DoubleArray): Pair<DoubleArray, DoubleArray> {
        val n = re.size
        val outRe = DoubleArray(n)
        val outIm = DoubleArray(n)
        for (k in 0 until n) {
            for (j in 0 until n) {
                val angle = -2 * PI * j * k / n
                outRe[k] += re[j] * cos(angle) - im[j] * sin(angle)
                outIm[k] += re[j] * sin(angle) + im[j] * cos(angle)
            }
        }
        return outRe to outIm
    }
}
