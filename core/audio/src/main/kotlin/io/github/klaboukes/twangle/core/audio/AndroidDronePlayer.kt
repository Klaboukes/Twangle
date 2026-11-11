package io.github.klaboukes.twangle.core.audio

import android.content.Context
import androidx.annotation.VisibleForTesting
import io.github.klaboukes.twangle.core.dsp.drone.DroneSound
import io.github.klaboukes.twangle.core.dsp.drone.DroneSynth
import io.github.klaboukes.twangle.core.dsp.drone.DroneVoicing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [DronePlayer] backed by a streaming `android.media.AudioTrack`, through [PlayerSessions] as the
 * reference tone ([AndroidTonePlayer]): mono `ENCODING_PCM_FLOAT` at the phone's native output rate,
 * `USAGE_MEDIA` / `CONTENT_TYPE_MUSIC`, so the media volume sets its loudness. The samples come from
 * one [DroneSynth], made on the first drone and kept.
 *
 * The track exists only while the drone sounds. [play] starts a session on the player's own writer
 * thread at audio priority, which keeps [BUFFERED_CHUNKS] queued (≈ 120 ms, against a busy moment: a
 * gap would click). `DroneSynth` is not thread-safe, so [play] and [stop] only leave the latest
 * request, which the session takes before every chunk and hands to the synth there. After [stop]
 * the synth fades out ([DroneSynth.STOP_FADE_SECONDS]); once its last sample has been heard the
 * session releases the track and lets the speaker and audio focus go. A [play] during that fade or
 * wait brings the drone back in the same session and track.
 *
 * **Audio focus:** each session holds `AUDIOFOCUS_GAIN_TRANSIENT`, as the tone: a drone is for
 * playing against on its own, so another app's music pauses, and resumes when the drone ends.
 * Losing focus stops the drone, refused focus (during a call) means no drone. **Headphones coming
 * unplugged** stop it, as the metronome, so it never carries on out of the speaker. With the media
 * volume at 0 a session shows the phone's own volume panel. A failing track ends the session
 * quietly (logged).
 */
class AndroidDronePlayer(context: Context) : DronePlayer {

    private val sessions = PlayerSessions<Request>(
        context = context,
        name = WRITER_THREAD_NAME,
        bufferedChunks = BUFFERED_CHUNKS,
        stopsWhenNoisy = true,
        stop = ::stop,
        startSession = ::droneSession,
        whenFailed = {
            // Failed or refused: the synth may still be mid-drone, so the next session starts afresh.
            synth = null
            playing.value = false
        },
    )

    /** Sample rate of the drone: the phone's native output rate, 48 kHz if it reports nothing usable. */
    val sampleRate: Int = sessions.sampleRate

    // Used only on the writer thread, made on the first drone.
    private var synth: DroneSynth? = null

    // Changed only together with a request, under the sessions' lock.
    private val playing = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = playing.asStateFlow()

    /** True while a session holds an audio track (from [play] until the fade after [stop] has been heard). */
    val holdsSpeaker: StateFlow<Boolean> = sessions.active

    /** Times the speaker ran dry in the last session that ended (each would be heard as a click). */
    @get:VisibleForTesting
    val lastUnderruns: Int get() = sessions.lastUnderruns

    override fun play(rootHz: Float, sound: DroneSound, voicing: DroneVoicing, lower: Boolean) {
        require(rootHz in DroneSynth.MIN_ROOT_HZ..DroneSynth.MAX_ROOT_HZ) {
            "rootHz must be in ${DroneSynth.MIN_ROOT_HZ}..${DroneSynth.MAX_ROOT_HZ}, was $rootHz"
        }
        sessions.request(Request.Play(rootHz, sound, voicing, lower), startsSession = true) { playing.value = true }
    }

    /** Fades the drone out; with none, does nothing. */
    override fun stop() {
        sessions.request(Request.Stop, startsSession = false) { playing.value = false }
    }

    /** A session's sound: the one synth (made on the first drone), handed each request as it comes. */
    private fun droneSession(): PlayerSessions.Chunks<Request> {
        val drone = synth ?: DroneSynth(sampleRate).also { synth = it }
        return PlayerSessions.Chunks { request, buffer ->
            when (request) {
                is Request.Play -> drone.play(request.rootHz, request.sound, request.voicing, request.lower)
                Request.Stop -> drone.release()
                null -> Unit
            }
            drone.render(buffer)
        }
    }

    /** What [play] and [stop] ask the session for. */
    private sealed interface Request {
        data object Stop : Request
        class Play(val rootHz: Float, val sound: DroneSound, val voicing: DroneVoicing, val lower: Boolean) : Request
    }

    private companion object {
        /** Chunks kept queued in the track: ≈ 120 ms against gaps, which is also how late a change starts. */
        const val BUFFERED_CHUNKS = 12

        const val WRITER_THREAD_NAME = "DronePlayer"
    }
}
