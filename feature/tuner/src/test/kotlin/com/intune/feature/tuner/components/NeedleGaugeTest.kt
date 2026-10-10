package com.intune.feature.tuner.components

import org.junit.Assert.assertEquals
import org.junit.Test

class NeedleGaugeTest {

    private val halfPi = (Math.PI / 2).toFloat()

    @Test
    fun `needle points straight up when in tune and to the edges at the ends of the range`() {
        assertEquals(0f, needleAngle(0f), 1e-6f)
        assertEquals(-halfPi, needleAngle(-50f), 1e-6f)
        assertEquals(halfPi, needleAngle(50f), 1e-6f)
        assertEquals(halfPi / 5f, needleAngle(10f), 1e-6f)
    }

    @Test
    fun `readings beyond the gauge pin the needle to the edge`() {
        assertEquals(-50f, needleCents(-200f))
        assertEquals(50f, needleCents(65f))
        assertEquals(-12f, needleCents(-12f))
    }

    @Test
    fun `no reading rests the needle in the centre`() {
        assertEquals(0f, needleCents(null))
    }

    @Test
    fun `the needle layer turns clockwise in degrees, a quarter turn at each end`() {
        assertEquals(0f, needleDegrees(0f), 1e-4f)
        assertEquals(90f, needleDegrees(50f), 1e-4f)
        assertEquals(-90f, needleDegrees(-50f), 1e-4f)
        assertEquals(-18f, needleDegrees(-10f), 1e-4f)
    }
}
