package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.klaboukes.twangle.core.designsystem.OwnTouchArea
import io.github.klaboukes.twangle.core.designsystem.pressFeedback
import io.github.klaboukes.twangle.core.designsystem.pressFlash
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.designsystem.touchArea
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.feature.tuner.R
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags

/** Two-button switch between guitar ([TunerMode.AUTO]) and chromatic mode. */
@Composable
fun ModeSwitch(mode: TunerMode, onModeChange: (TunerMode) -> Unit, modifier: Modifier = Modifier) {
    val modes = TunerMode.entries
    val scheme = MaterialTheme.colorScheme
    // Futuristic's buttons are neon outlines in the accent, with nothing behind the unpicked one.
    val neon = MaterialTheme.styleTokens.style == VisualStyle.FUTURISTIC
    // The press effect in place of Material's ripple, the light over the segment's own fill; the
    // touch area set round the light, as the Twangle buttons do (OwnTouchArea).
    OwnTouchArea { touch ->
        SingleChoiceSegmentedButtonRow(modifier = modifier) {
            modes.forEachIndexed { index, option ->
                val shape = SegmentedButtonDefaults.itemShape(index, modes.size)
                val press = remember { MutableInteractionSource() }
                SegmentedButton(
                    selected = option == mode,
                    onClick = {
                        press.pressFlash()
                        onModeChange(option)
                    },
                    shape = shape,
                    interactionSource = press,
                    // The selected segment takes the style's main accent (Material's default is the
                    // secondary colour, e.g. purple beside Futuristic's cyan). The unselected one gets a
                    // backing, so it reads on any style's background.
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = scheme.primaryContainer,
                        activeContentColor = scheme.onPrimaryContainer,
                        inactiveContainerColor = if (neon) Color.Transparent else scheme.surface.copy(alpha = 0.6f),
                        activeBorderColor = if (neon) scheme.primary else scheme.outline,
                        inactiveBorderColor = if (neon) scheme.primary.copy(alpha = NEON_OUTLINE_ALPHA) else scheme.outline,
                    ),
                    modifier = Modifier.testTag(TunerTestTags.mode(option)).touchArea(touch).pressFeedback(press, shape, above = true),
                ) {
                    // One line, its whole width counted as its minimum: the row sizes both segments by
                    // the larger minimum width, and Chinese, Japanese and Korean text may break between
                    // any two characters, so otherwise "クロマチック" stood one character wide, three
                    // lines tall (softWrap alone doesn't change the minimum width). Every language's
                    // labels are single words, so Latin labels measure the same as before.
                    Text(
                        stringResource(label(option)),
                        modifier = Modifier.width(IntrinsicSize.Max),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }

}

/** Futuristic's unpicked button outline: the accent at 55 %, as the mock-up's neon. */
private const val NEON_OUTLINE_ALPHA = 0.55f

private fun label(mode: TunerMode): Int = when (mode) {
    TunerMode.AUTO -> R.string.tuner_mode_strings
    TunerMode.CHROMATIC -> R.string.tuner_mode_chromatic
}

@Preview(showBackground = true)
@Composable
private fun ModeSwitchPreview() {
    MaterialTheme { ModeSwitch(TunerMode.AUTO, onModeChange = {}) }
}
