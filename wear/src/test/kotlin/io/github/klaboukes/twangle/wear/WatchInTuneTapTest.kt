package io.github.klaboukes.twangle.wear

import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchInTuneTapTest {

    private val a2 = Note.of(PitchClass.A, 2)
    private val d3 = Note.of(PitchClass.D, 3)
    private val tap = WatchInTuneTap()

    private fun heard(note: Note, cents: Int, inTune: Boolean) = WatchTunerState.Detected(note, cents, inTune)

    @Test
    fun `taps once as the note comes in tune, not while it stays there`() {
        assertFalse(tap.update(WatchTunerState.Listening, 0))
        assertFalse(tap.update(heard(a2, -12, false), 100))
        assertFalse(tap.update(heard(a2, -3, false), 200))
        assertTrue(tap.update(heard(a2, -2, true), 500))
        // The cents move within tune: still the same tuned note.
        assertFalse(tap.update(heard(a2, 1, true), 600))
        assertFalse(tap.update(heard(a2, 0, true), 5_000))
    }

    @Test
    fun `a note wobbling out of tune and back does not tap again`() {
        assertTrue(tap.update(heard(a2, 0, true), 0))
        assertFalse(tap.update(heard(a2, 8, false), 1_000))
        assertFalse(tap.update(heard(a2, 3, true), 1_400))
    }

    @Test
    fun `the same note taps again after it left tune for a while`() {
        assertTrue(tap.update(heard(a2, 0, true), 0))
        assertFalse(tap.update(heard(a2, -15, false), 100))
        assertFalse(tap.update(WatchTunerState.Listening, 900))
        assertTrue(tap.update(heard(a2, 0, true), 2_000))
    }

    @Test
    fun `another note taps at once`() {
        assertTrue(tap.update(heard(a2, 0, true), 0))
        assertFalse(tap.update(heard(d3, -20, false), 100))
        assertTrue(tap.update(heard(d3, 0, true), 500))
    }

    @Test
    fun `no note, a failure or a note off pitch never taps`() {
        val states = listOf(
            WatchTunerState.Listening,
            WatchTunerState.Failed(WatchTunerError.MICROPHONE_BUSY),
            heard(a2, 20, false),
            WatchTunerState.Listening,
        )
        assertTrue(states.withIndex().none { (i, s) -> tap.update(s, i * 100L) })
    }

    @Test
    fun `appearing on a note already in tune does not tap`() {
        val restored = WatchInTuneTap(initiallyInTune = true)
        assertFalse(restored.update(heard(a2, 0, true), 0))
        assertTrue(restored.wasInTune)
    }

    @Test
    fun `counts every tap over a tuning session`() {
        // E2 tuned up into tune, wobbles once, then A2, then E2 again after a long pause.
        val e2 = Note.of(PitchClass.E, 2)
        val session = listOf(
            heard(e2, -30, false) to 0L, heard(e2, -4, false) to 400L, heard(e2, -2, true) to 700L,
            heard(e2, 6, true) to 900L, heard(e2, 9, false) to 1_000L, heard(e2, 2, true) to 1_300L,
            WatchTunerState.Listening to 3_000L,
            heard(a2, 10, false) to 4_000L, heard(a2, 1, true) to 4_400L,
            WatchTunerState.Listening to 6_000L,
            heard(e2, 0, true) to 9_000L,
        )
        assertEquals(3, session.count { (state, at) -> tap.update(state, at) })
    }
}
