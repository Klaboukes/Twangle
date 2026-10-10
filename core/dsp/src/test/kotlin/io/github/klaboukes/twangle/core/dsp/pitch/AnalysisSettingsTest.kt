package io.github.klaboukes.twangle.core.dsp.pitch

import io.github.klaboukes.twangle.core.dsp.allocatedBytes
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisSettingsTest {

    @Test
    fun `48 kHz keeps the tuned defaults`() {
        val settings = AnalysisSettings.forSampleRate(48_000)
        assertEquals(4096, settings.windowSize)
        assertEquals(1024, settings.hopSize)
        assertEquals(1, settings.upsampleFactor)
        assertEquals(YinPitchDetector.DEFAULT_MIN_FREQUENCY_HZ, settings.minFrequencyHz)
        assertEquals(YinPitchDetector.DEFAULT_MAX_FREQUENCY_HZ, settings.maxFrequencyHz)
        assertEquals(YinPitchDetector.DEFAULT_LOW_PASS_HZ, settings.lowPassHz)
        val detector = settings.newPitchDetector()
        assertTrue(detector is YinPitchDetector)
        assertEquals(4096, detector.windowSize)
    }

    @Test
    fun `other rates keep the same window and hop in time`() {
        for (rate in listOf(8_000, 16_000, 22_050, 44_100, 96_000)) {
            val settings = AnalysisSettings.forSampleRate(rate)
            assertEquals("window at $rate", 4096 / 48_000.0, settings.windowSize.toDouble() / rate, 1e-4)
            assertEquals("hop at $rate", 1024 / 48_000f, settings.hopSeconds, 1e-4f)
            val detector = settings.newPitchDetector()
            assertEquals(rate, detector.sampleRate)
            assertEquals(settings.windowSize, detector.windowSize)
        }
    }

    @Test
    fun `low rates are upsampled to at least 32 kHz`() {
        val narrowband = AnalysisSettings.forSampleRate(8_000)
        assertEquals(683, narrowband.windowSize)
        assertEquals(171, narrowband.hopSize)
        assertEquals(4, narrowband.upsampleFactor)
        assertEquals(32_000, narrowband.analysisRate)
        assertEquals(3_200f, narrowband.lowPassHz)
        assertTrue(narrowband.newPitchDetector() is UpsamplingPitchDetector)
        assertEquals(2, AnalysisSettings.forSampleRate(16_000).upsampleFactor)
        assertEquals(2, AnalysisSettings.forSampleRate(22_050).upsampleFactor)
        assertEquals(1, AnalysisSettings.forSampleRate(32_000).upsampleFactor)
    }

    @Test
    fun `rates outside 8 to 96 kHz are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { AnalysisSettings.forSampleRate(4_000) }
        assertThrows(IllegalArgumentException::class.java) { AnalysisSettings.forSampleRate(192_000) }
    }

    @Test
    fun `the upsampling detector does not allocate and leaves the frame alone`() {
        val settings = AnalysisSettings.forSampleRate(8_000)
        val detector = settings.newPitchDetector()
        val frame = TestSignals.pluck(110f, length = settings.windowSize, sampleRate = 8_000)
        val copy = frame.copyOf()
        repeat(300) { detector.detect(frame) }
        var result = PitchResult.NONE
        val bytes = allocatedBytes { repeat(100) { result = detector.detect(frame) } }
        assertTrue(result.isPitched)
        assertEquals("bytes allocated by 100 detect() calls", 0L, bytes)
        assertArrayEquals(copy, frame, 0f)
    }

    @Test
    fun `the upsampling detector rejects bad settings and short frames`() {
        val inner = YinPitchDetector(sampleRate = 32_000, windowSize = 2668)
        assertThrows(IllegalArgumentException::class.java) { UpsamplingPitchDetector(1, inner) }
        assertThrows(IllegalArgumentException::class.java) { UpsamplingPitchDetector(3, inner) }
        val detector = UpsamplingPitchDetector(4, inner)
        assertEquals(8_000, detector.sampleRate)
        assertEquals(2668 / 4 + 32, detector.windowSize)
        assertThrows(IllegalArgumentException::class.java) { detector.detect(FloatArray(detector.windowSize - 1)) }
    }
}
