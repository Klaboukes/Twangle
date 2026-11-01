package io.github.klaboukes.twangle.feature.metronome

import io.github.klaboukes.twangle.core.audio.AudioConfig
import io.github.klaboukes.twangle.core.audio.AudioRecorderException
import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.audio.FakeMetronomePlayer
import io.github.klaboukes.twangle.core.dsp.metronome.BeatAccent
import io.github.klaboukes.twangle.core.dsp.metronome.ClickSound
import io.github.klaboukes.twangle.core.dsp.metronome.ClickSounds
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.dsp.metronome.SpeedTrainer
import io.github.klaboukes.twangle.core.dsp.metronome.Subdivision
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Listen on the metronome (Phase 27) with a fake microphone playing synthetic music and the real
 * tempo detector, in virtual time: it pauses the clicks, holds the microphone only while listening,
 * ends by itself once the tempo is steady or after 15 s, suggests what it found (nothing changes until
 * Use), and brings the clicks back only if they were playing before.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MetronomeListenTest {

    private val dispatcher = StandardTestDispatcher()
    private val player = FakeMetronomePlayer()
    private val settings = FakeSettingsRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(recorder: FakeAudioRecorder, bpm: Int = 80, trainer: SpeedTrainer? = null) = MetronomeViewModel(
        player,
        settings,
        savedBpm = bpm,
        savedMeter = Meter.FOUR_FOUR,
        recorder = recorder,
        savedTrainer = trainer,
        savedTrainerOn = trainer != null,
        dspDispatcher = dispatcher,
        nanoTime = { testScheduler.currentTime * 1_000_000L },
    )

    /** Clicks at [bpm] for [seconds] over a little noise, as from a speaker nearby. */
    private fun clicks(bpm: Double, seconds: Double = 16.0): FloatArray {
        val rate = AudioConfig.DEFAULT_SAMPLE_RATE
        val out = FloatArray((seconds * rate).roundToInt())
        val random = Random(3)
        for (i in out.indices) out[i] = (random.nextGaussian() * 0.005).toFloat()
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
    fun `listening pauses the clicks, holds the microphone only while listening and suggests the steady tempo`() = runTest(dispatcher) {
        val recorder = FakeAudioRecorder(clicks(120.0))
        val vm = viewModel(recorder)
        vm.toggle()
        assertTrue(player.isPlaying.value)

        vm.listen()
        runCurrent()
        assertTrue(vm.listen.value.listening)
        assertFalse("the clicks pause: the microphone would hear them", player.isPlaying.value)
        assertTrue(recorder.isRecording.value)

        advanceTimeBy(6_000)
        assertTrue("still listening", vm.listen.value.listening)
        assertEquals("the tempo it is settling on shows", 120.0, vm.listen.value.estimate.toDouble(), 1.0)
        assertNotNull("the beat lights follow the heard beats", vm.heard.value)

        advanceTimeBy(9_500)
        runCurrent()
        val found = vm.listen.value
        assertEquals(ListenPhase.FOUND, found.phase)
        assertTrue("steady before the 15 s are up", found.endedAtNanos - found.startedAtNanos < 15_000_000_000L)
        assertEquals(120.0, found.found.toDouble(), 1.0)
        assertEquals(found.found / 2.0, found.half.toDouble(), 1.0)
        assertEquals(found.found * 2.0, found.double.toDouble(), 1.0)
        assertFalse("the microphone is released", recorder.isRecording.value)
        assertNull(vm.heard.value)
        assertEquals("nothing changes until Use", 80, vm.bpm.value)
        assertFalse("the clicks wait for the player", player.isPlaying.value)

        vm.use(TempoPick.AS_HEARD)
        advanceTimeBy(100)
        assertEquals(found.found, vm.bpm.value)
        assertFalse("not before the dial has glided", player.isPlaying.value)
        advanceUntilIdle()
        assertTrue("the clicks come back, they were playing", player.isPlaying.value)
        assertEquals(found.found, player.bpm)
        assertEquals(found.found, settings.current.metronomeBpm)
        assertEquals(ListenPhase.IDLE, vm.listen.value.phase)
        assertEquals("the microphone opened once", 1, recorder.startCount)
    }

    @Test
    fun `a stopped metronome stays stopped after Use, and half or double can be picked`() = runTest(dispatcher) {
        val recorder = FakeAudioRecorder(clicks(84.0))
        val vm = viewModel(recorder)
        vm.listen()
        advanceUntilIdle()
        val found = vm.listen.value
        assertEquals(ListenPhase.FOUND, found.phase)
        assertEquals(84.0, found.found.toDouble(), 1.0)
        vm.use(TempoPick.DOUBLE)
        advanceUntilIdle()
        assertEquals(found.double, vm.bpm.value)
        assertFalse(player.isPlaying.value)
        assertEquals(0, player.startCount)
    }

    @Test
    fun `Not now changes nothing and brings the clicks back at the old tempo`() = runTest(dispatcher) {
        val vm = viewModel(FakeAudioRecorder(clicks(132.0)))
        vm.toggle()
        vm.listen()
        advanceUntilIdle()
        assertEquals(ListenPhase.FOUND, vm.listen.value.phase)
        vm.notNow()
        assertEquals(80, vm.bpm.value)
        assertTrue(player.isPlaying.value)
        assertEquals(80, player.bpm)
        assertEquals(ListenPhase.IDLE, vm.listen.value.phase)
    }

    @Test
    fun `no beat after 15 s says so for 8 s, keeps the tempo and brings the clicks back`() = runTest(dispatcher) {
        val recorder = FakeAudioRecorder.silence()
        val vm = viewModel(recorder)
        vm.toggle()
        vm.listen()
        advanceTimeBy(14_900)
        assertTrue(vm.listen.value.listening)
        assertTrue(recorder.isRecording.value)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(ListenPhase.NO_BEAT, vm.listen.value.phase)
        assertFalse("the microphone is released", recorder.isRecording.value)
        assertEquals(80, vm.bpm.value)
        assertTrue("the clicks come back", player.isPlaying.value)
        advanceTimeBy(7_900)
        assertEquals("the line stays a while", ListenPhase.NO_BEAT, vm.listen.value.phase)
        advanceTimeBy(200)
        assertEquals(ListenPhase.IDLE, vm.listen.value.phase)
    }

    @Test
    fun `a microphone that fails says it is busy, not that no beat was heard, and Try again listens again`() = runTest(dispatcher) {
        val failures = listOf(
            AudioRecorderException.MicrophoneBusy(),
            AudioRecorderException.InitializationFailed("AudioRecord did not initialise"),
        )
        for (failure in failures) {
            val recorder = FakeAudioRecorder(FloatArray(4_800), failure = failure)
            val vm = viewModel(recorder)
            vm.listen()
            advanceTimeBy(1_000)
            assertEquals("$failure", ListenPhase.MIC_BUSY, vm.listen.value.phase)
            assertFalse(recorder.isRecording.value)
            vm.tryAgain()
            assertEquals(ListenPhase.LISTENING, vm.listen.value.phase)
            vm.stop()
        }
    }

    @Test
    fun `a microphone whose permission went missing says it is off`() = runTest(dispatcher) {
        val recorder = FakeAudioRecorder(FloatArray(4_800), failure = AudioRecorderException.PermissionMissing())
        val vm = viewModel(recorder)
        vm.listen()
        advanceTimeBy(1_000)
        assertEquals(ListenPhase.MIC_OFF, vm.listen.value.phase)
    }

    @Test
    fun `Stop ends listening at once, keeps the tempo and brings the clicks back`() = runTest(dispatcher) {
        val recorder = FakeAudioRecorder(clicks(120.0))
        val vm = viewModel(recorder)
        vm.toggle()
        vm.listen()
        advanceTimeBy(4_000)
        vm.listen()
        runCurrent()
        assertEquals(ListenPhase.IDLE, vm.listen.value.phase)
        assertFalse(recorder.isRecording.value)
        assertEquals(80, vm.bpm.value)
        assertTrue(player.isPlaying.value)
        assertTrue(vm.listen.value.endedAtNanos > vm.listen.value.startedAtNanos)
    }

    @Test
    fun `another control ends listening, as the player took over`() = runTest(dispatcher) {
        val recorder = FakeAudioRecorder(clicks(120.0))
        val vm = viewModel(recorder)
        vm.toggle()
        vm.listen()
        advanceTimeBy(2_000)
        vm.stepBpm(+1)
        runCurrent()
        assertFalse(vm.listen.value.listening)
        assertFalse(recorder.isRecording.value)
        assertEquals(81, vm.bpm.value)
        assertTrue(player.isPlaying.value)
        assertEquals(81, player.bpm)
    }

    @Test
    fun `picking clicks between the beats ends listening, and the clicks come back with them`() = runTest(dispatcher) {
        val recorder = FakeAudioRecorder(clicks(120.0))
        val vm = viewModel(recorder)
        vm.toggle()
        vm.listen()
        advanceTimeBy(2_000)
        vm.setSubdivision(Subdivision.TWO)
        runCurrent()
        assertFalse(vm.listen.value.listening)
        assertFalse(recorder.isRecording.value)
        assertTrue(player.isPlaying.value)
        assertEquals(Subdivision.TWO, player.subdivision)
        assertEquals("no preview: the clicks play", 0, player.previewCount)
    }

    @Test
    fun `Start and Stop takes no taps while listening, and closes a suggestion to do its own job`() = runTest(dispatcher) {
        val vm = viewModel(FakeAudioRecorder(clicks(120.0)))
        vm.toggle()
        vm.listen()
        advanceTimeBy(1_000)
        vm.toggle()
        assertTrue(vm.listen.value.listening)
        assertFalse(player.isPlaying.value)
        advanceUntilIdle()
        assertEquals(ListenPhase.FOUND, vm.listen.value.phase)
        vm.toggle()
        assertEquals(ListenPhase.IDLE, vm.listen.value.phase)
        assertTrue("from stopped, Start starts", player.isPlaying.value)
        assertEquals(80, vm.bpm.value)
    }

    @Test
    fun `leaving the screen releases the microphone and brings nothing back`() = runTest(dispatcher) {
        val recorder = FakeAudioRecorder(clicks(120.0))
        val vm = viewModel(recorder)
        vm.toggle()
        vm.listen()
        advanceTimeBy(3_000)
        vm.stop()
        advanceUntilIdle()
        assertFalse(recorder.isRecording.value)
        assertFalse(player.isPlaying.value)
        assertEquals(ListenPhase.IDLE, vm.listen.value.phase)
    }

    @Test
    fun `Listen ends a preview, and the clicks don't start after it`() = runTest(dispatcher) {
        val recorder = FakeAudioRecorder(clicks(120.0))
        val vm = viewModel(recorder)
        vm.previewSound(ClickSound.COWBELL)
        vm.listen()
        assertFalse("the microphone doesn't hear the preview", player.isPreviewing.value)
        assertFalse(vm.listen.value.wasPlaying)
        advanceUntilIdle()
        vm.notNow()
        assertFalse(player.isPlaying.value)
    }

    @Test
    fun `listening again from an open suggestion remembers that the clicks were playing`()= runTest(dispatcher) {
        val recorder = FakeAudioRecorder(clicks(120.0))
        val vm = viewModel(recorder)
        vm.toggle()
        vm.listen()
        advanceUntilIdle()
        assertEquals(ListenPhase.FOUND, vm.listen.value.phase)
        vm.listen()
        assertTrue(vm.listen.value.wasPlaying)
        advanceUntilIdle()
        vm.notNow()
        assertTrue(player.isPlaying.value)
        assertEquals(2, recorder.startCount)
    }

    @Test
    fun `a refused microphone says so for 8 s, and the refusal is saved`() = runTest(dispatcher) {
        val vm = viewModel(FakeAudioRecorder.silence())
        vm.showMicOff(needsSettings = true)
        vm.onMicRefused()
        advanceTimeBy(7_000)
        assertEquals(ListenPhase.MIC_OFF_SETTINGS, vm.listen.value.phase)
        assertTrue(settings.current.micRefused)
        advanceTimeBy(1_100)
        assertEquals(ListenPhase.IDLE, vm.listen.value.phase)
    }

    @Test
    fun `the tempo it settles on changes only a few times while listening`() = runTest(dispatcher) {
        val vm = viewModel(FakeAudioRecorder(clicks(97.0)))
        vm.listen()
        val shown = mutableListOf<Int>()
        repeat(60) {
            advanceTimeBy(250)
            val estimate = vm.listen.value.estimate
            if (vm.listen.value.listening && estimate > 0 && shown.lastOrNull() != estimate) shown += estimate
        }
        assertTrue("shown $shown", shown.size in 1..3)
        assertTrue("shown $shown", shown.all { abs(it - 97) <= 1 })
    }

    @Test
    fun `listening ends the speed trainer, and the clicks come back at the tempo it reached`() = runTest(dispatcher) {
        val recorder = FakeAudioRecorder(clicks(120.0))
        val vm = viewModel(recorder, trainer = SpeedTrainer(80, 100, 10, 1))
        runCurrent()
        vm.toggle()
        for (beat in 0L until 5L) player.beat(beat)
        runCurrent()
        assertEquals(90, vm.bpm.value)
        vm.listen()
        runCurrent()
        assertFalse(vm.trainerOn.value)
        vm.stopListening()
        runCurrent()
        assertTrue(player.isPlaying.value)
        assertEquals(null, player.trainer)
        assertEquals(90, player.bpm)
    }
}
