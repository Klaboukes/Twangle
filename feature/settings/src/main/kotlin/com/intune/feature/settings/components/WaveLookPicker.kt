package com.intune.feature.settings.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.intune.core.designsystem.dial.drawWave
import com.intune.core.designsystem.styleTokens
import com.intune.core.settings.WaveLook
import com.intune.feature.settings.R

/**
 * A titled grid of cards, three to a row, to pick the wave view's look from. Each card shows a still
 * picture of its look a little off pitch, drawn in the current style, with its [label] under it. The
 * [selected] card has a ring; tapping a card calls [onSelect]. The cards form one selectable group
 * for TalkBack, which reads each card's name.
 */
@Composable
fun WaveLookPicker(
    title: String,
    selected: WaveLook,
    onSelect: (WaveLook) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (WaveLook) -> String,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WaveLook.entries.chunked(PER_ROW).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { look ->
                        WaveLookCard(
                            look = look,
                            label = label(look),
                            selected = look == selected,
                            onClick = { onSelect(look) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Keeps the cards of a shorter last row as wide as the others.
                    repeat(PER_ROW - row.size) { Column(Modifier.weight(1f)) {} }
                }
            }
        }
    }
}

@Composable
private fun WaveLookCard(look: WaveLook, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.styleTokens.style
    val path = remember { Path() }
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.medium)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.primary else colors.outlineVariant,
                shape = MaterialTheme.shapes.medium,
            )
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(PREVIEW_HEIGHT)) {
            drawWave(
                look = look,
                style = style,
                color = if (selected) colors.primary else colors.onSurfaceVariant,
                beats = 3f,
                phase = 0.15f,
                depth = 0.6f,
                level = 1f,
                glow = 0f,
                lean = -0.3f,
                breath = 0.25f,
                path = path,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
        )
    }
}

private const val PER_ROW = 3
private val PREVIEW_HEIGHT = 32.dp

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun WaveLookPickerPreview() {
    MaterialTheme {
        WaveLookPicker(
            title = stringResource(R.string.settings_wave_look_title),
            selected = WaveLook.STRIPES,
            onSelect = {},
            modifier = Modifier.padding(16.dp),
        ) { it.name.lowercase() }
    }
}
