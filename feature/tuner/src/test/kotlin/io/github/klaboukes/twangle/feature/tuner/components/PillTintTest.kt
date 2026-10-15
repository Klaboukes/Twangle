package io.github.klaboukes.twangle.feature.tuner.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PillTintTest {

    @Test
    fun `no tint far off`() {
        assertEquals(0f, pillTint(PILL_TINT_FROM_CENTS), 0f)
        assertEquals(0f, pillTint(-40f), 0f)
        assertEquals(0f, pillTint(300f), 0f)
    }

    @Test
    fun `most tint at the target, but never a full pill`() {
        assertEquals(PILL_NEAR_TINT, pillTint(0f), 1e-6f)
        assertTrue(PILL_NEAR_TINT < 1f)
    }

    @Test
    fun `flat and sharp tint alike`() {
        for (cents in listOf(1f, 3.5f, 8f, 17f, 24f)) {
            assertEquals(pillTint(cents), pillTint(-cents), 0f)
        }
    }

    @Test
    fun `grows steadily as the note comes closer`() {
        var previous = 0f
        for (tenth in 250 downTo 0) {
            val tint = pillTint(tenth / 10f)
            assertTrue("at ${tenth / 10f} cents", tint >= previous)
            previous = tint
        }
    }

    @Test
    fun `already clearly tinted at the edge of in tune`() {
        // Relaxed counts 5 cents as in tune: the pill should be most of the way there.
        assertTrue(pillTint(5f) > 0.75f * PILL_NEAR_TINT)
        // Half way to the far edge it has only just begun.
        assertTrue(pillTint(12.5f) <= 0.5f * PILL_NEAR_TINT)
    }

    @Test
    fun `the bloom grows and deepens steadily up to in tune`() {
        var reach = 0f
        var centre = 0f
        for (step in 0..100) {
            val fill = step / 100f
            assertTrue(bloomReach(fill) >= reach)
            assertTrue(bloomCentre(fill) >= centre)
            reach = bloomReach(fill)
            centre = bloomCentre(fill)
        }
    }

    @Test
    fun `close but not in tune the bloom reaches past the ends, in tune its middle is full`() {
        assertTrue(bloomReach(0f) < 1f)
        assertTrue(bloomReach(PILL_NEAR_TINT) > 1f)
        assertTrue(bloomCentre(PILL_NEAR_TINT) < 1f)
        assertEquals(1f, bloomCentre(1f), 0f)
    }
}
