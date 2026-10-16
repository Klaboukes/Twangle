package io.github.klaboukes.twangle.core.dsp.intonation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SettledPitchTest {

    /** 0.1 s frames keep the numbers easy: settle 3 frames, collect 10, a gap of 2 frames is allowed. */
    private fun settled() = SettledPitch(frameSeconds = 0.1f, settleSeconds = 0.25f, collectSeconds = 0.95f, steadyCents = 20f, gapSeconds = 0.15f)

    private fun SettledPitch.feed(vararg midi: Float): Boolean {
        var done = false
        for (m in midi) done = update(m)
        return done
    }

    @Test
    fun `a steady note settles, then a second of readings gives their median`() {
        val pitch = settled()
        assertEquals(10, pitch.collectFrames)
        // The first reading and 3 steady ones settle the note; nothing is kept yet.
        assertFalse(pitch.feed(45f, 45f, 45f, 45f))
        assertEquals(0f, pitch.progress, 0f)
        val kept = floatArrayOf(45.02f, 44.98f, 45.01f, 45.10f, 44.99f, 45.00f, 45.03f, 44.97f, 45.02f)
        assertFalse(pitch.feed(*kept))
        assertEquals(0.9f, pitch.progress, 1e-6f)
        assertTrue(pitch.feed(45.01f))
        assertEquals(1f, pitch.progress, 0f)
        // Sorted: 44.97 44.98 44.99 45.00 45.01 45.01 45.02 45.02 45.03 45.10: the middle two.
        assertEquals(45.01f, pitch.midi, 1e-5f)
    }

    @Test
    fun `the attack's glide doesn't count`() {
        val pitch = settled()
        // Starting 40 cents sharp and gliding down in 25-cent steps: steady only from 45.0 on.
        pitch.feed(45.9f, 45.65f, 45.4f)
        pitch.feed(45.15f, 45f, 45f, 45f)
        assertEquals(0f, pitch.progress, 0f)
        repeat(10) { pitch.update(45f) }
        assertTrue(pitch.done)
        assertEquals(45f, pitch.midi, 0f)
    }

    @Test
    fun `another note starts over`() {
        val pitch = settled()
        pitch.feed(45f, 45f, 45f, 45f, 45f, 45f, 45f)
        assertTrue(pitch.progress > 0f)
        pitch.update(57f)
        assertEquals(0f, pitch.progress, 0f)
        repeat(3) { pitch.update(57f) }
        repeat(10) { pitch.update(57.05f) }
        assertTrue(pitch.done)
        assertEquals(57.05f, pitch.midi, 1e-5f)
    }

    @Test
    fun `a short dropout is bridged, a longer one starts over`() {
        val pitch = settled()
        pitch.feed(45f, 45f, 45f, 45f, 45f, 45f)
        val before = pitch.progress
        pitch.feed(Float.NaN, Float.NaN)
        assertEquals(before, pitch.progress, 0f)
        pitch.update(45f)
        assertTrue(pitch.progress > before)
        pitch.feed(Float.NaN, Float.NaN, Float.NaN)
        assertEquals(0f, pitch.progress, 0f)
    }

    @Test
    fun `done stays until reset`() {
        val pitch = settled()
        repeat(14) { pitch.update(50f) }
        assertTrue(pitch.done)
        assertTrue(pitch.update(60f))
        assertTrue(pitch.update(Float.NaN))
        assertEquals(50f, pitch.midi, 0f)
        pitch.reset()
        assertFalse(pitch.done)
        assertTrue(pitch.midi.isNaN())
        assertEquals(0f, pitch.progress, 0f)
    }

    @Test
    fun `at the tuner's frame rate a second is about 47 readings`() {
        val pitch = SettledPitch(frameSeconds = 1024f / 48_000f)
        assertEquals(47, pitch.collectFrames)
    }

    @Test
    fun `bad settings are refused`() {
        assertThrows(IllegalArgumentException::class.java) { SettledPitch(0f) }
        assertThrows(IllegalArgumentException::class.java) { SettledPitch(0.02f, collectSeconds = 0f) }
        assertThrows(IllegalArgumentException::class.java) { SettledPitch(0.02f, steadyCents = 0f) }
        assertThrows(IllegalArgumentException::class.java) { SettledPitch(0.02f, settleSeconds = -1f) }
    }
}
