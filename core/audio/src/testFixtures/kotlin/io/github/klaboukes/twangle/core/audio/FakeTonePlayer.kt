package io.github.klaboukes.twangle.core.audio

import io.github.klaboukes.twangle.core.dsp.synth.StringVoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [TonePlayer] for tests: makes no sound, remembers what it was asked to do.
 *
 * - [played] lists every frequency passed to [play], in order, [voices] the voice each was
 *   asked for and [octavePairs] whether it was an octave pair; [stopCount] counts [stop] calls.
 * - [together] lists every [playTogether] call's notes, in order, and [strums] its strum time;
 *   they don't show in [played].
 * - [isPlaying] turns true on [play] or [playTogether] and false on [stop] (the real player's quick fade is instant
 *   here), or when the test calls [finish], i.e. the tone has died away by itself.
 */
class FakeTonePlayer : TonePlayer {

    private val playing = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = playing.asStateFlow()

    private val plays = mutableListOf<Float>()
    private val playedVoices = mutableListOf<StringVoice>()
    private val playedPairs = mutableListOf<Boolean>()
    private val playedTogether = mutableListOf<List<Float>>()
    private val playedStrums = mutableListOf<Float>()

    /** Every frequency [play] was called with, oldest first. */
    val played: List<Float> get() = plays.toList()

    /** The voice of every [play] call, oldest first, alongside [played]. */
    val voices: List<StringVoice> get() = playedVoices.toList()

    /** Whether every [play] call asked for an octave pair, oldest first, alongside [played]. */
    val octavePairs: List<Boolean> get() = playedPairs.toList()

    /** The notes of every [playTogether] call, oldest first. */
    val together: List<List<Float>> get() = playedTogether.toList()

    /** The strum time of every [playTogether] call, oldest first, alongside [together]. */
    val strums: List<Float> get() = playedStrums.toList()

    /** How many times [stop] was called, whether or not a tone was playing. */
    var stopCount: Int = 0
        private set

    override fun play(frequencyHz: Float, voice: StringVoice, octavePair: Boolean) {
        require(frequencyHz > 0f) { "frequencyHz must be positive, was $frequencyHz" }
        plays += frequencyHz
        playedVoices += voice
        playedPairs += octavePair
        playing.value = true
    }

    override fun playTogether(frequenciesHz: List<Float>, voice: StringVoice, strumSeconds: Float, octavePairs: List<Boolean>) {
        require(frequenciesHz.size in 1..TonePlayer.MAX_TOGETHER) { "One to ${TonePlayer.MAX_TOGETHER} notes, not ${frequenciesHz.size}" }
        require(frequenciesHz.all { it > 0f }) { "frequencies must be positive, were $frequenciesHz" }
        playedTogether += frequenciesHz.toList()
        playedStrums += strumSeconds
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
