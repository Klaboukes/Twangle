package com.intune.core.audio

import android.media.AudioManager
import android.media.MediaRecorder

/**
 * A microphone input the tuner may use. Only sources without automatic gain control and noise
 * suppression are listed: those would distort pitch and level. `MIC` and `VOICE_COMMUNICATION`
 * are deliberately absent (CLAUDE.md, hard rule 5).
 */
enum class AudioInput(val androidSource: Int) {
    /** Raw microphone signal. Optional on Android; see [AudioSourceSelector.supportsUnprocessed]. */
    UNPROCESSED(MediaRecorder.AudioSource.UNPROCESSED),

    /** Tuned for speech recognition, which Android requires to have AGC and noise suppression off. */
    VOICE_RECOGNITION(MediaRecorder.AudioSource.VOICE_RECOGNITION),
}

/** Picks which [AudioInput]s to try, best first. */
object AudioSourceSelector {

    /**
     * Inputs to try in order: [AudioInput.UNPROCESSED] when the device reports support for it,
     * then [AudioInput.VOICE_RECOGNITION] as the fallback. The recorder moves on to the next one if
     * an input fails to open.
     */
    fun candidates(supportsUnprocessed: Boolean): List<AudioInput> =
        if (supportsUnprocessed) {
            listOf(AudioInput.UNPROCESSED, AudioInput.VOICE_RECOGNITION)
        } else {
            listOf(AudioInput.VOICE_RECOGNITION)
        }

    /** Whether the device says it supports the unprocessed source (it is optional on Android). */
    fun supportsUnprocessed(audioManager: AudioManager): Boolean =
        audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED).toBoolean()
}
