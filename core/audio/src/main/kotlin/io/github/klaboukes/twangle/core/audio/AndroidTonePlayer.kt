package io.github.klaboukes.twangle.core.audio

import android.content.Context
import io.github.klaboukes.twangle.core.dsp.synth.StringMix
import io.github.klaboukes.twangle.core.dsp.synth.StringSynth
import io.github.klaboukes.twangle.core.dsp.synth.StringVoice
import kotlinx.coroutines.flow.StateFlow

/**
 * [TonePlayer] backed by a streaming `android.media.AudioTrack`, through [PlayerSessions] as the
 * drone ([AndroidDronePlayer]): mono `ENCODING_PCM_FLOAT` at the phone's native output rate,
 * `USAGE_MEDIA` / `CONTENT_TYPE_MUSIC`, so the tone follows the media volume. The samples come from
 * a [StringMix] per voice (one [StringSynth] per note sounding together, a single note being exactly
 * its synth's), made the first time an instrument's voice is asked for and kept; a synth for each
 * further note is made the first time that many notes sound together, and kept.
 *
 * The track exists only while a tone sounds: [play] starts a session on the player's own thread at
 * audio priority, which takes the latest request every 10 ms chunk. A new note (or notes,
 * [playTogether]) releases the old ones over [StringSynth.STOP_FADE_SECONDS] and then starts (in its
 * own voice's mix), [stop] only releases. When the tone has ended and its last sample has been
 * heard, the session releases the track and [isPlaying] turns false.
 *
 * Each session holds transient audio focus, so music from another app pauses while the tone sounds
 * and resumes after it; losing focus (a call, another app's sound that won't share) stops the tone,
 * and when focus is refused (during a call) nothing plays. With the media volume at 0 a session
 * shows the phone's own volume panel, so a silent tone explains itself.
 */
class AndroidTonePlayer(context: Context) : TonePlayer {

    private val sessions = PlayerSessions<Request>(
        context = context,
        name = WRITER_THREAD_NAME,
        bufferedChunks = BUFFERED_CHUNKS,
        stopsWhenNoisy = false,
        stop = ::stop,
        startSession = { ToneSession() },
    )

    /** Sample rate of the tone: the phone's native output rate, 48 kHz if it reports nothing usable. */
    val sampleRate: Int = sessions.sampleRate

    /** Highest frequency [play] accepts. */
    val maxFrequencyHz: Float = StringSynth.maxFrequencyHz(sampleRate)

    // Used only on the writer thread: one mix per voice heard so far.
    private val mixes = HashMap<StringVoice, StringMix>()

    override val isPlaying: StateFlow<Boolean> = sessions.active

    override fun play(frequencyHz: Float, voice: StringVoice, octavePair: Boolean) {
        require(frequencyHz > 0f && frequencyHz <= maxFrequencyHz) {
            "frequencyHz must be in (0, $maxFrequencyHz], was $frequencyHz"
        }
        sessions.request(Request.Play(floatArrayOf(frequencyHz), voice, booleanArrayOf(octavePair), strumSeconds = 0f), startsSession = true)
    }

    override fun playTogether(frequenciesHz: List<Float>, voice: StringVoice, strumSeconds: Float, octavePairs: List<Boolean>) {
        require(frequenciesHz.size in 1..TonePlayer.MAX_TOGETHER) { "One to ${TonePlayer.MAX_TOGETHER} notes, not ${frequenciesHz.size}" }
        for (f in frequenciesHz) require(f > 0f && f <= maxFrequencyHz) { "frequencyHz must be in (0, $maxFrequencyHz], was $f" }
        require(strumSeconds in 0f..StringMix.MAX_SPREAD_SECONDS) { "strumSeconds must be in 0..${StringMix.MAX_SPREAD_SECONDS}, was $strumSeconds" }
        // Copied here, on the caller's thread, so the writer thread allocates nothing for it.
        val pairs = BooleanArray(frequenciesHz.size) { octavePairs.getOrElse(it) { false } }
        sessions.request(Request.Play(frequenciesHz.toFloatArray(), voice, pairs, strumSeconds), startsSession = true)
    }

    /** Releases the tone sounding; with none, does nothing. */
    override fun stop() {
        sessions.request(Request.Stop, startsSession = false)
    }

    /** The mix for [voice], made on its first tone (on the writer thread). */
    private fun mixFor(voice: StringVoice): StringMix = mixes.getOrPut(voice) { StringMix(sampleRate, voice) }

    /** One session's tone: the mix sounding, and the notes waiting for its release to end. */
    private inner class ToneSession : PlayerSessions.Chunks<Request> {
        private var synth: StringMix? = null
        private var next: Request.Play? = null

        override fun render(request: Request?, buffer: FloatArray): Int {
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
            return synth?.render(buffer) ?: 0
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
        const val WRITER_THREAD_NAME = "TonePlayer"
        const val BUFFERED_CHUNKS = 4
    }
}
