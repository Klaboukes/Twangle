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
import io.github.klaboukes.twangle.core.dsp.synth.StringMix
import io.github.klaboukes.twangle.core.dsp.synth.StringSynth
import io.github.klaboukes.twangle.core.dsp.synth.StringVoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
 * volume. The samples come from a [StringMix] per voice (one [StringSynth] per note sounding
 * together, a single note being exactly its synth's), made the first time an instrument's voice is
 * asked for and kept; a synth for each further note is made the first time that many notes sound
 * together, and kept.
 *
 * The track exists only while a tone sounds. [play] starts a session on the player's own thread at
 * audio priority ([audioThreadDispatcher]; `AudioTrack.write` blocks); the session renders 10 ms chunks into one buffer allocated per
 * session, so playing creates no garbage. Requests reach it through a field it checks every chunk:
 * a new note (or notes, [playTogether]) releases the old ones over [StringSynth.STOP_FADE_SECONDS]
 * and then starts (in its own voice's mix), [stop] only releases. When the tone has ended the session pads the track with one
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

    // Used only on the writer thread: one mix per voice heard so far.
    private val mixes = HashMap<StringVoice, StringMix>()
    private val chunkFrames = sampleRate / CHUNKS_PER_SECOND

    private val scope = CoroutineScope(SupervisorJob())
    private val writeDispatcher = audioThreadDispatcher(WRITER_THREAD_NAME)

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

    override fun play(frequencyHz: Float, voice: StringVoice, octavePair: Boolean) {
        require(frequencyHz > 0f && frequencyHz <= maxFrequencyHz) {
            "frequencyHz must be in (0, $maxFrequencyHz], was $frequencyHz"
        }
        request(Request.Play(floatArrayOf(frequencyHz), voice, booleanArrayOf(octavePair), strumSeconds = 0f))
    }

    override fun playTogether(frequenciesHz: List<Float>, voice: StringVoice, strumSeconds: Float, octavePairs: List<Boolean>) {
        require(frequenciesHz.size in 1..TonePlayer.MAX_TOGETHER) { "One to ${TonePlayer.MAX_TOGETHER} notes, not ${frequenciesHz.size}" }
        for (f in frequenciesHz) require(f > 0f && f <= maxFrequencyHz) { "frequencyHz must be in (0, $maxFrequencyHz], was $f" }
        require(strumSeconds in 0f..StringMix.MAX_SPREAD_SECONDS) { "strumSeconds must be in 0..${StringMix.MAX_SPREAD_SECONDS}, was $strumSeconds" }
        // Copied here, on the caller's thread, so the writer thread allocates nothing for it.
        val pairs = BooleanArray(frequenciesHz.size) { octavePairs.getOrElse(it) { false } }
        request(Request.Play(frequenciesHz.toFloatArray(), voice, pairs, strumSeconds))
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

    /** The mix for [voice], made on its first tone (on the writer thread). */
    private fun mixFor(voice: StringVoice): StringMix = mixes.getOrPut(voice) { StringMix(sampleRate, voice) }

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
            var synth: StringMix? = null
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
                        synth = mixFor(request.voice).also(request::startIn)
                    }
                    null -> Unit
                }
                val queued = next
                if (queued != null && synth?.isSounding != true) {
                    synth = mixFor(queued.voice).also(queued::startIn)
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

    /** What [play], [playTogether] and [stop] ask the session for. */
    private sealed interface Request {
        data object Stop : Request
        class Play(val frequenciesHz: FloatArray, val voice: StringVoice, val octavePairs: BooleanArray, val strumSeconds: Float) : Request {
            fun startIn(mix: StringMix) = mix.start(frequenciesHz, frequenciesHz.size, strumSeconds, octavePairs)
        }
    }

    private companion object {
        const val TAG = "TonePlayer"
        const val WRITER_THREAD_NAME = "TonePlayer"

        const val CHUNKS_PER_SECOND = 100
        const val BUFFERED_CHUNKS = 4
        const val DRAIN_POLL_MILLIS = 5L
        const val DRAIN_MARGIN_MILLIS = 300L
    }
}
