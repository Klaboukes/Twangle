package io.github.klaboukes.twangle.feature.tuner.trail

import io.github.klaboukes.twangle.core.audio.AudioRecorderException
import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.audio.FakeScreenReaderSpeech
import io.github.klaboukes.twangle.core.audio.ScreenReaderSpeech
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import io.github.klaboukes.twangle.core.dsp.trail.PitchHistory
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.feature.tuner.TunerError
import io.github.klaboukes.twangle.feature.tuner.TunerUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/** The pitch trail's ViewModel (Phase 78): the tuner's ears in Any note, every reading into the trail's history. */
@OptIn(ExperimentalCoroutinesApi::class)
class PitchTrailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(recorder: FakeAudioRecorder, settings: FakeSettingsRepository = FakeSettingsRepository()) =
        PitchTrailViewModel(recorder, settings, StandardTestDispatcher(testScheduler))

    private fun note() = FakeAudioRecorder(TestSignals.sawtooth(110f, length = 48_000), loop = true)

    @Test
    fun `a held note fills the history, one entry per frame, on the note`() = runTest(dispatcher) {
        val vm = viewModel(note())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(1_000)

        val history = vm.history
        assertTrue("about one entry per frame, got ${history.end}", history.end > 30)
        var notes = 0
        history.forEachWithin { _, target, cents, _ ->
            if (target != PitchHistory.NO_NOTE) {
                notes++
                assertEquals("A2", 45, target)
                assertTrue("off by $cents c", abs(cents) < 1f)
            }
        }
        assertTrue("only $notes notes", notes > 20)
        val end = history.end
        for (i in history.firstReadable(end) + 1 until end) {
            assertEquals(1024f / 48_000f, history.timeAt(i) - history.timeAt(i - 1), 1e-4f)
        }
    }

    @Test
    fun `a mic that went busy while the trail was away listens again by itself when it comes back`() = runTest(dispatcher) {
        // Android silences the recording in the background (busy) while the microphone is kept for the 5 s grace.
        val recorder = FakeAudioRecorder(failure = AudioRecorderException.MicrophoneBusy())
        val vm = viewModel(recorder)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        vm.setShown(false)
        advanceTimeBy(100)
        assertEquals(TunerUiState.Error(TunerError.MIC_BUSY), vm.state.value)

        recorder.failure = null
        recorder.signal = TestSignals.sawtooth(110f, length = 48_000)
        recorder.loop = true
        advanceTimeBy(2_000)
        assertEquals("no try while away", 1, recorder.startCount)

        vm.setShown(true)
        advanceTimeBy(500)
        assertEquals(2, recorder.startCount)
        assertTrue(vm.state.value is TunerUiState.Detected)
    }

    @Test
    fun `it listens for any note, whatever mode the tuner was left in`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        settings.setMode(TunerMode.AUTO)
        val vm = viewModel(note(), settings)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)

        val shown = vm.state.value
        assertTrue("a note: $shown", shown is TunerUiState.Detected)
        assertEquals(TunerMode.CHROMATIC, (shown as TunerUiState.Detected).mode)
        assertEquals("the tuner's saved mode is left as it was", TunerMode.AUTO, settings.current.mode)
    }

    @Test
    fun `a screen reader's voice leaves a gap, and the note comes back after its tail`() = runTest(dispatcher) {
        val speech = FakeScreenReaderSpeech(speaking = true)
        val vm = PitchTrailViewModel(note(), FakeSettingsRepository(), StandardTestDispatcher(testScheduler), screenReaderSpeech = speech)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(1_000)
        assertTrue("listening while TalkBack speaks: ${vm.state.value}", vm.state.value is TunerUiState.Listening)
        assertTrue(vm.history.end > 30)
        vm.history.forEachWithin { _, target, _, _ -> assertEquals("TalkBack's speech is no note", PitchHistory.NO_NOTE, target) }

        speech.isSpeaking = false
        advanceTimeBy((ScreenReaderSpeech.TAIL_SECONDS * 1000).toLong() + 500)
        assertTrue("a note after the tail: ${vm.state.value}", vm.state.value is TunerUiState.Detected)
    }

    @Test
    fun `silence leaves only gaps`() = runTest(dispatcher) {
        val vm = viewModel(FakeAudioRecorder.silence())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)

        assertTrue(vm.history.end > 10)
        vm.history.forEachWithin { _, target, _, _ -> assertEquals(PitchHistory.NO_NOTE, target) }
    }

    @Test
    fun `no microphone without the permission, nor while nothing is shown`() = runTest(dispatcher) {
        val recorder = note()
        val vm = viewModel(recorder)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)
        assertEquals(TunerUiState.PermissionRequired, vm.state.value)
        assertEquals(0L, vm.history.end)

        // Allowed but not shown: still closed.
        val unseen = viewModel(note())
        unseen.onPermissionChanged(true)
        advanceTimeBy(500)
        assertEquals(0L, unseen.history.end)
    }
}
