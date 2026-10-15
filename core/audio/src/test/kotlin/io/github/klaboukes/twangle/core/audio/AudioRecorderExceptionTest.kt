package io.github.klaboukes.twangle.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRecorderExceptionTest {

    @Test
    fun `read failures keep the AudioRecord error code`() {
        val e = AudioRecorderException.ReadFailed(-6)
        assertEquals(-6, e.errorCode)
        assertTrue(e.message!!.contains("-6"))
    }

    @Test
    fun `a stuck read can say why`() {
        val e = AudioRecorderException.ReadFailed(0, "nothing came")
        assertEquals(0, e.errorCode)
        assertEquals("nothing came", e.message)
    }

    @Test
    fun `causes are kept for diagnostics`() {
        val cause = IllegalStateException("start failed")
        assertSame(cause, AudioRecorderException.MicrophoneBusy(cause = cause).cause)
        assertSame(cause, AudioRecorderException.InitializationFailed("x", cause).cause)
    }

    @Test
    fun `every failure can be handled as one type`() {
        val failures: List<Exception> = listOf(
            AudioRecorderException.PermissionMissing(),
            AudioRecorderException.InitializationFailed("x"),
            AudioRecorderException.MicrophoneBusy(),
            AudioRecorderException.ReadFailed(-3),
        )
        assertTrue(failures.all { it is AudioRecorderException })
    }
}
