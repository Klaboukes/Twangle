package io.github.klaboukes.twangle.wear

import io.github.klaboukes.twangle.core.audio.AudioConfig
import io.github.klaboukes.twangle.core.audio.AudioRecorderException
import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log2

class WatchPitchPipelineTest {

    /** Every state the pipeline sends while the fake recorder plays [signal] once. */
    private suspend fun TestScope.statesFor(signal: FloatArray, config: AudioConfig = AudioConfig()): List<WatchTunerState> =
        WatchPitchPipeline(FakeAudioRecorder(signal, config), dspDispatcher = StandardTestDispatcher(testScheduler))
            .states().toList()

    private fun List<WatchTunerState>.lastDetected(): WatchTunerState.Detected {
        val detected = filterIsInstance<WatchTunerState.Detected>()
        if (detected.isEmpty()) fail("no note detected in $this")
        return detected.last()
    }

    private fun centsBetween(hz: Float, referenceHz: Float): Float = 1200f * log2(hz / referenceHz)

    @Test
    fun `starts listening, then names A4 in tune`() = runTest {
        val states = statesFor(TestSignals.sine(440f, length = 48_000))

        assertEquals(WatchTunerState.Listening, states.first())
        assertEquals(WatchTunerState.Detected(Note.of(PitchClass.A, 4), 0), states.lastDetected())
    }

    @Test
    fun `cents off match the played pitch within one cent`() = runTest {
        for (hz in floatArrayOf(82.41f * 1.006f, 196f * 0.99f, 445f, 659.26f * 1.02f)) {
            val reading = statesFor(TestSignals.sawtooth(hz, length = 48_000)).lastDetected()
            val target = 440f * Math.pow(2.0, (reading.note.midi - 69) / 12.0).toFloat()
            val expected = centsBetween(hz, target)
            assertTrue("$hz Hz read ${reading.cents} cents, expected $expected", abs(reading.cents - expected) <= 1f)
        }
    }

    @Test
    fun `a steady note sends only a few states, not one per frame`() = runTest {
        // Two seconds of a held note is about 94 frames.
        val states = statesFor(TestSignals.sine(196f, length = 96_000))

        assertEquals(Note.of(PitchClass.G, 3), states.lastDetected().note)
        assertTrue("sent ${states.size} states", states.size <= 6)
    }

    @Test
    fun `silence stays listening`() = runTest {
        assertEquals(listOf(WatchTunerState.Listening), statesFor(FloatArray(48_000)))
    }

    @Test
    fun `a watch microphone at 16 kHz reads the same note`() = runTest {
        val rate = 16_000
        val states = statesFor(TestSignals.sine(220f, length = rate, sampleRate = rate), AudioConfig.forSampleRate(rate))

        assertEquals(WatchTunerState.Detected(Note.of(PitchClass.A, 3), 0), states.lastDetected())
    }

    @Test
    fun `an unsupported sample rate fails the flow`() = runTest {
        val pipeline = WatchPitchPipeline(FakeAudioRecorder(FloatArray(10), AudioConfig(sampleRate = 4_000)))
        try {
            pipeline.states().toList()
            fail("expected a failure")
        } catch (e: AudioRecorderException.InitializationFailed) {
            assertTrue(e.message.orEmpty().contains("4000"))
        }
    }
}
