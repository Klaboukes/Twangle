package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.ceil
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.settings.VisualStyle

/**
 * Twangle's segmented switch: one of [options], [selected] in the style's main accent container (the
 * fill alone tells it, with no check mark: the owner's choice, 2026-10-18), a tap on another calls
 * [onSelect]. The tuner's mode switch (Strings · Chromatic) is this switch (the Practice tab's tool switch was too, until Phase 47 gave it a tool button). Each
 * segment shows [label] on one line, all of them shrinking to one size where the widest must, and carries the test tag [tag].
 * TalkBack hears the segments as radio buttons by their labels, the picked one selected, or by [spoken]
 * where a short word stands for a longer name (Ear training's exercises: "Play back" for "Play it back").
 */
@Composable
fun <T> SegmentedSwitch(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    tag: (T) -> String,
    modifier: Modifier = Modifier,
    spoken: (@Composable (T) -> String)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    // Futuristic's buttons are neon outlines in the accent, the unpicked one on a solid tint.
    val neon = MaterialTheme.styleTokens.style == VisualStyle.FUTURISTIC
    val crowded = options.size >= CROWDED_SEGMENTS
    val base = segmentedShapeFor(MaterialTheme.styleTokens.style, MaterialTheme.shapes.small)
    val measurer = rememberTextMeasurer()
    val smallest = lastResortLabelSize(LocalDensity.current)
    // The press effect in place of Material's ripple, the light over the segment's own fill; the
    // touch area set round the light, as the Twangle buttons do (OwnTouchArea).
    OwnTouchArea { touch ->
        SingleChoiceSegmentedButtonRow(modifier = modifier) {
            options.forEachIndexed { index, option ->
                val shape = SegmentedButtonDefaults.itemShape(index, options.size, base)
                val press = remember { MutableInteractionSource() }
                val name = spoken?.invoke(option)
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
                    // Three segments (Practice's tool switch until Phase 47; none today) keep less padding, so Dutch
                    // "Metronoom" still fits a 320 dp phone at 2× text. Two keep Material's 12 dp where
                    // the labels fit at full size; where they don't, the padding gives way down to the
                    // three segments' 6 dp before a label shrinks (the label's [segmentLabel] holds the
                    // difference): Russian "Хроматический" at 2× text on a 360 dp phone, beside focus
                    // mode's button.
                    contentPadding = CROWDED_PADDING,
                    icon = {},
                    modifier = Modifier
                        .testTag(tag(option))
                        .touchArea(touch)
                        .pressFeedback(press, shape, above = true)
                        .then(if (name != null) Modifier.semantics { contentDescription = name } else Modifier),
                ) {
                    // One line, its whole width counted as its minimum ([segmentLabel]): the row sizes every
                    // segment by the largest minimum width, and Chinese, Japanese and Korean text may break
                    // between any two characters, so otherwise "クロマチック" stood one character wide, three
                    // lines tall (softWrap alone doesn't change the minimum width). Every language's
                    // labels are single words, so Latin labels measure the same as before. Where a
                    // segment is too narrow (Dutch "Chromatisch" at 2× text on a 360 dp phone) it shrinks
                    // as far as it must to fit, rather than be cut off. One line needs no line spacing
                    // above and below it, which at 2× text is taller than the button (the letters fit):
                    // its line is 1.2 times the style's own size, in sp, so a shrunk label keeps the
                    // height of a whole one. The label's size as the row asks for it before laying it out
                    // (its intrinsic size) is its one line at full size, measured here: asked of the
                    // shrinking text itself, it came out differently before and after a tap, and the
                    // switch grew or shrank by 10 dp when the mode changed (English at 2× text beside
                    // focus mode's button, 2026-10-25).
                    val style = LocalTextStyle.current
                    val labelStyle = style.copy(
                        lineHeight = (style.fontSize.value * LABEL_LINE_HEIGHT).sp,
                        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
                    )
                    // One size for the whole bar (Phase 63's review): every segment is as wide as the others, and
                    // each sizes its text as if it held the widest label, so a shrunk bar shows one size, not a large
                    // "Saiten" beside a small "Chromatisch" (German at 2× text).
                    val text = label(option)
                    val texts = options.map { label(it) }
                    val lineHeight = with(LocalDensity.current) { labelStyle.lineHeight.toPx() }
                    val (whole, widest) = remember(texts, labelStyle, measurer, lineHeight) {
                        val sizes = texts.map { measurer.measure(it, labelStyle, maxLines = 1, softWrap = false).size }
                        val widestAt = sizes.indices.maxBy { sizes[it].width }
                        IntSize(sizes[widestAt].width, maxOf(sizes.maxOf { it.height }, ceil(lineHeight).toInt())) to texts[widestAt]
                    }
                    val widestFits = remember(widest, labelStyle, measurer) {
                        { size: TextUnit, room: Constraints ->
                            val laid = measurer.measure(
                                widest,
                                labelStyle.copy(fontSize = size),
                                maxLines = 1,
                                softWrap = false,
                                constraints = Constraints(maxWidth = room.maxWidth, maxHeight = room.maxHeight),
                            )
                            !laid.didOverflowWidth && !laid.didOverflowHeight
                        }
                    }
                    Text(
                        text,
                        style = labelStyle,
                        modifier = Modifier.segmentLabel(whole, give = if (crowded) 0.dp else GIVING_PADDING),
                        // One line, never wrapped: a word too wide overflows in width, which the auto-size sees and
                        // shrinks (as every one-line auto-sized text; Ear training's AnswerButton in IntervalsPart.kt
                        // says why).
                        maxLines = 1,
                        softWrap = false,
                        // The largest size at which the widest label fits, from the style's own down (lastResortLabelSize), each tried in turn.
                        autoSize = remember(style.fontSize, smallest, widestFits) { OneLineAutoSize(style.fontSize, smallest, sizedBy = widestFits) },
                    )
                }
            }
        }
    }
}

/**
 * The joined bar's outer shape in [style] (Phase 63's review): the round ends of a capsule where the
 * style's buttons are round (Contemporary, Space Age), else the style's [small] corners, as the
 * buttons and faces beside it: Classic's square, Retro's soft, Futuristic's cut, Ancient's uneven ones.
 * Until then it was a capsule in every style, beside Classic's square and Futuristic's eight-sided buttons.
 */
internal fun segmentedShapeFor(style: VisualStyle, small: CornerBasedShape): CornerBasedShape = when (style) {
    VisualStyle.CONTEMPORARY, VisualStyle.SPACE_AGE -> CircleShape
    VisualStyle.ANCIENT, VisualStyle.CLASSIC, VisualStyle.RETRO, VisualStyle.FUTURISTIC -> small
}

/**
 * A segment's label: its intrinsic size, both minimum and maximum, is [whole] (its one line at full
 * size) and [give] more in width, so the row gives every segment room for the longest label and that
 * padding where it can, and the same height in every state. Laid out, the padding goes first and
 * the text shrinks only once it has gone; it keeps that height (where the row allows) with the text
 * in its middle.
 */
private fun Modifier.segmentLabel(whole: IntSize, give: Dp): Modifier = this then SegmentLabelElement(whole, give)

private data class SegmentLabelElement(val whole: IntSize, val give: Dp) : ModifierNodeElement<SegmentLabelNode>() {
    override fun create() = SegmentLabelNode(whole, give)
    override fun update(node: SegmentLabelNode) {
        node.whole = whole
        node.give = give
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "segmentLabel"
    }
}

private class SegmentLabelNode(var whole: IntSize, var give: Dp) : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val giving = give.roundToPx()
        val room = constraints.maxWidth
        // The padding first: the text keeps its whole width while the room holds it.
        val textRoom = if (room == Constraints.Infinity) room else maxOf(room - giving, minOf(room, whole.width))
        // Only the width limits the label: its one line is never squeezed to a shorter row.
        val placeable = measurable.measure(Constraints(maxWidth = textRoom))
        val width = constraints.constrainWidth(placeable.width + giving)
        val height = constraints.constrainHeight(maxOf(whole.height, placeable.height))
        return layout(width, height) { placeable.place((width - placeable.width) / 2, (height - placeable.height) / 2) }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int = whole.width + give.roundToPx()
    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int = whole.width + give.roundToPx()
    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int = whole.height
    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int = whole.height
}

/**
 * The labels shrink only as far as they must to fit: no smaller than 8 sp in most cases measured
 * (as the string buttons' names), down to this only where one would otherwise be cut off: three
 * segments at 2× text on a 320 dp phone, and Russian "Хроматический" in Classic at 2× text on a phone
 * on its side, which needs 5.5 sp on the Xiaomi 12X (the overnight run of 2026-10-27).
 */
private val LAST_RESORT_LABEL_SIZE = 5.sp

/**
 * [LAST_RESORT_LABEL_SIZE], or 5 dp where large text makes 5 sp more: Ear training's three words at 2× text on a
 * 320 dp phone (Dutch "Akkoorden" needs about 4.5 sp there, Phase 63). It is reached only where a word would
 * otherwise be cut off; at the usual text size the two are the same.
 */
private fun lastResortLabelSize(density: Density): TextUnit =
    with(density) { if (LAST_RESORT_LABEL_DP.toSp().value < LAST_RESORT_LABEL_SIZE.value) LAST_RESORT_LABEL_DP.toSp() else LAST_RESORT_LABEL_SIZE }

private val LAST_RESORT_LABEL_DP = 5.dp

/** From this many segments, each keeps [CROWDED_PADDING] at its sides instead of Material's 12 dp. */
private const val CROWDED_SEGMENTS = 3
private val CROWDED_PADDING = PaddingValues(horizontal = 6.dp)

/** With two segments, what the label holds on top of [CROWDED_PADDING]: Material's 12 dp a side in all, while the labels fit. */
private val GIVING_PADDING = 12.dp

/** Futuristic's unpicked button outline: the accent at 55 %, as the mock-up's neon. */
private const val NEON_OUTLINE_ALPHA = 0.55f

/** A segment's line, as a multiple of the label style's own font size: the letters at full size fit it. */
private const val LABEL_LINE_HEIGHT = 1.2f
