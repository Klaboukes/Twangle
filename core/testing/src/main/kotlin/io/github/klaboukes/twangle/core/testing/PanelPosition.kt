package io.github.klaboukes.twangle.core.testing

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue

/**
 * Checks an open floating panel (`FloatingPanel` in `:core:designsystem`, found by its tag: [panel]) opened under
 * [anchor]: its top under the anchor's bottom; or, where it can't fit there, that it slid up to stay inside [window]
 * (the screen it floats over), as it does when it is taller than the room under its anchor. The room is reckoned
 * less the panel's gap and margin and the tallest system bar, so a panel that had to slide is never taken for one
 * that could fit. [what] names it in the failure message. Until Phase 77 the Practice tools' panels were Material
 * menus, windows of their own measured on the real screen (`assertMenuUnder`).
 */
fun assertPanelUnder(anchor: SemanticsNodeInteraction, panel: SemanticsNodeInteraction, window: SemanticsNodeInteraction, what: String) {
    val under = anchor.getBoundsInRoot()
    val shown = panel.getBoundsInRoot()
    val room = window.getBoundsInRoot().bottom - under.bottom - PANEL_GAP - PANEL_MARGIN - SystemBars.BUTTON_BAR
    val fits = shown.bottom - shown.top <= room
    assertTrue("$what: the panel $shown under its anchor $under (${room.value} dp of room under it)", !fits || shown.top >= under.bottom - SLACK)
}

/** The panel's gap to its anchor and its margin to the window's edge (FloatingPanel's). */
private val PANEL_GAP = 8.dp
private val PANEL_MARGIN = 8.dp
private val SLACK = 0.5.dp
