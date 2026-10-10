package io.github.klaboukes.twangle.feature.tuner.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.feature.tuner.R

/**
 * The selected tuning's name with a drop-down arrow. Tapping it lists every preset with its open
 * strings (low to high); the current one is ticked. Choosing one calls [onSelect].
 */
@Composable
fun TuningPicker(
    selected: Tuning,
    onSelect: (Tuning) -> Unit,
    modifier: Modifier = Modifier,
    notation: Notation = Notation.DEFAULT,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier) {
        val changeTuning = stringResource(R.string.tuner_change_tuning)
        // TalkBack: "Standard, button, double-tap to change tuning".
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics {
                onClick(label = changeTuning) {
                    expanded = true
                    true
                }
            },
        ) {
            Text(tuningLabel(selected), style = MaterialTheme.typography.titleMedium)
            Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Tunings.all.forEach { tuning ->
                val isSelected = tuning == selected
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(tuningLabel(tuning), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = openStrings(tuning, notation),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(tuning)
                    },
                    trailingIcon = if (isSelected) {
                        { Icon(painterResource(R.drawable.ic_check), contentDescription = null) }
                    } else {
                        null
                    },
                    modifier = Modifier.semantics { this.selected = isSelected },
                )
            }
        }
    }
}

/** The tuning's name for users. Tunings without a known name (none today) show their id. */
@Composable
fun tuningLabel(tuning: Tuning): String = tuningNameRes(tuning.id)?.let { stringResource(it) } ?: tuning.id

/** Open-string note names, low to high, e.g. "D A D G B E". */
private fun openStrings(tuning: Tuning, notation: Notation): String =
    tuning.strings.joinToString(" ") { notation.nameOf(it.pitchClass) }

@StringRes
private fun tuningNameRes(id: String): Int? = when (id) {
    Tunings.STANDARD.id -> R.string.tuning_standard
    Tunings.DROP_D.id -> R.string.tuning_drop_d
    Tunings.HALF_STEP_DOWN.id -> R.string.tuning_half_step_down
    Tunings.FULL_STEP_DOWN.id -> R.string.tuning_full_step_down
    Tunings.DADGAD.id -> R.string.tuning_dadgad
    Tunings.OPEN_G.id -> R.string.tuning_open_g
    Tunings.OPEN_D.id -> R.string.tuning_open_d
    Tunings.OPEN_E.id -> R.string.tuning_open_e
    else -> null
}

@Preview(showBackground = true)
@Composable
private fun TuningPickerPreview() {
    MaterialTheme { TuningPicker(Tunings.DROP_D, onSelect = {}) }
}
