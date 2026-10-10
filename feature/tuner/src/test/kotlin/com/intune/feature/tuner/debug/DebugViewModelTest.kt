package com.intune.feature.tuner.debug

import com.intune.core.audio.AudioRecorderException
import com.intune.core.audio.FakeAudioRecorder
import com.intune.core.dsp.testsignals.TestSignals
import com.intune.feature.tuner.TunerError
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebugViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** One second of A2 (110 Hz): a whole number of periods, so looping it has no click. */
    private fun a2Recorder() = FakeAudioRecorder(TestSignals.sawtooth(110f, length = 48_000), loop = true)

    private fun TestScope.viewModel(recorder: FakeAudioRecorder) = DebugViewModel(recorder, StandardTestDispatcher(testScheduler))

    @Test
    fun `shows the live note while subscribed`() = runTest(dispatcher) {
        val vm = viewModel(a2Recorder())
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)

        val state = vm.state.value
        assertTrue("was $state", state is DebugUiState.Live)
        state as DebugUiState.Live
        assertEquals("A2", state.note)
        assertTrue(kotlin.math.abs(state.cents) < 1f)
        assertEquals(48_000, state.sampleRate)
    }

    @Test
    fun `mic is opened only while the screen collects, plus 5 seconds`() = runTest(dispatcher) {
        val recorder = a2Recorder()
        val vm = viewModel(recorder)
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
    fun `a busy mic shows an error and retry opens it again`() = runTest(dispatcher) {
        val recorder = FakeAudioRecorder(failure = AudioRecorderException.MicrophoneBusy())
        val vm = viewModel(recorder)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(100)
        assertEquals(DebugUiState.Failed(TunerError.MIC_BUSY), vm.state.value)

        recorder.failure = null
        recorder.signal = TestSignals.sawtooth(110f, length = 48_000)
        recorder.loop = true
        vm.retry()
        advanceTimeBy(500)

        assertEquals(2, recorder.startCount)
        assertTrue(vm.state.value is DebugUiState.Live)
    }
}
