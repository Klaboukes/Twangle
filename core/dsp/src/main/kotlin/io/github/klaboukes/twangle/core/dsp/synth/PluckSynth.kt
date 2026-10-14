package io.github.klaboukes.twangle.core.dsp.synth

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A plucked-string tone at an exact frequency, for the reference-tone player: the note sounds, dies
 * away and ends by itself after [durationSeconds].
 *
 * Additive synthesis of an ideal plucked string (Fletcher & Rossing, *The Physics of Musical
 * Instruments*, 2nd ed. 1998, ch. 2):
 * - harmonic k of a string plucked near one end starts at amplitude ∝ sin(kπβ)/k² ≈ πβ/k for a
 *   small pluck point β, so harmonic k starts at 1/k;
 * - each harmonic decays exponentially and the higher ones faster, as on a real string: harmonic k
 *   with time constant `DECAY_SECONDS / (1 + (k − 1) / 2)` (the same shape as the test signals'
 *   `TestSignals.pluck`, which the detector is tested on);
 * - harmonics stop below [MAX_PARTIAL_HZ] and 0.45 × the sample rate, so nothing aliases, with at
 *   most [MAX_HARMONICS]. Low notes keep many harmonics, so a phone speaker that can't play the
 *   fundamental still gives the right pitch.
 *
 * Each harmonic is a decaying phasor turned by a fixed complex step every sample
 * (`z ← r·e^{iω}·z`, output `Im z`), so the pitch is exact (no wavetable rounding) and a sample costs
 * a few multiply-adds per harmonic. In double precision the rotation drifts far less than 0.01 cent
 * over a tone.
 *
 * The envelope keeps it free of clicks: a [ATTACK_SECONDS] raised-cosine fade-in (the sines start
 * at 0 anyway), and a raised-cosine fade-out that ends on exactly 0, over the last
 * [END_FADE_SECONDS] or, after [release], over the time asked for. The level is scaled so the sum of
 * all harmonic amplitudes is [PEAK]: the output can never pass it, so it never clips.
 *
 * [start], [release] and [render] allocate nothing. Not thread-safe: one thread renders.
 */
class PluckSynth(
    val sampleRate: Int,
    val durationSeconds: Float = DEFAULT_DURATION_SECONDS,
) {

    init {
        require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }
        require(durationSeconds > END_FADE_SECONDS + ATTACK_SECONDS) {
            "durationSeconds must be longer than the attack and end fade, was $durationSeconds"
        }
    }

    /** Highest frequency [start] accepts: harmonics above it would alias or be inaudible. */
    val maxFrequencyHz: Float = minOf(MAX_PARTIAL_HZ, NYQUIST_FRACTION * sampleRate)

    private val totalSamples = (durationSeconds * sampleRate).roundToInt()
    private val attackSamples = (ATTACK_SECONDS * sampleRate).roundToInt().coerceAtLeast(1)
    private val endFadeSamples = (END_FADE_SECONDS * sampleRate).roundToInt().coerceAtLeast(1)

    // Per harmonic: the phasor (re, im) and its per-sample step r·(cos ω, sin ω).
    private val re = DoubleArray(MAX_HARMONICS)
    private val im = DoubleArray(MAX_HARMONICS)
    private val stepRe = DoubleArray(MAX_HARMONICS)
    private val stepIm = DoubleArray(MAX_HARMONICS)
    private var harmonics = 0

    private var position = 0
    private var sounding = false

    // Fade-out in progress: fadeLength samples from level fadeFrom down to 0. fadeLength 0 = none.
    private var fadePosition = 0
    private var fadeLength = 0
    private var fadeFrom = 1.0

    /** True from [start] until the tone has faded to silence. */
    val isSounding: Boolean get() = sounding

    /** Frequency of the tone last started, in Hz (0 before the first [start]). */
    var frequencyHz: Float = 0f
        private set

    /**
     * Starts a new tone at [frequencyHz] (0 < f ≤ [maxFrequencyHz]) from the beginning, dropping any
     * tone still sounding. To change notes without a click, [release] the old one and start the new
     * one once [isSounding] is false.
     */
    fun start(frequencyHz: Float) {
        require(frequencyHz > 0f && frequencyHz <= maxFrequencyHz) {
            "frequencyHz must be in (0, $maxFrequencyHz], was $frequencyHz"
        }
        this.frequencyHz = frequencyHz
        harmonics = floor(maxFrequencyHz / frequencyHz).toInt().coerceIn(1, MAX_HARMONICS)
        var amplitudeSum = 0.0
        for (h in 0 until harmonics) amplitudeSum += 1.0 / (h + 1)
        val level = PEAK / amplitudeSum
        for (h in 0 until harmonics) {
            val k = h + 1
            val omega = 2.0 * PI * k * frequencyHz / sampleRate
            val timeConstant = DECAY_SECONDS / (1.0 + (k - 1) / 2.0)
            val r = exp(-1.0 / (timeConstant * sampleRate))
            stepRe[h] = r * cos(omega)
            stepIm[h] = r * sin(omega)
            re[h] = level / k
            im[h] = 0.0
        }
        position = 0
        fadePosition = 0
        fadeLength = 0
        fadeFrom = 1.0
        sounding = true
    }

    /**
     * Fades the tone out over [seconds] (at least one sample), ending on silence. A fade already
     * running that ends sooner is kept; a longer one is cut short smoothly, from its current level.
     */
    fun release(seconds: Float = STOP_FADE_SECONDS) {
        if (!sounding) return
        // Never past the tone's natural end.
        val length = (seconds * sampleRate).roundToInt().coerceIn(1, maxOf(1, totalSamples - position))
        if (fadeLength > 0 && fadeLength - fadePosition <= length) return
        fadeFrom = currentFadeLevel()
        fadeLength = length
        fadePosition = 0
    }

    /**
     * Writes up to [count] samples (−[PEAK]..[PEAK]) into [out] from [offset] and returns how many it
     * wrote: fewer than [count] when the tone ends, 0 once it has (or before [start]).
     */
    fun render(out: FloatArray, offset: Int = 0, count: Int = out.size - offset): Int {
        require(offset >= 0 && count >= 0 && offset + count <= out.size) { "offset/count outside the array" }
        var written = 0
        while (written < count && sounding) {
            if (fadeLength == 0 && position >= totalSamples - endFadeSamples) {
                fadeLength = endFadeSamples
                fadePosition = 0
                fadeFrom = 1.0
            }
            var sum = 0.0
            for (h in 0 until harmonics) {
                val r = re[h]
                val i = im[h]
                re[h] = r * stepRe[h] - i * stepIm[h]
                im[h] = r * stepIm[h] + i * stepRe[h]
                sum += i
            }
            var gain = if (position < attackSamples) raisedCosine(position.toDouble() / attackSamples) else 1.0
            if (fadeLength > 0) {
                fadePosition++
                gain *= currentFadeLevel()
                if (fadePosition >= fadeLength) sounding = false
            }
            out[offset + written] = (sum * gain).toFloat()
            written++
            position++
        }
        return written
    }

    // Level of the fade-out at fadePosition: fadeFrom → 0 along half a cosine, exactly 0 at the end.
    private fun currentFadeLevel(): Double =
        if (fadeLength == 0) 1.0 else fadeFrom * (1.0 - raisedCosine(fadePosition.toDouble() / fadeLength))

    // 0 → 1 along half a cosine as x goes 0 → 1.
    private fun raisedCosine(x: Double): Double = 0.5 - 0.5 * cos(PI * x)

    companion object {
        /** Length of a tone from [start] to silence. */
        const val DEFAULT_DURATION_SECONDS = 4f

        /** Fade-in at the start of a tone, so it doesn't click. */
        const val ATTACK_SECONDS = 0.005f

        /** Fade-out at the natural end of a tone. */
        const val END_FADE_SECONDS = 0.6f

        /** Default fade-out for [release], e.g. when the player is stopped: quick, but no click. */
        const val STOP_FADE_SECONDS = 0.03f

        /** Time constant of the fundamental's exponential decay; higher harmonics die faster. */
        const val DECAY_SECONDS = 1.5

        /** Largest possible sample value: about −2 dBFS, so the tone never clips. */
        const val PEAK = 0.8

        /** Most harmonics a tone uses (a low bass note). */
        const val MAX_HARMONICS = 32

        /** No harmonic above this frequency: enough brightness for a pluck, little work per sample. */
        const val MAX_PARTIAL_HZ = 6_000f

        private const val NYQUIST_FRACTION = 0.45f
    }
}
