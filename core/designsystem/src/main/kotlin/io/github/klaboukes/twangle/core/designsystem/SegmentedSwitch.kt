package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.settings.VisualStyle

/**
 * Twangle's segmented switch: one of [options], [selected] in the style's main accent container (the
 * fill alone tells it, with no check mark: the owner's choice, 2026-10-18), a tap on another calls
 * [onSelect]. The tuner's mode switch (Strings · Chromatic) and the Practice tab's tool switch (Metronome · Chords · Capo) are this one switch, so they look and behave alike. Each
 * segment shows [label] on one line, shrinking to fit where it must, and carries the test tag [tag].
 * TalkBack hears the segments as radio buttons by their labels, the picked one selected.
 */
@Composable
fun <T> SegmentedSwitch(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    tag: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // Futuristic's buttons are neon outlines in the accent, the unpicked one on a solid tint.
    val neon = MaterialTheme.styleTokens.style == VisualStyle.FUTURISTIC
    val crowded = options.size >= CROWDED_SEGMENTS
    // The press effect in place of Material's ripple, the light over the segment's own fill; the
    // touch area set round the light, as the Twangle buttons do (OwnTouchArea).
    OwnTouchArea { touch ->
        SingleChoiceSegmentedButtonRow(modifier = modifier) {
            options.forEachIndexed { index, option ->
                val shape = SegmentedButtonDefaults.itemShape(index, options.size)
                val press = remember { MutableInteractionSource() }
                SegmentedButton(
                    selected = option == selected,
                    onClick = {
                        press.pressFlash()
                        onSelect(option)
                    },
                    shape = shape,
                    interactionSource = press,
                    // The selected segment takes the style's main accent (Material's default is the
                    // secondary colour, e.g. purple beside Futuristic's cyan). The unselected one gets a
                    // backing, so it reads on any style's background.
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = scheme.primaryContainer,
                        activeContentColor = scheme.onPrimaryContainer,
                        inactiveContainerColor = if (neon) neonFill(scheme) else scheme.surface.copy(alpha = 0.6f),
                        activeBorderColor = if (neon) scheme.primary else scheme.outline,
                        inactiveBorderColor = if (neon) scheme.primary.copy(alpha = NEON_OUTLINE_ALPHA) else scheme.outline,
                    ),
                    // Three segments (Practice's Metronome · Chords · Capo) keep less padding, so Dutch
                    // "Metronoom" still fits a 320 dp phone at 2× text.
                    contentPadding = if (crowded) CROWDED_PADDING else SegmentedButtonDefaults.ContentPadding,
                    icon = {},
                    modifier = Modifier.testTag(tag(option)).touchArea(touch).pressFeedback(press, shape, above = true),
                ) {
                    // One line, its whole width counted as its minimum ([segmentLabel]): the row sizes every
                    // segment by the largest minimum width, and Chinese, Japanese and Korean text may break
                    // between any two characters, so otherwise "クロマチック" stood one character wide, three
                    // lines tall (softWrap alone doesn't change the minimum width). Every language's
                    // labels are single words, so Latin labels measure the same as before. Where a
                    // segment is too narrow (Dutch "Chromatisch" at 2× text on a 360 dp phone) it shrinks
                    // as far as it must to fit, rather than be cut off. One line needs no line spacing
                    // above and below it, which at 2× text is taller than the button (the letters fit).
                    Text(
                        label(option),
                        style = LocalTextStyle.current.copy(lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)),
                        modifier = Modifier.segmentLabel(),
                        // Wrapping on, so a word too wide for one line counts as not fitting and shrinks.
                        maxLines = 1,
                        // The largest size that fits, from the style's own down (LAST_RESORT_LABEL_SIZE).
                        autoSize = TextAutoSize.StepBased(minFontSize = LAST_RESORT_LABEL_SIZE, maxFontSize = LocalTextStyle.current.fontSize, stepSize = 0.5.sp),
                    )
                }
            }
        }
    }
}

/**
 * A segment's label: its minimum width is its whole one-line width, so the row gives every segment
 * room for the longest label where it can.
 */
private fun Modifier.segmentLabel(): Modifier = this then SegmentLabelElement

private object SegmentLabelElement : ModifierNodeElement<SegmentLabelNode>() {
    override fun create() = SegmentLabelNode()
    override fun update(node: SegmentLabelNode) = Unit
    override fun InspectorInfo.inspectableProperties() {
        name = "segmentLabel"
    }
    override fun hashCode(): Int = 0
    override fun equals(other: Any?): Boolean = other === this
}

private class SegmentLabelNode : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        measurable.maxIntrinsicWidth(height)
}

/**
 * The labels shrink only as far as they must to fit: no smaller than 8 sp in every case measured
 * (as the string buttons' names), down to this only where one would otherwise be cut off (as the
 * Practice pickers' buttons): three segments at 2× text on a 320 dp phone.
 */
private val LAST_RESORT_LABEL_SIZE = 6.sp

/** From this many segments, each keeps [CROWDED_PADDING] at its sides instead of Material's 12 dp. */
private const val CROWDED_SEGMENTS = 3
private val CROWDED_PADDING = PaddingValues(horizontal = 6.dp)

/** Futuristic's unpicked button outline: the accent at 55 %, as the mock-up's neon. */
private const val NEON_OUTLINE_ALPHA = 0.55f
