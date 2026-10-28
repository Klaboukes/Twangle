package io.github.klaboukes.twangle.core.audio

import io.github.klaboukes.twangle.core.dsp.synth.StringMix
import io.github.klaboukes.twangle.core.dsp.synth.StringVoice
import kotlinx.coroutines.flow.StateFlow

/**
 * Plays a reference tone: a note at an exact pitch in the voice of the player's instrument (a
 * plucked guitar, bass, ukulele or mandolin string, a bowed violin, viola or cello string) that
 * dies away or is lifted and stops by itself after a few seconds (`StringSynth` in `:core:dsp`).
 *
 * Callers turn a note into Hz with their `NoteMapper`, so the tone follows the A4 calibration, and
 * pass the instrument's `StringVoice`. [play], [playTogether] and [stop] return at once and may be
 * called from the main thread.
 */
interface TonePlayer {

    /**
     * True from [play] until the tone has faded out and left the speaker (or failed to play). While
     * it is true the tuner should not trust the microphone: it would hear the tone itself.
     */
    val isPlaying: StateFlow<Boolean>

    /**
     * Sounds a note at [frequencyHz] (above 0, at most a few kHz) in [voice]. A tone still sounding
     * fades out quickly first, so a new note never clicks. With [octavePair] a voice of paired
     * strings sounds the pair an octave apart, as a 12-string's lower courses (`StringSynth.start`).
     */
    fun play(frequencyHz: Float, voice: StringVoice, octavePair: Boolean = false)

    /**
     * Sounds [frequenciesHz] (one to [MAX_TOGETHER] notes, each as [play] accepts) together in
     * [voice], about as loud together as one note alone (each at 1/√n, a soft limiter keeping
     * them from clipping, `StringMix`): all at once, or with [strumSeconds] (at most half a second) one after another
     * that far apart, in the order given, as a strum crosses the strings. Where [octavePairs] says
     * so, a note sounds as an octave pair. Otherwise as [play]: a tone still sounding fades out
     * quickly first, and [isPlaying] stays true until the last note has left the speaker.
     */
    fun playTogether(
        frequenciesHz: List<Float>,
        voice: StringVoice,
        strumSeconds: Float = 0f,
        octavePairs: List<Boolean> = emptyList(),
    )

    /** Fades the current tone out quickly (a few tens of ms). Does nothing when nothing plays. */
    fun stop()

    companion object {
        /** Most notes [playTogether] sounds at once: an 8-string guitar's chord. */
        const val MAX_TOGETHER = StringMix.MAX_NOTES
    }
}
