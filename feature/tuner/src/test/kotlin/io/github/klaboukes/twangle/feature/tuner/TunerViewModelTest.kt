package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.audio.AudioRecorderException
import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.audio.FakeTonePlayer
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.synth.StringVoices
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import io.github.klaboukes.twangle.core.settings.InTunePrecision
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.UnderMeter
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

@OptIn(ExperimentalCoroutinesApi::class)
class TunerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** One second of A2 (110 Hz, the 5th string): a whole number of periods, so looping it has no click. */
    private fun a2Recorder() = FakeAudioRecorder(TestSignals.sawtooth(110f, length = 48_000), loop = true)

    private val settings = FakeSettingsRepository()
    private val tones = FakeTonePlayer()

    private fun TestScope.viewModel(recorder: FakeAudioRecorder) =
        TunerViewModel(recorder, settings, tones, StandardTestDispatcher(testScheduler))

    private fun TunerViewModel.detected(): TunerUiState.Detected {
        val state = state.value
        assertTrue("expected Detected, was $state", state is TunerUiState.Detected)
        return state as TunerUiState.Detected
    }

    @Test
    fun `asks for permission and keeps the mic closed until it is granted`() = runTest(dispatcher) {
        val recorder = a2Recorder()
        val vm = viewModel(recorder)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)

        assertEquals(TunerUiState.PermissionRequired, vm.state.value)
        assertFalse(recorder.isRecording.value)
        assertEquals(0, recorder.startCount)
    }

    @Test
    fun `detects the played string in auto mode`() = runTest(dispatcher) {
        val vm = viewModel(a2Recorder())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)

        val state = vm.detected()
        assertEquals(TunerMode.AUTO, state.mode)
        assertEquals(Tunings.STANDARD, state.tuning)
        assertEquals(1, state.targetString)
        assertEquals("A", state.noteName)
        assertEquals(2, state.octave)
        assertTrue("off by ${state.cents} c", abs(state.cents) < 1f)
        assertEquals(110f, state.frequencyHz, 0.1f)
    }

    @Test
    fun `a bass tuning makes the tuner hear its low E without reopening the mic`() = runTest(dispatcher) {
        // One second of E1 (41.2 Hz), the bass's lowest string, below the guitar's range.
        val recorder = FakeAudioRecorder(TestSignals.sawtooth(41.2034f, length = 48_000), loop = true)
        val vm = viewModel(recorder)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(400)
        val guitar = vm.state.value
        assertFalse("guitar range heard $guitar", guitar is TunerUiState.Detected && guitar.octave == 1)

        vm.setTuning(Instruments.BASS_4.defaultTuning)
        advanceTimeBy(400)
        val state = vm.detected()
        assertEquals(0, state.targetString)
        assertEquals("E", state.noteName)
        assertEquals(1, state.octave)
        assertTrue("off by ${state.cents} c", abs(state.cents) < 1f)
        assertEquals(1, recorder.startCount)
    }

    @Test
    fun `switching to chromatic keeps the mic open`() = runTest(dispatcher) {
        val recorder = a2Recorder()
        val vm = viewModel(recorder)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)

        vm.setMode(TunerMode.CHROMATIC)
        advanceTimeBy(100)

        val state = vm.detected()
        assertEquals(TunerMode.CHROMATIC, vm.mode.value)
        assertEquals(TunerMode.CHROMATIC, state.mode)
        assertNull(state.targetString)
        assertEquals("A", state.noteName)
        assertEquals(1, recorder.startCount)
    }

    @Test
    fun `switching modes saves the mode`() = runTest(dispatcher) {
        val vm = viewModel(a2Recorder())

        vm.setMode(TunerMode.CHROMATIC)
        runCurrent()
        assertEquals(TunerMode.CHROMATIC, settings.current.mode)

        vm.setMode(TunerMode.AUTO)
        runCurrent()
        assertEquals(TunerMode.AUTO, settings.current.mode)
    }

    @Test
    fun `shows the mode the screen already read before the settings arrive`() = runTest(dispatcher) {
        settings.current = AppSettings(mode = TunerMode.CHROMATIC)
        val vm = TunerViewModel(a2Recorder(), settings, tones, StandardTestDispatcher(testScheduler), settings.current)

        assertEquals(TunerMode.CHROMATIC, vm.mode.value)
        runCurrent()
        assertEquals(TunerMode.CHROMATIC, vm.mode.value)
    }

    @Test
    fun `a mode saved elsewhere after a pick on the switch shows at once`() = runTest(dispatcher) {
        // Phase 89: picking an instrument or tuning in Practice saves guitar mode from outside the tuner.
        val vm = viewModel(a2Recorder())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        vm.setMode(TunerMode.CHROMATIC)
        advanceTimeBy(500)
        assertEquals(TunerMode.CHROMATIC, vm.detected().mode)

        settings.setMode(TunerMode.AUTO)
        runCurrent()
        assertEquals(TunerMode.AUTO, vm.mode.value)
        advanceTimeBy(500)
        assertEquals(TunerMode.AUTO, vm.detected().mode)
        assertEquals(1, vm.detected().targetString)

        // The switch still works after it.
        vm.setMode(TunerMode.CHROMATIC)
        runCurrent()
        assertEquals(TunerMode.CHROMATIC, vm.mode.value)
        assertEquals(TunerMode.CHROMATIC, settings.current.mode)
    }

    @Test
    fun `a pick on the switch holds while its save is under way`() = runTest(dispatcher) {
        val saving = CompletableDeferred<Unit>()
        val saved = settings
        val slow = object : SettingsRepository by saved {
            override suspend fun setMode(mode: TunerMode) {
                saving.await()
                saved.setMode(mode)
            }
        }
        val vm = TunerViewModel(a2Recorder(), slow, tones, StandardTestDispatcher(testScheduler))
        runCurrent()

        vm.setMode(TunerMode.CHROMATIC)
        runCurrent()
        assertEquals(TunerMode.CHROMATIC, vm.mode.value)
        // Another setting saved meanwhile (the old mode with it) doesn't undo the pick.
        settings.setA4(442f)
        runCurrent()
        assertEquals(TunerMode.CHROMATIC, vm.mode.value)

        saving.complete(Unit)
        runCurrent()
        assertEquals(TunerMode.CHROMATIC, vm.mode.value)
        settings.setMode(TunerMode.AUTO)
        runCurrent()
        assertEquals(TunerMode.AUTO, vm.mode.value)
    }

    @Test
    fun `starts in the saved mode`() = runTest(dispatcher) {
        settings.current = AppSettings(mode = TunerMode.CHROMATIC)
        val vm = viewModel(a2Recorder())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        assertEquals(TunerMode.CHROMATIC, vm.mode.value)
        assertEquals(TunerMode.CHROMATIC, (vm.state.value as TunerUiState.Listening).mode)
        advanceTimeBy(500)
        assertEquals(TunerMode.CHROMATIC, vm.detected().mode)
        assertNull(vm.detected().targetString)
    }

    @Test
    fun `silence is listening`() = runTest(dispatcher) {
        val vm = viewModel(FakeAudioRecorder.silence())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)

        assertEquals(TunerUiState.Listening(TunerMode.AUTO, Tunings.STANDARD), vm.state.value)
    }

    @Test
    fun `mic is opened only while the screen collects, plus 5 seconds`() = runTest(dispatcher) {
        val recorder = a2Recorder()
        val vm = viewModel(recorder)
        vm.onPermissionChanged(true)
        runCurrent()
        assertFalse("nothing collects yet", recorder.isRecording.value)

        val ui = launch { vm.state.collect {} }
        advanceTimeBy(100)
        assertTrue(recorder.isRecording.value)

        ui.cancel()
        advanceTimeBy(4_900)
        assertTrue("kept for quick returns, e.g. rotation", recorder.isRecording.value)
        advanceTimeBy(200)
        runCurrent()
        assertFalse("released after 5 s in the background", recorder.isRecording.value)
        assertEquals(1, recorder.startCount)
    }

    @Test
    fun `losing the permission closes the mic`() = runTest(dispatcher) {
        val recorder = a2Recorder()
        val vm = viewModel(recorder)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(100)
        assertTrue(recorder.isRecording.value)

        vm.onPermissionChanged(false)
        runCurrent()
        assertFalse(recorder.isRecording.value)
        assertEquals(TunerUiState.PermissionRequired, vm.state.value)
    }

    @Test
    fun `a mic silenced mid-session shows busy instead of listening`() = runTest(dispatcher) {
        // A note, then Android hands the mic to a call: the recorder ends with MicrophoneBusy.
        val recorder = FakeAudioRecorder(
            TestSignals.sawtooth(110f, length = 48_000),
            failure = AudioRecorderException.MicrophoneBusy(),
        )
        val vm = viewModel(recorder)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)
        assertTrue(vm.state.value is TunerUiState.Detected)

        advanceTimeBy(1_000)

        assertEquals(TunerUiState.Error(TunerError.MIC_BUSY), vm.state.value)
        assertFalse(recorder.isRecording.value)
    }

    @Test
    fun `a busy mic shows an error and retry opens it again`() = runTest(dispatcher) {
        val recorder = FakeAudioRecorder(failure = AudioRecorderException.MicrophoneBusy())
        val vm = viewModel(recorder)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(100)
        assertEquals(TunerUiState.Error(TunerError.MIC_BUSY), vm.state.value)

        recorder.failure = null
        recorder.signal = TestSignals.sawtooth(110f, length = 48_000)
        recorder.loop = true
        vm.retry()
        advanceTimeBy(500)

        assertEquals(2, recorder.startCount)
        assertTrue(vm.state.value is TunerUiState.Detected)
    }

    @Test
    fun `uses the saved tuning and A4`() = runTest(dispatcher) {
        settings.current = AppSettings(a4Hz = 442f, tuning = Tunings.DROP_D)
        val vm = viewModel(FakeAudioRecorder(TestSignals.sawtooth(110.5f, length = 48_000), loop = true))
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)

        val state = vm.detected()
        assertEquals(Tunings.DROP_D, state.tuning)
        assertEquals(442f, state.a4Hz)
        // A2 at A4 = 442 Hz is 110.5 Hz.
        assertEquals("A", state.noteName)
        assertTrue("off by ${state.cents} c", abs(state.cents) < 1f)
    }

    @Test
    fun `a new A4 applies while listening, without reopening the mic`() = runTest(dispatcher) {
        val recorder = a2Recorder()
        val vm = viewModel(recorder)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)
        assertTrue(abs(vm.detected().cents) < 1f)

        settings.setA4(442f)
        advanceTimeBy(500)

        val state = vm.detected()
        assertEquals(442f, state.a4Hz)
        // 110 Hz is 7.85 cents flat of A2 once A4 is 442 Hz.
        assertEquals(-7.85f, state.cents, 1f)
        assertEquals(1, recorder.startCount)
    }

    @Test
    fun `picking an instrument saves it with its standard tuning and tunes its strings`() = runTest(dispatcher) {
        // G3 (196 Hz): the violin's lowest string.
        val recorder = FakeAudioRecorder(TestSignals.sawtooth(195.998f, length = 48_000), loop = true)
        val vm = viewModel(recorder)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)
        assertEquals(Instruments.GUITAR, vm.instrument.value)

        vm.setInstrument(Instruments.VIOLIN)
        advanceTimeBy(500)

        assertEquals(Instruments.VIOLIN, settings.current.instrument)
        assertEquals(Instruments.VIOLIN.defaultTuning, settings.current.tuning)
        assertEquals(Instruments.VIOLIN, vm.instrument.value)
        val state = vm.detected()
        assertEquals(Instruments.VIOLIN.defaultTuning, state.tuning)
        assertEquals(0, state.targetString)
        assertEquals("G", state.noteName)
        assertEquals(3, state.octave)
        assertEquals(1, recorder.startCount)
    }

    @Test
    fun `picking another instrument releases the lock`() = runTest(dispatcher) {
        val vm = viewModel(a2Recorder())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        vm.toggleString(5)
        advanceTimeBy(500)
        assertEquals(5, vm.detected().lockedString)

        vm.setInstrument(Instruments.BASS_4)
        advanceTimeBy(500)

        val state = vm.state.value
        val locked = (state as? TunerUiState.Detected)?.lockedString ?: (state as? TunerUiState.Listening)?.lockedString
        assertNull(locked)
    }

    @Test
    fun `the panel knows the saved tuning without the microphone`() = runTest(dispatcher) {
        settings.current = AppSettings(tuning = Tunings.DROP_D, notationChoice = Notation.SOLFEGE)
        val recorder = a2Recorder()
        val vm = viewModel(recorder)
        runCurrent()

        assertEquals(PanelTuning(Tunings.DROP_D, Notation.SOLFEGE), vm.panelTuning.value)
        assertEquals("the mic stays closed", 0, recorder.startCount)
    }

    @Test
    fun `a refused microphone is saved once and known from the start`() = runTest(dispatcher) {
        val vm = viewModel(a2Recorder())
        runCurrent()
        assertFalse(vm.micRefused.value)

        vm.onMicRefused()
        runCurrent()
        assertTrue(settings.current.micRefused)
        assertTrue(vm.micRefused.value)

        val started = TunerViewModel(a2Recorder(), FakeSettingsRepository(), FakeTonePlayer(), saved = AppSettings(micRefused = true))
        assertTrue("the saved value before the settings arrive", started.micRefused.value)
    }

    @Test
    fun `the options start as the settings read at start, never the defaults for a frame`() = runTest(dispatcher) {
        val saved = AppSettings(
            inTunePrecision = InTunePrecision.entries.first { it != InTunePrecision.DEFAULT },
            stringTickOff = !AppSettings().stringTickOff,
            underMeter = UnderMeter.CENTS,
            keepScreenOn = !AppSettings().keepScreenOn,
            vibrateInTune = !AppSettings().vibrateInTune,
        )
        val vm = TunerViewModel(a2Recorder(), FakeSettingsRepository(saved), FakeTonePlayer(), StandardTestDispatcher(testScheduler), saved = saved)

        // Before anything has been read or collected: what the screen shows on its first frame.
        assertEquals(saved.inTunePrecision, vm.inTunePrecision.value)
        assertEquals(saved.stringTickOff, vm.stringTickOff.value)
        assertEquals(saved.underMeter, vm.underMeter.value)
        assertEquals(saved.keepScreenOn, vm.keepScreenOn.value)
        assertEquals(saved.vibrateInTune, vm.vibrateInTune.value)
    }

    @Test
    fun `the instrument starts as the saved one before the settings arrive`() = runTest(dispatcher) {
        val vm = TunerViewModel(
            a2Recorder(),
            FakeSettingsRepository(AppSettings(instrument = Instruments.CELLO, tuning = Instruments.CELLO.defaultTuning)),
            FakeTonePlayer(),
            StandardTestDispatcher(testScheduler),
            saved = AppSettings(instrument = Instruments.CELLO, tuning = Instruments.CELLO.defaultTuning),
        )

        assertEquals(Instruments.CELLO, vm.instrument.value)
    }

    @Test
    fun `choosing a tuning saves it and retargets the strings`() = runTest(dispatcher) {
        // D2 (73.42 Hz): the 6th string in Drop D, but 2 semitones flat of E2 in standard.
        val vm = viewModel(FakeAudioRecorder(TestSignals.sawtooth(73.416f, length = 48_000), loop = true))
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)
        assertEquals("E", vm.detected().noteName)

        vm.setTuning(Tunings.DROP_D)
        advanceTimeBy(500)

        assertEquals(Tunings.DROP_D, settings.current.tuning)
        val state = vm.detected()
        assertEquals(Tunings.DROP_D, state.tuning)
        assertEquals(0, state.targetString)
        assertEquals("D", state.noteName)
        assertTrue("off by ${state.cents} c", abs(state.cents) < 1f)
    }

    @Test
    fun `tapping a string locks it and tapping it again unlocks`() = runTest(dispatcher) {
        // A2 is played, but the 4th string (D3) is locked: the needle shows the distance to D3.
        val vm = viewModel(a2Recorder())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }

        vm.toggleString(2)
        advanceTimeBy(500)
        val locked = vm.detected()
        assertEquals(2, locked.targetString)
        assertEquals(2, locked.lockedString)
        assertEquals(-500f, locked.cents, 1f)

        vm.toggleString(2)
        advanceTimeBy(500)
        val auto = vm.detected()
        assertEquals(1, auto.targetString)
        assertNull(auto.lockedString)
    }

    @Test
    fun `changing the tuning releases the lock`() = runTest(dispatcher) {
        val vm = viewModel(a2Recorder())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        vm.toggleString(4)
        advanceTimeBy(500)
        assertEquals(4, vm.detected().lockedString)

        vm.setTuning(Tunings.DROP_D)
        advanceTimeBy(500)

        assertNull(vm.detected().lockedString)
        assertEquals(1, vm.detected().targetString)
    }

    @Test
    fun `a new notation applies while listening`() = runTest(dispatcher) {
        val vm = viewModel(a2Recorder())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)
        assertEquals("A", vm.detected().noteName)

        settings.setNotation(Notation.SOLFEGE)
        advanceTimeBy(100)

        assertEquals("La", vm.detected().noteName)
        assertEquals(Notation.SOLFEGE, vm.detected().notation)
    }

    @Test
    fun `note names follow the app's language until the player picks a notation`() = runTest(dispatcher) {
        val vm = TunerViewModel(a2Recorder(), settings, tones, StandardTestDispatcher(testScheduler), language = "fr")
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)
        assertEquals("French players get Do Re Mi", "La", vm.detected().noteName)

        vm.setLanguage("de")
        advanceTimeBy(100)
        assertEquals(Notation.GERMAN, vm.detected().notation)

        settings.setNotation(Notation.FLATS)
        advanceTimeBy(100)
        assertEquals("the player's own pick wins", Notation.FLATS, vm.detected().notation)
        vm.setLanguage("it")
        advanceTimeBy(100)
        assertEquals("and keeps winning", Notation.FLATS, vm.detected().notation)

        settings.setNotation(null)
        advanceTimeBy(100)
        assertEquals("Automatic follows the language again", Notation.SOLFEGE, vm.detected().notation)
    }

    @Test
    fun `the in-tune precision applies while listening, and the hint follows it`() = runTest(dispatcher) {
        // 110 Hz is 3.9 cents flat of A2 once A4 is 441 Hz: in tune when relaxed, not when precise.
        settings.setA4(441f)
        val recorder = a2Recorder()
        val vm = viewModel(recorder)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(1_000)
        assertEquals(-3.9f, vm.detected().cents, 0.5f)
        assertTrue(vm.detected().inTune)
        assertEquals(TunerHint.IN_TUNE, vm.detected().hint)

        settings.setInTunePrecision(InTunePrecision.PRECISE)
        advanceTimeBy(1_000)

        assertFalse(vm.detected().inTune)
        assertEquals(TunerHint.TUNE_UP, vm.detected().hint)
        assertEquals(1, recorder.startCount)
    }

    @Test
    fun `keepScreenOn follows the setting`() = runTest(dispatcher) {
        val vm = viewModel(a2Recorder())
        backgroundScope.launch { vm.keepScreenOn.collect {} }
        runCurrent()
        assertTrue("on by default", vm.keepScreenOn.value)

        settings.setKeepScreenOn(false)
        runCurrent()
        assertFalse(vm.keepScreenOn.value)
    }

    @Test
    fun `inTunePrecision follows the setting, Relaxed by default, for the meter's in-tune window`() = runTest(dispatcher) {
        val vm = viewModel(a2Recorder())
        backgroundScope.launch { vm.inTunePrecision.collect {} }
        runCurrent()
        assertEquals(InTunePrecision.RELAXED, vm.inTunePrecision.value)

        settings.setInTunePrecision(InTunePrecision.PRECISE)
        runCurrent()
        assertEquals(InTunePrecision.PRECISE, vm.inTunePrecision.value)
    }

    @Test
    fun `the string tick-off follows the setting, off by default`() = runTest(dispatcher) {
        val vm = viewModel(a2Recorder())
        backgroundScope.launch { vm.stringTickOff.collect {} }
        runCurrent()
        assertFalse("off by default", vm.stringTickOff.value)

        settings.setStringTickOff(true)
        runCurrent()
        assertTrue(vm.stringTickOff.value)
    }

    @Test
    fun `what goes under the meter follows the setting, the words by default`() = runTest(dispatcher) {
        val vm = viewModel(a2Recorder())
        backgroundScope.launch { vm.underMeter.collect {} }
        runCurrent()
        assertEquals("the words by default", UnderMeter.WORDS, vm.underMeter.value)

        settings.setUnderMeter(UnderMeter.CENTS)
        runCurrent()
        assertEquals(UnderMeter.CENTS, vm.underMeter.value)
    }

    @Test
    fun `vibrateInTune follows the setting`() = runTest(dispatcher) {
        val vm = viewModel(a2Recorder())
        backgroundScope.launch { vm.vibrateInTune.collect {} }
        runCurrent()
        assertTrue("on by default", vm.vibrateInTune.value)

        settings.setVibrateInTune(false)
        runCurrent()
        assertFalse(vm.vibrateInTune.value)
    }

    // Reference tone (Phase 17): a long-press plays a string, a tap stops it, the mic is ignored meanwhile.

    @Test
    fun `a long-press plays the string's note at the current A4 and marks it sounding`() = runTest(dispatcher) {
        val vm = viewModel(a2Recorder())
        runCurrent()

        vm.playString(1)
        runCurrent()
        assertEquals(listOf(110f), tones.played)
        assertEquals(1, vm.soundingString.value)

        settings.setA4(432f)
        settings.setTuning(Tunings.DROP_D)
        runCurrent()
        vm.playString(0)
        runCurrent()
        // D2 at A4 = 432 Hz: 432 × 2^((38 − 69) / 12).
        assertEquals(432.0 * Math.pow(2.0, -31.0 / 12.0), tones.played.last().toDouble(), 0.01)
        assertEquals(0, vm.soundingString.value)

        tones.finish()
        runCurrent()
        assertNull("the tone died away", vm.soundingString.value)
    }

    @Test
    fun `a string is played in the picked instrument's voice`() = runTest(dispatcher) {
        val vm = viewModel(a2Recorder())
        runCurrent()

        vm.playString(0)
        runCurrent()
        assertEquals(listOf(StringVoices.STEEL_GUITAR), tones.voices)

        vm.setInstrument(Instruments.VIOLIN)
        runCurrent()
        vm.playString(3)
        runCurrent()
        assertEquals(StringVoices.VIOLIN, tones.voices.last())
        // E5 at A4 = 440 Hz.
        assertEquals(440.0 * Math.pow(2.0, 7.0 / 12.0), tones.played.last().toDouble(), 0.01)

        vm.setInstrument(Instruments.BASS_4)
        runCurrent()
        vm.playString(0)
        runCurrent()
        assertEquals(StringVoices.ELECTRIC_BASS, tones.voices.last())
        assertEquals(listOf(false, false, false), tones.octavePairs)
    }

    @Test
    fun `a 12-string's course plays as its pair, an octave apart on the lower four`() = runTest(dispatcher) {
        val vm = viewModel(a2Recorder())
        runCurrent()
        vm.setInstrument(Instruments.GUITAR_12)
        runCurrent()
        for (string in 0 until 6) {
            vm.playString(string)
            runCurrent()
        }
        assertEquals(List(6) { StringVoices.TWELVE_STRING }, tones.voices)
        assertEquals(listOf(true, true, true, true, false, false), tones.octavePairs)
        // The course's own note, E2 for the low pair: the voice adds the octave string.
        assertEquals(82.41, tones.played.first().toDouble(), 0.01)
    }

    @Test
    fun `a tap while a tone sounds only stops it, and the next tap locks as before`() = runTest(dispatcher) {
        val vm = viewModel(a2Recorder())
        backgroundScope.launch { vm.state.collect {} }
        vm.onPermissionChanged(true)
        runCurrent()
        vm.playString(2)
        runCurrent()

        vm.toggleString(4)
        runCurrent()
        assertEquals(1, tones.stopCount)
        assertFalse(tones.isPlaying.value)
        assertNull(vm.soundingString.value)
        advanceTimeBy(1_000)
        assertEquals("the stopping tap locked nothing", null, (vm.state.value as? TunerUiState.Detected)?.lockedString)

        vm.toggleString(4)
        advanceTimeBy(200)
        assertEquals(4, vm.detected().lockedString)
    }

    @Test
    fun `while a tone sounds the tuner keeps the mic open but shows no reading`() = runTest(dispatcher) {
        val recorder = a2Recorder()
        val vm = viewModel(recorder)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)
        vm.detected()

        vm.playString(1)
        advanceTimeBy(1_000)
        assertTrue("listening while the tone sounds, was ${vm.state.value}", vm.state.value is TunerUiState.Listening)
        assertTrue(recorder.isRecording.value)
        assertEquals(1, recorder.startCount)

        // The tone has left the speaker; its echo may still be in the room for a moment.
        tones.finish()
        advanceTimeBy((TunerViewModel.TONE_TAIL_SECONDS * 1000 / 2).toLong())
        assertTrue("still listening in the tail, was ${vm.state.value}", vm.state.value is TunerUiState.Listening)

        advanceTimeBy(500)
        assertEquals("A", vm.detected().noteName)
        assertEquals(1, recorder.startCount)
    }

    @Test
    fun `a new tuning or chromatic mode stops the tone`() = runTest(dispatcher) {
        val vm = viewModel(a2Recorder())
        runCurrent()
        vm.playString(0)
        runCurrent()

        vm.setTuning(Tunings.DROP_D)
        runCurrent()
        assertFalse("a new tuning stops it", tones.isPlaying.value)
        assertNull(vm.soundingString.value)

        vm.playString(0)
        runCurrent()
        vm.setMode(TunerMode.CHROMATIC)
        runCurrent()
        assertFalse("chromatic mode stops it", tones.isPlaying.value)
    }

    @Test
    fun `stopTone stops a sounding tone and leaves a silent player alone`() = runTest(dispatcher) {
        val vm = viewModel(a2Recorder())
        runCurrent()
        vm.stopTone()
        assertEquals(0, tones.stopCount)

        vm.playString(5)
        runCurrent()
        vm.stopTone()
        runCurrent()
        assertEquals(1, tones.stopCount)
        assertNull(vm.soundingString.value)
    }

    @Test
    fun `a string the tuning doesn't have plays nothing`() = runTest(dispatcher) {
        val vm = viewModel(a2Recorder())
        runCurrent()
        vm.playString(6)
        runCurrent()
        assertTrue(tones.played.isEmpty())
        assertNull(vm.soundingString.value)
    }

    // --- Strings ticked off as they're tuned (Phase 65) -------------------------------------------

    @Test
    fun `a string held in tune is ticked off`() = runTest(dispatcher) {
        settings.setStringTickOff(true)
        val vm = viewModel(a2Recorder())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        assertNull("nothing ticked before a note", vm.tunedStrings.value)

        advanceTimeBy(1_000)
        assertEquals(TunedStrings(Tunings.STANDARD, 0b10L), vm.tunedStrings.value)
    }

    @Test
    fun `a note sliding through the string ticks nothing`() = runTest(dispatcher) {
        settings.setStringTickOff(true)
        // From 100 to 120 Hz in one second: through A2's in-tune window (±5 cents) in about 30 ms, then silence.
        val glide = FloatArray(48_000)
        var phase = 0.0
        for (i in glide.indices) {
            phase += (100.0 + 20.0 * i / glide.size) / 48_000
            glide[i] = (2.0 * (phase - kotlin.math.floor(phase)) - 1.0).toFloat() * 0.5f
        }
        val vm = viewModel(FakeAudioRecorder(glide + FloatArray(48_000)))
        vm.onPermissionChanged(true)
        val seen = mutableListOf<TunerUiState>()
        backgroundScope.launch { vm.state.collect { seen += it } }

        advanceTimeBy(2_000)
        assertTrue("the needle passed 0", seen.any { it is TunerUiState.Detected && it.targetString == 1 && abs(it.cents) < 5f })
        assertFalse(seen.any { it is TunerUiState.Detected && it.inTune })
        assertNull(vm.tunedStrings.value)
    }

    @Test
    fun `chromatic mode ticks nothing`() = runTest(dispatcher) {
        settings.setStringTickOff(true)
        val vm = viewModel(a2Recorder())
        vm.setMode(TunerMode.CHROMATIC)
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }

        advanceTimeBy(1_000)
        assertTrue(vm.detected().inTune)
        assertNull(vm.tunedStrings.value)
    }

    @Test
    fun `a new tuning or A4 clears the ticks`() = runTest(dispatcher) {
        settings.setStringTickOff(true)
        val vm = viewModel(a2Recorder())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(1_000)
        assertEquals(TunedStrings(Tunings.STANDARD, 0b10L), vm.tunedStrings.value)

        // Open G has no A string: the A2 heard is no string's note in tune.
        vm.setTuning(Tunings.OPEN_G)
        advanceTimeBy(1_000)
        assertNull(vm.tunedStrings.value)

        vm.setTuning(Tunings.STANDARD)
        advanceTimeBy(1_000)
        assertEquals(TunedStrings(Tunings.STANDARD, 0b10L), vm.tunedStrings.value)

        // 110 Hz is 7.85 cents flat of A2 once A4 is 442 Hz.
        settings.setA4(442f)
        advanceTimeBy(1_000)
        assertNull(vm.tunedStrings.value)
    }

    @Test
    fun `the ticks stay while a tool is on, and go when the tuner is left`() = runTest(dispatcher) {
        settings.setStringTickOff(true)
        val vm = viewModel(a2Recorder())
        vm.onPermissionChanged(true)
        val ui = launch { vm.state.collect {} }
        advanceTimeBy(1_000)
        val ticked = TunedStrings(Tunings.STANDARD, 0b10L)
        assertEquals(ticked, vm.tunedStrings.value)

        vm.setIntonationCheck(true)
        advanceTimeBy(500)
        vm.setIntonationCheck(false)
        advanceTimeBy(100)
        assertEquals("kept for the tuner, which shows them again", ticked, vm.tunedStrings.value)

        ui.cancel()
        advanceTimeBy(4_900)
        assertEquals("kept for quick returns, e.g. rotation", ticked, vm.tunedStrings.value)
        advanceTimeBy(200)
        runCurrent()
        assertNull("gone once the tuner stopped listening", vm.tunedStrings.value)
    }

    @Test
    fun `a ticked string played clearly out of tune loses its tick, but not for a moment's wobble`() = runTest(dispatcher) {
        settings.setStringTickOff(true)
        fun tone(hz: Float, seconds: Float) = TestSignals.sawtooth(hz, length = (48_000 * seconds).toInt())
        // 20 cents sharp of A2: well past the in-tune window's 7-cent exit margin.
        val sharp = 110f * 2f.pow(20f / 1200f)
        val signal = tone(110f, 1.5f) + tone(sharp, 0.5f) + tone(110f, 1f) + tone(sharp, 2.5f) + tone(110f, 1.5f)
        val vm = viewModel(FakeAudioRecorder(signal))
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }
        val ticked = TunedStrings(Tunings.STANDARD, 0b10L)

        advanceTimeBy(1_500)
        assertEquals(ticked, vm.tunedStrings.value)
        advanceTimeBy(1_400)
        assertEquals("half a second off and back keeps the tick", ticked, vm.tunedStrings.value)

        advanceTimeBy(2_400)
        assertEquals(1, vm.detected().targetString)
        assertTrue(vm.detected().cents > 7f)
        assertNull("off for over a second: the tick goes", vm.tunedStrings.value)

        advanceTimeBy(1_400)
        assertEquals("back in tune: ticked again", ticked, vm.tunedStrings.value)
    }

    @Test
    fun `with the string tick-off off, the default, no string is ticked`() = runTest(dispatcher) {
        val vm = viewModel(a2Recorder())
        vm.onPermissionChanged(true)
        backgroundScope.launch { vm.state.collect {} }

        advanceTimeBy(1_000)
        assertTrue(vm.detected().inTune)
        assertNull(vm.tunedStrings.value)

        settings.setStringTickOff(true)
        advanceTimeBy(100)
        assertEquals("kept meanwhile, so turning it on shows them", TunedStrings(Tunings.STANDARD, 0b10L), vm.tunedStrings.value)
    }
}
