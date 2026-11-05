package io.github.klaboukes.twangle.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.SegmentedSwitch
import io.github.klaboukes.twangle.feature.settings.rowTitleStyle

/**
 * A titled setting with one of a few short [options], as the app's segmented switch ([SegmentedSwitch]) across the
 * row: Light or dark (Phone · Light · Dark) and In-tune precision (Relaxed · Precise), since 2026-11-05 (the owner:
 * three and two full-width rows for a few short words made Settings long). An optional [summary] under the title says
 * what the picked one means ("In tune within 5 cents"), one line whichever is picked, so the row keeps its height.
 * TalkBack hears the segments as radio buttons by their labels. Each segment carries the test tag [tag].
 */
@Composable
fun <T> SegmentedSetting(
    title: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    tag: (T) -> String,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = rowTitleStyle())
            if (summary != null) {
                Text(text = summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SegmentedSwitch(
            options = options,
            selected = selected,
            onSelect = { if (it != selected) onSelect(it) },
            label = label,
            tag = tag,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
