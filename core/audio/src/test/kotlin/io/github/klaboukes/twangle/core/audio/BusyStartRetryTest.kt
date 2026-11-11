package io.github.klaboukes.twangle.core.audio

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Before Android 10, a microphone still kept by the app's own tuner (its 5 s grace) is tried again until it lets go;
 * one another app holds, for about 1 s.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BusyStartRetryTest {

    private val busy = AudioRecorderException.MicrophoneBusy()

    // Whether another of the app's own recordings (the tuner's, in its grace) holds the microphone.
    private var tunerHolds = false

    private fun FakeAudioRecorder.retrying() = chunks().retryingBusyStart(appRecording = { tunerHolds })

    /** The tuner lets go of the microphone after [millis]: free for us then, unless [otherApp] holds it too. */
    private fun TestScope.tunerLetsGoAfter(millis: Long, recorder: FakeAudioRecorder, otherApp: Boolean = false) {
        tunerHolds = true
        launch {
            delay(millis)
            tunerHolds = false
            if (!otherApp) {
                recorder.failure = null
                recorder.signal = FloatArray(1024) { 0.1f }
                recorder.loop = true
            }
        }
    }

    /** The busy that [recorder] passes on, with the virtual time it came at. */
    private suspend fun TestScope.givesUp(recorder: FakeAudioRecorder): Long {
        try {
            recorder.retrying().first()
            fail("a busy microphone is passed on")
        } catch (e: AudioRecorderException.MicrophoneBusy) {
            // Passed on.
        }
        return currentTime
    }

    @Test
    fun `a mic the tuner keeps 4 s more records as soon as it lets go`() = runTest {
        val recorder = FakeAudioRecorder(failure = busy)
        tunerLetsGoAfter(4_000, recorder)

        val chunk = recorder.retrying().first()

        assertEquals(0.1f, chunk[0])
        assertTrue("tried every 250 ms: ${recorder.startCount}", recorder.startCount in 16..18)
        assertTrue("about 4 s: $currentTime", currentTime in 4_000..4_300)
    }

    @Test
    fun `a mic another app holds is busy after about 1 s`() = runTest {
        val recorder = FakeAudioRecorder(failure = busy)

        assertEquals(1_000L, givesUp(recorder))
        assertEquals(BUSY_START_FREE_TRIES + 1, recorder.startCount)
    }

    @Test
    fun `Try again on a mic another app holds answers after about 1 s again`() = runTest {
        val recorder = FakeAudioRecorder(failure = busy)
        val first = givesUp(recorder)

        // The player taps Try again: a new start, the other app still recording.
        val again = givesUp(recorder) - first

        assertEquals(1_000L, again)
        assertEquals(2 * (BUSY_START_FREE_TRIES + 1), recorder.startCount)
    }

    @Test
    fun `a mic another app takes as the tuner lets go is busy about 1 s later`() = runTest {
        val recorder = FakeAudioRecorder(failure = busy)
        tunerLetsGoAfter(3_000, recorder, otherApp = true)

        val time = givesUp(recorder)

        assertTrue("1 s after 3 s: $time", time in 3_750..4_000)
    }

    @Test
    fun `a mic the tuner never lets go is busy after 6 s`() = runTest {
        val recorder = FakeAudioRecorder(failure = busy)
        tunerHolds = true

        assertEquals("longer than the tuner's 5 s grace", 6_000L, givesUp(recorder))
        assertEquals(BUSY_START_TRIES + 1, recorder.startCount)
    }

    @Test
    fun `a mic that goes busy once it records is not tried again`() = runTest {
        // A call takes the microphone after the first chunks.
        val recorder = FakeAudioRecorder(FloatArray(2048) { 0.1f }, failure = busy)
        val chunks = mutableListOf<FloatArray>()

        try {
            recorder.retrying().collect { chunks += it.copyOf() }
            fail("the busy is passed on")
        } catch (e: AudioRecorderException.MicrophoneBusy) {
            assertEquals(2, chunks.size)
            assertEquals(1, recorder.startCount)
        }
    }

    @Test
    fun `other failures are passed on at once`() = runTest {
        val recorder = FakeAudioRecorder(failure = AudioRecorderException.InitializationFailed("no input"))
        tunerHolds = true

        try {
            recorder.retrying().first()
            fail("the failure is passed on")
        } catch (e: AudioRecorderException.InitializationFailed) {
            assertEquals(1, recorder.startCount)
            assertEquals(0L, currentTime)
        }
    }

    @Test
    fun `a free mic records from the first try`() = runTest {
        val recorder = FakeAudioRecorder(FloatArray(4096) { 0.1f })

        assertEquals(4, recorder.retrying().take(4).toList().size)
        assertEquals(1, recorder.startCount)
    }

    @Test
    fun `the app's recordings are counted until each lets go`() {
        val open = OpenRecordings()
        assertFalse(open.any)

        open.opened()
        open.opened()
        open.closed()
        assertTrue("one still records", open.any)

        open.closed()
        assertFalse(open.any)
    }
}
