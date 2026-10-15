package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.audio.AudioConfig
import io.github.klaboukes.twangle.core.audio.AudioRecorderException
import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
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
import kotlin.math.pow

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
    fun `the low range reads B0 after one long window, the standard range does not`() = runTest {
        val b0 = 30.87f
        val signal = TestSignals.weakFundamental(b0, length = 24 * 1024)
        val low = PitchPipeline(FakeAudioRecorder(signal), StandardTestDispatcher(testScheduler))
            .readings { PitchRange.LOW }.toList()

        // First frame after 8192 samples, then one per 1024: (24 · 1024 − 8192) / 1024 + 1.
        assertEquals(17, low.size)
        for (r in low) assertTrue("off by ${cents(r.frequencyHz, b0)} c", abs(cents(r.frequencyHz, b0)) < 1f)

        val standard = PitchPipeline(FakeAudioRecorder(TestSignals.sine(b0, length = 24 * 1024)), StandardTestDispatcher(testScheduler))
            .readings().toList()
        assertEquals(21, standard.size)
        assertTrue(standard.none { it.isPitched })
    }

    @Test
    fun `switching range mid-note takes effect on the next hop without a gap`() = runTest {
        val e1 = 41.2f
        val signal = TestSignals.sine(e1, length = 24 * 1024)
        var frames = 0
        val readings = PitchPipeline(FakeAudioRecorder(signal), StandardTestDispatcher(testScheduler))
            .readings { if (frames++ < 8) PitchRange.STANDARD else PitchRange.LOW }.toList()

        // Same pace as the standard range throughout: the long window had already filled.
        assertEquals(21, readings.size)
        assertTrue(readings.take(8).none { it.isPitched })
        for (r in readings.drop(8)) assertTrue("off by ${cents(r.frequencyHz, e1)} c", abs(cents(r.frequencyHz, e1)) < 1f)
    }

    @Test
    fun `readings carry the room's noise floor, the note's SNR and its clarity`() = runTest {
        // 1 s of room noise at −50 dBFS, then a note 20 dB above it for 1 s.
        val room = TestSignals.noise(96_000, rms = 10f.pow(-50f / 20f), seed = 3)
        val note = TestSignals.sawtooth(110f, length = 48_000)
        val scale = (10.0.pow(-30.0 / 20.0) / TestSignals.rms(note)).toFloat()
        for (i in note.indices) room[48_000 + i] += scale * note[i]
        val readings = PitchPipeline(FakeAudioRecorder(room), StandardTestDispatcher(testScheduler)).readings().toList()

        val before = readings.subList(5, 40)
        assertTrue(before.all { !it.isPitched && it.snrDb.isNaN() && it.clarity == 0f })
        assertTrue(before.all { abs(it.noiseFloorDbfs + 50f) < 0.5f })
        val during = readings.subList(52, 85)
        for (r in during) {
            assertTrue(r.isPitched)
            assertEquals(-50f, r.noiseFloorDbfs, 0.5f)
            assertEquals(20f, r.snrDb, 1f)
            assertTrue("clarity ${r.clarity}", r.clarity > 0.95f)
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
    fun `a microphone that opens at another rate than expected is still read correctly`() = runTest {
        // The phone refused 48 kHz, so the mic opened at 44.1 kHz; frame sizes must follow.
        val opened = AudioConfig.forSampleRate(44_100)
        val signal = TestSignals.sine(196f, length = 30 * opened.chunkSize, sampleRate = 44_100)
        val recorder = FakeAudioRecorder(signal, AudioConfig(), openedConfig = opened)
        val readings = PitchPipeline(recorder, StandardTestDispatcher(testScheduler)).readings().toList()

        // First frame after one 3763-sample window, then one per 941-sample hop.
        assertEquals((30 * 941 - 3763) / 941 + 1, readings.size)
        assertTrue(readings.all { abs(cents(it.frequencyHz, 196f)) < 1f })
        assertEquals(44_100, recorder.config.sampleRate)
    }

    @Test
    fun `a microphone silenced mid-note ends the readings with a busy error`() = runTest {
        val recorder = FakeAudioRecorder(
            TestSignals.sawtooth(110f, length = 24 * 1024),
            failure = AudioRecorderException.MicrophoneBusy(),
        )
        val readings = mutableListOf<PitchReading>()
        var failure: Throwable? = null
        PitchPipeline(recorder, StandardTestDispatcher(testScheduler)).readings()
            .catch { failure = it }
            .collect { readings += it }

        // The note was read until the end (the last hop may be cut off by the failure).
        assertTrue("${readings.size} readings", readings.size >= 20)
        assertTrue(readings.all { it.isPitched })
        assertTrue("got $failure", failure is AudioRecorderException.MicrophoneBusy)
        assertFalse(recorder.isRecording.value)
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
