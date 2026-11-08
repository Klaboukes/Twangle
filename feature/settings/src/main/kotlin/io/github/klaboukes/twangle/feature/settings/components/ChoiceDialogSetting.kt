package io.github.klaboukes.twangle.feature.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.dialogEdge
import io.github.klaboukes.twangle.core.designsystem.CHOICE_ROW_GAP
import io.github.klaboukes.twangle.core.designsystem.ChoiceRow
import io.github.klaboukes.twangle.core.designsystem.TwangleTextButton
import io.github.klaboukes.twangle.feature.settings.R
import io.github.klaboukes.twangle.feature.settings.rowTitleStyle

/**
 * A setting picked from a list that opens on a tap: a row with [title], the picked choice under it and an arrow,
 * like the other rows that open something, which opens a dialog listing [options] as the app's choice rows
 * ([ChoiceRow]). Picking one closes the dialog and, if it is another, calls [onSelect]; Cancel closes it.
 * Language and Note names (since 2026-11-05: five rows that hardly anyone changes, as *Automatic* follows the
 * app's language) are this row.
 *
 * Each choice is shown as [label] and, where it is said otherwise than written, heard by TalkBack as [spoken]
 * (the note names' signs in words), under the title and in the list alike. A list longer than the dialog has room
 * for scrolls inside it, opens at the picked choice, and a thin line at its top or bottom shows there is more
 * that way. The row carries [rowTag]; each choice [optionTag].
 */
@Composable
fun <T> ChoiceDialogSetting(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> AnnotatedString,
    onSelect: (T) -> Unit,
    rowTag: String,
    optionTag: (T) -> String,
    modifier: Modifier = Modifier,
    spoken: @Composable (T) -> String? = { null },
) {
    // Not saved: a turn of the phone closes the list, which is quickly opened again.
    var open by remember { mutableStateOf(false) }
    // Worked out on the page, not in the dialog: a dialog is a window of its own, and the texts must be those of the
    // page's language (a test's forced language too).
    val labels = options.map { label(it) }
    val heards = options.map { spoken(it) }
    val picked = options.indexOf(selected)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button) { open = true }
            .testTag(rowTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = rowTitleStyle())
            val heard = heards.getOrNull(picked)
            Text(
                text = labels.getOrElse(picked) { AnnotatedString("") },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (heard != null) Modifier.semantics { contentDescription = heard } else Modifier,
            )
        }
        // Like the rows under it, which also open something; the title already says what.
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            modifier = Modifier.dialogEdge(),
            confirmButton = {
                TwangleTextButton(onClick = { open = false }) { Text(stringResource(R.string.settings_language_cancel)) }
            },
            title = { Text(title) },
            text = {
                val scroll = rememberScrollState()
                val pickedInView = remember { BringIntoViewRequester() }
                LaunchedEffect(Unit) {
                    withFrameNanos { } // Once the list has been laid out.
                    pickedInView.bringIntoView()
                }
                val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
                Column {
                    HorizontalDivider(color = edgeColor(scroll.canScrollBackward))
                    Column(
                        Modifier
                            .heightIn(max = windowHeight * LIST_MAX_SHARE)
                            .verticalScroll(scroll)
                            .selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(CHOICE_ROW_GAP),
                    ) {
                        options.forEachIndexed { index, option ->
                            ChoiceRow(
                                text = labels[index],
                                selected = index == picked,
                                onClick = {
                                    open = false
                                    if (option != selected) onSelect(option)
                                },
                                spoken = heards[index],
                                modifier = Modifier
                                    .then(if (index == picked) Modifier.bringIntoViewRequester(pickedInView) else Modifier)
                                    .testTag(optionTag(option)),
                            )
                        }
                    }
                    HorizontalDivider(color = edgeColor(scroll.canScrollForward))
                }
            },
        )
    }
}

/** The line at an end of the list: shown while the list scrolls further that way. */
@Composable
private fun edgeColor(more: Boolean): Color = if (more) MaterialTheme.colorScheme.outlineVariant else Color.Transparent

/** The list takes at most this share of the window's height, so the dialog fits with room around it. */
private const val LIST_MAX_SHARE = 0.6f
