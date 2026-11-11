package io.github.klaboukes.twangle.wear

import io.github.klaboukes.twangle.core.audio.AudioRecorderException
import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.audio.FakeScreenReaderSpeech
import io.github.klaboukes.twangle.core.dsp.guide.GuidePulse
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
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
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class WatchTunerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** One second of E4 (329.63 Hz) on a loop, like a string that keeps ringing. */
    private fun e4Recorder() = FakeAudioRecorder(TestSignals.sawtooth(329.63f, length = 48_000), loop = true)

    private fun TestScope.viewModel(recorder: FakeAudioRecorder) =
        WatchTunerViewModel(recorder, StandardTestDispatcher(testScheduler))

    @Test
    fun `the mic stays closed until the screen collects`() = runTest(dispatcher) {
        val recorder = e4Recorder()
        viewModel(recorder)
        advanceTimeBy(1_000)

        assertFalse(recorder.isRecording.value)
        assertEquals(0, recorder.startCount)
    }

    @Test
    fun `shows the note while collected`() = runTest(dispatcher) {
        val vm = viewModel(e4Recorder())
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(1_000)

        val state = vm.state.value
        assertTrue("expected Detected, was $state", state is WatchTunerState.Detected)
        assertEquals(Note.of(PitchClass.E, 4), (state as WatchTunerState.Detected).note)
    }

    @Test
    fun `releases the mic 5 s after the screen stops collecting`() = runTest(dispatcher) {
        val recorder = e4Recorder()
        val vm = viewModel(recorder)
        val screen = backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)
        assertTrue(recorder.isRecording.value)

        screen.cancel()
        advanceTimeBy(WatchTunerViewModel.STOP_TIMEOUT_MILLIS - 100)
        assertTrue("a quick trip away keeps the mic", recorder.isRecording.value)
        advanceTimeBy(200)
        assertFalse(recorder.isRecording.value)
        assertEquals(1, recorder.startCount)
    }

    @Test
    fun `lets go of the mic at once when the screen turns off, and listens again when it comes on`() = runTest(dispatcher) {
        val recorder = e4Recorder()
        val vm = viewModel(recorder)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(1_000)
        assertTrue(vm.state.value is WatchTunerState.Detected)

        vm.setScreenOn(false)
        advanceTimeBy(1)
        assertFalse("no 5 s grace when the screen goes off", recorder.isRecording.value)
        assertEquals(WatchTunerState.Listening, vm.state.value)

        advanceTimeBy(10_000)
        assertEquals(1, recorder.startCount)

        vm.setScreenOn(true)
        advanceTimeBy(1_000)
        assertTrue(recorder.isRecording.value)
        assertEquals(2, recorder.startCount)
        assertTrue(vm.state.value is WatchTunerState.Detected)
    }

    @Test
    fun `screen off before the tuner shows keeps the mic closed`() = runTest(dispatcher) {
        val recorder = e4Recorder()
        val vm = viewModel(recorder)
        vm.setScreenOn(false)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(1_000)

        assertEquals(0, recorder.startCount)
        assertEquals(WatchTunerState.Listening, vm.state.value)
    }

    @Test
    fun `a busy mic shows an error, and retry opens it again`() = runTest(dispatcher) {
        val recorder = FakeAudioRecorder(failure = AudioRecorderException.MicrophoneBusy())
        val vm = viewModel(recorder)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(100)
        assertEquals(WatchTunerState.Failed(WatchTunerError.MICROPHONE_BUSY), vm.state.value)

        recorder.failure = null
        recorder.signal = TestSignals.sawtooth(329.63f, length = 48_000)
        recorder.loop = true
        vm.retry()
        advanceTimeBy(1_000)

        assertEquals(2, recorder.startCount)
        assertTrue(vm.state.value is WatchTunerState.Detected)
    }

    @Test
    fun `coming back on screen after a failure opens the mic again, but leaves a working mic alone`() = runTest(dispatcher) {
        val recorder = FakeAudioRecorder(failure = AudioRecorderException.MicrophoneBusy())
        val vm = viewModel(recorder)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(100)
        assertEquals(WatchTunerState.Failed(WatchTunerError.MICROPHONE_BUSY), vm.state.value)

        recorder.failure = null
        recorder.signal = TestSignals.sawtooth(329.63f, length = 48_000)
        recorder.loop = true
        vm.shown()
        advanceTimeBy(1_000)
        assertEquals(2, recorder.startCount)
        assertTrue(vm.state.value is WatchTunerState.Detected)

        vm.shown()
        advanceTimeBy(1_000)
        assertEquals("a working mic isn't restarted", 2, recorder.startCount)
    }

    // Phase 85: the vibration guide.

    /** The guide's motor, counting what it plays and how often it is stopped. */
    private class CountingMotor : WatchGuideVibrator {
        val played = mutableListOf<GuidePulse>()
        var stops = 0

        override fun play(pulse: GuidePulse) {
            if (pulse != GuidePulse.NONE) played += pulse
        }

        override fun stop() {
            stops++
        }
    }

    /** A string 20 cents flat, held on a loop: the guide taps to tighten. */
    private fun flatRecorder() = FakeAudioRecorder(TestSignals.sawtooth(110f * 0.98851f, length = 48_000), loop = true)

    private fun TestScope.guidedViewModel(
        recorder: FakeAudioRecorder,
        motor: CountingMotor,
        speech: FakeScreenReaderSpeech = FakeScreenReaderSpeech(),
        store: WatchTunerStore = WatchTunerStore.inMemory(),
    ) = WatchTunerViewModel(recorder, StandardTestDispatcher(testScheduler), speech, store, motor)

    @Test
    fun `the guide is on by itself while a screen reader is, until the player chooses`() = runTest(dispatcher) {
        val speech = FakeScreenReaderSpeech(on = false)
        val vm = guidedViewModel(e4Recorder(), CountingMotor(), speech)
        advanceTimeBy(1)
        assertFalse(vm.guideOn.value)
        speech.isOn = true
        advanceTimeBy(1)
        assertTrue("on with TalkBack", vm.guideOn.value)

        vm.setVibrationGuide(false)
        advanceTimeBy(1)
        assertFalse("the player's choice wins", vm.guideOn.value)
        speech.isOn = false
        vm.setVibrationGuide(true)
        advanceTimeBy(1)
        assertTrue(vm.guideOn.value)
    }

    @Test
    fun `off by default, the guide plays nothing`() = runTest(dispatcher) {
        val motor = CountingMotor()
        val vm = guidedViewModel(flatRecorder(), motor)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(3_000)
        assertTrue(vm.state.value is WatchTunerState.Detected)
        assertTrue(motor.played.isEmpty())
    }

    @Test
    fun `on, a flat string taps, and leaving the page stops it at once`() = runTest(dispatcher) {
        val motor = CountingMotor()
        val vm = guidedViewModel(flatRecorder(), motor, store = WatchTunerStore.inMemory(choice = true))
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(3_000)
        assertTrue("taps: ${motor.played}", motor.played.size >= 2)
        assertTrue(motor.played.all { it == GuidePulse.TIGHTEN })

        vm.setShown(false)
        assertEquals(1, motor.stops)
        val before = motor.played.size
        advanceTimeBy(3_000)
        assertEquals("nothing while the page is away", before, motor.played.size)

        vm.setShown(true)
        advanceTimeBy(2_000)
        assertTrue("taps again on coming back", motor.played.size > before)
    }

    @Test
    fun `the screen going off stops the guide with the microphone`() = runTest(dispatcher) {
        val motor = CountingMotor()
        val vm = guidedViewModel(flatRecorder(), motor, store = WatchTunerStore.inMemory(choice = true))
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(2_000)
        assertTrue(motor.played.isNotEmpty())

        vm.setScreenOn(false)
        advanceTimeBy(1)
        assertTrue("stopped", motor.stops >= 1)
        val before = motor.played.size
        advanceTimeBy(3_000)
        assertEquals(before, motor.played.size)
    }

    @Test
    fun `failures map to what the watch says`() {
        assertEquals(WatchTunerError.PERMISSION_MISSING, WatchTunerError.of(AudioRecorderException.PermissionMissing()))
        assertEquals(WatchTunerError.MICROPHONE_BUSY, WatchTunerError.of(AudioRecorderException.MicrophoneBusy()))
        assertEquals(WatchTunerError.MICROPHONE_UNAVAILABLE, WatchTunerError.of(AudioRecorderException.ReadFailed(-3)))
        assertEquals(WatchTunerError.MICROPHONE_UNAVAILABLE, WatchTunerError.of(AudioRecorderException.InitializationFailed("x")))
    }

    @Test
    fun `a microphone failure is logged and shown`() = runTest {
        val logged = mutableListOf<Throwable>()
        val busy = AudioRecorderException.MicrophoneBusy()
        val shown = flow<WatchTunerState> {
            emit(WatchTunerState.Listening)
            throw busy
        }.showingMicFailures { logged += it }.toList()
        assertEquals(listOf(WatchTunerState.Listening, WatchTunerState.Failed(WatchTunerError.MICROPHONE_BUSY)), shown)
        assertEquals(listOf<Throwable>(busy), logged)
    }

    // Until Phase 91 a bug in the watch app's own code showed as an unavailable microphone, logged nowhere and never in
    // Play's crash reports; the phone has thrown it on since Phase 89.
    @Test
    fun `any other failure is logged and thrown on, not shown as an unavailable microphone`() = runTest {
        val logged = mutableListOf<Throwable>()
        val bug = IllegalStateException("a bug")
        val shown = mutableListOf<WatchTunerState>()
        try {
            flow<WatchTunerState> { throw bug }.showingMicFailures { logged += it }.collect { shown += it }
            fail("the bug was swallowed")
        } catch (e: IllegalStateException) {
            assertSame(bug, e)
        }
        assertEquals(emptyList<WatchTunerState>(), shown)
        assertEquals(listOf<Throwable>(bug), logged)
    }

    @Test
    fun `note names and the cents' digits`() {
        val aSharp = Note.of(PitchClass.A_SHARP, 2)
        assertEquals("A♯", WatchNoteName.letter(aSharp))
        assertEquals("La♯", WatchNoteName.letter(aSharp, Notation.forLanguage("fr")))
        assertEquals("H", WatchNoteName.letter(Note.of(PitchClass.B, 3), Notation.forLanguage("de")))
        assertEquals(1f, WatchNoteName.sizeFor("A♯"))
        assertEquals(0.8f, WatchNoteName.sizeFor("La♯"))
        assertEquals(0.65f, WatchNoteName.sizeFor("Sol♯"))
        assertEquals("2", WatchNoteName.octave(aSharp))
        assertEquals("12", digits(12, Locale.ROOT))
        // In the digits of the app's language, like its texts; the octave stays 0–9.
        val arabic = Locale.forLanguageTag("ar-u-nu-arab")
        assertEquals("١٢", digits(12, arabic))
    }
}
