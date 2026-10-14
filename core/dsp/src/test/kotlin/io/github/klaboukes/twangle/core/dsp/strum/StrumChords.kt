package io.github.klaboukes.twangle.core.dsp.strum

import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.synth.PluckSynth
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Synthetic strums for the strum detector: every string's true frequency is known exactly. */
object StrumChords {

    const val SAMPLE_RATE = 48_000

    /** Open-string frequencies of [tuning] at [a4Hz], in the tuning's string order. */
    fun expectedHz(tuning: Tuning, a4Hz: Float = 440f): FloatArray {
        val mapper = NoteMapper(a4Hz)
        return FloatArray(tuning.stringCount) { mapper.frequencyOf(tuning.strings[it]) }
    }

    /** [expected] with string i moved by `cents[i]`. */
    fun detuned(expected: FloatArray, cents: FloatArray): FloatArray =
        FloatArray(expected.size) { (expected[it] * 2.0.pow(cents[it] / 1200.0)).toFloat() }

    /**
     * A strum from [PluckSynth] tones (harmonic k at 1/k, higher ones dying faster): string i
     * starts `i × spreadSeconds` after the first (a downstroke), at gain `gains[i]`; a gain of 0
     * leaves the string out. Returns [length] samples from [fromSeconds] after the first string.
     */
    fun pluckSynthStrum(
        hz: FloatArray,
        length: Int,
        fromSeconds: Double = 0.05,
        spreadSeconds: Double = 0.015,
        gains: FloatArray = FloatArray(hz.size) { 1f },
        sampleRate: Int = SAMPLE_RATE,
    ): FloatArray {
        val from = (fromSeconds * sampleRate).roundToInt()
        val total = from + length
        val mix = FloatArray(total)
        val tone = FloatArray(total)
        val synth = PluckSynth(sampleRate, durationSeconds = (total / sampleRate.toFloat() + 1f).coerceAtLeast(2f))
        for (i in hz.indices) {
            if (gains[i] == 0f) continue
            val delay = (i * spreadSeconds * sampleRate).roundToInt()
            tone.fill(0f)
            synth.start(hz[i])
            synth.render(tone, delay, total - delay)
            for (n in 0 until total) mix[n] += gains[i] * tone[n]
        }
        return scaled(mix.copyOfRange(from, total), PEAK)
    }

    /**
     * A strum of stiff strings: partial k of a string at `f` sits at `k·f·√(1 + B·k²)` (Fletcher
     * 1964), starts at 1/k with a random phase and decays with time constant
     * `decaySeconds / (1 + (k − 1) / 2)`. Partials stop below 6 kHz. [harmonicGain] can replace
     * the 1/k start, e.g. for a low string heard through a phone mic with a weak fundamental.
     */
    fun stiffStrum(
        hz: FloatArray,
        length: Int,
        inharmonicity: DoubleArray,
        harmonicGain: (string: Int, k: Int) -> Double = { _, k -> 1.0 / k },
        fromSeconds: Double = 0.05,
        spreadSeconds: Double = 0.015,
        decaySeconds: Double = 1.5,
        seed: Long = 7L,
        sampleRate: Int = SAMPLE_RATE,
    ): FloatArray {
        val random = Random(seed)
        val out = DoubleArray(length)
        for (i in hz.indices) {
            val onset = i * spreadSeconds
            var k = 1
            while (true) {
                val partial = k * hz[i] * sqrt(1 + inharmonicity[i] * k * k)
                if (partial > 6_000.0) break
                val phase = 2 * PI * random.nextDouble()
                val tau = decaySeconds / (1 + (k - 1) / 2.0)
                val gain = harmonicGain(i, k)
                for (n in 0 until length) {
                    val t = fromSeconds + n.toDouble() / sampleRate - onset
                    if (t < 0) continue
                    out[n] += gain * exp(-t / tau) * sin(2 * PI * partial * t + phase)
                }
                k++
            }
        }
        return scaled(FloatArray(length) { out[it].toFloat() }, PEAK)
    }

    private const val PEAK = 0.5f

    private fun scaled(x: FloatArray, peak: Float): FloatArray {
        val max = x.maxOf { kotlin.math.abs(it) }
        return if (max == 0f) x else FloatArray(x.size) { x[it] * peak / max }
    }
}
