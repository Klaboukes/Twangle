package io.github.klaboukes.twangle.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstStartTourTest {

    @Test
    fun `a fresh install is due the tour, and it shows on the tuner once the microphone is allowed`() {
        val due = tourDue(seen = false, requested = false, endedNow = false)
        assertTrue(due)
        assertFalse("not over the microphone question", tourShown(due, onTuner = true, tunerShown = false))
        assertFalse("not on another screen", tourShown(due, onTuner = false, tunerShown = true))
        assertTrue(tourShown(due, onTuner = true, tunerShown = true))
    }

    @Test
    fun `skip, back or done ends it at once, before the saved flag arrives`() {
        assertFalse(tourDue(seen = false, requested = false, endedNow = true))
    }

    @Test
    fun `seen (or an update) never shows it by itself`() {
        assertFalse(tourDue(seen = true, requested = false, endedNow = false))
    }

    @Test
    fun `show me around brings it back, also after it ended`() {
        assertTrue(tourDue(seen = true, requested = true, endedNow = true))
        assertTrue(tourShown(tourDue(seen = true, requested = true, endedNow = false), onTuner = true, tunerShown = true))
    }
}
