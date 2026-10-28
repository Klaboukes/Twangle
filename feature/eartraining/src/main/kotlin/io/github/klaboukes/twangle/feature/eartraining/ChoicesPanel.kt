package io.github.klaboukes.twangle.feature.eartraining

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteChoice
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.designsystem.R as DesignR

/**
 * "What is asked" (the owner's pick A: questions start at once, and this panel opens any time): the
 * choices of the [exercise] shown, in a panel from the bottom as the instrument and tuning panel. A
 * pick takes effect at once ([onNoteChoice]: a question the new choice doesn't offer gives way to a new
 * one) and the panel stays open for more; a tap beside it, a swipe down or Back closes it ([onDismiss]).
 *
 * Play it back picks its notes: the open strings, the natural notes to the 5th fret, or every note to
 * the 12th, on the instrument and tuning picked. Intervals and Chord type add theirs as they are built.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChoicesPanel(exercise: Exercise, noteChoice: NoteChoice, onNoteChoice: (NoteChoice) -> Unit, onDismiss: () -> Unit) {
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
        shape = panelShape(MaterialTheme.styleTokens.style),
        containerColor = scheme.surface,
        contentColor = scheme.onSurface,
        modifier = Modifier.testTag(EarTrainingTestTags.CHOICES_PANEL),
    ) {
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
                            ChoiceRow(stringResource(choice.label), selected = choice == noteChoice, tag = EarTrainingTestTags.noteChoice(choice)) {
                                onNoteChoice(choice)
                            }
                        }
                    }
                }
                // Their choices come with their own increments.
                Exercise.INTERVALS, Exercise.CHORD_TYPE -> Unit
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

/** One choice of a group where one is picked: its [text], the [selected] one in the container colour and ticked. TalkBack hears a radio button. */
@Composable
private fun ChoiceRow(text: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Row(
        Modifier
            .testTag(tag)
            .padding(top = 4.dp)
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .clip(shape)
            .background(if (selected) scheme.primaryContainer else Color.Transparent, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        if (selected) {
            Icon(painterResource(DesignR.drawable.ic_check), contentDescription = null, tint = scheme.primary, modifier = Modifier.size(CHECK_SIZE))
        } else {
            Spacer(Modifier.size(CHECK_SIZE))
        }
    }
}

/** The panel's top corners, as the instrument and tuning panel's: the style's card corners, at least 14 dp. */
private fun panelShape(style: VisualStyle): Shape = when (style) {
    VisualStyle.FUTURISTIC -> CutCornerShape(topStart = 14.dp, topEnd = 14.dp)
    VisualStyle.RETRO -> RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    VisualStyle.SPACE_AGE -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    else -> RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
}

/** The panel is at most this share of the window's height, so the question shows above it. */
private const val PANEL_MAX_HEIGHT = 0.74f

/** Material's drag handle at the panel's top: 4 dp with 22 dp above and below. */
private val HANDLE_HEIGHT = 48.dp
private val PANEL_PADDING = 20.dp
private val LIST_PADDING = 12.dp
private val ROW_HEIGHT = 48.dp
private val CHECK_SIZE = 20.dp
