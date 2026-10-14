package io.github.klaboukes.twangle.core.audio

import io.github.klaboukes.twangle.core.dsp.metronome.ClickTrack
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [MetronomePlayer] for tests: makes no sound and emits no beats by itself.
 *
 * - [bpm] and [meter] follow the calls the way the real player does (clamped, ignored while stopped);
 *   [startCount] and [stopCount] count calls.
 * - [beat] emits a beat as if it had just been heard, with the accent the meter gives it.
 */
class FakeMetronomePlayer : MetronomePlayer {

    private val playing = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = playing.asStateFlow()

    private val beatFlow = MutableSharedFlow<MetronomeBeat>(extraBufferCapacity = 64)
    override val beats: SharedFlow<MetronomeBeat> = beatFlow.asSharedFlow()

    /** The tempo playing (or last played). */
    var bpm: Int = ClickTrack.DEFAULT_BPM
        private set

    /** The meter playing (or last played). */
    var meter: Meter = Meter.DEFAULT
        private set

    var startCount: Int = 0
        private set

    var stopCount: Int = 0
        private set

    override fun start(bpm: Int, meter: Meter) {
        startCount++
        this.bpm = ClickTrack.clampBpm(bpm)
        this.meter = meter
        playing.value = true
    }

    override fun setTempo(bpm: Int) {
        if (playing.value) this.bpm = ClickTrack.clampBpm(bpm)
    }

    override fun setMeter(meter: Meter) {
        if (playing.value) this.meter = meter
    }

    override fun stop() {
        stopCount++
        playing.value = false
    }

    /** Emits beat [index] (counted from the start, in the current meter) as heard at [heardAtNanos]. */
    fun beat(index: Long, heardAtNanos: Long = 0L) {
        val beatInBar = (index % meter.beatsPerBar).toInt()
        beatFlow.tryEmit(MetronomeBeat(index, beatInBar, meter.accentOf(beatInBar), heardAtNanos))
    }
}
