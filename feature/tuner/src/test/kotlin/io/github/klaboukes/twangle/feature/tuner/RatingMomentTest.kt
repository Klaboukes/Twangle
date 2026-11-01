package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.audio.FakeTonePlayer
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import io.github.klaboukes.twangle.core.settings.TunerMode
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

/** Google's rating window (Phase 50): when [TunerViewModel.ratingDue] may turn true, and when never. */
@OptIn(ExperimentalCoroutinesApi::class)
class RatingMomentTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // --- The rule -------------------------------------------------------------------------------

    @Test
    fun `may ask only past the first day and only once`() {
        val day = RatingMoment.FIRST_DAY_MILLIS
        assertTrue(RatingMoment.mayAsk(firstUseMillis = 1_000L, asked = false, nowMillis = 1_000L + day))
        assertFalse("the first day", RatingMoment.mayAsk(1_000L, asked = false, nowMillis = 1_000L + day - 1))
        assertFalse("asked before", RatingMoment.mayAsk(1_000L, asked = true, nowMillis = 1_000L + 30 * day))
        assertFalse("first use not noted yet", RatingMoment.mayAsk(0L, asked = false, nowMillis = 30 * day))
    }

    // --- Every string in tune in one go ---------------------------------------------------------

    private fun detected(
        string: Int,
        inTune: Boolean = true,
        mode: TunerMode = TunerMode.AUTO,
        cents: Float = 0f,
        hint: TunerHint? = if (inTune) TunerHint.IN_TUNE else null,
    ) =
        TunerUiState.Detected(
            mode = mode,
            tuning = Tunings.STANDARD,
            noteName = "E",
            octave = 2,
            frequencyHz = 82.4f,
            cents = cents,
            targetString = if (mode == TunerMode.AUTO) string else null,
            inTune = inTune,
            hint = hint,
        )

    /** Just over one second of frames at the tuner's rate, [FRAME] each. */
    private val second = (1f / FRAME).toInt() + 1

    @Test
    fun `completes once, when the last string not yet in tune comes in tune`() {
        val all = AllStringsInTune(FRAME)
        for (string in 0 until 5) assertFalse(all.update(detected(string)))
        assertFalse("a string again", all.update(detected(2)))
        assertFalse("off pitch counts nothing", all.update(detected(5, inTune = false)))
        assertTrue(all.update(detected(5)))
        assertFalse("only once", all.update(detected(5)))
        assertFalse(all.update(detected(0)))
    }

    @Test
    fun `chromatic mode counts nothing, and a new tuning starts afresh`() {
        val all = AllStringsInTune(FRAME)
        for (string in 0 until 6) assertFalse(all.update(detected(string, mode = TunerMode.CHROMATIC)))
        for (string in 0 until 5) assertFalse(all.update(detected(string)))
        val dropD = TunerUiState.Detected(
            mode = TunerMode.AUTO, tuning = Tunings.DROP_D, noteName = "D", octave = 2, frequencyHz = 73.4f,
            cents = 0f, targetString = 5, inTune = true,
        )
        assertFalse("the other tuning's five strings don't carry over", all.update(dropD))
    }

    @Test
    fun `keeps the strings in tune so far, the ones ticked off on their buttons`() {
        val all = AllStringsInTune(FRAME)
        assertEquals(0L, all.tuned)
        all.update(detected(1))
        all.update(detected(4, inTune = false))
        all.update(detected(3, mode = TunerMode.CHROMATIC))
        all.update(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD))
        assertEquals("only the A string, in tune in guitar mode", 0b10L, all.tuned)
        assertEquals(Tunings.STANDARD, all.tuning)
        all.update(detected(1, inTune = false))
        assertEquals("a string merely not held in tune stays ticked", 0b10L, all.tuned)
        val ticks = TunedStrings(Tunings.STANDARD, all.tuned)
        assertTrue(1 in ticks)
        assertFalse(0 in ticks)
        assertFalse(-1 in ticks)
        assertFalse(64 in ticks)
    }

    @Test
    fun `a ticked string played clearly out of tune for a second loses its tick, and earns it again`() {
        val all = AllStringsInTune(FRAME)
        all.update(detected(1))
        all.update(detected(4))
        val sharp = detected(1, inTune = false, cents = 15f, hint = TunerHint.TUNE_DOWN)
        repeat(second - 3) { all.update(sharp) }
        assertEquals("not yet: under a second", 0b10010L, all.tuned)
        repeat(3) { all.update(sharp) }
        assertEquals("the A string's tick goes, the B string's stays", 0b10000L, all.tuned)

        all.update(detected(1))
        assertEquals("back in tune: ticked again", 0b10010L, all.tuned)
    }

    @Test
    fun `a pluck's attack, a passing wobble or a reading near the window keeps the tick`() {
        val all = AllStringsInTune(FRAME)
        all.update(detected(1))
        val sharp = detected(1, inTune = false, cents = 15f, hint = TunerHint.TUNE_DOWN)
        // Off for most of a second, three times, with a moment back in tune or a gap between plucks.
        repeat(second - 5) { all.update(sharp) }
        all.update(detected(1))
        repeat(second - 5) { all.update(sharp) }
        all.update(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD))
        repeat(second - 5) { all.update(sharp) }
        all.update(detected(1))
        // Just inside the exit margin (7 cents, Relaxed): not clearly out of tune.
        repeat(2 * second) { all.update(detected(1, inTune = false, cents = 6.5f, hint = TunerHint.TUNE_DOWN)) }
        // Another string heard off pitch says nothing about this one.
        repeat(2 * second) { all.update(detected(2, inTune = false, cents = 15f, hint = TunerHint.TUNE_DOWN)) }
        assertEquals(0b10L, all.tuned)

        // Precise's narrower window (out past 3 cents) counts 6.5 cents as clearly off.
        repeat(second) { all.update(detected(1, inTune = false, cents = 6.5f, hint = TunerHint.TUNE_DOWN), outCents = 3f) }
        assertEquals(0L, all.tuned)
    }

    @Test
    fun `a new tuning or A4, even while nothing is heard, starts the ticks afresh`() {
        val all = AllStringsInTune(FRAME)
        all.update(detected(1))
        all.update(TunerUiState.Listening(TunerMode.AUTO, Tunings.DROP_D))
        assertEquals(0L, all.tuned)
        assertEquals(Tunings.DROP_D, all.tuning)

        all.update(detected(2))
        assertEquals(0b100L, all.tuned)
        all.update(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD, a4Hz = 442f))
        assertEquals("tuned to the old A4, not the new", 0L, all.tuned)
    }

    // --- The ViewModel ----------------------------------------------------------------------------

    /** Every string of standard tuning for 1.5 s, in order, then silence; [skip] leaves one string out, [quiet] the silence. */
    private fun tuningRecorder(skip: Int? = null, quiet: Boolean = true): FakeAudioRecorder {
        val hz = floatArrayOf(82.4069f, 110f, 146.8324f, 195.9977f, 246.9417f, 329.6276f)
        val parts = hz.indices.filter { it != skip }.map { TestSignals.sawtooth(hz[it], length = 72_000) }
        val tail = if (quiet) FloatArray(48_000 * 6) else TestSignals.sawtooth(hz.last(), length = 48_000 * 6)
        val signal = FloatArray(parts.sumOf { it.size } + tail.size)
        var at = 0
        for (part in parts + listOf(tail)) {
            part.copyInto(signal, at)
            at += part.size
        }
        return FakeAudioRecorder(signal, loop = false)
    }

    private val now = 100L * RatingMoment.FIRST_DAY_MILLIS

    private companion object {
        /** The tuner's time between two states, near the phone's (1024 samples at 48 kHz). */
        const val FRAME = 1024f / 48_000f
    }

    private fun TestScope.viewModel(recorder: FakeAudioRecorder, settings: FakeSettingsRepository) =
        TunerViewModel(recorder, settings, FakeTonePlayer(), StandardTestDispatcher(testScheduler), clock = { now })

    private fun settings(firstUse: Long = now - 3 * RatingMoment.FIRST_DAY_MILLIS, asked: Boolean = false) =
        FakeSettingsRepository(AppSettings(firstUseMillis = firstUse, ratingAsked = asked))

    @Test
    fun `due once every string was in tune and the strings fell quiet, then saved as asked`() = runTest(dispatcher) {
        val settings = settings()
        val vm = viewModel(tuningRecorder(), settings)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }

        advanceTimeBy(9_000)
        assertFalse("not while the strings are played", vm.ratingDue.value)
        advanceTimeBy(5_000)
        assertTrue("due after the quiet", vm.ratingDue.value)

        vm.onRatingAsked()
        runCurrent()
        assertFalse(vm.ratingDue.value)
        assertTrue("never asked again", settings.current.ratingAsked)
    }

    @Test
    fun `never on the first day`() = runTest(dispatcher) {
        val vm = viewModel(tuningRecorder(), settings(firstUse = now - RatingMoment.FIRST_DAY_MILLIS / 2))
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(16_000)
        assertFalse(vm.ratingDue.value)
    }

    @Test
    fun `never once asked`() = runTest(dispatcher) {
        val vm = viewModel(tuningRecorder(), settings(asked = true))
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(16_000)
        assertFalse(vm.ratingDue.value)
    }

    @Test
    fun `not when a string was left out`() = runTest(dispatcher) {
        val vm = viewModel(tuningRecorder(skip = 3), settings())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(16_000)
        assertFalse(vm.ratingDue.value)
    }

    @Test
    fun `not while a note keeps sounding`() = runTest(dispatcher) {
        val vm = viewModel(tuningRecorder(quiet = false), settings())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(14_000)
        assertFalse(vm.ratingDue.value)
    }
}
