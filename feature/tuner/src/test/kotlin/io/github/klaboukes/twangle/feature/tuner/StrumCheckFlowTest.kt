package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.audio.FakeTonePlayer
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.strum.StrumVerdict
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import io.github.klaboukes.twangle.core.settings.TunerMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
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
import kotlin.math.abs

/** The strum check end to end: microphone chunks → [PitchPipeline] → [TunerViewModel] → [TunerUiState.StrumCheck]. */
@OptIn(ExperimentalCoroutinesApi::class)
class StrumCheckFlowTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val standardHz = TunerOptions(tuning = Tunings.STANDARD).stringHz

    /** A2 20 cents flat and G3 15 cents sharp, the others in tune. */
    private val offCents = floatArrayOf(0f, -20f, 0f, 15f, 0f, 0f)
    private val strum = SyntheticStrum.signal(SyntheticStrum.detuned(standardHz, offCents))

    @Test
    fun `the pipeline reads every string of a strum and hands no tuner readings meanwhile`() = runTest {
        val recorder = FakeAudioRecorder(strum)
        val readings = PitchPipeline(recorder, StandardTestDispatcher(testScheduler)).listen { standardHz }.toList()

        assertTrue(readings.all { it is StrumReading })
        val strum = readings.filterIsInstance<StrumReading>()
        assertEquals(StrumReading(0, ringing = false, readingsSinceStrum = 0), strum.first())
        assertEquals(StrumReading(1, ringing = true, readingsSinceStrum = 0), strum[1])
        val read = strum.filter { it.strings != null }
        // About 2.1 s of ringing from 0.4 s after the strum, one reading every 0.1 s.
        assertTrue("${read.size} readings", read.size in 10..25)
        for (reading in read.take(5)) {
            val strings = reading.strings!!
            assertEquals(6, strings.size)
            strings.forEachIndexed { i, string ->
                assertTrue("string $i not heard", string.present)
                val room = if (string.shadowed) 3f else 0.5f
                assertTrue("string $i off by ${string.cents - offCents[i]}", abs(string.cents - offCents[i]) < room)
            }
        }
    }

    @Test
    fun `the pipeline goes back to tuner readings when strum check stops`() = runTest {
        val recorder = FakeAudioRecorder(TestSignals.sawtooth(110f, length = 48_000))
        var chunks = 0
        val readings = PitchPipeline(recorder, StandardTestDispatcher(testScheduler))
            .listen(strumStrings = { if (chunks++ < 20) standardHz else null })
            .toList()

        val firstTuner = readings.indexOfFirst { it is PitchReading }
        assertTrue(firstTuner > 0)
        assertTrue(readings.take(firstTuner).all { it is StrumReading })
        assertTrue(readings.drop(firstTuner).all { it is PitchReading })
        // A steady note isn't a strum.
        assertTrue(readings.filterIsInstance<StrumReading>().all { it.strumCount == 0 })
        // The tuner's window starts afresh: its first reading needs a whole window of new chunks.
        // 47 chunks (the last one padded), 20 of them for the strum check, 3 more to fill the window.
        assertEquals(47 - 20 - 3, readings.size - firstTuner)
    }

    private val settings = FakeSettingsRepository()
    private val tones = FakeTonePlayer()

    private fun TestScope.viewModel(recorder: FakeAudioRecorder, tools: ToolSwitches = ToolSwitches.ALL_ON) =
        TunerViewModel(recorder, settings, tones, StandardTestDispatcher(testScheduler), tools = tools)

    @Test
    fun `with its switch off the strum check is not offered`() = runTest(dispatcher) {
        val vm = viewModel(FakeAudioRecorder(strum, loop = true), ToolSwitches.ALL_ON.copy(strumCheck = false))
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        assertFalse(TunerTool.STRUM_CHECK in vm.tunerTools.value)
        vm.setStrumCheck(true)
        advanceTimeBy(500)
        assertFalse(vm.strumCheck.value)
        assertFalse(vm.state.value is TunerUiState.StrumCheck)
    }

    private fun TunerViewModel.strumCheckState(): TunerUiState.StrumCheck {
        val state = state.value
        assertTrue("expected StrumCheck, was $state", state is TunerUiState.StrumCheck)
        return state as TunerUiState.StrumCheck
    }

    @Test
    fun `strum check shows every string flat, in tune or sharp, on the open mic`() = runTest(dispatcher) {
        // The strum, then silence until it loops.
        val recorder = FakeAudioRecorder(strum + FloatArray(48_000 * 3), loop = true)
        val vm = viewModel(recorder)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        vm.setStrumCheck(true)
        advanceTimeBy(250)
        assertEquals(StrumPhase.WAITING, vm.strumCheckState().phase)
        assertTrue(vm.strumCheck.value)

        advanceTimeBy(400)
        assertEquals(StrumPhase.HEARING, vm.strumCheckState().phase)

        advanceTimeBy(500)
        val ringing = vm.strumCheckState()
        assertEquals(StrumPhase.RINGING, ringing.phase)
        assertEquals(
            listOf(StrumVerdict.IN_TUNE, StrumVerdict.FLAT, StrumVerdict.IN_TUNE, StrumVerdict.SHARP, StrumVerdict.IN_TUNE, StrumVerdict.IN_TUNE),
            ringing.strings.map { it.verdict },
        )
        assertEquals(-20f, ringing.strings[1].cents)
        assertEquals(15f, ringing.strings[3].cents)
        // B3 and E4 lie on E2's harmonics in standard tuning.
        assertEquals(listOf(true, true, true, true, false, false), ringing.strings.map { it.sure })

        advanceTimeBy(2_500)
        val done = vm.strumCheckState()
        assertEquals(StrumPhase.DONE, done.phase)
        assertEquals(ringing.strings.map { it.verdict }, done.strings.map { it.verdict })
        assertEquals(1, recorder.startCount)
    }

    @Test
    fun `switching strum check on and off keeps the mic open and goes back to the tuner`() = runTest(dispatcher) {
        val recorder = FakeAudioRecorder(TestSignals.sawtooth(110f, length = 48_000), loop = true)
        val vm = viewModel(recorder)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)
        assertTrue(vm.state.value is TunerUiState.Detected)

        vm.setStrumCheck(true)
        advanceTimeBy(500)
        // A steady note is no strum.
        assertEquals(StrumPhase.WAITING, vm.strumCheckState().phase)

        vm.setStrumCheck(false)
        advanceTimeBy(500)
        val back = vm.state.value
        assertTrue("was $back", back is TunerUiState.Detected && back.targetString == 1)
        assertEquals(1, recorder.startCount)
    }

    @Test
    fun `chromatic mode turns strum check off, and strum check goes back to the strings`() = runTest(dispatcher) {
        val vm = viewModel(FakeAudioRecorder.silence())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        vm.setStrumCheck(true)
        advanceTimeBy(100)
        vm.setMode(TunerMode.CHROMATIC)
        advanceTimeBy(100)
        assertFalse(vm.strumCheck.value)
        assertTrue(vm.state.value is TunerUiState.Listening)

        vm.setStrumCheck(true)
        advanceTimeBy(100)
        assertEquals(TunerMode.AUTO, vm.mode.value)
        assertEquals(StrumPhase.WAITING, vm.strumCheckState().phase)
    }

    @Test
    fun `tapping a string in strum check tunes that string`() = runTest(dispatcher) {
        val vm = viewModel(FakeAudioRecorder.silence())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        vm.setStrumCheck(true)
        advanceTimeBy(100)

        vm.toggleString(3)
        advanceTimeBy(100)
        assertFalse(vm.strumCheck.value)
        val state = vm.state.value
        assertTrue("was $state", state is TunerUiState.Listening && state.lockedString == 3)
    }

    @Test
    fun `a reference tone starts the strum check over instead of being read as a strum`() = runTest(dispatcher) {
        val recorder = FakeAudioRecorder(strum + FloatArray(48_000 * 3), loop = true)
        val vm = viewModel(recorder)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        vm.setStrumCheck(true)
        advanceTimeBy(1_200)
        assertEquals(StrumPhase.RINGING, vm.strumCheckState().phase)

        vm.playString(0)
        advanceTimeBy(100)
        assertEquals(StrumPhase.WAITING, vm.strumCheckState().phase)
        tones.finish()
        advanceTimeBy(1_000)
        // The strum still rings, but it began before the tone: no new strum is heard from the middle of it.
        val after = vm.strumCheckState()
        assertEquals(StrumPhase.WAITING, after.phase)
        assertNull(after.strings.firstOrNull())
        assertEquals(1, recorder.startCount)
    }
}
