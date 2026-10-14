package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.settings.CustomTuning
import io.github.klaboukes.twangle.feature.tuner.R
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags

/**
 * The player's own tunings in the panel, under the presets: the small heading "My tunings" (as the
 * approved mock-up), then each of [tunings] as a [TuningRow] ([selected] ticked; a tap calls
 * [onSelect]) with a ⋮ button at its end. Its menu has Edit ([onEdit]) and Delete ([onDelete]); Delete
 * first asks in a dialog, so a stray tap deletes nothing. The heading also stands alone over "New tuning".
 */
@Composable
internal fun MyTunings(
    tunings: List<CustomTuning>,
    selected: Tuning,
    notation: Notation,
    onSelect: (Tuning) -> Unit,
    onEdit: ((CustomTuning) -> Unit)?,
    onDelete: ((CustomTuning) -> Unit)?,
) {
    // The tuning asked about, by id, so the question survives turning the phone.
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    val title = stringResource(R.string.tuner_my_tunings)
    Text(
        // In capitals as the mock-up, but TalkBack says it as written.
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.08.em),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = TUNING_TEXT_INSET, end = TUNING_TEXT_INSET, top = 14.dp, bottom = 2.dp)
            .semantics {
                heading()
                contentDescription = title
            },
    )
    for (mine in tunings) {
        val isSelected = mine.id == selected.id
        TuningRow(
            name = mine.name,
            tuning = mine.tuning,
            notation = notation,
            selected = isSelected,
            onClick = { onSelect(mine.tuning) },
            modifier = Modifier.testTag(TunerTestTags.customTuning(mine.id)),
            trailing = if (onEdit == null && onDelete == null) {
                null
            } else {
                {
                    TuningMenu(
                        mine,
                        selected = isSelected,
                        onEdit = onEdit?.let { { it(mine) } },
                        onDelete = onDelete?.let { { deletingId = mine.id } },
                    )
                }
            },
        )
    }
    val deleting = tunings.firstOrNull { it.id == deletingId }
    if (deleting != null && onDelete != null) {
        DeleteTuningDialog(
            name = deleting.name,
            onConfirm = {
                deletingId = null
                onDelete(deleting)
            },
            onDismiss = { deletingId = null },
        )
    }
}

/** The ⋮ button at the end of [tuning]'s row and the small menu it opens: Edit, Delete. */
@Composable
private fun TuningMenu(tuning: CustomTuning, selected: Boolean, onEdit: (() -> Unit)?, onDelete: (() -> Unit)?) {
    var open by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag(TunerTestTags.customTuningMenu(tuning.id))) {
            Icon(
                painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.tuner_tuning_options, tuning.name),
                tint = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (onEdit != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tuner_edit_tuning)) },
                    onClick = {
                        open = false
                        onEdit()
                    },
                )
            }
            if (onDelete != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tuner_delete_tuning)) },
                    onClick = {
                        open = false
                        onDelete()
                    },
                )
            }
        }
    }
}

/** Asks before the tuning [name] is deleted for good: Cancel (and Back, or a tap outside) keeps it. */
@Composable
private fun DeleteTuningDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(TunerTestTags.DELETE_TUNING_DIALOG),
        // The name goes in the text, not the title: in a style's large title face a long name would
        // take a line per word at large text.
        title = {
            val title = MaterialTheme.typography.titleMedium
            Text(stringResource(R.string.tuner_delete_tuning_title), style = title.copy(fontSize = title.fontSize * TITLE_SCALE))
        },
        text = { Text(stringResource(R.string.tuner_delete_tuning_text, name)) },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.tuner_delete_tuning_cancel)) }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.tuner_delete_tuning), color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

/**
 * The question is set in the style's title face a little larger than the panel's list (the dialog's
 * own headline size leaves "verwijderen?" no room on a line at 2× text in the wider faces).
 */
private const val TITLE_SCALE = 1.15f
