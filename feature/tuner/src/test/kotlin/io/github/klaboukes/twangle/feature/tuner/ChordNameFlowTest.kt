package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.audio.FakeTonePlayer
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.chord.SettledChord
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
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
import kotlin.math.pow

/** The chord name end to end: synthetic strums → [PitchPipeline] → [TunerViewModel] → [TunerUiState.ChordName]. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChordNameFlowTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val settings = FakeSettingsRepository()
    private val tones = FakeTonePlayer()

    private val am = Chord.of(PitchClass.A, ChordQuality.MINOR)
    private val c = Chord.of(PitchClass.C, ChordQuality.MAJOR)
    private val g = Chord.of(PitchClass.G, ChordQuality.MAJOR)

    private fun TestScope.viewModel(recorder: FakeAudioRecorder, tools: ToolSwitches = ToolSwitches.ALL_ON) =
        TunerViewModel(recorder, settings, tones, StandardTestDispatcher(testScheduler), tools = tools)

    private fun TunerViewModel.chord(): TunerUiState.ChordName {
        val state = state.value
        assertTrue("expected ChordName, was $state", state is TunerUiState.ChordName)
        return state as TunerUiState.ChordName
    }

    @Test
    fun `the pipeline names each reading of an A minor strum and passes time between`() = runTest {
        val recorder = FakeAudioRecorder(strum(AM))
        val readings = PitchPipeline(recorder, StandardTestDispatcher(testScheduler))
            .listen(chordA4Hz = { 440f }) { null }
            .toList()
        assertTrue(readings.all { it is ChordReading })
        val chord = readings.filterIsInstance<ChordReading>()
        assertEquals(ChordReading(0.0, 0, ringing = false), chord.first())
        val read = chord.filter { it.reading }
        assertTrue("${read.size} readings", read.size >= 10)
        assertTrue("every reading A minor or nothing: ${read.map { it.chord }}", read.all { it.chord == null || it.chord == am })
        assertTrue(read.count { it.chord == am } >= read.size / 2)
        // Time passes while nothing rings: at least one status every 0.1 s.
        val gaps = chord.zipWithNext { a, b -> b.seconds - a.seconds }
        assertTrue("longest gap ${gaps.max()} s", gaps.max() <= 0.15)
    }

    @Test
    fun `strum A minor and A minor shows, fading as it dies away`() = runTest(dispatcher) {
        val recorder = FakeAudioRecorder(silence(0.5) + strum(AM, seconds = 2.5) + silence(2.0))
        val vm = viewModel(recorder)
        vm.onPermissionChanged(true)
        val seen = mutableListOf<TunerUiState>()
        backgroundScope.launch { vm.state.collect { seen += it } }
        vm.setChordName(true)
        advanceTimeBy(300)
        assertEquals(ChordPhase.WAITING, vm.chord().phase)

        advanceTimeBy(3_500)
        val named = seen.filterIsInstance<TunerUiState.ChordName>()
        assertTrue("listening before the name", named.any { it.phase == ChordPhase.LISTENING })
        val first = named.first { it.chord != null }
        assertEquals(SettledChord(am, PitchClass.A), first.chord)
        assertFalse(first.faded)
        // Never another name, never "not a clear chord".
        assertTrue(named.all { it.chord == null || it.chord == first.chord })
        assertTrue(named.none { it.phase == ChordPhase.UNCLEAR })

        advanceTimeBy(2_000)
        val last = vm.chord()
        assertEquals(SettledChord(am, PitchClass.A), last.chord)
        assertEquals(ChordPhase.NAMED, last.phase)
        assertTrue(last.faded)
        assertEquals(listOf(SettledChord(am, PitchClass.A)), last.recent)
        assertEquals(1, recorder.startCount)
    }

    @Test
    fun `G, C and a slash chord C over E one after the other, the last three kept`() = runTest(dispatcher) {
        val recorder = FakeAudioRecorder(silence(0.3) + strum(G) + strum(C) + strum(C_OVER_E) + silence(1.0))
        val vm = viewModel(recorder)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        vm.setChordName(true)
        advanceTimeBy(10_000)
        val state = vm.chord()
        assertEquals(SettledChord(c, PitchClass.E), state.chord)
        assertTrue(state.chord!!.isSlash)
        assertEquals(listOf(SettledChord(g, PitchClass.G), SettledChord(c, PitchClass.C), SettledChord(c, PitchClass.E)), state.recent)
    }

    @Test
    fun `a single note is not a clear chord, never a guess`() = runTest(dispatcher) {
        val recorder = FakeAudioRecorder(silence(0.3) + strum(intArrayOf(45), seconds = 3.0) + silence(1.0))
        val vm = viewModel(recorder)
        vm.onPermissionChanged(true)
        val seen = mutableListOf<TunerUiState>()
        backgroundScope.launch { vm.state.collect { seen += it } }
        vm.setChordName(true)
        advanceTimeBy(4_000)
        assertEquals(ChordPhase.UNCLEAR, vm.chord().phase)
        assertNull(vm.chord().chord)
        assertTrue(seen.filterIsInstance<TunerUiState.ChordName>().all { it.chord == null })
    }

    @Test
    fun `one tool at a time, chromatic turns it off, and a tapped string tunes that string`() = runTest(dispatcher) {
        val vm = viewModel(FakeAudioRecorder.silence())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        vm.setStrumCheck(true)
        vm.setChordName(true)
        advanceTimeBy(100)
        assertFalse(vm.strumCheck.value)
        assertTrue(vm.chordName.value)
        assertTrue(vm.state.value is TunerUiState.ChordName)
        vm.setIntonationCheck(true)
        advanceTimeBy(100)
        assertFalse(vm.chordName.value)

        vm.setChordName(true)
        vm.setMode(TunerMode.CHROMATIC)
        advanceTimeBy(100)
        assertFalse(vm.chordName.value)
        assertTrue(vm.state.value is TunerUiState.Listening)

        vm.setChordName(true)
        advanceTimeBy(100)
        assertEquals(TunerMode.AUTO, vm.mode.value)
        vm.toggleString(2)
        advanceTimeBy(100)
        assertFalse(vm.chordName.value)
        assertEquals(2, (vm.state.value as TunerUiState.Listening).lockedString)
    }

    @Test
    fun `basses and violins have no chord name, nor does a build with its switch off`() = runTest(dispatcher) {
        val vm = viewModel(FakeAudioRecorder.silence())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        vm.setChordName(true)
        advanceTimeBy(100)
        assertTrue(vm.chordName.value)
        vm.setInstrument(Instruments.BASS_4)
        advanceTimeBy(100)
        assertFalse(TunerTool.CHORD_NAME in vm.tunerTools.value)
        assertFalse(vm.chordName.value)
        vm.setChordName(true)
        advanceTimeBy(100)
        assertFalse(vm.chordName.value)

        val off = viewModel(FakeAudioRecorder.silence(), tools = ToolSwitches.ALL_OFF)
        off.onPermissionChanged(true)
        backgroundScope.launch { off.state.collect {} }
        off.setChordName(true)
        advanceTimeBy(100)
        assertFalse(off.chordName.value)
        assertTrue(off.state.value is TunerUiState.Listening)
    }

    @Test
    fun `the 12-string has neither the strum check nor the chord name, and either turns off on picking it`() = runTest(dispatcher) {
        val vm = viewModel(FakeAudioRecorder.silence())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        vm.setStrumCheck(true)
        advanceTimeBy(100)
        assertTrue(vm.strumCheck.value)
        vm.setInstrument(Instruments.GUITAR_12)
        advanceTimeBy(100)
        assertEquals(listOf(TunerTool.INTONATION_CHECK), vm.tunerTools.value)
        assertFalse(vm.strumCheck.value)
        assertTrue(vm.state.value is TunerUiState.Listening)
        vm.setStrumCheck(true)
        vm.setChordName(true)
        advanceTimeBy(100)
        assertFalse(vm.strumCheck.value)
        assertFalse(vm.chordName.value)
        assertTrue(vm.state.value is TunerUiState.Listening)
    }

    private companion object {
        /** Open A minor (x02210), C (x32010), G (320003) and C over E (032010), as MIDI notes low to high. */
        val AM = intArrayOf(45, 52, 57, 60, 64)
        val C = intArrayOf(48, 52, 55, 60, 64)
        val G = intArrayOf(43, 47, 50, 55, 62, 67)
        val C_OVER_E = intArrayOf(40, 48, 52, 55, 60, 64)

        fun hz(midi: IntArray) = FloatArray(midi.size) { (440.0 * 2.0.pow((midi[it] - 69) / 12.0)).toFloat() }

        /** A downstroke over [midi], [seconds] long from the strum, no silence before it. */
        fun strum(midi: IntArray, seconds: Double = 2.5) = SyntheticStrum.signal(hz(midi), silenceSeconds = 0.0, seconds = seconds)

        fun silence(seconds: Double) = FloatArray((seconds * SyntheticStrum.SAMPLE_RATE).toInt())
    }
}
