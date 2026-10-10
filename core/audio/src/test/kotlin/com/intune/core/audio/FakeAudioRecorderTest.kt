package com.intune.core.audio

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeAudioRecorderTest {

    private val config = AudioConfig(sampleRate = 48_000, chunkSize = 4)

    @Test
    fun `cuts the signal into chunks and pads the last one with silence`() = runTest {
        val signal = FloatArray(10) { it + 1f }
        val chunks = FakeAudioRecorder(signal, config).chunks().map { it.copyOf() }.toList()

        assertEquals(3, chunks.size)
        assertArrayEquals(floatArrayOf(1f, 2f, 3f, 4f), chunks[0], 0f)
        assertArrayEquals(floatArrayOf(5f, 6f, 7f, 8f), chunks[1], 0f)
        assertArrayEquals(floatArrayOf(9f, 10f, 0f, 0f), chunks[2], 0f)
    }

    @Test
    fun `loops the signal when asked`() = runTest {
        val signal = floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f)
        val chunks = FakeAudioRecorder(signal, config, loop = true).chunks().map { it[0] }.take(5).toList()

        assertEquals(listOf(1f, 5f, 1f, 5f, 1f), chunks)
    }

    @Test
    fun `is paced like a real microphone in virtual time`() = runTest {
        val realistic = AudioConfig(sampleRate = 48_000, chunkSize = 1024)
        FakeAudioRecorder(FloatArray(10 * 1024), realistic).chunks().toList()

        assertEquals(10 * 21L, currentTime) // 1024 samples at 48 kHz ≈ 21 ms
    }

    @Test
    fun `reuses one array like the real recorder`() = runTest {
        val chunks = FakeAudioRecorder(FloatArray(12), config).chunks().toList()

        assertSame(chunks[0], chunks[2])
    }

    @Test
    fun `ends with the configured failure after the signal`() = runTest {
        val busy = AudioRecorderException.MicrophoneBusy()
        var caught: Throwable? = null
        val count = FakeAudioRecorder(FloatArray(8), config, failure = busy).chunks()
            .catch { caught = it }
            .toList().size

        assertEquals(2, count)
        assertSame(busy, caught)
    }

    @Test
    fun `reports when the mic is open and how often it was opened`() = runTest {
        val recorder = FakeAudioRecorder.silence(config)
        assertFalse(recorder.isRecording.value)

        val job = launch { recorder.chunks().collect {} }
        runCurrent()
        assertTrue(recorder.isRecording.value)
        advanceTimeBy(100)
        job.cancel()
        runCurrent()

        assertFalse(recorder.isRecording.value)
        assertEquals(1, recorder.startCount)
    }
}
