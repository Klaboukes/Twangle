package io.github.klaboukes.twangle.core.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import io.github.klaboukes.twangle.core.dsp.drone.DroneSound
import io.github.klaboukes.twangle.core.dsp.drone.DroneSynth
import io.github.klaboukes.twangle.core.dsp.drone.DroneVoicing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [DronePlayer] backed by a streaming `android.media.AudioTrack`: mono `ENCODING_PCM_FLOAT` at the
 * phone's native output rate, `USAGE_MEDIA` / `CONTENT_TYPE_MUSIC`, so the media volume sets its
 * loudness, as the reference tone ([AndroidTonePlayer]). The samples come from one [DroneSynth],
 * made on the first drone and kept.
 *
 * The track exists only while the drone sounds. [play] starts a session on the player's own writer
 * thread (`AudioTrack.write` blocks), at `THREAD_PRIORITY_AUDIO` and gone again a few seconds after
 * the last session; the session renders 10 ms chunks into one buffer allocated per session and keeps
 * [BUFFERED_CHUNKS] queued (≈ 120 ms, against a busy moment: a gap would click), so playing creates
 * no garbage. `DroneSynth` is not thread-safe, so [play] and [stop] only leave the latest request
 * in a field under a lock, which the session takes before every chunk and hands to the synth there.
 * After [stop] the synth fades out ([DroneSynth.STOP_FADE_SECONDS]); the session then pads one
 * buffer of silence, waits until the last sample has been played, releases the track and lets the
 * speaker and audio focus go. A [play] during that fade or wait brings the drone back in the same
 * session and track.
 *
 * **Audio focus:** each session holds `AUDIOFOCUS_GAIN_TRANSIENT`, as the tone: a drone is for
 * playing against on its own, so another app's music pauses, and resumes when the drone ends.
 * Losing focus (a call, another app's sound that won't share) stops the drone; a loss that lets it
 * duck (a notification) is left to the system; refused focus (during a call) means no drone.
 * **Headphones coming unplugged** (`ACTION_AUDIO_BECOMING_NOISY`, Bluetooth ones too) stop it, as
 * the metronome, so it never carries on out of the speaker. With the media volume at 0 a session
 * shows the phone's own volume panel. A failing track ends the session quietly (logged).
 */
class AndroidDronePlayer(context: Context) : DronePlayer {

    private val appContext = context.applicationContext
    private val audioManager = context.getSystemService(AudioManager::class.java)

    /** Sample rate of the drone: the phone's native output rate, 48 kHz if it reports nothing usable. */
    val sampleRate: Int = AudioOutput.nativeSampleRate()

    private val chunkFrames = sampleRate / CHUNKS_PER_SECOND

    // One thread at audio priority, made for a session and ended when idle for a while.
    private val writer = audioThreadExecutor(WRITER_THREAD_NAME)

    // Used only on the writer thread, made on the first drone.
    private var synth: DroneSynth? = null

    private val lock = Any()

    // Guarded by lock: the latest request the session hasn't taken yet (null for none), and whether
    // a session is running.
    private var pending: Request? = null
    private var active = false

    private val playing = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = playing.asStateFlow()

    private val holding = MutableStateFlow(false)

    /** True while a session holds an audio track (from [play] until the fade after [stop] has been heard). */
    val holdsSpeaker: StateFlow<Boolean> = holding.asStateFlow()

    /** Times the speaker ran dry in the last session that ended (each would be heard as a click). */
    @VisibleForTesting
    @Volatile
    var lastUnderruns: Int = 0
        private set

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener(
            { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop()
            },
            Handler(Looper.getMainLooper()),
        )
        .build()

    // Headphones unplugged (or a Bluetooth headset gone): stop rather than drone out of the speaker.
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) stop()
        }
    }

    override fun play(rootHz: Float, sound: DroneSound, voicing: DroneVoicing, lower: Boolean) {
        require(rootHz in DroneSynth.MIN_ROOT_HZ..DroneSynth.MAX_ROOT_HZ) {
            "rootHz must be in ${DroneSynth.MIN_ROOT_HZ}..${DroneSynth.MAX_ROOT_HZ}, was $rootHz"
        }
        synchronized(lock) {
            pending = Request.Play(rootHz, sound, voicing, lower)
            playing.value = true
            if (active) return
            active = true
            holding.value = true
        }
        writer.execute(::session)
    }

    override fun stop() {
        synchronized(lock) {
            if (!active) return
            pending = Request.Stop
            playing.value = false
        }
    }

    private fun takeRequest(): Request? = synchronized(lock) { pending.also { pending = null } }

    /** Ends the session unless a request came in meanwhile. */
    private fun finishIfIdle(): Boolean = synchronized(lock) {
        if (pending != null) return false
        active = false
        holding.value = false
        true
    }

    private fun session() {
        var track: AudioTrack? = null
        var finished = false
        var focused = false
        var listening = false
        try {
            if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.i(TAG, "No audio focus (a call?): the drone stays silent")
                return
            }
            focused = true
            // Only the system sends it (a protected broadcast), which reaches a not-exported receiver too.
            val noisy = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(noisyReceiver, noisy, Context.RECEIVER_NOT_EXPORTED)
            } else {
                appContext.registerReceiver(noisyReceiver, noisy)
            }
            listening = true
            AudioOutput.showVolumeIfSilent(audioManager)
            val drone = synth ?: DroneSynth(sampleRate).also { synth = it }
            val opened = AudioOutput.openTrack(sampleRate, attributes, chunkFrames * BUFFERED_CHUNKS)
            track = opened
            opened.play()
            val buffer = FloatArray(chunkFrames)
            var framesWritten = 0L
            while (true) {
                when (val request = takeRequest()) {
                    is Request.Play -> drone.play(request.rootHz, request.sound, request.voicing, request.lower)
                    Request.Stop -> drone.release()
                    null -> Unit
                }
                val count = drone.render(buffer)
                if (count > 0) {
                    AudioOutput.write(opened, buffer, count)
                    framesWritten += count
                }
                if (count == buffer.size) continue
                // The fade has ended: let it be heard to the last sample before letting go.
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
        } catch (e: Exception) {
            // A track that can't be built or written (UnsupportedOperationException,
            // IllegalStateException). The drone fails quietly, its button back on ▶.
            Log.w(TAG, "Drone playback failed", e)
        } finally {
            track?.let {
                val underruns = runCatching { it.underrunCount }.getOrDefault(0)
                if (underruns > 0) Log.w(TAG, "The speaker ran dry $underruns times: the drone may have clicked")
                lastUnderruns = underruns
                AudioOutput.close(it)
            }
            if (listening) runCatching { appContext.unregisterReceiver(noisyReceiver) }
            if (focused) audioManager.abandonAudioFocusRequest(focusRequest)
            if (!finished) {
                // Failed or refused: the synth may still be mid-drone, so the next session starts afresh.
                synth = null
                synchronized(lock) {
                    active = false
                    pending = null
                    playing.value = false
                    holding.value = false
                }
            }
        }
    }

    /** Waits until the playback head has passed [frames], or a little longer than the buffer lasts. */
    private fun awaitPlayed(track: AudioTrack, frames: Long) {
        val bufferMillis = track.bufferSizeInFrames * 1000L / sampleRate
        val deadline = SystemClock.uptimeMillis() + 2 * bufferMillis + DRAIN_MARGIN_MILLIS
        // The head position is an unsigned 32-bit frame count.
        while ((track.playbackHeadPosition.toLong() and 0xFFFF_FFFFL) < frames &&
            SystemClock.uptimeMillis() < deadline
        ) {
            SystemClock.sleep(DRAIN_POLL_MILLIS)
        }
    }

    /** What [play] and [stop] ask the session for. */
    private sealed interface Request {
        data object Stop : Request
        class Play(val rootHz: Float, val sound: DroneSound, val voicing: DroneVoicing, val lower: Boolean) : Request
    }

    private companion object {
        const val TAG = "DronePlayer"

        const val CHUNKS_PER_SECOND = 100

        /** Chunks kept queued in the track: ≈ 120 ms against gaps, which is also how late a change starts. */
        const val BUFFERED_CHUNKS = 12

        const val DRAIN_POLL_MILLIS = 5L
        const val DRAIN_MARGIN_MILLIS = 300L

        const val WRITER_THREAD_NAME = "DronePlayer"
    }
}
