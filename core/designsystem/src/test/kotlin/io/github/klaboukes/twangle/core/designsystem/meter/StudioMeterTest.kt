package io.github.klaboukes.twangle.core.designsystem.meter

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioMeterTest {

    /** A phone's meter box: 340 × 170 dp at about 2.6 px per dp. */
    private val studio = StudioGeometry(width = 884f, height = 442f)
    private val u = studio.u

    @Test
    fun `the face is 18u in from every side and the pivot lies below it, under the box`() {
        assertEquals(18f * u, studio.left, 1e-3f)
        assertEquals(884f - 18f * u, studio.right, 1e-3f)
        assertEquals(442f - 18f * u, studio.bottom, 1e-3f)
        assertTrue(studio.pivotY > 442f)
        assertEquals(studio.pivotY / 442f, studioPivotFraction(884f, 442f), 1e-5f)
        assertEquals(1f, studioPivotFraction(884f, 0f), 0f)
    }

    @Test
    fun `in tune the needle stands straight up and the scale's middle is 20u below the face's top`() {
        assertEquals(0f, studioNeedleRadians(0f), 0f)
        assertEquals(studio.cx, studio.x(studio.radius, 0f), 1e-3f)
        assertEquals(studio.top + 20f * u, studio.y(studio.radius, 0f), 1e-3f)
    }

    @Test
    fun `the needle turns to the side the note is off, 0_78 radians at 50 cents`() {
        assertTrue(studioNeedleRadians(-10f) < 0f)
        assertTrue(studioNeedleRadians(10f) > 0f)
        assertEquals(StudioGeometry.SWEEP, studioNeedleRadians(50f), 1e-6f)
        assertEquals(-StudioGeometry.SWEEP, studioNeedleRadians(-50f), 1e-6f)
        assertEquals(studio.angle(25f), studioNeedleRadians(25f), 1e-6f)
    }

    @Test
    fun `readings beyond the scale are pinned just past its end`() {
        assertEquals(studioNeedleRadians(55f), studioNeedleRadians(90f), 0f)
        assertEquals(studioNeedleRadians(-55f), studioNeedleRadians(-400f), 0f)
        assertTrue(studioNeedleRadians(55f) > studioNeedleRadians(50f))
    }

    @Test
    fun `the whole scale, numbers and needle tip stay on the face`() {
        for (cents in listOf(-55f, -50f, -25f, 0f, 25f, 50f, 55f)) {
            // The arc and the numbers 20u inside it.
            for (distance in listOf(studio.radius, studio.radius - 23f * u, studio.radius + StudioGeometry.NEEDLE_OVERHANG * u)) {
                val x = studio.x(distance, cents)
                val y = studio.y(distance, cents)
                assertTrue("$cents at $distance: x $x", x > studio.left + 8f * u && x < studio.right - 8f * u)
                assertTrue("$cents at $distance: y $y", y > studio.top && y < studio.bottom)
            }
        }
    }

    @Test
    fun `the lamp sits in the face's top right corner, clear of the scale`() {
        assertEquals(studio.right - 15f * u, studio.lampX, 1e-3f)
        assertEquals(studio.top + 15f * u, studio.lampY, 1e-3f)
        // Farther from the pivot than the needle reaches, so the needle never covers it.
        val dx = studio.lampX - studio.cx
        val dy = studio.lampY - studio.pivotY
        assertTrue(kotlin.math.sqrt(dx * dx + dy * dy) > studio.radius + StudioGeometry.NEEDLE_OVERHANG * u + 5.5f * u)
    }

    @Test
    fun `the lamp lights in the in-tune colour with the in-tune amount`() {
        val unlit = Color(0xFF404040)
        val inTune = Color(0xFF20C040)
        assertEquals(unlit, studioLampColor(unlit, inTune, 0f))
        assertEquals(inTune, studioLampColor(unlit, inTune, 1f))
        val half = studioLampColor(unlit, inTune, 0.5f)
        assertEquals((unlit.green + inTune.green) / 2f, half.green, 1e-3f)
        assertEquals(inTune, studioLampColor(unlit, inTune, 1.4f))
        assertEquals(0.25f, studioLampGlint(0f), 1e-6f)
        assertEquals(0.7f, studioLampGlint(1f), 1e-6f)
        assertTrue(studioLampGlint(0.5f) > studioLampGlint(0.2f))
    }
}
