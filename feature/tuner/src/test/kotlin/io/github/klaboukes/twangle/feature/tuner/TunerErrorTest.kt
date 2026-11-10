package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.audio.AudioRecorderException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
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
    fun `a microphone failure is logged and shown`() = runTest {
        val logged = mutableListOf<Throwable>()
        val busy = AudioRecorderException.MicrophoneBusy()
        val shown = flow<TunerUiState> {
            emit(TunerUiState.PermissionRequired)
            throw busy
        }.showingMicFailures { logged += it }.toList()
        assertEquals(listOf(TunerUiState.PermissionRequired, TunerUiState.Error(TunerError.MIC_BUSY)), shown)
        assertEquals(listOf<Throwable>(busy), logged)
    }

    // Until Phase 89 a bug in the app's own code showed as "Recording stopped", logged nowhere and never in Play's
    // crash reports.
    @Test
    fun `any other failure is logged and thrown on, not shown as a stopped microphone`() = runTest {
        val logged = mutableListOf<Throwable>()
        val bug = IllegalStateException("a bug")
        val shown = mutableListOf<TunerUiState>()
        try {
            flow<TunerUiState> { throw bug }.showingMicFailures { logged += it }.collect { shown += it }
            fail("the bug was swallowed")
        } catch (e: IllegalStateException) {
            assertSame(bug, e)
        }
        assertEquals(emptyList<TunerUiState>(), shown)
        assertEquals(listOf<Throwable>(bug), logged)
    }
}
