package io.github.klaboukes.twangle.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.musicText

/**
 * A titled setting with one choice out of [options], shown as radio buttons. Each whole row is the
 * touch target (at least 48 dp high), and the rows form one selectable group for TalkBack.
 */
@Composable
fun <T> RadioGroupSetting(
    title: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (T) -> String,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Column(modifier = Modifier.selectableGroup()) {
            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(selected = option == selected, onClick = { onSelect(option) }, role = Role.RadioButton),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // The row handles the click, so the button itself doesn't.
                    RadioButton(selected = option == selected, onClick = null)
                    // Labels such as "Sharps: C, C♯, D …" set their signs like the rest of the app.
                    Text(text = musicText(label(option)), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
