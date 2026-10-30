package io.github.klaboukes.twangle.core.designsystem.meter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrowsTest {

    @Test
    fun `in tune and just off, no arrow is lit`() {
        for (side in listOf(-1, 1)) for (k in 0 until ARROW_COUNT) {
            assertEquals(0f, arrowLight(side, k, 0f), 0f)
            assertEquals(0f, arrowLight(side, k, 2f * side), 0f)
        }
    }

    @Test
    fun `each arrow lights over its own step, 2 to 5 cents, 5 to 10, 10 to 20 and 20 to 40`() {
        val steps = listOf(2f to 5f, 5f to 10f, 10f to 20f, 20f to 40f)
        assertEquals(ARROW_COUNT, steps.size)
        for ((k, step) in steps.withIndex()) {
            val (from, full) = step
            assertEquals(from, arrowLightsFrom(k))
            assertEquals(full, arrowFullAt(k))
            for (side in listOf(-1, 1)) {
                assertEquals(0f, arrowLight(side, k, from * side), 0f)
                assertEquals(0.5f, arrowLight(side, k, (from + full) / 2f * side), 1e-6f)
                assertEquals(1f, arrowLight(side, k, full * side), 0f)
            }
        }
    }

    @Test
    fun `one lit arrow means almost there, two close`() {
        val lit = { cents: Float -> (0 until ARROW_COUNT).count { arrowLight(1, it, cents) > 0f } }
        // Inside the in-tune window only the inner arrow lights.
        assertEquals(0, lit(1.5f))
        assertEquals(1, lit(3f))
        assertEquals(1, lit(5f))
        assertEquals(2, lit(8f))
        assertEquals(3, lit(15f))
        assertEquals(4, lit(30f))
    }

    @Test
    fun `only the side the note is off lights, the inner arrow first`() {
        for (k in 0 until ARROW_COUNT) assertEquals(0f, arrowLight(1, k, -8f), 0f)
        assertEquals(1f, arrowLight(-1, 0, -8f), 0f)
        assertTrue(arrowLight(-1, 1, -8f) in 0.01f..0.99f)
        assertEquals(0f, arrowLight(-1, 2, -8f), 0f)
    }

    @Test
    fun `more arrows light the further off the note is`() {
        val lit = { cents: Float -> (0 until ARROW_COUNT).sumOf { arrowLight(1, it, cents).toDouble() } }
        assertTrue(lit(3f) < lit(5f))
        assertTrue(lit(5f) < lit(15f))
        assertTrue(lit(15f) < lit(30f))
        // All four from 40 cents, and past the edge.
        assertEquals(4.0, lit(40f), 1e-6)
        // Past the edge, all four.
        assertEquals(4.0, lit(80f), 1e-6)
    }

    @Test
    fun `the arrows point at the centre, the outer ones smaller, all inside the box`() {
        val arrows = ArrowsGeometry(width = 884f, height = 442f)
        for (k in 1 until ARROW_COUNT) {
            assertTrue(arrows.tipX(1, k) > arrows.tipX(1, k - 1))
            assertTrue(arrows.tipX(-1, k) < arrows.tipX(-1, k - 1))
            assertTrue(arrows.scale(k) < arrows.scale(k - 1))
        }
        val outer = arrows.tipX(1, ARROW_COUNT - 1) + ArrowsGeometry.ARM_DEPTH_UNITS * arrows.u + arrows.strokeWidth(ARROW_COUNT - 1)
        assertTrue(outer < 884f)
        assertTrue(arrows.tipX(1, 0) - arrows.strokeWidth(0) > arrows.cx + arrows.barWidth)
    }
}
