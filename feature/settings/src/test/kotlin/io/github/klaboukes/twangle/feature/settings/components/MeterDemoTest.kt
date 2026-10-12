package io.github.klaboukes.twangle.feature.settings.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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
    fun `the marker follows the loop gently and stays on the meter`() {
        val demo = MeterDemo()
        var previous = demo.cents.floatValue
        repeat(2 * 60 * METER_DEMO_SECONDS.toInt()) { frame ->
            demo.advance(1f / 60f, frame.toLong())
            val now = demo.cents.floatValue
            assertTrue("marker at $now", abs(now) <= 18f)
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
}
