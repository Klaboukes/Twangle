package io.github.klaboukes.twangle.core.audio

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenReaderSpeechTest {

    @Test
    fun `watching follows the voice and lets go of it at the end`() = runTest {
        val speech = FakeScreenReaderSpeech()
        val seen = speech.watching { speaking ->
            runCurrent()
            assertEquals(1, speech.watchers)
            val before = speaking.value
            speech.isSpeaking = true
            runCurrent()
            val during = speaking.value
            speech.isSpeaking = false
            runCurrent()
            listOf(before, during, speaking.value)
        }
        assertEquals(listOf(false, true, false), seen)
        runCurrent()
        assertEquals("watched only while the block runs", 0, speech.watchers)
    }

    @Test
    fun `watching starts with a voice already speaking`() = runTest {
        val speech = FakeScreenReaderSpeech(speaking = true)
        assertTrue(speech.watching { it.value })
    }

    @Test
    fun `no screen reader never speaks`() = runTest {
        assertFalse(ScreenReaderSpeech.NONE.watching { it.value })
    }

    @Test
    fun `the tail is counted up in whole frames`() {
        // 0.4 s at the tuner's hop (1024 samples at 48 kHz, ≈ 21.3 ms) is 18.75 frames: 19.
        assertEquals(19, ScreenReaderSpeech.tailFrames(1024f / 48_000f))
    }
}
