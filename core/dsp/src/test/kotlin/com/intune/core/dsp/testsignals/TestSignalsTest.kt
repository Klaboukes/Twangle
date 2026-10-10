package com.intune.core.dsp.testsignals

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.sin

class TestSignalsTest {

    // 480 Hz has a period of exactly 100 samples, so with N = 4800 every harmonic of it falls
    // on an exact DFT bin and the measured amplitudes are leakage-free.
    private val f = 480f

    @Test
    fun `sine has the requested amplitude, frequency and phase`() {
        val s = TestSignals.sine(f, length = N, amplitude = 0.4f)
        assertEquals(0.4, amplitudeAt(s, f), 1e-4)
        assertEquals(0.0, amplitudeAt(s, 2 * f), 1e-4)
        assertEquals(0f, s[0], 0f)
        assertEquals(0.4f, TestSignals.sine(f, amplitude = 0.4f, phase = PI / 2)[0], 1e-6f)
    }

    @Test
    fun `sawtooth has every harmonic at 1 over k and nothing above the limit`() {
        val s = TestSignals.sawtooth(f, length = N, amplitude = 0.5f, maxHarmonicHz = 2400f)
        val h1 = amplitudeAt(s, f)
        for (k in 2..4) assertEquals("harmonic $k", h1 / k, amplitudeAt(s, k * f), 1e-4)
        assertEquals("harmonic 5 sits on the limit", 0.0, amplitudeAt(s, 5 * f), 1e-4)
        assertEquals(0.5f, s.maxOf { abs(it) }, 1e-6f)
    }

    @Test
    fun `square has odd harmonics at 1 over k and no even ones`() {
        val s = TestSignals.square(f, length = N)
        val h1 = amplitudeAt(s, f)
        assertEquals(h1 / 3, amplitudeAt(s, 3 * f), 1e-4)
        assertEquals(h1 / 5, amplitudeAt(s, 5 * f), 1e-4)
        for (k in listOf(2, 4, 6)) assertEquals("harmonic $k", 0.0, amplitudeAt(s, k * f), 1e-4)
    }

    @Test
    fun `harmonic-rich signals stay below Nyquist`() {
        val s = TestSignals.sawtooth(f, length = N)
        // 49 × 480 = 23 520 Hz is the last harmonic below 24 kHz. 50 × 480 = 24 kHz is left out.
        assertTrue(amplitudeAt(s, 49 * f) > 1e-4)
        assertEquals(0.0, amplitudeAt(s, 50 * f), 1e-4)
    }

    @Test
    fun `weak fundamental is quieter than its second harmonic`() {
        val s = TestSignals.weakFundamental(f, length = N)
        val expected = TestSignals.WEAK_FUNDAMENTAL_HARMONICS
        val h2 = amplitudeAt(s, 2 * f)
        for (k in 1..expected.size) {
            assertEquals("harmonic $k", expected[k - 1].toDouble(), amplitudeAt(s, k * f) / h2, 1e-3)
        }
    }

    @Test
    fun `pluck dies away and loses its brightness`() {
        val s = TestSignals.pluck(f, length = N * 4, decaySeconds = 0.1)
        val start = s.copyOfRange(0, N)
        val end = s.copyOfRange(3 * N, 4 * N)
        assertTrue(TestSignals.rms(end) < TestSignals.rms(start) / 10)
        val brightnessStart = amplitudeAt(start, 3 * f) / amplitudeAt(start, f)
        val brightnessEnd = amplitudeAt(end, 3 * f) / amplitudeAt(end, f)
        assertTrue(brightnessEnd < brightnessStart / 2)
    }

    @Test
    fun `pluck with given harmonics starts with those amplitudes`() {
        val s = TestSignals.pluck(f, floatArrayOf(0.2f, 1f, 0.5f), length = N, decaySeconds = 100.0)
        val h2 = amplitudeAt(s, 2 * f)
        assertEquals(0.2, amplitudeAt(s, f) / h2, 0.01)
        assertEquals(0.5, amplitudeAt(s, 3 * f) / h2, 0.01)
        assertEquals(0.0, amplitudeAt(s, 4 * f), 1e-4)
    }

    @Test
    fun `hum has the requested level and mains harmonics`() {
        val hum = TestSignals.hum(baseHz = 60f, length = N, rms = 0.05f, harmonicCount = 3)
        assertEquals(0.05, TestSignals.rms(hum), 1e-4)
        val h1 = amplitudeAt(hum, 60f)
        assertEquals(h1 / 2, amplitudeAt(hum, 120f), 1e-4)
        assertEquals(h1 / 3, amplitudeAt(hum, 180f), 1e-4)
        assertEquals(0.0, amplitudeAt(hum, 240f), 1e-4)
    }

    @Test
    fun `mix adds the interference at the requested ratio`() {
        val clean = TestSignals.sine(f, length = 48_000)
        val hum = TestSignals.hum(length = 48_000)
        val mixed = TestSignals.mix(clean, hum, snrDb = 6f)
        val residual = FloatArray(clean.size) { mixed[it] - clean[it] }
        assertEquals(6.0, 20 * log10(TestSignals.rms(clean) / TestSignals.rms(residual)), 0.01)
    }

    @Test
    fun `bandLimit passes the band and removes what is outside it`() {
        // Steady state: skip the filter's start-up before measuring.
        fun gain(hz: Float): Double {
            val tone = TestSignals.sine(hz, length = 2 * N, amplitude = 1f)
            val out = TestSignals.bandLimit(tone, lowHz = 300f, highHz = 3400f).copyOfRange(N, 2 * N)
            return amplitudeAt(out, hz)
        }
        assertEquals(1.0, gain(960f), 0.02)
        assertTrue("100 Hz", gain(100f) < 0.02)
        assertTrue("9600 Hz", gain(9600f) < 0.01)
    }

    @Test
    fun `noise is repeatable per seed with the requested level`() {
        val a = TestSignals.noise(length = 48_000, rms = 0.05f, seed = 7)
        assertArrayEquals(a, TestSignals.noise(length = 48_000, rms = 0.05f, seed = 7), 0f)
        assertTrue(!a.contentEquals(TestSignals.noise(length = 48_000, rms = 0.05f, seed = 8)))
        assertEquals(0.05, TestSignals.rms(a), 0.001)
        assertEquals(0.0, a.average(), 0.001)
    }

    @Test
    fun `withNoise adds noise at the requested signal-to-noise ratio`() {
        val clean = TestSignals.sine(f, length = 48_000)
        val noisy = TestSignals.withNoise(clean, snrDb = 20f)
        val residual = FloatArray(clean.size) { noisy[it] - clean[it] }
        val snr = 20 * log10(TestSignals.rms(clean) / TestSignals.rms(residual))
        assertEquals(20.0, snr, 0.2)
    }

    // Amplitude of the sinusoid at [hz], from a single DFT bin (exact when hz is on a bin).
    private fun amplitudeAt(signal: FloatArray, hz: Float): Double {
        var re = 0.0
        var im = 0.0
        val step = 2 * PI * hz / TestSignals.SAMPLE_RATE
        for (i in signal.indices) {
            re += signal[i] * cos(step * i)
            im += signal[i] * sin(step * i)
        }
        return 2 * hypot(re, im) / signal.size
    }

    private companion object {
        const val N = 4800
    }
}
