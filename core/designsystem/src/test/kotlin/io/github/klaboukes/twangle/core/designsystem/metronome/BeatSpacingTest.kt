package io.github.klaboukes.twangle.core.designsystem.metronome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatSpacingTest {
    /** Beats per bar of 2/4 to 5/4, 6/8 (two groups of three) and 7/8. */
    private val meters = listOf(2, 3, 4, 5, 6, 7)

    @Test
    fun withoutClicksBetweenTheBeatsTheSpacingIsAsBefore() {
        for (width in listOf(200f, 328f, 360f)) {
            for (beats in meters) {
                assertEquals("$beats beats in $width dp", minOf(48f, (width - 16f) / beats), beatSpacing(width, beats, 1, 1f), 0.001f)
            }
        }
    }

    @Test
    fun theBeatsKeepTheirPlacesWhereTheSmallMarksFit() {
        // A 360 dp phone's row (328 dp) and the widest (360 dp): every subdivision a meter offers fits.
        for (width in listOf(328f, 360f)) {
            for (beats in meters) {
                val offered = if (beats >= 6) listOf(2) else listOf(2, 3, 4)
                for (clicks in offered) {
                    assertEquals("$beats beats, $clicks clicks, $width dp", beatSpacing(width, beats, 1, 1f), beatSpacing(width, beats, clicks, 1f), 0.001f)
                }
            }
        }
    }

    @Test
    fun theLastBeatsSmallMarksStayInsideTheRow() {
        for (width in listOf(160f, 220f, 260f, 328f, 360f)) {
            for (beats in meters) {
                for (clicks in 2..4) {
                    val s = beatSpacing(width, beats, clicks, 1f)
                    val lastBeat = width / 2f + (beats - 1) / 2f * s
                    val lastMark = lastBeat + (clicks - 1f) / clicks * s
                    assertTrue("$beats beats, $clicks clicks, $width dp: last mark at $lastMark", lastMark <= width - 8f + 0.001f)
                    assertTrue("never wider than without them", s <= beatSpacing(width, beats, 1, 1f) + 0.001f)
                }
            }
        }
    }

    @Test
    fun aNarrowRowSetsTheBeatsCloserOnlyAsMuchAsNeeded() {
        // 5/4 in sixteenths on a 260 dp row: the last beat's three marks need 0.75 of a spacing after it.
        val s = beatSpacing(260f, 5, 4, 1f)
        assertEquals((130f - 8f) / (2f + 0.75f), s, 0.001f)
        assertTrue(s < 48f)
    }

    @Test
    fun theSpacingScalesWithTheDp() {
        assertEquals(beatSpacing(328f, 4, 3, 1f) * 2.75f, beatSpacing(328f * 2.75f, 4, 3, 2.75f), 0.01f)
    }
}
