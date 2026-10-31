package io.github.klaboukes.twangle.wear

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What a screen broadcast tells the tuner: the screen going off always lets go of the microphone,
 * and the screen coming on opens it again only while the tuner is the screen shown.
 */
class ScreenStateTest {

    @Test
    fun `the screen going off counts whether or not the tuner is shown`() {
        assertEquals(false, screenReport(Intent.ACTION_SCREEN_OFF, tunerOnScreen = true))
        assertEquals(false, screenReport(Intent.ACTION_SCREEN_OFF, tunerOnScreen = false))
    }

    @Test
    fun `the screen coming on counts only with the tuner on it`() {
        assertEquals(true, screenReport(Intent.ACTION_SCREEN_ON, tunerOnScreen = true))
        // Waking from always-on onto the watch face: the microphone stays closed until the tuner
        // itself comes back, which catches up on the screen then.
        assertNull(screenReport(Intent.ACTION_SCREEN_ON, tunerOnScreen = false))
    }

    @Test
    fun `other broadcasts tell nothing`() {
        assertNull(screenReport(Intent.ACTION_USER_PRESENT, tunerOnScreen = true))
        assertNull(screenReport(null, tunerOnScreen = true))
    }
}
