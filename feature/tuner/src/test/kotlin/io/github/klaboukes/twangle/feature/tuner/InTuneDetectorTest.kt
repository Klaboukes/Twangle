package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.settings.InTunePrecision
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
    fun `relaxed is the default window`() {
        val relaxed = InTuneDetector.forPrecision(InTunePrecision.RELAXED)
        assertEquals(5f, relaxed.enterCents)
        assertEquals(7f, relaxed.exitCents)
        assertEquals(0.3f, relaxed.holdSeconds)
        assertEquals(InTunePrecision.RELAXED, InTunePrecision.DEFAULT)
    }

    @Test
    fun `relaxed counts 4 cents as in tune`() {
        detector = InTuneDetector.forPrecision(InTunePrecision.RELAXED)
        assertTrue(hold(4f, 0.4f))
        assertTrue(hold(-4.9f, 1f))
    }

    @Test
    fun `precise needs 300 ms within 2 cents`() {
        detector = InTuneDetector.forPrecision(InTunePrecision.PRECISE)
        repeat(14) { assertFalse(detector.update(-1.9f, frame)) }
        assertTrue(detector.update(-1.9f, frame))
    }

    @Test
    fun `precise never counts 4 cents as in tune`() {
        detector = InTuneDetector.forPrecision(InTunePrecision.PRECISE)
        assertFalse(hold(4f, 2f))
        assertFalse(hold(-2.1f, 2f))
    }

    @Test
    fun `precise holds between 2 and 3 cents once reached`() {
        detector = InTuneDetector.forPrecision(InTunePrecision.PRECISE)
        hold(0f, 0.4f)
        assertTrue(hold(2.5f, 1f))
        assertTrue(hold(-3f, 1f))
    }

    @Test
    fun `precise drifting past 3 cents ends it, and it has to be earned again`() {
        detector = InTuneDetector.forPrecision(InTunePrecision.PRECISE)
        hold(0f, 0.4f)
        assertFalse(detector.update(3.2f, frame))
        assertFalse(hold(1f, 0.2f))
        assertTrue(hold(1f, 0.15f))
    }
}
