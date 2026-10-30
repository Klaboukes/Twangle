package io.github.klaboukes.twangle.wear

import io.github.klaboukes.twangle.core.dsp.awake.ScreenAwakeRule
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchScreenAwakeTest {

    private val minutes3 = ScreenAwakeRule.DEFAULT_QUIET_MILLIS
    private var now = 0L
    private val awake = WatchScreenAwake { now }
    private val note = WatchTunerState.Detected(Note.of(PitchClass.E, 2), cents = 3)

    @Test
    fun `held at first, let go after three quiet minutes, without a warning`() {
        assertTrue(awake.held)
        now = minutes3 - 1
        awake.heard(WatchTunerState.Listening)
        assertTrue(awake.held)
        now = minutes3
        awake.heard(WatchTunerState.Listening)
        assertFalse(awake.held)
    }

    @Test
    fun `a note held a second takes the screen back, and the quiet time counts from its end`() {
        now = minutes3
        awake.heard(note)
        assertFalse("not yet a second", awake.held)
        now = minutes3 + 1_000
        awake.heard(note)
        assertTrue(awake.held)
        now = minutes3 + 30_000
        awake.heard(WatchTunerState.Listening)
        now = 2 * minutes3 + 30_000 - 1
        awake.heard(WatchTunerState.Listening)
        assertTrue(awake.held)
    }

    @Test
    fun `the screen coming on starts the quiet time afresh`() {
        now = 10 * minutes3
        awake.touched()
        assertTrue(awake.held)
        now = 11 * minutes3 - 1
        awake.heard(WatchTunerState.Listening)
        assertTrue(awake.held)
    }

    @Test
    fun `lets go by itself once the quiet time has passed`() = runTest {
        val timed = WatchScreenAwake { testScheduler.currentTime }
        backgroundScope.launch { timed.run() }
        advanceTimeBy(minutes3 - 1)
        assertTrue(timed.held)
        advanceTimeBy(2)
        assertFalse(timed.held)
    }

    @Test
    fun `a heard note is sounding, listening and failures are quiet`() {
        assertEquals(ScreenAwakeRule.Sound.SOUNDING, WatchScreenAwake.soundOf(note))
        assertEquals(ScreenAwakeRule.Sound.QUIET, WatchScreenAwake.soundOf(WatchTunerState.Listening))
        assertEquals(
            ScreenAwakeRule.Sound.QUIET,
            WatchScreenAwake.soundOf(WatchTunerState.Failed(WatchTunerError.MICROPHONE_BUSY)),
        )
    }
}
