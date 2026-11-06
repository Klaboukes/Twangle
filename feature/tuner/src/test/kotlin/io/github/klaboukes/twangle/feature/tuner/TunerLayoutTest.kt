package io.github.klaboukes.twangle.feature.tuner

import org.junit.Assert.assertEquals
import org.junit.Test

class TunerLayoutTest {

    @Test
    fun phoneUprightIsOneColumn() {
        assertEquals(TunerLayout.SINGLE_COLUMN, tunerLayoutFor(widthDp = 384f, heightDp = 780f, focusMode = false))
    }

    @Test
    fun tabletInPortraitIsOneColumn() {
        assertEquals(TunerLayout.SINGLE_COLUMN, tunerLayoutFor(widthDp = 800f, heightDp = 1240f, focusMode = false))
    }

    @Test
    fun squareWindowStaysOneColumn() {
        assertEquals(TunerLayout.SINGLE_COLUMN, tunerLayoutFor(widthDp = 840f, heightDp = 840f, focusMode = false))
        assertEquals(TunerLayout.SINGLE_COLUMN, tunerLayoutFor(widthDp = 840f, heightDp = 840f, focusMode = true))
    }

    @Test
    fun everyWindowWiderThanTallIsWide() {
        // A phone on its side (beside the rail or not), a small one, a tablet on its side, split screen on its side.
        for ((width, height) in listOf(780f to 340f, 640f to 330f, 1280f to 760f, 560f to 300f, 401f to 400f)) {
            assertEquals("$width × $height", TunerLayout.WIDE, tunerLayoutFor(width, height, focusMode = false))
            assertEquals("$width × $height in focus mode", TunerLayout.WIDE, tunerLayoutFor(width, height, focusMode = true))
        }
    }

    @Test
    fun focusModeUprightStaysOneColumn() {
        // A large screen that keeps its rotation lock upright: focus mode's exit button is in the column's top row.
        assertEquals(TunerLayout.SINGLE_COLUMN, tunerLayoutFor(widthDp = 384f, heightDp = 780f, focusMode = true))
        assertEquals(TunerLayout.SINGLE_COLUMN, tunerLayoutFor(widthDp = 800f, heightDp = 1240f, focusMode = true))
    }

    @Test
    fun focusModeStaysWideWhenTheRailAndTheBarsComeBack() {
        // A Galaxy S26 with navigation buttons on its side: 663 dp without the rail and the bars, 578 dp
        // with them. The layout switching with the width hid and showed the bars ~40 times a second.
        assertEquals(TunerLayout.WIDE, tunerLayoutFor(widthDp = 663f, heightDp = 360f, focusMode = true))
        assertEquals(TunerLayout.WIDE, tunerLayoutFor(widthDp = 578f, heightDp = 330f, focusMode = true))
        assertEquals(TunerLayout.WIDE, tunerLayoutFor(widthDp = 578f, heightDp = 330f, focusMode = false))
    }

    @Test
    fun aToolOnItsSideKeepsTwoPanesWhereTheyFitUntilPhase74() {
        assertEquals(TunerLayout.TWO_PANE, tunerLayoutFor(widthDp = 780f, heightDp = 340f, focusMode = false, toolPanel = true))
        assertEquals(TunerLayout.TWO_PANE, tunerLayoutFor(widthDp = 1280f, heightDp = 760f, focusMode = false, toolPanel = true))
        assertEquals(TunerLayout.TWO_PANE, tunerLayoutFor(widthDp = TWO_PANE_MIN_WIDTH_DP, heightDp = 400f, focusMode = false, toolPanel = true))
        // Too narrow for two columns of controls (split screen): one column.
        assertEquals(TunerLayout.SINGLE_COLUMN, tunerLayoutFor(widthDp = 560f, heightDp = 300f, focusMode = false, toolPanel = true))
        assertEquals(TunerLayout.SINGLE_COLUMN, tunerLayoutFor(widthDp = 384f, heightDp = 780f, focusMode = false, toolPanel = true))
    }

    @Test
    fun aToolInFocusModeIsCompactHoweverNarrow() {
        for ((width, height) in listOf(780f to 340f, 663f to 360f, 578f to 330f, 560f to 300f, 1280f to 760f)) {
            assertEquals("$width × $height", TunerLayout.COMPACT, tunerLayoutFor(width, height, focusMode = true, toolPanel = true))
        }
        assertEquals(TunerLayout.SINGLE_COLUMN, tunerLayoutFor(widthDp = 384f, heightDp = 780f, focusMode = true, toolPanel = true))
    }
}
