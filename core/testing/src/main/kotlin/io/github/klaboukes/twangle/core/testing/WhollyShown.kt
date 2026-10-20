package io.github.klaboukes.twangle.core.testing

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.fail
import kotlin.math.abs

/**
 * Scrolls [node] into view (unless [scroll] is false, or it is in no scrolling column) and checks all of it shows: its visible part
 * is as big as the node itself, and its text has no line or letter cut off and no ellipsis. A node
 * taller than the scrolling column it is in (a long paragraph at 2× text on a phone on its side)
 * can't show all at once: it must fill the column's height, and scrolling shows the rest.
 * [what] names it in the failure message.
 */
fun assertWhollyShown(node: SemanticsNodeInteraction, what: String, scroll: Boolean = true) {
    // Only where it can scroll: a pane sized to fit has nothing to scroll.
    if (scroll && scrollingColumnHeight(node.fetchSemanticsNode()) != null) node.performScrollTo()
    node.assertIsDisplayed()
    val semantics = node.fetchSemanticsNode()
    val visible = semantics.boundsInRoot
    val column = scrollingColumnHeight(semantics)
    val shownHeight = if (column != null && semantics.size.height > column + 1f) column else semantics.size.height.toFloat()
    if (abs(semantics.size.width - visible.width) > 1f || abs(shownHeight - visible.height) > 1f) {
        fail("\"$what\" shows ${visible.width} x ${visible.height} of ${semantics.size.width} x ${semantics.size.height} px")
    }
    val layouts = mutableListOf<TextLayoutResult>()
    semantics.config.getOrElseNullable(SemanticsActions.GetTextLayoutResult) { null }?.action?.invoke(layouts)
    // (TextLayoutResult.didOverflowWidth can't be used: it compares with the widest the text may
    // be, not what it needs.) Every line fits the text's box, none is cut off at the bottom, and
    // none ends in an ellipsis.
    for (layout in layouts) {
        if (layout.isCutOffAtTheBottom()) {
            val lines = "${layout.lineCount} lines${if (layout.multiParagraph.didExceedMaxLines) ", more than it may have" else ""}"
            fail("\"$what\" is cut off at the bottom ($lines, ${layout.multiParagraph.height} px tall in a box of ${layout.size.height})")
        }
        for (line in 0 until layout.lineCount) {
            // By width: a centred line may be placed by a wider paragraph than the text's box.
            val width = layout.getLineRight(line) - layout.getLineLeft(line)
            val text = layout.layoutInput.text.substring(layout.getLineStart(line), layout.getLineEnd(line))
            if (width > layout.size.width + slackFor(text, layout.size.width)) {
                fail("\"$what\" line $line (\"$text\", of ${layout.lineCount}) is $width px wide in a box of ${layout.size.width}")
            }
            if (layout.isLineEllipsized(line)) fail("\"$what\" line $line is ellipsized")
        }
    }
}

/**
 * How many px a line of [text] may be wider than its box of [width] px: one px of rounding, and up to
 * 2 % more for a line the line breaker could have broken (it holds a space) but judged to fit, which
 * a font's shaping can make a little wider than its measure (Samsung's Hindi font: 4 px of 984, 8 of
 * 444, drawn whole). A word too long for its box can't be broken and gets the px alone.
 */
private fun slackFor(text: String, width: Int): Float =
    if (text.trim().any { it.isWhitespace() || it == '​' }) maxOf(1f, width * SHAPING_SLACK) else 1f

private const val SHAPING_SLACK = 0.02f

/**
 * A line lost (more lines than the text may have), or the lines more than a px taller than the text's box.
 * Not `didOverflowHeight` alone: a box sized in dp to just the line height (a 48 dp row at 2× text)
 * rounds to whole px one way and the text's lines the other, at densities a forced window size brings
 * (`PhoneScreen`), and the text then overflows by a fraction of a px. The same px of slack as the width check.
 */
fun TextLayoutResult.isCutOffAtTheBottom() =
    didOverflowHeight && (multiParagraph.didExceedMaxLines || multiParagraph.height > size.height + 1f)

/** The visible height of the nearest scrolling ancestor of [node] (px), or null outside one. */
private fun scrollingColumnHeight(node: SemanticsNode): Float? {
    var parent = node.parent
    while (parent != null && !parent.config.contains(SemanticsActions.ScrollBy)) parent = parent.parent
    return parent?.boundsInRoot?.height
}
