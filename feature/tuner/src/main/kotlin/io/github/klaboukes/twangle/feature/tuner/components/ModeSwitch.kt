package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.klaboukes.twangle.feature.tuner.R
import io.github.klaboukes.twangle.feature.tuner.TunerMode
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags

/** Two-button switch between guitar ([TunerMode.AUTO]) and chromatic mode. */
@Composable
fun ModeSwitch(mode: TunerMode, onModeChange: (TunerMode) -> Unit, modifier: Modifier = Modifier) {
    val modes = TunerMode.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        modes.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == mode,
                onClick = { onModeChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                // The unselected segment gets a backing, so it reads on any style's background.
                colors = SegmentedButtonDefaults.colors(
                    inactiveContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                ),
                modifier = Modifier.testTag(TunerTestTags.mode(option)),
            ) {
                Text(stringResource(label(option)))
            }
        }
    }
}

private fun label(mode: TunerMode): Int = when (mode) {
    TunerMode.AUTO -> R.string.tuner_mode_guitar
    TunerMode.CHROMATIC -> R.string.tuner_mode_chromatic
}

@Preview(showBackground = true)
@Composable
private fun ModeSwitchPreview() {
    MaterialTheme { ModeSwitch(TunerMode.AUTO, onModeChange = {}) }
}
