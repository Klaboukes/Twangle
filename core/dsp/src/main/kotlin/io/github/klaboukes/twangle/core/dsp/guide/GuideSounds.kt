package io.github.klaboukes.twangle.core.dsp.guide

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The sound guide's sounds (Phase 94): what the phone plays for each of the guide's pulses ([GuidePulse]) when the
 * player hears the guide instead of (or besides) feeling it. Made once per sample rate and length by the player, never
 * in the audio loop.
 *
 * - [sharp] (the string too high, [GuidePulse.LOOSEN]) steps **up**, [flat] (too low, [GuidePulse.TIGHTEN]) steps
 *   **down**: the direction of the sound is where the string is, as the blind reviewer of 2026-11-23 asked (a tone that
 *   goes up while the string is sharp, down while it is flat).
 * - [inTune] is a soft bell, as long as the player picked (0.3, 1 or 2 s).
 *
 * **A stand-in:** these are round 2's pair C and its bell, exactly as `scripts/guide-sounds.py` makes them for the
 * website (`steps_c`, `bell`), until the blind reviewer picks from round 2. Their pick changes only [sharp] and [flat]
 * (a function per pair, as [stepsC]) and perhaps [inTune]; nothing else in the app needs to know.
 *
 * Every sound is mono in −1..1, its peak at [PEAK_DB] dBFS, and starts and ends on exactly 0 with raised-cosine fades,
 * so it never clicks. None holds a note on an open guitar string's pitch (E A D G B): F♯5, A♯5 and C5 are all at least
 * a semitone from one. They are all inside what the tuner listens for (up to 1,500 Hz), so the tuner sets each sound's
 * frames aside while it plays (`TunerViewModel`).
 */
object GuideSounds {

    /** Every sound's loudest sample, in dB below full scale (`PEAK_DB` in guide-sounds.py). */
    const val PEAK_DB = -12f

    /** F♯5 and A♯5 (equal temperament from A4 = 440 Hz), pair C's two notes, a major third apart. */
    const val FS5_HZ = 739.99f
    const val AS5_HZ = 932.33f

    /** C5, the bell's note: middle C an octave up (the reviewer suggested middle C; one octave up is clearer on a phone). */
    const val C5_HZ = 523.25f

    /** One blip of pair C, and when its second blip starts. */
    const val BLIP_SECONDS = 0.06f
    const val STEP_SECONDS = 0.085f

    /** Pair C's whole length: two 60 ms blips with 25 ms between. */
    const val STEPS_SECONDS = 0.145f

    /** The in-tune bell's shortest and default length. */
    const val SHORT_IN_TUNE_SECONDS = 0.3f

    /** The sound for "too high, loosen": the stand-in steps up. */
    fun sharp(sampleRate: Int): FloatArray = stepsC(up = true, sampleRate)

    /** The sound for "too low, tighten": the stand-in steps down. */
    fun flat(sampleRate: Int): FloatArray = stepsC(up = false, sampleRate)

    /** The sound for "in tune", [lengthSeconds] long: the stand-in bell. */
    fun inTune(lengthSeconds: Float, sampleRate: Int): FloatArray = bell(lengthSeconds, sampleRate)

    /** The sound for [pulse] ([GuidePulse.NONE] has none: an empty array). */
    fun of(pulse: GuidePulse, sampleRate: Int, inTuneSeconds: Float = SHORT_IN_TUNE_SECONDS): FloatArray = when (pulse) {
        GuidePulse.NONE -> FloatArray(0)
        GuidePulse.LOOSEN -> sharp(sampleRate)
        GuidePulse.TIGHTEN -> flat(sampleRate)
        GuidePulse.IN_TUNE -> inTune(inTuneSeconds, sampleRate)
    }

    /**
     * Round 2's pair C (`steps_c`): two soft blips a major third apart, F♯5 then A♯5 when [up], A♯5 then F♯5 when not,
     * each [BLIP_SECONDS] long, the second [STEP_SECONDS] after the first, [STEPS_SECONDS] in all.
     */
    fun stepsC(up: Boolean, sampleRate: Int): FloatArray {
        val first = if (up) FS5_HZ else AS5_HZ
        val second = if (up) AS5_HZ else FS5_HZ
        val step = samples(STEP_SECONDS, sampleRate)
        // As long as the second blip ends (STEPS_SECONDS, give or take a sample's rounding), so it is never cut short.
        val out = DoubleArray(step + samples(BLIP_SECONDS, sampleRate))
        addAt(out, blip(first, sampleRate), 0)
        addAt(out, blip(second, sampleRate), step)
        return finish(out)
    }

    /**
     * Round 2's soft bell (`bell`) on [frequencyHz], [lengthSeconds] long: the fundamental and three inharmonic partials
     * (2, 2.76 and 4.07 times, as a small bell's, at 0.3, 0.2 and 0.07), each dying away exponentially with a time
     * constant of a 2.5th of the length divided by the partial's ratio to the 0.8th power (the higher, the faster), so a
     * longer setting rings longer; a 4 ms rise and a gentle fade to 0 at its end (40 ms, or 15 % of a shorter length).
     */
    fun bell(lengthSeconds: Float, sampleRate: Int, frequencyHz: Float = C5_HZ): FloatArray {
        require(lengthSeconds > 0f) { "lengthSeconds must be positive, was $lengthSeconds" }
        val n = samples(lengthSeconds, sampleRate)
        val tau = lengthSeconds / 2.5
        val x = DoubleArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            var v = 0.0
            for (p in BELL_PARTIALS.indices step 2) {
                val ratio = BELL_PARTIALS[p]
                val amplitude = BELL_PARTIALS[p + 1]
                v += amplitude * exp(-t / (tau / ratio.pow(0.8))) * sin(2 * PI * frequencyHz * ratio * t)
            }
            x[i] = v
        }
        fadeEnds(x, 0.004, minOf(0.04, 0.15 * lengthSeconds), sampleRate)
        return finish(x)
    }

    /** One soft note (`blip`): a sine with a tenth of its octave, easing down (time constant 40 ms), 6 ms in, 15 ms out. */
    private fun blip(frequencyHz: Float, sampleRate: Int): DoubleArray {
        val n = samples(BLIP_SECONDS, sampleRate)
        val x = DoubleArray(n)
        var phase = 0.0
        val step = 2 * PI * frequencyHz / sampleRate
        for (i in 0 until n) {
            x[i] = (sin(phase) + 0.1 * sin(2 * phase)) * exp(-i.toDouble() / sampleRate / 0.040)
            phase += step
        }
        fadeEnds(x, 0.006, 0.015, sampleRate)
        return x
    }

    /** The sound's last steps (`normalize(remove_dc(…))`): no DC, peak at [PEAK_DB], as floats. */
    private fun finish(x: DoubleArray): FloatArray {
        removeDc(x)
        var peak = 0.0
        for (v in x) peak = maxOf(peak, abs(v))
        val k = if (peak > 0.0) 10.0.pow(PEAK_DB / 20.0) / peak else 1.0
        return FloatArray(x.size) { (x[it] * k).toFloat() }
    }

    /** Adds [part] into [out] from sample [at] on; what would run past its end is left out (`mix`). */
    private fun addAt(out: DoubleArray, part: DoubleArray, at: Int) {
        for (i in part.indices) {
            val j = at + i
            if (j >= out.size) break
            out[j] += part[i]
        }
    }

    /** Raised-cosine fades at both ends (`fade_ends`); the first and last samples become exactly 0. */
    private fun fadeEnds(x: DoubleArray, fadeInSeconds: Double, fadeOutSeconds: Double, sampleRate: Int) {
        val n = x.size
        if (n == 0) return
        val a = (fadeInSeconds * sampleRate).toInt()
        val b = (fadeOutSeconds * sampleRate).toInt()
        for (i in 0 until n) {
            var g = 1.0
            if (i < a) g *= cosIn(i, a)
            if (n - 1 - i < b) g *= cosIn(n - 1 - i, b)
            x[i] *= g
        }
        x[0] = 0.0
        x[n - 1] = 0.0
    }

    /** A raised-cosine rise over [n] samples: 0 at i = 0, 1 at i = n (`cos_in`). */
    private fun cosIn(i: Int, n: Int): Double = if (n > 0) 0.5 - 0.5 * cos(PI * minOf(i, n) / n) else 1.0

    /** Subtracts the mean under a Hann window (`remove_dc`), so the sound still starts and ends at 0. */
    private fun removeDc(x: DoubleArray) {
        val n = x.size
        if (n < 2) return
        var sum = 0.0
        var weights = 0.0
        for (i in 0 until n) {
            sum += x[i]
            weights += hann(i, n)
        }
        val k = sum / weights
        for (i in 0 until n) x[i] -= k * hann(i, n)
    }

    private fun hann(i: Int, n: Int): Double = 0.5 - 0.5 * cos(2 * PI * i / (n - 1))

    private fun samples(seconds: Float, sampleRate: Int): Int = (seconds.toDouble() * sampleRate).roundToInt()

    /** The bell's partials as (ratio, amplitude) pairs, flat. */
    private val BELL_PARTIALS = doubleArrayOf(1.0, 1.0, 2.0, 0.30, 2.76, 0.20, 4.07, 0.07)
}
