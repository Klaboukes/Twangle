package io.github.klaboukes.twangle.core.audio

import kotlinx.coroutines.flow.StateFlow

/**
 * Plays a reference tone: a short plucked note at an exact pitch that dies away and stops by
 * itself after a few seconds (`PluckSynth` in `:core:dsp`).
 *
 * Callers turn a note into Hz with their `NoteMapper`, so the tone follows the A4 calibration.
 * [play] and [stop] return at once and may be called from the main thread.
 */
interface TonePlayer {

    /**
     * True from [play] until the tone has faded out and left the speaker (or failed to play). While
     * it is true the tuner should not trust the microphone: it would hear the tone itself.
     */
    val isPlaying: StateFlow<Boolean>

    /**
     * Plucks a tone at [frequencyHz] (above 0, at most a few kHz). A tone still sounding fades out
     * quickly first, so a new note never clicks.
     */
    fun play(frequencyHz: Float)

    /** Fades the current tone out quickly (a few tens of ms). Does nothing when nothing plays. */
    fun stop()
}
