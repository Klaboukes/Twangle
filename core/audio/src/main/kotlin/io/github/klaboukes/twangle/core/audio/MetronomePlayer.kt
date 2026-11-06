package io.github.klaboukes.twangle.core.audio

import io.github.klaboukes.twangle.core.dsp.metronome.BeatAccent
import io.github.klaboukes.twangle.core.dsp.metronome.ClickSound
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.dsp.metronome.SpeedTrainer
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
 * @property bpm the tempo from this beat to the next (0 where unknown): while the speed trainer climbs,
 *   with the step it took on this downbeat.
 * @property trainerBar the speed trainer's bar this beat plays in, from 0 at its first downbeat; −1
 *   before that, or with no trainer climbing.
 * @property clicksPerBeat the clicks this beat is split into (Phase 62): 1 with none between the
 *   beats, else its own and the [Subdivision] clicks after it, evenly up to the next beat at [bpm].
 */
data class MetronomeBeat(
    val index: Long,
    val beatInBar: Int,
    val accent: BeatAccent,
    val heardAtNanos: Long,
    val sound: ClickSound = ClickSound.DEFAULT,
    val bpm: Int = 0,
    val trainerBar: Long = -1L,
    val clicksPerBeat: Int = 1,
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
     * The same beats as [beats], each told as soon as its heard moment is known, ahead of it (about
     * one buffer, 0.1–0.3 s; at once if it is already due), for what must start before a beat to
     * land on it: the watch's wrist taps (Phase 80), whose motor takes ~10 ms to start. A beat told
     * here may never be heard if the metronome stops first, so check [isPlaying] at its moment.
     * Nothing is replayed to a new collector.
     */
    val upcomingBeats: SharedFlow<MetronomeBeat>

    /**
     * Starts clicking at [bpm] (clamped to 30–250) in [meter] with [sound] and [subdivision], beat 0
     * a downbeat, the first click at once; a preview still sounding ends. With a [trainer] (Phase 62)
     * it starts at the trainer's start tempo instead and climbs, as [setTrainer]. While already
     * playing it only changes tempo, meter, sound, subdivision and trainer, like [setTempo],
     * [setMeter], [setSound], [setSubdivision] and [setTrainer].
     */
    fun start(
        bpm: Int,
        meter: Meter,
        sound: ClickSound = ClickSound.DEFAULT,
        subdivision: Subdivision = Subdivision.NONE,
        trainer: SpeedTrainer? = null,
    )

    /**
     * Changes the tempo (clamped to 30–250) from the next beat, which comes one new interval after
     * the last one heard (or at once, if that moment has passed). Ignored while stopped, and while a
     * speed trainer climbs (end it first: [setTrainer] with null).
     */
    fun setTempo(bpm: Int)

    /**
     * Starts the speed trainer [trainer] while playing (Phase 62): its start tempo from the next beat,
     * then its steps, each on its bar's downbeat to the sample (`ClickTrack` climbs on the audio
     * thread); each of [beats] tells the tempo it plays at. A new trainer begins again from its start.
     * Null ends it, leaving the tempo where it climbed to. Ignored while stopped (and during a preview).
     */
    fun setTrainer(trainer: SpeedTrainer?)

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
     * Whether the clicks are silent (Phase 80, the watch's taps-only metronome): silent, the track
     * plays silence in their place, so the clock, the [beats] and their heard moments go on exactly as
     * with the clicks, and switching either way mid-play never moves a beat. Heard or silent from
     * about one buffer later (≈ 0.12 s); kept across [stop] and [start], playing or not. A preview
     * always sounds. Heard until set.
     */
    fun setSilent(silent: Boolean)

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
