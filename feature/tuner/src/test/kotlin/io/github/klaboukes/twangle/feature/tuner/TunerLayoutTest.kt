package io.github.klaboukes.twangle.feature.tuner

import org.junit.Assert.assertEquals
import org.junit.Test

class TunerLayoutTest {

    @Test
    fun phoneUprightIsOneColumn() {
        assertEquals(TunerLayout.SINGLE_COLUMN, tunerLayoutFor(widthDp = 384f, heightDp = 780f, compactLandscape = false))
    }

    @Test
    fun phoneOnItsSideIsTwoPanes() {
        assertEquals(TunerLayout.TWO_PANE, tunerLayoutFor(widthDp = 780f, heightDp = 340f, compactLandscape = false))
    }

    @Test
    fun smallPhoneOnItsSideIsTwoPanes() {
        assertEquals(TunerLayout.TWO_PANE, tunerLayoutFor(widthDp = 640f, heightDp = 330f, compactLandscape = false))
    }

    @Test
    fun tabletInPortraitIsOneColumn() {
        assertEquals(TunerLayout.SINGLE_COLUMN, tunerLayoutFor(widthDp = 800f, heightDp = 1240f, compactLandscape = false))
    }

    @Test
    fun tabletInLandscapeIsTwoPanes() {
        assertEquals(TunerLayout.TWO_PANE, tunerLayoutFor(widthDp = 1280f, heightDp = 760f, compactLandscape = false))
    }

    @Test
    fun narrowWideWindowStaysOneColumn() {
        // E.g. split screen on a phone on its side: wider than tall, but too narrow for two columns.
        assertEquals(TunerLayout.SINGLE_COLUMN, tunerLayoutFor(widthDp = 560f, heightDp = 300f, compactLandscape = false))
    }

    @Test
    fun squareWindowStaysOneColumn() {
        assertEquals(TunerLayout.SINGLE_COLUMN, tunerLayoutFor(widthDp = 840f, heightDp = 840f, compactLandscape = false))
    }

    @Test
    fun theMinimumWidthIsIncluded() {
        assertEquals(TunerLayout.TWO_PANE, tunerLayoutFor(widthDp = TWO_PANE_MIN_WIDTH_DP, heightDp = 400f, compactLandscape = false))
    }

    @Test
    fun phoneOnItsSideIsCompactWhenAsked() {
        assertEquals(TunerLayout.COMPACT, tunerLayoutFor(widthDp = 780f, heightDp = 340f, compactLandscape = true))
    }

    @Test
    fun phonesOnTheirSideBesideTheRailAreCompactInFocusMode() {
        // The navigation rail (80 dp) stays in focus mode: the Xiaomi 12X and the Galaxy S26 beside it.
        assertEquals(TunerLayout.COMPACT, tunerLayoutFor(widthDp = 760f, heightDp = 360f, compactLandscape = true))
        assertEquals(TunerLayout.COMPACT, tunerLayoutFor(widthDp = 700f, heightDp = 330f, compactLandscape = true))
    }

    @Test
    fun tabletInLandscapeIsCompactWhenAsked() {
        assertEquals(TunerLayout.COMPACT, tunerLayoutFor(widthDp = 1280f, heightDp = 760f, compactLandscape = true))
    }

    @Test
    fun uprightAndSquareWindowsStayOneColumnWhenCompactIsAsked() {
        assertEquals(TunerLayout.SINGLE_COLUMN, tunerLayoutFor(widthDp = 384f, heightDp = 780f, compactLandscape = true))
        assertEquals(TunerLayout.SINGLE_COLUMN, tunerLayoutFor(widthDp = 800f, heightDp = 1240f, compactLandscape = true))
        assertEquals(TunerLayout.SINGLE_COLUMN, tunerLayoutFor(widthDp = 840f, heightDp = 840f, compactLandscape = true))
    }

    @Test
    fun focusModeStaysCompactWhenTheRailAndTheBarsComeBack() {
        // A Galaxy S26 with navigation buttons on its side: 663 dp without the rail and the bars, 578 dp
        // with them. The layout switching with the width hid and showed the bars ~40 times a second.
        assertEquals(TunerLayout.COMPACT, tunerLayoutFor(widthDp = 663f, heightDp = 360f, compactLandscape = true))
        assertEquals(TunerLayout.COMPACT, tunerLayoutFor(widthDp = 578f, heightDp = 330f, compactLandscape = true))
        assertEquals(TunerLayout.COMPACT, tunerLayoutFor(widthDp = 560f, heightDp = 300f, compactLandscape = true))
        // Without focus mode the narrow width keeps one column.
        assertEquals(TunerLayout.SINGLE_COLUMN, tunerLayoutFor(widthDp = 578f, heightDp = 330f, compactLandscape = false))
    }

    @Test
    fun phonesKeepTheColumnAtItsOwnSize() {
        // Upright: a Galaxy S26, the Xiaomi 12X, a large phone; on its side beside the rail (focus mode's controls).
        for ((width, height) in listOf(360f to 780f, 393f to 873f, 480f to 1000f, 578f to 330f, 780f to 360f)) {
            assertEquals("$width × $height", 1f, columnScaleFor(width, height), 0f)
        }
    }

    @Test
    fun anUprightTabletGrowsTheColumnWithItsWidth() {
        // The Lenovo Tab M11 upright (the tuner's space under the status bar, over the navigation bar).
        assertEquals(800f / COLUMN_SCALE_BASE_WIDTH_DP, columnScaleFor(800f, 1100f), 0.001f)
        // A short window keeps it from growing past what its height has room for.
        assertEquals(840f / COLUMN_SCALE_BASE_HEIGHT_DP, columnScaleFor(840f, 840f), 0.001f)
    }

    @Test
    fun theColumnGrowsSmoothlyAndStopsAtItsLargest() {
        assertEquals(1f, columnScaleFor(COLUMN_SCALE_BASE_WIDTH_DP, 1200f), 0f)
        assertEquals(1.01f, columnScaleFor(COLUMN_SCALE_BASE_WIDTH_DP * 1.01f, 1200f), 0.001f)
        assertEquals(COLUMN_MAX_SCALE, columnScaleFor(1200f, 1900f), 0f)
    }
}
