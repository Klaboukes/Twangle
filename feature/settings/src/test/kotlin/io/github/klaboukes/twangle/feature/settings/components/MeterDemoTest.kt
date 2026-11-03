package io.github.klaboukes.twangle.feature.settings.components

import io.github.klaboukes.twangle.core.dsp.trail.PitchHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The Meter cards' demo loop (Phase 72; 1.1's meter look cards had the same loop). */
class MeterDemoTest {

    @Test
    fun `the loop goes flat, in tune, sharp, in tune`() {
        assertTrue(meterDemoTarget(0.5f) < -10f)
        assertEquals(0f, meterDemoTarget(3.5f))
        assertTrue(meterDemoTarget(6f) > 10f)
        assertEquals(0f, meterDemoTarget(8f))
    }

    @Test
    fun `the loop never jumps, also where it starts again`() {
        val step = 0.001f
        var clock = 0f
        while (clock < METER_DEMO_SECONDS) {
            val next = (clock + step) % METER_DEMO_SECONDS
            val jump = abs(meterDemoTarget(next) - meterDemoTarget(clock))
            assertTrue("jump of $jump cents at $clock s", jump < 0.1f)
            clock += step
        }
    }

    @Test
    fun `the reading follows the loop gently and stays on the lens`() {
        val demo = MeterDemo()
        var previous = demo.cents.floatValue
        repeat(2 * 60 * METER_DEMO_SECONDS.toInt()) { frame ->
            demo.advance(1f / 60f, frame.toLong())
            val now = demo.cents.floatValue
            assertTrue("reading at $now", abs(now) <= 18f)
            assertTrue("step of ${abs(now - previous)} cents", abs(now - previous) < 1f)
            previous = now
        }
    }

    @Test
    fun `the in-tune colour settles exactly while in tune and goes off the note`() {
        val demo = MeterDemo()
        // Two and a half seconds of tuning up, then a second and a half on the note.
        repeat(4 * 60) { demo.advance(1f / 60f, it.toLong()) }
        assertEquals(1f, demo.tuned.floatValue)
        // Sharp from 5.5 s to 6.5 s.
        repeat(2 * 60 + 30) { demo.advance(1f / 60f, it.toLong()) }
        assertTrue(demo.tuned.floatValue < 0.5f)
    }

    @Test
    fun `a long pause counts as one short step`() {
        val demo = MeterDemo()
        val start = demo.cents.floatValue
        demo.advance(30f, 0L)
        assertEquals(start, demo.cents.floatValue, 1e-4f)
    }

    @Test
    fun `a still card shows the note reached and settled, the strobe standing still`() {
        val demo = MeterDemo()
        holdStill(demo)
        assertEquals("in tune, settled", 1f, demo.tuned.floatValue)
        assertTrue("within the strobe's half cent: ${demo.cents.floatValue}", abs(demo.cents.floatValue) < 0.5f)
    }

    @Test
    fun `the trail's history fills as the tuner's does, one note, the in-tune part marked`() {
        val demo = MeterDemo()
        holdStill(demo)
        val history = demo.history
        val expected = METER_DEMO_STILL_SECONDS / PitchHistory.DEFAULT_FRAME_SECONDS
        assertTrue("about one reading a frame: ${history.end} for $expected", abs(history.end - expected) <= 1f)
        var lastTime = Float.NEGATIVE_INFINITY
        var flatSeen = false
        history.forEachWithin { time, target, cents, inTune ->
            assertTrue("time goes on", time > lastTime)
            lastTime = time
            assertEquals("one note, so the line never breaks", 45, target)
            if (cents < -10f) {
                flatSeen = true
                assertFalse("flat is not in tune", inTune)
            }
        }
        assertTrue("the flat start is in view", flatSeen)
        assertTrue("the newest reading is on the note, in tune", history.inTuneAt(history.end - 1))
    }

    @Test
    fun `the demo has a reading only while the cards are on screen`() {
        val demo = MeterDemo()
        assertFalse(demo.onScreen.value)
        demo.onScreenChanged(true)
        assertTrue(demo.onScreen.value)
        demo.onScreenChanged(false)
        assertFalse(demo.onScreen.value)
    }
}
