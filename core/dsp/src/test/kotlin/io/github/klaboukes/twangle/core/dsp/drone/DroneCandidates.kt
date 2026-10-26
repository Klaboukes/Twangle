package io.github.klaboukes.twangle.core.dsp.drone

import io.github.klaboukes.twangle.core.dsp.synth.BodyMode
import io.github.klaboukes.twangle.core.dsp.synth.StringSynth
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Phase 48, "Hear them first", round 2: the candidate sounds for the drone under the Scales tool,
 * made the way [StringSynth] is (additive partials at exact frequencies, resonances as
 * [BodyMode]s, seeded noise), so there are no recordings and nothing to licence. Test sources only:
 * [DroneCandidatesReport] renders them into `docs/design/drone-sounds-preview/` for the owner to
 * pick from. The owner picked all four (2026-10-26); the app plays them through `DroneSynth`.
 *
 * Round 1's four (the cello held, a soft organ, a steady tanpura-like hum, a shruti box) were
 * steady stacks of harmonics with slopes close to each other, and the owner heard them as nearly
 * the same sound (2026-10-26). Round 2's four are each a different kind of instrument: a plucked
 * drone that is re-plucked, a pair of beating reeds, a nearly pure pipe and a filtered synth pad.
 * They differ in brightness, in how much of them is above a phone speaker's low end, and in how
 * they move (plucks, beating, nothing, a slow filter).
 *
 * [render] makes [frames] samples, unscaled; the report puts the fades on. Every note's average
 * pitch is exact: harmonic partials of exact frequencies, and where a note has detuned voices
 * (the reeds, the pad) they sit symmetrically in cents around it.
 */
internal enum class DroneCandidate(val file: String) {
    /** A tanpura re-plucked in its cycle (Pa, Sa, Sa, low Sa), every pluck blooming into the jawari bridge's buzz. */
    TANPURA("tanpura"),

    /** A harmonium (shruti box) with two reeds a note, a few cents either side (musette), nasal and bright, the bellows swelling. */
    HARMONIUM("harmonium"),

    /** A soft stopped-flute organ pipe: nearly pure, round and hollow, perfectly still. */
    FLUTE_ORGAN("flute-organ"),

    /** A warm synth pad: detuned sawtooth voices through a low-pass that slowly opens and closes. */
    WARM_PAD("warm-pad"),
}

internal object DroneCandidates {

    /**
     * [frames] samples at [sampleRate] of [candidate] on [rootHz], with the [fifth] and the
     * [octave] if asked. The steady sounds put the fifth above the root (3:2, [FIFTH_GAIN]) and the
     * octave above it (2:1, [OCTAVE_GAIN]); the tanpura voices them in its own way ([tanpura]).
     */
    fun render(candidate: DroneCandidate, sampleRate: Int, rootHz: Double, fifth: Boolean, octave: Boolean, frames: Int): DoubleArray {
        if (candidate == DroneCandidate.TANPURA) return tanpura(sampleRate, rootHz, fifth, octave, frames)
        val notes = buildList {
            add(rootHz to 1.0)
            if (fifth) add(1.5 * rootHz to FIFTH_GAIN)
            if (octave) add(2 * rootHz to OCTAVE_GAIN)
        }
        val out = DoubleArray(frames)
        for ((i, note) in notes.withIndex()) {
            val (hz, gain) = note
            when (candidate) {
                DroneCandidate.HARMONIUM -> harmonium(sampleRate, hz, gain, i, out)
                DroneCandidate.FLUTE_ORGAN -> fluteOrgan(sampleRate, hz, gain, out)
                DroneCandidate.WARM_PAD -> warmPad(sampleRate, hz, gain, i, out)
                DroneCandidate.TANPURA -> error("handled above")
            }
        }
        return out
    }

    /**
     * A tanpura: four strings plucked one after the other, about every [PLUCK_SECONDS], over and
     * over, each ringing on for seconds under the next. The classic tuning: Pa (the fifth, a fourth
     * below the root, 3:4), Sa, Sa (the root) and the low Sa (the octave below, 1:2). Without the
     * [fifth] the first string is a Sa too; with the [octave] the third sounds the Sa above (2:1).
     *
     * Each pluck is additive (partial k at 1/k, rolled off above 5 kHz, decaying with a time
     * constant of 5 s at 100 Hz falling to 0.8 s at 2 kHz). The jawari, the tanpura's wide curved
     * bridge, lets the string graze it, which pumps energy into high partials a moment after the
     * pluck and lets it sink down through them as the note rings (Raman 1921; Valette 1993): a bump
     * of partials (0.6 octave wide) up to ×(1 + [JAWARI]) louder, rising over 0.12 s and fading
     * over 3 s, its centre moving from about the 24th partial down to the 8th. That is the buzz you hear blooming.
     *
     * Every pluck is made afresh and a little different (a seeded random: its time ±60 ms, its
     * strength ±1.5 dB, its buzz ±15 %, where the buzz starts ±0.2 octave), so nothing repeats.
     */
    private fun tanpura(rate: Int, rootHz: Double, fifth: Boolean, octave: Boolean, frames: Int): DoubleArray {
        val strings = doubleArrayOf(if (fifth) 0.75 * rootHz else rootHz, rootHz, if (octave) 2 * rootHz else rootHz, 0.5 * rootHz)
        val random = Random(SEED.toLong())
        val out = DoubleArray(frames)
        var pluck = 0
        while (true) {
            val at = FIRST_PLUCK_SECONDS + pluck * PLUCK_SECONDS + 0.06 * (2 * random.nextDouble() - 1)
            val onset = (at * rate).toInt()
            if (onset >= frames) break
            val hz = strings[pluck % strings.size]
            val strength = 10.0.pow(1.5 * (2 * random.nextDouble() - 1) / 20) * (if (pluck % strings.size == 3) 1.15 else 1.0)
            val buzz = JAWARI * (1 + 0.15 * (2 * random.nextDouble() - 1))
            val from = ln(24.0) / ln(2.0) + 0.2 * (2 * random.nextDouble() - 1)
            val count = partialCount(rate, hz)
            val freqs = DoubleArray(count) { (it + 1) * hz }
            // Spread phases (Schroeder 1970), turned anew each pluck: the buzz rings without a spike once a period.
            val turn = 2 * PI * random.nextDouble()
            val phases = DoubleArray(count) { PI * (it + 1) * (it + 1) / count + turn * (it + 1) }
            val length = minOf(frames - onset, (PLUCK_RING_SECONDS * rate).toInt())
            additive(rate, freqs, phases, onset, length, out) { i, t ->
                val k = i + 1
                val f = freqs[i]
                val lk = ln(k.toDouble()) / ln(2.0)
                val attack = minOf(1.0, t / 0.004)
                val decay = exp(-t / decaySeconds(f))
                val bloom = (1 - exp(-t / 0.12)) * exp(-t / 3.0)
                val centre = from - 1.6 * (1 - exp(-t / 1.5))
                val d = lk - centre
                val jawari = 1 + buzz * bloom * exp(-d * d / (2 * 0.6 * 0.6))
                strength * attack * decay * jawari / k.toDouble() * rollOff(f, 5_000.0)
            }
            pluck++
        }
        return out
    }

    /** A tanpura string's decay time constant at [f]: 5 s at 100 Hz, 0.8 s at 2 kHz, 1/τ linear between (as [io.github.klaboukes.twangle.core.dsp.synth.StringVoice.decaySecondsAt]). */
    private fun decaySeconds(f: Double): Double {
        val rate = 1 / 5.0 + (f.coerceAtLeast(100.0) - 100) * (1 / 0.8 - 1 / 5.0) / 1_900
        return 1 / rate
    }

    /**
     * A harmonium (shruti box): two free reeds a note, one [MUSETTE_CENTS] below it and a softer
     * one ([SECOND_REED]) as far above as keeps their level-weighted average exactly on pitch, so
     * they beat slowly (the musette shimmer, faster in the higher partials) without ever quite
     * cancelling. A reed's spectrum is bright (partial k at 1/k^0.75), coloured by the reed
     * chamber's nasal hills near 1.25 kHz (+9 dB) and 2.6 kHz (+5 dB), rolled off above 5 kHz. The
     * bellows push in over 0.3 s, then swell and ease by ±12 % every 2.4 s, the high partials
     * brightening as the pressure rises, with a breath of air noise (2 kHz band) that follows them.
     */
    private fun harmonium(rate: Int, hz: Double, gain: Double, voice: Int, out: DoubleArray) {
        val phase = voice * VOICE_PHASE
        val nasal = BodyMode(1_250.0, 2.5, 9.0)
        val upper = BodyMode(2_600.0, 3.0, 5.0)
        fun bellows(t: Double) = sin(2 * PI * t / 2.4 + phase)
        val reed = DoubleArray(out.size)
        for (side in intArrayOf(-1, 1)) {
            // The upper reed is softer (SECOND_REED) and detuned more, so the reeds' level-weighted average is on pitch.
            val reedGain = if (side < 0) 1.0 else SECOND_REED
            val reedHz = hz * 2.0.pow(side * MUSETTE_CENTS * (if (side < 0) 1.0 else 1 / SECOND_REED) / 1200)
            val count = partialCount(rate, reedHz)
            val freqs = DoubleArray(count) { (it + 1) * reedHz }
            val phases = DoubleArray(count) { PI * (it + 1) * (it + 1) / count + (if (side > 0) 1.3 else 0.0) }
            additive(rate, freqs, phases, 0, out.size, reed) { i, t ->
                val f = freqs[i]
                val press = (1 - exp(-t / 0.3)) * (1 + 0.12 * bellows(t))
                val bright = 1 + 0.25 * bellows(t) * f / 1_000
                reedGain * press * bright / (i + 1.0).pow(0.75) * nasal.gainAt(f) * upper.gainAt(f) * rollOff(f, 5_000.0)
            }
        }
        val noise = bandPassedNoise(rate, 2_000.0, 0.8, out.size, SEED + voice)
        val reedPeak = reed.maxOf { abs(it) }
        val noisePeak = noise.maxOf { abs(it) }
        for (n in out.indices) {
            val t = n.toDouble() / rate
            val air = AIR * reedPeak / noisePeak * noise[n] * (1 - exp(-t / 0.3)) * (1 + 0.4 * bellows(t))
            out[n] += gain * (reed[n] + air)
        }
    }

    /**
     * A stopped flute pipe (a Gedackt): closed at the top, so it speaks mostly in odd partials, and
     * softly: the fundamental, the 3rd at −10 dB, the 5th at −20 dB, the 7th at −30 dB, and the
     * even ones far down (−26 dB and below) from the pipe not being quite ideal (Fletcher & Rossing,
     * *The Physics of Musical Instruments*, 1998). It speaks over 0.15 s and then nothing moves at all.
     */
    private fun fluteOrgan(rate: Int, hz: Double, gain: Double, out: DoubleArray) {
        val levels = doubleArrayOf(1.0, 0.05, 0.32, 0.02, 0.1, 0.01, 0.03)
        val count = minOf(levels.size, partialCount(rate, hz))
        val freqs = DoubleArray(count) { (it + 1) * hz }
        val phases = DoubleArray(count) { PI * (it + 1) * (it + 1) / count }
        additive(rate, freqs, phases, 0, out.size, out) { i, t ->
            val speak = if (t < 0.15) 0.5 - 0.5 * cos(PI * t / 0.15) else 1.0
            gain * speak * levels[i]
        }
    }

    /**
     * A warm synth pad: five sawtooth voices (partial k at 1/k) at [PAD_DETUNE_CENTS], spread
     * symmetrically around the note at symmetric levels ([PAD_LEVELS]) so their average is on it, each with its own random phases
     * (seeded), through a two-pole low-pass (Q 1, a soft bump at the edge) whose corner sweeps from
     * 350 Hz up to 2.4 kHz and back every [PAD_SWEEP_SECONDS] (evenly in octaves), the same filter
     * for every note of the chord. It swells in over 1.5 s.
     */
    private fun warmPad(rate: Int, hz: Double, gain: Double, voice: Int, out: DoubleArray) {
        val random = Random(SEED.toLong() + 31L * voice)
        for ((v, cents) in PAD_DETUNE_CENTS.withIndex()) {
            val voiceLevel = PAD_LEVELS[v]
            val voiceHz = hz * 2.0.pow(cents / 1200)
            val count = partialCount(rate, voiceHz)
            val freqs = DoubleArray(count) { (it + 1) * voiceHz }
            val phases = DoubleArray(count) { 2 * PI * random.nextDouble() }
            additive(rate, freqs, phases, 0, out.size, out) { i, t ->
                val swell = if (t < 1.5) 0.5 - 0.5 * cos(PI * t / 1.5) else 1.0
                val corner = 350.0 * (2_400.0 / 350.0).pow(0.5 - 0.5 * cos(2 * PI * t / PAD_SWEEP_SECONDS))
                val x = freqs[i] / corner
                val lowPass = 1 / sqrt((1 - x * x) * (1 - x * x) + x * x)
                gain * swell * lowPass / (i + 1) * voiceLevel
            }
        }
    }

    /** Two poles above [hz], as [io.github.klaboukes.twangle.core.dsp.synth.StringVoice.partialLevel]'s brightness. */
    private fun rollOff(f: Double, hz: Double): Double {
        val x = f / hz
        return 1 / sqrt(1 + x * x * x * x)
    }

    /**
     * Adds partials at [freqs] (exact, each a phasor turned by its exact step, starting at
     * [phases]) into [out] from sample [start] for [length] samples. Partial i's level is [level]
     * (i, seconds since [start]), recomputed every [BLOCK] samples and ramped across the block.
     */
    private fun additive(
        rate: Int,
        freqs: DoubleArray,
        phases: DoubleArray,
        start: Int,
        length: Int,
        out: DoubleArray,
        level: (Int, Double) -> Double,
    ) {
        val count = freqs.size
        val re = DoubleArray(count) { cos(phases[it]) }
        val im = DoubleArray(count) { sin(phases[it]) }
        val stepRe = DoubleArray(count) { cos(2 * PI * freqs[it] / rate) }
        val stepIm = DoubleArray(count) { sin(2 * PI * freqs[it] / rate) }
        val now = DoubleArray(count) { level(it, 0.0) }
        val next = DoubleArray(count)
        var from = 0
        while (from < length) {
            val to = minOf(length, from + BLOCK)
            for (i in 0 until count) next[i] = level(i, to.toDouble() / rate)
            for (n in from until to) {
                val x = (n - from).toDouble() / (to - from)
                var sum = 0.0
                for (i in 0 until count) {
                    val r = re[i]
                    sum += (now[i] + (next[i] - now[i]) * x) * im[i]
                    re[i] = r * stepRe[i] - im[i] * stepIm[i]
                    im[i] = r * stepIm[i] + im[i] * stepRe[i]
                }
                out[start + n] += sum
            }
            // Keep every phasor on the unit circle, so rounding never changes a level.
            for (i in 0 until count) {
                val m = 1 / sqrt(re[i] * re[i] + im[i] * im[i])
                re[i] *= m
                im[i] *= m
            }
            next.copyInto(now)
            from = to
        }
    }

    /** How many harmonics of [hz] fit under [StringSynth.MAX_PARTIAL_HZ] and 0.45 × the rate, at most [StringSynth.MAX_PARTIALS]. */
    private fun partialCount(rate: Int, hz: Double): Int =
        minOf(StringSynth.MAX_PARTIALS, (minOf(StringSynth.MAX_PARTIAL_HZ.toDouble(), 0.45 * rate) / hz).toInt())

    private fun bandPassedNoise(rate: Int, hz: Double, q: Double, frames: Int, seed: Int): DoubleArray {
        // RBJ cookbook band-pass (constant 0 dB peak).
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

    /** The fifth and the octave under the root, by amplitude: −3 dB and −6 dB. */
    const val FIFTH_GAIN = 0.7
    const val OCTAVE_GAIN = 0.5

    /** Levels are recomputed this often (samples): a pluck, a sweep or a breath moves smoothly at this step. */
    private const val BLOCK = 64

    /** The tanpura's first pluck, and the time from one pluck to the next. */
    const val FIRST_PLUCK_SECONDS = 0.3
    const val PLUCK_SECONDS = 1.5

    /** How long a tanpura pluck is rendered (it is 40 dB down well before). */
    private const val PLUCK_RING_SECONDS = 8.0

    /** How much the jawari raises the partials at the peak of its bloom. */
    private const val JAWARI = 22.0

    /** The harmonium's lower reed sits this far below the note. */
    const val MUSETTE_CENTS = 3.0

    /** The upper reed's level against the lower's. */
    const val SECOND_REED = 0.8

    /** The pad's voices, in cents from the note: symmetric, so their average is on it. */
    val PAD_DETUNE_CENTS = doubleArrayOf(-13.0, -6.0, 0.0, 6.0, 13.0)

    /** Their levels: the middle voice strongest, so the voices' beating never empties the sound. */
    val PAD_LEVELS = doubleArrayOf(0.25, 0.4, 1.0, 0.4, 0.25)

    /** The pad's low-pass opens and closes once in this time. */
    const val PAD_SWEEP_SECONDS = 7.0

    /** Voices of a chord move this far apart in phase (radians), so the fifth doesn't swell with the root. */
    private const val VOICE_PHASE = 2.1

    /** The harmonium's air noise peaks at this share of the reeds' peak. */
    private const val AIR = 0.03

    private const val SEED = 0x1F3D5B79
}
