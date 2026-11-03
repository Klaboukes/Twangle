package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Material's [DropdownMenu] in the one look (Phase 75): a floating panel on the raised surface
 * (`surfaceContainerHigh`) with the large corners (`shapes.large`, 22 dp) and a soft lift, no
 * border, as the metronome's options panel. The Practice tools' pickers drop it under what opened
 * them: the chord's or the scale's panel, the drone's, the Capo helper's shapes. [expanded],
 * [onDismissRequest], [modifier] and [content] are the menu's own.
 */
@Composable
fun FloatingMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = FLOATING_LIFT,
        content = content,
    )
}

/** The panel's lift: its soft shadow, the metronome's panel's. */
private val FLOATING_LIFT = 12.dp
