package io.github.klaboukes.twangle.core.testing

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
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
    val semantics = node.fetchSemanticsNode()
    if (!scroll) {
        node.assertIsDisplayed()
        checkWhollyShown(semantics, what)
        return
    }
    // Only where it can scroll, and only when not wholly in view already (it wouldn't move): a pane
    // sized to fit has nothing to scroll. Each fetch and scroll waits for the screen to settle, so
    // fetching once saves most of a check's time (2026-10-30).
    if (checkedWithoutScrolling(semantics, what)) return
    if (!scrollIntoView(semantics)) node.performScrollTo()
    node.assertIsDisplayed()
    checkWhollyShown(node.fetchSemanticsNode(), what)
}

/**
 * Scrolls the vertical scrolling column [semantics] is in once, as far as it goes, to bring all of it into view (its
 * top, if it is taller than the column); false where it is in none, for performScrollTo. Not performScrollTo there:
 * it scrolls again until the node is in view, so a text reaching past the end of its column (Burmese's cents line on
 * the tablet, 3 px past the window's bottom, the full check of 2026-11-09) hung the test for its 40 minutes, where
 * this lets the check fail at once and say how much shows.
 */
private fun scrollIntoView(semantics: SemanticsNode): Boolean {
    val column = scrollingColumn(semantics) ?: return false
    if (!column.config.contains(SemanticsProperties.VerticalScrollAxisRange)) return false
    val view = column.boundsInRoot
    val top = semantics.positionInRoot.y
    val bottom = top + semantics.size.height
    val by = if (top < view.top || semantics.size.height > view.height) top - view.top else bottom - view.bottom
    if (abs(by) >= 1f) {
        val scroll = column.config[SemanticsActions.ScrollBy].action
        InstrumentationRegistry.getInstrumentation().runOnMainSync { scroll?.invoke(0f, by) }
    }
    return true
}

/**
 * Scrolls the vertical scrolling column [semantics] is in so that its top is at the column's top,
 * as far as the column goes: then the texts after it are in view too. performScrollTo moves a text
 * only just into view, so in [assertEveryTextWhollyShown] each next text needed a scroll of its own
 * (17 on one Ear training screen, about 90 ms each). Nothing where it isn't in one.
 */
internal fun SemanticsNodeInteractionsProvider.scrollToTheTop(semantics: SemanticsNode) {
    val column = scrollingColumn(semantics) ?: return
    if (!column.config.contains(SemanticsProperties.VerticalScrollAxisRange)) return
    val by = semantics.positionInRoot.y - column.boundsInRoot.top
    if (abs(by) < 1f) return
    onNode(SemanticsMatcher("semantics id ${column.id}") { it.id == column.id }, useUnmergedTree = true)
        .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, by) }
}

/**
 * [semantics], as fetched, checked as [assertWhollyShown] checks a node once scrolled to, when it needs
 * no scroll: outside a scrolling column, or wholly in view in one already (scrolling to it would not
 * move it). Else false, with nothing checked: it must be scrolled to first. For
 * [assertEveryTextWhollyShown], which fetches a screen's texts once rather than once for each.
 */
internal fun checkedWithoutScrolling(semantics: SemanticsNode, what: String): Boolean {
    if (scrollingColumnHeight(semantics) != null && !wholly(semantics, semantics.size.height.toFloat())) return false
    // As assertIsDisplayed, which is not called here: a text of no size shows nothing.
    if (semantics.size.width <= 0 || semantics.size.height <= 0) fail("\"$what\" is not displayed (${semantics.size})")
    checkWhollyShown(semantics, what)
    return true
}

/** Whether [semantics]'s visible part is its width and [height] (px), within a px. */
private fun wholly(semantics: SemanticsNode, height: Float): Boolean {
    val visible = semantics.boundsInRoot
    return abs(semantics.size.width - visible.width) <= 1f && abs(height - visible.height) <= 1f
}

/** The checks of [assertWhollyShown] on [semantics] where it is now. */
private fun checkWhollyShown(semantics: SemanticsNode, what: String) {
    val visible = semantics.boundsInRoot
    val column = scrollingColumnHeight(semantics)
    val shownHeight = if (column != null && semantics.size.height > column + 1f) column else semantics.size.height.toFloat()
    if (!wholly(semantics, shownHeight)) {
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
private fun scrollingColumnHeight(node: SemanticsNode): Float? = scrollingColumn(node)?.boundsInRoot?.height

/** The nearest scrolling ancestor of [node], or null outside one. */
private fun scrollingColumn(node: SemanticsNode): SemanticsNode? {
    var parent = node.parent
    while (parent != null && !parent.config.contains(SemanticsActions.ScrollBy)) parent = parent.parent
    return parent
}
