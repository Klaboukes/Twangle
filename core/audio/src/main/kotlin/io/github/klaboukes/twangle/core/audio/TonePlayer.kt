package io.github.klaboukes.twangle.core.audio

import io.github.klaboukes.twangle.core.dsp.synth.StringVoice
import kotlinx.coroutines.flow.StateFlow

/**
 * Plays a reference tone: a note at an exact pitch in the voice of the player's instrument (a
 * plucked guitar, bass, ukulele or mandolin string, a bowed violin, viola or cello string) that
 * dies away or is lifted and stops by itself after a few seconds (`StringSynth` in `:core:dsp`).
 *
 * Callers turn a note into Hz with their `NoteMapper`, so the tone follows the A4 calibration, and
 * pass the instrument's `StringVoice`. [play] and [stop] return at once and may be called from the
 * main thread.
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

    /** Fades the current tone out quickly (a few tens of ms). Does nothing when nothing plays. */
    fun stop()
}
