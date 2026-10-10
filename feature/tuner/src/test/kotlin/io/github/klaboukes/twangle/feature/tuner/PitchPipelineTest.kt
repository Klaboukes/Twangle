package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.audio.AudioConfig
import io.github.klaboukes.twangle.core.audio.AudioRecorderException
import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log2

class PitchPipelineTest {

    private fun cents(measured: Float, expected: Float) = 1200f * log2(measured / expected)

    @Test
    fun `a steady note gives one accurate reading per hop`() = runTest {
        val recorder = FakeAudioRecorder(TestSignals.sawtooth(110f, length = 24 * 1024))
        val readings = PitchPipeline(recorder, StandardTestDispatcher(testScheduler)).readings().toList()

        // First frame after 4096 samples, then one per 1024: (24 · 1024 − 4096) / 1024 + 1.
        assertEquals(21, readings.size)
        for (r in readings) {
            assertTrue(r.gateOpen)
            assertTrue("off by ${cents(r.frequencyHz, 110f)} c", abs(cents(r.frequencyHz, 110f)) < 1f)
            assertTrue(r.confidence > 0.8f)
        }
    }

    @Test
    fun `follows the recorder's sample rate`() = runTest {
        val config = AudioConfig(sampleRate = 44_100)
        val signal = TestSignals.sine(196f, length = 12_000, sampleRate = 44_100)
        val readings = PitchPipeline(FakeAudioRecorder(signal, config), StandardTestDispatcher(testScheduler))
            .readings().toList()

        assertTrue(readings.isNotEmpty())
        assertTrue(readings.all { abs(cents(it.frequencyHz, 196f)) < 1f })
    }

    @Test
    fun `low headset-style rates work end to end at the same pace`() = runTest {
        for (rate in listOf(8_000, 16_000)) {
            val config = AudioConfig.forSampleRate(rate)
            // A whole number of chunks, so the fake recorder doesn't pad the end with silence.
            val signal = TestSignals.pluck(82.41f, length = 94 * config.chunkSize, sampleRate = rate, decaySeconds = 2.0)
            val pipeline = PitchPipeline(FakeAudioRecorder(signal, config), StandardTestDispatcher(testScheduler))
            val readings = pipeline.readings().toList()

            assertEquals("frame time at $rate Hz", 1024f / 48_000f, pipeline.frameSeconds, 1e-4f)
            // One reading per hop after the first window (≈ 4 hops): 90–91 of the 94 chunks.
            assertTrue("${readings.size} readings at $rate Hz", readings.size in 90..91)
            for (r in readings) {
                assertTrue("$rate Hz: off by ${cents(r.frequencyHz, 82.41f)} c", abs(cents(r.frequencyHz, 82.41f)) < 1f)
            }
        }
    }

    @Test
    fun `an unsupported sample rate ends with an initialization error instead of crashing`() = runTest {
        val recorder = FakeAudioRecorder(FloatArray(4000), AudioConfig(sampleRate = 4_000))
        var failure: Throwable? = null
        PitchPipeline(recorder, StandardTestDispatcher(testScheduler)).readings().catch { failure = it }.toList()

        assertTrue("got $failure", failure is AudioRecorderException.InitializationFailed)
    }

    @Test
    fun `quiet input keeps the gate closed and reports no pitch`() = runTest {
        val quiet = TestSignals.sine(110f, length = 8192, amplitude = 0.001f) // ≈ −63 dBFS
        val readings = PitchPipeline(FakeAudioRecorder(quiet), StandardTestDispatcher(testScheduler))
            .readings().toList()

        assertTrue(readings.isNotEmpty())
        for (r in readings) {
            assertFalse(r.gateOpen)
            assertFalse(r.isPitched)
            assertTrue(r.levelDbfs < -50f)
        }
    }

    @Test
    fun `recorder failures reach the collector`() = runTest {
        val recorder = FakeAudioRecorder(failure = AudioRecorderException.MicrophoneBusy())
        var failure: Throwable? = null
        PitchPipeline(recorder, StandardTestDispatcher(testScheduler)).readings().catch { failure = it }.toList()

        assertTrue(failure is AudioRecorderException.MicrophoneBusy)
    }
}
