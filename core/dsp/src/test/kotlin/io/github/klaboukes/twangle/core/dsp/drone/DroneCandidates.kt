package io.github.klaboukes.twangle.core.dsp.drone

import io.github.klaboukes.twangle.core.dsp.synth.BodyMode
import io.github.klaboukes.twangle.core.dsp.synth.StringSynth
import io.github.klaboukes.twangle.core.dsp.synth.StringVoices
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Phase 48, "Hear them first": the candidate sounds for the drone under the Scales tool, made the
 * way [StringSynth] is (additive partials at exact frequencies, the body's resonances as
 * [BodyMode]s, seeded noise), so there are no recordings and nothing to licence. Test sources only:
 * [DroneCandidatesReport] renders them into `docs/design/drone-sounds-preview/` for the owner to
 * pick from; the picked one is built into `:core:dsp` in the next increment.
 *
 * Every candidate is a steady sound with no beginning or end of its own: [render] makes [frames]
 * samples of it, and the report puts the fades on. Each note is exactly on its frequency (the
 * partials of the additive candidates are phasors turned by exact steps; the cello is the tuner's
 * own [StringSynth]).
 */
internal enum class DroneCandidate(val file: String) {
    /** The tuner's bowed cello reference voice ([StringVoices.CELLO]), held: the bow never lifts. */
    CELLO("cello"),

    /** A soft flue organ: a quiet 8' principal with a 4' octave and a 2' fifteenth, perfectly still. */
    ORGAN("organ"),

    /** A tanpura-like hum: a bright, buzzy tone whose overtones slowly sweep up and down (the jawari bridge's shimmer). */
    TANPURA("tanpura"),

    /** A shruti box (a small harmonium of free reeds, the singers' drone): reedy, nasal, breathing with the bellows. */
    SHRUTI_BOX("shruti-box"),
}

internal object DroneCandidates {

    /**
     * [frames] samples at [sampleRate] of [candidate] sounding [notes] together (each a frequency in
     * Hz with its gain), unscaled. Voice i of a chord gets its own noise and movement (seed, phase
     * of the sway or shimmer), as two strings or two pipes never move together.
     */
    fun render(candidate: DroneCandidate, sampleRate: Int, notes: List<Pair<Double, Double>>, frames: Int): DoubleArray {
        val out = DoubleArray(frames)
        for ((i, note) in notes.withIndex()) {
            val (hz, gain) = note
            val voice = when (candidate) {
                DroneCandidate.CELLO -> cello(sampleRate, hz, frames, i)
                DroneCandidate.ORGAN -> organ(sampleRate, hz, frames)
                DroneCandidate.TANPURA -> tanpura(sampleRate, hz, frames, i)
                DroneCandidate.SHRUTI_BOX -> shrutiBox(sampleRate, hz, frames, i)
            }
            for (n in 0 until frames) out[n] += gain * voice[n]
        }
        return out
    }

    /**
     * The tuner's cello ([StringVoices.CELLO]: bow noise through the body's four modes, the bow's
     * pressure swaying the level by up to ±6 %), rendered longer than needed so its end fade (the
     * bow lifting) is never reached. [StringSynth]'s noise has a fixed seed, so voice i starts
     * `i × CELLO_VOICE_OFFSET_SECONDS` into its own note: the voices' noise and sway don't line up.
     */
    private fun cello(rate: Int, hz: Double, frames: Int, voice: Int): DoubleArray {
        val skip = (voice * CELLO_VOICE_OFFSET_SECONDS * rate).toInt()
        val seconds = (frames + skip).toFloat() / rate + StringSynth.END_FADE_SECONDS + 1f
        val synth = StringSynth(rate, StringVoices.CELLO, seconds)
        synth.start(hz.toFloat())
        val tone = FloatArray(frames + skip)
        check(synth.render(tone) == tone.size)
        return DoubleArray(frames) { tone[skip + it].toDouble() }
    }

    /**
     * Soft flue pipes, drawn together: an 8' principal (partial k at 1/k^1.2, a gentle slope), a 4'
     * octave raising the 2nd partial (×1.5) and a 2' fifteenth the 4th (×1.4), rolled off above
     * 2.5 kHz. The upper ranks put enough of it above 400 Hz for a phone's speaker. Nothing moves.
     */
    private fun organ(rate: Int, hz: Double, frames: Int): DoubleArray =
        additive(rate, hz, frames) { k, f, _ ->
            val rank = when (k) {
                2 -> 1.5
                4 -> 1.4
                else -> 1.0
            }
            rank / k.toDouble().pow(1.2) * rollOff(f, 2_500.0)
        }

    /**
     * A tanpura-like hum. The jawari, the tanpura's wide curved bridge, lets the string graze it, so
     * energy moves up into high partials and back as the note sounds, the "buzz" with a moving ring
     * in it (Raman 1921; Valette 1993). Here: rich partials (1/k^0.85, rolled off above 4.5 kHz),
     * and two bumps of a few partials each, up to 4.5× and 3.5× louder, that sweep slowly up and
     * down the partials (log2 k between 1.3 and 3.5, i.e. partials 2.5–11, and 2.9–5.7, i.e.
     * partials 7.5–52 where the pitch allows), at 3.4 s and 5.3 s periods so the pattern doesn't
     * repeat soon. The sweep moves the colour, not the loudness: the partials' energy stays the same.
     */
    private fun tanpura(rate: Int, hz: Double, frames: Int, voice: Int): DoubleArray {
        val phase = voice * VOICE_PHASE
        val count = partialCount(rate, hz)
        fun raw(k: Int, t: Double): Double {
            val lk = ln(k.toDouble()) / ln(2.0)
            val c1 = 2.4 + 1.1 * sin(2 * PI * t / 3.4 + phase)
            val c2 = 4.3 + 1.4 * sin(2 * PI * t / 5.3 + 1.7 + phase)
            val bumps = 1 + 3.5 * bump(lk, c1) + 2.5 * bump(lk, c2)
            return bumps / k.toDouble().pow(0.85) * rollOff(k * hz, 4_500.0)
        }
        // The sweep moves the colour, not the loudness: the partials' energy is held constant.
        var normAt = Double.NaN
        var norm = 1.0
        return additive(rate, hz, frames) { k, _, t ->
            if (t != normAt) {
                normAt = t
                norm = 1 / sqrt((1..count).sumOf { raw(it, t).pow(2) })
            }
            raw(k, t) * norm
        }
    }

    /**
     * A shruti box: free reeds swinging through a slot give a pulse-like spectrum (partial k at 1/k,
     * the even ones softer, ×0.6), coloured by the box (a nasal hill near 1.3 kHz, a little body
     * near 450 Hz) and rolled off above 3.5 kHz. The bellows breathe: the level swells and sinks by
     * a few percent, twice as much in the high partials (more pressure, brighter reeds), at 2.9 s
     * and 1.13 s periods, with a breath of air noise (2 kHz band) under it that follows the bellows.
     */
    private fun shrutiBox(rate: Int, hz: Double, frames: Int, voice: Int): DoubleArray {
        val phase = voice * VOICE_PHASE
        val nasal = BodyMode(1_300.0, 3.0, 6.0)
        val box = BodyMode(450.0, 4.0, 3.0)
        fun breath(t: Double) = 0.7 * sin(2 * PI * t / 2.9 + phase) + 0.3 * sin(2 * PI * t / 1.13 + 2 * phase)
        val tone = additive(rate, hz, frames) { k, f, t ->
            val even = if (k % 2 == 0) 0.6 else 1.0
            val pressure = 1 + 0.05 * breath(t) * (1 + f / 1_500.0)
            pressure * even / k * nasal.gainAt(f) * box.gainAt(f) * rollOff(f, 3_500.0)
        }
        // Air: white noise through a band-pass (RBJ cookbook, Q 0.8) at 2 kHz, a little louder as the bellows press.
        val noise = bandPassedNoise(rate, 2_000.0, 0.8, frames, SEED + voice)
        val peak = tone.maxOf { kotlin.math.abs(it) }
        val noisePeak = noise.maxOf { kotlin.math.abs(it) }
        for (n in 0 until frames) tone[n] += AIR * peak / noisePeak * noise[n] * (1 + 0.3 * breath(n.toDouble() / rate))
        return tone
    }

    /** A bump of a few partials around [center] (in octaves, log2 k), 0.28 octaves wide. */
    private fun bump(lk: Double, center: Double): Double {
        val d = lk - center
        return exp(-d * d / (2 * 0.28 * 0.28))
    }

    /** Two poles above [hz], as [io.github.klaboukes.twangle.core.dsp.synth.StringVoice.partialLevel]'s brightness. */
    private fun rollOff(f: Double, hz: Double): Double {
        val x = f / hz
        return 1 / sqrt(1 + x * x * x * x)
    }

    /**
     * Harmonic partials of [hz] up to [StringSynth.MAX_PARTIAL_HZ] (and 0.45 × the rate), each a
     * phasor turned by its exact step; partial k's level is [level] (k, its frequency, the time in
     * seconds), recomputed every [BLOCK] samples and ramped across the block. The partials start at
     * phases `k²·π/K` (Schroeder 1970), which keeps the waveform's peaks low for its loudness.
     */
    private fun additive(rate: Int, hz: Double, frames: Int, level: (Int, Double, Double) -> Double): DoubleArray {
        val count = partialCount(rate, hz)
        val re = DoubleArray(count)
        val im = DoubleArray(count)
        val stepRe = DoubleArray(count) { cos(2 * PI * (it + 1) * hz / rate) }
        val stepIm = DoubleArray(count) { sin(2 * PI * (it + 1) * hz / rate) }
        for (i in 0 until count) {
            val phi = PI * (i + 1) * (i + 1) / count
            re[i] = cos(phi)
            im[i] = sin(phi)
        }
        val now = DoubleArray(count) { level(it + 1, (it + 1) * hz, 0.0) }
        val next = DoubleArray(count)
        val out = DoubleArray(frames)
        var start = 0
        while (start < frames) {
            val end = minOf(frames, start + BLOCK)
            for (i in 0 until count) next[i] = level(i + 1, (i + 1) * hz, end.toDouble() / rate)
            for (n in start until end) {
                val x = (n - start).toDouble() / (end - start)
                var sum = 0.0
                for (i in 0 until count) {
                    val r = re[i]
                    sum += (now[i] + (next[i] - now[i]) * x) * im[i]
                    re[i] = r * stepRe[i] - im[i] * stepIm[i]
                    im[i] = r * stepIm[i] + im[i] * stepRe[i]
                }
                out[n] = sum
            }
            // Keep every phasor on the unit circle, so rounding never changes a level.
            for (i in 0 until count) {
                val m = 1 / sqrt(re[i] * re[i] + im[i] * im[i])
                re[i] *= m
                im[i] *= m
            }
            now.indices.forEach { now[it] = next[it] }
            start = end
        }
        return out
    }

    /** How many harmonics of [hz] [additive] plays: up to [StringSynth.MAX_PARTIAL_HZ] and 0.45 × the rate. */
    private fun partialCount(rate: Int, hz: Double): Int =
        minOf(StringSynth.MAX_PARTIALS, (minOf(StringSynth.MAX_PARTIAL_HZ.toDouble(), 0.45 * rate) / hz).toInt())

    private fun bandPassedNoise(rate: Int, hz: Double, q: Double, frames: Int, seed: Int): DoubleArray {
        val w0 = 2 * PI * hz / rate
        val alpha = sin(w0) / (2 * q)
        val a0 = 1 + alpha
        val b0 = alpha / a0
        val a1 = -2 * cos(w0) / a0
        val a2 = (1 - alpha) / a0
        var state = seed
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        return DoubleArray(frames) {
            // xorshift32 (Marsaglia 2003), as StringSynth's noise.
            state = state xor (state shl 13)
            state = state xor (state ushr 17)
            state = state xor (state shl 5)
            val x = state * (1.0 / Int.MAX_VALUE)
            val y = b0 * x - b0 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
            y
        }
    }

    /** Levels are recomputed this often (samples): a sweep or a breath moves smoothly at this step. */
    private const val BLOCK = 64

    /** The cello's voices start this far apart in their own notes, so their bows don't sway together. */
    private const val CELLO_VOICE_OFFSET_SECONDS = 0.37f

    /** Voices of a chord move this far apart in phase (radians), so the fifth doesn't breathe or shimmer with the root. */
    private const val VOICE_PHASE = 2.1

    /** The shruti box's air noise peaks at this share of the reeds' peak. */
    private const val AIR = 0.025

    private const val SEED = 0x1F3D5B79
}
