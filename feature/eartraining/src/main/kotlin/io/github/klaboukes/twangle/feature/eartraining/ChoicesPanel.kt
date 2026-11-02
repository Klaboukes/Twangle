package io.github.klaboukes.twangle.feature.eartraining

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.SheetUnderNavigationBar
import io.github.klaboukes.twangle.core.designsystem.CHOICE_FACE_GAP
import io.github.klaboukes.twangle.core.designsystem.CHOICE_ROW_GAP
import io.github.klaboukes.twangle.core.designsystem.ChoiceRow
import io.github.klaboukes.twangle.core.designsystem.TextChoiceFace
import io.github.klaboukes.twangle.core.designsystem.appLocale
import io.github.klaboukes.twangle.core.designsystem.faceFill
import io.github.klaboukes.twangle.core.designsystem.faceOutline
import io.github.klaboukes.twangle.core.designsystem.localDigits
import io.github.klaboukes.twangle.core.designsystem.pressFeedback
import io.github.klaboukes.twangle.core.designsystem.pressFlash
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
 * second to octave, from twelve chips ([onToggleInterval]) and how they are played, up, down or
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
                    GroupHeading(stringResource(R.string.ear_notes))
                    Column(Modifier.selectableGroup()) {
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
                    GroupHeading(stringResource(R.string.ear_notes_in_row))
                    val locale = appLocale()
                    Row(
                        Modifier
                            .padding(start = PANEL_PADDING - LIST_PADDING, top = 2.dp)
                            .fillMaxWidth()
                            .selectableGroup(),
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
                        tag = EarTrainingTestTags.ANY_OCTAVE,
                    ) { onNoteMatch(if (it) NoteMatch.ANY_OCTAVE else NoteMatch.EXACT) }
                }
                Exercise.INTERVALS -> {
                    GroupHeading(stringResource(R.string.ear_exercise_intervals))
                    ChipGrid(Interval.entries) { interval ->
                        Chip(
                            text = AnnotatedString(stringResource(interval.sign)),
                            spoken = stringResource(interval.label),
                            on = interval in state.intervals,
                            tag = EarTrainingTestTags.askInterval(interval),
                        ) { onToggleInterval(interval) }
                    }
                    GroupHeading(stringResource(R.string.ear_played))
                    ChipGrid(IntervalDirection.entries) { direction ->
                        val name = stringResource(direction.label)
                        Chip(text = AnnotatedString(name), spoken = name, on = direction in state.directions, tag = EarTrainingTestTags.direction(direction)) {
                            onToggleDirection(direction)
                        }
                    }
                }
                Exercise.CHORD_TYPE -> {
                    GroupHeading(stringResource(R.string.ear_exercise_chord_type))
                    ChipGrid(ChordQuality.inPanel) { type ->
                        Chip(
                            text = chordSign(type),
                            spoken = stringResource(type.words),
                            on = type in state.chordTypes,
                            tag = EarTrainingTestTags.askChordType(type),
                        ) { onToggleChordType(type) }
                    }
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

/** A group's heading in the panel, small in the accent (as the mock-up's). */
@Composable
private fun GroupHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = PANEL_PADDING - LIST_PADDING, top = 10.dp, bottom = 2.dp)
            .semantics { heading() },
    )
}

/** A choice that is on or off: its [text] with a switch at the end; the whole row toggles it (as the drone's Lower). */
@Composable
private fun SwitchRow(text: String, checked: Boolean, tag: String, onCheckedChange: (Boolean) -> Unit) {
    val source = remember { MutableInteractionSource() }
    Row(
        Modifier
            .testTag(tag)
            .padding(top = 10.dp)
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .pressFeedback(source, MaterialTheme.shapes.medium)
            .toggleable(value = checked, interactionSource = source, indication = null, role = Role.Switch) {
                source.pressFlash()
                onCheckedChange(it)
            }
            .padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(vertical = 6.dp),
        )
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** [items] as chips three to a row, every chip the same width and each row as high as its tallest. */
@Composable
private fun <T> ChipGrid(items: List<T>, chip: @Composable (T) -> Unit) {
    Column(
        Modifier.padding(start = PANEL_PADDING - LIST_PADDING, top = 6.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
    ) {
        for (row in items.chunked(CHIPS_PER_ROW)) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(CHIP_GAP)) {
                for (item in row) Box(Modifier.weight(1f).fillMaxHeight()) { chip(item) }
                repeat(CHIPS_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * One choice that is [on] or off among several (an interval, up, down or together), as the mock-up's
 * chips: picked ones with the accent's 2 dp outline on the container colour, the others muted on the
 * see-through face. Its [text] on one line, set smaller where it is long. TalkBack hears a checkbox
 * named [spoken].
 */
@Composable
private fun Chip(text: AnnotatedString, spoken: String, on: Boolean, tag: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val style = MaterialTheme.typography.labelLarge
    Box(
        Modifier
            .testTag(tag)
            .fillMaxWidth()
            .fillMaxHeight()
            .heightIn(min = ROW_HEIGHT)
            .pressFeedback(
                source,
                shape,
                fill = if (on) scheme.primaryContainer else faceFill(),
                outline = if (on) BorderStroke(2.dp, scheme.primary) else faceOutline(),
            )
            .toggleable(value = on, interactionSource = source, indication = null, role = Role.Checkbox) {
                source.pressFlash()
                onClick()
            }
            .semantics { contentDescription = spoken }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = style.copy(fontWeight = FontWeight.Medium),
            color = if (on) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            maxLines = 1,
            // Never wrapped, so the auto-size sees a text too wide (IntervalsPart's AnswerButton says why).
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp),
        )
    }
}

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
private val ROW_HEIGHT = 48.dp

/** The chips, three to a row (twelve intervals in four rows, up · down · together in one, twenty chord types in seven), this far apart. */
private const val CHIPS_PER_ROW = 3
private val CHIP_GAP = 6.dp
