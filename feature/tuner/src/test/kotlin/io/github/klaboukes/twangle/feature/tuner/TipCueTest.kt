package io.github.klaboukes.twangle.feature.tuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TipCueTest {

    /** One hop at 48 kHz, as on the phone. */
    private val frame = 1024f / 48_000f
    private val cue = TipCue()

    /** A pitched frame of a note heard [snrDb] above a quiet room. */
    private fun note(levelDbfs: Float, snrDb: Float = 30f, peakDbfs: Float = levelDbfs + 3f) = PitchReading(
        frequencyHz = 110f, confidence = 0.95f, levelDbfs = levelDbfs, gateOpen = true,
        noiseFloorDbfs = -60f, snrDb = snrDb, clarity = 1f, peakDbfs = peakDbfs,
    )

    /** A frame between notes in a room whose background is at [floorDbfs]. */
    private fun room(floorDbfs: Float, peakDbfs: Float = floorDbfs + 3f) = PitchReading(
        frequencyHz = 0f, confidence = 0f, levelDbfs = floorDbfs, gateOpen = false,
        noiseFloorDbfs = floorDbfs, peakDbfs = peakDbfs,
    )

    /** A plucked note: starts at [startDbfs] and fades [dbPerSecond] for [seconds]. */
    private fun pluck(startDbfs: Float, seconds: Float, dbPerSecond: Float = 12f, snrAbove: Float = -60f) =
        List((seconds / frame).toInt()) { i ->
            val level = startDbfs - dbPerSecond * i * frame
            note(level, snrDb = level - snrAbove)
        }

    @Test
    fun `a well played note asks for no tip, even while it fades away`() {
        for (reading in pluck(startDbfs = -25f, seconds = 2.5f)) assertNull(cue.update(reading, noteHeard = true, frame))
    }

    @Test
    fun `a note that starts too quietly asks to play louder, the whole note through`() {
        val tips = pluck(startDbfs = -46f, seconds = 0.5f, dbPerSecond = 4f).map { cue.update(it, noteHeard = true, frame) }
        assertTrue(tips.all { it == ReceptionTip.TOO_SOFT })
    }

    @Test
    fun `the quiet first frames of a strong note's attack are soon outgrown`() {
        val attack = listOf(-48f, -40f, -28f).map { cue.update(note(it), noteHeard = true, frame) }
        assertEquals(ReceptionTip.TOO_SOFT, attack.first())
        assertNull(attack.last())
    }

    @Test
    fun `each note is judged on its own`() {
        cue.update(note(-47f), noteHeard = true, frame)
        cue.update(room(-60f), noteHeard = false, frame)
        assertNull(cue.update(note(-30f), noteHeard = true, frame))
        cue.update(room(-60f), noteHeard = false, frame)
        assertEquals(ReceptionTip.TOO_SOFT, cue.update(note(-47f), noteHeard = true, frame))
    }

    @Test
    fun `a note's tip is still asked for through a short gap, then let go`() {
        cue.update(note(-47f), noteHeard = true, frame)
        val holdFrames = (TipCue.NOTE_TIP_HOLD_SECONDS / frame).toInt()
        repeat(holdFrames - 1) { assertEquals(ReceptionTip.TOO_SOFT, cue.update(room(-60f), noteHeard = false, frame)) }
        repeat(3) { cue.update(room(-60f), noteHeard = false, frame) }
        assertNull(cue.update(room(-60f), noteHeard = false, frame))
    }

    @Test
    fun `clipping asks to move back, and stops only once the peak is clearly lower`() {
        assertEquals(ReceptionTip.TOO_LOUD, cue.update(note(-6f, peakDbfs = 0f), noteHeard = true, frame))
        // Hovering just under full scale keeps it: no flicker at the limit.
        assertEquals(ReceptionTip.TOO_LOUD, cue.update(note(-6f, peakDbfs = -1f), noteHeard = true, frame))
        assertEquals(ReceptionTip.TOO_LOUD, cue.update(note(-6f, peakDbfs = -2.9f), noteHeard = true, frame))
        assertNull(cue.update(note(-8f, peakDbfs = -3.1f), noteHeard = true, frame))
        assertNull(cue.update(note(-8f, peakDbfs = -0.6f), noteHeard = true, frame))
        assertEquals(ReceptionTip.TOO_LOUD, cue.update(note(-8f, peakDbfs = -0.4f), noteHeard = true, frame))
    }

    @Test
    fun `too loud comes before every other tip`() {
        assertEquals(ReceptionTip.TOO_LOUD, cue.update(room(-30f, peakDbfs = 0f), noteHeard = false, frame))
    }

    @Test
    fun `a loud room asks for a quieter place, between notes too, with separate on and off limits`() {
        assertNull(cue.update(room(-39f), noteHeard = false, frame))
        assertEquals(ReceptionTip.TOO_NOISY, cue.update(room(-38f), noteHeard = false, frame))
        assertEquals(ReceptionTip.TOO_NOISY, cue.update(room(-41.9f), noteHeard = false, frame))
        assertEquals(ReceptionTip.TOO_NOISY, cue.update(note(-30f, snrDb = 10f).copy(noiseFloorDbfs = -40f), noteHeard = true, frame))
        assertNull(cue.update(room(-42.1f), noteHeard = false, frame))
        assertNull(cue.update(room(-39f), noteHeard = false, frame))
    }

    @Test
    fun `an unknown room background changes nothing`() {
        cue.update(room(-36f), noteHeard = false, frame)
        assertEquals(ReceptionTip.TOO_NOISY, cue.update(room(-36f).copy(noiseFloorDbfs = Float.NaN), noteHeard = false, frame))
    }

    @Test
    fun `a note that barely rises above the room is too noisy, a soft one asks to play louder first`() {
        assertEquals(ReceptionTip.TOO_NOISY, cue.update(note(-40f, snrDb = 6f), noteHeard = true, frame))
        assertNull(cue.update(note(-40f, snrDb = 12f), noteHeard = true, frame))
        // Its best SNR counts, so the fading tail doesn't bring the tip back.
        assertNull(cue.update(note(-43f, snrDb = 3f), noteHeard = true, frame))
        cue.update(room(-60f), noteHeard = false, frame)
        assertEquals(ReceptionTip.TOO_SOFT, cue.update(note(-46f, snrDb = 5f), noteHeard = true, frame))
    }

    @Test
    fun `through the steady hint line, a soft player gets one steady tip, not one per pluck`() {
        val line = SteadyHint<ReceptionTip>(TIP_GAP_SECONDS)
        val shown = mutableListOf<ReceptionTip?>()
        repeat(4) {
            for (reading in pluck(startDbfs = -46f, seconds = 1.2f, dbPerSecond = 3f)) {
                shown += line.update(cue.update(reading, noteHeard = true, frame), frame)
            }
            repeat((0.8f / frame).toInt()) { shown += line.update(cue.update(room(-60f), noteHeard = false, frame), frame) }
        }
        val changes = shown.zipWithNext().count { (a, b) -> a != b }
        assertEquals("only appears, never blinks: $shown", 1, changes)
        assertEquals(ReceptionTip.TOO_SOFT, shown.last())
    }

    @Test
    fun `through the steady hint line, a hard pluck that clips for its attack brings no tip`() {
        val line = SteadyHint<ReceptionTip>(TIP_GAP_SECONDS)
        val attack = List(8) { note(-8f, peakDbfs = 0f) }
        val shown = (attack + pluck(startDbfs = -12f, seconds = 2f)).map { line.update(cue.update(it, noteHeard = true, frame), frame) }
        assertTrue(shown.all { it == null })
    }

    @Test
    fun `through the steady hint line, a background hovering at the limit gives at most one change`() {
        val line = SteadyHint<ReceptionTip>(TIP_GAP_SECONDS)
        val shown = List(500) { i ->
            val floor = if (i % 6 < 3) -37.5f else -40.5f
            line.update(cue.update(room(floor), noteHeard = false, frame), frame)
        }
        assertEquals(1, shown.zipWithNext().count { (a, b) -> a != b })
    }
}
