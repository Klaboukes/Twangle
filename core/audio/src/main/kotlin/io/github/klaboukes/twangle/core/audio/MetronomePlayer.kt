package io.github.klaboukes.twangle.core.audio

import io.github.klaboukes.twangle.core.dsp.metronome.BeatAccent
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One metronome click, told as it reaches the speaker, for beat lights and vibration.
 *
 * @property index beats since [MetronomePlayer.start], from 0.
 * @property beatInBar position in the bar, from 0 (the downbeat) to [Meter.beatsPerBar] − 1.
 * @property accent which click sounded.
 * @property heardAtNanos when the click leaves the speaker, on the `System.nanoTime()` clock (the
 *   clock of Compose's frame times), best estimate from the audio output's timestamps. It is within
 *   a few milliseconds of the moment the beat is emitted.
 */
data class MetronomeBeat(
    val index: Long,
    val beatInBar: Int,
    val accent: BeatAccent,
    val heardAtNanos: Long,
)

/**
 * Plays the metronome: clicks with sample-exact timing (`ClickTrack` in `:core:dsp`) at 30–250 BPM
 * in any [Meter], the first beat of each bar accented. All calls return at once and may come from
 * the main thread. It never touches the microphone.
 */
interface MetronomePlayer {

    /** True from [start] until [stop] (or a failure, or another app taking the audio for good). */
    val isPlaying: StateFlow<Boolean>

    /** Every click, emitted as it is heard. Nothing is replayed to a new collector. */
    val beats: SharedFlow<MetronomeBeat>

    /**
     * Starts clicking at [bpm] (clamped to 30–250) in [meter], beat 0 a downbeat, the first click at
     * once. While already playing it only changes tempo and meter, like [setTempo] and [setMeter].
     */
    fun start(bpm: Int, meter: Meter)

    /**
     * Changes the tempo (clamped to 30–250) from the next beat, which comes one new interval after
     * the last one heard (or at once, if that moment has passed). Ignored while stopped.
     */
    fun setTempo(bpm: Int)

    /** Changes the meter: the next beat starts a new bar, at the same time as before. Ignored while stopped. */
    fun setMeter(meter: Meter)

    /** Stops the clicks within a few tens of ms. Does nothing when nothing plays. */
    fun stop()
}
