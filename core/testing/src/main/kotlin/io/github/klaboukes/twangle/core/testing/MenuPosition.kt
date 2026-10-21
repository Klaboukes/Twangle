package io.github.klaboukes.twangle.core.testing

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue

/**
 * Checks an open Material dropdown menu, found by [inMenu] (any node in it), opened under [anchor]; or, where it
 * can't fit there, that Material moved it up, as it does when the menu is taller than the room under the anchor
 * less its 48 dp margin. The menu is a window of its own, measured on the real screen, which [PhoneScreen] doesn't
 * force: the room under the anchor differs from phone to phone. [what] names it in the failure message.
 */
fun assertMenuUnder(anchor: SemanticsNodeInteraction, inMenu: SemanticsNodeInteraction, what: String) {
    val button = anchor.fetchSemanticsNode()
    val buttonBottom = button.positionOnScreen.y + button.size.height
    val window = button.root()
    val room = window.positionOnScreen.y + window.size.height - buttonBottom
    val menu = inMenu.fetchSemanticsNode().root()
    // Material's MenuVerticalMargin, in the menu window's own density.
    val margin = with(menu.layoutInfo.density) { 48.dp.toPx() }
    val fits = menu.size.height <= room - margin
    assertTrue(
        "$what: the menu under the name (menu ${menu.size.height} px, ${room - margin} px of room under it)",
        !fits || menu.positionOnScreen.y >= buttonBottom,
    )
}

private fun SemanticsNode.root(): SemanticsNode = generateSequence(this) { it.parent }.last()
