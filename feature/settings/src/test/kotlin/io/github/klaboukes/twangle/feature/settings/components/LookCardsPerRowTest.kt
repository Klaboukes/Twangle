package io.github.klaboukes.twangle.feature.settings.components

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** How many look cards (styles, meter looks, wave looks) a row of the settings holds: from the room, not the device. */
class LookCardsPerRowTest {

    private val phones = listOf(
        DpSize(360.dp, 780.dp), // a Galaxy S26 upright
        DpSize(393.dp, 873.dp), // the Xiaomi 12X upright
        DpSize(780.dp, 360.dp), // on their sides
        DpSize(873.dp, 393.dp),
        DpSize(932.dp, 430.dp), // the tallest phones on their side
    )

    private val tablets = listOf(
        DpSize(800.dp, 1200.dp), // the Lenovo Tab M11 upright, above the navigation bar
        DpSize(1200.dp, 800.dp), // and on its side, beside the rail
        DpSize(560.dp, 560.dp), // the smallest large window
    )

    @Test
    fun `three to a row at normal text on any window`() {
        for (window in phones + tablets + DpSize.Unspecified) {
            assertEquals("$window", 3, lookCardsPerRow(1f, window))
            assertEquals("$window", 3, lookCardsPerRow(1.29f, window))
        }
    }

    @Test
    fun `a phone shows two to a row from 1_3x text`() {
        for (window in phones) {
            assertEquals("$window", 2, lookCardsPerRow(1.3f, window))
            assertEquals("$window", 2, lookCardsPerRow(2f, window))
        }
    }

    @Test
    fun `a large window keeps three to a row at any text size`() {
        for (window in tablets) {
            assertEquals("$window", 3, lookCardsPerRow(1.3f, window))
            assertEquals("$window", 3, lookCardsPerRow(2f, window))
        }
    }

    @Test
    fun `just short of large either way counts as small`() {
        assertEquals(2, lookCardsPerRow(1.3f, DpSize(559.dp, 1200.dp)))
        assertEquals(2, lookCardsPerRow(1.3f, DpSize(1200.dp, 559.dp)))
    }

    @Test
    fun `an unknown window counts as small`() {
        assertEquals(2, lookCardsPerRow(1.3f, DpSize.Unspecified))
    }
}
