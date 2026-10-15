package io.github.klaboukes.twangle.core.designsystem.meter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpiritLevelTest {

    /** A phone's meter box: 340 × 170 dp at about 2.6 px per dp. */
    private val level = LevelGeometry(width = 884f, height = 442f)

    @Test
    fun `in tune the bubble rests in the middle of the vial`() {
        assertEquals(level.cx, level.bubbleX(0f), 1e-3f)
    }

    @Test
    fun `the bubble slides to the side the note is off, further the further off it is`() {
        assertTrue(level.bubbleX(-10f) < level.cx)
        assertTrue(level.bubbleX(10f) > level.cx)
        assertTrue(level.bubbleX(-30f) < level.bubbleX(-10f))
        assertTrue(level.bubbleX(30f) > level.bubbleX(10f))
        assertEquals(level.cx - level.bubbleX(-25f), level.bubbleX(25f) - level.cx, 1e-3f)
        assertEquals(92f * level.u, level.bubbleX(50f) - level.cx, 1e-3f)
    }

    @Test
    fun `readings beyond the range are pinned just past its end`() {
        assertEquals(level.bubbleX(52f), level.bubbleX(80f), 0f)
        assertEquals(level.bubbleX(-52f), level.bubbleX(-300f), 0f)
        assertTrue(level.bubbleX(52f) > level.bubbleX(50f))
    }

    @Test
    fun `even stretched at the pinned end, the bubble stays inside the vial`() {
        val end = level.bubbleX(1000f) + level.bubbleHalfLength * bubbleStretch(1000f)
        assertTrue("bubble end at $end of ${level.right}", end < level.right)
        assertTrue(level.bubbleHalfHeight < level.thickness / 2f)
    }

    @Test
    fun `in tune the bubble fits between the two lines, which keep close to it`() {
        assertTrue(level.cx + level.bubbleHalfLength < level.cx + level.markOffset)
        // So a bubble a cent or two off already touches a line.
        val twoCents = level.bubbleX(2f) - level.cx
        assertTrue(level.markOffset - level.bubbleHalfLength < twoCents)
    }

    @Test
    fun `the vial spans 86 percent of the box, straight, and sits inside it`() {
        assertEquals(884f * 0.86f, level.right - level.left, 1e-2f)
        assertTrue(level.top > 6f * level.u)
        assertTrue(level.bottom < 442f - 6f * level.u)
        assertEquals(level.centreY, (level.top + level.bottom) / 2f, 1e-3f)
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
