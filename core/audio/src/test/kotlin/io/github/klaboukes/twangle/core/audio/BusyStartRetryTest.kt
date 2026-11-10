package io.github.klaboukes.twangle.core.audio

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Before Android 10, a microphone still kept by the tuner (its 5 s grace) is tried again for a few seconds. */
@OptIn(ExperimentalCoroutinesApi::class)
class BusyStartRetryTest {

    private val busy = AudioRecorderException.MicrophoneBusy()

    @Test
    fun `a mic busy at the start, free 4 s later, records`() = runTest {
        val recorder = FakeAudioRecorder(failure = busy)
        launch {
            // The tuner lets go of the microphone when its grace ends.
            delay(4_000)
            recorder.failure = null
            recorder.signal = FloatArray(1024) { 0.1f }
            recorder.loop = true
        }

        val chunk = recorder.chunks().retryingBusyStart().first()

        assertEquals(0.1f, chunk[0])
        assertTrue("tried every 250 ms: ${recorder.startCount}", recorder.startCount in 16..18)
        assertTrue("about 4 s: ${currentTime}", currentTime in 4_000..4_300)
    }

    @Test
    fun `a mic that stays busy gives up after a few seconds`() = runTest {
        val recorder = FakeAudioRecorder(failure = busy)

        try {
            recorder.chunks().retryingBusyStart().first()
            fail("a busy microphone is passed on")
        } catch (e: AudioRecorderException.MicrophoneBusy) {
            assertEquals(BUSY_START_TRIES + 1, recorder.startCount)
            assertEquals("longer than the tuner's 5 s grace", 6_000L, currentTime)
        }
    }

    @Test
    fun `a mic that goes busy once it records is not tried again`() = runTest {
        // A call takes the microphone after the first chunks.
        val recorder = FakeAudioRecorder(FloatArray(2048) { 0.1f }, failure = busy)
        val chunks = mutableListOf<FloatArray>()

        try {
            recorder.chunks().retryingBusyStart().collect { chunks += it.copyOf() }
            fail("the busy is passed on")
        } catch (e: AudioRecorderException.MicrophoneBusy) {
            assertEquals(2, chunks.size)
            assertEquals(1, recorder.startCount)
        }
    }

    @Test
    fun `other failures are passed on at once`() = runTest {
        val recorder = FakeAudioRecorder(failure = AudioRecorderException.InitializationFailed("no input"))

        try {
            recorder.chunks().retryingBusyStart().first()
            fail("the failure is passed on")
        } catch (e: AudioRecorderException.InitializationFailed) {
            assertEquals(1, recorder.startCount)
            assertEquals(0L, currentTime)
        }
    }

    @Test
    fun `a free mic records from the first try`() = runTest {
        val recorder = FakeAudioRecorder(FloatArray(4096) { 0.1f })

        assertEquals(4, recorder.chunks().retryingBusyStart().take(4).toList().size)
        assertEquals(1, recorder.startCount)
    }
}
