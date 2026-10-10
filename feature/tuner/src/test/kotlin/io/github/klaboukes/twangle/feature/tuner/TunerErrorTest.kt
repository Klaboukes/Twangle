package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.audio.AudioRecorderException
import org.junit.Assert.assertEquals
import org.junit.Test

class TunerErrorTest {

    @Test
    fun `recorder failures map to user-facing errors`() {
        assertEquals(TunerError.MIC_PERMISSION_MISSING, AudioRecorderException.PermissionMissing().toTunerError())
        assertEquals(TunerError.MIC_BUSY, AudioRecorderException.MicrophoneBusy().toTunerError())
        assertEquals(TunerError.MIC_UNAVAILABLE, AudioRecorderException.InitializationFailed("x").toTunerError())
        assertEquals(TunerError.MIC_STOPPED, AudioRecorderException.ReadFailed(-6).toTunerError())
    }

    @Test
    fun `unexpected failures are treated as a stopped mic`() {
        assertEquals(TunerError.MIC_STOPPED, IllegalStateException().toTunerError())
    }
}
