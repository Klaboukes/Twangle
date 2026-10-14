package io.github.klaboukes.twangle.core.dsp.strum

import io.github.klaboukes.twangle.core.dsp.pitch.Fft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.sqrt

class PartialFitTest {

    private val rate = 48_000
    private val n = 16_384
    private val fftSize = 2 * n
    private val lobeHz = 2.0 * rate / n // Main-lobe half-width of the Hann window: 5.9 Hz.
    private val re = DoubleArray(fftSize)
    private val im = DoubleArray(fftSize)
    private val fit = PartialFit(n, fftSize, rate, maxOthers = 3)

    // Spectrum as StrumDetector makes it: periodic Hann window, 2× zero padding.
    private fun spectrum(vararg partials: Pair<Double, Double>) {
        re.fill(0.0)
        im.fill(0.0)
        for (i in 0 until n) {
            var x = 0.0
            for ((index, partial) in partials.withIndex()) x += partial.second * cos(2 * PI * partial.first * i / rate + index)
            re[i] = x * (0.5 - 0.5 * cos(2 * PI * i / n))
        }
        Fft(fftSize).forward(re, im)
    }

    private fun cents(measured: Double, truth: Double) = 1200 * log2(measured / truth)

    @Test
    fun `a lone partial is found exactly`() {
        val hz = 247.123
        spectrum(hz to 0.3)
        assertTrue(fit.fit(re, im, 247.0, 247.0 - 0.5 * lobeHz, 247.0 + 0.5 * lobeHz, doubleArrayOf(247.0 + 2 * lobeHz), 1))
        assertEquals(0.0, cents(fit.frequencyHz, hz), 0.01)
        assertEquals(0.3, fit.amplitude, 0.001)
    }

    @Test
    fun `a partial three quarters of a main lobe from another is told apart from it`() {
        val ours = 221.9
        val other = ours - 0.75 * lobeHz
        spectrum(ours to 0.5, other to 0.8)
        assertTrue(fit.fit(re, im, ours, other + 0.5 * lobeHz, ours + 0.5 * lobeHz, doubleArrayOf(other), 1))
        assertEquals(0.0, cents(fit.frequencyHz, ours), 0.01)
        assertEquals(0.5, fit.amplitude, 0.002)
    }

    @Test
    fun `two neighbours either side`() {
        val ours = 659.3
        spectrum(ours to 0.2, ours - 1.2 * lobeHz to 0.4, ours + 0.9 * lobeHz to 0.3)
        val others = doubleArrayOf(ours - 1.2 * lobeHz, ours + 0.9 * lobeHz)
        assertTrue(fit.fit(re, im, ours, ours - 0.5 * lobeHz, ours + 0.4 * lobeHz, others, 2))
        assertEquals(0.0, cents(fit.frequencyHz, ours), 0.01)
        assertEquals(0.2, fit.amplitude, 0.002)
    }

    @Test
    fun `a plain peak pick would be pulled by the neighbour`() {
        // The case the fit exists for: the merged peak sits well off our partial.
        val ours = 221.9
        val other = ours - 0.75 * lobeHz
        spectrum(ours to 0.5, other to 0.8)
        val from = ((ours - lobeHz) * fftSize / rate).toInt()
        val to = ((ours + lobeHz) * fftSize / rate).toInt()
        var peak = from
        for (b in from..to) if (re[b] * re[b] + im[b] * im[b] > re[peak] * re[peak] + im[peak] * im[peak]) peak = b
        assertTrue(abs(cents(peak.toDouble() * rate / fftSize, ours)) > 5)
    }
}
