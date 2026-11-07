package io.github.klaboukes.twangle.feature.eartraining

import io.github.klaboukes.twangle.core.audio.AudioRecorderException
import io.github.klaboukes.twangle.core.audio.FakeAudioRecorder
import io.github.klaboukes.twangle.core.audio.FakeScreenReaderSpeech
import io.github.klaboukes.twangle.core.audio.FakeTonePlayer
import io.github.klaboukes.twangle.core.audio.ScreenReaderSpeech
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.chord.ChordShapeFinder
import io.github.klaboukes.twangle.core.dsp.eartraining.ChordTypeQuestion
import io.github.klaboukes.twangle.core.dsp.eartraining.ChordTypeQuestions
import io.github.klaboukes.twangle.core.dsp.eartraining.EarTrainingVisit
import io.github.klaboukes.twangle.core.dsp.eartraining.Exercise
import io.github.klaboukes.twangle.core.dsp.eartraining.Interval
import io.github.klaboukes.twangle.core.dsp.eartraining.IntervalDirection
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteChoice
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteMatch
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteQuestion
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteQuestions
import io.github.klaboukes.twangle.core.dsp.eartraining.Tally
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tuning
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

    private fun TestScope.viewModel(
        settings: AppSettings = AppSettings(),
        repository: FakeSettingsRepository = FakeSettingsRepository(settings),
        speech: ScreenReaderSpeech = ScreenReaderSpeech.NONE,
    ) = EarTrainingViewModel(ears, tone, repository, settings, dspDispatcher = dispatcher, random = { Random(SEED) }, screenReaderSpeech = speech)

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
    fun `a screen reader's voice is never taken for the answer, nor its echo, and the player's note is after`() = runTest(dispatcher) {
        // Phase 81: TalkBack reads the screen through the speaker; the microphone hears its voice as the asked note.
        val speech = FakeScreenReaderSpeech()
        val vm = viewModel(speech = speech)
        vm.setMicAllowed(true)
        val heard = mutableListOf<Note?>()
        backgroundScope.launch { vm.heard.collect { heard += it } }
        vm.show()
        runCurrent()
        fade()
        val asked = vm.state.value.notes!!.notes.single()
        assertEquals("watched while it listens", 1, speech.watchers)

        speech.isSpeaking = true
        ears.playedHz = NoteMapper().frequencyOf(asked)
        advanceTimeBy(2_000)
        runCurrent()
        assertNull("nothing heard while the voice speaks", vm.heard.value)
        assertFalse("no answer judged", vm.state.value.answered)

        // The voice has stopped; its echo may still be in the room for a moment.
        speech.isSpeaking = false
        advanceTimeBy((ScreenReaderSpeech.TAIL_SECONDS * 1000).toLong() - 60)
        runCurrent()
        assertNull("nothing heard in the tail", vm.heard.value)
        assertFalse(vm.state.value.answered)
        assertTrue("the voice was never shown as heard", heard.none { it == asked })
        assertEquals(Tally(), vm.state.value.tally)

        // Now the player plays it.
        advanceTimeBy(60 + TAIL_AND_SOME_MILLIS + STEADY_MILLIS)
        runCurrent()
        assertTrue("found once the voice has gone", vm.state.value.answered)
        assertEquals(Tally(right = 1, asked = 1), vm.state.value.tally)
    }

    @Test
    fun `a voice speaking as the question sounds doesn't keep the answer from being judged`() = runTest(dispatcher) {
        val speech = FakeScreenReaderSpeech(speaking = true)
        val vm = viewModel(speech = speech)
        vm.setMicAllowed(true)
        backgroundScope.launch { vm.heard.collect {} }
        advanceTimeBy(500)
        // TalkBack still speaks as the question starts to sound, and stops before it fades.
        vm.show()
        runCurrent()
        advanceTimeBy(500)
        speech.isSpeaking = false
        advanceTimeBy(1_000)
        fade()
        val asked = vm.state.value.notes!!.notes.single()
        ears.playedHz = NoteMapper().frequencyOf(asked)
        advanceTimeBy(TAIL_AND_SOME_MILLIS + STEADY_MILLIS)
        runCurrent()
        assertTrue("the player's note is judged", vm.state.value.answered)
        assertEquals(Tally(right = 1, asked = 1), vm.state.value.tally)
    }

    @Test
    fun `with no screen reader speaking the player's note is heard as before`() = runTest(dispatcher) {
        val speech = FakeScreenReaderSpeech()
        val vm = viewModel(speech = speech)
        vm.setMicAllowed(true)
        val shown = backgroundScope.launch { vm.heard.collect {} }
        vm.show()
        runCurrent()
        fade()
        ears.playedHz = 184.0f
        advanceTimeBy(500)
        runCurrent()
        assertEquals(noteAt(184.0f), vm.heard.value)

        shown.cancel()
        advanceTimeBy(5_100)
        runCurrent()
        assertEquals("let go of with the microphone", 0, speech.watchers)
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

    @Test
    fun `a hidden screen holds the next question back, and asks it when the screen shows again`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.show()
        runCurrent()
        val first = vm.state.value.notes
        vm.answer(true)
        // Home pressed (or the phone locked) at once: the next question is due 1½ s later, but must not sound in the background.
        vm.stopSound()
        advanceTimeBy(EarTrainingViewModel.NEXT_DELAY_MILLIS * 10)
        runCurrent()
        assertEquals("no question sounds while hidden", 1, tone.played.size)
        assertEquals(first, vm.state.value.notes)
        assertTrue("still answered", vm.state.value.answered)

        // The screen shows again: the next question comes 1½ s later, as after an answer.
        vm.shownAgain()
        advanceTimeBy(EarTrainingViewModel.NEXT_DELAY_MILLIS - 100)
        runCurrent()
        assertEquals("not at once", 1, tone.played.size)
        advanceTimeBy(200)
        runCurrent()
        assertEquals("the next one sounds", 2, tone.played.size)
        assertFalse(vm.state.value.answered)
        assertNotEquals(first, vm.state.value.notes)

        // Shown again with the question still open: nothing extra is asked.
        vm.stopSound()
        vm.shownAgain()
        advanceTimeBy(EarTrainingViewModel.NEXT_DELAY_MILLIS * 2)
        runCurrent()
        assertEquals(2, tone.played.size)
    }

    @Test
    fun `a hidden screen holds back Intervals' next question too, after a wrong button`() = runTest(dispatcher) {
        val vm = viewModel(AppSettings(earExercise = Exercise.INTERVALS))
        vm.show()
        advanceTimeBy(EarTrainingViewModel.NOTE_SPACING_MILLIS + 100)
        runCurrent()
        val question = vm.state.value.interval!!
        val sounded = tone.played.size + tone.together.size
        vm.pickInterval(Interval.entries.first { !question.isRight(it) })
        vm.stopSound()
        advanceTimeBy(EarTrainingViewModel.NEXT_DELAY_AFTER_MISS_MILLIS * 4)
        runCurrent()
        assertEquals("nothing more sounds while hidden", sounded, tone.played.size + tone.together.size)
        assertEquals(question, vm.state.value.interval)
        assertTrue(vm.state.value.answered)

        vm.shownAgain()
        advanceTimeBy(EarTrainingViewModel.NEXT_DELAY_MILLIS + 100)
        runCurrent()
        assertFalse("the next one asked", vm.state.value.answered)
        assertTrue("and sounded", tone.played.size + tone.together.size > sounded)
    }

    @Test
    fun `the microphone stopping while Play it back listens says so, and it listens again when the screen comes back`() = runTest(dispatcher) {
        val vm = listening()
        val asked = vm.state.value.notes!!.notes.single()
        assertTrue(recorder.isRecording.value)
        assertFalse(vm.state.value.micStopped)

        // The phone locks: Android silences the microphone and the recorder ends with MicrophoneBusy.
        recorder.failure = AudioRecorderException.MicrophoneBusy()
        recorder.loop = false
        advanceTimeBy(100)
        runCurrent()
        assertTrue("the line says so", vm.state.value.micStopped)
        assertFalse("the microphone is let go", recorder.isRecording.value)
        assertNull(vm.heard.value)
        assertEquals("the question stays", asked, vm.state.value.notes!!.notes.single())

        // The phone is unlocked: it listens again, and hears the player.
        recorder.failure = null
        recorder.loop = true
        val opened = recorder.startCount
        vm.shownAgain()
        runCurrent()
        assertFalse("the line is gone", vm.state.value.micStopped)
        assertTrue("open again", recorder.isRecording.value)
        assertEquals(opened + 1, recorder.startCount)
        ears.playedHz = NoteMapper().frequencyOf(asked)
        advanceTimeBy(150 + STEADY_MILLIS)
        runCurrent()
        assertTrue("the answer heard", vm.state.value.answered && vm.state.value.allFound)
    }

    @Test
    fun `a microphone that fails to open says so instead of closing the app, and ▶ tries again`() = runTest(dispatcher) {
        recorder.signal = FloatArray(0)
        recorder.loop = false
        recorder.failure = AudioRecorderException.InitializationFailed("no microphone")
        val vm = viewModel()
        vm.setMicAllowed(true)
        backgroundScope.launch { vm.heard.collect {} }
        vm.show()
        runCurrent()
        assertTrue("the line says so", vm.state.value.micStopped)
        assertNotNull("the question is still asked", vm.state.value.notes)
        assertEquals(1, recorder.startCount)

        // Nothing tries again by itself.
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(1, recorder.startCount)

        // ▶ sounds the question again and opens the microphone again: still failing, still said, no crash.
        vm.replay()
        runCurrent()
        assertEquals(2, recorder.startCount)
        assertTrue(vm.state.value.micStopped)

        // Another exercise and back opens it afresh too; this time it opens.
        recorder.failure = null
        recorder.signal = FloatArray(recorder.config.chunkSize)
        recorder.loop = true
        vm.pickExercise(Exercise.INTERVALS)
        runCurrent()
        vm.pickExercise(Exercise.PLAY_IT_BACK)
        runCurrent()
        assertFalse(vm.state.value.micStopped)
        assertTrue(recorder.isRecording.value)
    }

    // ---------- Play it back: the player's answer ----------

    /** A listening Play it back, its first question sounded and faded. */
    private fun TestScope.listening(settings: AppSettings = AppSettings(), repository: FakeSettingsRepository = FakeSettingsRepository(settings)): EarTrainingViewModel {
        val vm = viewModel(settings, repository)
        vm.setMicAllowed(true)
        backgroundScope.launch { vm.heard.collect {} }
        vm.show()
        runCurrent()
        soundAndFade()
        return vm
    }

    /** Lets every note of the question start, then fade, then waits out the tail after it. */
    private fun TestScope.soundAndFade() {
        advanceTimeBy(EarTrainingViewModel.NOTE_SPACING_MILLIS * 4)
        runCurrent()
        fade()
    }

    /** The player plays [note] for [millis], then lets it ring out. */
    private fun TestScope.play(note: Note, millis: Long) {
        ears.playedHz = NoteMapper().frequencyOf(note)
        advanceTimeBy(millis)
        runCurrent()
        ears.playedHz = 0f
        advanceTimeBy(QUIET_MILLIS)
        runCurrent()
    }

    /** A note of another pitch class than [note], a whole tone up. */
    private fun wrongFor(note: Note): Note = Note(note.midi + 2)

    @Test
    fun `the right note held steady is right, and the next question comes by itself`() = runTest(dispatcher) {
        val vm = listening()
        val asked = vm.state.value.notes!!.notes.single()
        ears.playedHz = NoteMapper().frequencyOf(asked)
        advanceTimeBy(150)
        runCurrent()
        assertFalse("not held long enough yet", vm.state.value.answered)
        advanceTimeBy(STEADY_MILLIS)
        runCurrent()
        assertTrue("right once steady", vm.state.value.answered && vm.state.value.allFound)
        assertEquals(Tally(right = 1, asked = 1), vm.state.value.tally)
        assertFalse(vm.state.value.wrongHeld)

        // Held on, it doesn't count again; the next question sounds 1½ s after.
        advanceTimeBy(EarTrainingViewModel.NEXT_DELAY_MILLIS - 600)
        runCurrent()
        assertEquals(Tally(right = 1, asked = 1), vm.state.value.tally)
        ears.playedHz = 0f
        advanceTimeBy(700)
        runCurrent()
        assertFalse("a new question", vm.state.value.answered)
        assertEquals(0, vm.state.value.found)
        assertEquals(2, tone.played.size)
    }

    @Test
    fun `the right note in another octave counts`() = runTest(dispatcher) {
        val vm = listening(AppSettings(earNoteChoice = NoteChoice.ALL_TO_12TH))
        play(octaveAway(vm.state.value.notes!!.notes.single()), HOLD_STEADY_MILLIS)
        assertTrue("any octave counts", vm.state.value.allFound)
        assertEquals(Tally(right = 1, asked = 1), vm.state.value.tally)
    }

    @Test
    fun `only the exact note counts when that is asked`() = runTest(dispatcher) {
        val vm = listening(AppSettings(earNoteChoice = NoteChoice.ALL_TO_12TH, earNoteMatch = NoteMatch.EXACT))
        val asked = vm.state.value.notes!!.notes.single()
        play(octaveAway(asked), HOLD_SETTLED_MILLIS)
        assertFalse("not the exact note", vm.state.value.answered)
        assertTrue(vm.state.value.wrongHeld)
        play(asked, HOLD_STEADY_MILLIS)
        assertTrue(vm.state.value.allFound)
        assertEquals(Tally(right = 0, asked = 1), vm.state.value.tally)
    }

    private fun octaveAway(note: Note): Note = Note(if (note.midi >= 52) note.midi - 12 else note.midi + 12)

    @Test
    fun `a wrong note held is not right, the question can be heard again, and it comes back missed`() = runTest(dispatcher) {
        val vm = listening(AppSettings(earNoteChoice = NoteChoice.ALL_TO_12TH))
        val question = vm.state.value.notes!!
        val asked = question.notes.single()

        // Passing a wrong note on the way (under 1 s) says nothing.
        play(wrongFor(asked), HOLD_STEADY_MILLIS)
        assertFalse(vm.state.value.wrongHeld)

        play(wrongFor(asked), HOLD_SETTLED_MILLIS)
        assertTrue("Not that one", vm.state.value.wrongHeld)
        assertFalse("the question stays", vm.state.value.answered)
        assertEquals("nothing counted yet", Tally(), vm.state.value.tally)
        assertEquals(question, vm.state.value.notes)

        // ▶: the question sounds again, "Not that one" goes, and its own sound isn't taken for the answer.
        vm.replay()
        runCurrent()
        assertEquals(2, tone.played.size)
        assertFalse(vm.state.value.wrongHeld)
        advanceTimeBy(3_000)
        runCurrent()
        assertFalse("its sound is no answer", vm.state.value.answered)
        fade()

        play(asked, HOLD_STEADY_MILLIS)
        assertTrue("found in the end", vm.state.value.allFound)
        assertEquals("but missed first", Tally(right = 0, asked = 1), vm.state.value.tally)

        // It comes back a few questions later.
        val asks = mutableListOf<NoteQuestion>()
        repeat(EarTrainingVisit.MISSED_RETURN_MAX) {
            advanceTimeBy(EarTrainingViewModel.NEXT_DELAY_MILLIS)
            runCurrent()
            soundAndFade()
            val now = vm.state.value.notes!!
            asks += now
            play(now.notes.single(), HOLD_STEADY_MILLIS)
            assertTrue(vm.state.value.answered)
        }
        assertTrue("back as the 3rd to 5th question after it: $asks", question in asks.drop(EarTrainingVisit.MISSED_RETURN_MIN - 1))
        assertEquals(Tally(right = EarTrainingVisit.MISSED_RETURN_MAX, asked = EarTrainingVisit.MISSED_RETURN_MAX + 1), vm.state.value.tally)
    }

    @Test
    fun `nothing is counted while the question plays, even the right note`() = runTest(dispatcher) {
        val vm = viewModel(AppSettings(earNotesInRow = 2))
        vm.setMicAllowed(true)
        backgroundScope.launch { vm.heard.collect {} }
        vm.show()
        runCurrent()
        // The microphone hears the question's own notes, each the right one, for as long as they ring.
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(2, tone.played.size)
        assertEquals("not one note found", 0, vm.state.value.found)
        assertFalse(vm.state.value.wrongHeld)
        fade()
        assertEquals(0, vm.state.value.found)
        assertEquals(Tally(), vm.state.value.tally)
    }

    @Test
    fun `two to four notes in a row are found one after another, in order`() = runTest(dispatcher) {
        val repository = FakeSettingsRepository()
        val vm = listening(repository = repository)
        vm.setNotesInRow(3)
        runCurrent()
        assertEquals("saved", 3, repository.settings.value.earNotesInRow)
        val question = vm.state.value.notes!!
        assertEquals("a question that long at once", 3, question.notes.size)
        advanceTimeBy(EarTrainingViewModel.NOTE_SPACING_MILLIS * 3)
        runCurrent()
        assertEquals("its notes sound one after another, after the first question's one", 4, tone.played.size)
        assertEquals(question.notes.map { NoteMapper().frequencyOf(it) }, tone.played.takeLast(3))
        fade()

        play(question.notes[1], HOLD_SETTLED_MILLIS)
        assertEquals("the second first is no use", 0, vm.state.value.found)
        play(question.notes[0], HOLD_STEADY_MILLIS)
        assertEquals(1, vm.state.value.found)
        play(question.notes[1], HOLD_STEADY_MILLIS)
        assertEquals(2, vm.state.value.found)
        assertFalse(vm.state.value.answered)
        play(question.notes[2], HOLD_STEADY_MILLIS)
        assertTrue(vm.state.value.allFound && vm.state.value.answered)
        assertEquals(Tally(right = 0, asked = 1), vm.state.value.tally)

        vm.setNotesInRow(9)
        runCurrent()
        assertEquals("four at most", 4, vm.state.value.notesInRow)
        assertEquals(4, vm.state.value.notes!!.notes.size)
    }

    @Test
    fun `the exercise and the choices of what is asked are saved and come back`() = runTest(dispatcher) {
        val repository = FakeSettingsRepository()
        val vm = viewModel(repository = repository)
        vm.show()
        runCurrent()
        vm.setNoteChoice(NoteChoice.NATURALS_TO_5TH)
        vm.setNotesInRow(2)
        vm.setNoteMatch(NoteMatch.EXACT)
        vm.pickExercise(Exercise.INTERVALS)
        runCurrent()
        val saved = repository.settings.value
        assertEquals(Exercise.INTERVALS, saved.earExercise)
        assertEquals(NoteChoice.NATURALS_TO_5TH, saved.earNoteChoice)
        assertEquals(2, saved.earNotesInRow)
        assertEquals(NoteMatch.EXACT, saved.earNoteMatch)

        // The next time the tool opens, it starts where the player left it.
        val again = viewModel(saved, FakeSettingsRepository(saved))
        assertEquals(Exercise.INTERVALS, again.state.value.exercise)
        again.pickExercise(Exercise.PLAY_IT_BACK)
        runCurrent()
        val state = again.state.value
        assertEquals(Triple(NoteChoice.NATURALS_TO_5TH, 2, NoteMatch.EXACT), Triple(state.noteChoice, state.notesInRow, state.noteMatch))
        assertEquals(2, state.notes!!.notes.size)
        assertTrue(state.notes!!.notes.all { it in NoteChoice.NATURALS_TO_5TH.notesOn(Tunings.STANDARD) })
        assertEquals(Tally(), state.tally)
    }

    // ---- Intervals ----

    private fun TestScope.intervals(settings: AppSettings = AppSettings(earExercise = Exercise.INTERVALS), repository: FakeSettingsRepository = FakeSettingsRepository(settings)): EarTrainingViewModel {
        val vm = viewModel(settings, repository)
        vm.show()
        runCurrent()
        return vm
    }

    private fun frequenciesOf(first: Note, second: Note? = null): List<Float> = listOfNotNull(first, second).map { NoteMapper().frequencyOf(it) }

    @Test
    fun `Intervals asks at once, two notes up in the instrument's voice, and never opens the microphone`() = runTest(dispatcher) {
        val vm = intervals()
        val question = vm.state.value.interval
        assertNotNull(question)
        assertNull("no Play it back question", vm.state.value.notes)
        assertTrue("one of the intervals chosen", question!!.interval in Interval.DEFAULT)
        assertEquals(IntervalDirection.UP, question.direction)
        assertEquals("the lower note first", frequenciesOf(question.low), tone.played)
        advanceTimeBy(EarTrainingViewModel.NOTE_SPACING_MILLIS)
        runCurrent()
        assertEquals("then the higher, 0.7 s after", frequenciesOf(question.low, question.high), tone.played)
        assertEquals(listOf(Instruments.GUITAR.voice, Instruments.GUITAR.voice), tone.voices)

        vm.setMicAllowed(true)
        backgroundScope.launch { vm.heard.collect {} }
        advanceTimeBy(1_000)
        runCurrent()
        assertFalse("no microphone for Intervals", recorder.isRecording.value)
    }

    @Test
    fun `down plays the higher note first, and together both at once`() = runTest(dispatcher) {
        val down = intervals(AppSettings(earExercise = Exercise.INTERVALS, earDirections = setOf(IntervalDirection.DOWN)))
        advanceTimeBy(EarTrainingViewModel.NOTE_SPACING_MILLIS)
        runCurrent()
        val asked = down.state.value.interval!!
        assertEquals(frequenciesOf(asked.high, asked.low), tone.played)

        val together = intervals(AppSettings(earExercise = Exercise.INTERVALS, earDirections = setOf(IntervalDirection.TOGETHER)))
        val both = together.state.value.interval!!
        assertEquals(IntervalDirection.TOGETHER, both.direction)
        assertEquals("both in one call", listOf(frequenciesOf(both.low, both.high)), tone.together)
        assertEquals("not one after the other", 2, tone.played.size)
        assertEquals("struck at once", listOf(0f), tone.strums)
    }

    @Test
    fun `a button answers once, right in the tally, the next question 1½ s later`() = runTest(dispatcher) {
        val vm = intervals()
        val first = vm.state.value.interval!!
        vm.pickInterval(first.interval)
        assertTrue(vm.state.value.answered)
        assertEquals(first.interval, vm.state.value.picked)
        assertEquals(Tally(right = 1, asked = 1), vm.state.value.tally)
        vm.pickInterval(Interval.entries.first { it != first.interval })
        assertEquals("only once", Tally(right = 1, asked = 1), vm.state.value.tally)

        advanceTimeBy(EarTrainingViewModel.NEXT_DELAY_MILLIS - 100)
        runCurrent()
        assertEquals(first, vm.state.value.interval)
        advanceTimeBy(200)
        runCurrent()
        assertFalse("a new question", vm.state.value.answered)
        assertNull("nothing picked yet", vm.state.value.picked)
        assertNotEquals(first, vm.state.value.interval)
    }

    @Test
    fun `a wrong button shows the pick, waits longer, comes back unmarked, and ▶ holds the next one back`() = runTest(dispatcher) {
        val vm = intervals()
        val missed = vm.state.value.interval!!
        val wrong = Interval.DEFAULT.first { it != missed.interval }
        vm.pickInterval(wrong)
        assertEquals(wrong, vm.state.value.picked)
        assertEquals(Tally(right = 0, asked = 1), vm.state.value.tally)
        advanceTimeBy(EarTrainingViewModel.NEXT_DELAY_MILLIS + 100)
        runCurrent()
        assertEquals("still shown, so the right one can be seen", missed, vm.state.value.interval)

        // ▶ sounds it again and the next question waits until it has been heard.
        val sounded = tone.played.size
        vm.replay()
        runCurrent()
        assertEquals(sounded + 1, tone.played.size)
        advanceTimeBy(EarTrainingViewModel.NEXT_DELAY_AFTER_MISS_MILLIS)
        runCurrent()
        assertEquals("held back", missed, vm.state.value.interval)
        advanceTimeBy(EarTrainingViewModel.NOTE_SPACING_MILLIS + 100)
        runCurrent()
        assertNotEquals(missed, vm.state.value.interval)

        // It comes back 3 to 5 questions later, the same notes, nothing marking it.
        val asked = mutableListOf<Any>()
        repeat(EarTrainingVisit.MISSED_RETURN_MAX) {
            val question = vm.state.value.interval!!
            asked += question
            vm.pickInterval(question.interval)
            advanceTimeBy(EarTrainingViewModel.NEXT_DELAY_MILLIS + 100)
            runCurrent()
        }
        assertTrue("came back: $asked", missed in asked)
    }

    @Test
    fun `the intervals and directions chosen are saved, never none, and a question they no longer offer gives way`() = runTest(dispatcher) {
        val repository = FakeSettingsRepository(AppSettings(earExercise = Exercise.INTERVALS))
        val vm = intervals(repository.settings.value, repository)
        // Down to the fifth alone: everything else taken away.
        for (interval in Interval.DEFAULT - Interval.PERFECT_FIFTH) vm.toggleInterval(interval)
        runCurrent()
        assertEquals(setOf(Interval.PERFECT_FIFTH), vm.state.value.intervals)
        assertEquals(Interval.PERFECT_FIFTH, vm.state.value.interval!!.interval)
        vm.toggleInterval(Interval.PERFECT_FIFTH)
        assertEquals("the last one stays", setOf(Interval.PERFECT_FIFTH), vm.state.value.intervals)
        vm.toggleInterval(Interval.PERFECT_FOURTH)
        vm.toggleDirection(IntervalDirection.TOGETHER)
        vm.toggleDirection(IntervalDirection.UP)
        runCurrent()
        assertEquals(setOf(IntervalDirection.TOGETHER), vm.state.value.directions)
        assertEquals("together now", IntervalDirection.TOGETHER, vm.state.value.interval!!.direction)
        vm.toggleDirection(IntervalDirection.TOGETHER)
        assertEquals("the last one stays", setOf(IntervalDirection.TOGETHER), vm.state.value.directions)

        val saved = repository.settings.value
        assertEquals(listOf(Interval.PERFECT_FOURTH, Interval.PERFECT_FIFTH), saved.earIntervals.toList())
        assertEquals(setOf(IntervalDirection.TOGETHER), saved.earDirections)
        assertEquals("the tally is never saved", Tally(), vm.state.value.tally)
        val again = viewModel(saved, FakeSettingsRepository(saved))
        assertEquals(saved.earIntervals, again.state.value.intervals)
        assertEquals(saved.earDirections, again.state.value.directions)
    }

    @Test
    fun `leaving the tool drops Intervals' visit too`() = runTest(dispatcher) {
        val vm = intervals()
        vm.pickInterval(vm.state.value.interval!!.interval)
        vm.endVisit()
        assertNull(vm.state.value.interval)
        assertNull(vm.state.value.picked)
        assertEquals(Tally(), vm.state.value.tally)
        vm.show()
        runCurrent()
        assertNotNull("a new visit asks at once", vm.state.value.interval)
        assertFalse(vm.state.value.answered)
    }

    // ---- Chord type ----

    private fun TestScope.chordType(settings: AppSettings = AppSettings(earExercise = Exercise.CHORD_TYPE), repository: FakeSettingsRepository = FakeSettingsRepository(settings)): EarTrainingViewModel {
        val vm = viewModel(settings, repository)
        vm.show()
        runCurrent()
        return vm
    }

    /** What a chord question's strum should sound: its shape's strings on [tuning], lowest first. */
    private fun strumOf(question: ChordTypeQuestion, tuning: Tuning = Tunings.STANDARD): List<Float> =
        question.shape.notesIn(tuning).filterNotNull().map { NoteMapper().frequencyOf(it) }

    @Test
    fun `Chord type asks at once, a chord of the types chosen strummed low to high, and never opens the microphone`() = runTest(dispatcher) {
        val vm = chordType()
        val question = vm.state.value.chord
        assertNotNull(question)
        assertNull("no other exercise's question", vm.state.value.interval ?: vm.state.value.notes)
        assertTrue("major, minor or 7", question!!.quality in ChordTypeQuestions.DEFAULT)
        assertEquals("the Chords tool's first shape", ChordShapeFinder.shapesOf(question.chord, Tunings.STANDARD).first(), question.shape)
        assertEquals("strummed in one call", listOf(strumOf(question)), tone.together)
        assertEquals("low string first, 40 ms apart", listOf(EarTrainingViewModel.STRUM_SECONDS), tone.strums)
        assertEquals("no octave courses on a six-string", listOf(List(strumOf(question).size) { false }), tone.togetherPairs)
        assertTrue("nothing one by one", tone.played.isEmpty())

        vm.setMicAllowed(true)
        backgroundScope.launch { vm.heard.collect {} }
        advanceTimeBy(1_000)
        runCurrent()
        assertFalse("no microphone for Chord type", recorder.isRecording.value)
    }

    @Test
    fun `a twelve-string strums its courses as octave pairs`() = runTest(dispatcher) {
        val tuning = Instruments.GUITAR_12.defaultTuning
        chordType(AppSettings(earExercise = Exercise.CHORD_TYPE, instrument = Instruments.GUITAR_12, tuning = tuning))
        val pairs = tone.togetherPairs.single()
        assertTrue("some courses paired: $pairs", pairs.any { it })
    }

    @Test
    fun `a chord type button answers once, a wrong one waits longer and comes back unmarked`() = runTest(dispatcher) {
        val vm = chordType()
        val first = vm.state.value.chord!!
        vm.pickChordType(first.quality)
        assertEquals(first.quality, vm.state.value.pickedType)
        assertEquals(Tally(right = 1, asked = 1), vm.state.value.tally)
        vm.pickChordType(ChordQuality.SUS4)
        assertEquals("only once", Tally(right = 1, asked = 1), vm.state.value.tally)
        advanceTimeBy(EarTrainingViewModel.NEXT_DELAY_MILLIS + 100)
        runCurrent()
        val missed = vm.state.value.chord!!
        assertNotEquals(first, missed)
        assertNull("nothing picked yet", vm.state.value.pickedType)
        assertEquals("a new strum", 2, tone.together.size)

        val wrong = ChordTypeQuestions.DEFAULT.first { it != missed.quality }
        vm.pickChordType(wrong)
        assertEquals(Tally(right = 1, asked = 2), vm.state.value.tally)
        advanceTimeBy(EarTrainingViewModel.NEXT_DELAY_MILLIS + 100)
        runCurrent()
        assertEquals("still shown, so the right one can be seen", missed, vm.state.value.chord)
        // ▶ strums it again and the next question waits until it has been heard.
        vm.replay()
        runCurrent()
        assertEquals(listOf(strumOf(missed), strumOf(missed)), tone.together.takeLast(2))
        advanceTimeBy(EarTrainingViewModel.NEXT_DELAY_AFTER_MISS_MILLIS)
        runCurrent()
        assertEquals("held back", missed, vm.state.value.chord)
        advanceTimeBy(1_000)
        runCurrent()
        assertNotEquals(missed, vm.state.value.chord)

        val asked = mutableListOf<ChordTypeQuestion>()
        repeat(EarTrainingVisit.MISSED_RETURN_MAX) {
            val question = vm.state.value.chord!!
            asked += question
            vm.pickChordType(question.quality)
            advanceTimeBy(EarTrainingViewModel.NEXT_DELAY_MILLIS + 100)
            runCurrent()
        }
        assertTrue("came back: $asked", missed in asked)
    }

    @Test
    fun `the chord types chosen are saved in the Chords order, never none, and a question they no longer offer gives way`() = runTest(dispatcher) {
        val repository = FakeSettingsRepository(AppSettings(earExercise = Exercise.CHORD_TYPE))
        val vm = chordType(repository.settings.value, repository)
        vm.toggleChordType(ChordQuality.MAJOR)
        vm.toggleChordType(ChordQuality.DOMINANT_SEVENTH)
        runCurrent()
        assertEquals(setOf(ChordQuality.MINOR), vm.state.value.chordTypes)
        assertEquals("minor now", ChordQuality.MINOR, vm.state.value.chord!!.quality)
        vm.toggleChordType(ChordQuality.MINOR)
        assertEquals("the last one stays", setOf(ChordQuality.MINOR), vm.state.value.chordTypes)
        vm.toggleChordType(ChordQuality.AUGMENTED)
        vm.toggleChordType(ChordQuality.SUS2)
        runCurrent()
        assertEquals("in the Chords panel's order", listOf(ChordQuality.MINOR, ChordQuality.SUS2, ChordQuality.AUGMENTED), vm.state.value.chordTypes.toList())
        val saved = repository.settings.value
        assertEquals(setOf(ChordQuality.MINOR, ChordQuality.SUS2, ChordQuality.AUGMENTED), saved.earChordTypes)
        assertEquals("the tally is never saved", Tally(), vm.state.value.tally)
        val again = viewModel(saved, FakeSettingsRepository(saved))
        assertEquals(listOf(ChordQuality.MINOR, ChordQuality.SUS2, ChordQuality.AUGMENTED), again.state.value.chordTypes.toList())
    }

    @Test
    fun `on a banjo Chord type strums the Chords tool's first shape in the banjo voice, the short string first`() = runTest(dispatcher) {
        val openG = Instruments.BANJO_5.defaultTuning
        val banjo = AppSettings(earExercise = Exercise.CHORD_TYPE, instrument = Instruments.BANJO_5, tuning = openG, earChordTypes = setOf(ChordQuality.MAJOR))
        val repository = FakeSettingsRepository(banjo)
        val vm = chordType(banjo, repository)
        assertNull("asked, no line in its place", vm.state.value.chordsUnavailable)
        val first = vm.state.value.chord!!
        assertEquals("the Chords tool's first shape", ChordShapeFinder.shapesOf(first.chord, openG).first(), first.shape)
        assertEquals("strummed in one call", listOf(strumOf(first, openG)), tone.together)
        assertEquals("in the banjo's voice", listOf(Instruments.BANJO_5.voice), tone.togetherVoices)
        assertEquals(listOf(EarTrainingViewModel.STRUM_SECONDS), tone.strums)
        assertEquals("no octave courses", listOf(List(strumOf(first, openG).size) { false }), tone.togetherPairs)

        // G major, 00000: all five strings, the short g first, as a strum crosses them.
        repeat(40) {
            if (vm.state.value.chord!!.chord.root == PitchClass.G) return@repeat
            vm.pickChordType(ChordQuality.MAJOR)
            advanceTimeBy(EarTrainingViewModel.NEXT_DELAY_MILLIS + 100)
            runCurrent()
        }
        assertEquals(PitchClass.G, vm.state.value.chord!!.chord.root)
        assertEquals("g4 D3 G3 B3 D4, all open", openG.strings.map { NoteMapper().frequencyOf(it) }, tone.together.last())

        // Another banjo tuning strums its own shape; the guitar its own, in its voice.
        val doubleD = Instruments.BANJO_5.tuningById("banjo_double_d")!!
        repository.setTuning(doubleD)
        runCurrent()
        val onDoubleD = vm.state.value.chord!!
        assertEquals(ChordShapeFinder.shapesOf(onDoubleD.chord, doubleD).first(), onDoubleD.shape)
        assertEquals(strumOf(onDoubleD, doubleD), tone.together.last())
        repository.setInstrument(Instruments.GUITAR)
        repository.setTuning(Tunings.STANDARD)
        runCurrent()
        val onGuitar = vm.state.value.chord!!
        assertEquals(strumOf(onGuitar), tone.together.last())
        assertEquals(Instruments.GUITAR.voice, tone.togetherVoices.last())
    }

    @Test
    fun `where none of the types chosen has a shape Chord type asks nothing and says why, and asks again on another tuning`() = runTest(dispatcher) {
        val oneString = Tuning("custom_one", listOf(Note(40))) // a player's own, one string: no chord at all
        val repository = FakeSettingsRepository(AppSettings(earExercise = Exercise.CHORD_TYPE, tuning = oneString))
        val vm = chordType(repository.settings.value, repository)
        assertEquals(ChordsUnavailable.NO_SHAPE, vm.state.value.chordsUnavailable)
        assertNull(vm.state.value.chord)
        assertTrue("nothing strummed", tone.together.isEmpty())
        vm.pickChordType(ChordQuality.MAJOR)
        assertEquals(Tally(), vm.state.value.tally)

        repository.setTuning(Instruments.BANJO_5.defaultTuning)
        runCurrent()
        assertNull(vm.state.value.chordsUnavailable)
        assertNotNull("asks at once on the banjo", vm.state.value.chord)
        assertEquals(1, tone.together.size)
    }

    @Test
    fun `another tuning strums its own shape`() = runTest(dispatcher) {
        val repository = FakeSettingsRepository(AppSettings(earExercise = Exercise.CHORD_TYPE))
        val vm = chordType(repository.settings.value, repository)
        val dropD = Tunings.DROP_D
        repository.setTuning(dropD)
        runCurrent()
        val question = vm.state.value.chord!!
        assertEquals(ChordShapeFinder.shapesOf(question.chord, dropD).first(), question.shape)
        assertEquals(strumOf(question, dropD), tone.together.last())
    }

    @Test
    fun `leaving the tool drops Chord type's visit too`() = runTest(dispatcher) {
        val vm = chordType()
        vm.pickChordType(vm.state.value.chord!!.quality)
        vm.endVisit()
        assertNull(vm.state.value.chord)
        assertNull(vm.state.value.pickedType)
        assertEquals(Tally(), vm.state.value.tally)
        vm.show()
        runCurrent()
        assertNotNull("a new visit asks at once", vm.state.value.chord)
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

        /** The in-tune hold (0.3 s) and a little more, after the smoothing's first frames. */
        const val STEADY_MILLIS = 300L

        /** A note played long enough to be right, but not to be wrong (1 s); and long enough to be wrong. */
        const val HOLD_STEADY_MILLIS = 600L
        const val HOLD_SETTLED_MILLIS = 1_400L

        /** Long enough for a let-go string to be heard as gone. */
        const val QUIET_MILLIS = 600L
    }
}
