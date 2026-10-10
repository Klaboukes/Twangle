package com.intune.feature.tuner

import org.junit.Assert.assertEquals
import org.junit.Test

class TunerLayoutTest {

    @Test
    fun phoneUprightIsOneColumn() {
        assertEquals(TunerLayout.SINGLE_COLUMN, tunerLayoutFor(widthDp = 384f, heightDp = 780f))
    }

    @Test
    fun phoneOnItsSideIsTwoPanes() {
        assertEquals(TunerLayout.TWO_PANE, tunerLayoutFor(widthDp = 780f, heightDp = 340f))
    }

    @Test
    fun smallPhoneOnItsSideIsTwoPanes() {
        assertEquals(TunerLayout.TWO_PANE, tunerLayoutFor(widthDp = 640f, heightDp = 330f))
    }

    @Test
    fun tabletInPortraitIsOneColumn() {
        assertEquals(TunerLayout.SINGLE_COLUMN, tunerLayoutFor(widthDp = 800f, heightDp = 1240f))
    }

    @Test
    fun tabletInLandscapeIsTwoPanes() {
        assertEquals(TunerLayout.TWO_PANE, tunerLayoutFor(widthDp = 1280f, heightDp = 760f))
    }

    @Test
    fun narrowWideWindowStaysOneColumn() {
        // E.g. split screen on a phone on its side: wider than tall, but too narrow for two columns.
        assertEquals(TunerLayout.SINGLE_COLUMN, tunerLayoutFor(widthDp = 560f, heightDp = 300f))
    }

    @Test
    fun squareWindowStaysOneColumn() {
        assertEquals(TunerLayout.SINGLE_COLUMN, tunerLayoutFor(widthDp = 840f, heightDp = 840f))
    }

    @Test
    fun theMinimumWidthIsIncluded() {
        assertEquals(TunerLayout.TWO_PANE, tunerLayoutFor(widthDp = TWO_PANE_MIN_WIDTH_DP, heightDp = 400f))
    }
}
