package io.github.klaboukes.twangle.feature.tuner.components

import org.junit.Assert.assertEquals
import org.junit.Test

class MeterFrameTest {

    private val halfPi = (Math.PI / 2).toFloat()

    @Test
    fun `the meter spans 50 cents each way in a 2 to 1 box, for every look`() {
        assertEquals(50f, METER_RANGE_CENTS)
        assertEquals(2f, METER_ASPECT_RATIO)
    }

    @Test
    fun `the angle is straight up when in tune and at the edges at the ends of the range`() {
        assertEquals(0f, meterAngle(0f), 1e-6f)
        assertEquals(-halfPi, meterAngle(-50f), 1e-6f)
        assertEquals(halfPi, meterAngle(50f), 1e-6f)
        assertEquals(halfPi / 5f, meterAngle(10f), 1e-6f)
    }

    @Test
    fun `readings beyond the meter are pinned at the edge`() {
        assertEquals(-50f, meterCents(-200f))
        assertEquals(50f, meterCents(65f))
        assertEquals(-12f, meterCents(-12f))
    }

    @Test
    fun `no reading rests the marker in the centre`() {
        assertEquals(0f, meterCents(null))
    }

    @Test
    fun `the classic needle layer turns clockwise in degrees, a quarter turn at each end`() {
        assertEquals(0f, needleDegrees(0f), 1e-4f)
        assertEquals(90f, needleDegrees(50f), 1e-4f)
        assertEquals(-90f, needleDegrees(-50f), 1e-4f)
        assertEquals(-18f, needleDegrees(-10f), 1e-4f)
    }
}
