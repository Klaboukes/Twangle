package io.github.klaboukes.twangle.core.audio

import io.github.klaboukes.twangle.core.dsp.metronome.ClickSound
import io.github.klaboukes.twangle.core.dsp.metronome.ClickTrack
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.dsp.metronome.SpeedTrainer
import io.github.klaboukes.twangle.core.dsp.metronome.Subdivision
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [MetronomePlayer] for tests: makes no sound and emits no beats by itself.
 *
 * - [bpm], [meter], [sound], [subdivision] and [trainer] follow the calls the way the real player
 *   does (clamped, ignored while stopped); [startCount], [stopCount] and [previewCount] count calls.
 * - [beat] emits a beat as if it had just been heard, with the accent the meter gives it; with a
 *   [trainer] on, each downbeat counts a bar and takes the trainer's tempo for it, as the real click
 *   track climbs.
 * - A preview lasts until [finishPreview] (its bar heard), [stop] or [start].
 */
class FakeMetronomePlayer : MetronomePlayer {

    private val playing = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = playing.asStateFlow()

    private val previewing = MutableStateFlow(false)
    override val isPreviewing: StateFlow<Boolean> = previewing.asStateFlow()

    private val beatFlow = MutableSharedFlow<MetronomeBeat>(extraBufferCapacity = 64)
    override val beats: SharedFlow<MetronomeBeat> = beatFlow.asSharedFlow()

    /** The tempo playing (or last played or previewed); while a trainer climbs, the tempo it climbed to. */
    var bpm: Int = ClickTrack.DEFAULT_BPM
        private set

    /** The meter playing (or last played or previewed). */
    var meter: Meter = Meter.DEFAULT
        private set

    /** The sound playing (or last played or previewed). */
    var sound: ClickSound = ClickSound.DEFAULT
        private set

    /** The subdivision playing (or last played or previewed), as picked (not yet [Subdivision.within] the meter). */
    var subdivision: Subdivision = Subdivision.DEFAULT
        private set

    /** The speed trainer climbing, or null. */
    var trainer: SpeedTrainer? = null
        private set

    /** The trainer's bar the last beat played in: −1 before its first downbeat, or with none. */
    var trainerBar: Long = -1L
        private set

    var startCount: Int = 0
        private set

    var stopCount: Int = 0
        private set

    /** Previews started (not counting [previewBar] calls while playing, which only change the sound and subdivision). */
    var previewCount: Int = 0
        private set

    /** Trainers started or ended with [setTrainer] while playing. */
    var trainerChanges: Int = 0
        private set

    override fun start(bpm: Int, meter: Meter, sound: ClickSound, subdivision: Subdivision, trainer: SpeedTrainer?) {
        startCount++
        this.bpm = ClickTrack.clampBpm(trainer?.startBpm ?: bpm)
        this.meter = meter
        this.sound = sound
        this.subdivision = subdivision
        this.trainer = trainer
        trainerBar = -1L
        previewing.value = false
        playing.value = true
    }

    override fun setTempo(bpm: Int) {
        if (playing.value && trainer == null) this.bpm = ClickTrack.clampBpm(bpm)
    }

    override fun setTrainer(trainer: SpeedTrainer?) {
        if (!playing.value) return
        trainerChanges++
        this.trainer = trainer
        trainerBar = -1L
        if (trainer != null) bpm = ClickTrack.clampBpm(trainer.startBpm)
    }

    override fun setMeter(meter: Meter) {
        if (playing.value) this.meter = meter
    }

    override fun setSound(sound: ClickSound) {
        if (playing.value) this.sound = sound
    }

    override fun setSubdivision(subdivision: Subdivision) {
        if (playing.value) this.subdivision = subdivision
    }

    override fun previewBar(bpm: Int, meter: Meter, sound: ClickSound, subdivision: Subdivision) {
        this.sound = sound
        this.subdivision = subdivision
        if (playing.value) return
        previewCount++
        this.bpm = ClickTrack.clampBpm(bpm)
        this.meter = meter
        this.trainer = null
        previewing.value = true
    }

    override fun stop() {
        stopCount++
        playing.value = false
        previewing.value = false
    }

    /** The preview's bar has been heard: it ends by itself. */
    fun finishPreview() {
        previewing.value = false
    }

    /**
     * Emits beat [index] (counted from the start, in the current meter) as heard at [heardAtNanos]; a
     * downbeat with a [trainer] on first takes the next bar's tempo.
     */
    fun beat(index: Long, heardAtNanos: Long = 0L) {
        val beatInBar = (index % meter.beatsPerBar).toInt()
        val climbing = trainer
        if (climbing != null && beatInBar == 0) {
            trainerBar++
            bpm = ClickTrack.clampBpm(climbing.bpmOfBar(trainerBar))
        }
        beatFlow.tryEmit(
            MetronomeBeat(
                index,
                beatInBar,
                meter.accentOf(beatInBar),
                heardAtNanos,
                sound,
                bpm = bpm,
                trainerBar = if (climbing != null) trainerBar else -1L,
            ),
        )
    }
}
