package io.github.klaboukes.twangle.core.dsp.metronome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class ClickSoundsTest {

    @Test
    fun `every click is 40 ms, starts and ends on silence and never clips`() {
        for (rate in listOf(16_000, 44_100, 48_000, 96_000)) {
            val sounds = ClickSounds(rate)
            assertEquals(rate * 40 / 1000, sounds.clickFrames)
            for (accent in BeatAccent.entries) {
                val click = sounds.waveform(accent)
                assertEquals(sounds.clickFrames, click.size)
                assertEquals("$accent at $rate Hz starts at 0", 0f, click.first(), 0f)
                assertEquals("$accent at $rate Hz ends at 0", 0f, click.last(), 1e-6f)
                val peak = click.maxOf { abs(it) }
                assertEquals("$accent peak at $rate Hz", ClickSounds.peakOf(accent), peak.toDouble(), 1e-6)
                assertTrue(peak <= 0.8f)
            }
        }
    }

    @Test
    fun `the downbeat is higher and louder than a group accent, which beats a plain beat`() {
        val sounds = ClickSounds(48_000)
        val order = listOf(BeatAccent.DOWNBEAT, BeatAccent.GROUP, BeatAccent.PLAIN)
        val pitches = order.map { strongestFrequency(sounds.waveform(it), 48_000) }
        val levels = order.map { rms(sounds.waveform(it)) }
        assertTrue("pitches $pitches", pitches[0] > pitches[1] && pitches[1] > pitches[2])
        assertTrue("levels $levels", levels[0] > levels[1] && levels[1] > levels[2])
        for ((accent, pitch) in order.zip(pitches)) {
            assertEquals("$accent pitch", ClickSounds.frequencyOf(accent), pitch, 25.0)
        }
    }

    @Test
    fun `an upper mode past 0,45 of the rate is left out, so nothing folds back`() {
        // The downbeat's upper mode (4.85 kHz) would fold back to about 3.15 kHz at 8 kHz, so it is left out.
        val rate = 8_000
        val click = ClickSounds(rate).waveform(BeatAccent.DOWNBEAT)
        val low = magnitude(click, rate, ClickSounds.frequencyOf(BeatAccent.DOWNBEAT))
        for (f in 3_000..3_300 step 10) {
            assertTrue("energy at $f Hz", magnitude(click, rate, f.toDouble()) < low * 0.05)
        }
    }

    @Test
    fun `the same rate gives the same clicks`() {
        val a = ClickSounds(44_100).waveform(BeatAccent.GROUP)
        val b = ClickSounds(44_100).waveform(BeatAccent.GROUP)
        assertTrue(a.contentEquals(b))
    }

    private fun rms(x: FloatArray): Double = sqrt(x.sumOf { it.toDouble() * it } / x.size)

    // Magnitude of the click's spectrum at frequencyHz (one DFT bin, zero-padded resolution).
    private fun magnitude(x: FloatArray, rate: Int, frequencyHz: Double): Double {
        var re = 0.0
        var im = 0.0
        for (n in x.indices) {
            val phase = 2 * PI * frequencyHz * n / rate
            re += x[n] * cos(phase)
            im -= x[n] * sin(phase)
        }
        return sqrt(re * re + im * im)
    }

    private fun strongestFrequency(x: FloatArray, rate: Int): Double =
        (200..4_000 step 5).maxBy { magnitude(x, rate, it.toDouble()) }.toDouble()
}
