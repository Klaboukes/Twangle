package io.github.klaboukes.twangle.feature.tuner.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.klaboukes.twangle.core.designsystem.StepperButton
import io.github.klaboukes.twangle.core.designsystem.TwangleIconButton
import io.github.klaboukes.twangle.core.designsystem.TwangleTextButton
import io.github.klaboukes.twangle.core.designsystem.appLanguage
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.dsp.music.Instrument
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.CustomTuning
import io.github.klaboukes.twangle.core.settings.SettingsRepository
import io.github.klaboukes.twangle.feature.tuner.R
import io.github.klaboukes.twangle.feature.tuner.components.DIALOG_TITLE_SCALE
import io.github.klaboukes.twangle.feature.tuner.components.instrumentLabel
import io.github.klaboukes.twangle.feature.tuner.components.tuningLabel

/**
 * The tuning editor, a screen of its own (the tuner and its microphone rest meanwhile): a name, then
 * every string of the instrument with its note between − and + buttons that move it a semitone,
 * within [EDITOR_NOTE_RANGE]. With [editingId] null it makes a new tuning for [instrument], starting
 * from its standard notes; with the id of one of [settings]' custom tunings it changes that one (its
 * instrument stays). Save checks the name first (not empty, not already one of the instrument's
 * tunings), then saves, picks the tuning and calls [onClose]; the save runs in [TuningEditorSaver],
 * so turning the phone can't cut it off. A save the phone couldn't write (its storage full) keeps the
 * editor open with its edits and a line saying so; Save tries again. The close button, Back and each new [closeRequest] (a tap
 * on the home-screen widget) leave without saving, after asking "Discard changes?" when the draft has
 * edits. [settings] are the saved settings, followed live.
 */
@Composable
fun TuningEditorScreen(
    settingsRepository: SettingsRepository,
    settings: AppSettings,
    instrument: Instrument,
    editingId: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    closeRequest: Int = 0,
) {
    // Found once: a tuning deleted meanwhile still saves nothing (saveDraft), but the screen stays as it was.
    val editing: CustomTuning? = remember { editingId?.let { id -> settings.customTunings.firstOrNull { it.id == id } } }
    val forInstrument = editing?.instrument ?: instrument
    // What the editor opened with: closing asks first once the draft differs from it.
    val opened = remember { editing?.let(TuningDraft::forEdit) ?: TuningDraft.forNew(forInstrument) }
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(opened) }
    var problem by rememberSaveable { mutableStateOf<NameProblem?>(null) }
    var askingDiscard by rememberSaveable { mutableStateOf(false) }
    val saver: TuningEditorSaver = viewModel { TuningEditorSaver(settingsRepository) }
    val phase by saver.phase.collectAsStateWithLifecycle()
    // Also after turning the phone mid-save: the new screen closes once the save is done.
    LaunchedEffect(phase) {
        if (phase == SavePhase.SAVED) {
            saver.closed()
            onClose()
        }
    }
    val presetNames = forInstrument.tunings.map { tuningLabel(it) }
    val takenNames = presetNames + settings.customTuningsFor(forInstrument).filter { it.id != editing?.id }.map { it.name }
    val requestClose = {
        // While the save runs (a moment) nothing else closes the editor: it closes when the save is done.
        if (phase == SavePhase.IDLE || phase == SavePhase.FAILED) {
            if (draft.hasChangesFrom(opened)) askingDiscard = true else onClose()
        }
    }
    BackHandler(onBack = requestClose)
    // Only requests made while the editor is open: the count may have grown before it opened.
    val openedAtRequest = remember { closeRequest }
    LaunchedEffect(closeRequest) { if (closeRequest != openedAtRequest) requestClose() }
    if (askingDiscard) {
        DiscardChangesDialog(
            onDiscard = {
                askingDiscard = false
                onClose()
            },
            onKeepEditing = { askingDiscard = false },
        )
    }
    TuningEditor(
        draft = draft,
        instrument = forInstrument,
        notation = settings.notationFor(appLanguage()),
        isNew = editing == null,
        problem = problem,
        notSaved = phase == SavePhase.FAILED,
        onNameChange = {
            draft = draft.copy(name = CustomTuning.cutName(it))
            // A message goes as soon as the name changes and only comes back on the next Save.
            problem = null
        },
        onLower = { draft = draft.lowered(it) },
        onRaise = { draft = draft.raised(it) },
        onSave = {
            if (phase == SavePhase.IDLE || phase == SavePhase.FAILED) {
                problem = draft.nameProblem(takenNames)
                if (problem == null) saver.save(draft, forInstrument, editing?.id)
            }
        },
        onClose = requestClose,
        modifier = modifier,
    )
}

/**
 * The editor's page, stateless: a close button and Save along the top, the title ("New tuning" when
 * [isNew], else "Edit tuning") and [instrument], the name field (with [problem]'s message under it,
 * or else a line when the last Save couldn't be written, [notSaved]), then a row per string: its note in the standard tuning, and its note in [draft] between − and +.
 * Notes are written in [notation]; a button at the end of [EDITOR_NOTE_RANGE] is turned off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TuningEditor(
    draft: TuningDraft,
    instrument: Instrument,
    notation: Notation,
    isNew: Boolean,
    problem: NameProblem?,
    notSaved: Boolean,
    onNameChange: (String) -> Unit,
    onLower: (Int) -> Unit,
    onRaise: (Int) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        // See-through, so the style's quiet background shows, as on the settings.
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        // safeDrawing also keeps clear of the keyboard and of a camera cutout at the side.
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            // No title here: the title sits in the page, where it can wrap at a large font.
            TopAppBar(
                title = {},
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    TwangleIconButton(onClick = onClose, modifier = Modifier.testTag(TuningEditorTestTags.CLOSE)) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.tuning_editor_close))
                    }
                },
                actions = {
                    TwangleTextButton(onClick = onSave, modifier = Modifier.testTag(TuningEditorTestTags.SAVE)) {
                        Text(stringResource(R.string.tuning_editor_save))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = CONTENT_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(start = SIDE_PADDING, end = SIDE_PADDING, bottom = 24.dp),
            ) {
                Text(
                    stringResource(if (isNew) R.string.tuner_new_tuning else R.string.tuning_editor_title_edit),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.tuning_editor_instrument, instrumentLabel(instrument)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
                )
                NameField(draft.name, problem, notSaved, onNameChange)
                Text(
                    stringResource(R.string.tuning_editor_strings),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 28.dp, bottom = 4.dp)
                        .semantics { heading() },
                )
                draft.midi.forEachIndexed { index, midi ->
                    StringStepper(
                        standard = instrument.defaultTuning.strings[index],
                        note = Note(midi),
                        notation = notation,
                        instrument = instrument,
                        canLower = draft.canLower(index),
                        canRaise = draft.canRaise(index),
                        onLower = { onLower(index) },
                        onRaise = { onRaise(index) },
                        modifier = Modifier.testTag(TuningEditorTestTags.string(index)),
                    )
                }
            }
        }
    }
}

/**
 * Asks before the editor drops its edits: Discard leaves, Keep editing (and Back, or a tap outside)
 * stays.
 */
@Composable
private fun DiscardChangesDialog(onDiscard: () -> Unit, onKeepEditing: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        modifier = Modifier.testTag(TuningEditorTestTags.DISCARD_DIALOG),
        title = {
            // As the delete question's: the dialog's own headline size is too big at 2× text in the wider faces.
            val title = MaterialTheme.typography.titleMedium
            Text(stringResource(R.string.tuning_editor_discard_title), style = title.copy(fontSize = title.fontSize * DIALOG_TITLE_SCALE))
        },
        dismissButton = {
            TwangleTextButton(onClick = onKeepEditing, modifier = Modifier.testTag(TuningEditorTestTags.KEEP_EDITING)) {
                Text(stringResource(R.string.tuning_editor_keep_editing))
            }
        },
        confirmButton = {
            TwangleTextButton(onClick = onDiscard, modifier = Modifier.testTag(TuningEditorTestTags.DISCARD)) {
                Text(stringResource(R.string.tuning_editor_discard), color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

/**
 * The name, one line. [problem]'s message shows under it only once Save has found one, and goes as
 * soon as the name changes, so nothing flashes while typing; TalkBack reads it when it appears. Else,
 * when the phone couldn't write the last Save ([notSaved]), that line shows there until the next
 * Save: next to the field, so the keyboard can't hide it, without marking the name as wrong.
 */
@Composable
private fun NameField(name: String, problem: NameProblem?, notSaved: Boolean, onNameChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    val message = when (problem) {
        NameProblem.EMPTY -> stringResource(R.string.tuning_editor_name_empty)
        NameProblem.TAKEN -> stringResource(R.string.tuning_editor_name_taken)
        null -> if (notSaved) stringResource(R.string.tuning_editor_not_saved) else null
    }
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.tuning_editor_name)) },
        singleLine = true,
        isError = problem != null,
        supportingText = message?.let {
            {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .then(if (problem == null) Modifier.testTag(TuningEditorTestTags.NOT_SAVED) else Modifier),
                )
            }
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TuningEditorTestTags.NAME),
    )
}

/**
 * One string: which string it is (its [standard] note, "E2 string"), then − [note] +. TalkBack hears
 * "E 2 string, D 2" on the note, said again when it changes, and each button with the string's name.
 */
@Composable
private fun StringStepper(
    standard: Note,
    note: Note,
    notation: Notation,
    instrument: Instrument,
    canLower: Boolean,
    canRaise: Boolean,
    onLower: () -> Unit,
    onRaise: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val speech = rememberNoteNameSpeech()
    val spokenNote = stringResource(R.string.tuner_spoken_string)
    // The standard tuning's own spelling (Open D's F♯), the note now in the notation's.
    val standardName = notation.nameIn(instrument.defaultTuning, standard.pitchClass)
    val noteName = notation.nameOf(note.pitchClass)
    val spokenString = stringResource(R.string.tuning_editor_string, spokenNote.format(speech.name(standardName), standard.octave))
    val spoken = stringResource(R.string.tuning_editor_spoken_note, spokenString, spokenNote.format(speech.name(noteName), note.octave))
    // "E2 string" with the note's sign and octave set like the string buttons'.
    val template = stringResource(R.string.tuning_editor_string, PLACEHOLDER)
    val at = template.indexOf(PLACEHOLDER)
    val caption = buildAnnotatedString {
        append(template.substring(0, at))
        append(noteText(standardName, standard.octave))
        append(template.substring(at + PLACEHOLDER.length))
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            caption,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // The note beside it says it for TalkBack.
            modifier = Modifier
                .weight(1f)
                .semantics { hideFromAccessibility() },
        )
        // − note + reads lower to higher left to right in every language, like the tuner's meter and
        // the string row, which are not mirrored in right-to-left languages.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StepperButton(more = false, label = stringResource(R.string.tuning_editor_lower, spokenString), onClick = onLower, enabled = canLower)
                Text(
                    noteText(noteName, note.octave),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .widthIn(min = NOTE_WIDTH)
                        .semantics {
                            contentDescription = spoken
                            liveRegion = LiveRegionMode.Polite
                        },
                )
                StepperButton(more = true, label = stringResource(R.string.tuning_editor_raise, spokenString), onClick = onRaise, enabled = canRaise)
            }
        }
    }
}

/** A note's name with raised signs and its octave as a small subscript, like the string buttons: E₂, F♯₁. */
private fun noteText(name: String, octave: Int): AnnotatedString = buildAnnotatedString {
    append(musicText(name))
    withStyle(SpanStyle(fontSize = OCTAVE_SIZE, baselineShift = BaselineShift.Subscript)) { append(octave.toString()) }
}

/** Keeps the draft through rotation: the name, then the notes. */
private val DraftSaver = listSaver<TuningDraft, Any>(
    save = { listOf<Any>(it.name) + it.midi },
    restore = { saved -> TuningDraft(saved[0] as String, saved.drop(1).map { it as Int }) },
)

/** Test tags for the editor's UI tests; they don't change with the language. */
object TuningEditorTestTags {
    const val CLOSE = "tuning_editor_close"
    const val SAVE = "tuning_editor_save"
    const val NAME = "tuning_editor_name"
    const val NOT_SAVED = "tuning_editor_not_saved"
    const val DISCARD_DIALOG = "tuning_editor_discard_dialog"
    const val DISCARD = "tuning_editor_discard"
    const val KEEP_EDITING = "tuning_editor_keep_editing"

    /** The row of string [index], thickest first. */
    fun string(index: Int): String = "tuning_editor_string_$index"
}

private const val PLACEHOLDER = "\u0000"
private val OCTAVE_SIZE = 0.6.em
private val CONTENT_MAX_WIDTH = 600.dp
private val SIDE_PADDING = 16.dp
private val ROW_HEIGHT = 56.dp

/** Room for the widest note ("Sol♯4") so the buttons don't move as the note changes. */
private val NOTE_WIDTH = 88.dp

@Preview(showBackground = true)
@Composable
private fun TuningEditorPreview() {
    MaterialTheme {
        TuningEditor(
            draft = TuningDraft.forNew(Instruments.GUITAR).copy(name = "Drop C").lowered(0),
            instrument = Instruments.GUITAR,
            notation = Notation.SHARPS,
            isNew = true,
            problem = null,
            notSaved = true,
            onNameChange = {},
            onLower = {},
            onRaise = {},
            onSave = {},
            onClose = {},
        )
    }
}
