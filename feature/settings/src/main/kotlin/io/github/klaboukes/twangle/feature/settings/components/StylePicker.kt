package io.github.klaboukes.twangle.feature.settings.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.StyleMood
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.designsystem.dial.WaveMotion
import io.github.klaboukes.twangle.core.designsystem.styleName
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.feature.settings.R

/**
 * A titled grid of cards ([LookCardGrid]) to pick the app's style from, like the meter and wave
 * looks: three to a row (two from 1.3× system font on a small window), in the owner's order. Each
 * card is a mood picture of its style ([StyleMood]) in that style's own colours, letters and shapes, light or dark
 * as the app is now, with the style's name under it. The cards' wave strips drift gently together.
 *
 * The drift only runs while some of the cards are on screen, and each frame only redraws the
 * strips: nothing is recomposed.
 */
@Composable
fun StylePicker(
    title: String,
    selected: VisualStyle,
    onSelect: (VisualStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val demo = remember { StyleDemo() }
    val dark = MaterialTheme.styleTokens.dark
    LookCardGrid(
        title = title,
        looks = VisualStyle.entries,
        selected = selected,
        onSelect = onSelect,
        cardTag = ::styleCardTag,
        label = { styleName(it) },
        demo = demo,
        modifier = modifier,
    ) { style, _ ->
        StyleMood(style = style, darkTheme = dark, motion = demo.motion, frame = { demo.frame.longValue })
    }
}

/** The test tag of [style]'s card in [StylePicker]. */
fun styleCardTag(style: VisualStyle): String = "style_card_" + style.name.lowercase()

/** The cards' wave strips: a note held [DRIFT_CENTS] sharp, so they drift slowly to the right. */
private class StyleDemo : LookDemo {
    val motion = WaveMotion()
    val frame = mutableLongStateOf(0L)

    override fun advance(seconds: Float, frameNanos: Long) {
        motion.advance(seconds.coerceIn(0f, MAX_STEP_SECONDS), DRIFT_CENTS)
        frame.longValue = frameNanos
    }
}

private const val DRIFT_CENTS = 3f

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun StylePickerPreview() {
    TwangleTheme(style = VisualStyle.ANCIENT, darkTheme = false) {
        StylePicker(
            title = stringResource(R.string.settings_style_title),
            selected = VisualStyle.ANCIENT,
            onSelect = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
