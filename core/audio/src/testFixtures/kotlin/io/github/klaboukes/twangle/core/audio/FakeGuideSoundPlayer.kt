package io.github.klaboukes.twangle.core.audio

import io.github.klaboukes.twangle.core.dsp.guide.GuidePulse
import io.github.klaboukes.twangle.core.dsp.guide.GuideSounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [GuideSoundPlayer] for tests: makes no sound, remembers what it was asked to play.
 *
 * - [played] lists every pulse [play] was asked for (not [GuidePulse.NONE]), oldest first, and [inTuneLengths] the bell's
 *   length of each in-tune one; [stopCount] counts [stop] calls.
 * - [isSounding] turns true on [play] and, with a [scope] (a test's `backgroundScope`, on virtual time), false again
 *   once the sound would have ended: [GuideSounds.STEPS_SECONDS] for a step, the bell's length for the bell, each plus
 *   [latencySeconds] for the track to start and drain, as the real player's sessions take. Without one it stays true
 *   until [finish]. [stop] ends it at once.
 */
class FakeGuideSoundPlayer(
    private val scope: CoroutineScope? = null,
    private val latencySeconds: Float = 0.04f,
) : GuideSoundPlayer {

    private val sounding = MutableStateFlow(false)
    override val isSounding: StateFlow<Boolean> = sounding.asStateFlow()

    private val plays = mutableListOf<GuidePulse>()
    private val lengths = mutableListOf<Float>()
    private var ending: Job? = null

    /** Every pulse played, oldest first. */
    val played: List<GuidePulse> get() = synchronized(plays) { plays.toList() }

    /** The bell's length of every in-tune pulse played, oldest first. */
    val inTuneLengths: List<Float> get() = synchronized(plays) { lengths.toList() }

    /** How many times [stop] was called. */
    var stopCount: Int = 0
        private set

    override fun play(pulse: GuidePulse, inTuneSeconds: Float) {
        if (pulse == GuidePulse.NONE) return
        synchronized(plays) {
            plays += pulse
            if (pulse == GuidePulse.IN_TUNE) lengths += inTuneSeconds
        }
        sounding.value = true
        val seconds = (if (pulse == GuidePulse.IN_TUNE) inTuneSeconds else GuideSounds.STEPS_SECONDS) + latencySeconds
        ending?.cancel()
        ending = scope?.launch {
            delay((seconds * 1000f).toLong())
            sounding.value = false
        }
    }

    override fun stop() {
        stopCount++
        ending?.cancel()
        sounding.value = false
    }

    /** The sound has ended by itself. */
    fun finish() {
        sounding.value = false
    }
}
