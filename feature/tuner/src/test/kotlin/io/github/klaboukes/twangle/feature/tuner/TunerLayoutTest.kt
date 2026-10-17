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
    fun uprightAndNarrowWindowsStayOneColumnWhenCompactIsAsked() {
        assertEquals(TunerLayout.SINGLE_COLUMN, tunerLayoutFor(widthDp = 384f, heightDp = 780f, compactLandscape = true))
        assertEquals(TunerLayout.SINGLE_COLUMN, tunerLayoutFor(widthDp = 800f, heightDp = 1240f, compactLandscape = true))
        assertEquals(TunerLayout.SINGLE_COLUMN, tunerLayoutFor(widthDp = 560f, heightDp = 300f, compactLandscape = true))
        assertEquals(TunerLayout.SINGLE_COLUMN, tunerLayoutFor(widthDp = 840f, heightDp = 840f, compactLandscape = true))
    }
}
