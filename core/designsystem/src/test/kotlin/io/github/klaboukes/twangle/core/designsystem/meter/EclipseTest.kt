package io.github.klaboukes.twangle.core.designsystem.meter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EclipseTest {

    /** A phone's meter box: 340 × 170 dp at about 2.6 px per dp. */
    private val eclipse = EclipseGeometry(width = 884f, height = 442f)

    @Test
    fun `in tune the moon sits on the sun and covers it whole`() {
        assertEquals(eclipse.cx, eclipse.moonX(0f), 1e-3f)
        assertTrue(eclipse.moonRadius > eclipse.sunRadius)
        assertEquals(1f, eclipse.covered(0f), 0f)
    }

    @Test
    fun `the moon slides to the side the note is off and uncovers the sun as it goes`() {
        assertTrue(eclipse.moonX(-10f) < eclipse.cx)
        assertTrue(eclipse.moonX(10f) > eclipse.cx)
        assertEquals(eclipse.cx - eclipse.moonX(-20f), eclipse.moonX(20f) - eclipse.cx, 1e-3f)
        assertTrue(eclipse.covered(5f) < 1f)
        assertTrue(eclipse.covered(15f) < eclipse.covered(5f))
        // A few cents off already shows a sliver of sun.
        assertTrue(eclipse.covered(2f) < 0.97f)
        // Far off they no longer touch.
        assertEquals(0f, eclipse.covered(45f), 0f)
    }

    @Test
    fun `readings beyond the range are pinned, and the moon stays in the box`() {
        assertEquals(eclipse.moonX(52f), eclipse.moonX(300f), 0f)
        assertEquals(eclipse.moonX(-52f), eclipse.moonX(-300f), 0f)
        assertTrue(eclipse.moonX(300f) + eclipse.moonRadius < 884f)
        assertTrue(eclipse.moonX(-300f) - eclipse.moonRadius > 0f)
        assertTrue(eclipse.centreY - eclipse.moonRadius > 0f)
    }
}
