package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.dsp.synth.PluckSynth
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/** Synthetic strums for the strum check's tests: plucked strings ([PluckSynth]) whose pitch is known exactly. */
object SyntheticStrum {

    const val SAMPLE_RATE = 48_000

    /** [hz] with string i moved by `cents[i]` cents. */
    fun detuned(hz: FloatArray, cents: FloatArray): FloatArray =
        FloatArray(hz.size) { (hz[it] * 2.0.pow(cents[it] / 1200.0)).toFloat() }

    /**
     * [silenceSeconds] of silence, then a downstroke over the strings at [hz] (each [spreadSeconds]
     * after the one before; a gain of 0 leaves a string out), [seconds] long in all from the strum.
     * Its peak is [peak].
     */
    fun signal(
        hz: FloatArray,
        silenceSeconds: Double = 0.3,
        seconds: Double = 2.5,
        spreadSeconds: Double = 0.015,
        gains: FloatArray = FloatArray(hz.size) { 1f },
        peak: Float = 0.4f,
    ): FloatArray {
        val start = (silenceSeconds * SAMPLE_RATE).roundToInt()
        val length = (seconds * SAMPLE_RATE).roundToInt()
        val out = FloatArray(start + length)
        val tone = FloatArray(length)
        val synth = PluckSynth(SAMPLE_RATE, durationSeconds = 4f)
        for (i in hz.indices) {
            if (gains[i] == 0f) continue
            val delay = (i * spreadSeconds * SAMPLE_RATE).roundToInt()
            tone.fill(0f)
            synth.start(hz[i])
            synth.render(tone, delay, length - delay)
            for (n in 0 until length) out[start + n] += gains[i] * tone[n]
        }
        val loudest = out.maxOf { abs(it) }
        if (loudest > 0f) for (n in out.indices) out[n] *= peak / loudest
        return out
    }
}
