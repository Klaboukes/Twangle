package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.audio.FakeTonePlayer
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import io.github.klaboukes.twangle.core.dsp.trail.PitchHistory
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import io.github.klaboukes.twangle.core.settings.LensLook
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

@OptIn(ExperimentalCoroutinesApi::class)
class PitchTrailFeedTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun detected(frequencyHz: Float, cents: Float, mode: TunerMode = TunerMode.CHROMATIC, a4Hz: Float = 440f) =
        TunerUiState.Detected(
            mode = mode, tuning = Tunings.STANDARD, a4Hz = a4Hz, noteName = "A", octave = 2,
            frequencyHz = frequencyHz, cents = cents, targetString = null, inTune = abs(cents) < 5f,
        )

    // ---- The note aimed at ----

    @Test
    fun `the target is the note the cents are measured from`() {
        // A2 (MIDI 45) 30 cents sharp, and 40 cents flat.
        assertEquals(45, PitchTrailFeed.targetMidiOf(detected(110f * 2f.pow(0.3f / 12f), 30f)))
        assertEquals(45, PitchTrailFeed.targetMidiOf(detected(110f * 2f.pow(-0.4f / 12f), -40f)))
        // A string tuned far off in guitar mode: 150 cents below the A string is still aimed at A2.
        assertEquals(45, PitchTrailFeed.targetMidiOf(detected(110f * 2f.pow(-1.5f / 12f), -150f, TunerMode.AUTO)))
        // Another concert pitch.
        assertEquals(69, PitchTrailFeed.targetMidiOf(detected(432f, 0f, a4Hz = 432f)))
    }

    @Test
    fun `notes become entries and anything else a gap, on a steady clock`() {
        val feed = PitchTrailFeed(PitchHistory())
        val frame = 0.02f
        feed.record(detected(110f, 0f), frame)
        feed.record(TunerUiState.Listening(TunerMode.CHROMATIC, Tunings.STANDARD), frame)
        feed.gap(frame)
        feed.record(detected(110f * 2f.pow(0.1f / 12f), 10f), frame)
        val history = feed.history
        assertEquals(4L, history.end)
        assertEquals(45, history.targetAt(0))
        assertTrue(history.inTuneAt(0))
        assertTrue(history.isGapAt(1))
        assertTrue(history.isGapAt(2))
        assertEquals(10f, history.centsAt(3), 0f)
        for (i in 0L until 4L) assertEquals((i + 1) * frame, history.timeAt(i), 1e-6f)
        // A new session starts afresh, and the clock goes on.
        feed.start()
        assertTrue(history.isEmpty)
        feed.gap(frame)
        assertEquals(5 * frame, history.timeAt(4), 1e-6f)
    }

    // ---- Fed by the tuner ----

    private fun TestScope.viewModel(recorder: FakeAudioRecorder, tools: ToolSwitches = ToolSwitches.ALL_ON) =
        TunerViewModel(recorder, FakeSettingsRepository(), FakeTonePlayer(), StandardTestDispatcher(testScheduler), tools = tools)

    @Test
    fun `the tuner fills the history with the played note`() = runTest(dispatcher) {
        val vm = viewModel(FakeAudioRecorder(TestSignals.sawtooth(110f, length = 48_000), loop = true))
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(1_000)

        val history = vm.pitchHistory
        assertTrue("about one entry per frame, got ${history.end}", history.end > 30)
        var notes = 0
        history.forEachWithin { _, target, cents, _ ->
            if (target != PitchHistory.NO_NOTE) {
                notes++
                assertEquals(45, target)
                assertTrue("off by $cents c", abs(cents) < 1f)
            }
        }
        assertTrue("only $notes notes", notes > 20)
        // Every entry is one frame after the one before.
        val end = history.end
        for (i in history.firstReadable(end) + 1 until end) {
            assertEquals(1024f / 48_000f, history.timeAt(i) - history.timeAt(i - 1), 1e-4f)
        }
    }

    @Test
    fun `silence leaves only gaps`() = runTest(dispatcher) {
        val vm = viewModel(FakeAudioRecorder.silence())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)

        val history = vm.pitchHistory
        assertTrue(history.end > 10)
        history.forEachWithin { _, target, _, _ -> assertEquals(PitchHistory.NO_NOTE, target) }
    }

    @Test
    fun `stays empty while its switch is off`() = runTest(dispatcher) {
        val vm = viewModel(FakeAudioRecorder(TestSignals.sawtooth(110f, length = 48_000), loop = true), ToolSwitches.ALL_OFF)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)

        assertTrue(vm.state.value is TunerUiState.Detected)
        assertEquals(0L, vm.pitchHistory.end)
    }

    // ---- The trail as the lens's look (2.0, Phase 71) ----

    @Test
    fun `the history fills in chromatic mode too, with no tool to turn on`() = runTest(dispatcher) {
        val vm = viewModel(FakeAudioRecorder(TestSignals.sawtooth(110f, length = 48_000), loop = true))
        vm.setMode(TunerMode.CHROMATIC)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)

        var notes = 0
        vm.pitchHistory.forEachWithin { _, target, _, _ -> if (target != PitchHistory.NO_NOTE) notes++ }
        assertTrue("only $notes notes", notes > 10)
    }

    @Test
    fun `a tool on draws no note in the trail`() = runTest(dispatcher) {
        val vm = viewModel(FakeAudioRecorder(TestSignals.sawtooth(110f, length = 48_000), loop = true))
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        vm.setChordName(true)
        advanceTimeBy(500)

        vm.pitchHistory.forEachWithin { _, target, _, _ -> assertEquals(PitchHistory.NO_NOTE, target) }
    }

    @Test
    fun `the lens look is the saved one`() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val vm = TunerViewModel(FakeAudioRecorder.silence(), settings, FakeTonePlayer(), StandardTestDispatcher(testScheduler))
        backgroundScope.launch { vm.lensLook.collect {} }
        advanceTimeBy(100)
        assertEquals(LensLook.TRAIL, vm.lensLook.value)
        settings.setLensLook(LensLook.STROBE)
        advanceTimeBy(100)
        assertEquals(LensLook.STROBE, vm.lensLook.value)
    }
}
