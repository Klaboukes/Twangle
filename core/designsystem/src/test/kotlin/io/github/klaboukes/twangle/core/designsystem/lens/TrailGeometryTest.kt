package io.github.klaboukes.twangle.core.designsystem.lens

import io.github.klaboukes.twangle.core.designsystem.lens.TrailGeometry.Companion.FALL_PER_SECOND
import io.github.klaboukes.twangle.core.designsystem.lens.TrailGeometry.Companion.NOW_FROM_TOP
import io.github.klaboukes.twangle.core.designsystem.lens.TrailGeometry.Companion.SCALE_INSET
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The trail look's turned axes (Phase 71): cents across, time falling down, in a lens 350 × 298 dp at 2.75 px a dp. */
class TrailGeometryTest {

    private val dp = 2.75f
    private val geometry = TrailGeometry(width = 350f * dp, height = 298f * dp, dp = dp)

    @Test
    fun `flat is left, sharp is right, the note in the middle`() {
        assertEquals(geometry.width / 2f, geometry.xOf(0f), 1e-3f)
        assertTrue(geometry.xOf(-10f) < geometry.xOf(-5f))
        assertTrue(geometry.xOf(-5f) < geometry.xOf(0f))
        assertTrue(geometry.xOf(5f) > geometry.xOf(0f))
        // Mirrored about the middle.
        assertEquals(geometry.width - geometry.xOf(-23f), geometry.xOf(23f), 1e-3f)
    }

    @Test
    fun `±50 cents reach the scale's ends, 24 dp in, and readings further off stay there`() {
        assertEquals(SCALE_INSET * dp, geometry.xOf(-50f), 1e-3f)
        assertEquals(geometry.width - SCALE_INSET * dp, geometry.xOf(50f), 1e-3f)
        assertEquals(geometry.xOf(50f), geometry.xOf(120f), 1e-3f)
        assertEquals(geometry.xOf(-50f), geometry.xOf(-300f), 1e-3f)
    }

    @Test
    fun `the scale is stretched in the middle`() {
        // 5 cents sits where 11 would on a plain scale, 10 where 19 would (k = 1.5).
        val perCentPlain = geometry.half / 50f
        assertEquals(5f * 2.5f / 1.15f * perCentPlain, geometry.xOf(5f) - geometry.centreX, 0.01f)
        assertEquals(10f * 2.5f / 1.3f * perCentPlain, geometry.xOf(10f) - geometry.centreX, 0.01f)
        // A cent near the note moves the line further than one near the ends.
        assertTrue(geometry.xOf(1f) - geometry.xOf(0f) > geometry.xOf(50f) - geometry.xOf(49f))
    }

    @Test
    fun `the in-tune band is the tuner's window on the same scale`() {
        assertEquals(geometry.xOf(5f) - geometry.centreX, geometry.zoneHalfWidth(5f), 1e-3f)
        assertEquals(geometry.xOf(2f) - geometry.centreX, geometry.zoneHalfWidth(2f), 1e-3f)
        assertTrue(geometry.zoneHalfWidth(2f) < geometry.zoneHalfWidth(5f))
    }

    @Test
    fun `the newest reading is near the top and the older ones fall 80 dp a second`() {
        assertEquals(NOW_FROM_TOP * dp, geometry.yOf(0f), 1e-3f)
        assertEquals((NOW_FROM_TOP + FALL_PER_SECOND) * dp, geometry.yOf(1f), 1e-3f)
        assertTrue(geometry.yOf(0.5f) > geometry.yOf(0.25f))
        // The box shows the seconds that fit under the newest reading: 270 dp at 80 a second.
        assertEquals(270f / 80f, geometry.spanSeconds, 1e-3f)
        assertEquals(geometry.height, geometry.yOf(geometry.spanSeconds), 1e-2f)
    }

    @Test
    fun `a short lens shows less of the past`() {
        val wide = TrailGeometry(width = 800f * dp, height = 72f * dp, dp = dp)
        assertEquals(44f / 80f, wide.spanSeconds, 1e-3f)
        assertTrue(wide.spanSeconds < geometry.spanSeconds)
    }

    @Test
    fun `ticks every 10 cents, the ends and the middle longer`() {
        assertEquals(12f * dp, geometry.tickLength(0), 0f)
        assertEquals(12f * dp, geometry.tickLength(50), 0f)
        assertEquals(12f * dp, geometry.tickLength(-50), 0f)
        assertEquals(5f * dp, geometry.tickLength(10), 0f)
        assertEquals(5f * dp, geometry.tickLength(-40), 0f)
    }

    @Test
    fun `the line fades toward the bottom`() {
        assertEquals(1f, geometry.fadeAt(geometry.nowY), 1e-4f)
        assertEquals(1f, geometry.fadeAt(0f), 1e-4f)
        val span = geometry.height - geometry.nowY
        assertEquals(0.28f, geometry.fadeAt(geometry.nowY + 0.7f * span), 1e-4f)
        assertEquals(0f, geometry.fadeAt(geometry.height), 1e-4f)
        assertTrue(geometry.fadeAt(geometry.nowY + 0.2f * span) > geometry.fadeAt(geometry.nowY + 0.5f * span))
    }
}
