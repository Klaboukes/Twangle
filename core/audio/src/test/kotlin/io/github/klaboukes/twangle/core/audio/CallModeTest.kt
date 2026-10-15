package io.github.klaboukes.twangle.core.audio

import android.media.AudioManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallModeTest {

    @Test
    fun `the metronome plays only when the phone's audio is in its normal mode`() {
        assertFalse(isCallMode(AudioManager.MODE_NORMAL))
    }

    @Test
    fun `ringing, a phone call and a voice chat all stop the metronome`() {
        assertTrue(isCallMode(AudioManager.MODE_RINGTONE))
        assertTrue(isCallMode(AudioManager.MODE_IN_CALL))
        assertTrue(isCallMode(AudioManager.MODE_IN_COMMUNICATION))
        assertTrue(isCallMode(AudioManager.MODE_CALL_SCREENING))
        assertTrue(isCallMode(AudioManager.MODE_CALL_REDIRECT))
        assertTrue(isCallMode(AudioManager.MODE_COMMUNICATION_REDIRECT))
    }
}
