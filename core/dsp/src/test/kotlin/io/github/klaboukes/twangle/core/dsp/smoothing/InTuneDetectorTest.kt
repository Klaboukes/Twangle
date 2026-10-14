package io.github.klaboukes.twangle.core.dsp.smoothing

import io.github.klaboukes.twangle.core.dsp.allocatedBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InTuneDetectorTest {

    /** One hop at 48 kHz, as on the phone. */
    private val frame = 1024f / 48_000f

    private var detector = InTuneDetector()

    /** Feeds [cents] for [seconds] worth of frames and returns the last result. */
    private fun hold(cents: Float, seconds: Float): Boolean {
        var result = false
        repeat((seconds / frame).toInt()) { result = detector.update(cents, frame) }
        return result
    }

    @Test
    fun `needs 300 ms within 5 cents`() {
        repeat(14) { assertFalse(detector.update(4f, frame)) } // 14 frames ≈ 299 ms
        assertTrue(detector.update(4f, frame))
    }

    @Test
    fun `exactly the hold time counts`() {
        val exact = InTuneDetector(holdSeconds = 10 * frame)
        repeat(9) { assertFalse(exact.update(0f, frame)) }
        assertTrue(exact.update(0f, frame))
    }

    @Test
    fun `leaving the window before 300 ms starts the wait over`() {
        hold(-3f, 0.25f)
        detector.update(6f, frame)
        assertFalse(hold(-3f, 0.25f))
        assertTrue(hold(-3f, 0.1f))
    }

    @Test
    fun `hovering between 5 and 7 cents stays in tune once reached`() {
        hold(0f, 0.4f)
        assertTrue(hold(6.5f, 1f))
        assertTrue(hold(-6.9f, 1f))
    }

    @Test
    fun `drifting past 7 cents ends it, and it has to be earned again`() {
        hold(0f, 0.4f)
        assertFalse(detector.update(7.5f, frame))
        assertFalse(hold(2f, 0.2f))
        assertTrue(hold(2f, 0.15f))
    }

    @Test
    fun `reset clears the state`() {
        hold(0f, 0.4f)
        detector.reset()
        assertFalse(detector.inTune)
        assertFalse(detector.update(0f, frame))
    }

    @Test
    fun `the default window is 5 cents in, 7 out, 300 ms`() {
        val default = InTuneDetector()
        assertEquals(5f, default.enterCents)
        assertEquals(7f, default.exitCents)
        assertEquals(0.3f, default.holdSeconds)
    }

    @Test
    fun `allocates nothing per frame`() {
        val bytes = allocatedBytes { repeat(1_000) { detector.update(if (it % 50 < 25) 1f else 9f, frame) } }
        assertEquals(0L, bytes)
    }
}
