package io.github.klaboukes.twangle.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.klaboukes.twangle.feature.settings.rowTitleStyle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.CHOICE_ROW_GAP
import io.github.klaboukes.twangle.core.designsystem.ChoiceRow
import io.github.klaboukes.twangle.core.designsystem.isMusicSign
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech

/**
 * A titled setting with one choice out of [options], shown as the app's choice rows ([ChoiceRow]: the
 * picked one in the container colour with the accent outline and a ✓), with an optional one-line
 * [summary] under the title that says what the choice is about. Each whole row is the touch target
 * (at least 48 dp high), and the rows form one selectable group for TalkBack, each a radio button.
 */
@Composable
fun <T> RadioGroupSetting(
    title: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    label: @Composable (T) -> String,
) {
    val speech = rememberNoteNameSpeech()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = rowTitleStyle())
            if (summary != null) {
                Text(text = summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(CHOICE_ROW_GAP)) {
            options.forEach { option ->
                // Labels such as "Sharps: C, C♯, D …" set their signs like the rest of the app,
                // and TalkBack says their note names in words ("C sharp", in Dutch "cis").
                val text = label(option)
                ChoiceRow(
                    text = musicText(text),
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    spoken = if (text.any(::isMusicSign)) speech.text(text) else null,
                )
            }
        }
    }
}
