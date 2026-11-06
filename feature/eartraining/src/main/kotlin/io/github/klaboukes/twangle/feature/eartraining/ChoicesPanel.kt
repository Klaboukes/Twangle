package io.github.klaboukes.twangle.feature.eartraining

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.CHOICE_FACE_GAP
import io.github.klaboukes.twangle.core.designsystem.CHOICE_ROW_GAP
import io.github.klaboukes.twangle.core.designsystem.ChoiceRow
import io.github.klaboukes.twangle.core.designsystem.FloatingPanel
import io.github.klaboukes.twangle.core.designsystem.PanelLabel
import io.github.klaboukes.twangle.core.designsystem.PickGrid
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
 * choices of the exercise shown, while [open], in the app's floating panel ([FloatingPanel], as the metronome's
 * options) under the ⋯ that opened it ([anchor]), their ends in line, [width] wide (the top row's); it scrolls where
 * the window is short. Until Phase 77 a sheet from the bottom, as the instrument and tuning sheet. A
 * pick takes effect at once ([onNoteChoice]: a question the new choice doesn't offer gives way to a new
 * one) and the panel stays open for more; a tap beside it or Back closes it ([onDismiss]). TalkBack hears it by
 * its name, "What is asked"; it shows no title of its own, as the metronome's.
 *
 * Play it back picks its notes (the open strings, the natural notes to the 5th fret, or every note to
 * the 12th, on the instrument and tuning picked), how many in a row (1 to 4, [onNotesInRow]) and whether
 * any octave counts ([onNoteMatch]), as the mock-up's panel. Intervals picks its intervals, minor
 * second to octave, from twelve buttons ([onToggleInterval]) and how they are played, up, down or
 * together ([onToggleDirection]): a tap adds or takes away, and the last one picked stays. Chord type
 * picks its types from the Chords tool's twenty, in its order ([onToggleChordType]), the same way.
 * Everything picked here is saved ([EarTrainingViewModel]).
 */
@Composable
internal fun ChoicesPanel(
    open: Boolean,
    anchor: Rect?,
    width: Dp,
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
    FloatingPanel(
        open = open,
        anchor = anchor,
        onDismiss = onDismiss,
        title = stringResource(R.string.ear_choices),
        width = width,
        tag = EarTrainingTestTags.CHOICES_PANEL,
    ) {
        Column(Modifier.padding(bottom = 4.dp)) {
            when (exercise) {
                Exercise.PLAY_IT_BACK -> {
                    val notes = stringResource(R.string.ear_notes)
                    GroupLabel(notes, first = true)
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
                    GroupLabel(intervals, first = true)
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
                    GroupLabel(types, first = true)
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

/**
 * A group's name in the panel: the shared small label ([PanelLabel]; until Phase 77 a heading of its own in the accent),
 * [GROUP_GAP] under the group above it, or at the panel's top where it is the [first], as the metronome's.
 */
@Composable
private fun GroupLabel(text: String, first: Boolean = false) =
    PanelLabel(text, Modifier.padding(top = if (first) 0.dp else GROUP_GAP - 4.dp))

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

/** Above each group's name, and above Any octave. */
private val GROUP_GAP = 10.dp

/** The toggles three to a row: twelve intervals in four rows, up · down · together in one, twenty chord types in seven. */
private const val CHOICES_PER_ROW = 3
