package io.github.klaboukes.twangle.core.audio

import io.github.klaboukes.twangle.core.dsp.drone.DroneSound
import io.github.klaboukes.twangle.core.dsp.drone.DroneSynth
import io.github.klaboukes.twangle.core.dsp.drone.DroneVoicing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One [DronePlayer.play] call, as [FakeDronePlayer] remembers it. */
data class DronePlay(
    val rootHz: Float,
    val sound: DroneSound,
    val voicing: DroneVoicing,
    val lower: Boolean,
)

/**
 * [DronePlayer] for tests: makes no sound, remembers what it was asked to do.
 *
 * - [plays] lists every [play] call, oldest first; [stopCount] counts [stop] calls.
 * - [isPlaying] turns true on [play] and false on [stop], or when the test calls [interrupt]
 *   (a call, another app taking the sound, headphones unplugged: the drone stopped by itself).
 */
class FakeDronePlayer : DronePlayer {

    private val playing = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = playing.asStateFlow()

    private val calls = mutableListOf<DronePlay>()

    /** Every [play] call, oldest first. */
    val plays: List<DronePlay> get() = calls.toList()

    /** The last [play] call, or null before the first. */
    val lastPlay: DronePlay? get() = calls.lastOrNull()

    /** How many times [stop] was called, whether or not the drone was playing. */
    var stopCount: Int = 0
        private set

    override fun play(rootHz: Float, sound: DroneSound, voicing: DroneVoicing, lower: Boolean) {
        require(rootHz in DroneSynth.MIN_ROOT_HZ..DroneSynth.MAX_ROOT_HZ) {
            "rootHz must be in ${DroneSynth.MIN_ROOT_HZ}..${DroneSynth.MAX_ROOT_HZ}, was $rootHz"
        }
        calls += DronePlay(rootHz, sound, voicing, lower)
        playing.value = true
    }

    override fun stop() {
        stopCount++
        playing.value = false
    }

    /** The drone stops by itself, as the real one does on a call or when headphones come unplugged. */
    fun interrupt() {
        playing.value = false
    }
}
