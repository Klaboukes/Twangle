package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.audio.FakeScreenReaderSpeech
import io.github.klaboukes.twangle.core.audio.FakeTonePlayer
import io.github.klaboukes.twangle.core.audio.ScreenReaderSpeech
import io.github.klaboukes.twangle.core.dsp.testsignals.TestSignals
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
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
 * The tuner sets the microphone's frames aside while a screen reader's voice plays and for its tail (Phase 81), so
 * TalkBack saying a reading through the speaker is never read as a note and can't set off the next one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScreenReaderSpeechTunerTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** One second of A2 (110 Hz, the 5th string), looped: what the microphone hears, string or speech alike. */
    private fun a2Recorder() = FakeAudioRecorder(TestSignals.sawtooth(110f, length = 48_000), loop = true)

    private val tailMillis = (ScreenReaderSpeech.TAIL_SECONDS * 1000).toLong()

    private fun TestScope.viewModel(recorder: FakeAudioRecorder, speech: ScreenReaderSpeech) =
        TunerViewModel(
            recorder, FakeSettingsRepository(), FakeTonePlayer(), StandardTestDispatcher(testScheduler),
            screenReaderSpeech = speech,
        ).also { it.onPermissionChanged(true) }

    private fun TunerViewModel.shows(): TunerUiState = state.value

    @Test
    fun `while the voice speaks the tuner keeps the mic open but shows no reading, then reads again after the tail`() =
        runTest(dispatcher) {
            val recorder = a2Recorder()
            val speech = FakeScreenReaderSpeech()
            val vm = viewModel(recorder, speech)
            backgroundScope.launch { vm.state.collect {} }
            advanceTimeBy(500)
            assertTrue("a note before the voice, was ${vm.shows()}", vm.shows() is TunerUiState.Detected)

            // TalkBack starts saying the reading: the very next frames are set aside.
            speech.isSpeaking = true
            advanceTimeBy(50)
            assertTrue("listening as soon as the voice plays, was ${vm.shows()}", vm.shows() is TunerUiState.Listening)
            advanceTimeBy(1_500)
            assertTrue("listening while it speaks, was ${vm.shows()}", vm.shows() is TunerUiState.Listening)
            assertTrue(recorder.isRecording.value)
            assertEquals("the mic never reopens", 1, recorder.startCount)

            // The voice has stopped; its echo may still be in the room for a moment.
            speech.isSpeaking = false
            advanceTimeBy(tailMillis / 2)
            assertTrue("still listening in the tail, was ${vm.shows()}", vm.shows() is TunerUiState.Listening)
            advanceTimeBy(tailMillis / 2 - 40)
            assertTrue("still listening at the tail's end, was ${vm.shows()}", vm.shows() is TunerUiState.Listening)

            advanceTimeBy(500)
            val after = vm.shows()
            assertTrue("reads again after the tail, was $after", after is TunerUiState.Detected)
            assertEquals("A", (after as TunerUiState.Detected).noteName)
            assertEquals(1, recorder.startCount)
        }

    @Test
    fun `a voice already speaking when the tuner opens is set aside from the first frame`() = runTest(dispatcher) {
        val vm = viewModel(a2Recorder(), FakeScreenReaderSpeech(speaking = true))
        val states = mutableListOf<TunerUiState>()
        backgroundScope.launch { vm.state.collect { states += it } }
        advanceTimeBy(2_000)
        assertTrue("no note while it speaks: $states", states.none { it is TunerUiState.Detected })
    }

    @Test
    fun `the voice is watched only while the tuner listens`() = runTest(dispatcher) {
        val speech = FakeScreenReaderSpeech()
        val vm = viewModel(a2Recorder(), speech)
        assertEquals("nothing watched before the tuner shows", 0, speech.watchers)
        val collecting = backgroundScope.launch { vm.state.collect {} }
        advanceTimeBy(500)
        assertEquals(1, speech.watchers)

        collecting.cancel()
        advanceTimeBy(6_000)
        assertEquals("let go of with the microphone", 0, speech.watchers)
    }

    @Test
    fun `with no screen reader speaking the tuner shows what it showed before`() = runTest(dispatcher) {
        val withNone = mutableListOf<TunerUiState>()
        val withQuiet = mutableListOf<TunerUiState>()
        val none = viewModel(a2Recorder(), ScreenReaderSpeech.NONE)
        val quiet = viewModel(a2Recorder(), FakeScreenReaderSpeech())
        backgroundScope.launch { none.state.collect { withNone += it } }
        backgroundScope.launch { quiet.state.collect { withQuiet += it } }
        advanceTimeBy(2_000)
        assertTrue(withNone.any { it is TunerUiState.Detected })
        assertEquals(withNone, withQuiet)
        assertFalse(withQuiet.isEmpty())
    }
}
