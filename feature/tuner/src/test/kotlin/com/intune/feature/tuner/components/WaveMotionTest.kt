package com.intune.feature.tuner.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WaveMotionTest {

    /** Steps [motion] at 60 frames per second for [seconds]. */
    private fun run(motion: WaveMotion, seconds: Float, cents: Float, inTune: Boolean = false) {
        repeat((seconds * 60).toInt()) { motion.advance(1f / 60f, cents, inTune) }
    }

    @Test
    fun `a sharp string is squeezed and runs ahead`() {
        val motion = WaveMotion()
        run(motion, 0.1f, 20f)
        assertTrue("ratio ${motion.ratio}", motion.ratio > 1f)
        assertTrue("drift ${motion.drift}", motion.drift > 0f)
    }

    @Test
    fun `a flat string is stretched and falls behind`() {
        val motion = WaveMotion()
        run(motion, 0.1f, -20f)
        assertTrue("ratio ${motion.ratio}", motion.ratio < 1f)
        assertTrue("drift ${motion.drift}", motion.drift < 0f)
    }

    @Test
    fun `the shape follows the reading`() {
        val motion = WaveMotion()
        run(motion, 2f, -20f)
        assertEquals(1f - 20f / WaveMotion.WAVE_RANGE_CENTS * WaveMotion.MAX_STRETCH, motion.ratio, 0.001f)
    }

    @Test
    fun `further off drifts faster`() {
        val near = WaveMotion().also { run(it, 2f, 4f) }
        val far = WaveMotion().also { run(it, 2f, 25f) }
        val nearBefore = near.drift
        val farBefore = far.drift
        near.advance(0.05f, 4f, inTune = false)
        far.advance(0.05f, 25f, inTune = false)
        assertTrue(abs(far.drift - farBefore) > abs(near.drift - nearBefore))
    }

    @Test
    fun `in tune the played wave settles onto the target`() {
        val motion = WaveMotion()
        run(motion, 1f, 30f)
        run(motion, 2f, 3f, inTune = true)
        assertEquals(1f, motion.ratio, 0.001f)
        assertEquals(0f, motion.drift, 0.01f)
        assertEquals(motion.targetPhase, motion.playedPhase, 0.01f)
    }

    @Test
    fun `far off looks like the edge of the range`() {
        val far = WaveMotion().also { run(it, 3f, 300f) }
        val edge = WaveMotion().also { run(it, 3f, WaveMotion.WAVE_RANGE_CENTS) }
        assertEquals(edge.ratio, far.ratio, 0.0001f)
    }

    @Test
    fun `phases stay small however long it runs`() {
        val motion = WaveMotion()
        run(motion, 120f, 40f)
        assertTrue(motion.targetPhase in 0f..1f)
        assertTrue(motion.drift in -0.5f..0.5f)
    }

    @Test
    fun `a long pause counts as one short step`() {
        val paused = WaveMotion().apply { advance(30f, 20f, inTune = false) }
        val step = WaveMotion().apply { advance(0.1f, 20f, inTune = false) }
        assertEquals(step.ratio, paused.ratio, 0.0001f)
        assertEquals(step.drift, paused.drift, 0.0001f)
    }

    @Test
    fun `advice in words`() {
        assertEquals(WaveAdvice.IN_TUNE, waveAdvice(4f, inTune = true))
        assertEquals(WaveAdvice.ALMOST, waveAdvice(-4.9f, inTune = false))
        assertEquals(WaveAdvice.ALMOST, waveAdvice(3f, inTune = false))
        assertEquals(WaveAdvice.LITTLE_LOW, waveAdvice(-12f, inTune = false))
        assertEquals(WaveAdvice.LOW, waveAdvice(-20f, inTune = false))
        assertEquals(WaveAdvice.LOW, waveAdvice(-300f, inTune = false))
        assertEquals(WaveAdvice.LITTLE_HIGH, waveAdvice(5f, inTune = false))
        assertEquals(WaveAdvice.HIGH, waveAdvice(48f, inTune = false))
    }
}
