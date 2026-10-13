package io.github.klaboukes.twangle.core.designsystem.meter

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberFitTest {

    @Test
    fun numbersWithRoomToSpareKeepTheirSize() {
        // The classic dial: neighbours ~40u apart, "50" ~11u wide.
        assertEquals(1f, numberFit(nearest = 40f, widest = 11f, u = 1f), 0f)
    }

    @Test
    fun closeNumbersAreSetSmallerToKeepTheGap() {
        // 22u apart and 15u wide leaves 7u; set at 14/15 of the size the gap is 8u again.
        val fit = numberFit(nearest = 22f, widest = 15f, u = 1f)
        assertEquals(14f / 15f, fit, 1e-4f)
        assertEquals(MIN_NUMBER_GAP_UNITS, 22f - 15f * fit, 1e-3f)
    }

    @Test
    fun theGapIsMeasuredInTheMetersOwnUnits() {
        // The same scale three times as big fits the same way.
        assertEquals(numberFit(22f, 15f, 1f), numberFit(66f, 45f, 3f), 1e-4f)
    }

    @Test
    fun numbersNeverShrinkBelowTheFloor() {
        assertEquals(MIN_NUMBER_FIT, numberFit(nearest = 12f, widest = 15f, u = 1f), 0f)
    }

    @Test
    fun nothingMeasuredChangesNothing() {
        assertEquals(1f, numberFit(nearest = 20f, widest = 0f, u = 1f), 0f)
    }
}
