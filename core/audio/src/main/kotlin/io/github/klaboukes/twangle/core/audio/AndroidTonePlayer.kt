package io.github.klaboukes.twangle.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import io.github.klaboukes.twangle.core.dsp.synth.PluckSynth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [TonePlayer] backed by a streaming `android.media.AudioTrack`: mono `ENCODING_PCM_FLOAT` at the
 * phone's native output rate, `USAGE_MEDIA` / `CONTENT_TYPE_MUSIC`, so the tone follows the media
 * volume. The samples come from a [PluckSynth].
 *
 * The track exists only while a tone sounds. [play] starts a session on a dedicated single thread
 * (`AudioTrack.write` blocks); the session renders 10 ms chunks into one buffer allocated per
 * session, so playing creates no garbage. Requests reach it through a field it checks every chunk:
 * a new note releases the old one over [PluckSynth.STOP_FADE_SECONDS] and then starts, [stop] only
 * releases. When the tone has ended the session pads the track with one buffer of silence (so a
 * very short tone still starts playing), waits until the last sample has been played, then releases
 * the track and [isPlaying] turns false. A failing track ends the session the same way, quietly.
 *
 * Each session holds transient audio focus (`AUDIOFOCUS_GAIN_TRANSIENT`), so music from another
 * app pauses while the tone sounds and resumes after it; losing focus (a call, another app's sound
 * that won't share) stops the tone, and when focus is refused (during a call) nothing plays. With
 * the media volume at 0 a session shows the phone's own volume panel, so a silent tone explains itself.
 */
class AndroidTonePlayer(context: Context) : TonePlayer {

    private val audioManager = context.getSystemService(AudioManager::class.java)

    /** Sample rate of the tone: the phone's native output rate, 48 kHz if it reports nothing usable. */
    val sampleRate: Int = AudioOutput.nativeSampleRate()

    // Used only on the writer thread, apart from the immutable maxFrequencyHz.
    private val synth = PluckSynth(sampleRate)
    private val chunkFrames = sampleRate / CHUNKS_PER_SECOND

    private val scope = CoroutineScope(SupervisorJob())
    private val writeDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val lock = Any()

    // Guarded by lock: the latest request the session hasn't taken yet (NONE, STOP or a frequency),
    // and whether a session is running.
    private var pending = NONE
    private var active = false

    private val playing = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = playing.asStateFlow()

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    // Asked for when a session starts, given up when it ends. A loss that lets the tone duck
    // (a notification) is left to the system, which lowers the tone by itself; any other loss stops it.
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener(
            { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop()
            },
            Handler(Looper.getMainLooper()),
        )
        .build()

    override fun play(frequencyHz: Float) {
        require(frequencyHz > 0f && frequencyHz <= synth.maxFrequencyHz) {
            "frequencyHz must be in (0, ${synth.maxFrequencyHz}], was $frequencyHz"
        }
        request(frequencyHz)
    }

    override fun stop() = request(STOP)

    private fun request(value: Float) {
        synchronized(lock) {
            if (active) {
                pending = value
                return
            }
            if (value == STOP) return
            pending = value
            active = true
            playing.value = true
        }
        scope.launch(writeDispatcher) { session() }
    }

    private fun takeRequest(): Float = synchronized(lock) { pending.also { pending = NONE } }

    /** Ends the session unless a request came in meanwhile. */
    private fun finishIfIdle(): Boolean = synchronized(lock) {
        if (pending != NONE) return false
        active = false
        playing.value = false
        true
    }

    private suspend fun session() {
        var track: AudioTrack? = null
        var finished = false
        var focused = false
        try {
            if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.i(TAG, "No audio focus (a call?): the tone stays silent")
                return
            }
            focused = true
            AudioOutput.showVolumeIfSilent(audioManager)
            val opened = AudioOutput.openTrack(sampleRate, attributes, chunkFrames * BUFFERED_CHUNKS)
            track = opened
            opened.play()
            val buffer = FloatArray(chunkFrames)
            var framesWritten = 0L
            var next = NONE
            while (true) {
                currentCoroutineContext().ensureActive()
                val request = takeRequest()
                when {
                    request == STOP -> {
                        synth.release()
                        next = NONE
                    }
                    request != NONE && synth.isSounding -> {
                        synth.release()
                        next = request
                    }
                    request != NONE -> synth.start(request)
                }
                if (!synth.isSounding && next != NONE) {
                    synth.start(next)
                    next = NONE
                }
                val count = synth.render(buffer)
                if (count > 0) {
                    AudioOutput.write(opened, buffer, count)
                    framesWritten += count
                    continue
                }
                // The tone has ended: let it be heard to the last sample before saying so.
                buffer.fill(0f)
                var padding = opened.bufferSizeInFrames
                while (padding > 0) {
                    val n = minOf(padding, buffer.size)
                    AudioOutput.write(opened, buffer, n)
                    padding -= n
                }
                awaitPlayed(opened, framesWritten)
                if (finishIfIdle()) {
                    finished = true
                    break
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A track that can't be built or written (UnsupportedOperationException,
            // IllegalStateException). The tone is a convenience, so it fails quietly.
            Log.w(TAG, "Tone playback failed", e)
        } finally {
            track?.let(AudioOutput::close)
            if (focused) audioManager.abandonAudioFocusRequest(focusRequest)
            if (!finished) {
                synchronized(lock) {
                    active = false
                    pending = NONE
                    playing.value = false
                }
            }
        }
    }

    /** Waits until the playback head has passed [frames], or a little longer than the buffer lasts. */
    private suspend fun awaitPlayed(track: AudioTrack, frames: Long) {
        val bufferMillis = track.bufferSizeInFrames * 1000L / sampleRate
        val deadline = SystemClock.uptimeMillis() + 2 * bufferMillis + DRAIN_MARGIN_MILLIS
        // The head position is an unsigned 32-bit frame count.
        while ((track.playbackHeadPosition.toLong() and 0xFFFF_FFFFL) < frames &&
            SystemClock.uptimeMillis() < deadline
        ) {
            delay(DRAIN_POLL_MILLIS)
        }
    }

    private companion object {
        const val TAG = "TonePlayer"

        // Requests other than a frequency, which is always above 0.
        const val NONE = -1f
        const val STOP = -2f

        const val CHUNKS_PER_SECOND = 100
        const val BUFFERED_CHUNKS = 4
        const val DRAIN_POLL_MILLIS = 5L
        const val DRAIN_MARGIN_MILLIS = 300L
    }
}
