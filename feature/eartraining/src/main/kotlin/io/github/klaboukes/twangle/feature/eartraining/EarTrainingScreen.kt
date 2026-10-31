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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
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
import io.github.klaboukes.twangle.core.designsystem.StyleButton
import io.github.klaboukes.twangle.core.designsystem.OptionsButton
import io.github.klaboukes.twangle.core.designsystem.OPTIONS_FACE
import io.github.klaboukes.twangle.core.designsystem.SegmentedSwitch
import io.github.klaboukes.twangle.core.designsystem.TwangleTextButton
import io.github.klaboukes.twangle.core.designsystem.meter.meterGlowSpec
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.designsystem.styleTokens
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
 * hidden; leaving the tool ends the visit (its tally and questions are dropped, never saved), while a
 * turned phone keeps it. [header] is the top row (the tool button, the instrument button and the
 * tuning line), which `:app` builds; note names follow [notation].
 */
@Composable
fun EarTrainingScreen(
    listener: PitchListener,
    tonePlayer: TonePlayer,
    settingsRepository: SettingsRepository,
    notation: Notation,
    header: @Composable () -> Unit,
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
    EndVisitWhenLeft(onHidden = viewModel::stopSound, onLeft = viewModel::endVisit)

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
 * ([header]), the exercise row (the three icons, this visit's tally, the "What is asked" button), the
 * ▶ that sounds the question again and the exercise's own part under it. Play it back's part is the
 * prompt and the note [heard] (in [notation]), or "Listening…"; with the microphone not allowed ([mic]),
 * "Microphone is off" with a button that asks again ([onAllowMic]) or, refused for good, opens
 * Settings ([onOpenSettings]). Intervals' part is the question and its answer buttons ([onPickInterval],
 * [IntervalsPart]), Chord type's the same with a button per chord type ([onPickChordType],
 * [ChordTypePart]), or on the banjo the Chords tool's quiet line in its place. The choices panel opens over it and keeps open across a turned phone.
 *
 * One column, at most [COLUMN_MAX_WIDTH] wide in the middle; on its side (at least [HALVES_MIN_WIDTH]
 * wide and wider than tall) the top rows fill the start half and the question stands in the middle of
 * the end half. Each scrolls only where the window is too short (large text); Chord type's answers
 * instead scroll in their own box, which takes the room under its question. Nothing moves when the tally appears or the heard note
 * changes: the tally fills room the row keeps, the note a box of steady height.
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
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var choicesOpen by rememberSaveable { mutableStateOf(false) }
    if (choicesOpen) {
        ChoicesPanel(state, onNoteChoice, onNotesInRow, onNoteMatch, onToggleInterval, onToggleDirection, onToggleChordType, onDismiss = { choicesOpen = false })
    }
    val topRows = @Composable {
        Spacer(Modifier.height(HEADER_TOP))
        header()
        Spacer(Modifier.height(8.dp))
        ExerciseRow(state.exercise, state.tally, onPickExercise, choicesOpen = choicesOpen, onOpenChoices = { choicesOpen = true })
    }
    // Chord type's answers scroll in their own box, which takes the room left under the question, so
    // the ▶ and the question stay put; its column doesn't scroll then.
    val answersFillRoom = state.exercise == Exercise.CHORD_TYPE && state.chord != null && state.chordsUnavailable == null
    val question: @Composable ColumnScope.() -> Unit = {
        when (state.exercise) {
            Exercise.PLAY_IT_BACK -> if (state.notes != null) {
                ReplayButton(onReplay)
                Spacer(Modifier.height(8.dp))
                PlayItBackPart(state, state.notes, heard, notation, mic, onAllowMic, onOpenSettings)
            }
            Exercise.INTERVALS -> if (state.interval != null) {
                ReplayButton(onReplay)
                Spacer(Modifier.height(8.dp))
                IntervalsPart(state, state.interval, onPickInterval)
            }
            Exercise.CHORD_TYPE -> when {
                // The banjo, or a tuning with no shape for the types chosen: the Chords tool's quiet line, no ▶.
                state.chordsUnavailable != null -> ChordsUnavailableLine(state.chordsUnavailable)
                state.chord != null -> {
                    ReplayButton(onReplay)
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
            Row(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
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

    /** The "What is asked" button (sliders), and the panel it opens. */
    const val CHOICES = "ear_choices"
    const val CHOICES_PANEL = "ear_choices_panel"

    /** A choice of notes in that panel, e.g. "ear_notes_open_strings". */
    fun noteChoice(choice: NoteChoice): String = "ear_notes_" + choice.name.lowercase()

    /** A choice of notes in a row in that panel, 1 to 4, e.g. "ear_in_row_2"; and the "Any octave counts" switch. */
    fun notesInRow(count: Int): String = "ear_in_row_$count"
    const val ANY_OCTAVE = "ear_any_octave"

    /** The round ▶ that sounds the question again. */
    const val REPLAY = "ear_replay"

    /** Play it back: the note the tuner hears (a dimmed dash while none, hidden from TalkBack). */
    const val HEARD = "ear_heard"

    /** Play it back: the small line under it ("Listening…", "Hold the note you heard", "Note 2 of 3", "Right", "Not that one"). */
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
 * The exercise row: the three exercises in one switch of words ([ExerciseSwitch]), this visit's [tally] once a
 * question was answered, quiet, at the end, and the "What is asked" button: the shared options button
 * ([OptionsButton], the metronome's), picked while its sheet is open ([choicesOpen]). The switch's width
 * depends only on the row's ([exerciseSwitchWidth]), so the words and the button never move: the tally takes
 * the room between them.
 */
@Composable
private fun ExerciseRow(exercise: Exercise, tally: Tally, onPick: (Exercise) -> Unit, choicesOpen: Boolean, onOpenChoices: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val switchWidth = exerciseSwitchWidth(maxWidth)
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            ExerciseSwitch(exercise, onPick, Modifier.width(switchWidth))
            Box(Modifier.weight(1f).padding(start = 8.dp, end = 4.dp), contentAlignment = Alignment.CenterEnd) {
                if (tally.asked > 0) {
                    val spoken = stringResource(R.string.ear_tally_spoken, tally.right, tally.asked)
                    val style = MaterialTheme.typography.bodyMedium
                    Text(
                        stringResource(R.string.ear_tally, tally.right, tally.asked),
                        style = style,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        // Set smaller where the room between the switch and the button is short (2× text on a narrow phone);
                        // never wrapped, so the auto-size sees it too wide (IntervalsPart's AnswerButton says why).
                        softWrap = false,
                        autoSize = TextAutoSize.StepBased(minFontSize = with(LocalDensity.current) { TALLY_MIN_SIZE.toSp() }, maxFontSize = style.fontSize, stepSize = 0.5.sp),
                        modifier = Modifier.testTag(EarTrainingTestTags.TALLY).semantics {
                            contentDescription = spoken
                            liveRegion = LiveRegionMode.Polite
                        },
                    )
                }
            }
            OptionsButton(
                open = choicesOpen,
                onClick = onOpenChoices,
                label = stringResource(R.string.ear_choices),
                modifier = Modifier.testTag(EarTrainingTestTags.CHOICES),
            )
        }
    }
}

/**
 * The exercise switch's width in a row [row] wide: [EXERCISE_SWITCH_MAX], or less where the row is narrower,
 * keeping the options button's face and [TALLY_ROOM] beside it. The same whatever the language, the font size and
 * the exercise picked, so nothing moves; the words are set smaller where they don't fit.
 */
internal fun exerciseSwitchWidth(row: Dp): Dp = (row - OPTIONS_FACE - TALLY_ROOM).coerceIn(0.dp, EXERCISE_SWITCH_MAX)

/**
 * The three exercises in the app's switch of words ([SegmentedSwitch], as the tuner's Strings · Chromatic; Phase 63:
 * three note-symbol icons were hard to tell apart): a short word each ("Play back · Intervals · Chords"), TalkBack
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

/** The ▶, [REPLAY_SIZE] across, that sounds the question again: the style's button ([StyleButton]). */
@Composable
private fun ReplayButton(onReplay: () -> Unit) {
    StyleButton(
        onClick = onReplay,
        label = stringResource(R.string.ear_replay),
        modifier = Modifier.testTag(EarTrainingTestTags.REPLAY),
        face = REPLAY_SIZE,
    ) {
        // Nudged right, so the triangle looks centred in the face.
        Icon(PLAY_ICON, contentDescription = null, modifier = Modifier.padding(start = 3.dp).size(REPLAY_ICON_SIZE))
    }
}

/**
 * Play it back under the ▶: "Play it on your instrument" ("Play them…" for notes in a row, with a dot
 * for each, those found filled in the in-tune colour), then the note the tuner hears as the player
 * plays, large ("F♯" with its octave small), or a dimmed dash while nothing is heard, and a small
 * line under it ([HeardLine]). Once every note is found, a soft glow in the in-tune colour eases in
 * behind the note as the tuner's glow does (0.4 s in, 0.6 s out). All in a box of steady height, so
 * nothing moves; with the microphone not allowed, "Microphone is off" and its button in that box.
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
    Text(
        stringResource(if (many) R.string.ear_play_prompt_many else R.string.ear_play_prompt),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    if (many) {
        Spacer(Modifier.height(14.dp))
        NoteDots(question.notes.size, state.found)
    }
    Spacer(Modifier.height(16.dp))
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
            HeardNote(heard, notation, glowing = state.allFound)
            Spacer(Modifier.height(4.dp))
            HeardLine(state, question, heard)
        }
    }
}

/** The note heard, large, with a soft in-tune glow behind it while [glowing]; a dimmed dash (silent for TalkBack) while none. */
@Composable
private fun HeardNote(heard: Note?, notation: Notation, glowing: Boolean) {
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
    if (heard == null) {
        // An unseen octave digit either side, so the dash's line is as tall as a note's with its octave and stays centred: nothing moves.
        val unseen = SpanStyle(fontSize = OCTAVE_SIZE, baselineShift = BaselineShift.Subscript, color = Color.Transparent)
        Text(
            buildAnnotatedString {
                withStyle(unseen) { append("0") }
                append("—")
                withStyle(unseen) { append("0") }
            },
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DASH_ALPHA),
            maxLines = 1,
            modifier = Modifier.testTag(EarTrainingTestTags.HEARD).then(glowModifier).clearAndSetSemantics {},
        )
    } else {
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
}

/**
 * The small line under the note heard, one at a time: "Right" in the in-tune colour once every note
 * is found; "Not that one" after a wrong note held, until a right one (never red); for notes in a row
 * "Note 2 of 3"; else "Hold the note you heard" while a note is heard, "Listening…" while none.
 */
@Composable
private fun HeardLine(state: EarTrainingState, question: NoteQuestion, heard: Note?) {
    val size = question.notes.size
    val text = when {
        state.allFound -> stringResource(R.string.ear_right)
        state.wrongHeld -> stringResource(R.string.ear_not_that_one)
        size > 1 -> stringResource(R.string.ear_note_of, (state.found + 1).coerceAtMost(size), size)
        heard != null -> stringResource(R.string.ear_hold)
        else -> stringResource(R.string.ear_listening)
    }
    val right = state.allFound
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (right) FontWeight.Medium else FontWeight.Normal),
        color = if (right) MaterialTheme.styleTokens.inTune else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag(EarTrainingTestTags.HEARD_LINE).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** A dot for each of [count] notes in a row: the [found] ones filled in the in-tune colour, the one looked for in the muted colour, the rest open. */
@Composable
private fun NoteDots(count: Int, found: Int) {
    val inTune = MaterialTheme.styleTokens.inTune
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.testTag(EarTrainingTestTags.DOTS).size(DOT_SIZE * count + DOT_GAP * (count - 1), DOT_SIZE)) {
        val dot = DOT_SIZE.toPx()
        val step = dot + DOT_GAP.toPx()
        val stroke = 1.dp.toPx()
        for (i in 0 until count) {
            // Left to right in every language, as the notes are played.
            val centre = Offset(dot / 2 + i * step, dot / 2)
            when {
                i < found -> drawCircle(inTune, dot / 2, centre)
                i == found -> drawCircle(muted, dot / 2, centre)
                else -> drawCircle(muted, dot / 2 - stroke / 2, centre, style = Stroke(stroke))
            }
        }
    }
}

/**
 * The question stops sounding when the screen is hidden ([onHidden]: the app goes to the background,
 * or another tool or tab takes its place), and the visit ends when the tool is left ([onLeft]), but
 * not when the activity is only recreated (the phone turns, dark mode switches): then the visit goes on.
 */
@Composable
private fun EndVisitWhenLeft(onHidden: () -> Unit, onLeft: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activity = LocalContext.current.findActivity()
    val currentOnHidden by rememberUpdatedState(onHidden)
    val currentOnLeft by rememberUpdatedState(onLeft)
    DisposableEffect(lifecycle, activity) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) currentOnHidden() }
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

/** ▶, filled, its corners softened (as the drone button's). */
private val PLAY_ICON: ImageVector = ImageVector.Builder(
    name = "ear_replay",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).addPath(
    pathData = addPathNodes("M8,5.5L19,12L8,18.5z"),
    fill = SolidColor(Color.Black),
    stroke = SolidColor(Color.Black),
    strokeLineWidth = 2f,
    strokeLineJoin = StrokeJoin.Round,
).build()


private val HEADER_TOP = 8.dp
/** Under the exercise row: 24 dp less the 4 dp the options button's 52 dp face adds to the row, so the ▶ keeps its place. */
private val QUESTION_TOP = 20.dp

/** The column at most this wide, in the middle of a tablet. */
private val COLUMN_MAX_WIDTH = 480.dp

/** From this wide, a window wider than tall is laid out in two halves (as the Practice tools on their side). */
private val HALVES_MIN_WIDTH = 600.dp

/** The exercise switch at most this wide (each word's segment a third of it), and the room it leaves for the tally at least. */
private val EXERCISE_SWITCH_MAX = 252.dp
private val TALLY_ROOM = 56.dp

/** The ▶: as the mock-up's, a round button this big, its triangle this big. */
private val REPLAY_SIZE = 72.dp
private val REPLAY_ICON_SIZE = 32.dp

/** The heard note's box: tall enough for the big note and its line, so nothing moves when they change. */
private val HEARD_HEIGHT = 96.dp

/** The dash while no note is heard, this opaque in the muted colour. */
private const val DASH_ALPHA = 0.55f

/** The right note's glow: the in-tune colour this opaque at its middle, fading out this far beyond the note's box (as the mock-up's). */
private const val GLOW_ALPHA = 0.34f
private const val GLOW_REACH = 0.9f

/** Notes in a row: each dot this big, this far apart. */
private val DOT_SIZE = 10.dp
private val DOT_GAP = 8.dp

/** The tally set no smaller than this to fit; in dp, so it holds at any font size. */
private val TALLY_MIN_SIZE = 8.dp

/** The octave after the note's name, relative to it. */
private val OCTAVE_SIZE = 0.45.em
