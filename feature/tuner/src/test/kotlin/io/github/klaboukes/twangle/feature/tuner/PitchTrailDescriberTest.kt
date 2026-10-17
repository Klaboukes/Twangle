package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.dsp.trail.PitchHistory
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class PitchTrailDescriberTest {

    private val hop = 1024f / 48_000f

    /** A history of [seconds] of readings, one per hop: [cents] (NaN for a gap) at each time, around A2 (MIDI 45). */
    private fun history(seconds: Float, inTuneWithin: Float = 5f, cents: (Float) -> Float): PitchHistory {
        val history = PitchHistory()
        var t = 0f
        while (t < seconds) {
            t += hop
            val c = cents(t)
            if (c.isNaN()) history.pushGap(t) else push(history, t, c, inTuneWithin)
        }
        return history
    }

    /** A reading [cents] off A2, folded onto the nearest note as the chromatic tuner does. */
    private fun push(history: PitchHistory, t: Float, cents: Float, inTuneWithin: Float) {
        var target = 45
        var off = cents
        while (off > 50f) { off -= 100f; target++ }
        while (off <= -50f) { off += 100f; target-- }
        history.push(t, target, off, abs(off) < inTuneWithin)
    }

    private fun describe(history: PitchHistory) = PitchTrailDescriber.describe(history)

    /** A tiny wobble, like a real held string read by the smoothed tuner. */
    private fun wobble(t: Float) = 0.8f * sin(2f * PI.toFloat() * 1.7f * t)

    @Test
    fun `a held note in tune is steady and in tune`() {
        assertEquals(TrailDescription(TrailMotion.STEADY, inTune = true), describe(history(3f) { 1f + wobble(it) }))
    }

    @Test
    fun `a held note off the note is steady, so many cents sharp or flat`() {
        assertEquals(TrailDescription(TrailMotion.STEADY, cents = 10), describe(history(3f) { 11f + wobble(it) }))
        assertEquals(TrailDescription(TrailMotion.STEADY, cents = -20), describe(history(3f) { -19f + wobble(it) }))
        // Near the note the cents are whole.
        assertEquals(TrailDescription(TrailMotion.STEADY, cents = 3), describe(history(3f, inTuneWithin = 2f) { 3f + wobble(it) }))
    }

    @Test
    fun `vibrato wavers round its middle`() {
        // 5 Hz, ±15 cents, round 10 cents sharp (not in tune most of the time).
        val vibrato = describe(history(3f, inTuneWithin = 2f) { 10f + 15f * sin(2f * PI.toFloat() * 5f * it) })
        assertEquals(TrailMotion.WAVERING, vibrato.motion)
        assertEquals(10, vibrato.cents)
        // Round the note: most readings are off it, so it says where the middle is (0: in tune).
        assertEquals(TrailDescription(TrailMotion.WAVERING, inTune = true), describe(history(3f) { 15f * sin(2f * PI.toFloat() * 5f * it) }))
    }

    @Test
    fun `a bend up and a bend down`() {
        // 40 cents up over the last 1.5 s.
        assertEquals(TrailDescription(TrailMotion.BENDING_UP), describe(history(3f) { if (it < 1.5f) 0f else (it - 1.5f) * 27f + wobble(it) }))
        assertEquals(TrailDescription(TrailMotion.BENDING_DOWN), describe(history(3f) { if (it < 1.5f) 0f else -(it - 1.5f) * 27f + wobble(it) }))
    }

    @Test
    fun `a bend that crosses into the next note is one bend`() {
        // From 30 cents sharp of A2 to 30 cents flat of A#2's neighbour: the target changes on the way.
        assertEquals(TrailDescription(TrailMotion.BENDING_UP), describe(history(3f) { if (it < 1.5f) 30f else 30f + (it - 1.5f) * 60f }))
    }

    @Test
    fun `a slow small drift is not a bend`() {
        // 6 cents over the window: steady.
        assertEquals(TrailMotion.STEADY, describe(history(3f) { 20f + it * 4f }).motion)
    }

    @Test
    fun `no note without readings, with only a moment of one, or after it stopped`() {
        assertEquals(TrailDescription.NO_NOTE, describe(PitchHistory()))
        assertEquals(TrailDescription.NO_NOTE, describe(history(3f) { Float.NaN }))
        // 0.1 s of a note at the end: too short.
        assertEquals(TrailDescription.NO_NOTE, describe(history(3f) { if (it > 2.9f) 0f else Float.NaN }))
        // A note that stopped half a second ago.
        assertEquals(TrailDescription.NO_NOTE, describe(history(3f) { if (it < 2.5f) 0f else Float.NaN }))
        // A short dropout (0.1 s) just now is still the note.
        assertEquals(TrailMotion.STEADY, describe(history(3f) { if (it > 2.9f) Float.NaN else 0f }).motion)
    }

    @Test
    fun `describing allocates nothing`() {
        val history = history(5f) { 10f * sin(it * 3f) }
        repeat(1_000) { PitchTrailDescriber.describe(history) }
        val memory = java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val thread = Thread.currentThread().id
        val before = memory.getThreadAllocatedBytes(thread)
        var steady = 0
        repeat(1_000) { if (PitchTrailDescriber.describe(history).motion == TrailMotion.STEADY) steady++ }
        val allocated = memory.getThreadAllocatedBytes(thread) - before
        // Only the description itself (a few dozen bytes each); nothing per entry walked.
        assertEquals("allocated $allocated bytes", true, allocated < 1_000 * 64L + 4_096L)
    }

    @Test
    fun `cents are said in whole cents near the note and in fives further off`() {
        assertEquals(3, PitchTrailDescriber.shownCents(3.4f))
        assertEquals(-4, PitchTrailDescriber.shownCents(-4.4f))
        assertEquals(10, PitchTrailDescriber.shownCents(11.9f))
        assertEquals(15, PitchTrailDescriber.shownCents(13f))
        assertEquals(-25, PitchTrailDescriber.shownCents(-24f))
    }

    // ---- Settling ----

    @Test
    fun `a new description is said once it came twice in a row`() {
        val settled = SettledTrailDescription()
        val steady = TrailDescription(TrailMotion.STEADY, inTune = true)
        val bending = TrailDescription(TrailMotion.BENDING_UP)
        assertEquals(TrailDescription.NO_NOTE, settled.offer(steady))
        assertEquals(steady, settled.offer(steady))
        // One odd moment changes nothing.
        assertEquals(steady, settled.offer(bending))
        assertEquals(steady, settled.offer(steady))
        assertEquals(steady, settled.offer(bending))
        assertEquals(bending, settled.offer(bending))
        assertEquals(bending, settled.shown)
        // Two different ones in turn never settle.
        assertEquals(bending, settled.offer(steady))
        assertEquals(bending, settled.offer(TrailDescription.NO_NOTE))
        assertEquals(bending, settled.offer(steady))
    }
}
