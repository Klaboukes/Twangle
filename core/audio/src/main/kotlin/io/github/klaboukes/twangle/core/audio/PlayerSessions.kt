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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the reference tone ([AndroidTonePlayer]) and the drone ([AndroidDronePlayer]) share, so a
 * fix lands once: the requests, the session that plays them, its padding at the end, audio focus
 * and the volume panel.
 *
 * **Requests:** the player's `play` and `stop` only leave the latest request ([R]) in a field under
 * a lock ([request]) and return; the session takes it before every 10 ms chunk ([Chunks.render]),
 * on the player's own writer thread at audio priority ([audioThreadExecutor], named [name];
 * `AudioTrack.write` blocks). The player's sound (a synth, not thread-safe) is touched only there.
 *
 * **A session** exists only while there is sound: a mono float `AudioTrack` at the phone's native
 * output rate, `USAGE_MEDIA` / `CONTENT_TYPE_MUSIC` (the media volume sets its loudness), its buffer
 * at least [bufferedChunks] chunks. It renders into one buffer allocated per session, so playing
 * creates no garbage. Once a chunk comes back empty the sound has ended: the session pads the track
 * with one buffer of silence (so a very short sound still starts playing), waits until the last
 * sample has been played, and ends unless a request came meanwhile; [active] then turns false. A
 * failing track ends the session the same way, quietly (logged, with any underruns).
 *
 * **Audio focus:** each session holds `AUDIOFOCUS_GAIN_TRANSIENT`, so another app's music pauses
 * while the sound plays and resumes after it; a loss (a call, another app's sound that won't share)
 * calls [stop], a loss that lets it duck (a notification) is left to the system, which lowers it by
 * itself, and refused focus (during a call) means no sound. With [stopsWhenNoisy], headphones coming
 * unplugged (`ACTION_AUDIO_BECOMING_NOISY`, Bluetooth ones too) call [stop] as well, so it never
 * carries on out of the speaker. At media volume 0 a session shows the phone's own volume panel.
 */
internal class PlayerSessions<R : Any>(
    context: Context,
    private val name: String,
    bufferedChunks: Int,
    private val stopsWhenNoisy: Boolean,
    private val stop: () -> Unit,
    private val startSession: () -> Chunks<R>,
    private val whenFailed: () -> Unit = {},
) {

    /** One session's sound, made by `startSession` on the writer thread as the session begins. */
    fun interface Chunks<R> {
        /**
         * Hands the session's sound [request] (null when nothing new was asked) and fills [buffer]
         * from index 0 with its next samples, returning how many: 0 once it has ended.
         */
        fun render(request: R?, buffer: FloatArray): Int
    }

    private val appContext = context.applicationContext
    private val audioManager = context.getSystemService(AudioManager::class.java)

    /** The sound's sample rate: the phone's native output rate, 48 kHz if it reports nothing usable. */
    val sampleRate: Int = AudioOutput.nativeSampleRate()

    private val chunkFrames = sampleRate / CHUNKS_PER_SECOND
    private val trackFrames = chunkFrames * bufferedChunks

    private val writer = audioThreadExecutor(name)

    private val lock = Any()

    // Guarded by lock: the latest request the session hasn't taken yet (null for none), and whether
    // a session is running.
    private var pending: R? = null
    private var running = false

    private val activeFlow = MutableStateFlow(false)

    /** True while a session runs: from the request that starts it until its last sample has been heard. */
    val active: StateFlow<Boolean> = activeFlow.asStateFlow()

    /** Times the speaker ran dry in the last session that ended (each would be heard as a click). */
    @Volatile
    var lastUnderruns: Int = 0
        private set

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    // Asked for when a session starts, given up when it ends.
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener(
            { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop()
            },
            Handler(Looper.getMainLooper()),
        )
        .build()

    // Headphones unplugged (or a Bluetooth headset gone).
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) stop()
        }
    }

    /**
     * Leaves [value] for the running session, or, with none running, starts one for it when
     * [startsSession] (a request that doesn't is dropped then). [alsoUnderLock] runs under the same
     * lock when the request is taken, for state that must change with it.
     */
    fun request(value: R, startsSession: Boolean, alsoUnderLock: () -> Unit = {}) {
        synchronized(lock) {
            if (running) {
                pending = value
                alsoUnderLock()
                return
            }
            if (!startsSession) return
            pending = value
            running = true
            activeFlow.value = true
            alsoUnderLock()
        }
        writer.execute(::session)
    }

    private fun takeRequest(): R? = synchronized(lock) { pending.also { pending = null } }

    /** Ends the session unless a request came in meanwhile. */
    private fun finishIfIdle(): Boolean = synchronized(lock) {
        if (pending != null) return false
        running = false
        activeFlow.value = false
        true
    }

    private fun session() {
        var track: AudioTrack? = null
        var finished = false
        var focused = false
        var listening = false
        try {
            if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.i(name, "No audio focus (a call?): no sound")
                return
            }
            focused = true
            if (stopsWhenNoisy) {
                // Only the system sends it (a protected broadcast), which reaches a not-exported receiver too.
                val noisy = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appContext.registerReceiver(noisyReceiver, noisy, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    appContext.registerReceiver(noisyReceiver, noisy)
                }
                listening = true
            }
            AudioOutput.showVolumeIfSilent(audioManager)
            val sound = startSession()
            val opened = AudioOutput.openTrack(sampleRate, attributes, trackFrames)
            track = opened
            opened.play()
            val buffer = FloatArray(chunkFrames)
            var framesWritten = 0L
            while (true) {
                val count = sound.render(takeRequest(), buffer)
                if (count > 0) {
                    AudioOutput.write(opened, buffer, count)
                    framesWritten += count
                    continue
                }
                // The sound has ended: let it be heard to the last sample before letting go.
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
            // IllegalStateException). The sound is a convenience, so it fails quietly.
            Log.w(name, "Playback failed", e)
        } finally {
            track?.let {
                val underruns = runCatching { it.underrunCount }.getOrDefault(0)
                if (underruns > 0) Log.w(name, "The speaker ran dry $underruns times: it may have clicked")
                lastUnderruns = underruns
                AudioOutput.close(it)
            }
            if (listening) runCatching { appContext.unregisterReceiver(noisyReceiver) }
            if (focused) audioManager.abandonAudioFocusRequest(focusRequest)
            if (!finished) {
                synchronized(lock) {
                    running = false
                    pending = null
                    activeFlow.value = false
                    whenFailed()
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

    private companion object {
        const val CHUNKS_PER_SECOND = 100
        const val DRAIN_POLL_MILLIS = 5L
        const val DRAIN_MARGIN_MILLIS = 300L
    }
}
