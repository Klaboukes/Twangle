package io.github.klaboukes.twangle.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.view.accessibility.AccessibilityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The phone's playback reports as [AndroidScreenReaderSpeech] reads them (Phase 81). A screen reader's voice is played
 * here by a track of silence with the accessibility usage, as TalkBack's speech engine plays its own; it makes no
 * sound and stops by itself.
 */
@RunWith(AndroidJUnit4::class)
class AndroidScreenReaderSpeechTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val speech = AndroidScreenReaderSpeech(context)

    @Test
    fun aSoundWithTheAccessibilityUsageCountsWhilePlayingOnly() = runBlocking {
        val playback = speech.accessibilityPlayback().produceIn(this)
        try {
            withTimeout(TIMEOUT_MILLIS) { while (playback.receive()) Unit }
            val track = silentAccessibilityTrack()
            try {
                track.play()
                withTimeout(TIMEOUT_MILLIS) { while (!playback.receive()) Unit }
                // Stopped but not yet released: the report leaves it out, so the voice counts as gone.
                track.stop()
                withTimeout(TIMEOUT_MILLIS) { while (playback.receive()) Unit }
            } finally {
                track.release()
            }
        } finally {
            playback.cancel()
        }
    }

    @Test
    fun withoutAScreenReaderNothingSpeaks() = runBlocking {
        // The test phones run without TalkBack (docs/devices.md): touch exploration is off.
        assertFalse(context.getSystemService(AccessibilityManager::class.java).isTouchExplorationEnabled)
        assertFalse(withTimeout(TIMEOUT_MILLIS) { speech.speaking.first() })
        val track = silentAccessibilityTrack()
        try {
            track.play()
            assertFalse("only watched with a screen reader on", withTimeout(TIMEOUT_MILLIS) { speech.speaking.first() })
        } finally {
            track.release()
        }
    }

    /** A second of silence, mono, with the usage TalkBack's speech has. */
    private fun silentAccessibilityTrack(): AudioTrack {
        val rate = 16_000
        val samples = ShortArray(rate)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
        track.write(samples, 0, samples.size)
        return track
    }

    private companion object {
        const val TIMEOUT_MILLIS = 3_000L
    }
}
