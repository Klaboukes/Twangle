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
import io.github.klaboukes.twangle.core.dsp.synth.StringSynth
import io.github.klaboukes.twangle.core.dsp.synth.StringVoice
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
 * volume. The samples come from a [StringSynth] per voice, made the first time an instrument's
 * voice is asked for and kept.
 *
 * The track exists only while a tone sounds. [play] starts a session on a dedicated single thread
 * (`AudioTrack.write` blocks); the session renders 10 ms chunks into one buffer allocated per
 * session, so playing creates no garbage. Requests reach it through a field it checks every chunk:
 * a new note releases the old one over [StringSynth.STOP_FADE_SECONDS] and then starts (in its own
 * voice's synth), [stop] only releases. When the tone has ended the session pads the track with one
 * buffer of silence (so a very short tone still starts playing), waits until the last sample has
 * been played, then releases the track and [isPlaying] turns false. A failing track ends the
 * session the same way, quietly.
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

    /** Highest frequency [play] accepts. */
    val maxFrequencyHz: Float = StringSynth.maxFrequencyHz(sampleRate)

    // Used only on the writer thread: one synth per voice heard so far.
    private val synths = HashMap<StringVoice, StringSynth>()
    private val chunkFrames = sampleRate / CHUNKS_PER_SECOND

    private val scope = CoroutineScope(SupervisorJob())
    private val writeDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val lock = Any()

    // Guarded by lock: the latest request the session hasn't taken yet (null for none), and whether
    // a session is running.
    private var pending: Request? = null
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

    override fun play(frequencyHz: Float, voice: StringVoice) {
        require(frequencyHz > 0f && frequencyHz <= maxFrequencyHz) {
            "frequencyHz must be in (0, $maxFrequencyHz], was $frequencyHz"
        }
        request(Request.Play(frequencyHz, voice))
    }

    override fun stop() = request(Request.Stop)

    private fun request(value: Request) {
        synchronized(lock) {
            if (active) {
                pending = value
                return
            }
            if (value is Request.Stop) return
            pending = value
            active = true
            playing.value = true
        }
        scope.launch(writeDispatcher) { session() }
    }

    private fun takeRequest(): Request? = synchronized(lock) { pending.also { pending = null } }

    /** Ends the session unless a request came in meanwhile. */
    private fun finishIfIdle(): Boolean = synchronized(lock) {
        if (pending != null) return false
        active = false
        playing.value = false
        true
    }

    /** The synth for [voice], made on its first tone (on the writer thread). */
    private fun synthFor(voice: StringVoice): StringSynth = synths.getOrPut(voice) { StringSynth(sampleRate, voice) }

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
            var synth: StringSynth? = null
            var next: Request.Play? = null
            while (true) {
                currentCoroutineContext().ensureActive()
                val request = takeRequest()
                val current = synth
                when (request) {
                    is Request.Stop -> {
                        current?.release()
                        next = null
                    }
                    is Request.Play -> if (current != null && current.isSounding) {
                        current.release()
                        next = request
                    } else {
                        synth = synthFor(request.voice).also { it.start(request.frequencyHz) }
                    }
                    null -> Unit
                }
                val queued = next
                if (queued != null && synth?.isSounding != true) {
                    synth = synthFor(queued.voice).also { it.start(queued.frequencyHz) }
                    next = null
                }
                val count = synth?.render(buffer) ?: 0
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
                    pending = null
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

    /** What [play] and [stop] ask the session for. */
    private sealed interface Request {
        data object Stop : Request
        class Play(val frequencyHz: Float, val voice: StringVoice) : Request
    }

    private companion object {
        const val TAG = "TonePlayer"

        const val CHUNKS_PER_SECOND = 100
        const val BUFFERED_CHUNKS = 4
        const val DRAIN_POLL_MILLIS = 5L
        const val DRAIN_MARGIN_MILLIS = 300L
    }
}
