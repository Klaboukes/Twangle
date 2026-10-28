package io.github.klaboukes.twangle.feature.eartraining

import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.audio.FakeTonePlayer
import io.github.klaboukes.twangle.core.dsp.eartraining.EarTrainingVisit
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteChoice
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteQuestion
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteQuestions
import io.github.klaboukes.twangle.core.dsp.eartraining.Tally
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/**
 * Ear training's ViewModel (Phase 55) with a fake microphone and a fake speaker, in virtual time: the
 * questions start at once in the instrument's voice, the microphone is held only while Play it back
 * is shown and allowed, the question's own sound is never taken for the answer (while it sounds and
 * for a moment after), the tally counts this visit only, the next question follows an answer by itself,
 * a missed one comes back, and the visit ends when the tool is left.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EarTrainingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val tone = FakeTonePlayer()
    private val recorder = FakeAudioRecorder.silence()

    /** What the microphone hears: the question's tone while it sounds (the speaker is near), else what the player plays. */
    private val ears = FakeEars(recorder, tone)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(settings: AppSettings = AppSettings(), repository: FakeSettingsRepository = FakeSettingsRepository(settings)) =
        EarTrainingViewModel(ears, tone, repository, settings, dspDispatcher = dispatcher, random = { Random(SEED) })

    private fun noteAt(frequencyHz: Float, a4Hz: Float = NoteMapper.DEFAULT_A4_HZ): Note = Note(Math.round(NoteMapper(a4Hz).midiOf(frequencyHz)))

    /** Lets the question's tone fade by itself, then waits out the tail after it. */
    private fun TestScope.fade() {
        tone.finish()
        advanceTimeBy(TAIL_AND_SOME_MILLIS)
        runCurrent()
    }

    @Test
    fun `questions start at once, in the instrument's voice, from the notes chosen`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.show()
        runCurrent()
        val question = vm.state.value.notes
        assertNotNull(question)
        assertEquals(Exercise.PLAY_IT_BACK, vm.state.value.exercise)
        assertEquals("one note asked", 1, question!!.notes.size)
        assertTrue("an open string", question.notes.single() in NoteChoice.OPEN_STRINGS.notesOn(Tunings.STANDARD))
        assertEquals("it sounds at once", listOf(NoteMapper().frequencyOf(question.notes.single())), tone.played)
        assertEquals(listOf(Instruments.GUITAR.voice), tone.voices)
        assertEquals("nothing answered yet", Tally(), vm.state.value.tally)

        // The phone turns: the screen shows again, the visit goes on and nothing sounds again.
        vm.show()
        runCurrent()
        assertEquals(question, vm.state.value.notes)
        assertEquals(1, tone.played.size)
    }

    @Test
    fun `the question follows the A4 and the instrument picked`() = runTest(dispatcher) {
        val vm = viewModel(AppSettings(a4Hz = 432f, instrument = Instruments.BASS_4, tuning = Instruments.BASS_4.defaultTuning))
        vm.show()
        runCurrent()
        val note = vm.state.value.notes!!.notes.single()
        assertTrue("a bass's open string", note in NoteChoice.OPEN_STRINGS.notesOn(Instruments.BASS_4.defaultTuning))
        assertEquals(NoteMapper(432f).frequencyOf(note), tone.played.single(), 0.001f)
        assertEquals(Instruments.BASS_4.voice, tone.voices.single())
        assertEquals("the longer window for its low strings", PitchRange.LOW, run {
            vm.setMicAllowed(true)
            backgroundScope.launch { vm.heard.collect {} }
            advanceTimeBy(100)
            runCurrent()
            ears.lastRange
        })
    }

    @Test
    fun `the microphone is held only while Play it back is shown and allowed, and let go 5 s after`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.show()
        val shown = backgroundScope.launch { vm.heard.collect {} }
        runCurrent()
        assertFalse("not before it is allowed", recorder.isRecording.value)

        vm.setMicAllowed(true)
        runCurrent()
        assertTrue("open while shown", recorder.isRecording.value)

        vm.pickExercise(Exercise.INTERVALS)
        runCurrent()
        assertFalse("Intervals doesn't listen", recorder.isRecording.value)
        vm.pickExercise(Exercise.PLAY_IT_BACK)
        runCurrent()
        assertTrue("Play it back listens again", recorder.isRecording.value)

        shown.cancel()
        advanceTimeBy(4_900)
        runCurrent()
        assertTrue("kept for a turning phone", recorder.isRecording.value)
        advanceTimeBy(200)
        runCurrent()
        assertFalse("let go 5 s after the screen went", recorder.isRecording.value)
    }

    @Test
    fun `the question's own sound is never taken for the answer`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setMicAllowed(true)
        val heard = mutableListOf<Note?>()
        backgroundScope.launch { vm.heard.collect { heard += it } }
        vm.show()
        runCurrent()
        val asked = vm.state.value.notes!!.notes.single()
        // The tone rings for a few seconds and the microphone hears it all along.
        advanceTimeBy(3_000)
        runCurrent()
        assertNull("nothing heard while the question sounds", vm.heard.value)

        // It has faded, but its echo may still be in the room: still nothing for a moment.
        tone.finish()
        advanceTimeBy(100)
        runCurrent()
        assertNull("nothing heard just after it faded", vm.heard.value)
        assertTrue("the asked note was never shown as heard", heard.none { it == asked })

        ears.playedHz = NoteMapper().frequencyOf(Note.of(asked.pitchClass, asked.octave))
        advanceTimeBy(TAIL_AND_SOME_MILLIS)
        runCurrent()
        assertEquals("the player's note once it has gone", asked, vm.heard.value)

        // ▶ sounds it again: from then on the microphone is set aside again.
        ears.playedHz = 0f
        vm.replay()
        runCurrent()
        assertEquals(2, tone.played.size)
        assertEquals(tone.played[0], tone.played[1])
        advanceTimeBy(500)
        runCurrent()
        assertNull(vm.heard.value)
    }

    @Test
    fun `the player's note is heard as the tuner hears it`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setMicAllowed(true)
        backgroundScope.launch { vm.heard.collect {} }
        vm.show()
        runCurrent()
        fade()
        assertNull("nothing played yet", vm.heard.value)
        ears.playedHz = 184.0f // F♯3, 9 cents flat
        advanceTimeBy(500)
        runCurrent()
        assertEquals(noteAt(184.0f), vm.heard.value)
        assertEquals(Note.of(io.github.klaboukes.twangle.core.dsp.music.PitchClass.F_SHARP, 3), vm.heard.value)
        ears.playedHz = 0f
        advanceTimeBy(1_000)
        runCurrent()
        assertNull("gone once the string is quiet", vm.heard.value)
    }

    @Test
    fun `an answer counts in this visit's tally and the next question comes by itself after 1½ s`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.show()
        runCurrent()
        val first = vm.state.value.notes
        vm.answer(true)
        assertEquals(Tally(right = 1, asked = 1), vm.state.value.tally)
        assertTrue(vm.state.value.answered)
        vm.answer(false)
        assertEquals("one answer per question", Tally(right = 1, asked = 1), vm.state.value.tally)

        advanceTimeBy(EarTrainingViewModel.NEXT_DELAY_MILLIS - 100)
        runCurrent()
        assertEquals("not yet", first, vm.state.value.notes)
        assertEquals(1, tone.played.size)
        advanceTimeBy(200)
        runCurrent()
        assertFalse(vm.state.value.answered)
        assertNotEquals("never the same twice in a row", first, vm.state.value.notes)
        assertEquals("the next one sounds", 2, tone.played.size)

        vm.answer(false)
        assertEquals(Tally(right = 1, asked = 2), vm.state.value.tally)
    }

    @Test
    fun `a missed question comes back a few questions later, and the visit repeats with its seed`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setNoteChoice(NoteChoice.ALL_TO_12TH)
        vm.show()
        runCurrent()
        val asked = mutableListOf<NoteQuestion>()
        asked += vm.state.value.notes!!
        vm.answer(false)
        repeat(EarTrainingVisit.MISSED_RETURN_MAX) {
            advanceTimeBy(EarTrainingViewModel.NEXT_DELAY_MILLIS)
            runCurrent()
            asked += vm.state.value.notes!!
            vm.answer(true)
        }
        val back = asked.drop(1).indexOf(asked[0]) + 1
        assertTrue("back as question ${back + 1} of $asked", back in EarTrainingVisit.MISSED_RETURN_MIN..EarTrainingVisit.MISSED_RETURN_MAX)

        // The same seed, the same visit: a test can repeat it question for question.
        val again = EarTrainingVisit(NoteQuestions(Tunings.STANDARD, NoteChoice.ALL_TO_12TH), Random(SEED))
        assertEquals(asked[0], again.next())
    }

    @Test
    fun `leaving the tool ends the visit, its tally and questions gone, and the next visit starts afresh`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.show()
        runCurrent()
        vm.answer(true)
        vm.endVisit()
        runCurrent()
        assertEquals(Tally(), vm.state.value.tally)
        assertNull(vm.state.value.notes)
        assertTrue("the tone stopped", tone.stopCount >= 1 || !tone.isPlaying.value)
        advanceTimeBy(EarTrainingViewModel.NEXT_DELAY_MILLIS * 2)
        runCurrent()
        assertNull("no question comes by itself after it", vm.state.value.notes)
        assertEquals(1, tone.played.size)

        vm.show()
        runCurrent()
        assertNotNull("a new visit asks at once", vm.state.value.notes)
        assertEquals(2, tone.played.size)
        assertEquals(Tally(), vm.state.value.tally)
    }

    @Test
    fun `each exercise keeps its own place in the visit, and those without questions yet ask nothing`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.show()
        runCurrent()
        vm.answer(true)
        val tally = vm.state.value.tally
        vm.pickExercise(Exercise.CHORD_TYPE)
        runCurrent()
        assertEquals(Exercise.CHORD_TYPE, vm.state.value.exercise)
        assertNull(vm.state.value.notes)
        assertEquals(Tally(), vm.state.value.tally)
        vm.replay()
        advanceTimeBy(EarTrainingViewModel.NEXT_DELAY_MILLIS * 2)
        runCurrent()
        assertEquals("nothing sounds", 1, tone.played.size)

        vm.pickExercise(Exercise.PLAY_IT_BACK)
        runCurrent()
        assertEquals("its tally kept", tally, vm.state.value.tally)
        assertNotNull("answered before it went: a new question", vm.state.value.notes)
        assertEquals(2, tone.played.size)
    }

    @Test
    fun `a choice of notes that no longer offers the question asks a new one at once`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setNoteChoice(NoteChoice.ALL_TO_12TH)
        vm.show()
        runCurrent()
        val openStrings = NoteChoice.OPEN_STRINGS.notesOn(Tunings.STANDARD)
        var tries = 0
        while (vm.state.value.notes!!.notes.single() in openStrings) {
            vm.answer(true)
            advanceTimeBy(EarTrainingViewModel.NEXT_DELAY_MILLIS)
            runCurrent()
            check(++tries < 50) { "Every question an open string" }
        }
        val played = tone.played.size
        vm.setNoteChoice(NoteChoice.OPEN_STRINGS)
        runCurrent()
        assertEquals(NoteChoice.OPEN_STRINGS, vm.state.value.noteChoice)
        assertTrue(vm.state.value.notes!!.notes.single() in openStrings)
        assertEquals("it sounds at once", played + 1, tone.played.size)

        // A choice that still offers it changes nothing now.
        vm.setNoteChoice(NoteChoice.ALL_TO_12TH)
        runCurrent()
        assertEquals(played + 1, tone.played.size)
    }

    @Test
    fun `another instrument asks its own notes`() = runTest(dispatcher) {
        val repository = FakeSettingsRepository()
        val vm = viewModel(repository = repository)
        vm.show()
        runCurrent()
        repository.setInstrument(Instruments.BASS_4)
        runCurrent()
        val note = vm.state.value.notes!!.notes.single()
        assertTrue("a bass's open string", note in NoteChoice.OPEN_STRINGS.notesOn(Instruments.BASS_4.defaultTuning))
        assertEquals("in the bass's voice", Instruments.BASS_4.voice, tone.voices.last())
    }

    @Test
    fun `a hidden screen silences the question, and a refusal of the microphone is saved once`() = runTest(dispatcher) {
        val repository = FakeSettingsRepository()
        val vm = viewModel(repository = repository)
        vm.show()
        runCurrent()
        assertTrue(tone.isPlaying.value)
        vm.stopSound()
        assertFalse(tone.isPlaying.value)

        vm.onMicRefused()
        runCurrent()
        assertTrue(repository.settings.value.micRefused)
        assertTrue(vm.micRefused.value)
    }

    /** The microphone for the tests: a frame per chunk of [recorder] (open only while collected), hearing the speaker's tone while it sounds. */
    private class FakeEars(private val recorder: FakeAudioRecorder, private val tone: FakeTonePlayer) : PitchListener {

        /** What the player plays, in Hz (0: nothing). */
        @Volatile
        var playedHz: Float = 0f

        /** How low the last frame was asked to listen. */
        @Volatile
        var lastRange: PitchRange? = null

        override val frameSeconds: Float = recorder.config.chunkSize.toFloat() / recorder.config.sampleRate

        override fun frames(range: () -> PitchRange): Flow<HeardFrame> = recorder.chunks().map {
            lastRange = range()
            val hz = if (tone.isPlaying.value) tone.played.last() else playedHz
            HeardFrame(hz, if (hz > 0f) 0.95f else 0f)
        }
    }

    private companion object {
        const val SEED = 55L

        /** The tail after a tone (0.3 s) and a few frames more. */
        const val TAIL_AND_SOME_MILLIS = 600L
    }
}
