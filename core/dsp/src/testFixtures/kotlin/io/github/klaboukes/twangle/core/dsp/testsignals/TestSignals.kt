package io.github.klaboukes.twangle.core.dsp.testsignals

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
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

    /**
     * Like [pluck], but harmonic k starts at `harmonicAmplitudes[k − 1]` instead of 1/k, e.g. a
     * high string heard with a weak fundamental and strong 2nd/3rd harmonics. Harmonics at or above
     * Nyquist are left out.
     */
    fun pluck(
        frequencyHz: Float,
        harmonicAmplitudes: FloatArray,
        length: Int = DEFAULT_LENGTH,
        sampleRate: Int = SAMPLE_RATE,
        amplitude: Float = DEFAULT_AMPLITUDE,
        decaySeconds: Double = 0.5,
    ): FloatArray = additive(frequencyHz, harmonicAmplitudes.size, length, sampleRate, amplitude, sampleRate / 2f) { k, t ->
        harmonicAmplitudes[k - 1] * exp(-t * (1 + (k - 1) / 2.0) / decaySeconds)
    }

    /**
     * Mains-style hum or a machine drone: [baseHz] and its harmonics up to [harmonicCount], each at
     * 1/k, scaled to an RMS of [rms]. Real rooms hum at 50 Hz (Europe) or 60 Hz (Americas).
     */
    fun hum(
        baseHz: Float = 50f,
        length: Int = DEFAULT_LENGTH,
        sampleRate: Int = SAMPLE_RATE,
        rms: Float = 0.05f,
        harmonicCount: Int = 6,
    ): FloatArray {
        val raw = additive(baseHz, harmonicCount, length, sampleRate, 1f, sampleRate / 2f) { k, _ -> 1.0 / k }
        val scale = (rms / rms(raw)).toFloat()
        return FloatArray(length) { raw[it] * scale }
    }

    /**
     * A tick, knock or click: damped resonances at [frequenciesHz], each dying away with time
     * constant [decaySeconds] (a clock's tick a few ms, a knock on wood 10–30 ms), summed and scaled
     * so the peak is [amplitude]. Starts at full strength on the first sample; the rest of the
     * [length] samples ring out to silence. Rings like these fooled the tuner into showing a note.
     */
    fun tick(
        frequenciesHz: FloatArray,
        decaySeconds: Double,
        length: Int = DEFAULT_LENGTH,
        sampleRate: Int = SAMPLE_RATE,
        amplitude: Float = DEFAULT_AMPLITUDE,
    ): FloatArray {
        val raw = DoubleArray(length) { i ->
            val t = i.toDouble() / sampleRate
            var s = 0.0
            for ((k, f) in frequenciesHz.withIndex()) s += sin(2 * PI * f * t + PI / 2 * k)
            s * exp(-t / decaySeconds)
        }
        val peak = raw.maxOf { abs(it) }
        return FloatArray(length) { (amplitude * raw[it] / peak).toFloat() }
    }

    /** [signal] plus [interference] scaled so that the signal-to-interference ratio is [snrDb] (RMS based). */
    fun mix(signal: FloatArray, interference: FloatArray, snrDb: Float): FloatArray {
        require(interference.size >= signal.size) { "interference is shorter than the signal" }
        val scale = (rms(signal) / 10.0.pow(snrDb / 20.0) / rms(interference)).toFloat()
        return FloatArray(signal.size) { signal[it] + scale * interference[it] }
    }

    /**
     * [signal] through a 4th-order Butterworth band-pass: two high-pass biquads at [lowHz] and two
     * low-pass biquads at [highHz] (R. Bristow-Johnson, "Audio EQ Cookbook"). Models the narrow
     * band of a Bluetooth headset mic: about 300–3400 Hz for narrowband (CVSD, 8 kHz) calls and
     * 100–7000 Hz for wideband (mSBC, 16 kHz). Pass 0 as [lowHz] to skip the high-pass.
     */
    fun bandLimit(signal: FloatArray, sampleRate: Int = SAMPLE_RATE, lowHz: Float, highHz: Float): FloatArray {
        require(highHz < sampleRate / 2f && lowHz < highHz) { "need lowHz < highHz < Nyquist" }
        val out = signal.copyOf()
        // Butterworth Qs for two cascaded 2nd-order sections.
        for (q in doubleArrayOf(1.0 / (2 * cos(PI / 8)), 1.0 / (2 * cos(3 * PI / 8)))) {
            if (lowHz > 0f) biquad(out, sampleRate, lowHz, q, highPass = true)
            biquad(out, sampleRate, highHz, q, highPass = false)
        }
        return out
    }

    // In-place Cookbook biquad (low- or high-pass), direct form I.
    private fun biquad(x: FloatArray, sampleRate: Int, cutoffHz: Float, q: Double, highPass: Boolean) {
        val w0 = 2 * PI * cutoffHz / sampleRate
        val alpha = sin(w0) / (2 * q)
        val c = cos(w0)
        val a0 = 1 + alpha
        val b1 = (if (highPass) -(1 + c) else 1 - c) / a0
        val b0 = abs(b1) / 2
        val b2 = b0
        val a1 = -2 * c / a0
        val a2 = (1 - alpha) / a0
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        for (i in x.indices) {
            val x0 = x[i].toDouble()
            val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
            x[i] = y0.toFloat()
        }
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
