package com.intune.core.audio

import android.media.MediaRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AudioSourceSelectorTest {

    @Test
    fun `unprocessed is tried first when supported, voice recognition second`() {
        assertEquals(
            listOf(AudioInput.UNPROCESSED, AudioInput.VOICE_RECOGNITION),
            AudioSourceSelector.candidates(supportsUnprocessed = true),
        )
    }

    @Test
    fun `only voice recognition when unprocessed is unsupported`() {
        assertEquals(listOf(AudioInput.VOICE_RECOGNITION), AudioSourceSelector.candidates(supportsUnprocessed = false))
    }

    @Test
    fun `inputs map to the matching Android sources`() {
        assertEquals(MediaRecorder.AudioSource.UNPROCESSED, AudioInput.UNPROCESSED.androidSource)
        assertEquals(MediaRecorder.AudioSource.VOICE_RECOGNITION, AudioInput.VOICE_RECOGNITION.androidSource)
    }

    @Test
    fun `sources with AGC or noise suppression are never used`() {
        val forbidden = setOf(MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.VOICE_COMMUNICATION)
        for (input in AudioInput.entries) {
            assertFalse("$input uses a processed source", input.androidSource in forbidden)
        }
    }
}
