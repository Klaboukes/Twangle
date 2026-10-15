package io.github.klaboukes.twangle.app

import io.github.klaboukes.twangle.core.dsp.pitch.YinPitchDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log2

/** The benchmark build's synthetic input: it must sweep as documented and read as that pitch. */
class SweepToneTest {

    @Test
    fun sweepsAcrossAboutThirtyCentsEachWay() {
        val cents = (0 until 8_000).map { SweepTone.centsAt(it / 1000.0) }
        assertTrue("max ${cents.max()}", cents.max() in 28.0..32.0)
        assertTrue("min ${cents.min()}", cents.min() in -32.0..-28.0)
    }

    @Test
    fun staysInTuneForAboutASecondTwicePerSweep() {
        // The tuner's in-tune window is ±5 cents held for 0.3 s; the glow needs time to show.
        for (centre in listOf(0.0, SweepTone.SWEEP_SECONDS / 2)) {
            var start = centre
            while (abs(SweepTone.centsAt(start - 0.001)) <= 5.0) start -= 0.001
            var end = centre
            while (abs(SweepTone.centsAt(end + 0.001)) <= 5.0) end += 0.001
            assertTrue("in tune for ${end - start} s around $centre s", end - start >= 0.8)
        }
    }

    @Test
    fun yinHearsThePitchItSweepsTo() {
        val sampleRate = 48_000
        val tone = SweepTone(sampleRate)
        val samples = FloatArray(sampleRate * 9)
        val chunk = FloatArray(1024)
        var filled = 0
        while (filled < samples.size) {
            tone.fill(chunk)
            val n = minOf(chunk.size, samples.size - filled)
            chunk.copyInto(samples, filled, 0, n)
            filled += n
        }
        val yin = YinPitchDetector(sampleRate = sampleRate)
        val window = FloatArray(yin.windowSize)
        var start = 0
        while (start + window.size <= samples.size) {
            samples.copyInto(window, 0, start, start + window.size)
            val result = yin.detect(window)
            assertTrue("no pitch at sample $start", result.isPitched)
            val heard = 1200f * log2(result.frequencyHz / SweepTone.A4_HZ.toFloat())
            val expected = SweepTone.centsAt((start + window.size / 2.0) / sampleRate)
            assertEquals("at ${start / sampleRate.toDouble()} s", expected, heard.toDouble(), 1.5)
            start += sampleRate / 10
        }
    }
}
