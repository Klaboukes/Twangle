package io.github.klaboukes.twangle.core.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [TonePlayer] for tests: makes no sound, remembers what it was asked to do.
 *
 * - [played] lists every frequency passed to [play], in order; [stopCount] counts [stop] calls.
 * - [isPlaying] turns true on [play] and false on [stop] (the real player's quick fade is instant
 *   here), or when the test calls [finish], i.e. the tone has died away by itself.
 */
class FakeTonePlayer : TonePlayer {

    private val playing = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = playing.asStateFlow()

    private val plays = mutableListOf<Float>()

    /** Every frequency [play] was called with, oldest first. */
    val played: List<Float> get() = plays.toList()

    /** How many times [stop] was called, whether or not a tone was playing. */
    var stopCount: Int = 0
        private set

    override fun play(frequencyHz: Float) {
        require(frequencyHz > 0f) { "frequencyHz must be positive, was $frequencyHz" }
        plays += frequencyHz
        playing.value = true
    }

    override fun stop() {
        stopCount++
        playing.value = false
    }

    /** The tone ends by itself, as the real one does after a few seconds. */
    fun finish() {
        playing.value = false
    }
}
