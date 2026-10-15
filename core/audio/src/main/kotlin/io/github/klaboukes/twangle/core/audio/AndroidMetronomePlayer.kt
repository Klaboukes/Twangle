package io.github.klaboukes.twangle.core.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import io.github.klaboukes.twangle.core.dsp.metronome.BeatAccent
import io.github.klaboukes.twangle.core.dsp.metronome.BeatListener
import io.github.klaboukes.twangle.core.dsp.metronome.ClickTrack
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [MetronomePlayer] backed by a streaming `android.media.AudioTrack`: mono `ENCODING_PCM_FLOAT` at
 * the phone's native output rate, `USAGE_MEDIA` / `CONTENT_TYPE_SONIFICATION`, so the media volume
 * sets its loudness. The samples come from a [ClickTrack], which puts every click on its exact
 * sample; the track only has to play the stream without gaps, so the beat can't wobble.
 *
 * The track exists only while the metronome plays. [start] launches a session on a dedicated single
 * thread (`AudioTrack.write` blocks); the session renders 10 ms chunks into one buffer and keeps
 * [BUFFERED_CHUNKS] chunks queued, enough that a busy moment on the phone doesn't starve the
 * speaker. Tempo, meter and stop requests reach it through fields it reads before every chunk, so
 * a change is heard about one buffer later (≈ 0.1 s) and the clicks stay on their samples.
 *
 * [beats]: the session notes each click as it renders it, works out from the output's timestamp
 * (`AudioTrack.getTimestamp`) when it will be heard, and emits it then from a timer; before the
 * first timestamp, when the playback head reaches it.
 *
 * No audio focus: the clicks play over another app's music (a backing track to play along with),
 * which keeps playing at its own volume, and another app taking the audio doesn't stop them. A call
 * does: the session checks the phone's audio mode ([isCallMode]: ringing, in a call or a voice chat)
 * every [MODE_CHECK_CHUNKS] chunks and stops, and doesn't start during one. Headphones coming
 * unplugged (`ACTION_AUDIO_BECOMING_NOISY`, Bluetooth ones too) stop it, so it never carries on out
 * of the speaker. A failing track ends the session quietly (logged).
 *
 * A [stop] directly followed by [start], before the session has seen the stop, keeps the clicks
 * going with the new tempo and meter rather than starting over.
 */
class AndroidMetronomePlayer(context: Context) : MetronomePlayer {

    private val appContext = context.applicationContext
    private val audioManager = context.getSystemService(AudioManager::class.java)

    /** Sample rate of the clicks: the phone's native output rate, 48 kHz if it reports nothing usable. */
    val sampleRate: Int = AudioOutput.nativeSampleRate()

    private val chunkFrames = sampleRate / CHUNKS_PER_SECOND

    private val scope = CoroutineScope(SupervisorJob())
    private val writeDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val lock = Any()

    // Guarded by lock: whether a session runs, a stop it hasn't taken yet, and the wanted settings.
    private var active = false
    private var stopRequested = false
    private var wantedBpm = ClickTrack.DEFAULT_BPM
    private var wantedMeter = Meter.DEFAULT

    private val playing = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = playing.asStateFlow()

    private val beatFlow = MutableSharedFlow<MetronomeBeat>(
        extraBufferCapacity = BEAT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val beats: SharedFlow<MetronomeBeat> = beatFlow.asSharedFlow()

    // Everything below is used only on the writer thread.
    private val clickTrack = ClickTrack(sampleRate)
    private val timestamp = AudioTimestamp()

    // Beats rendered but not yet heard, oldest first: a ring of PENDING_BEATS.
    private val pendingIndex = LongArray(PENDING_BEATS)
    private val pendingBeatInBar = IntArray(PENDING_BEATS)
    private val pendingAccent = Array(PENDING_BEATS) { BeatAccent.PLAIN }
    private val pendingFrame = LongArray(PENDING_BEATS)
    private var pendingFirst = 0
    private var pendingCount = 0

    private val noteBeat = BeatListener { index, beatInBar, accent, frame ->
        if (pendingCount == PENDING_BEATS) dropOldestPending()
        val slot = (pendingFirst + pendingCount) % PENDING_BEATS
        pendingIndex[slot] = index
        pendingBeatInBar[slot] = beatInBar
        pendingAccent[slot] = accent
        pendingFrame[slot] = frame
        pendingCount++
    }

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    // Headphones unplugged (or a Bluetooth headset gone): stop rather than click out of the speaker.
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) stop()
        }
    }

    override fun start(bpm: Int, meter: Meter) {
        synchronized(lock) {
            wantedBpm = ClickTrack.clampBpm(bpm)
            wantedMeter = meter
            stopRequested = false
            if (active) return
            active = true
            playing.value = true
        }
        scope.launch(writeDispatcher) { session() }
    }

    override fun setTempo(bpm: Int) {
        synchronized(lock) { if (active) wantedBpm = ClickTrack.clampBpm(bpm) }
    }

    override fun setMeter(meter: Meter) {
        synchronized(lock) { if (active) wantedMeter = meter }
    }

    override fun stop() {
        synchronized(lock) { if (active) stopRequested = true }
    }

    /**
     * Takes the latest requests: true if the session must end (then it is marked ended already, so
     * a [start] from now on launches a new one), else copies the wanted tempo and meter to the track.
     */
    private fun takeRequests(): Boolean {
        val bpm: Int
        val meter: Meter
        synchronized(lock) {
            if (stopRequested) {
                stopRequested = false
                active = false
                playing.value = false
                return true
            }
            bpm = wantedBpm
            meter = wantedMeter
        }
        clickTrack.bpm = bpm
        clickTrack.meter = meter
        return false
    }

    private suspend fun session() {
        var track: AudioTrack? = null
        var listening = false
        var ended = false
        // Timers that emit beats at the moment they are heard; cancelled with the session.
        val beatScope = CoroutineScope(Dispatchers.Default + Job())
        try {
            if (isCallMode(audioManager.mode)) {
                Log.i(TAG, "A call: the metronome stays silent")
                return
            }
            // Only the system sends it (a protected broadcast), which reaches a not-exported receiver too.
            val noisy = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(noisyReceiver, noisy, Context.RECEIVER_NOT_EXPORTED)
            } else {
                appContext.registerReceiver(noisyReceiver, noisy)
            }
            listening = true
            AudioOutput.showVolumeIfSilent(audioManager)
            clickTrack.reset()
            pendingCount = 0
            val opened = AudioOutput.openTrack(sampleRate, attributes, chunkFrames * BUFFERED_CHUNKS)
            track = opened
            opened.play()
            val buffer = FloatArray(chunkFrames)
            var chunk = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                if (++chunk % MODE_CHECK_CHUNKS == 0 && isCallMode(audioManager.mode)) {
                    Log.i(TAG, "A call: the metronome stops")
                    stop()
                }
                if (takeRequests()) {
                    ended = true
                    break
                }
                clickTrack.render(buffer, listener = noteBeat)
                AudioOutput.write(opened, buffer, buffer.size)
                scheduleBeats(opened, beatScope)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A track that can't be built or written (UnsupportedOperationException, IllegalStateException).
            Log.w(TAG, "Metronome playback failed", e)
        } finally {
            beatScope.cancel()
            track?.let {
                val underruns = runCatching { it.underrunCount }.getOrDefault(0)
                if (underruns > 0) Log.w(TAG, "The speaker ran dry $underruns times: the beat may have stumbled")
                AudioOutput.close(it)
            }
            if (listening) runCatching { appContext.unregisterReceiver(noisyReceiver) }
            if (!ended) {
                synchronized(lock) {
                    active = false
                    stopRequested = false
                    playing.value = false
                }
            }
        }
    }

    /**
     * Hands the pending beats on. Once the output has a timestamp, each beat's moment is known well
     * ahead (it was rendered about one buffer earlier), so it is emitted by a timer at that moment:
     * the writer thread itself only wakes when the speaker takes a block of samples (every 10–40 ms).
     * Before the first timestamp, a beat is emitted as soon as the playback head reaches it.
     */
    private fun scheduleBeats(track: AudioTrack, beatScope: CoroutineScope) {
        if (pendingCount == 0) return
        val now = System.nanoTime()
        val stamped = track.getTimestamp(timestamp)
        val head = track.playbackHeadPosition.toLong() and 0xFFFF_FFFFL // an unsigned 32-bit count
        while (pendingCount > 0) {
            val frame = pendingFrame[pendingFirst]
            val beat = if (stamped) {
                heardBeat(timestamp.nanoTime + (frame - timestamp.framePosition) * NANOS_PER_SECOND / sampleRate)
            } else {
                val heardAt = now + (frame - head) * NANOS_PER_SECOND / sampleRate
                if (heardAt > now + EARLY_NANOS) return
                heardBeat(heardAt)
            }
            dropOldestPending()
            val wait = beat.heardAtNanos - now
            if (wait <= EARLY_NANOS) {
                beatFlow.tryEmit(beat)
            } else {
                beatScope.launch {
                    delay(wait / NANOS_PER_MILLI)
                    beatFlow.tryEmit(beat)
                }
            }
        }
    }

    private fun heardBeat(heardAtNanos: Long) = MetronomeBeat(
        pendingIndex[pendingFirst],
        pendingBeatInBar[pendingFirst],
        pendingAccent[pendingFirst],
        heardAtNanos,
    )

    private fun dropOldestPending() {
        pendingFirst = (pendingFirst + 1) % PENDING_BEATS
        pendingCount--
    }

    private companion object {
        const val TAG = "MetronomePlayer"

        const val CHUNKS_PER_SECOND = 100

        /** Chunks kept queued in the track: ≈ 120 ms against gaps, which is also how late a change is heard. */
        const val BUFFERED_CHUNKS = 12

        /** Beats in flight between rendering and hearing; at most one buffer's worth, so a few. */
        const val PENDING_BEATS = 16
        const val BEAT_BUFFER = 16

        const val NANOS_PER_SECOND = 1_000_000_000L
        const val NANOS_PER_MILLI = 1_000_000L

        /** A beat due within half a chunk is emitted at once. */
        const val EARLY_NANOS = NANOS_PER_SECOND / CHUNKS_PER_SECOND / 2

        /** How often the session looks for a call: every 25 chunks, 4 times a second. */
        const val MODE_CHECK_CHUNKS = 25
    }
}

/**
 * Whether the phone's audio [mode] (`AudioManager.getMode()`) means a call: ringing, in a phone call,
 * in a voice or video chat (`MODE_IN_COMMUNICATION`), or any call mode newer Android adds. Only
 * `MODE_NORMAL` is free for the metronome.
 */
internal fun isCallMode(mode: Int): Boolean = mode != AudioManager.MODE_NORMAL
