package io.github.klaboukes.twangle.core.dsp.strum

import io.github.klaboukes.twangle.core.dsp.strum.StrumVerdict.FLAT
import io.github.klaboukes.twangle.core.dsp.strum.StrumVerdict.IN_TUNE
import io.github.klaboukes.twangle.core.dsp.strum.StrumVerdict.NOT_HEARD
import io.github.klaboukes.twangle.core.dsp.strum.StrumVerdict.SHARP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StrumVerdictTest {

    private fun of(cents: Float, present: Boolean = true, shadowed: Boolean = false, tolerance: Float = 5f, previous: StrumVerdict? = null) =
        StrumVerdict.of(cents, present, shadowed, tolerance, previous)

    @Test
    fun `within the tolerance is in tune, beyond it flat or sharp`() {
        assertEquals(IN_TUNE, of(0f))
        assertEquals(IN_TUNE, of(5f))
        assertEquals(IN_TUNE, of(-5f))
        assertEquals(SHARP, of(5.1f))
        assertEquals(FLAT, of(-5.1f))
        assertEquals(FLAT, of(-30f))
        assertEquals(SHARP, of(99f))
    }

    @Test
    fun `the Precise tolerance is narrower`() {
        assertEquals(IN_TUNE, of(2f, tolerance = 2f))
        assertEquals(SHARP, of(2.1f, tolerance = 2f))
        assertEquals(FLAT, of(-3f, tolerance = 2f))
    }

    @Test
    fun `a string not heard has no direction`() {
        assertEquals(NOT_HEARD, of(Float.NaN, present = false))
        assertEquals(NOT_HEARD, of(12f, present = false))
        assertEquals(NOT_HEARD, of(Float.NaN, present = true))
    }

    @Test
    fun `a shadowed string gets the merge's margin`() {
        assertEquals(IN_TUNE, of(7.5f, shadowed = true))
        assertEquals(SHARP, of(7.6f, shadowed = true))
        assertEquals(IN_TUNE, of(-4.5f, shadowed = true, tolerance = 2f))
        assertEquals(FLAT, of(-4.6f, shadowed = true, tolerance = 2f))
    }

    @Test
    fun `in tune is held a cent longer so a reading at the edge doesn't flicker`() {
        assertEquals(SHARP, of(5.5f))
        assertEquals(IN_TUNE, of(5.5f, previous = IN_TUNE))
        assertEquals(IN_TUNE, of(-6f, previous = IN_TUNE))
        assertEquals(FLAT, of(-6.1f, previous = IN_TUNE))
        assertEquals(SHARP, of(5.5f, previous = SHARP))
    }

    @Test
    fun `reads a result by string index`() {
        val result = StrumResult(3)
        result.stringCount = 3
        result.present[0] = true
        result.cents[0] = -12f
        result.present[1] = true
        result.cents[1] = 1f
        result.present[2] = false
        result.cents[2] = Float.NaN
        assertEquals(FLAT, StrumVerdict.of(result, 0))
        assertEquals(IN_TUNE, StrumVerdict.of(result, 1))
        assertEquals(NOT_HEARD, StrumVerdict.of(result, 2))
        assertThrows(IllegalArgumentException::class.java) { StrumVerdict.of(result, 3) }
        assertThrows(IllegalArgumentException::class.java) { of(0f, tolerance = -1f) }
    }
}
