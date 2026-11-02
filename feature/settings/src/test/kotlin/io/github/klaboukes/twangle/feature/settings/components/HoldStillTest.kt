package io.github.klaboukes.twangle.feature.settings.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The still picture the look cards show with Android's "Remove animations". */
class HoldStillTest {

    @Test
    fun `a still card shows its loop STILL_SECONDS in, in small steps`() {
        val steps = mutableListOf<Float>()
        val frames = mutableListOf<Long>()
        holdStill(
            object : LookDemo {
                override fun advance(seconds: Float, frameNanos: Long) {
                    steps += seconds
                    frames += frameNanos
                }
            },
        )
        assertEquals(STILL_SECONDS, steps.sum(), 1e-4f)
        // Steps no bigger than a demo takes at once, so it moves as it would on screen.
        assertTrue(steps.all { it in 0f..MAX_STEP_SECONDS })
        // Each step is a new frame, so a preview that redraws on a new frame draws the last one.
        assertEquals(frames.sorted().distinct(), frames)
    }

}
