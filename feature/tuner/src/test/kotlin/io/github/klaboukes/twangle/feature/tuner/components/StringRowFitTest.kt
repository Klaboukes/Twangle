package io.github.klaboukes.twangle.feature.tuner.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StringRowFitTest {

    /** A six-string guitar upright on a narrow phone keeps its full-size buttons, spread out. */
    @Test
    fun sixStringsFitAtFullSize() {
        val fit = stringRowFit(availableDp = 328f, count = 6, buttonWidthDp = 40f, ringGapDp = 4f, minSlotDp = 48f)
        assertFalse(fit.squeezed)
        assertEquals(1f, fit.buttonScale)
        assertEquals(48f, fit.slotDp)
    }

    /** A four-string bass spreads its buttons over the same width. */
    @Test
    fun fourStringsSpreadOut() {
        val fit = stringRowFit(availableDp = 328f, count = 4, buttonWidthDp = 40f, ringGapDp = 4f, minSlotDp = 48f)
        assertFalse(fit.squeezed)
        assertEquals(1f, fit.buttonScale)
    }

    /** Eight strings on a narrow phone: equal shares, smaller buttons that still keep apart. */
    @Test
    fun eightStringsShrinkToFit() {
        for (button in listOf(38f, 40f, 42f, 44f, 46f)) {
            val fit = stringRowFit(availableDp = 328f, count = 8, buttonWidthDp = button, ringGapDp = 4f, minSlotDp = 48f)
            assertTrue(fit.squeezed)
            assertEquals(41f, fit.slotDp, 0.001f)
            val outer = button * fit.buttonScale + fit.ringGapDp * 2f
            assertEquals(41f - SQUEEZED_GAP_DP, outer, 0.001f)
            assertTrue(fit.ringGapDp in MIN_RING_GAP_DP..4f)
            assertTrue(fit.buttonScale in 0.6f..1f)
        }
    }

    /** The compact layout's small row packs its chips 4 dp apart, and shrinks only when it must. */
    @Test
    fun smallRowPacksThenShrinks() {
        val roomy = stringRowFit(availableDp = 400f, count = 8, buttonWidthDp = 28f, ringGapDp = 2f, minSlotDp = 0f, spacingDp = 4f)
        assertFalse(roomy.squeezed)
        assertEquals(32f, roomy.slotDp)
        val tight = stringRowFit(availableDp = 200f, count = 8, buttonWidthDp = 28f, ringGapDp = 2f, minSlotDp = 0f, spacingDp = 4f)
        assertTrue(tight.squeezed)
        assertEquals(25f, tight.slotDp, 0.001f)
        assertEquals(2f, tight.ringGapDp)
        assertEquals((25f - SQUEEZED_GAP_DP - 4f) / 28f, tight.buttonScale, 0.001f)
    }

    @Test
    fun exactFitIsNotSqueezed() {
        assertFalse(stringRowFit(availableDp = 7 * 54f, count = 7, buttonWidthDp = 46f, ringGapDp = 4f, minSlotDp = 48f).squeezed)
        assertTrue(stringRowFit(availableDp = 7 * 54f - 1f, count = 7, buttonWidthDp = 46f, ringGapDp = 4f, minSlotDp = 48f).squeezed)
    }

    /** Spread evenly, the faces start a gap plus the touch slot's margin and the ring's room in. */
    @Test
    fun faceInsetSpreadEvenly() {
        // Classic's 38 dp keys in 48 dp slots, six on a 328 dp row: gaps of (328 - 288) / 7.
        val fit = stringRowFit(availableDp = 328f, count = 6, buttonWidthDp = 38f, ringGapDp = 4f, minSlotDp = 48f)
        assertEquals(40f / 7f + 1f + 4f, stringFaceInset(328f, 6, 38f, fit), 0.001f)
    }

    /** Squeezed, each face sits in the middle of its share, inside its ring's room. */
    @Test
    fun faceInsetSqueezed() {
        val fit = stringRowFit(availableDp = 328f, count = 8, buttonWidthDp = 40f, ringGapDp = 4f, minSlotDp = 48f)
        val outer = 40f * fit.buttonScale + fit.ringGapDp * 2f
        assertEquals((41f - outer) / 2f + fit.ringGapDp, stringFaceInset(328f, 8, 40f, fit), 0.001f)
    }
}
