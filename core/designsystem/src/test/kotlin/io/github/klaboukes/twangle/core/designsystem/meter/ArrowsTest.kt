package io.github.klaboukes.twangle.core.designsystem.meter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrowsTest {

    @Test
    fun `in tune and just off, no arrow is lit`() {
        for (side in listOf(-1, 1)) for (k in 0 until ARROW_COUNT) {
            assertEquals(0f, arrowLight(side, k, 0f), 0f)
            assertEquals(0f, arrowLight(side, k, ARROWS_DEAD_CENTS * side), 0f)
        }
    }

    @Test
    fun `only the side the note is off lights, the inner arrow first`() {
        for (k in 0 until ARROW_COUNT) assertEquals(0f, arrowLight(1, k, -20f), 0f)
        assertTrue(arrowLight(-1, 0, -20f) > 0f)
        assertEquals(1f, arrowLight(-1, 0, -20f), 0f)
        assertTrue(arrowLight(-1, 1, -20f) in 0.01f..0.99f)
        assertEquals(0f, arrowLight(-1, 2, -20f), 0f)
    }

    @Test
    fun `more arrows light the further off the note is`() {
        val lit = { cents: Float -> (0 until ARROW_COUNT).sumOf { arrowLight(1, it, cents).toDouble() } }
        assertTrue(lit(5f) < lit(15f))
        assertTrue(lit(15f) < lit(30f))
        assertTrue(lit(30f) < lit(45f))
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
