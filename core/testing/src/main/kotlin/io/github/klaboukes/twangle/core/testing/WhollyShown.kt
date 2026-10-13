package io.github.klaboukes.twangle.core.testing

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.fail
import kotlin.math.abs

/**
 * Scrolls [node] into view (unless [scroll] is false) and checks all of it shows: its visible part
 * is as big as the node itself, and its text has no line or letter cut off and no ellipsis.
 * [what] names it in the failure message.
 */
fun assertWhollyShown(node: SemanticsNodeInteraction, what: String, scroll: Boolean = true) {
    if (scroll) node.performScrollTo()
    node.assertIsDisplayed()
    val semantics = node.fetchSemanticsNode()
    val visible = semantics.boundsInRoot
    if (abs(semantics.size.width - visible.width) > 1f || abs(semantics.size.height - visible.height) > 1f) {
        fail("\"$what\" shows ${visible.width} x ${visible.height} of ${semantics.size.width} x ${semantics.size.height} px")
    }
    val layouts = mutableListOf<TextLayoutResult>()
    semantics.config.getOrElseNullable(SemanticsActions.GetTextLayoutResult) { null }?.action?.invoke(layouts)
    // (TextLayoutResult.didOverflowWidth can't be used: it compares with the widest the text may
    // be, not what it needs.) Every line fits the text's box, none is cut off at the bottom, and
    // none ends in an ellipsis.
    for (layout in layouts) {
        if (layout.didOverflowHeight) fail("\"$what\" is cut off at the bottom")
        for (line in 0 until layout.lineCount) {
            // By width: a centred line may be placed by a wider paragraph than the text's box.
            val width = layout.getLineRight(line) - layout.getLineLeft(line)
            if (width > layout.size.width + 1f) fail("\"$what\" line $line is $width px wide in a box of ${layout.size.width}")
            if (layout.isLineEllipsized(line)) fail("\"$what\" line $line is ellipsized")
        }
    }
}
