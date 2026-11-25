package io.github.klaboukes.twangle.core.audio

import android.content.Context
import android.media.AudioAttributes
import io.github.klaboukes.twangle.core.dsp.guide.GuidePulse
import io.github.klaboukes.twangle.core.dsp.guide.GuideSounds
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [GuideSoundPlayer] through a streaming `AudioTrack`, by the same [PlayerSessions] as the reference tone and the drone:
 * on its own writer thread at audio priority ([audioThreadExecutor]), mono float at the phone's native rate,
 * `USAGE_MEDIA` so the media volume sets its loudness, as the reference tone's.
 *
 * Unlike the tone it asks for **no audio focus**: its sounds come up to three times a second, and another app's music
 * would pause and resume with each. (A screen reader's voice ducks it as it ducks any media.) At media volume 0 it shows
 * the phone's volume panel once after each [stop] (once a visit to the tuner), not with every sound.
 *
 * The sounds are made once, on the writer thread, the first time each is asked for (the in-tune bell again when its
 * length changes), and kept: playing one copies from the kept array into the session's buffer, allocating nothing. A new sound waits for
 * the one playing to end (the tuner never asks for one while it plays: it waits until [isSounding] is false and it has
 * heard the string again); [stop] fades the one playing out over [STOP_FADE_SECONDS].
 */
class AndroidGuideSoundPlayer(context: Context) : GuideSoundPlayer {

    /** Whether the volume panel may show at the next session's start: once after each [stop]. */
    private val volumeUnchecked = AtomicBoolean(true)

    private val sessions = PlayerSessions<Request>(
        context = context,
        name = WRITER_THREAD_NAME,
        bufferedChunks = BUFFERED_CHUNKS,
        stopsWhenNoisy = false,
        stop = ::stop,
        startSession = { GuideSession() },
        holdsFocus = false,
        showsVolume = { volumeUnchecked.getAndSet(false) },
        contentType = AudioAttributes.CONTENT_TYPE_SONIFICATION,
    )

    private val sampleRate: Int = sessions.sampleRate

    // Used only on the writer thread: the sounds made so far, the bell at the length last asked for.
    private var sharp: FloatArray? = null
    private var flat: FloatArray? = null
    private var bell: FloatArray? = null
    private var bellSeconds = 0f

    /** The last in-tune request, reused while its length stays, so playing the bell allocates nothing. */
    @Volatile
    private var inTuneRequest = Request.Play(GuidePulse.IN_TUNE, GuideSounds.SHORT_IN_TUNE_SECONDS)

    override val isSounding: StateFlow<Boolean> = sessions.active

    override fun play(pulse: GuidePulse, inTuneSeconds: Float) {
        val request = when (pulse) {
            GuidePulse.NONE -> return
            GuidePulse.LOOSEN -> Request.SHARP
            GuidePulse.TIGHTEN -> Request.FLAT
            GuidePulse.IN_TUNE -> inTuneRequest.takeIf { it.inTuneSeconds == inTuneSeconds }
                ?: Request.Play(GuidePulse.IN_TUNE, inTuneSeconds).also { inTuneRequest = it }
        }
        sessions.request(request, startsSession = true)
    }

    override fun stop() {
        volumeUnchecked.set(true)
        sessions.request(Request.Stop, startsSession = false)
    }

    /** The samples for [request], made the first time (on the writer thread). */
    private fun samplesFor(request: Request.Play): FloatArray = when (request.pulse) {
        GuidePulse.LOOSEN -> sharp ?: GuideSounds.sharp(sampleRate).also { sharp = it }
        GuidePulse.TIGHTEN -> flat ?: GuideSounds.flat(sampleRate).also { flat = it }
        else -> bell?.takeIf { bellSeconds == request.inTuneSeconds }
            ?: GuideSounds.inTune(request.inTuneSeconds, sampleRate).also {
                bell = it
                bellSeconds = request.inTuneSeconds
            }
    }

    /** One session: the sound playing, where it is, and the next one waiting for it to end. */
    private inner class GuideSession : PlayerSessions.Chunks<Request> {
        private var sound: FloatArray? = null
        private var position = 0
        private var next: Request.Play? = null

        /** Samples left of the stop's fade, or -1 while none. */
        private var fadeLeft = -1
        private val fadeLength = maxOf(1, (STOP_FADE_SECONDS * sampleRate).toInt())

        override fun render(request: Request?, buffer: FloatArray): Int {
            when (request) {
                is Request.Stop -> {
                    next = null
                    if (sound != null && fadeLeft < 0) fadeLeft = fadeLength
                }
                is Request.Play -> if (sound == null) start(request) else next = request
                null -> Unit
            }
            if (sound == null) next?.let { start(it) }
            val playing = sound ?: return 0
            var count = 0
            while (count < buffer.size && position < playing.size && fadeLeft != 0) {
                var v = playing[position++]
                if (fadeLeft > 0) v *= (--fadeLeft).toFloat() / fadeLength
                buffer[count++] = v
            }
            if (position >= playing.size || fadeLeft == 0) {
                sound = null
                fadeLeft = -1
            }
            return count
        }

        private fun start(request: Request.Play) {
            sound = samplesFor(request)
            position = 0
            fadeLeft = -1
            next = null
        }
    }

    /** What [play] and [stop] ask the session for. */
    private sealed interface Request {
        data object Stop : Request

        /** A sound: [pulse]'s, the in-tune bell [inTuneSeconds] long. */
        class Play(val pulse: GuidePulse, val inTuneSeconds: Float) : Request

        companion object {
            val SHARP = Play(GuidePulse.LOOSEN, 0f)
            val FLAT = Play(GuidePulse.TIGHTEN, 0f)
        }
    }

    private companion object {
        const val WRITER_THREAD_NAME = "GuideSoundPlayer"
        const val BUFFERED_CHUNKS = 4

        /** How quickly [stop] silences a sound: quick, yet long enough not to click. */
        const val STOP_FADE_SECONDS = 0.005f
    }
}
