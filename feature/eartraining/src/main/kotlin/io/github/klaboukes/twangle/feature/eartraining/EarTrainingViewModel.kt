package io.github.klaboukes.twangle.feature.eartraining

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.klaboukes.twangle.core.audio.TonePlayer
import io.github.klaboukes.twangle.core.dsp.eartraining.EarTrainingVisit
import io.github.klaboukes.twangle.core.dsp.eartraining.Exercise
import io.github.klaboukes.twangle.core.dsp.eartraining.HeldNote
import io.github.klaboukes.twangle.core.dsp.eartraining.Hold
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteChoice
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteMatch
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteQuestion
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteQuestions
import io.github.klaboukes.twangle.core.dsp.eartraining.PlayBackAnswer
import io.github.klaboukes.twangle.core.dsp.eartraining.Tally
import io.github.klaboukes.twangle.core.dsp.eartraining.Verdict
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import io.github.klaboukes.twangle.core.dsp.smoothing.PitchSmoother
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.random.Random

/**
 * What the Ear training screen shows of the [exercise] picked: its question and this visit's [tally]
 * of it ("7 of 9", in memory only, never saved), whether the question was [answered] (the next one
 * comes by itself [EarTrainingViewModel.NEXT_DELAY_MILLIS] later), and what the player chose to be
 * asked. Intervals and Chord type add their questions here as they are built.
 */
data class EarTrainingState(
    val exercise: Exercise = Exercise.DEFAULT,
    /** Play it back's question: the notes to play back, in order. Null before the first and in the other exercises. */
    val notes: NoteQuestion? = null,
    val tally: Tally = Tally(),
    val answered: Boolean = false,
    /** Play it back: how many of [notes] the player has found, in order (all of them once it is answered). */
    val found: Int = 0,
    /** Play it back: a wrong note was held ("Not that one"), until a right one is found or the question sounds again. */
    val wrongHeld: Boolean = false,
    /** Play it back's choices (saved, as the other Practice settings): which notes, how many in a row, any octave or the exact note. */
    val noteChoice: NoteChoice = NoteChoice.OPEN_STRINGS,
    val notesInRow: Int = 1,
    val noteMatch: NoteMatch = NoteMatch.ANY_OCTAVE,
) {
    /** Play it back's question was found, every note of it: "Right", glowing softly. */
    val allFound: Boolean get() = notes != null && found == notes.notes.size
}

/**
 * Ear training (Phase 55), a Practice tool: the exercise picked, its questions and this visit's tally,
 * the question's sound and the note the microphone hears.
 *
 * **A visit.** Questions start at once ([show]) and keep coming until the player leaves the tool
 * ([endVisit]), which drops every question and tally: a tool, not a game. Each exercise keeps its own
 * [EarTrainingVisit] for the visit (seeded with [random], so tests repeat a visit), so a missed
 * question comes back a few questions later, unmarked (the owner's pick A). After an answer
 * ([answer]) the next question comes by itself after [NEXT_DELAY_MILLIS] (pick A).
 *
 * **Saved.** The exercise picked and the player's choices of what is asked are settings (from
 * [savedSettings], saved through [settingsRepository] when changed), never the tally or the questions.
 *
 * **The sound.** A question sounds in the voice of the instrument picked ([AppSettings.instrument],
 * at its A4) through [tonePlayer], note after note ([NOTE_SPACING_MILLIS] apart); [replay] sounds it
 * again.
 *
 * **Listening.** Only Play it back listens ([Exercise.listens]), and only while the microphone is
 * allowed ([setMicAllowed]) and [heard] is collected: `WhileSubscribed(5 s)`, as the tuner, so the
 * microphone is held only while the tool is shown. [listener] is the tuner's own pipeline. While the
 * question sounds, and for [TONE_TAIL_SECONDS] after it has faded from the speaker, the microphone
 * stays open but what it hears is set aside and the smoothing starts afresh, so the question's own
 * sound (or its echo in the room) is never taken for the player's answer. The heard note is the
 * tuner's ([PitchSmoother.note]), smoothed on [dspDispatcher].
 *
 * **Play it back's answer.** How long the heard note is held is counted frame by frame ([HeldNote]),
 * and the question's [PlayBackAnswer] judges each change on the main thread: the right note held
 * 0.3 s (the in-tune hold) is found, a wrong one held 1 s marks the question missed ("Not that one")
 * and the player plays on until it is found. Every note found ends the question, counted right only
 * if no wrong note was held first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EarTrainingViewModel(
    private val listener: PitchListener,
    private val tonePlayer: TonePlayer,
    private val settingsRepository: SettingsRepository,
    savedSettings: AppSettings = AppSettings(),
    private val dspDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val random: () -> Random = { Random(System.nanoTime()) },
) : ViewModel() {

    /** The saved settings: the instrument and tuning the questions are asked on, its voice and A4, and the microphone's refusal. */
    private val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, savedSettings)

    /** How low the microphone listens: the longer window where the tuning's lowest string needs it (a bass), as the tuner. */
    private val range: StateFlow<PitchRange> = settings
        .map(::rangeOf)
        .stateIn(viewModelScope, SharingStarted.Eagerly, rangeOf(savedSettings))

    private val shown = MutableStateFlow(
        EarTrainingState(
            exercise = savedSettings.earExercise,
            noteChoice = savedSettings.earNoteChoice,
            notesInRow = savedSettings.earNotesInRow.coerceIn(1, NoteQuestions.MAX_LENGTH),
            noteMatch = savedSettings.earNoteMatch,
        ),
    )

    val state: StateFlow<EarTrainingState> = shown.asStateFlow()

    /** Whether the player refused the microphone before (saved), so a refusal Android answers without a dialog is told apart. */
    val micRefused: StateFlow<Boolean> = settingsRepository.settings
        .map { it.micRefused }
        .stateIn(viewModelScope, SharingStarted.Eagerly, savedSettings.micRefused)

    // Play it back's questions this visit, null until it starts and after the visit ends; the answer to its current one.
    private var playBack: EarTrainingVisit<NoteQuestion>? = null
    private var playAnswer: PlayBackAnswer? = null

    private var soundJob: Job? = null
    private var nextJob: Job? = null

    // True while a question's notes are being started, one after another (read on the DSP thread too).
    private val questionStarting = MutableStateFlow(false)

    // True from a question starting to sound until the microphone has been seen setting it aside, so
    // nothing heard before it (still on its way from the DSP thread) is judged against it.
    private var awaitingAside = false

    private val micAllowed = MutableStateFlow(false)

    /**
     * The note the microphone hears as the player plays (the tuner's note, smoothed), or null: nothing
     * heard, the question still sounding or fading, the microphone not allowed, or an exercise that
     * doesn't listen. Collecting it opens the microphone (and lets Play it back judge what it hears);
     * it closes 5 s after the last collector goes.
     */
    val heard: StateFlow<Note?> = combine(micAllowed, shown) { allowed, state -> allowed && state.exercise.listens }
        .distinctUntilChanged()
        .flatMapLatest { listening -> if (listening) hear() else flowOf(Hearing.ASIDE) }
        .onEach(::judge)
        .map { it.midi }
        .distinctUntilChanged()
        .map { midi -> if (midi == HeldNote.NO_NOTE) null else Note(midi) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Another tuning (or instrument) asks its own notes; a question the new one can't ask gives way to a new one.
        viewModelScope.launch {
            settings.map { it.tuning }.distinctUntilChanged().drop(1).collect { changeNotes() }
        }
    }

    /** The tool is shown: the exercise picked starts asking at once, unless its visit is already going (a turned phone). */
    fun show() {
        if (visitOf(shown.value.exercise) == null) start(shown.value.exercise)
    }

    /** Picks [exercise] (saved): its own questions and tally, its first question at once if it has none yet. */
    fun pickExercise(exercise: Exercise) {
        if (exercise == shown.value.exercise) return
        nextJob?.cancel()
        stopSound()
        shown.value = shown.value.copy(exercise = exercise)
        viewModelScope.launch { settingsRepository.setEarExercise(exercise) }
        val visit = visitOf(exercise)
        when {
            visit == null -> start(exercise)
            visit.answered -> ask(visit)
            else -> publish()
        }
    }

    /** Play it back asks for [choice]'s notes from now on (saved); a question it no longer offers gives way to a new one at once. */
    fun setNoteChoice(choice: NoteChoice) {
        if (choice == shown.value.noteChoice) return
        shown.value = shown.value.copy(noteChoice = choice)
        viewModelScope.launch { settingsRepository.setEarNoteChoice(choice) }
        changeNotes()
    }

    /** Play it back plays [count] notes in a row from now on (1 to 4, saved); the question gives way to one that long at once. */
    fun setNotesInRow(count: Int) {
        val kept = count.coerceIn(1, NoteQuestions.MAX_LENGTH)
        if (kept == shown.value.notesInRow) return
        shown.value = shown.value.copy(notesInRow = kept)
        viewModelScope.launch { settingsRepository.setEarNotesInRow(kept) }
        changeNotes()
    }

    /** Any octave of the note asked counts, or only the exact note (saved); the question stays. */
    fun setNoteMatch(match: NoteMatch) {
        if (match == shown.value.noteMatch) return
        shown.value = shown.value.copy(noteMatch = match)
        viewModelScope.launch { settingsRepository.setEarNoteMatch(match) }
    }

    /** Sounds the question again (the ▶), from its first note; the notes found so far stay found. */
    fun replay() {
        when (shown.value.exercise) {
            Exercise.PLAY_IT_BACK -> playBack?.current?.let {
                playAnswer?.replayed()
                publish()
                sound(it.notes)
            }
            // Their questions come with their own increments.
            Exercise.INTERVALS, Exercise.CHORD_TYPE -> Unit
        }
    }

    /**
     * The answer to the question shown, once: [right] or not, counted in the tally, a missed one sent
     * back a few questions later. The next question follows by itself after [NEXT_DELAY_MILLIS].
     */
    fun answer(right: Boolean) {
        val visit = visitOf(shown.value.exercise) ?: return
        if (visit.current == null || visit.answered) return
        visit.answer(right)
        publish()
        nextJob?.cancel()
        nextJob = viewModelScope.launch {
            delay(NEXT_DELAY_MILLIS)
            ask(visit)
        }
    }

    /** Whether Android allows the microphone: Play it back listens only then. */
    fun setMicAllowed(allowed: Boolean) {
        micAllowed.value = allowed
    }

    /** The player refused the microphone in Android's dialog: saved, as the tuner saves it. */
    fun onMicRefused() {
        if (!micRefused.value) viewModelScope.launch { settingsRepository.setMicRefused(true) }
    }

    /** Silences the question (the screen was hidden): the tone fades out within a few tens of ms. */
    fun stopSound() {
        soundJob?.cancel()
        questionStarting.value = false
        if (tonePlayer.isPlaying.value) tonePlayer.stop()
    }

    /** The player left the tool: this visit's questions and tallies are dropped, never saved. */
    fun endVisit() {
        nextJob?.cancel()
        stopSound()
        playBack = null
        playAnswer = null
        shown.value = shown.value.copy(notes = null, tally = Tally(), answered = false, found = 0, wrongHeld = false)
    }

    override fun onCleared() {
        stopSound()
    }

    private fun visitOf(exercise: Exercise): EarTrainingVisit<*>? = when (exercise) {
        Exercise.PLAY_IT_BACK -> playBack
        Exercise.INTERVALS, Exercise.CHORD_TYPE -> null
    }

    /** Play it back's questions as the player chose them, on the tuning picked. */
    private fun noteQuestions(): NoteQuestions = shown.value.let { NoteQuestions(settings.value.tuning, it.noteChoice, it.notesInRow) }

    /** The notes asked changed (choice, length, tuning): a question the new choices don't offer gives way to a new one. */
    private fun changeNotes() {
        val visit = playBack ?: return
        visit.change(noteQuestions())
        val current = visit.current
        if (current == null || !visit.source.offers(current)) {
            if (shown.value.exercise == Exercise.PLAY_IT_BACK) {
                nextJob?.cancel()
                ask(visit)
            } else {
                publish()
            }
        }
    }

    private fun start(exercise: Exercise) {
        when (exercise) {
            Exercise.PLAY_IT_BACK -> {
                val visit = EarTrainingVisit(noteQuestions(), random())
                playBack = visit
                ask(visit)
            }
            // Intervals and Chord type ask nothing yet: their questions come with their own increments.
            Exercise.INTERVALS, Exercise.CHORD_TYPE -> publish()
        }
    }

    /** The next question of [visit], shown and sounded. */
    private fun ask(visit: EarTrainingVisit<*>) {
        val question = visit.next()
        if (question is NoteQuestion) playAnswer = PlayBackAnswer(question)
        publish()
        if (question is NoteQuestion) sound(question.notes)
    }

    /** Judges a change in what Play it back hears against its question, on the main thread. */
    private fun judge(hearing: Hearing) {
        if (hearing.aside) awaitingAside = false
        if (awaitingAside || shown.value.exercise != Exercise.PLAY_IT_BACK) return
        val visit = playBack ?: return
        val answer = playAnswer ?: return
        if (visit.answered || answer.question != visit.current) return
        when (answer.hear(hearing.midi, hearing.hold, shown.value.noteMatch)) {
            Verdict.NONE -> Unit
            Verdict.NOTE_FOUND, Verdict.WRONG -> publish()
            Verdict.ALL_FOUND -> answer(right = !answer.missed)
        }
    }

    /** Shows the picked exercise's question and tally as they are now. */
    private fun publish() {
        val state = shown.value
        val visit = visitOf(state.exercise)
        val playing = state.exercise == Exercise.PLAY_IT_BACK
        shown.value = state.copy(
            notes = if (playing) playBack?.current else null,
            tally = visit?.tally ?: Tally(),
            answered = visit?.answered ?: false,
            found = if (playing) playAnswer?.found ?: 0 else 0,
            wrongHeld = playing && playAnswer?.wrongHeld == true,
        )
    }

    /** Sounds [notes] one after another in the instrument's voice, at its A4. */
    private fun sound(notes: List<Note>) {
        soundJob?.cancel()
        val current = settings.value
        val mapper = NoteMapper(current.a4Hz)
        val voice = current.instrument.voice
        questionStarting.value = true
        awaitingAside = true
        soundJob = viewModelScope.launch {
            try {
                notes.forEachIndexed { index, note ->
                    if (index > 0) delay(NOTE_SPACING_MILLIS)
                    tonePlayer.play(mapper.frequencyOf(note), voice)
                }
            } finally {
                questionStarting.value = false
            }
        }
    }

    /** Whether the speaker may be sounding the question: its notes are being started, or a tone hasn't faded yet. */
    private fun questionSounding(): Boolean = questionStarting.value || tonePlayer.isPlaying.value

    /**
     * What the microphone hears, frame by frame, as changes only: the note heard (a MIDI number, or
     * [HeldNote.NO_NOTE]) and how long it has been held, or [Hearing.ASIDE] while the question sounds
     * (and first of all, so nothing from before this session is judged). A new [Hearing] is made only
     * when it changes, a few times a second at most; nothing is allocated per frame.
     */
    private fun hear(): Flow<Hearing> = flow {
        val smoother = PitchSmoother()
        val held = HeldNote()
        val tailFrames = ceil(TONE_TAIL_SECONDS / listener.frameSeconds).toInt()
        var ignoredFrames = 0
        var mapper = NoteMapper(settings.value.a4Hz)
        var last = Hearing.ASIDE
        emit(last)
        listener.frames(range = { range.value }).collect { frame ->
            if (questionSounding()) ignoredFrames = tailFrames + 1
            val next = if (ignoredFrames > 0) {
                // The microphone would hear the question itself: set aside, and the smoothing starts afresh after.
                ignoredFrames--
                smoother.reset()
                held.reset()
                Hearing.ASIDE
            } else {
                val a4 = settings.value.a4Hz
                if (a4 != mapper.a4Hz) {
                    mapper = NoteMapper(a4)
                    smoother.reset()
                }
                if (frame.isPitched) {
                    smoother.update(mapper.midiOf(frame.frequencyHz), frame.confidence, frame.loudEnough)
                } else {
                    smoother.noReading(frame.sustained)
                }
                val midi = if (smoother.hasReading) smoother.note.midi else HeldNote.NO_NOTE
                val hold = held.update(midi, listener.frameSeconds)
                when {
                    !last.aside && last.midi == midi && last.hold == hold -> last
                    midi == HeldNote.NO_NOTE -> Hearing.NOTHING
                    else -> Hearing(midi, hold)
                }
            }
            if (next !== last) {
                last = next
                emit(next)
            }
        }
    }.flowOn(dspDispatcher)

    private fun rangeOf(settings: AppSettings): PitchRange =
        PitchRange.forLowestNote(NoteMapper(settings.a4Hz).frequencyOf(settings.tuning.lowestNote))

    /** One change in what the microphone hears: the note ([HeldNote.NO_NOTE] for none) and its [hold], or set [aside]. */
    private data class Hearing(val midi: Int, val hold: Hold, val aside: Boolean = false) {
        companion object {
            val ASIDE = Hearing(HeldNote.NO_NOTE, Hold.SOUNDING, aside = true)
            val NOTHING = Hearing(HeldNote.NO_NOTE, Hold.SOUNDING)
        }
    }

    companion object {
        /** After an answer, the next question comes by itself this much later (the owner's pick A: about 1½ s). */
        const val NEXT_DELAY_MILLIS = 1_500L

        /** Two to four notes in a row start this far apart. */
        const val NOTE_SPACING_MILLIS = 700L

        /**
         * After the question has left the speaker, the microphone is set aside this much longer, so the
         * room's echo of it isn't heard as the answer either (as the tuner after a reference tone).
         */
        const val TONE_TAIL_SECONDS = 0.3f
    }
}
