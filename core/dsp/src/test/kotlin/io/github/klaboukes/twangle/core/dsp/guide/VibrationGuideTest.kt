package io.github.klaboukes.twangle.core.dsp.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VibrationGuideTest {

    private val guide = VibrationGuide()

    /** What [guide] plays over [seconds] of frames with [cue] at [cents] on [midi], as (time from the run's start, pulse). */
    private fun run(
        cue: GuideCue,
        cents: Float,
        seconds: Float,
        midi: Int = E2,
        edgeCents: Float = VibrationGuide.DEFAULT_EDGE_CENTS,
    ): List<Pair<Float, GuidePulse>> {
        val played = mutableListOf<Pair<Float, GuidePulse>>()
        val frames = (seconds / FRAME).toInt()
        for (i in 0 until frames) {
            val pulse = guide.update(cue, cents, midi, FRAME, edgeCents)
            if (pulse != GuidePulse.NONE) played += i * FRAME to pulse
        }
        return played
    }

    private fun gaps(played: List<Pair<Float, GuidePulse>>): List<Float> = played.zipWithNext { a, b -> b.first - a.first }

    @Test
    fun `the time between groups eases from 700 ms far off to 300 ms at the window's edge`() {
        assertEquals(0.7f, VibrationGuide.intervalSeconds(30f), 1e-6f)
        assertEquals(0.7f, VibrationGuide.intervalSeconds(-80f), 1e-6f)
        assertEquals(0.3f, VibrationGuide.intervalSeconds(5f), 1e-6f)
        assertEquals(0.3f, VibrationGuide.intervalSeconds(-2f), 1e-6f)
        assertEquals(0.5f, VibrationGuide.intervalSeconds(17.5f), 1e-6f)
        // Precise's narrower window: the quickest rate only at its own edge.
        assertEquals(0.3f, VibrationGuide.intervalSeconds(2f, edgeCents = 2f), 1e-6f)
        assertTrue(VibrationGuide.intervalSeconds(5f, edgeCents = 2f) > 0.3f)
        assertEquals(0.7f, VibrationGuide.intervalSeconds(Float.NaN), 1e-6f)
    }

    @Test
    fun `a flat string taps once per group, at once and then every interval`() {
        val played = run(GuideCue.TIGHTEN, -40f, 3f)
        assertEquals(GuidePulse.TIGHTEN, played.first().second)
        assertEquals(0f, played.first().first, 1e-6f)
        assertTrue(played.all { it.second == GuidePulse.TIGHTEN })
        // Every gap within a frame of 0.7 s.
        gaps(played).forEach { assertEquals(0.7f, it, FRAME + 1e-4f) }
        assertEquals(5, played.size)
    }

    @Test
    fun `a sharp string taps twice per group`() {
        val played = run(GuideCue.LOOSEN, 40f, 2f)
        assertTrue(played.isNotEmpty())
        assertTrue(played.all { it.second == GuidePulse.LOOSEN })
    }

    @Test
    fun `the groups come quicker as the string nears pitch`() {
        val far = gaps(run(GuideCue.TIGHTEN, -30f, 3f)).average()
        guide.reset()
        val middle = gaps(run(GuideCue.TIGHTEN, -15f, 3f)).average()
        guide.reset()
        val near = gaps(run(GuideCue.TIGHTEN, -5f, 3f)).average()
        assertTrue("$far > $middle > $near", far > middle && middle > near)
        // Never quicker than a double tap can be told apart.
        assertTrue(near >= 0.28)
    }

    @Test
    fun `nothing while no note is heard or its direction isn't settled`() {
        assertTrue(run(GuideCue.NONE, Float.NaN, 3f).isEmpty())
        assertTrue(run(GuideCue.WAIT, -20f, 3f).isEmpty())
    }

    @Test
    fun `in tune buzzes once, then stays quiet while it holds`() {
        run(GuideCue.TIGHTEN, -10f, 1f)
        val played = run(GuideCue.IN_TUNE, 0f, 5f)
        assertEquals(listOf(GuidePulse.IN_TUNE), played.map { it.second })
        // At once, even right after a tap.
        assertEquals(0f, played.single().first, 1e-6f)
    }

    @Test
    fun `leaving in tune starts the taps again, after the buzz`() {
        run(GuideCue.IN_TUNE, 0f, 0.1f)
        val played = run(GuideCue.LOOSEN, 8f, 2f)
        assertTrue(played.all { it.second == GuidePulse.LOOSEN })
        // Not before the buzz (0.375 s from its start) and a short stillness after it.
        val first = played.first().first + 0.1f
        assertTrue("first tap at $first s", first >= VibrationGuide.IN_TUNE_BUZZ_SECONDS + VibrationGuide.BUZZ_GAP_SECONDS - FRAME)
        assertTrue(first < 0.6f)
    }

    @Test
    fun `the same note in tune again right away doesn't buzz twice, a new note or a pause does`() {
        run(GuideCue.IN_TUNE, 0f, 1f)
        run(GuideCue.TIGHTEN, -8f, 0.5f)
        assertTrue(run(GuideCue.IN_TUNE, 0f, 1f).isEmpty())
        // Another string: its own buzz.
        assertEquals(listOf(GuidePulse.IN_TUNE), run(GuideCue.IN_TUNE, 0f, 1f, midi = A2).map { it.second })
        // The first string again after a pause of 2 s.
        run(GuideCue.NONE, Float.NaN, 2f)
        assertEquals(listOf(GuidePulse.IN_TUNE), run(GuideCue.IN_TUNE, 0f, 1f).map { it.second })
    }

    @Test
    fun `a new note starts afresh with a tap at once`() {
        run(GuideCue.TIGHTEN, -40f, 0.2f)
        val played = run(GuideCue.TIGHTEN, -40f, 0.5f, midi = A2)
        assertEquals(0f, played.first().first, 1e-6f)
    }

    @Test
    fun `a silence between plucks starts afresh too`() {
        run(GuideCue.TIGHTEN, -40f, 0.2f)
        run(GuideCue.NONE, Float.NaN, 0.1f)
        val played = run(GuideCue.TIGHTEN, -40f, 0.5f)
        assertEquals(0f, played.first().first, 1e-6f)
    }

    @Test
    fun `a swapped direction comes after the shortest gap`() {
        run(GuideCue.TIGHTEN, -40f, FRAME)
        val played = run(GuideCue.LOOSEN, 40f, 1f)
        assertEquals(GuidePulse.LOOSEN, played.first().second)
        assertEquals(VibrationGuide.NEAR_INTERVAL_SECONDS - FRAME, played.first().first, FRAME + 1e-4f)
    }

    @Test
    fun `a string drifting into tune taps ever quicker, then buzzes once`() {
        val played = mutableListOf<Pair<Float, GuidePulse>>()
        var t = 0f
        // From 40 cents flat to in tune over 4 s, then held 2 s.
        while (t < 6f) {
            val cents = if (t < 4f) -40f + 40f * t / 4f else 0f
            val cue = if (t < 4f && cents < -5f) GuideCue.TIGHTEN else if (t < 4f) GuideCue.WAIT else GuideCue.IN_TUNE
            val pulse = guide.update(cue, cents, E2, FRAME)
            if (pulse != GuidePulse.NONE) played += t to pulse
            t += FRAME
        }
        val taps = played.filter { it.second == GuidePulse.TIGHTEN }
        assertTrue(taps.size >= 5)
        val tapGaps = gaps(taps)
        assertTrue("gaps shrink: $tapGaps", tapGaps.first() > tapGaps.last())
        assertEquals(1, played.count { it.second == GuidePulse.IN_TUNE })
        assertEquals(GuidePulse.IN_TUNE, played.last().second)
    }

    @Test
    fun `reset forgets the note`() {
        run(GuideCue.IN_TUNE, 0f, 1f)
        guide.reset()
        assertEquals(listOf(GuidePulse.IN_TUNE), run(GuideCue.IN_TUNE, 0f, 1f).map { it.second })
    }

    private companion object {
        /** 1024 samples at 48 kHz, the tuner's hop. */
        const val FRAME = 1024f / 48_000f
        const val E2 = 40
        const val A2 = 45
    }
}
