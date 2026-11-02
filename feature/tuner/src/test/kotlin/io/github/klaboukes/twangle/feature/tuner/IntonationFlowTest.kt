package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.audio.FakeTonePlayer
import io.github.klaboukes.twangle.core.dsp.intonation.IntonationVerdict
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.synth.PluckSynth
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import io.github.klaboukes.twangle.core.settings.TunerMode
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.pow

/** The 12th-fret check end to end: synthetic plucks → [PitchPipeline] → [TunerViewModel] → [TunerUiState.IntonationCheck]. */
@OptIn(ExperimentalCoroutinesApi::class)
class IntonationFlowTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val settings = FakeSettingsRepository()
    private val tones = FakeTonePlayer()

    private fun TestScope.viewModel(recorder: FakeAudioRecorder, tools: ToolSwitches = ToolSwitches.ALL_ON) =
        TunerViewModel(recorder, settings, tones, StandardTestDispatcher(testScheduler), tools = tools)

    private fun TunerViewModel.check(): TunerUiState.IntonationCheck {
        val state = state.value
        assertTrue("expected IntonationCheck, was $state", state is TunerUiState.IntonationCheck)
        return state as TunerUiState.IntonationCheck
    }

    private fun cents(c: Float) = 2.0.pow(c / 1200.0).toFloat()

    /** A pluck [seconds] long, through the app's own pluck synthesis (as the phone check's). */
    private fun pluck(hz: Float, seconds: Float): FloatArray {
        val out = FloatArray((48_000 * seconds).toInt())
        PluckSynth(48_000, durationSeconds = seconds).apply { start(hz) }.render(out)
        return out
    }

    @Test
    fun `the open A string, then its 12th fret 8 cents sharp, says move the saddle back about 3 mm`() = runTest(dispatcher) {
        val recorder = FakeAudioRecorder(pluck(110f, 2.6f) + pluck(220f * cents(8f), 2.6f))
        val vm = viewModel(recorder)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        vm.setIntonationCheck(true)
        vm.toggleString(1)
        advanceTimeBy(300)
        assertEquals(IntonationStep.OPEN, vm.check().step)
        assertEquals(1, vm.check().string)

        advanceTimeBy(2_200)
        assertEquals(IntonationStep.FRET, vm.check().step)

        advanceTimeBy(2_400)
        val verdict = vm.check()
        assertEquals(IntonationStep.VERDICT, verdict.step)
        assertEquals(IntonationVerdict.MOVE_SADDLE_BACK, verdict.result?.verdict)
        assertEquals(8f, verdict.result!!.offsetCents, 1f)
        assertEquals(648f, verdict.scaleLengthMm)
        assertEquals(1, recorder.startCount)
    }

    @Test
    fun `next string, check again, and back to choosing after the last string`() = runTest(dispatcher) {
        val vm = viewModel(FakeAudioRecorder.silence())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        vm.setIntonationCheck(true)
        advanceTimeBy(100)
        assertEquals(IntonationStep.CHOOSE, vm.check().step)

        vm.checkIntonationOf(4)
        advanceTimeBy(100)
        assertEquals(4, vm.check().string)
        vm.checkNextString()
        advanceTimeBy(100)
        assertEquals(5, vm.check().string)
        vm.checkIntonationAgain()
        advanceTimeBy(100)
        assertEquals(IntonationStep.OPEN, vm.check().step)
        assertEquals(5, vm.check().string)
        vm.checkNextString()
        advanceTimeBy(100)
        assertEquals(IntonationStep.CHOOSE, vm.check().step)
        assertNull(vm.check().string)
    }

    @Test
    fun `one tool at a time, and chromatic mode turns the check off`() = runTest(dispatcher) {
        val vm = viewModel(FakeAudioRecorder.silence())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        vm.setStrumCheck(true)
        vm.setIntonationCheck(true)
        advanceTimeBy(100)
        assertFalse(vm.strumCheck.value)
        assertTrue(vm.intonationCheck.value)
        vm.setStrumCheck(true)
        advanceTimeBy(100)
        assertFalse(vm.intonationCheck.value)
        assertTrue(vm.state.value is TunerUiState.StrumCheck)

        vm.setIntonationCheck(true)
        vm.setMode(TunerMode.CHROMATIC)
        advanceTimeBy(100)
        assertFalse(vm.intonationCheck.value)
        assertTrue(vm.state.value is TunerUiState.Listening)

        vm.setIntonationCheck(true)
        advanceTimeBy(100)
        assertEquals(TunerMode.AUTO, vm.mode.value)
        assertEquals(IntonationStep.CHOOSE, vm.check().step)
    }

    @Test
    fun `a violin has no 12th-fret check`() = runTest(dispatcher) {
        val vm = viewModel(FakeAudioRecorder.silence())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        vm.setIntonationCheck(true)
        advanceTimeBy(100)
        assertTrue(vm.intonationCheck.value)
        vm.setInstrument(Instruments.VIOLIN)
        advanceTimeBy(100)
        assertEquals(listOf(TunerTool.STRUM_CHECK), vm.tunerTools.value)
        assertFalse(vm.intonationCheck.value)
        assertTrue(vm.state.value is TunerUiState.Listening)
        vm.setIntonationCheck(true)
        advanceTimeBy(100)
        assertFalse(vm.intonationCheck.value)
    }

    @Test
    fun `with its switch off the check is not offered`() = runTest(dispatcher) {
        val vm = viewModel(FakeAudioRecorder.silence(), tools = ToolSwitches.ALL_OFF)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        assertEquals(emptyList<TunerTool>(), vm.tunerTools.value)
        vm.setIntonationCheck(true)
        advanceTimeBy(100)
        assertFalse(vm.intonationCheck.value)
        assertTrue(vm.state.value is TunerUiState.Listening)
    }

    @Test
    fun `a new tuning goes back to choosing a string`() = runTest(dispatcher) {
        val vm = viewModel(FakeAudioRecorder.silence())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        vm.setIntonationCheck(true)
        vm.checkIntonationOf(2)
        advanceTimeBy(100)
        assertEquals(2, vm.check().string)
        vm.setTuning(Instruments.GUITAR.tunings[1])
        advanceTimeBy(100)
        assertEquals(IntonationStep.CHOOSE, vm.check().step)
        assertEquals(Instruments.GUITAR.tunings[1], vm.check().tuning)
    }
}
