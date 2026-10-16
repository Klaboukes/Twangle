package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.node.invalidateMeasurement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.settings.VisualStyle

/**
 * Twangle's segmented switch: one of [options], [selected] in the style's main accent container, a
 * tap on another calls [onSelect]. The tuner's mode switch (Strings · Chromatic) and the Practice
 * tab's tool switch (Metronome · Chords) are this one switch, so they look and behave alike. Each
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
                        modifier = Modifier.segmentLabel(checked = option == selected),
                        // Wrapping on, so a word too wide for one line counts as not fitting and shrinks.
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = MIN_LABEL_SIZE, maxFontSize = LocalTextStyle.current.fontSize, stepSize = 0.5.sp),
                    )
                }
            }
        }
    }
}

/**
 * A segment's label: its minimum width is its whole one-line width, so the row gives every segment
 * room for the longest label where it can. In the [checked] segment it is measured in the room left
 * beside the check mark (the mark's [SegmentedButtonDefaults.IconSize] and Material's 8 dp spacing):
 * Material measures the label in the segment's whole width and lays the mark beside it, which cut
 * the end off a picked label that just fitted (Dutch "Akkoorden" at 2× text on a 320 dp phone). An
 * unpicked segment has no mark, and Material centres its label in the whole width.
 */
private fun Modifier.segmentLabel(checked: Boolean): Modifier = this then SegmentLabelElement(checked)

private data class SegmentLabelElement(val checked: Boolean) : ModifierNodeElement<SegmentLabelNode>() {
    override fun create() = SegmentLabelNode(checked)
    override fun update(node: SegmentLabelNode) {
        if (node.checked != checked) {
            node.checked = checked
            node.invalidateMeasurement()
        }
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "segmentLabel"
        properties["checked"] = checked
    }
}

private class SegmentLabelNode(var checked: Boolean) : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val room = if (checked) (SegmentedButtonDefaults.IconSize + CHECK_MARK_SPACING).roundToPx() else 0
        val inner = if (constraints.hasBoundedWidth) {
            val max = (constraints.maxWidth - room).coerceAtLeast(0)
            constraints.copy(minWidth = minOf(constraints.minWidth, max), maxWidth = max)
        } else {
            constraints
        }
        val placeable = measurable.measure(inner)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        measurable.maxIntrinsicWidth(height)
}

/** Material's space between a segment's check mark and its label. */
private val CHECK_MARK_SPACING = 8.dp

/** The labels shrink to fit no smaller than this, as the string buttons' names. */
private val MIN_LABEL_SIZE = 8.sp

/** Futuristic's unpicked button outline: the accent at 55 %, as the mock-up's neon. */
private const val NEON_OUTLINE_ALPHA = 0.55f
