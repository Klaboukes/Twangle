package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.audio.AudioConfig
import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.audio.FakeGuideSoundPlayer
import io.github.klaboukes.twangle.core.audio.FakeScreenReaderSpeech
import io.github.klaboukes.twangle.core.audio.FakeTonePlayer
import io.github.klaboukes.twangle.core.audio.ScreenReaderSpeech
import io.github.klaboukes.twangle.core.dsp.guide.GuidePulse
import io.github.klaboukes.twangle.core.dsp.guide.GuideSounds
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import io.github.klaboukes.twangle.core.settings.InTuneSoundLength
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.feature.tuner.components.GuideVibrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The sound guide in the tuner (Phase 94): sounds only while a note is heard, up for sharp and down for flat at the
 * vibration guide's pace, one bell of the player's length in tune, its own switch beside the vibration guide's, and,
 * above all, the tuner never reading the guide's own sounds, which are notes it could hear (F♯5, A♯5, C5).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SoundGuideTunerTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Records what the vibration guide plays. */
    private class FakeGuideVibrator : GuideVibrator {
        val played = mutableListOf<GuidePulse>()

        override fun play(pulse: GuidePulse) {
            if (pulse != GuidePulse.NONE) played += pulse
        }

        override fun stop() = Unit
    }

    /** A steady tone at [hz], looped: 110 Hz is A2 in tune, 108 Hz about 31 cents flat, 112 Hz 31 cents sharp. */
    private fun recorder(hz: Float) = FakeAudioRecorder(TestSignals.sawtooth(hz, length = 48_000), loop = true)

    private fun TestScope.viewModel(
        recorder: AudioRecorder,
        sounds: FakeGuideSoundPlayer,
        settings: AppSettings = AppSettings(soundGuide = true),
        vibrator: GuideVibrator = GuideVibrator.NONE,
        speech: ScreenReaderSpeech = ScreenReaderSpeech.NONE,
    ) = TunerViewModel(
        recorder, FakeSettingsRepository(settings), FakeTonePlayer(), StandardTestDispatcher(testScheduler),
        screenReaderSpeech = speech,
        guideVibrator = vibrator,
        guideSounds = sounds,
    ).also { it.onPermissionChanged(true) }

    @Test
    fun `a flat string steps down, quicker than once a second`() = runTest(dispatcher) {
        val sounds = FakeGuideSoundPlayer(backgroundScope)
        val vm = viewModel(recorder(108f), sounds)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(3_000)
        assertTrue("sounds: ${sounds.played}", sounds.played.size >= 3)
        assertTrue(sounds.played.all { it == GuidePulse.TIGHTEN })
    }

    @Test
    fun `a sharp string steps up`() = runTest(dispatcher) {
        val sounds = FakeGuideSoundPlayer(backgroundScope)
        val vm = viewModel(recorder(112f), sounds)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(3_000)
        assertTrue("sounds: ${sounds.played}", sounds.played.size >= 3)
        assertTrue(sounds.played.all { it == GuidePulse.LOOSEN })
    }

    @Test
    fun `far off, the sounds keep the vibration guide's pace`() = runTest(dispatcher) {
        // 40 cents flat: one every 0.7 s, as the taps; the sound and its set-aside (about 0.35 s) fit between.
        val sounds = FakeGuideSoundPlayer(backgroundScope)
        val vm = viewModel(recorder(107.5f), sounds)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(1_000)
        val before = sounds.played.size
        advanceTimeBy(7_000)
        val perSecond = (sounds.played.size - before) / 7f
        assertEquals("sounds a second: ${sounds.played.size - before} in 7 s", 1f / 0.7f, perSecond, 0.15f)
    }

    @Test
    fun `in tune rings one bell of the player's length, however long it holds`() = runTest(dispatcher) {
        val sounds = FakeGuideSoundPlayer(backgroundScope)
        val vm = viewModel(recorder(110f), sounds, settings = AppSettings(soundGuide = true, inTuneSoundLength = InTuneSoundLength.TWO_SECONDS))
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(8_000)
        assertEquals(listOf(GuidePulse.IN_TUNE), sounds.played)
        assertEquals(listOf(2f), sounds.inTuneLengths)
    }

    @Test
    fun `off until the player turns it on, TalkBack or not`() = runTest(dispatcher) {
        val sounds = FakeGuideSoundPlayer(backgroundScope)
        val vm = viewModel(recorder(108f), sounds, settings = AppSettings(), speech = FakeScreenReaderSpeech(on = true))
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(3_000)
        assertEquals(emptyList<GuidePulse>(), sounds.played)
    }

    @Test
    fun `sound and vibration are switches of their own, and play together when both are on`() = runTest(dispatcher) {
        val sounds = FakeGuideSoundPlayer(backgroundScope)
        val vibrator = FakeGuideVibrator()
        val vm = viewModel(recorder(108f), sounds, settings = AppSettings(soundGuide = true, vibrationGuide = false), vibrator = vibrator)
        val collecting = backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(2_000)
        assertTrue(sounds.played.isNotEmpty())
        assertEquals("the vibration guide off: no taps", emptyList<GuidePulse>(), vibrator.played)
        collecting.cancel()

        val bothSounds = FakeGuideSoundPlayer(backgroundScope)
        val bothTaps = FakeGuideVibrator()
        val both = viewModel(recorder(108f), bothSounds, settings = AppSettings(soundGuide = true, vibrationGuide = true), vibrator = bothTaps)
        backgroundScope.launch { both.state.collect {} }
        advanceTimeBy(2_000)
        assertTrue(bothSounds.played.isNotEmpty())
        assertEquals("a tap with every sound", bothSounds.played, bothTaps.played)
    }

    @Test
    fun `leaving the screen stops the sound at once`() = runTest(dispatcher) {
        val sounds = FakeGuideSoundPlayer(backgroundScope)
        val vm = viewModel(recorder(108f), sounds)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(1_000)
        vm.setShown(false)
        assertTrue(sounds.stopCount >= 1)
        val left = sounds.played.size
        advanceTimeBy(2_000)
        assertEquals("quiet while the screen is gone", left, sounds.played.size)
    }

    /**
     * A microphone that hears a string at [stringHz] and, while [sounds] plays and for [echoChunks] chunks after, the
     * guide's own sound, louder than the string (the phone's speaker beside its microphone): its notes, steady, as the
     * worst case.
     */
    private class HearsTheGuide(
        private val stringHz: Float,
        private val sounds: FakeGuideSoundPlayer?,
        private val always: Boolean = false,
        private val echoChunks: Int = 2,
    ) : AudioRecorder {
        override val config = AudioConfig()
        private val string = TestSignals.sawtooth(stringHz, length = 48_000, amplitude = 0.3f)
        private val guide = TestSignals.sine(GuideSounds.AS5_HZ, length = 48_000, amplitude = 0.8f)

        override fun chunks(): Flow<FloatArray> = flow {
            val chunk = FloatArray(config.chunkSize)
            var pos = 0
            var echo = 0
            while (true) {
                echo = if (always || sounds?.isSounding?.value == true) echoChunks else maxOf(0, echo - 1)
                val loud = always || echo > 0
                for (i in chunk.indices) {
                    val j = (pos + i) % string.size
                    chunk[i] = string[j] + if (loud) guide[j] else 0f
                }
                pos = (pos + chunk.size) % string.size
                delay(config.chunkSize * 1000L / config.sampleRate)
                emit(chunk)
            }
        }
    }

    @Test
    fun `the guide's own sound is a note the tuner would read`() = runTest(dispatcher) {
        // The check below means something: with the guide's A♯5 always in the room, the tuner reads it.
        val vm = viewModel(HearsTheGuide(108f, sounds = null, always = true), FakeGuideSoundPlayer(backgroundScope), settings = AppSettings(mode = TunerMode.CHROMATIC))
        val read = mutableListOf<Float>()
        backgroundScope.launch { vm.state.collect { if (it is TunerUiState.Detected) read += it.frequencyHz } }
        advanceTimeBy(2_000)
        assertTrue("read: ${read.distinct().take(10)}", read.any { it > 800f })
    }

    @Test
    fun `the tuner never reads the guide's own sounds, and the guide never answers itself`() = runTest(dispatcher) {
        for (mode in listOf(TunerMode.CHROMATIC, TunerMode.AUTO)) {
            val sounds = FakeGuideSoundPlayer(backgroundScope)
            val vm = viewModel(HearsTheGuide(108f, sounds), sounds, settings = AppSettings(soundGuide = true, mode = mode))
            val read = mutableListOf<Float>()
            val collecting = backgroundScope.launch { vm.state.collect { if (it is TunerUiState.Detected) read += it.frequencyHz } }
            advanceTimeBy(5_000)
            collecting.cancel()
            advanceTimeBy(6_000)
            assertTrue("$mode: sounds ${sounds.played}", sounds.played.size >= 5)
            assertTrue("$mode: only flat, the string's own: ${sounds.played}", sounds.played.all { it == GuidePulse.TIGHTEN })
            assertTrue("$mode: read ${read.filter { it !in 100f..120f }.distinct().take(10)}", read.all { it in 100f..120f })
        }
    }

    @Test
    fun `the tuner still follows the string between the sounds`() = runTest(dispatcher) {
        // A string that the player brings up to pitch: 31 cents flat for 2 s, then in tune. The tuner sees the change
        // and the guide rings the bell, even though its sounds came every 0.7 s before.
        val sounds = FakeGuideSoundPlayer(backgroundScope)
        val flat = TestSignals.sawtooth(108f, length = 96_000)
        val inTune = TestSignals.sawtooth(110f, length = 192_000)
        val vm = viewModel(FakeAudioRecorder(flat + inTune), sounds, settings = AppSettings(soundGuide = true))
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(5_500)
        assertTrue("flat sounds first: ${sounds.played}", sounds.played.first() == GuidePulse.TIGHTEN)
        assertEquals("then the bell: ${sounds.played}", GuidePulse.IN_TUNE, sounds.played.last())
        assertEquals(1, sounds.played.count { it == GuidePulse.IN_TUNE })
    }
}
