package com.intune.core.dsp.testsignals

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Synthetic audio for testing pitch detection, where the true frequency is known exactly.
 *
 * Every generator returns a new mono [FloatArray] in −1..1. Harmonic-rich waveforms are built by
 * additive synthesis and stop below [maxHarmonicHz] (default: Nyquist), so they are band-limited
 * and free of aliasing, like real audio after the phone's anti-aliasing filter. Noise uses a
 * seeded generator, so every run produces the same samples.
 */
object TestSignals {

    const val SAMPLE_RATE = 48_000
    const val DEFAULT_LENGTH = 4096
    const val DEFAULT_AMPLITUDE = 0.5f

    /**
     * Harmonic amplitudes of a low guitar string as heard by a phone mic: the 2nd harmonic is
     * much louder than the fundamental, which is what tempts detectors into octave errors.
     */
    val WEAK_FUNDAMENTAL_HARMONICS = floatArrayOf(0.3f, 1.0f, 0.7f, 0.4f, 0.3f, 0.2f)

    /** Pure sine with peak [amplitude], starting at [phase] radians. */
    fun sine(
        frequencyHz: Float,
        length: Int = DEFAULT_LENGTH,
        sampleRate: Int = SAMPLE_RATE,
        amplitude: Float = DEFAULT_AMPLITUDE,
        phase: Double = 0.0,
    ): FloatArray {
        val step = 2 * PI * frequencyHz / sampleRate
        return FloatArray(length) { (amplitude * sin(phase + step * it)).toFloat() }
    }

    /**
     * Sum of harmonics of [frequencyHz]: `harmonicAmplitudes[k − 1]` is the relative amplitude of
     * harmonic k. Harmonics at or above [maxHarmonicHz] are left out. The result is scaled so its
     * peak is [amplitude].
     */
    fun harmonics(
        frequencyHz: Float,
        harmonicAmplitudes: FloatArray,
        length: Int = DEFAULT_LENGTH,
        sampleRate: Int = SAMPLE_RATE,
        amplitude: Float = DEFAULT_AMPLITUDE,
        maxHarmonicHz: Float = sampleRate / 2f,
    ): FloatArray = additive(frequencyHz, harmonicAmplitudes.size, length, sampleRate, amplitude, maxHarmonicHz) { k, _ ->
        harmonicAmplitudes[k - 1].toDouble()
    }

    /** Band-limited sawtooth: every harmonic k at amplitude 1/k. Very bright, like a pick attack. */
    fun sawtooth(
        frequencyHz: Float,
        length: Int = DEFAULT_LENGTH,
        sampleRate: Int = SAMPLE_RATE,
        amplitude: Float = DEFAULT_AMPLITUDE,
        maxHarmonicHz: Float = sampleRate / 2f,
    ): FloatArray = additive(frequencyHz, Int.MAX_VALUE, length, sampleRate, amplitude, maxHarmonicHz) { k, _ ->
        1.0 / k
    }

    /** Band-limited square wave: odd harmonics k at amplitude 1/k, even harmonics absent. */
    fun square(
        frequencyHz: Float,
        length: Int = DEFAULT_LENGTH,
        sampleRate: Int = SAMPLE_RATE,
        amplitude: Float = DEFAULT_AMPLITUDE,
        maxHarmonicHz: Float = sampleRate / 2f,
    ): FloatArray = additive(frequencyHz, Int.MAX_VALUE, length, sampleRate, amplitude, maxHarmonicHz) { k, _ ->
        if (k % 2 == 1) 1.0 / k else 0.0
    }

    /** A note whose fundamental is much weaker than its 2nd harmonic ([WEAK_FUNDAMENTAL_HARMONICS]). */
    fun weakFundamental(
        frequencyHz: Float,
        length: Int = DEFAULT_LENGTH,
        sampleRate: Int = SAMPLE_RATE,
        amplitude: Float = DEFAULT_AMPLITUDE,
    ): FloatArray = harmonics(frequencyHz, WEAK_FUNDAMENTAL_HARMONICS, length, sampleRate, amplitude)

    /**
     * A plucked string: harmonics at 1/k that die away exponentially, the higher ones faster
     * (harmonic k decays with time constant `decaySeconds / (1 + (k − 1) / 2)`). The first sample
     * is the attack; [amplitude] is the peak.
     */
    fun pluck(
        frequencyHz: Float,
        length: Int = DEFAULT_LENGTH,
        sampleRate: Int = SAMPLE_RATE,
        amplitude: Float = DEFAULT_AMPLITUDE,
        decaySeconds: Double = 0.5,
        harmonicCount: Int = 12,
    ): FloatArray = additive(frequencyHz, harmonicCount, length, sampleRate, amplitude, sampleRate / 2f) { k, t ->
        exp(-t * (1 + (k - 1) / 2.0) / decaySeconds) / k
    }

    /** Gaussian white noise with RMS [rms]. The same [seed] always gives the same samples. */
    fun noise(length: Int = DEFAULT_LENGTH, rms: Float = 0.1f, seed: Long = 1L): FloatArray {
        val random = Random(seed)
        return FloatArray(length) { (rms * random.nextGaussian()).toFloat() }
    }

    /** [signal] plus white noise, at a signal-to-noise ratio of [snrDb] (RMS based). */
    fun withNoise(signal: FloatArray, snrDb: Float, seed: Long = 1L): FloatArray {
        val noiseRms = (rms(signal) / 10.0.pow(snrDb / 20.0)).toFloat()
        val noise = noise(signal.size, noiseRms, seed)
        return FloatArray(signal.size) { signal[it] + noise[it] }
    }

    /** Root-mean-square value of [signal]. */
    fun rms(signal: FloatArray): Double {
        if (signal.isEmpty()) return 0.0
        var sum = 0.0
        for (s in signal) sum += s.toDouble() * s
        return sqrt(sum / signal.size)
    }

    // Additive synthesis: sum over k of gain(k, t) · sin(2π k f t), for harmonics below maxHz,
    // then scaled to the requested peak.
    private inline fun additive(
        frequencyHz: Float,
        maxHarmonics: Int,
        length: Int,
        sampleRate: Int,
        amplitude: Float,
        maxHz: Float,
        gain: (k: Int, t: Double) -> Double,
    ): FloatArray {
        require(frequencyHz > 0f && frequencyHz < maxHz) { "frequency must be in (0, $maxHz) Hz, was $frequencyHz" }
        val harmonicCount = minOf(maxHarmonics.toLong(), ((maxHz - 1e-3f) / frequencyHz).toLong()).toInt()
        val samples = DoubleArray(length)
        for (k in 1..harmonicCount) {
            val step = 2 * PI * k * frequencyHz / sampleRate
            for (i in 0 until length) samples[i] += gain(k, i.toDouble() / sampleRate) * sin(step * i)
        }
        val peak = samples.maxOf { abs(it) }
        val scale = if (peak == 0.0) 0.0 else amplitude / peak
        return FloatArray(length) { (samples[it] * scale).toFloat() }
    }
}
