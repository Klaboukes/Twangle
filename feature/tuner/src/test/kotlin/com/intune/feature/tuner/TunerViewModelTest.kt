package com.intune.feature.tuner

import com.intune.core.audio.AudioRecorderException
import com.intune.core.audio.FakeAudioRecorder
import com.intune.core.dsp.music.Notation
import com.intune.core.dsp.music.Tunings
import com.intune.core.dsp.testsignals.TestSignals
import com.intune.core.settings.AppSettings
import com.intune.core.settings.FakeSettingsRepository
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

    private val settings = FakeSettingsRepository()

    private fun TestScope.viewModel(recorder: FakeAudioRecorder) =
        TunerViewModel(recorder, settings, StandardTestDispatcher(testScheduler))

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

    @Test
    fun `uses the saved tuning and A4`() = runTest(dispatcher) {
        settings.current = AppSettings(a4Hz = 442f, tuning = Tunings.DROP_D)
        val vm = viewModel(FakeAudioRecorder(TestSignals.sawtooth(110.5f, length = 48_000), loop = true))
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)

        val state = vm.detected()
        assertEquals(Tunings.DROP_D, state.tuning)
        assertEquals(442f, state.a4Hz)
        // A2 at A4 = 442 Hz is 110.5 Hz.
        assertEquals("A", state.noteName)
        assertTrue("off by ${state.cents} c", abs(state.cents) < 1f)
    }

    @Test
    fun `a new A4 applies while listening, without reopening the mic`() = runTest(dispatcher) {
        val recorder = a2Recorder()
        val vm = viewModel(recorder)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)
        assertTrue(abs(vm.detected().cents) < 1f)

        settings.setA4(442f)
        advanceTimeBy(500)

        val state = vm.detected()
        assertEquals(442f, state.a4Hz)
        // 110 Hz is 7.85 cents flat of A2 once A4 is 442 Hz.
        assertEquals(-7.85f, state.cents, 1f)
        assertEquals(1, recorder.startCount)
    }

    @Test
    fun `choosing a tuning saves it and retargets the strings`() = runTest(dispatcher) {
        // D2 (73.42 Hz): the 6th string in Drop D, but 2 semitones flat of E2 in standard.
        val vm = viewModel(FakeAudioRecorder(TestSignals.sawtooth(73.416f, length = 48_000), loop = true))
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)
        assertEquals("E", vm.detected().noteName)

        vm.setTuning(Tunings.DROP_D)
        advanceTimeBy(500)

        assertEquals(Tunings.DROP_D, settings.current.tuning)
        val state = vm.detected()
        assertEquals(Tunings.DROP_D, state.tuning)
        assertEquals(0, state.targetString)
        assertEquals("D", state.noteName)
        assertTrue("off by ${state.cents} c", abs(state.cents) < 1f)
    }

    @Test
    fun `tapping a string locks it and tapping it again unlocks`() = runTest(dispatcher) {
        // A2 is played, but the 4th string (D3) is locked: the needle shows the distance to D3.
        val vm = viewModel(a2Recorder())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }

        vm.toggleString(2)
        advanceTimeBy(500)
        val locked = vm.detected()
        assertEquals(2, locked.targetString)
        assertEquals(2, locked.lockedString)
        assertEquals(-500f, locked.cents, 1f)

        vm.toggleString(2)
        advanceTimeBy(500)
        val auto = vm.detected()
        assertEquals(1, auto.targetString)
        assertNull(auto.lockedString)
    }

    @Test
    fun `changing the tuning releases the lock`() = runTest(dispatcher) {
        val vm = viewModel(a2Recorder())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        vm.toggleString(4)
        advanceTimeBy(500)
        assertEquals(4, vm.detected().lockedString)

        vm.setTuning(Tunings.DROP_D)
        advanceTimeBy(500)

        assertNull(vm.detected().lockedString)
        assertEquals(1, vm.detected().targetString)
    }

    @Test
    fun `a new notation applies while listening`() = runTest(dispatcher) {
        val vm = viewModel(a2Recorder())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)
        assertEquals("A", vm.detected().noteName)

        settings.setNotation(Notation.SOLFEGE)
        advanceTimeBy(100)

        assertEquals("La", vm.detected().noteName)
        assertEquals(Notation.SOLFEGE, vm.detected().notation)
    }

    @Test
    fun `keepScreenOn follows the setting`() = runTest(dispatcher) {
        val vm = viewModel(a2Recorder())
        backgroundScope.launch { vm.keepScreenOn.collect {} }
        runCurrent()
        assertTrue("on by default", vm.keepScreenOn.value)

        settings.setKeepScreenOn(false)
        runCurrent()
        assertFalse(vm.keepScreenOn.value)
    }
}
