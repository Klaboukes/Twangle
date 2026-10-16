package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.klaboukes.twangle.core.designsystem.SegmentedSwitch
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.feature.tuner.R
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags

/** Two-button switch between guitar ([TunerMode.AUTO]) and chromatic mode: the app's [SegmentedSwitch]. */
@Composable
fun ModeSwitch(mode: TunerMode, onModeChange: (TunerMode) -> Unit, modifier: Modifier = Modifier) {
    SegmentedSwitch(
        options = TunerMode.entries,
        selected = mode,
        onSelect = onModeChange,
        label = { stringResource(label(it)) },
        tag = TunerTestTags::mode,
        modifier = modifier,
    )
}

private fun label(mode: TunerMode): Int = when (mode) {
    TunerMode.AUTO -> R.string.tuner_mode_strings
    TunerMode.CHROMATIC -> R.string.tuner_mode_chromatic
}

@Preview(showBackground = true)
@Composable
private fun ModeSwitchPreview() {
    MaterialTheme { ModeSwitch(TunerMode.AUTO, onModeChange = {}) }
}
