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
}
