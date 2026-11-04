package io.github.klaboukes.twangle.feature.eartraining

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.klaboukes.twangle.core.audio.MicPermissionStatus
import io.github.klaboukes.twangle.core.audio.TonePlayer
import io.github.klaboukes.twangle.core.designsystem.FLOATING_PANEL_WIDTH
import io.github.klaboukes.twangle.core.designsystem.MoreButton
import io.github.klaboukes.twangle.core.designsystem.SegmentedSwitch
import io.github.klaboukes.twangle.core.designsystem.StyleButton
import io.github.klaboukes.twangle.core.designsystem.TOP_BAR_MARGIN
import io.github.klaboukes.twangle.core.designsystem.TOP_BAR_TOP
import io.github.klaboukes.twangle.core.designsystem.TwangleTextButton
import io.github.klaboukes.twangle.core.designsystem.meter.meterGlowSpec
import io.github.klaboukes.twangle.core.designsystem.panelAnchor
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.designsystem.R as DesignR
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.eartraining.Exercise
import io.github.klaboukes.twangle.core.dsp.eartraining.Interval
import io.github.klaboukes.twangle.core.dsp.eartraining.IntervalDirection
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteChoice
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteMatch
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteQuestion
import io.github.klaboukes.twangle.core.dsp.eartraining.Tally
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.SettingsRepository

/**
 * Ear training (Phase 55), a Practice tool, as the owner picked from docs/design/ear-training-preview.html
 * (all option A, 2026-10-28): the three exercises as three icons in a row, the questions starting at
 * once, the "What is asked" button opening the choices any time, and Play it back showing the note
 * the tuner hears as the player plays. Intervals is answered with buttons and never asks for the
 * microphone; nor does Chord type, a chord strummed and named with buttons.
 *
 * The question sounds through [tonePlayer] in the voice of the instrument picked ([savedSettings], then
 * the saved settings from [settingsRepository]); Play it back hears the instrument through [listener]
 * (the tuner's pipeline), asking for the microphone the first time it is shown, as the tuner does. The
 * microphone is held only while this screen is shown. The question stops sounding when the screen is
 * hidden, and no next question comes until it shows again, when a microphone that stopped meanwhile
 * listens again too; leaving the tool ends the visit (its tally and questions are dropped, never saved), while a
 * turned phone keeps it. [header] is the top row (the back arrow and the picker chip), which `:app` builds, putting
 * the end it is given (the "What is asked" ⋯) at the row's end; note names follow [notation].
 */
@Composable
fun EarTrainingScreen(
    listener: PitchListener,
    tonePlayer: TonePlayer,
    settingsRepository: SettingsRepository,
    notation: Notation,
    header: @Composable (end: @Composable () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    savedSettings: AppSettings = AppSettings(),
) {
    val viewModel: EarTrainingViewModel = viewModel {
        EarTrainingViewModel(listener, tonePlayer, settingsRepository, savedSettings)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val heard by viewModel.heard.collectAsStateWithLifecycle()
    val micRefused by viewModel.micRefused.collectAsStateWithLifecycle()
    val permission = rememberEarMicPermission(
        needed = state.exercise.listens,
        refusedBefore = micRefused,
        onRefused = viewModel::onMicRefused,
    )
    val allowed = permission.status == MicPermissionStatus.GRANTED
    LaunchedEffect(allowed) { viewModel.setMicAllowed(allowed) }
    LaunchedEffect(viewModel) { viewModel.show() }
    EndVisitWhenLeft(onShown = viewModel::shownAgain, onHidden = viewModel::stopSound, onLeft = viewModel::endVisit)

    EarTrainingContent(
        state = state,
        heard = heard,
        notation = notation,
        mic = permission.status,
        onPickExercise = viewModel::pickExercise,
        onReplay = viewModel::replay,
        onNoteChoice = viewModel::setNoteChoice,
        onNotesInRow = viewModel::setNotesInRow,
        onNoteMatch = viewModel::setNoteMatch,
        onPickInterval = viewModel::pickInterval,
        onToggleInterval = viewModel::toggleInterval,
        onToggleDirection = viewModel::toggleDirection,
        onPickChordType = viewModel::pickChordType,
        onToggleChordType = viewModel::toggleChordType,
        onAllowMic = permission::request,
        onOpenSettings = permission::openSettings,
        header = header,
        modifier = modifier,
    )
}

/**
 * The Ear training screen as it is drawn, from [state] alone (stateless, for the tests): the top row
 * ([header], given the "What is asked" ⋯ for its end), the exercise row (the switch of the three exercises), the
 * ▶ that sounds the question again with this visit's tally beside it, and the exercise's own part under it. Play
 * it back's part is the note [heard] (in [notation]), or in its place what to do; with the microphone not allowed ([mic]),
 * "Microphone is off" with a button that asks again ([onAllowMic]) or, refused for good, opens
 * Settings ([onOpenSettings]). Intervals' part is the question and its answer buttons ([onPickInterval],
 * [IntervalsPart]), Chord type's the same with a button per chord type ([onPickChordType],
 * [ChordTypePart]), or on the banjo the Chords tool's quiet line in its place. The ⋯ opens the choices in the app's
 * floating panel under it ([ChoicesPanel], as the metronome's options; a sheet from the bottom until Phase 77), which
 * keeps open across a turned phone.
 *
 * One column, at most [COLUMN_MAX_WIDTH] wide in the middle; on its side (at least [HALVES_MIN_WIDTH]
 * wide and wider than tall) the top rows fill the start half and the question stands in the middle of
 * the end half. Each scrolls only where the window is too short (large text); Chord type's answers
 * instead scroll in their own box, which takes the room under its question. Nothing moves when the tally appears or the heard note
 * changes: the tally stands beside the ▶ in room its row keeps, the note in a box of steady height.
 */
@Composable
fun EarTrainingContent(
    state: EarTrainingState,
    heard: Note?,
    notation: Notation,
    mic: MicPermissionStatus,
    onPickExercise: (Exercise) -> Unit,
    onReplay: () -> Unit,
    onNoteChoice: (NoteChoice) -> Unit,
    onNotesInRow: (Int) -> Unit,
    onNoteMatch: (NoteMatch) -> Unit,
    onPickInterval: (Interval) -> Unit,
    onToggleInterval: (Interval) -> Unit,
    onToggleDirection: (IntervalDirection) -> Unit,
    onPickChordType: (ChordQuality) -> Unit,
    onToggleChordType: (ChordQuality) -> Unit,
    onAllowMic: () -> Unit,
    onOpenSettings: () -> Unit,
    header: @Composable (end: @Composable () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Kept when the phone turns: the panel opens again under the ⋯ in its new place.
    var choicesOpen by rememberSaveable { mutableStateOf(false) }
    var choicesBounds by remember { mutableStateOf<Rect?>(null) }
    // The top row's width, which the panel takes (CHOICES_PANEL_MAX_WIDTH at most), its end under the ⋯'s.
    var topRowWidth by remember { mutableIntStateOf(0) }
    val more = @Composable {
        // TalkBack hears "What is asked" as a drop-down list, as the metronome's ⋯.
        MoreButton(
            open = choicesOpen,
            onClick = { choicesOpen = true },
            label = stringResource(R.string.ear_choices),
            role = Role.DropdownList,
            // The panel's end in line with the button's outline, not its touch area.
            faceModifier = Modifier.testTag(EarTrainingTestTags.CHOICES).panelAnchor { choicesBounds = it },
        )
    }
    val topRows = @Composable {
        Spacer(Modifier.height(TOP_BAR_TOP))
        Box(Modifier.fillMaxWidth().onSizeChanged { topRowWidth = it.width }) { header(more) }
        Spacer(Modifier.height(8.dp))
        ExerciseRow(state.exercise, onPickExercise)
    }
    // Chord type's answers scroll in their own box, which takes the room left under the question, so
    // the ▶ and the question stay put; its column doesn't scroll then.
    val answersFillRoom = state.exercise == Exercise.CHORD_TYPE && state.chord != null && state.chordsUnavailable == null
    val question: @Composable ColumnScope.() -> Unit = {
        when (state.exercise) {
            Exercise.PLAY_IT_BACK -> if (state.notes != null) {
                ReplayRow(onReplay, state.tally)
                Spacer(Modifier.height(8.dp))
                PlayItBackPart(state, state.notes, heard, notation, mic, onAllowMic, onOpenSettings)
            }
            Exercise.INTERVALS -> if (state.interval != null) {
                ReplayRow(onReplay, state.tally)
                Spacer(Modifier.height(8.dp))
                IntervalsPart(state, state.interval, onPickInterval)
            }
            Exercise.CHORD_TYPE -> when {
                // The banjo, or a tuning with no shape for the types chosen: the Chords tool's quiet line, no ▶.
                state.chordsUnavailable != null -> ChordsUnavailableLine(state.chordsUnavailable)
                state.chord != null -> {
                    ReplayRow(onReplay, state.tally)
                    Spacer(Modifier.height(8.dp))
                    ChordTypePart(state, state.chord, notation, onPickChordType)
                }
            }
        }
    }
    BoxWithConstraints(
        modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).testTag(EarTrainingTestTags.SCREEN),
        contentAlignment = Alignment.TopCenter,
    ) {
        val questionScroll = rememberScrollState()
        val scrolls = if (answersFillRoom) Modifier else Modifier.verticalScroll(questionScroll)
        if (maxWidth > maxHeight && maxWidth >= HALVES_MIN_WIDTH) {
            // On its side: the top rows in the start half, the question in the middle of the end half.
            Row(Modifier.fillMaxSize().padding(horizontal = TOP_BAR_MARGIN)) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { topRows() }
                Spacer(Modifier.width(16.dp))
                Column(
                    Modifier.weight(1f).fillMaxHeight().then(scrolls),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Spacer(Modifier.height(8.dp))
                    question()
                    Spacer(Modifier.height(8.dp))
                }
            }
        } else {
            Column(
                Modifier
                    .widthIn(max = COLUMN_MAX_WIDTH)
                    .fillMaxWidth()
                    .then(if (answersFillRoom) Modifier.fillMaxHeight() else scrolls)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                topRows()
                Spacer(Modifier.height(QUESTION_TOP))
                question()
                Spacer(Modifier.height(16.dp))
            }
        }
        // Last, on its own layer over the screen: under the ⋯, as wide as the top row.
        val panelWidth = with(LocalDensity.current) { topRowWidth.toDp() }.coerceIn(FLOATING_PANEL_WIDTH, CHOICES_PANEL_MAX_WIDTH)
        ChoicesPanel(
            open = choicesOpen,
            anchor = choicesBounds,
            width = panelWidth,
            state = state,
            onNoteChoice = onNoteChoice,
            onNotesInRow = onNotesInRow,
            onNoteMatch = onNoteMatch,
            onToggleInterval = onToggleInterval,
            onToggleDirection = onToggleDirection,
            onToggleChordType = onToggleChordType,
            onDismiss = { choicesOpen = false },
        )
    }
}

/** Test tags for UI tests (as resource ids in the app). */
object EarTrainingTestTags {
    /** The Ear training tool's screen. */
    const val SCREEN = "ear_training"

    /** An exercise's icon in the row, e.g. "ear_exercise_play_it_back". */
    fun exercise(exercise: Exercise): String = "ear_exercise_" + exercise.name.lowercase()

    /** This visit's tally ("7 of 9"), at the end of the exercise row. */
    const val TALLY = "ear_tally"

    /** The "What is asked" button (the ⋯ at the end of the top row, its face), and the panel it opens. */
    const val CHOICES = "ear_choices"
    const val CHOICES_PANEL = "ear_choices_panel"

    /** A choice of notes in that panel, e.g. "ear_notes_open_strings". */
    fun noteChoice(choice: NoteChoice): String = "ear_notes_" + choice.name.lowercase()

    /** A choice of notes in a row in that panel, 1 to 4, e.g. "ear_in_row_2"; and the "Any octave counts" switch. */
    fun notesInRow(count: Int): String = "ear_in_row_$count"
    const val ANY_OCTAVE = "ear_any_octave"

    /** The round ▶ that sounds the question again. */
    const val REPLAY = "ear_replay"

    /** Play it back: the note the tuner hears; while none, in its place what to do ("Play it on your instrument"). */
    const val HEARD = "ear_heard"
    const val WAITING = "ear_waiting"

    /** Play it back: the small line under it ("Hold the note you heard", "Note 2 of 3", "Right", "Not that one", "Microphone stopped"; empty while waiting). */
    const val HEARD_LINE = "ear_heard_line"

    /** Play it back with notes in a row: a dot for each, the ones found filled in the in-tune colour. */
    const val DOTS = "ear_dots"

    /** Intervals: up, down or together over the question; the question line ("Which interval?", "Right", "Not that one"). */
    const val DIRECTION = "ear_direction"
    const val INTERVAL_LINE = "ear_interval_line"

    /** Intervals: an interval's answer button, e.g. "ear_answer_perfect_fifth". */
    fun answer(interval: Interval): String = "ear_answer_" + interval.name.lowercase()

    /** In the What is asked panel, Intervals: an interval's chip, e.g. "ear_ask_perfect_fifth", and a direction's, e.g. "ear_played_together". */
    fun askInterval(interval: Interval): String = "ear_ask_" + interval.name.lowercase()
    fun direction(direction: IntervalDirection): String = "ear_played_" + direction.name.lowercase()

    /** Chord type: the question line, a type's answer button (e.g. "ear_answer_chord_minor_seventh") and the box they scroll in. */
    const val CHORD_LINE = "ear_chord_line"
    fun chordAnswer(type: ChordQuality): String = "ear_answer_chord_" + type.name.lowercase()
    const val CHORD_ANSWERS = "ear_chord_answers"

    /** In the What is asked panel, Chord type: a type's chip, e.g. "ear_ask_chord_sus4". */
    fun askChordType(type: ChordQuality): String = "ear_ask_chord_" + type.name.lowercase()

    /** Chord type where it can't ask (no shape within reach): the quiet line in the question's place. */
    const val CHORDS_UNAVAILABLE = "ear_chords_unavailable"

    /** Play it back without the microphone: "Microphone is off" and its button. */
    const val MIC_OFF = "ear_mic_off"
    const val MIC_BUTTON = "ear_mic_button"
}

/**
 * The exercise row: the three exercises in one switch of words ([ExerciseSwitch]) across the whole row (Phase 77: until
 * then the "What is asked" button stood at its end, now the top row's ⋯), so its width depends only on the row's and
 * the words never move; they are set smaller only where they don't fit. This visit's tally stands beside the ▶
 * ([ReplayRow]).
 */
@Composable
private fun ExerciseRow(exercise: Exercise, onPick: (Exercise) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        ExerciseSwitch(exercise, onPick, Modifier.fillMaxWidth())
    }
}

/**
 * The three exercises in the app's switch of words ([SegmentedSwitch], as the tuner's Strings · Chromatic; Phase 63:
 * three note-symbol icons were hard to tell apart): a short word each ("Play back · Intervals · Chord type"; until
 * Phase 65 "Chords", the Chords tool's name), TalkBack
 * hearing its whole name ("Play it back", "Chord type") in the group "Exercise".
 */
@Composable
private fun ExerciseSwitch(selected: Exercise, onPick: (Exercise) -> Unit, modifier: Modifier = Modifier) {
    val group = stringResource(R.string.ear_exercises)
    SegmentedSwitch(
        options = Exercise.entries,
        selected = selected,
        onSelect = onPick,
        label = { stringResource(it.word) },
        tag = EarTrainingTestTags::exercise,
        spoken = { stringResource(it.label) },
        modifier = modifier.semantics { contentDescription = group },
    )
}

/** Each exercise's short word on the switch. A new exercise needs its word here (the `when` says so). */
internal val Exercise.word: Int
    get() = when (this) {
        Exercise.PLAY_IT_BACK -> R.string.ear_exercise_word_play_back
        Exercise.INTERVALS -> R.string.ear_exercise_word_intervals
        Exercise.CHORD_TYPE -> R.string.ear_exercise_word_chord_type
    }

/** Each exercise's name, as TalkBack says its word on the switch. A new exercise needs its name here (the `when` says so). */
internal val Exercise.label: Int
    get() = when (this) {
        Exercise.PLAY_IT_BACK -> R.string.ear_exercise_play_back
        Exercise.INTERVALS -> R.string.ear_exercise_intervals
        Exercise.CHORD_TYPE -> R.string.ear_exercise_chord_type
    }

/**
 * The ▶ ([ReplayButton]) in the middle of its row, and this visit's [tally] once a question was answered, quiet, at
 * the row's end (Phase 65: until then between the switch and that button, which
 * kept the switch short). The ▶ never moves when the tally appears; a long tally is set smaller to fit its room.
 */
@Composable
private fun ReplayRow(onReplay: () -> Unit, tally: Tally) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        ReplayButton(onReplay)
        if (tally.asked > 0) {
            val spoken = pluralStringResource(R.plurals.ear_tally_spoken, tally.right, tally.right, tally.asked)
            val style = MaterialTheme.typography.bodyMedium
            Text(
                stringResource(R.string.ear_tally, tally.right, tally.asked),
                style = style,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                // Set smaller where its room beside the ▶ is short (2× text on a narrow phone); never wrapped, so the
                // auto-size sees it too wide (IntervalsPart's AnswerButton says why).
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = with(LocalDensity.current) { TALLY_MIN_SIZE.toSp() }, maxFontSize = style.fontSize, stepSize = 0.5.sp),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .widthIn(max = ((maxWidth - REPLAY_SIZE) / 2 - TALLY_GAP).coerceAtLeast(0.dp))
                    .padding(end = 4.dp)
                    .testTag(EarTrainingTestTags.TALLY)
                    .semantics {
                        contentDescription = spoken
                        liveRegion = LiveRegionMode.Polite
                    },
            )
        }
    }
}

/**
 * The ▶, [REPLAY_SIZE] across, that sounds the question again: the style's button ([StyleButton]), filled in
 * the accent as the screen's main action (`main`, as the metronome's Start; until Phase 63 a plain outline).
 */
@Composable
private fun ReplayButton(onReplay: () -> Unit) {
    StyleButton(
        onClick = onReplay,
        label = stringResource(R.string.ear_replay),
        modifier = Modifier.testTag(EarTrainingTestTags.REPLAY),
        face = REPLAY_SIZE,
        main = true,
    ) {
        // Nudged right, so the triangle looks centred in the face.
        Icon(painterResource(DesignR.drawable.ic_play), contentDescription = null, modifier = Modifier.padding(start = 3.dp).size(REPLAY_ICON_SIZE))
    }
}

/**
 * Play it back under the ▶ (for notes in a row, a dot for each first, those found filled in the in-tune colour):
 * in the note's place, while nothing is heard, what to do, "Play it on your instrument" ("Play them…"), in the
 * tuner's waiting form ([WaitingHint], as its "Play a string"); then the note the tuner hears as the player plays,
 * large ("F♯" with its octave small), and a small line under it ([HeardLine]). Phase 65: until then the prompt
 * stood above a dimmed dash and "Listening…", three lines saying little over an empty screen. Once every note is
 * found, a soft glow in the in-tune colour eases in behind the note as the tuner's glow does (0.4 s in, 0.6 s
 * out). All in a box of steady height, so nothing moves; with the microphone not allowed, "Microphone is off"
 * and its button in that box.
 */
@Composable
private fun PlayItBackPart(
    state: EarTrainingState,
    question: NoteQuestion,
    heard: Note?,
    notation: Notation,
    mic: MicPermissionStatus,
    onAllowMic: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val many = question.notes.size > 1
    if (many) {
        Spacer(Modifier.height(8.dp))
        NoteDots(question.notes.size, state.found)
    }
    Spacer(Modifier.height(12.dp))
    Column(
        Modifier.fillMaxWidth().heightIn(min = HEARD_HEIGHT),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (mic != MicPermissionStatus.GRANTED) {
            Text(
                stringResource(R.string.ear_mic_off),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(EarTrainingTestTags.MIC_OFF),
            )
            val forGood = mic == MicPermissionStatus.PERMANENTLY_DENIED
            TwangleTextButton(onClick = if (forGood) onOpenSettings else onAllowMic, modifier = Modifier.testTag(EarTrainingTestTags.MIC_BUTTON)) {
                Text(stringResource(if (forGood) R.string.ear_open_settings else R.string.ear_mic_allow), textAlign = TextAlign.Center)
            }
        } else {
            Box(Modifier.fillMaxWidth().heightIn(min = WAITING_SLOT), contentAlignment = Alignment.Center) {
                NoteSlot()
                if (heard == null) {
                    WaitingHint(stringResource(if (many) R.string.ear_play_prompt_many else R.string.ear_play_prompt), Modifier.matchParentSize())
                } else {
                    HeardNote(heard, notation, glowing = state.allFound)
                }
            }
            Spacer(Modifier.height(4.dp))
            HeardLine(state, question, heard)
        }
    }
}

/**
 * Unseen, a note with its octave, so the note's place is as tall whether a note is heard or not: nothing moves,
 * and the waiting hint has that room.
 */
@Composable
private fun NoteSlot() {
    val unseen = SpanStyle(fontSize = OCTAVE_SIZE, baselineShift = BaselineShift.Subscript)
    Text(
        buildAnnotatedString {
            append("A")
            withStyle(unseen) { append("0") }
        },
        style = MaterialTheme.typography.displayMedium.copy(textDirection = TextDirection.Ltr),
        color = Color.Transparent,
        maxLines = 1,
        modifier = Modifier.clearAndSetSemantics {},
    )
}

/**
 * What to do while nothing is heard, in the note's place, as the tuner's waiting line: the display face at the
 * tuner's hint size, muted, on at most two lines, set smaller where it doesn't fit its place ([modifier]: the
 * note's slot).
 */
@Composable
private fun WaitingHint(text: String, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.displayLarge.copy(fontSize = HINT_SIZE, lineHeight = HINT_LINE_HEIGHT)
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text,
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = HINT_MAX_LINES,
            autoSize = TextAutoSize.StepBased(
                minFontSize = with(LocalDensity.current) { HINT_MIN_SIZE.toSp() },
                maxFontSize = HINT_SIZE,
                stepSize = 1.sp,
            ),
            modifier = Modifier.testTag(EarTrainingTestTags.WAITING),
        )
    }
}

/** The note heard, large, with a soft in-tune glow behind it while [glowing]. */
@Composable
private fun HeardNote(heard: Note, notation: Notation, glowing: Boolean) {
    val glow by animateFloatAsState(if (glowing) 1f else 0f, meterGlowSpec(glowing), label = "ear_right_glow")
    val inTune = MaterialTheme.styleTokens.inTune
    val style = MaterialTheme.typography.displayMedium.copy(textDirection = TextDirection.Ltr)
    val glowModifier = Modifier.drawBehind {
        // Read here, in the draw phase, so the glow easing in redraws without recomposing.
        val alpha = glow
        if (alpha > 0f) {
            val radius = size.maxDimension * GLOW_REACH
            drawCircle(
                Brush.radialGradient(listOf(inTune.copy(alpha = GLOW_ALPHA * alpha), Color.Transparent), center, radius),
                radius,
                center,
            )
        }
    }
    val speech = rememberNoteNameSpeech()
    val name = notation.nameOf(heard.pitchClass)
    val spoken = stringResource(R.string.ear_spoken_heard, speech.name(name) + " " + heard.octave)
    Text(
        buildAnnotatedString {
            append(musicText(name))
            withStyle(SpanStyle(fontSize = OCTAVE_SIZE, baselineShift = BaselineShift.Subscript)) { append(heard.octave.toString()) }
        },
        style = style,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        // Its text stays in the tree for the tests' checks; TalkBack says the description.
        modifier = Modifier.testTag(EarTrainingTestTags.HEARD).then(glowModifier).semantics {
            contentDescription = spoken
            liveRegion = LiveRegionMode.Polite
        },
    )
}

/**
 * The small line under the note heard, one at a time: "Microphone stopped" while it has (the waiting hint
 * above it; it listens again when the screen comes back); "Right" in the in-tune colour once every note
 * is found; "Not that one" after a wrong note held, until a right one (never red); for notes in a row
 * "Note 2 of 3"; else "Hold the note you heard" while a note is heard, and nothing while none (the hint above
 * says what to do; it keeps its line, so nothing moves).
 */
@Composable
private fun HeardLine(state: EarTrainingState, question: NoteQuestion, heard: Note?) {
    val size = question.notes.size
    val text = when {
        state.micStopped -> stringResource(R.string.ear_mic_stopped)
        state.allFound -> stringResource(R.string.ear_right)
        state.wrongHeld -> stringResource(R.string.ear_not_that_one)
        size > 1 -> stringResource(R.string.ear_note_of, (state.found + 1).coerceAtMost(size), size)
        heard != null -> stringResource(R.string.ear_hold)
        else -> ""
    }
    val right = state.allFound && !state.micStopped
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (right) FontWeight.Medium else FontWeight.Normal),
        color = if (right) MaterialTheme.styleTokens.inTune else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        // Across the column, so an empty line (while waiting) keeps its place and height too.
        modifier = Modifier.fillMaxWidth().testTag(EarTrainingTestTags.HEARD_LINE).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * A dot for each of [count] notes in a row: the [found] ones filled in the in-tune colour, the one looked
 * for a ring with a dot in it (◉, in the muted colour), the rest open rings. The one looked for differs
 * from the found ones by its shape, not by colour alone (Phase 65: both were filled discs). TalkBack hears
 * the same from the line under the note ("Note 2 of 3").
 */
@Composable
private fun NoteDots(count: Int, found: Int) {
    val inTune = MaterialTheme.styleTokens.inTune
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.testTag(EarTrainingTestTags.DOTS).size(DOT_SIZE * count + DOT_GAP * (count - 1), DOT_SIZE)) {
        val dot = DOT_SIZE.toPx()
        val step = dot + DOT_GAP.toPx()
        val stroke = 1.dp.toPx()
        val ring = LOOKED_FOR_RING.toPx()
        for (i in 0 until count) {
            // Left to right in every language, as the notes are played.
            val centre = Offset(dot / 2 + i * step, dot / 2)
            when {
                i < found -> drawCircle(inTune, dot / 2, centre)
                i == found -> {
                    drawCircle(muted, dot / 2 - ring / 2, centre, style = Stroke(ring))
                    drawCircle(muted, LOOKED_FOR_CORE.toPx(), centre)
                }
                else -> drawCircle(muted, dot / 2 - stroke / 2, centre, style = Stroke(stroke))
            }
        }
    }
}

/**
 * The question stops sounding when the screen is hidden ([onHidden], `ON_STOP`: the app goes to the
 * background, the phone locks) and goes on when it shows again ([onShown], `ON_START`, also as the tool
 * opens); the visit ends when the tool is left ([onLeft]: another tool or tab takes its place), but
 * not when the activity is only recreated (the phone turns, dark mode switches): then the visit goes on.
 */
@Composable
private fun EndVisitWhenLeft(onShown: () -> Unit, onHidden: () -> Unit, onLeft: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activity = LocalContext.current.findActivity()
    val currentOnShown by rememberUpdatedState(onShown)
    val currentOnHidden by rememberUpdatedState(onHidden)
    val currentOnLeft by rememberUpdatedState(onLeft)
    DisposableEffect(lifecycle, activity) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> currentOnShown()
                Lifecycle.Event.ON_STOP -> currentOnHidden()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (activity?.isChangingConfigurations != true) currentOnLeft()
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Under the exercise row (48 dp): the ▶ where it was (20 dp until Phase 77, while the options button's 52 dp face made the row 4 dp taller). */
private val QUESTION_TOP = 24.dp

/** The What is asked panel as wide as the top row (a phone's whole column), up to this on a tablet: its twenty chord types three to a row. */
private val CHOICES_PANEL_MAX_WIDTH = 400.dp

/** The column at most this wide, in the middle of a tablet. */
private val COLUMN_MAX_WIDTH = 480.dp

/** From this wide, a window wider than tall is laid out in two halves (as the Practice tools on their side). */
private val HALVES_MIN_WIDTH = 600.dp


/** Between the tally and the ▶ at least. */
private val TALLY_GAP = 8.dp

/** The ▶: as the mock-up's, a round button this big, its triangle this big. */
private val REPLAY_SIZE = 72.dp
private val REPLAY_ICON_SIZE = 32.dp

/** The heard note's box: tall enough for the big note and its line, so nothing moves when they change. */
private val HEARD_HEIGHT = 96.dp

/** The note's place at least this tall, so the waiting hint fits two lines at its size. */
private val WAITING_SLOT = 72.dp

/** The waiting hint, as the tuner's (NoteDisplay's WaitingLine): its size, line height, smallest size and lines. */
private val HINT_SIZE = 28.sp
private val HINT_LINE_HEIGHT = 1.2.em
private val HINT_MIN_SIZE = 10.dp
private const val HINT_MAX_LINES = 2

/** The right note's glow: the in-tune colour this opaque at its middle, fading out this far beyond the note's box (as the mock-up's). */
private const val GLOW_ALPHA = 0.34f
private const val GLOW_REACH = 0.9f

/** Notes in a row: each dot this big, this far apart. */
private val DOT_SIZE = 10.dp
private val DOT_GAP = 8.dp

/** The note looked for: a ring this thick around a dot this wide (its radius), a clear gap between them. */
private val LOOKED_FOR_RING = 1.5.dp
private val LOOKED_FOR_CORE = 2.dp

/** The tally set no smaller than this to fit; in dp, so it holds at any font size. */
private val TALLY_MIN_SIZE = 8.dp

/** The octave after the note's name, relative to it. */
private val OCTAVE_SIZE = 0.45.em
