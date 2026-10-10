package com.intune.feature.tuner

import com.intune.core.audio.AudioRecorderException
import com.intune.core.audio.FakeAudioRecorder
import com.intune.core.dsp.music.Tunings
import com.intune.core.dsp.testsignals.TestSignals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
class TunerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** One second of A2 (110 Hz, the 5th string): a whole number of periods, so looping it has no click. */
    private fun a2Recorder() = FakeAudioRecorder(TestSignals.sawtooth(110f, length = 48_000), loop = true)

    private fun TestScope.viewModel(recorder: FakeAudioRecorder) =
        TunerViewModel(recorder, StandardTestDispatcher(testScheduler))

    private fun TunerViewModel.detected(): TunerUiState.Detected {
        val state = state.value
        assertTrue("expected Detected, was $state", state is TunerUiState.Detected)
        return state as TunerUiState.Detected
    }

    @Test
    fun `asks for permission and keeps the mic closed until it is granted`() = runTest(dispatcher) {
        val recorder = a2Recorder()
        val vm = viewModel(recorder)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)

        assertEquals(TunerUiState.PermissionRequired, vm.state.value)
        assertFalse(recorder.isRecording.value)
        assertEquals(0, recorder.startCount)
    }

    @Test
    fun `detects the played string in auto mode`() = runTest(dispatcher) {
        val vm = viewModel(a2Recorder())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)

        val state = vm.detected()
        assertEquals(TunerMode.AUTO, state.mode)
        assertEquals(Tunings.STANDARD, state.tuning)
        assertEquals(1, state.targetString)
        assertEquals("A", state.noteName)
        assertEquals(2, state.octave)
        assertTrue("off by ${state.cents} c", abs(state.cents) < 1f)
        assertEquals(110f, state.frequencyHz, 0.1f)
    }

    @Test
    fun `switching to chromatic keeps the mic open`() = runTest(dispatcher) {
        val recorder = a2Recorder()
        val vm = viewModel(recorder)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)

        vm.setMode(TunerMode.CHROMATIC)
        advanceTimeBy(100)

        val state = vm.detected()
        assertEquals(TunerMode.CHROMATIC, vm.mode.value)
        assertEquals(TunerMode.CHROMATIC, state.mode)
        assertNull(state.targetString)
        assertEquals("A", state.noteName)
        assertEquals(1, recorder.startCount)
    }

    @Test
    fun `silence is listening`() = runTest(dispatcher) {
        val vm = viewModel(FakeAudioRecorder.silence())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)

        assertEquals(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD), vm.state.value)
    }

    @Test
    fun `mic is opened only while the screen collects, plus 5 seconds`() = runTest(dispatcher) {
        val recorder = a2Recorder()
        val vm = viewModel(recorder)
        vm.onPermissionChanged(true)
        runCurrent()
        assertFalse("nothing collects yet", recorder.isRecording.value)

        val ui = launch { vm.state.collect {} }
        advanceTimeBy(100)
        assertTrue(recorder.isRecording.value)

        ui.cancel()
        advanceTimeBy(4_900)
        assertTrue("kept for quick returns, e.g. rotation", recorder.isRecording.value)
        advanceTimeBy(200)
        runCurrent()
        assertFalse("released after 5 s in the background", recorder.isRecording.value)
        assertEquals(1, recorder.startCount)
    }

    @Test
    fun `losing the permission closes the mic`() = runTest(dispatcher) {
        val recorder = a2Recorder()
        val vm = viewModel(recorder)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(100)
        assertTrue(recorder.isRecording.value)

        vm.onPermissionChanged(false)
        runCurrent()
        assertFalse(recorder.isRecording.value)
        assertEquals(TunerUiState.PermissionRequired, vm.state.value)
    }

    @Test
    fun `a busy mic shows an error and retry opens it again`() = runTest(dispatcher) {
        val recorder = FakeAudioRecorder(failure = AudioRecorderException.MicrophoneBusy())
        val vm = viewModel(recorder)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(100)
        assertEquals(TunerUiState.Error(TunerError.MIC_BUSY), vm.state.value)

        recorder.failure = null
        recorder.signal = TestSignals.sawtooth(110f, length = 48_000)
        recorder.loop = true
        vm.retry()
        advanceTimeBy(500)

        assertEquals(2, recorder.startCount)
        assertTrue(vm.state.value is TunerUiState.Detected)
    }
}
