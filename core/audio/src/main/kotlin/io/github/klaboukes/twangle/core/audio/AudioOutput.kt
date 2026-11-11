package io.github.klaboukes.twangle.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/**
 * What the app's sound players (reference tone, drone, metronome) share: a mono float `AudioTrack`
 * at the phone's native output rate, blocking writes, and the volume panel at media volume 0. The
 * tone's and the drone's sessions around it are [PlayerSessions].
 */
internal object AudioOutput {

    private const val CHANNEL_MASK = AudioFormat.CHANNEL_OUT_MONO
    private const val ENCODING = AudioFormat.ENCODING_PCM_FLOAT
    private const val MIN_SAMPLE_RATE = 16_000
    private const val MAX_SAMPLE_RATE = 192_000
    private const val DEFAULT_SAMPLE_RATE = 48_000

    /** The phone's native output rate, or 48 kHz if it reports nothing usable. */
    fun nativeSampleRate(): Int = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
        .takeIf { it in MIN_SAMPLE_RATE..MAX_SAMPLE_RATE } ?: DEFAULT_SAMPLE_RATE

    /**
     * A streaming mono float track whose buffer holds at least [minFrames] samples.
     *
     * @throws IllegalStateException if it doesn't initialize; [UnsupportedOperationException] from
     *   the builder when the format can't be played.
     */
    fun openTrack(sampleRate: Int, attributes: AudioAttributes, minFrames: Int): AudioTrack {
        val minBytes = AudioTrack.getMinBufferSize(sampleRate, CHANNEL_MASK, ENCODING)
        val bufferBytes = maxOf(minBytes, minFrames * Float.SIZE_BYTES)
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(CHANNEL_MASK)
                    .setEncoding(ENCODING)
                    .build(),
            )
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            throw IllegalStateException("AudioTrack did not initialize at $sampleRate Hz")
        }
        return track
    }

    /**
     * Blocks until [count] samples of [buffer] are queued. A failing write, or a run of writes
     * that take nothing (a track the system stopped), throws [IllegalStateException] rather than
     * spinning; the players end their session quietly on it.
     */
    fun write(track: AudioTrack, buffer: FloatArray, count: Int) {
        transferFully(
            count = count,
            pause = ::pauseAfterEmptyStep,
            onError = { throw IllegalStateException("AudioTrack.write failed: $it") },
            onStalled = { throw IllegalStateException("AudioTrack.write took nothing $MAX_EMPTY_STEPS times in a row") },
        ) { done -> track.write(buffer, done, count - done, AudioTrack.WRITE_BLOCKING) }
    }

    /** Stops and frees [track], ignoring a track that is already broken. */
    fun close(track: AudioTrack) {
        runCatching {
            track.pause()
            track.flush()
        }
        track.release()
    }

    /**
     * At media volume 0 a sound would be silent: show the phone's volume panel (at its level, not
     * changing it), so the player sees why and can turn it up with the volume keys.
     */
    fun showVolumeIfSilent(audioManager: AudioManager) {
        runCatching {
            if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            }
        }
    }
}
