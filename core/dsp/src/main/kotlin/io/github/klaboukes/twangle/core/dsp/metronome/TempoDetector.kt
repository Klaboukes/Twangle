package io.github.klaboukes.twangle.core.dsp.metronome

/** Where a [TempoDetector] stands with the music it has heard so far. */
enum class TempoStatus {
    /** Too little heard yet (under [OnsetTempoDetector.MIN_SECONDS]): no tempo. */
    LISTENING,

    /** No steady beat in what was heard (silence, noise, music without a pulse): no tempo. */
    NO_BEAT,

    /** A tempo, still settling: it may move a little. */
    SETTLING,

    /** A tempo that has stayed within ±1 BPM for [OnsetTempoDetector.STEADY_SECONDS]. */
    STEADY,
}

/**
 * Hears the beat in music and tells its tempo. Fed microphone audio as it arrives ([process]);
 * the properties tell the current estimate, updated a few times a second. Implementations must not
 * allocate in [process] (hard rule 2) and are not thread-safe: one thread feeds and reads them.
 *
 * Tempos are whole BPM within [ClickTrack.MIN_BPM]..[ClickTrack.MAX_BPM], counting beats as a
 * player would tap them; 0 means "none". Beat moments are counted in samples of the audio fed
 * since the start (or the last [reset]).
 */
interface TempoDetector {

    /** Sample rate of the audio passed to [process], in Hz. */
    val sampleRate: Int

    /** Takes the next [length] samples of [samples] from [offset] (values in −1..1). */
    fun process(samples: FloatArray, offset: Int = 0, length: Int = samples.size - offset)

    /** Forgets everything heard, e.g. when listening starts again. */
    fun reset()

    /** Samples taken since the start or the last [reset]. */
    val samplesProcessed: Long

    val status: TempoStatus

    /** The tempo to show, whole BPM, or 0. It moves only when the estimate does by most of a BPM. */
    val bpm: Int

    /** The estimate behind [bpm], unrounded, or 0. */
    val exactBpm: Float

    /** Half of [exactBpm], rounded, if that is within 30–250; else 0. */
    val halfBpm: Int

    /** Twice [exactBpm], rounded, if that is within 30–250; else 0. */
    val doubleBpm: Int

    /** Whichever of [halfBpm] and [doubleBpm] the music came closer to being heard as, or 0. */
    val alternativeBpm: Int

    /** How clearly the music repeats at [bpm]'s beat, 0..1 (about 0.9 for a click track, under 0.3 for noise). */
    val confidence: Float

    /** How long the estimate has stayed within ±1 BPM, in seconds of audio. */
    val steadySeconds: Float

    /** The sample the latest heard beat fell on (on the beat grid fitted to the last few seconds), or −1. */
    val lastBeatSample: Long

    /** Samples from one beat to the next at [exactBpm], or 0. The next beat is due at [lastBeatSample] plus this. */
    val beatIntervalSamples: Float
}
