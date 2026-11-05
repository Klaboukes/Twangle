package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The largest size from [largest] down to [smallest] at which the text fits its box
 * on its lines without breaking a word ("Pentatonique / majeure", never "Pentatoni / que"); Chinese
 * and Japanese, written without spaces, may break between any two characters. Smallest if none.
 * Where it can, it leaves [twoLineMargin] above and below, so two lines never touch the outline
 * (Classic's and Retro's letters did); at large text sizes, where even the smallest wouldn't fit
 * with it, the label may use the whole face. With [sideMargin] it also keeps that much clear at either
 * side, set a little smaller or on two lines for it (down to [SIDE_ROOM_SHARE] of the size it would
 * have without), so a long name doesn't run edge to edge ("Phrygian dominant" at 1.3× text, Phase 52).
 * Where nothing down to [smallest] fits even on the whole face, it goes on down to [lastResort] rather than be cut off
 * (Japanese "メジャーペンタトニック" on a scale button at 2× text on a 320 dp phone, the overnight run of 2026-11-05).
 * In `:feature:practice` until Phase 77, when the pick buttons ([PickGrid]) and the switch rows ([SwitchRow]) came here.
 */
class WholeWordsAutoSize(
    private val largest: TextUnit,
    private val smallest: TextUnit,
    private val twoLineMargin: Dp = 0.dp,
    private val sideMargin: Dp = 0.dp,
    private val lastResort: TextUnit = smallest,
) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        val margin = 2 * twoLineMargin.roundToPx()
        val plain = largestFitting(constraints, text, margin) ?: return largestFitting(constraints, text, 0)
            ?: largestFitting(constraints, text, 0, from = smallest.value, floor = lastResort.value) ?: lastResort
        if (sideMargin == 0.dp) return plain
        val floor = maxOf(smallest.value, plain.value * SIDE_ROOM_SHARE)
        return largestFitting(constraints, text, margin, 2 * sideMargin.roundToPx(), floor) ?: plain
    }

    private fun TextAutoSizeLayoutScope.largestFitting(
        constraints: Constraints,
        text: AnnotatedString,
        margin: Int,
        sides: Int = 0,
        floor: Float? = null,
        from: Float = largest.value,
    ): TextUnit? {
        var size = from
        // Down to [floor] itself where one is given, else to just above [smallest] (which is the answer when nothing fits).
        while (if (floor == null) size > smallest.value else size >= floor - 0.001f) {
            val layout = performLayout(constraints, text, size.sp)
            val roomy = !constraints.hasBoundedHeight || layout.size.height <= constraints.maxHeight - margin
            val clearOfSides = sides == 0 || !constraints.hasBoundedWidth || layout.widestLine() <= constraints.maxWidth - sides
            if (!layout.didOverflowWidth && !layout.didOverflowHeight && roomy && clearOfSides && breaksBetweenWords(layout, text)) return size.sp
            size -= LABEL_STEP
        }
        return null
    }

    private fun TextLayoutResult.widestLine(): Float = (0 until lineCount).maxOfOrNull { getLineRight(it) - getLineLeft(it) } ?: 0f

    private fun breaksBetweenWords(layout: TextLayoutResult, text: AnnotatedString): Boolean = layout.breaksBetweenWords(text.text)

    override fun equals(other: Any?): Boolean =
        other is WholeWordsAutoSize && other.largest == largest && other.smallest == smallest && other.twoLineMargin == twoLineMargin &&
            other.sideMargin == sideMargin && other.lastResort == lastResort
    override fun hashCode(): Int =
        (((largest.hashCode() * 31 + smallest.hashCode()) * 31 + twoLineMargin.hashCode()) * 31 + sideMargin.hashCode()) * 31 + lastResort.hashCode()
}

/** A label keeps its side room ([WholeWordsAutoSize]'s sideMargin) only down to this share of the size it would have without. */
private const val SIDE_ROOM_SHARE = 0.85f

private const val LABEL_STEP = 0.5f
