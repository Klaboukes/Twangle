package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.audio.FakeTonePlayer
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

/**
 * The strum check and the chord name without a mode (2.0, Phase 74): microphone chunks → [PitchPipeline] (the tuner
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

    private fun hearing(open: FloatArray? = standardHz, chords: Boolean = true) = object : StrumHearing {
        override fun a4Hz() = 440f
        override fun openStrings() = open
        override fun namesChords() = chords
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
        assertTrue(heard.none { it.chord != null })
    }

    @Test
    fun `the pipeline names a fretted chord and takes one string for neither`() = runTest {
        val chord = listen(cChord, hearing()).filterIsInstance<HeardReading>()
        assertEquals(StrumKind.CHORD, chord.first { it.kind != null }.kind)
        val named = chord.mapNotNull { it.chord }.filter { it.reading }
        assertTrue("$named", named.count { it.chord === Chord.of(PitchClass.C, ChordQuality.MAJOR) } * 2 > named.size)

        val single = listen(aString, hearing()).filterIsInstance<HeardReading>()
        assertEquals(listOf(StrumKind.NONE), single.mapNotNull { it.kind }.distinct())
        assertTrue(single.none { it.strum != null || it.chord != null })
    }

    @Test
    fun `with nothing to listen for, the pipeline hands on the tuner's readings alone`() = runTest {
        val off = object : StrumHearing {
            override fun a4Hz() = Float.NaN
            override fun openStrings() = standardHz
            override fun namesChords() = true
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

    @Test
    fun `a strum of the open strings shows each string's verdict, the tuner waiting while it rings`() = runTest(dispatcher) {
        val vm = viewModel(openStrum)
        vm.onPermissionChanged(true)
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
        advanceTimeBy(3_000)
        assertTrue(states.none { it is TunerUiState.StrumCheck || it is TunerUiState.ChordName })
        assertTrue(whileRinging.none { it is TunerUiState.Detected })

        // The same sound with both brakes on: no strum shown.
        val braked = viewModel(openStrum, ToolSwitches.ALL_ON.copy(strumCheck = false, chordNaming = false))
        braked.onPermissionChanged(true)
        val brakedStates = mutableListOf<TunerUiState>()
        backgroundScope.launch { braked.state.collect { brakedStates += it } }
        advanceTimeBy(4_500)
        assertSame(HeardStrum.None, braked.heardStrum.value)
        // This strum gives the tuner no note of its own, so its states are the same either way.
        assertEquals(brakedStates, states)
    }

    @Test
    fun `the A string tuned on its own after the strum takes the tuner's verdict`() = runTest(dispatcher) {
        // The strum found the A string 20 cents flat; then it is plucked alone, in tune.
        val vm = viewModel(openStrum + aString)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(2_000)
        assertEquals(StrumVerdict.FLAT, (vm.heardStrum.value as HeardStrum.OpenStrings).check.strings[1].verdict)
        advanceTimeBy(3_500)
        val after = (vm.heardStrum.value as HeardStrum.OpenStrings).check
        assertEquals(StrumVerdict.IN_TUNE, after.strings[1].verdict)
        assertEquals(StrumVerdict.SHARP, after.strings[3].verdict)
        assertEquals(StrumPhase.DONE, after.phase)
    }

    @Test
    fun `a fretted chord shows its name`() = runTest(dispatcher) {
        val vm = viewModel(cChord)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(2_000)
        val shown = vm.heardStrum.value
        assertTrue("$shown", shown is HeardStrum.FrettedChord)
        assertSame(Chord.of(PitchClass.C, ChordQuality.MAJOR), (shown as HeardStrum.FrettedChord).name.chord?.chord)
    }

    @Test
    fun `one string shows nothing, and the chord name's brake stops the chord`() = runTest(dispatcher) {
        val vm = viewModel(aString)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(3_300)
        assertSame(HeardStrum.None, vm.heardStrum.value)

        val braked = viewModel(cChord, ToolSwitches.ALL_ON.copy(chordNaming = false))
        braked.onPermissionChanged(true)
        backgroundScope.launch { braked.state.collect {} }
        advanceTimeBy(3_300)
        assertSame(HeardStrum.None, braked.heardStrum.value)
    }

    @Test
    fun `in any-note mode a chord is named but open strings are not checked`() = runTest(dispatcher) {
        val chord = viewModel(cChord)
        chord.setMode(TunerMode.CHROMATIC)
        chord.onPermissionChanged(true)
        backgroundScope.launch { chord.state.collect {} }
        advanceTimeBy(2_000)
        assertTrue("${chord.heardStrum.value}", chord.heardStrum.value is HeardStrum.FrettedChord)

        val open = viewModel(openStrum)
        open.setMode(TunerMode.CHROMATIC)
        open.onPermissionChanged(true)
        backgroundScope.launch { open.state.collect {} }
        advanceTimeBy(2_000)
        assertSame(HeardStrum.None, open.heardStrum.value)
    }

    @Test
    fun `a strum stays shown after it, the 12th-fret check clears it, and listening ends with nothing shown`() = runTest(dispatcher) {
        // The strum, then 3 s of quiet before the microphone's sound ends.
        val vm = viewModel(openStrum + FloatArray(3 * SyntheticStrum.SAMPLE_RATE))
        vm.onPermissionChanged(true)
        val collecting = backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(5_000)
        val done = vm.heardStrum.value as HeardStrum.OpenStrings
        assertEquals(StrumPhase.DONE, done.check.phase)

        vm.setIntonationCheck(true)
        advanceTimeBy(100)
        assertSame(HeardStrum.None, vm.heardStrum.value)

        collecting.cancel()
        advanceTimeBy(6_000)
        assertSame(HeardStrum.None, vm.heardStrum.value)
    }

    @Test
    fun `a reference tone is never analysed`() = runTest(dispatcher) {
        val vm = viewModel(openStrum)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(50)
        vm.playString(1)
        advanceTimeBy(3_000)
        assertSame(HeardStrum.None, vm.heardStrum.value)
    }
}
