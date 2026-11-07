package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.audio.FakeScreenReaderSpeech
import io.github.klaboukes.twangle.core.audio.FakeTonePlayer
import io.github.klaboukes.twangle.core.audio.ScreenReaderSpeech
import io.github.klaboukes.twangle.core.dsp.guide.GuidePulse
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import io.github.klaboukes.twangle.feature.tuner.components.GuideVibrator
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

/**
 * The vibration guide in the tuner (Phase 85): taps only while a note is heard, in the word's direction, the long buzz
 * once in tune in place of *Vibrate when in tune*'s click, and nothing once the tuner stops listening or is left.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VibrationGuideTunerTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Records what the guide plays. */
    private class FakeGuideVibrator : GuideVibrator {
        val played = mutableListOf<GuidePulse>()
        var stops = 0

        override fun play(pulse: GuidePulse) {
            if (pulse != GuidePulse.NONE) played += pulse
        }

        override fun stop() {
            stops++
        }
    }

    /** A steady tone at [hz], looped: 110 Hz is A2 in tune, 108 Hz about 31 cents flat, 112 Hz 31 cents sharp. */
    private fun recorder(hz: Float) = FakeAudioRecorder(TestSignals.sawtooth(hz, length = 48_000), loop = true)

    private fun TestScope.viewModel(
        recorder: FakeAudioRecorder,
        vibrator: GuideVibrator,
        settings: AppSettings = AppSettings(vibrationGuide = true),
        speech: ScreenReaderSpeech = ScreenReaderSpeech.NONE,
    ) = TunerViewModel(
        recorder, FakeSettingsRepository(settings), FakeTonePlayer(), StandardTestDispatcher(testScheduler),
        screenReaderSpeech = speech,
        guideVibrator = vibrator,
    ).also { it.onPermissionChanged(true) }

    @Test
    fun `a flat string taps once per group, quicker than once a second`() = runTest(dispatcher) {
        val vibrator = FakeGuideVibrator()
        val vm = viewModel(recorder(108f), vibrator)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(3_000)
        assertTrue("taps: ${vibrator.played}", vibrator.played.size >= 3)
        assertTrue(vibrator.played.all { it == GuidePulse.TIGHTEN })
    }

    @Test
    fun `a sharp string taps twice per group`() = runTest(dispatcher) {
        val vibrator = FakeGuideVibrator()
        val vm = viewModel(recorder(112f), vibrator)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(3_000)
        assertTrue("taps: ${vibrator.played}", vibrator.played.size >= 3)
        assertTrue(vibrator.played.all { it == GuidePulse.LOOSEN })
    }

    @Test
    fun `in tune buzzes once, instead of the in-tune click`() = runTest(dispatcher) {
        val vibrator = FakeGuideVibrator()
        val vm = viewModel(recorder(110f), vibrator)
        backgroundScope.launch { vm.state.collect {} }
        backgroundScope.launch { vm.vibrateInTune.collect {} }
        advanceTimeBy(4_000)
        assertEquals(1, vibrator.played.count { it == GuidePulse.IN_TUNE })
        assertEquals(GuidePulse.IN_TUNE, vibrator.played.last())
        assertFalse("never both: the click is off", vm.vibrateInTune.value)
    }

    @Test
    fun `nothing while no note is heard`() = runTest(dispatcher) {
        val vibrator = FakeGuideVibrator()
        val vm = viewModel(FakeAudioRecorder.silence(), vibrator)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(3_000)
        assertEquals(emptyList<GuidePulse>(), vibrator.played)
    }

    @Test
    fun `off, nothing plays and the in-tune click stays`() = runTest(dispatcher) {
        val vibrator = FakeGuideVibrator()
        val vm = viewModel(recorder(108f), vibrator, settings = AppSettings())
        backgroundScope.launch { vm.state.collect {} }
        backgroundScope.launch { vm.vibrateInTune.collect {} }
        advanceTimeBy(3_000)
        assertEquals(emptyList<GuidePulse>(), vibrator.played)
        assertTrue(vm.vibrateInTune.value)
    }

    @Test
    fun `never set, it follows the screen reader`() = runTest(dispatcher) {
        val vibrator = FakeGuideVibrator()
        val speech = FakeScreenReaderSpeech(on = true)
        val vm = viewModel(recorder(108f), vibrator, settings = AppSettings(), speech = speech)
        backgroundScope.launch { vm.state.collect {} }
        backgroundScope.launch { vm.vibrateInTune.collect {} }
        advanceTimeBy(2_000)
        assertTrue("on with TalkBack: ${vibrator.played}", vibrator.played.isNotEmpty())
        assertFalse(vm.vibrateInTune.value)

        speech.isOn = false
        advanceTimeBy(100)
        val before = vibrator.played.size
        advanceTimeBy(2_000)
        assertEquals("off with TalkBack off", before, vibrator.played.size)
        assertTrue(vm.vibrateInTune.value)
    }

    @Test
    fun `turned off by the player, it stays off with TalkBack on`() = runTest(dispatcher) {
        val vibrator = FakeGuideVibrator()
        val vm = viewModel(recorder(108f), vibrator, settings = AppSettings(vibrationGuide = false), speech = FakeScreenReaderSpeech(on = true))
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(2_000)
        assertEquals(emptyList<GuidePulse>(), vibrator.played)
    }

    @Test
    fun `it stops the moment the screen is left, and when the tuner stops listening`() = runTest(dispatcher) {
        val vibrator = FakeGuideVibrator()
        val vm = viewModel(recorder(108f), vibrator)
        val collecting = backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(1_500)
        assertTrue(vibrator.played.isNotEmpty())

        // Settings opened: the microphone stays open 5 s, the guide goes quiet at once.
        vm.setShown(false)
        assertEquals(1, vibrator.stops)
        val left = vibrator.played.size
        advanceTimeBy(2_000)
        assertEquals("quiet while the screen is gone", left, vibrator.played.size)

        // Back on the tuner, it taps again; then the tuner stops listening.
        vm.setShown(true)
        advanceTimeBy(1_000)
        assertTrue(vibrator.played.size > left)
        collecting.cancel()
        advanceTimeBy(6_000)
        assertTrue("stopped with the microphone", vibrator.stops >= 2)
        val stopped = vibrator.played.size
        advanceTimeBy(2_000)
        assertEquals(stopped, vibrator.played.size)
    }
}
