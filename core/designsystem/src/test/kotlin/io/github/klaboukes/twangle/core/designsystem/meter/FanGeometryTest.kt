package io.github.klaboukes.twangle.core.designsystem.meter

import androidx.compose.ui.geometry.Size
import io.github.klaboukes.twangle.core.designsystem.meter.FanGeometry.Companion.END_ROOM
import io.github.klaboukes.twangle.core.designsystem.meter.FanGeometry.Companion.SIDE_ROOM
import io.github.klaboukes.twangle.core.designsystem.meter.FanGeometry.Companion.UPRIGHT_SWEEP
import io.github.klaboukes.twangle.core.designsystem.meter.FanGeometry.Companion.WIDE_SWEEP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * The Light arc's fan fitted into the lens (Phase 72; the mock-up's `lensArc`): ±1.2 rad upright and ±0.8 rad
 * wide, the largest fan that fits the box both ways, centred in its height, nothing outside the box and clear
 * of the lens's ♭ and ♯ in its bottom corners. At 2.75 px a dp.
 */
class FanGeometryTest {

    private val dp = 2.75f

    /** The Xiaomi's lens upright (353 × 300 dp), a short screen's (2:1), on its side today (2:1) and Phase 73's wide (3.4:1). */
    private val cases = listOf(
        Triple(353f, 300f, false),
        Triple(353f, 176f, false),
        Triple(380f, 190f, true),
        Triple(826f, 243f, true),
    )

    private fun fan(width: Float, height: Float, wide: Boolean) = FanGeometry(Size(width * dp, height * dp), wide, dp)

    @Test
    fun `the fan opens 1·2 rad upright and 0·8 rad wide, flat to the left and sharp to the right`() {
        for ((w, h, wide) in cases) {
            val fan = fan(w, h, wide)
            val sweep = if (wide) WIDE_SWEEP else UPRIGHT_SWEEP
            assertEquals(sweep, fan.sweep)
            assertEquals(-sweep, fan.rayAngle(0), 1e-5f)
            assertEquals(sweep, fan.rayAngle(LIGHT_ARC_RAYS - 1), 1e-5f)
            assertEquals(0f, fan.rayAngle(LIGHT_ARC_CENTRE_RAY), 1e-5f)
            // The flat end's outer light left of the middle, the sharp end's right, mirrored.
            val flat = (LIGHT_ARC_DOTS - 1) * 3
            val sharp = ((LIGHT_ARC_RAYS - 1) * LIGHT_ARC_DOTS + LIGHT_ARC_DOTS - 1) * 3
            assertTrue(fan.lights[flat] < fan.cx && fan.lights[sharp] > fan.cx)
            assertEquals(fan.cx - fan.lights[flat], fan.lights[sharp] - fan.cx, 0.01f)
        }
    }

    @Test
    fun `the fan is as large as fits, both ways, and centred in the height`() {
        for ((w, h, wide) in cases) {
            val fan = fan(w, h, wide)
            val width = w * dp
            val height = h * dp
            val case = "$w x $h wide=$wide"
            // Its top and bottom the same room from the box's edges.
            assertEquals(case, fan.top, height - fan.bottom, 0.01f)
            // It touches one of its limits: the sides' room, or the top's and bottom's.
            val sideRoom = width / 2f - (fan.radius * kotlin.math.sin(fan.sweep) + FanGeometry.OUTER_LIGHT_UNITS * fan.u)
            val endRoom = fan.top
            assertTrue("$case: side room ${sideRoom / dp} dp, end room ${endRoom / dp} dp", abs(sideRoom - SIDE_ROOM * dp) < 0.01f || abs(endRoom - END_ROOM * dp) < 0.01f)
            assertTrue(case, sideRoom >= SIDE_ROOM * dp - 0.01f && endRoom >= END_ROOM * dp - 0.01f)
            // The lights' units: the outer lights are 148 units out, as the 2:1 meter's were.
            assertEquals(case, fan.radius / FanGeometry.OUTER_UNITS, fan.u, 1e-4f)
        }
        // Upright on the Xiaomi the width decides (the fan as wide as the lens less 16 dp a side); wide, the height.
        val upright = fan(353f, 300f, false)
        assertTrue(upright.top > END_ROOM * dp + 10f * dp)
        val wide = fan(826f, 243f, true)
        assertEquals(END_ROOM * dp, wide.top, 0.01f)
    }

    @Test
    fun `every light and mark is inside the box and clear of the flat and sharp signs`() {
        for ((w, h, wide) in cases) {
            val fan = fan(w, h, wide)
            val width = w * dp
            val height = h * dp
            val case = "$w x $h wide=$wide"
            for (i in 0 until LIGHT_ARC_LIGHTS) {
                val x = fan.lights[i * 3]
                val y = fan.lights[i * 3 + 1]
                val r = fan.lights[i * 3 + 2] * 1.08f
                assertTrue("$case light $i at ($x, $y)", x - r >= 0f && x + r <= width && y - r >= 0f && y + r <= height)
                // The lens's ♭ and ♯: 18 dp signs, their middles 16 dp in from the bottom corners.
                for (signX in listOf(SIGN_INSET * dp, width - SIGN_INSET * dp)) {
                    val near = abs(x - signX) < (SIGN_HALF + 2f) * dp + r && abs(y - (height - SIGN_INSET * dp)) < (SIGN_HALF + 2f) * dp + r
                    assertTrue("$case light $i over a sign", !near)
                }
            }
            for (ray in 0 until LIGHT_ARC_RAYS) {
                if (ray == LIGHT_ARC_CENTRE_RAY) continue
                val mark = fan.markAt(ray)
                assertTrue("$case mark on ray $ray at $mark", mark.x in 0f..width && mark.y - 1.8f * fan.u >= 0f && mark.y <= height)
            }
        }
    }

    @Test
    fun `the marks sit just past the outer lights in both shapes`() {
        for ((w, h, wide) in cases) {
            val fan = fan(w, h, wide)
            for (out in 1..3) for (side in listOf(-1, 1)) {
                val ray = LIGHT_ARC_CENTRE_RAY + side * out
                val outer = ray * LIGHT_ARC_DOTS + LIGHT_ARC_DOTS - 1
                val mark = fan.markAt(ray)
                val gap = hypot(mark.x - fan.lights[outer * 3], mark.y - fan.lights[outer * 3 + 1])
                assertEquals(FanGeometry.MARK_GAP_UNITS * fan.u, gap, 0.01f)
            }
        }
    }

    @Test
    fun `the in-tune glow stands over the middle ray`() {
        for ((w, h, wide) in cases) {
            val fan = fan(w, h, wide)
            val middle = LIGHT_ARC_CENTRE_RAY * LIGHT_ARC_DOTS
            assertEquals(fan.cx, fan.glowCentre.x, 0.01f)
            // Between the middle ray's inner and outer lights.
            assertTrue(fan.glowCentre.y < fan.lights[middle * 3 + 1] && fan.glowCentre.y > fan.lights[(middle + LIGHT_ARC_DOTS - 1) * 3 + 1])
        }
    }

    private companion object {
        const val SIGN_INSET = 16f
        const val SIGN_HALF = 9f
    }
}
