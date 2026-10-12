package io.github.klaboukes.twangle.core.designsystem.meter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpiritLevelTest {

    /** A phone's meter box: 340 × 170 dp at about 2.6 px per dp. */
    private val level = LevelGeometry(width = 884f, height = 442f)

    /** The x of the bubble's middle at [cents]. */
    private fun bubbleX(cents: Float): Float = level.x(level.angle(cents), level.bubbleDistance)

    @Test
    fun `in tune the bubble rests in the middle, at the top of the tube`() {
        assertEquals(level.cx, bubbleX(0f), 1e-3f)
        assertEquals(level.bubbleRestY, level.y(level.angle(0f), level.bubbleDistance), 1e-3f)
    }

    @Test
    fun `the bubble floats to the side the note is off, further the further off it is`() {
        assertTrue(bubbleX(-10f) < level.cx)
        assertTrue(bubbleX(10f) > level.cx)
        assertTrue(bubbleX(-30f) < bubbleX(-10f))
        assertTrue(bubbleX(30f) > bubbleX(10f))
        assertEquals(level.cx - bubbleX(-25f), bubbleX(25f) - level.cx, 1e-3f)
    }

    @Test
    fun `fifty cents lands at 82 percent of the tube's half angle`() {
        assertEquals(level.maxAngle * 0.82f, level.angle(50f), 1e-6f)
        assertEquals(-level.maxAngle * 0.82f, level.angle(-50f), 1e-6f)
    }

    @Test
    fun `readings beyond the scale are pinned just past its end`() {
        assertEquals(level.angle(52f), level.angle(80f), 0f)
        assertEquals(level.angle(-52f), level.angle(-300f), 0f)
        assertTrue(level.angle(52f) > level.angle(50f))
    }

    @Test
    fun `even stretched at the pinned end, the bubble stays inside the tube`() {
        val stretched = level.bubbleHalfLength * bubbleStretch(1000f)
        val end = level.angle(1000f) + stretched / level.bubbleDistance
        assertTrue("bubble end at $end of ${level.maxAngle}", end < level.maxAngle)
        // And its top stays under the tube's outer edge.
        assertTrue(level.bubbleDistance + level.bubbleHalfHeight < level.radius + level.thickness / 2f)
    }

    @Test
    fun `in tune the bubble fits between the two marks`() {
        val markX = level.x(level.angle(LevelGeometry.LEVEL_MARK_CENTS), level.bubbleDistance)
        assertTrue(level.cx + level.bubbleHalfLength < markX)
        assertTrue(level.cx - level.bubbleHalfLength > 2f * level.cx - markX)
    }

    @Test
    fun `the tube spans 76 percent of the box and sits inside it`() {
        val half = level.radius * kotlin.math.sin(level.maxAngle)
        assertEquals(884f * 0.38f, half, 1e-2f)
        assertTrue(level.top - level.thickness / 2f > 6f * level.u)
    }

    @Test
    fun `a moving bubble stretches a little, up to a quarter, and is round at rest`() {
        assertEquals(1f, bubbleStretch(0f), 0f)
        assertEquals(1.125f, bubbleStretch(50f), 1e-6f)
        assertEquals(1.125f, bubbleStretch(-50f), 1e-6f)
        assertEquals(1.25f, bubbleStretch(100f), 1e-6f)
        assertEquals(1.25f, bubbleStretch(-5000f), 1e-6f)
    }
}
