package io.github.klaboukes.twangle.core.audio

import io.github.klaboukes.twangle.core.dsp.metronome.BeatAccent
import io.github.klaboukes.twangle.core.dsp.metronome.ClickSound
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.dsp.metronome.Subdivision
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One metronome click, told as it reaches the speaker, for beat lights and vibration.
 *
 * @property index beats since [MetronomePlayer.start], from 0.
 * @property beatInBar position in the bar, from 0 (the downbeat) to [Meter.beatsPerBar] − 1.
 * @property accent which click sounded.
 * @property sound the instrument it sounded in.
 * @property heardAtNanos when the click leaves the speaker, on the `System.nanoTime()` clock (the
 *   clock of Compose's frame times), best estimate from the audio output's timestamps. It is within
 *   a few milliseconds of the moment the beat is emitted.
 */
data class MetronomeBeat(
    val index: Long,
    val beatInBar: Int,
    val accent: BeatAccent,
    val heardAtNanos: Long,
    val sound: ClickSound = ClickSound.DEFAULT,
)

/**
 * Plays the metronome: clicks with sample-exact timing (`ClickTrack` in `:core:dsp`) at 30–250 BPM
 * in any [Meter], the first beat of each bar accented, in any [ClickSound], with softer clicks between
 * the beats when a [Subdivision] is set (Phase 62; one a meter doesn't offer plays as none there,
 * [Subdivision.within]). All calls return at once and may come from the main thread. It never
 * touches the microphone.
 *
 * Besides the metronome it plays a preview ([previewBar]): one bar of a sound, once, while the
 * metronome is stopped, so a player can hear a click before picking it.
 */
interface MetronomePlayer {

    /** True from [start] until [stop] (or a failure, a call, or headphones coming unplugged). Not during a preview. */
    val isPlaying: StateFlow<Boolean>

    /** True from [previewBar] until its bar has been heard, or [stop] or [start] ended it. */
    val isPreviewing: StateFlow<Boolean>

    /**
     * Every beat of the metronome, emitted as it is heard (a preview's aren't); the clicks between the
     * beats aren't beats and aren't told. Nothing is replayed to a new collector.
     */
    val beats: SharedFlow<MetronomeBeat>

    /**
     * Starts clicking at [bpm] (clamped to 30–250) in [meter] with [sound] and [subdivision], beat 0
     * a downbeat, the first click at once; a preview still sounding ends. While already playing it
     * only changes tempo, meter, sound and subdivision, like [setTempo], [setMeter], [setSound] and
     * [setSubdivision].
     */
    fun start(bpm: Int, meter: Meter, sound: ClickSound = ClickSound.DEFAULT, subdivision: Subdivision = Subdivision.NONE)

    /**
     * Changes the tempo (clamped to 30–250) from the next beat, which comes one new interval after
     * the last one heard (or at once, if that moment has passed). Ignored while stopped.
     */
    fun setTempo(bpm: Int)

    /** Changes the meter: the next beat starts a new bar, at the same time as before. Ignored while stopped. */
    fun setMeter(meter: Meter)

    /**
     * Changes the sound from the next beat; a click already sounding plays to its end. Ignored while
     * stopped (and during a preview).
     */
    fun setSound(sound: ClickSound)

    /**
     * Changes the clicks between the beats from the next beat; the beat sounding keeps its clicks.
     * Ignored while stopped (and during a preview).
     */
    fun setSubdivision(subdivision: Subdivision)

    /**
     * While stopped: plays one bar of [sound] at [bpm] in [meter] with [subdivision], once (never
     * looping), then stops by itself; a preview already sounding starts over with this one. While
     * playing it only changes the sound and subdivision, as [setSound] and [setSubdivision]. [stop]
     * and [start] end a preview.
     */
    fun previewBar(bpm: Int, meter: Meter, sound: ClickSound, subdivision: Subdivision = Subdivision.NONE)

    /** Stops the clicks (or a preview) within a few tens of ms. Does nothing when nothing plays. */
    fun stop()
}
