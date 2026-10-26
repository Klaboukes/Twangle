package io.github.klaboukes.twangle.core.audio

import io.github.klaboukes.twangle.core.dsp.drone.DroneSound
import io.github.klaboukes.twangle.core.dsp.drone.DroneVoicing
import kotlinx.coroutines.flow.StateFlow

/**
 * Plays the drone under the Scales tool (Phase 48): a steady low note, the scale's root alone or
 * with its fifth and octave, in one of four sounds, for as long as it is asked (`DroneSynth` in
 * `:core:dsp`). Playback only: it never touches the microphone.
 *
 * Callers place the root with `DroneSynth.rootNote(pitchClass)` and turn it into Hz with their
 * `NoteMapper`, so the drone follows the A4 setting. [play] and [stop] return at once and may be
 * called from the main thread.
 */
interface DronePlayer {

    /**
     * True from [play] until [stop], or until the drone stopped by itself: a call or another app
     * taking the sound, headphones coming unplugged, or a failure. It turns false at once on [stop],
     * while the drone still fades out, so a ▶ / ■ button can follow it directly.
     */
    val isPlaying: StateFlow<Boolean>

    /**
     * Sounds the drone on [rootHz] (`DroneSynth.MIN_ROOT_HZ`..`MAX_ROOT_HZ`) in [sound] and
     * [voicing], an octave lower if [lower]. From silence it fades in; while it sounds, a new root,
     * sound, voicing or lower crossfades to it (nothing happens if nothing changed); while it fades
     * out after [stop] it comes back.
     */
    fun play(
        rootHz: Float,
        sound: DroneSound = DroneSound.TANPURA,
        voicing: DroneVoicing = DroneVoicing.FIFTH,
        lower: Boolean = false,
    )

    /** Fades the drone out (a quarter of a second) and lets the speaker go. Does nothing when nothing plays. */
    fun stop()
}

