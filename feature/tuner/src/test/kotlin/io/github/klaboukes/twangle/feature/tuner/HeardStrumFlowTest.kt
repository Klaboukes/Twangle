package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.audio.FakeTonePlayer
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import io.github.klaboukes.twangle.core.dsp.strum.StrumKind
import io.github.klaboukes.twangle.core.dsp.strum.StrumVerdict
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

/**
 * The strum check beside the tuner (2.0, Phase 74): microphone chunks → [PitchPipeline] (the tuner
 * and [io.github.klaboukes.twangle.core.dsp.strum.StrumWatcher] side by side) → [TunerViewModel.heardStrum], the
 * tuner's own readings and states untouched.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HeardStrumFlowTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val standardHz = TunerOptions(tuning = Tunings.STANDARD).stringHz

    /** A2 20 cents flat and G3 15 cents sharp, the others in tune. */
    private val offCents = floatArrayOf(0f, -20f, 0f, 15f, 0f, 0f)
    private val openStrum = SyntheticStrum.signal(SyntheticStrum.detuned(standardHz, offCents), seconds = 3.0)

    /** C, x32010: C3 E3 G3 C4 E4. */
    private val cChord = SyntheticStrum.signal(midiHz(48, 52, 55, 60, 64), seconds = 3.0)

    /** The A string alone. */
    private val aString = SyntheticStrum.signal(floatArrayOf(standardHz[1]), seconds = 3.0)

    private fun midiHz(vararg midi: Int) = FloatArray(midi.size) { (440.0 * 2.0.pow((midi[it] - 69) / 12.0)).toFloat() }

    private fun hearing(open: FloatArray? = standardHz) = object : StrumHearing {
        override fun a4Hz() = 440f
        override fun openStrings() = open
        override fun range() = PitchRange.STANDARD
    }

    private suspend fun TestScope.listen(signal: FloatArray, hearing: StrumHearing?): List<PipelineReading> =
        PitchPipeline(FakeAudioRecorder(signal), StandardTestDispatcher(testScheduler)).listen(hearing = hearing).toList()

    // --- The pipeline ---

    @Test
    fun `the tuner's readings are the same with the analysis beside it`() = runTest {
        for (signal in listOf(openStrum, cChord, aString)) {
            val alone = listen(signal, hearing = null)
            val beside = listen(signal, hearing())
            assertEquals(alone, beside.filterIsInstance<PitchReading>())
            assertTrue(beside.any { it is HeardReading })
        }
    }

    @Test
    fun `the pipeline reads a strum of the open strings beside the tuner`() = runTest {
        val heard = listen(openStrum, hearing()).filterIsInstance<HeardReading>()
        assertEquals(HeardReading.FRESH, heard.first())
        assertEquals(StrumKind.OPEN_STRINGS, heard.first { it.kind != null }.kind)
        val read = heard.mapNotNull { it.strum?.strings }
        assertTrue("${read.size} readings", read.size >= 10)
        read.first().forEachIndexed { i, string ->
            assertTrue("string $i not heard", string.present)
            val room = if (string.shadowed) 3f else 0.5f
            assertTrue("string $i off by ${string.cents - offCents[i]}", abs(string.cents - offCents[i]) < room)
        }
    }

    @Test
    fun `the pipeline takes a fretted chord or one string for no strum of the open strings, and names no chord`() = runTest {
        for (signal in listOf(cChord, aString)) {
            val heard = listen(signal, hearing()).filterIsInstance<HeardReading>()
            assertEquals(listOf(StrumKind.NONE), heard.mapNotNull { it.kind }.distinct())
            assertTrue(heard.none { it.strum != null })
        }
    }

    @Test
    fun `with every sound a strum, the pipeline reads one string as a strum too`() = runTest {
        val everySound = object : StrumHearing {
            override fun a4Hz() = 440f
            override fun openStrings() = standardHz
            override fun everySoundIsAStrum() = true
            override fun range() = PitchRange.STANDARD
        }
        val strum = listen(openStrum, everySound).filterIsInstance<HeardReading>()
        assertTrue(strum.mapNotNull { it.strum?.strings }.size >= 10)
        val single = listen(aString, everySound).filterIsInstance<HeardReading>()
        assertEquals(listOf(StrumKind.OPEN_STRINGS), single.mapNotNull { it.kind }.distinct())
    }

    @Test
    fun `with nothing to listen for, the pipeline hands on the tuner's readings alone`() = runTest {
        val off = object : StrumHearing {
            override fun a4Hz() = Float.NaN
            override fun openStrings() = standardHz
            override fun range() = PitchRange.STANDARD
        }
        assertTrue(listen(openStrum, off).all { it is PitchReading })
    }

    // --- The ViewModel ---

    // The 12th-fret check's way in is on (its Settings switch, off on a fresh install), for the test that turns it on.
    private val settings = FakeSettingsRepository()
    private val tones = FakeTonePlayer()

    private fun TestScope.viewModel(signal: FloatArray, tools: ToolSwitches = ToolSwitches.ALL_ON) =
        TunerViewModel(FakeAudioRecorder(signal), settings, tones, StandardTestDispatcher(testScheduler), tools = tools)

    /** A tuner listening to [signal] with the strum check's button tapped on, as the player does before strumming. */
    private fun TestScope.checking(signal: FloatArray, tools: ToolSwitches = ToolSwitches.ALL_ON): TunerViewModel =
        viewModel(signal, tools).also {
            it.setStrumCheck(true)
            it.onPermissionChanged(true)
        }

    @Test
    fun `a strum of the open strings shows each string's verdict, the tuner waiting while it rings`() = runTest(dispatcher) {
        val vm = checking(openStrum)
        assertTrue(vm.strumCheck.value)
        val states = mutableListOf<TunerUiState>()
        // Each state with whether what the tuner heard rang as it came: the strum's notes are no string's reading.
        val whileRinging = mutableListOf<TunerUiState>()
        backgroundScope.launch {
            vm.state.collect {
                states += it
                if (vm.heardStrum.value.rings) whileRinging += it
            }
        }
        advanceTimeBy(1_500)
        val shown = vm.heardStrum.value
        assertTrue("$shown", shown is HeardStrum.OpenStrings)
        val check = (shown as HeardStrum.OpenStrings).check
        assertEquals(
            listOf(StrumVerdict.IN_TUNE, StrumVerdict.FLAT, StrumVerdict.IN_TUNE, StrumVerdict.SHARP, StrumVerdict.IN_TUNE, StrumVerdict.IN_TUNE),
            check.strings.map { it.verdict },
        )
        assertEquals(-20f, check.strings[1].cents)
        assertEquals(StrumPhase.RINGING, check.phase)
        // The button stays on while the strum rings.
        assertTrue(vm.strumCheck.value)
        advanceTimeBy(3_000)
        assertTrue(states.none { it is TunerUiState.StrumCheck })
        assertTrue(whileRinging.none { it is TunerUiState.Detected })

        // The same sound with the brake on: no button, no strum shown.
        val braked = checking(openStrum, ToolSwitches.ALL_ON.copy(strumCheck = false))
        assertFalse(braked.strumCheck.value)
        val brakedStates = mutableListOf<TunerUiState>()
        backgroundScope.launch { braked.state.collect { brakedStates += it } }
        advanceTimeBy(4_500)
        assertSame(HeardStrum.None, braked.heardStrum.value)
        // This strum gives the tuner no note of its own, so its states are the same either way.
        assertEquals(brakedStates, states)
    }

    @Test
    fun `without the button a strum shows nothing, nothing is decided by itself`() = runTest(dispatcher) {
        for (signal in listOf(openStrum, cChord, aString)) {
            val vm = viewModel(signal)
            vm.onPermissionChanged(true)
            backgroundScope.launch { vm.state.collect {} }
            advanceTimeBy(3_300)
            assertSame(HeardStrum.None, vm.heardStrum.value)
        }
    }

    @Test
    fun `the button turns itself off once the strum has rung out, and the A string tuned after it takes the tuner's verdict`() = runTest(dispatcher) {
        // The strum found the A string 20 cents flat; then it is plucked alone, in tune.
        val vm = checking(openStrum + aString)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(2_000)
        assertEquals(StrumVerdict.FLAT, (vm.heardStrum.value as HeardStrum.OpenStrings).check.strings[1].verdict)
        advanceTimeBy(3_500)
        assertFalse(vm.strumCheck.value)
        val after = (vm.heardStrum.value as HeardStrum.OpenStrings).check
        assertEquals(StrumVerdict.IN_TUNE, after.strings[1].verdict)
        assertEquals(StrumVerdict.SHARP, after.strings[3].verdict)
        assertEquals(StrumPhase.DONE, after.phase)

        // On again: the marks of the strum before go until the next strum.
        vm.setStrumCheck(true)
        advanceTimeBy(100)
        assertSame(HeardStrum.None, vm.heardStrum.value)
    }

    @Test
    fun `the panel's close turns the button off and clears the strum shown`() = runTest(dispatcher) {
        val vm = checking(openStrum + FloatArray(3 * SyntheticStrum.SAMPLE_RATE))
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(1_500)
        assertTrue(vm.heardStrum.value is HeardStrum.OpenStrings)
        vm.closeStrumCheck()
        assertFalse(vm.strumCheck.value)
        advanceTimeBy(100)
        assertSame(HeardStrum.None, vm.heardStrum.value)
    }

    @Test
    fun `one string after the button shows nothing and the button waits on, a tap turns it off`() = runTest(dispatcher) {
        // Quiet after it, so the microphone's sound doesn't end (that turns the button off too).
        val vm = checking(aString + FloatArray(3 * SyntheticStrum.SAMPLE_RATE))
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(3_300)
        assertSame(HeardStrum.None, vm.heardStrum.value)
        assertTrue(vm.strumCheck.value)
        vm.setStrumCheck(false)
        assertFalse(vm.strumCheck.value)
    }

    @Test
    fun `in any-note mode the button turns off`() = runTest(dispatcher) {
        val open = checking(openStrum)
        open.setMode(TunerMode.CHROMATIC)
        assertFalse(open.strumCheck.value)
        backgroundScope.launch { open.state.collect {} }
        advanceTimeBy(2_000)
        assertSame(HeardStrum.None, open.heardStrum.value)
    }

    @Test
    fun `a strum stays shown after it, the 12th-fret check clears it, and listening ends with nothing shown`() = runTest(dispatcher) {
        // The strum, then 3 s of quiet before the microphone's sound ends.
        val vm = checking(openStrum + FloatArray(3 * SyntheticStrum.SAMPLE_RATE))
        val collecting = backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(5_000)
        val done = vm.heardStrum.value as HeardStrum.OpenStrings
        assertEquals(StrumPhase.DONE, done.check.phase)
        assertFalse(vm.strumCheck.value)

        vm.setIntonationCheck(true)
        advanceTimeBy(100)
        assertSame(HeardStrum.None, vm.heardStrum.value)

        collecting.cancel()
        advanceTimeBy(6_000)
        assertSame(HeardStrum.None, vm.heardStrum.value)
    }

    @Test
    fun `a reference tone is never analysed`() = runTest(dispatcher) {
        val vm = checking(openStrum)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(50)
        vm.playString(1)
        advanceTimeBy(3_000)
        assertSame(HeardStrum.None, vm.heardStrum.value)
    }
}
