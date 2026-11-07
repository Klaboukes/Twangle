package io.github.klaboukes.twangle.feature.metronome

import io.github.klaboukes.twangle.core.audio.AudioConfig
import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.audio.FakeMetronomePlayer
import io.github.klaboukes.twangle.core.audio.FakeScreenReaderSpeech
import io.github.klaboukes.twangle.core.audio.ScreenReaderSpeech
import io.github.klaboukes.twangle.core.dsp.metronome.BeatAccent
import io.github.klaboukes.twangle.core.dsp.metronome.ClickSounds
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.dsp.metronome.OnsetTempoDetector
import io.github.klaboukes.twangle.core.dsp.metronome.TempoDetector
import io.github.klaboukes.twangle.core.dsp.metronome.TempoStatus
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.roundToInt

/**
 * The metronome's Listen while a screen reader's voice plays (Phase 81): the tempo detector hears silence in place of
 * the microphone's chunks while TalkBack speaks and for the tail after, so its syllables are never taken for beats.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScreenReaderSpeechListenTest {

    private val dispatcher = StandardTestDispatcher()
    private val player = FakeMetronomePlayer()
    private val rate = AudioConfig.DEFAULT_SAMPLE_RATE

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        recorder: FakeAudioRecorder,
        speech: ScreenReaderSpeech,
        detectorFor: (Int) -> TempoDetector = ::OnsetTempoDetector,
    ) = MetronomeViewModel(
        player,
        FakeSettingsRepository(),
        savedBpm = 80,
        savedMeter = Meter.FOUR_FOUR,
        recorder = recorder,
        dspDispatcher = dispatcher,
        nanoTime = { testScheduler.currentTime * 1_000_000L },
        detectorFor = detectorFor,
        screenReaderSpeech = speech,
    )

    /** Clicks at [bpm] for [seconds], as from a speaker nearby: what the voice is heard as too. */
    private fun clicks(bpm: Double, seconds: Double = 16.0): FloatArray {
        val out = FloatArray((seconds * rate).roundToInt())
        val click = ClickSounds(rate).waveform(BeatAccent.PLAIN)
        var beat = 0
        while (true) {
            val at = ((0.3 + beat * 60.0 / bpm) * rate).roundToInt()
            if (at >= out.size) break
            for (j in click.indices) if (at + j < out.size) out[at + j] += click[j]
            beat++
        }
        return out
    }

    @Test
    fun `while the voice speaks the detector hears silence, still in the tail, and the music after`() = runTest(dispatcher) {
        val detector = Ears(rate)
        val speech = FakeScreenReaderSpeech(speaking = true)
        val vm = viewModel(FakeAudioRecorder(clicks(120.0)), speech, detectorFor = { detector })
        vm.listen()
        advanceTimeBy(2_000)
        assertTrue(vm.listen.value.listening)
        assertTrue("it heard for 2 s", detector.samplesProcessed > rate)
        assertEquals("nothing of the voice while it speaks", 0L, detector.loudSamples)

        speech.isSpeaking = false
        val stoppedAt = detector.samplesProcessed
        advanceTimeBy((ScreenReaderSpeech.TAIL_SECONDS * 1000).toLong() - 50)
        assertEquals("nothing in the tail either", 0L, detector.loudSamples)
        assertTrue("time goes on meanwhile", detector.samplesProcessed > stoppedAt)

        advanceTimeBy(1_000)
        assertTrue("the music after the tail is heard", detector.loudSamples > 0L)
        assertEquals("watched while it listens", 1, speech.watchers)
        vm.stopListening()
        runCurrent()
        assertEquals("let go of with the microphone", 0, speech.watchers)
    }

    @Test
    fun `a voice that speaks all along is no beat`() = runTest(dispatcher) {
        val vm = viewModel(FakeAudioRecorder(clicks(120.0)), FakeScreenReaderSpeech(speaking = true))
        vm.listen()
        advanceTimeBy(MetronomeViewModel.LISTEN_MILLIS + 100)
        runCurrent()
        assertEquals(ListenPhase.NO_BEAT, vm.listen.value.phase)
        assertNull(vm.heard.value)
    }

    @Test
    fun `the tempo is found once the voice has stopped`() = runTest(dispatcher) {
        val speech = FakeScreenReaderSpeech(speaking = true)
        val vm = viewModel(FakeAudioRecorder(clicks(120.0)), speech)
        vm.listen()
        advanceTimeBy(2_000)
        assertEquals("no tempo while it speaks", 0, vm.listen.value.estimate)
        speech.isSpeaking = false
        advanceUntilIdle()
        assertEquals(ListenPhase.FOUND, vm.listen.value.phase)
        assertEquals(120.0, vm.listen.value.found.toDouble(), 1.0)
    }

    @Test
    fun `with no screen reader speaking Listen finds what it found before`() = runTest(dispatcher) {
        val quiet = viewModel(FakeAudioRecorder(clicks(97.0)), FakeScreenReaderSpeech())
        quiet.listen()
        advanceUntilIdle()
        val none = viewModel(FakeAudioRecorder(clicks(97.0)), ScreenReaderSpeech.NONE)
        none.listen()
        advanceUntilIdle()
        assertEquals(ListenPhase.FOUND, quiet.listen.value.phase)
        assertEquals(none.listen.value.untimed(), quiet.listen.value.untimed())
    }

    private fun ListenUi.untimed() = copy(startedAtNanos = 0, endedAtNanos = 0)

    /** A detector that only counts what it is fed: every sample, and the ones that aren't silence. */
    private class Ears(override val sampleRate: Int) : TempoDetector {
        var loudSamples = 0L
        override var samplesProcessed = 0L

        override fun process(samples: FloatArray, offset: Int, length: Int) {
            for (i in offset until offset + length) if (samples[i] != 0f) loudSamples++
            samplesProcessed += length
        }

        override fun reset() {
            loudSamples = 0L
            samplesProcessed = 0L
        }

        override val status = TempoStatus.LISTENING
        override val bpm = 0
        override val exactBpm = 0f
        override val halfBpm = 0
        override val doubleBpm = 0
        override val alternativeBpm = 0
        override val confidence = 0f
        override val steadySeconds = 0f
        override val lastBeatSample = -1L
        override val beatIntervalSamples = 0f
    }
}
