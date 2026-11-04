package io.github.klaboukes.twangle.feature.eartraining

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.CHOICE_FACE_GAP
import io.github.klaboukes.twangle.core.designsystem.CHOICE_ROW_GAP
import io.github.klaboukes.twangle.core.designsystem.ChoiceRow
import io.github.klaboukes.twangle.core.designsystem.PanelLabel
import io.github.klaboukes.twangle.core.designsystem.PickGrid
import io.github.klaboukes.twangle.core.designsystem.SheetUnderNavigationBar
import io.github.klaboukes.twangle.core.designsystem.SwitchRow
import io.github.klaboukes.twangle.core.designsystem.TextChoiceFace
import io.github.klaboukes.twangle.core.designsystem.appLocale
import io.github.klaboukes.twangle.core.designsystem.localDigits
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.eartraining.Exercise
import io.github.klaboukes.twangle.core.dsp.eartraining.Interval
import io.github.klaboukes.twangle.core.dsp.eartraining.IntervalDirection
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteChoice
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteMatch
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteQuestions

/**
 * "What is asked" (the owner's pick A: questions start at once, and this panel opens any time): the
 * choices of the [exercise] shown, in a panel from the bottom as the instrument and tuning panel. A
 * pick takes effect at once ([onNoteChoice]: a question the new choice doesn't offer gives way to a new
 * one) and the panel stays open for more; a tap beside it, a swipe down or Back closes it ([onDismiss]).
 *
 * Play it back picks its notes (the open strings, the natural notes to the 5th fret, or every note to
 * the 12th, on the instrument and tuning picked), how many in a row (1 to 4, [onNotesInRow]) and whether
 * any octave counts ([onNoteMatch]), as the mock-up's panel. Intervals picks its intervals, minor
 * second to octave, from twelve buttons ([onToggleInterval]) and how they are played, up, down or
 * together ([onToggleDirection]): a tap adds or takes away, and the last one picked stays. Chord type
 * picks its types from the Chords tool's twenty, in its order ([onToggleChordType]), the same way.
 * Everything picked here is saved ([EarTrainingViewModel]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChoicesPanel(
    state: EarTrainingState,
    onNoteChoice: (NoteChoice) -> Unit,
    onNotesInRow: (Int) -> Unit,
    onNoteMatch: (NoteMatch) -> Unit,
    onToggleInterval: (Interval) -> Unit,
    onToggleDirection: (IntervalDirection) -> Unit,
    onToggleChordType: (ChordQuality) -> Unit,
    onDismiss: () -> Unit,
) {
    val exercise = state.exercise
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scheme = MaterialTheme.colorScheme
    // The whole panel, with its handle and the room for the navigation bar under it.
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize.height
    val navigationBar = WindowInsets.navigationBars.getBottom(density)
    val maxHeight = with(density) { (window * PANEL_MAX_HEIGHT - navigationBar).toDp() - HANDLE_HEIGHT }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = PANEL_SHAPE,
        containerColor = scheme.surface,
        contentColor = scheme.onSurface,
        modifier = Modifier.testTag(EarTrainingTestTags.CHOICES_PANEL),
    ) {
        // The sheet's colour under the gesture bar too, no white strip there.
        SheetUnderNavigationBar()
        Column(
            Modifier
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(start = LIST_PADDING, end = LIST_PADDING, bottom = 22.dp),
        ) {
            Text(
                stringResource(R.string.ear_choices),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(start = PANEL_PADDING - LIST_PADDING, end = PANEL_PADDING - LIST_PADDING, top = 4.dp, bottom = 6.dp)
                    .semantics { heading() },
            )
            when (exercise) {
                Exercise.PLAY_IT_BACK -> {
                    val notes = stringResource(R.string.ear_notes)
                    GroupLabel(notes)
                    Column(Modifier.selectableGroup().semantics { contentDescription = notes }) {
                        for (choice in NoteChoice.entries) {
                            ChoiceRow(
                                stringResource(choice.label),
                                selected = choice == state.noteChoice,
                                onClick = { onNoteChoice(choice) },
                                modifier = Modifier.padding(top = CHOICE_ROW_GAP).testTag(EarTrainingTestTags.noteChoice(choice)),
                            )
                        }
                    }
                    // A count, so separate faces as the metronome's values, not a switch (ui.md, *Shared controls*).
                    val inRow = stringResource(R.string.ear_notes_in_row)
                    GroupLabel(inRow)
                    val locale = appLocale()
                    Row(
                        Modifier
                            .padding(top = 2.dp)
                            .fillMaxWidth()
                            .selectableGroup()
                            .semantics { contentDescription = inRow },
                        horizontalArrangement = Arrangement.spacedBy(CHOICE_FACE_GAP),
                    ) {
                        for (count in NOTES_IN_ROW) {
                            val digits = localDigits(count, locale)
                            TextChoiceFace(
                                text = digits,
                                name = digits,
                                picked = count == state.notesInRow,
                                onPick = { onNotesInRow(count) },
                                modifier = Modifier.weight(1f).testTag(EarTrainingTestTags.notesInRow(count)),
                            )
                        }
                    }
                    SwitchRow(
                        text = stringResource(R.string.ear_any_octave),
                        checked = state.noteMatch == NoteMatch.ANY_OCTAVE,
                        onCheckedChange = { onNoteMatch(if (it) NoteMatch.ANY_OCTAVE else NoteMatch.EXACT) },
                        modifier = Modifier.padding(top = GROUP_GAP).testTag(EarTrainingTestTags.ANY_OCTAVE),
                    )
                }
                Exercise.INTERVALS -> {
                    val intervals = stringResource(R.string.ear_exercise_intervals)
                    GroupLabel(intervals)
                    ToggleGrid(
                        options = Interval.entries,
                        on = { it in state.intervals },
                        onToggle = onToggleInterval,
                        groupLabel = intervals,
                        label = { stringResource(it.sign) },
                        spoken = { stringResource(it.label) },
                        tag = EarTrainingTestTags::askInterval,
                    )
                    val played = stringResource(R.string.ear_played)
                    GroupLabel(played)
                    ToggleGrid(
                        options = IntervalDirection.entries,
                        on = { it in state.directions },
                        onToggle = onToggleDirection,
                        groupLabel = played,
                        label = { stringResource(it.label) },
                        spoken = { stringResource(it.label) },
                        tag = EarTrainingTestTags::direction,
                    )
                }
                Exercise.CHORD_TYPE -> {
                    val types = stringResource(R.string.ear_exercise_chord_type)
                    GroupLabel(types)
                    ToggleGrid(
                        options = ChordQuality.inPanel,
                        on = { it in state.chordTypes },
                        onToggle = onToggleChordType,
                        groupLabel = types,
                        label = { chordSign(it).text },
                        spoken = { stringResource(it.words) },
                        tag = EarTrainingTestTags::askChordType,
                    )
                }
            }
        }
    }
}

/** Each choice of notes by name. A new choice needs its name here (the `when` says so). */
private val NoteChoice.label: Int
    get() = when (this) {
        NoteChoice.OPEN_STRINGS -> R.string.ear_notes_open_strings
        NoteChoice.NATURALS_TO_5TH -> R.string.ear_notes_naturals
        NoteChoice.ALL_TO_12TH -> R.string.ear_notes_all
    }

/** A group's name in the panel: the shared small label ([PanelLabel]; until Phase 77 a heading of its own in the accent). */
@Composable
private fun GroupLabel(text: String) = PanelLabel(text, Modifier.padding(top = GROUP_GAP - 4.dp))

/**
 * Choices that are [on] or off among several (an interval, up, down or together, a chord type): the shared pick
 * buttons as checkboxes ([PickGrid] with toggles, until Phase 77 chips of their own), three to a row, every one the
 * same width. A tap adds or takes away ([onToggle]); TalkBack hears a group named [groupLabel], each a checkbox named
 * by [spoken].
 */
@Composable
private fun <T> ToggleGrid(
    options: List<T>,
    on: (T) -> Boolean,
    onToggle: (T) -> Unit,
    groupLabel: String,
    label: @Composable (T) -> String,
    spoken: @Composable (T) -> String,
    tag: (T) -> String,
) = PickGrid(
    options = options,
    picked = on,
    onPick = onToggle,
    perRow = CHOICES_PER_ROW,
    groupLabel = groupLabel,
    label = label,
    spoken = spoken,
    tag = tag,
    modifier = Modifier.padding(top = 2.dp),
    toggles = true,
)

/** Play it back's choice of notes in a row: one to four. */
private val NOTES_IN_ROW = (1..NoteQuestions.MAX_LENGTH).toList()

/** The panel's top corners, as the instrument and tuning panel's: 22 dp, Ink's large shape. */
private val PANEL_SHAPE: Shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)

/** The panel is at most this share of the window's height, so the question shows above it. */
private const val PANEL_MAX_HEIGHT = 0.74f

/** Material's drag handle at the panel's top: 4 dp with 22 dp above and below. */
private val HANDLE_HEIGHT = 48.dp
private val PANEL_PADDING = 20.dp
private val LIST_PADDING = 12.dp

/** Above each group's name, and above Any octave. */
private val GROUP_GAP = 10.dp

/** The toggles three to a row: twelve intervals in four rows, up · down · together in one, twenty chord types in seven. */
private const val CHOICES_PER_ROW = 3
