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
import io.github.klaboukes.twangle.core.dsp.metronome.ClickSound
import io.github.klaboukes.twangle.core.dsp.metronome.ClickTrack
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.dsp.metronome.SpeedTrainer
import io.github.klaboukes.twangle.core.dsp.metronome.Subdivision
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
 * The track exists only while the metronome plays. [start] launches a session on the player's own
 * thread at audio priority ([audioThreadDispatcher]; `AudioTrack.write` blocks); the session renders 10 ms chunks into one buffer and keeps
 * [BUFFERED_CHUNKS] chunks queued, enough that a busy moment on the phone doesn't starve the
 * speaker. Tempo, meter, sound, subdivision and stop requests reach it through fields it reads before every
 * chunk, so a change is heard about one buffer later (≈ 0.1 s) and the clicks stay on their samples.
 * The speed trainer (Phase 62) reaches the [ClickTrack] the same way, once per [setTrainer] or [start];
 * the track then climbs by itself on each downbeat, and while it climbs the wanted tempo isn't copied
 * to it (the climb rules until the trainer ends).
 * A sound not played yet is rendered there, on the writer thread, the first time (a few ms); the
 * [ClickTrack] itself, which renders the first sound's clicks, is made there with the first session,
 * not with the player (on the main thread; on the watch at every start).
 *
 * A preview ([previewBar]) is a session too: it renders one bar from beat 0, then silence until the
 * playback head has passed the bar's end, and ends by itself; it emits no [beats]. A new preview or
 * a [start] during one starts the click track over within the same session and track (the frames
 * already queued still play out first), so nothing waits for a track to close and open again.
 *
 * Silent ([setSilent], the watch's taps only): the click track renders as ever, its beats noted on
 * their samples, and the chunk is zeroed before it is written, so the track, its timestamps and the
 * [beats] run exactly as with the clicks (measured on the Galaxy Watch 5, strum-and-metronome.md).
 *
 * [beats]: the session notes each click as it renders it, works out from the output's timestamp
 * (`AudioTrack.getTimestamp`) when it will be heard, and emits it then from a timer on a thread of
 * its own at urgent-audio priority; before the first timestamp, when the playback head reaches it.
 * [upcomingBeats] gets each beat as soon as its moment is worked out, ahead of it, for the watch's
 * taps.
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
    private val writeDispatcher = audioThreadDispatcher(WRITER_THREAD_NAME)

    // The timers that tell each beat as it is heard: one thread for every player in the app ([beatThread]).
    private val beatDispatcher get() = beatThread

    private val lock = Any()

    // Guarded by lock: whether a session runs, a stop it hasn't taken yet, and the wanted settings.
    private var active = false
    private var stopRequested = false
    private var wantedBpm = ClickTrack.DEFAULT_BPM
    private var wantedMeter = Meter.DEFAULT
    private var wantedSound = ClickSound.DEFAULT
    private var wantedSubdivision = Subdivision.DEFAULT
    private var wantedSilent = false

    // Guarded by lock: the speed trainer wanted, and a count bumped whenever one is started or ended,
    // so the session hands it to the click track once each time.
    private var wantedTrainer: SpeedTrainer? = null
    private var trainerStarts = 0

    // Guarded by lock: whether the session plays one bar (a preview) rather than the metronome, and
    // a count bumped whenever it must start over from beat 0 (a new preview, or Start during one).
    private var wantedPreview = false
    private var restarts = 0

    private val playing = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = playing.asStateFlow()

    private val previewing = MutableStateFlow(false)
    override val isPreviewing: StateFlow<Boolean> = previewing.asStateFlow()

    private val beatFlow = MutableSharedFlow<MetronomeBeat>(
        extraBufferCapacity = BEAT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val beats: SharedFlow<MetronomeBeat> = beatFlow.asSharedFlow()

    private val upcomingFlow = MutableSharedFlow<MetronomeBeat>(
        extraBufferCapacity = BEAT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val upcomingBeats: SharedFlow<MetronomeBeat> = upcomingFlow.asSharedFlow()

    // Everything below is used only on the writer thread. The click track is made with the first session.
    private lateinit var clickTrack: ClickTrack
    private val timestamp = AudioTimestamp()

    // Beats rendered but not yet heard, oldest first: a ring of PENDING_BEATS.
    private val pendingIndex = LongArray(PENDING_BEATS)
    private val pendingBeatInBar = IntArray(PENDING_BEATS)
    private val pendingAccent = Array(PENDING_BEATS) { BeatAccent.PLAIN }
    private val pendingFrame = LongArray(PENDING_BEATS)
    private val pendingSound = Array(PENDING_BEATS) { ClickSound.DEFAULT }
    private val pendingBpm = IntArray(PENDING_BEATS)
    private val pendingTrainerBar = LongArray(PENDING_BEATS)
    private val pendingClicks = IntArray(PENDING_BEATS)
    private var pendingFirst = 0
    private var pendingCount = 0

    // The session: whether it plays a preview, the restarts it has taken, the track frame the click
    // track's sample 0 was written at, frames written to the track, and where a preview's bar ends.
    private var preview = false
    private var silent = false
    private var seenRestarts = 0
    private var seenTrainerStarts = 0
    private var clickOffset = 0L
    private var framesWritten = 0L
    private var barEnd = Long.MAX_VALUE

    private val noteBeat = BeatListener { index, beatInBar, accent, frame ->
        if (pendingCount == PENDING_BEATS) dropOldestPending()
        val slot = (pendingFirst + pendingCount) % PENDING_BEATS
        pendingIndex[slot] = index
        pendingBeatInBar[slot] = beatInBar
        pendingAccent[slot] = accent
        pendingFrame[slot] = clickOffset + frame
        pendingSound[slot] = clickTrack.sound
        // The tempo from this beat on: a trainer's step on its downbeat is taken before onBeat.
        pendingBpm[slot] = clickTrack.bpm
        pendingTrainerBar[slot] = if (clickTrack.trainer != null) clickTrack.trainerBar else -1L
        // The clicks this beat is split into, as the track sets them right after onBeat.
        pendingClicks[slot] = clickTrack.subdivision.within(clickTrack.meter).clicksPerBeat
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

    override fun start(bpm: Int, meter: Meter, sound: ClickSound, subdivision: Subdivision, trainer: SpeedTrainer?) {
        synchronized(lock) {
            wantedBpm = ClickTrack.clampBpm(trainer?.startBpm ?: bpm)
            wantedMeter = meter
            wantedSound = sound
            wantedSubdivision = subdivision
            wantedTrainer = trainer
            trainerStarts++
            stopRequested = false
            playing.value = true
            if (active) {
                if (wantedPreview) {
                    // The preview's session goes on as the metronome, from beat 0.
                    wantedPreview = false
                    restarts++
                    previewing.value = false
                }
                return
            }
            active = true
            wantedPreview = false
        }
        scope.launch(writeDispatcher) { session() }
    }

    override fun previewBar(bpm: Int, meter: Meter, sound: ClickSound, subdivision: Subdivision) {
        synchronized(lock) {
            // Playing (and not about to stop): only the sound and subdivision change.
            if (active && !wantedPreview && !stopRequested) {
                wantedSound = sound
                wantedSubdivision = subdivision
                return
            }
            wantedBpm = ClickTrack.clampBpm(bpm)
            wantedMeter = meter
            wantedSound = sound
            wantedSubdivision = subdivision
            wantedTrainer = null
            trainerStarts++
            stopRequested = false
            wantedPreview = true
            playing.value = false
            previewing.value = true
            if (active) {
                restarts++
                return
            }
            active = true
        }
        scope.launch(writeDispatcher) { session() }
    }

    override fun setTempo(bpm: Int) {
        synchronized(lock) { if (active && !wantedPreview) wantedBpm = ClickTrack.clampBpm(bpm) }
    }

    override fun setTrainer(trainer: SpeedTrainer?) {
        synchronized(lock) {
            if (active && !wantedPreview) {
                wantedTrainer = trainer
                trainerStarts++
                // Ended: the tempo stays where the climb got to, until a setTempo.
                if (trainer == null) wantedBpm = KEEP_TEMPO
            }
        }
    }

    override fun setMeter(meter: Meter) {
        synchronized(lock) { if (active && !wantedPreview) wantedMeter = meter }
    }

    override fun setSound(sound: ClickSound) {
        synchronized(lock) { if (active && !wantedPreview) wantedSound = sound }
    }

    override fun setSubdivision(subdivision: Subdivision) {
        synchronized(lock) { if (active && !wantedPreview) wantedSubdivision = subdivision }
    }

    override fun setSilent(silent: Boolean) {
        synchronized(lock) { wantedSilent = silent }
    }

    override fun stop() {
        synchronized(lock) { if (active) stopRequested = true }
    }

    /**
     * Takes the latest requests: true if the session must end, for a stop or because the preview
     * was heard to its end ([previewHeard]) and nothing new came (then it is marked ended already, so
     * a [start] from now on launches a new one); else copies the wanted tempo, meter, sound and subdivision to the
     * track, and starts it over from beat 0 for a new preview or a start during one.
     */
    private fun takeRequests(previewHeard: Boolean): Boolean {
        val bpm: Int
        val meter: Meter
        val sound: ClickSound
        val subdivision: Subdivision
        val trainer: SpeedTrainer?
        val newTrainer: Boolean
        val restart: Boolean
        synchronized(lock) {
            if (stopRequested || (previewHeard && wantedPreview && restarts == seenRestarts)) {
                stopRequested = false
                active = false
                wantedPreview = false
                playing.value = false
                previewing.value = false
                return true
            }
            bpm = wantedBpm
            meter = wantedMeter
            sound = wantedSound
            subdivision = wantedSubdivision
            trainer = wantedTrainer
            newTrainer = trainerStarts != seenTrainerStarts
            seenTrainerStarts = trainerStarts
            preview = wantedPreview
            silent = wantedSilent
            restart = restarts != seenRestarts
            seenRestarts = restarts
        }
        if (newTrainer) clickTrack.startTrainer(trainer)
        // While the trainer climbs it sets the tempo; the wanted one counts again once it ends.
        if (clickTrack.trainer == null && bpm != KEEP_TEMPO) clickTrack.bpm = bpm
        clickTrack.meter = meter
        clickTrack.sound = sound // A sound not played yet is rendered here, once.
        clickTrack.subdivision = subdivision
        if (restart) startOver()
        return false
    }

    /** The click track starts over at beat 0 with the next frame written to the track. */
    private fun startOver() {
        clickTrack.reset()
        pendingCount = 0
        clickOffset = framesWritten
        barEnd = if (preview) {
            clickOffset + clickTrack.frameOfBeat(clickTrack.meter.beatsPerBar.toLong())
        } else {
            Long.MAX_VALUE
        }
    }

    private suspend fun session() {
        var track: AudioTrack? = null
        var listening = false
        var ended = false
        // Timers that emit beats at the moment they are heard; cancelled with the session.
        val beatScope = CoroutineScope(beatDispatcher + Job())
        try {
            if (!::clickTrack.isInitialized) clickTrack = ClickTrack(sampleRate)
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
            // Silent clicks need no volume; a preview always sounds.
            if (synchronized(lock) { wantedPreview || !wantedSilent }) AudioOutput.showVolumeIfSilent(audioManager)
            framesWritten = 0L
            // The first requests always start the click track over, at frame 0.
            seenRestarts = synchronized(lock) { restarts } - 1
            val opened = AudioOutput.openTrack(sampleRate, attributes, chunkFrames * BUFFERED_CHUNKS)
            track = opened
            opened.play()
            val buffer = FloatArray(chunkFrames)
            var chunk = 0
            var previewHeard = false
            while (true) {
                currentCoroutineContext().ensureActive()
                if (++chunk % MODE_CHECK_CHUNKS == 0 && isCallMode(audioManager.mode)) {
                    Log.i(TAG, "A call: the metronome stops")
                    stop()
                }
                if (takeRequests(previewHeard)) {
                    ended = true
                    break
                }
                if (preview) {
                    // The bar, then silence: the beat after it never sounds.
                    val bar = (barEnd - framesWritten).coerceIn(0L, chunkFrames.toLong()).toInt()
                    if (bar > 0) clickTrack.render(buffer, 0, bar)
                    if (bar < chunkFrames) buffer.fill(0f, bar, chunkFrames)
                } else {
                    clickTrack.render(buffer, listener = noteBeat)
                    // Silent: the beats were noted on their samples; silence plays in their place.
                    if (silent) buffer.fill(0f)
                }
                AudioOutput.write(opened, buffer, buffer.size)
                framesWritten += chunkFrames
                if (preview) {
                    previewHeard = framesWritten >= barEnd &&
                        (playedFrames(opened) >= barEnd || framesWritten >= barEnd + chunkFrames * DRAIN_CHUNKS)
                } else {
                    scheduleBeats(opened, beatScope)
                }
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
                    wantedPreview = false
                    playing.value = false
                    previewing.value = false
                }
            }
        }
    }

    /**
     * Hands the pending beats on. Once the output has a timestamp, each beat's moment is known well
     * ahead (it was rendered about one buffer earlier), so it is emitted by a timer at that moment:
     * the writer thread itself only wakes when the speaker takes a block of samples (every 10–40 ms).
     * Before the first timestamp, a beat is emitted as soon as the playback head reaches it. Either
     * way it goes to [upcomingBeats] at once.
     */
    private fun scheduleBeats(track: AudioTrack, beatScope: CoroutineScope) {
        if (pendingCount == 0) return
        val now = System.nanoTime()
        val stamped = track.getTimestamp(timestamp)
        val head = playedFrames(track)
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
            upcomingFlow.tryEmit(beat)
            // From now, not from before the timestamp calls above, which took several ms on the watch.
            val wait = beat.heardAtNanos - System.nanoTime()
            if (wait <= EARLY_NANOS) {
                beatFlow.tryEmit(beat)
            } else {
                // The timer is set at once, from here: a hop to the beat thread first would make
                // every beat late by however long that thread took to wake.
                beatScope.launch(start = CoroutineStart.UNDISPATCHED) {
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
        pendingSound[pendingFirst],
        pendingBpm[pendingFirst],
        pendingTrainerBar[pendingFirst],
        pendingClicks[pendingFirst],
    )

    /** Frames the track has played since it started: its playback head, an unsigned 32-bit count. */
    private fun playedFrames(track: AudioTrack): Long = track.playbackHeadPosition.toLong() and 0xFFFF_FFFFL

    private fun dropOldestPending() {
        pendingFirst = (pendingFirst + 1) % PENDING_BEATS
        pendingCount--
    }

    private companion object {
        const val TAG = "MetronomePlayer"
        const val WRITER_THREAD_NAME = "MetronomePlayer"

        /**
         * The thread the beat timers of every player run on, at urgent-audio priority ([urgentAudioDispatcher]): made
         * with the first session and kept, asleep between beats. Shared, since the watch makes a new player each time
         * its app is opened (Phase 89: one thread was left behind with each).
         */
        val beatThread by lazy { urgentAudioDispatcher(BEAT_THREAD_NAME) }

        /** The wanted tempo once a trainer ended: whatever the click track climbed to. */
        const val KEEP_TEMPO = -1

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

        /**
         * A preview ends once the playback head has passed its bar, or at the latest this many
         * chunks of silence after it (a head that doesn't move): the queue and then some.
         */
        const val DRAIN_CHUNKS = BUFFERED_CHUNKS + 8
    }
}

/**
 * Whether the phone's audio [mode] (`AudioManager.getMode()`) means a call: ringing, in a phone call,
 * in a voice or video chat (`MODE_IN_COMMUNICATION`), or any call mode newer Android adds. Only
 * `MODE_NORMAL` is free for the metronome.
 */
internal fun isCallMode(mode: Int): Boolean = mode != AudioManager.MODE_NORMAL

/** The name of the one thread that tells every player's beats as they are heard. */
internal const val BEAT_THREAD_NAME = "MetronomeBeats"
