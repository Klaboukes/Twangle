package io.github.klaboukes.twangle.core.dsp.drone

import io.github.klaboukes.twangle.core.dsp.synth.BodyMode
import io.github.klaboukes.twangle.core.dsp.synth.StringSynth
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One of the drone's sounds, made a block ([DroneSynth.BLOCK] samples) at a time for as long as it
 * is asked, never from a recording and never looped. The designs are round 2's candidates
 * (`DroneCandidates` in the test sources, which made the files the owner picked from), with the
 * same numbers, so they sound the same; here they run on and on instead of for 8 s.
 *
 * Everything is allocated at construction, for the largest voicing and the lowest root, so
 * [begin] and [renderBlock] allocate nothing (hard rule 2). Output is unscaled: [DroneSynth] puts
 * each sound's level on.
 */
internal abstract class DroneEngine(protected val sampleRate: Int) {

    protected val blockSeconds = DroneSynth.BLOCK.toDouble() / sampleRate

    /** Blocks rendered since [begin]. */
    protected var blocks = 0L

    /** Seconds on the sound's own clock at [begin] (an envelope or a sweep starts there). */
    protected var startSeconds = 0.0

    /** Seconds on the sound's clock at the end of the coming block. */
    protected val blockEndSeconds: Double get() = startSeconds + (blocks + 1) * blockSeconds

    /** Seconds on the sound's clock at the start of the coming block. */
    protected val blockStartSeconds: Double get() = startSeconds + blocks * blockSeconds

    /**
     * Sets the sound up for the notes of [voicing] on [rootHz], its clock at [startSeconds], drawing
     * whatever it chooses at random from [random].
     */
    fun begin(rootHz: Double, voicing: DroneVoicing, startSeconds: Double, random: DroneRandom) {
        blocks = 0
        this.startSeconds = startSeconds
        setUp(rootHz, voicing, random)
    }

    protected abstract fun setUp(rootHz: Double, voicing: DroneVoicing, random: DroneRandom)

    /** Adds the next [DroneSynth.BLOCK] samples into [out] from 0. */
    fun renderBlock(out: DoubleArray) {
        render(out)
        blocks++
    }

    protected abstract fun render(out: DoubleArray)

    companion object {
        /** The fifth's and the octave's level against the root's, by amplitude: −3 dB and −6 dB. */
        const val FIFTH_GAIN = 0.7
        const val OCTAVE_GAIN = 0.5

        /** How many notes a steady sound plays for [voicing]: the root, the fifth (3:2), the octave (2:1). */
        fun noteCount(voicing: DroneVoicing): Int = voicing.ordinal + 1

        fun noteRatio(note: Int): Double = when (note) {
            0 -> 1.0
            1 -> 1.5
            else -> 2.0
        }

        fun noteGain(note: Int): Double = when (note) {
            0 -> 1.0
            1 -> FIFTH_GAIN
            else -> OCTAVE_GAIN
        }

        /** How many harmonics of [hz] fit under [StringSynth.MAX_PARTIAL_HZ] and 0.45 × the rate, at most [StringSynth.MAX_PARTIALS]. */
        fun partialCount(sampleRate: Int, hz: Double): Int =
            minOf(StringSynth.MAX_PARTIALS, (StringSynth.maxFrequencyHz(sampleRate).toDouble() / hz).toInt())

        /** Two poles above [hz], as `StringVoice.partialLevel`'s brightness. */
        fun rollOff(f: Double, hz: Double): Double {
            val x = f / hz
            return 1 / sqrt(1 + x * x * x * x)
        }

        /** 0 → 1 along half a cosine as x goes 0 → 1, and 1 after. */
        fun raisedCosine(x: Double): Double = if (x >= 1) 1.0 else 0.5 - 0.5 * cos(PI * x)

        /** Spread phases (Schroeder 1970): partial k of [count] at πk²/count, so a stack of harmonics has no spike once a period. */
        fun schroederPhase(k: Int, count: Int): Double = PI * k * k / count
    }
}

/**
 * A tanpura: four strings plucked one after the other every [PLUCK_SECONDS], on and on, each
 * ringing for seconds under the next. The classic tuning: Pa (the fifth, a fourth below the root,
 * 3:4), Sa, Sa (the root) and the low Sa (the octave below, 1:2). For [DroneVoicing.ROOT] the first
 * string is a Sa too; for [DroneVoicing.FIFTH_OCTAVE] the third sounds the Sa above (2:1).
 *
 * Each pluck is additive (partial k at 1/k, rolled off above 5 kHz, decaying with a time constant
 * of 5 s at 100 Hz falling to 0.8 s at 2 kHz). The jawari, the tanpura's wide curved bridge, lets
 * the string graze it, which pumps energy into high partials a moment after the pluck and lets it
 * sink down through them as the note rings (Raman 1921; Valette 1993): a bump of partials (0.6
 * octave wide) up to ×(1 + [JAWARI]) louder, rising over 0.12 s and fading over 3 s, its centre
 * moving from about the 24th partial down to the 8th. Every pluck is made afresh and a little
 * different (its time ±60 ms, its strength ±1.5 dB, its buzz ±15 %, where the buzz starts ±0.2
 * octave), so nothing ever repeats.
 *
 * A pluck rings for [RING_SECONDS] and fades out over its last [RING_FADE_SECONDS] (by then the
 * same string has been plucked again), so [POOL] voices are always enough: they are reused, never
 * made. A partial that has died away below [PRUNE_LEVEL] is no longer turned, which keeps a
 * pluck's cost falling as it rings.
 */
internal class TanpuraEngine(sampleRate: Int) : DroneEngine(sampleRate) {

    private class Pluck(capacity: Int) {
        val bank = PartialBank(capacity)
        val base = DoubleArray(capacity)
        val decay = DoubleArray(capacity)
        val decayStep = DoubleArray(capacity)
        var sounding = false
        var startBlock = 0L
        var strength = 0.0
        var buzz = 0.0
        var from = 0.0
    }

    private val plucks = Array(POOL) { Pluck(StringSynth.MAX_PARTIALS) }
    private val strings = DoubleArray(STRINGS)
    private val log2k = DoubleArray(StringSynth.MAX_PARTIALS + 1) { if (it == 0) 0.0 else ln(it.toDouble()) / ln(2.0) }
    private var pluckIndex = 0L
    private var nextOnsetBlock = 0L
    private var random: DroneRandom? = null

    override fun setUp(rootHz: Double, voicing: DroneVoicing, random: DroneRandom) {
        strings[0] = if (voicing == DroneVoicing.ROOT) rootHz else 0.75 * rootHz
        strings[1] = rootHz
        strings[2] = if (voicing == DroneVoicing.FIFTH_OCTAVE) 2 * rootHz else rootHz
        strings[3] = 0.5 * rootHz
        for (p in plucks) p.sounding = false
        this.random = random
        pluckIndex = 0
        schedule(random)
    }

    /** When pluck [pluckIndex] comes, on a block's start: [FIRST_PLUCK_SECONDS] after the start, then every [PLUCK_SECONDS], ±60 ms. */
    private fun schedule(random: DroneRandom) {
        val at = FIRST_PLUCK_SECONDS + pluckIndex * PLUCK_SECONDS + 0.06 * random.nextSigned()
        nextOnsetBlock = ceil(at / blockSeconds).toLong()
    }

    override fun render(out: DoubleArray) {
        val random = random ?: return
        if (blocks >= nextOnsetBlock) {
            pluck(random)
            pluckIndex++
            schedule(random)
        }
        for (p in plucks) {
            if (!p.sounding) continue
            val ended = setLevels(p, (blocks + 1 - p.startBlock) * blockSeconds)
            p.bank.renderAdd(out, DroneSynth.BLOCK)
            if (ended) p.sounding = false
        }
    }

    private fun pluck(random: DroneRandom) {
        // A free voice; with POOL voices one always is, but take the oldest if not.
        var p = plucks[0]
        for (candidate in plucks) {
            if (!candidate.sounding) {
                p = candidate
                break
            }
            if (candidate.startBlock < p.startBlock) p = candidate
        }
        val string = (pluckIndex % STRINGS).toInt()
        val hz = strings[string]
        p.strength = 10.0.pow(1.5 * random.nextSigned() / 20) * (if (string == STRINGS - 1) 1.15 else 1.0)
        p.buzz = JAWARI * (1 + 0.15 * random.nextSigned())
        p.from = log2k[24] + 0.2 * random.nextSigned()
        // The spread phases turned anew each pluck: the buzz rings without a spike once a period.
        val turn = 2 * PI * random.nextDouble()
        val count = partialCount(sampleRate, hz)
        val bank = p.bank
        bank.clear()
        for (i in 0 until count) {
            val k = i + 1
            val f = k * hz
            bank.add(f, schroederPhase(k, count) + turn * k, sampleRate)
            p.base[i] = rollOff(f, 5_000.0) / k
            p.decay[i] = 1.0
            p.decayStep[i] = exp(-blockSeconds / decaySeconds(f))
        }
        p.startBlock = blocks
        p.sounding = true
    }

    /** Sets [p]'s levels for the end of the coming block, [t] seconds after its pluck; true once it has rung out. */
    private fun setLevels(p: Pluck, t: Double): Boolean {
        val attack = minOf(1.0, t / 0.004)
        val bloom = (1 - exp(-t / 0.12)) * exp(-t / 3.0)
        val centre = p.from - 1.6 * (1 - exp(-t / 1.5))
        val fadeFrom = RING_SECONDS - RING_FADE_SECONDS
        val ring = when {
            t >= RING_SECONDS -> 0.0
            t > fadeFrom -> 1 - raisedCosine((t - fadeFrom) / RING_FADE_SECONDS)
            else -> 1.0
        }
        val common = p.strength * attack * ring
        val buzz = p.buzz * bloom
        val bank = p.bank
        val next = bank.next
        for (i in 0 until bank.count) {
            p.decay[i] *= p.decayStep[i]
            val d = log2k[i + 1] - centre
            val jawari = 1 + buzz * exp(-d * d * (1 / (2 * 0.6 * 0.6)))
            next[i] = common * p.decay[i] * jawari * p.base[i]
        }
        if (t > PRUNE_AFTER_SECONDS) {
            while (bank.count > 0 && next[bank.count - 1] < PRUNE_LEVEL && bank.levelNow(bank.count - 1) < PRUNE_LEVEL) bank.count--
        }
        return ring == 0.0
    }

    /** A tanpura string's decay time constant at [f]: 5 s at 100 Hz, 0.8 s at 2 kHz, 1/τ linear between (as `StringVoice.decaySecondsAt`). */
    private fun decaySeconds(f: Double): Double {
        val rate = 1 / 5.0 + (f.coerceAtLeast(100.0) - 100) * (1 / 0.8 - 1 / 5.0) / 1_900
        return 1 / rate
    }

    companion object {
        const val STRINGS = 4

        /** The first pluck after the start, and the time from one pluck to the next. */
        const val FIRST_PLUCK_SECONDS = 0.1
        const val PLUCK_SECONDS = 1.5

        /** How long a pluck rings, and the fade at the end of that (the same string has been plucked again by then). */
        const val RING_SECONDS = 8.0
        const val RING_FADE_SECONDS = 1.5

        /** Voices: plucks at least 1.38 s apart ring 8 s, so at most 6 sound at once. */
        const val POOL = 7

        /** How much the jawari raises the partials at the peak of its bloom. */
        const val JAWARI = 22.0

        /** A partial this far down (−100 dB against a fresh pluck's fundamental), past the bloom, is no longer turned. */
        const val PRUNE_LEVEL = 1e-5
        const val PRUNE_AFTER_SECONDS = 0.5
    }
}

/**
 * A harmonium (shruti box): two free reeds a note, one [MUSETTE_CENTS] below it and a softer one
 * ([SECOND_REED]) as far above as keeps their level-weighted average exactly on pitch, so they beat
 * slowly (the musette shimmer, faster in the higher partials) without ever quite cancelling. A
 * reed's spectrum is bright (partial k at 1/k^0.75), coloured by the reed chamber's nasal hills
 * near 1.25 kHz (+9 dB) and 2.6 kHz (+5 dB), rolled off above 5 kHz. The bellows push in over
 * 0.3 s, then swell and ease by ±12 % every 2.4 s, the high partials brightening as the pressure
 * rises, with a breath of air noise (a 2 kHz band) that follows them. The notes of a chord breathe
 * [VOICE_PHASE] apart, so the fifth doesn't swell with the root.
 */
internal class HarmoniumEngine(sampleRate: Int) : DroneEngine(sampleRate) {

    private val reeds = Array(2 * MAX_NOTES) { PartialBank(StringSynth.MAX_PARTIALS) }
    private val reedBase = Array(2 * MAX_NOTES) { DoubleArray(StringSynth.MAX_PARTIALS) }
    private var notes = 0
    private val gain = DoubleArray(MAX_NOTES)
    private val airNow = DoubleArray(MAX_NOTES)

    // The air noise: one xorshift32 generator and band-pass (RBJ cookbook, constant 0 dB peak) per note.
    private val noiseState = IntArray(MAX_NOTES)
    private val x1 = DoubleArray(MAX_NOTES)
    private val x2 = DoubleArray(MAX_NOTES)
    private val y1 = DoubleArray(MAX_NOTES)
    private val y2 = DoubleArray(MAX_NOTES)
    private val b0: Double
    private val a1: Double
    private val a2: Double

    /** The band-passed noise's RMS, found once by running it, so [AIR_RMS] holds at every rate. */
    private val noiseRms: Double

    private val nasal = BodyMode(1_250.0, 2.5, 9.0)
    private val upper = BodyMode(2_600.0, 3.0, 5.0)

    init {
        val w0 = 2 * PI * 2_000.0 / sampleRate
        val alpha = sin(w0) / (2 * 0.8)
        val a0 = 1 + alpha
        b0 = alpha / a0
        a1 = -2 * cos(w0) / a0
        a2 = (1 - alpha) / a0
        noiseState[0] = NOISE_SEED
        var sum = 0.0
        val n = 2 * sampleRate
        for (i in 0 until n) {
            val y = noise(0)
            sum += y * y
        }
        noiseRms = sqrt(sum / n)
    }

    override fun setUp(rootHz: Double, voicing: DroneVoicing, random: DroneRandom) {
        notes = noteCount(voicing)
        for (note in 0 until notes) {
            val hz = rootHz * noteRatio(note)
            gain[note] = noteGain(note)
            for (side in 0..1) {
                // The upper reed is softer (SECOND_REED) and detuned more, so the reeds' level-weighted average is on pitch.
                val reedGain = if (side == 0) 1.0 else SECOND_REED
                val cents = if (side == 0) -MUSETTE_CENTS else MUSETTE_CENTS / SECOND_REED
                val reedHz = hz * 2.0.pow(cents / 1200)
                val count = partialCount(sampleRate, reedHz)
                val bank = reeds[2 * note + side]
                val base = reedBase[2 * note + side]
                bank.clear()
                for (i in 0 until count) {
                    val k = i + 1
                    val f = k * reedHz
                    bank.add(f, schroederPhase(k, count) + (if (side == 1) 1.3 else 0.0), sampleRate)
                    base[i] = gain[note] * reedGain / k.toDouble().pow(0.75) * nasal.gainAt(f) * upper.gainAt(f) * rollOff(f, 5_000.0)
                }
            }
            noiseState[note] = random.nextNonZeroInt()
            x1[note] = 0.0
            x2[note] = 0.0
            y1[note] = 0.0
            y2[note] = 0.0
        }
        // The first block starts from the levels at the start, not from 0.
        val t = blockStartSeconds
        for (note in 0 until notes) {
            setReedLevels(note, t)
            reeds[2 * note].settle()
            reeds[2 * note + 1].settle()
            airNow[note] = air(note, t)
        }
    }

    override fun render(out: DoubleArray) {
        val t = blockEndSeconds
        val n = DroneSynth.BLOCK
        for (note in 0 until notes) {
            setReedLevels(note, t)
            reeds[2 * note].renderAdd(out, n)
            reeds[2 * note + 1].renderAdd(out, n)
            val from = airNow[note]
            val to = air(note, t)
            val step = (to - from) / n
            for (s in 0 until n) out[s] += (from + step * s) * noise(note)
            airNow[note] = to
        }
    }

    private fun bellows(note: Int, t: Double) = sin(2 * PI * t / 2.4 + note * VOICE_PHASE)

    private fun press(t: Double) = 1 - exp(-t / 0.3)

    private fun setReedLevels(note: Int, t: Double) {
        val b = bellows(note, t)
        val press = press(t) * (1 + 0.12 * b)
        val bright = 0.25 * b / 1_000
        for (side in 0..1) {
            val bank = reeds[2 * note + side]
            val base = reedBase[2 * note + side]
            for (i in 0 until bank.count) bank.next[i] = base[i] * press * (1 + bright * bank.hz[i])
        }
    }

    private fun air(note: Int, t: Double) = gain[note] * AIR_RMS / noiseRms * press(t) * (1 + 0.4 * bellows(note, t))

    private fun noise(note: Int): Double {
        // xorshift32 (Marsaglia 2003), as StringSynth's noise.
        var s = noiseState[note]
        s = s xor (s shl 13)
        s = s xor (s ushr 17)
        s = s xor (s shl 5)
        noiseState[note] = s
        val x = s * (1.0 / Int.MAX_VALUE)
        val y = b0 * x - b0 * x2[note] - a1 * y1[note] - a2 * y2[note]
        x2[note] = x1[note]
        x1[note] = x
        y2[note] = y1[note]
        y1[note] = y
        return y
    }

    companion object {
        const val MAX_NOTES = 3

        /** The lower reed sits this far below the note. */
        const val MUSETTE_CENTS = 3.0

        /** The upper reed's level against the lower's. */
        const val SECOND_REED = 0.8

        /**
         * The air noise's RMS: the candidate's (3 % of the reeds' peak at D3, which is an RMS of
         * 0.126 against the reeds' 3.23, measured at 32 kHz), here held at every rate and root.
         */
        const val AIR_RMS = 0.126

        /** Notes of a chord move this far apart in the bellows' cycle (radians). */
        const val VOICE_PHASE = 2.1

        private const val NOISE_SEED = 0x1F3D5B79
    }
}

/**
 * A stopped flute pipe (a Gedackt): closed at the top, so it speaks mostly in odd partials, and
 * softly: the fundamental, the 3rd at −10 dB, the 5th at −20 dB, the 7th at −30 dB, and the even
 * ones far down (−26 dB and below) from the pipe not being quite ideal (Fletcher & Rossing, *The
 * Physics of Musical Instruments*, 1998). It speaks over 0.15 s and then nothing moves at all.
 */
internal class FluteOrganEngine(sampleRate: Int) : DroneEngine(sampleRate) {

    private val pipes = Array(HarmoniumEngine.MAX_NOTES) { PartialBank(LEVELS.size) }
    private val gain = DoubleArray(HarmoniumEngine.MAX_NOTES)
    private var notes = 0

    override fun setUp(rootHz: Double, voicing: DroneVoicing, random: DroneRandom) {
        notes = noteCount(voicing)
        for (note in 0 until notes) {
            val hz = rootHz * noteRatio(note)
            gain[note] = noteGain(note)
            val count = minOf(LEVELS.size, partialCount(sampleRate, hz))
            val bank = pipes[note]
            bank.clear()
            for (i in 0 until count) bank.add((i + 1) * hz, schroederPhase(i + 1, count), sampleRate)
            setLevels(note, blockStartSeconds)
            bank.settle()
        }
    }

    override fun render(out: DoubleArray) {
        for (note in 0 until notes) {
            setLevels(note, blockEndSeconds)
            pipes[note].renderAdd(out, DroneSynth.BLOCK)
        }
    }

    private fun setLevels(note: Int, t: Double) {
        val level = gain[note] * raisedCosine(t / 0.15)
        val bank = pipes[note]
        for (i in 0 until bank.count) bank.next[i] = level * LEVELS[i]
    }

    private companion object {
        val LEVELS = doubleArrayOf(1.0, 0.05, 0.32, 0.02, 0.1, 0.01, 0.03)
    }
}

/**
 * A warm synth pad: five sawtooth voices (partial k at 1/k) at [PAD_DETUNE_CENTS], spread
 * symmetrically around the note at symmetric levels ([PAD_LEVELS]) so their average is on it, each
 * with its own random phases, through a two-pole low-pass (Q 1, a soft bump at the edge) whose
 * corner sweeps from 350 Hz up to 2.4 kHz and back every [PAD_SWEEP_SECONDS] (evenly in octaves),
 * the same filter for every note of the chord. It swells in over 1.5 s.
 */
internal class WarmPadEngine(sampleRate: Int) : DroneEngine(sampleRate) {

    private val voices = Array(HarmoniumEngine.MAX_NOTES * PAD_DETUNE_CENTS.size) { PartialBank(StringSynth.MAX_PARTIALS) }
    private val base = Array(voices.size) { DoubleArray(StringSynth.MAX_PARTIALS) }
    private var count = 0

    override fun setUp(rootHz: Double, voicing: DroneVoicing, random: DroneRandom) {
        count = noteCount(voicing) * PAD_DETUNE_CENTS.size
        for (note in 0 until noteCount(voicing)) {
            val hz = rootHz * noteRatio(note)
            for (v in PAD_DETUNE_CENTS.indices) {
                val cents = PAD_DETUNE_CENTS[v]
                val index = note * PAD_DETUNE_CENTS.size + v
                val voiceHz = hz * 2.0.pow(cents / 1200)
                val partials = partialCount(sampleRate, voiceHz)
                val bank = voices[index]
                bank.clear()
                for (i in 0 until partials) {
                    bank.add((i + 1) * voiceHz, 2 * PI * random.nextDouble(), sampleRate)
                    base[index][i] = noteGain(note) * PAD_LEVELS[v] / (i + 1)
                }
            }
        }
        setLevels(blockStartSeconds)
        for (i in 0 until count) voices[i].settle()
    }

    override fun render(out: DoubleArray) {
        setLevels(blockEndSeconds)
        for (i in 0 until count) voices[i].renderAdd(out, DroneSynth.BLOCK)
    }

    private fun setLevels(t: Double) {
        val swell = raisedCosine(t / 1.5)
        val corner = 350.0 * (2_400.0 / 350.0).pow(0.5 - 0.5 * cos(2 * PI * t / PAD_SWEEP_SECONDS))
        val perCorner = 1 / corner
        for (index in 0 until count) {
            val bank = voices[index]
            val levels = base[index]
            for (i in 0 until bank.count) {
                val x = bank.hz[i] * perCorner
                val lowPass = 1 / sqrt((1 - x * x) * (1 - x * x) + x * x)
                bank.next[i] = swell * lowPass * levels[i]
            }
        }
    }

    companion object {
        /** The pad's voices, in cents from the note: symmetric, so their average is on it. */
        val PAD_DETUNE_CENTS = doubleArrayOf(-13.0, -6.0, 0.0, 6.0, 13.0)

        /** Their levels: the middle voice strongest, so the voices' beating never empties the sound. */
        val PAD_LEVELS = doubleArrayOf(0.25, 0.4, 1.0, 0.4, 0.25)

        /** The low-pass opens and closes once in this time. */
        const val PAD_SWEEP_SECONDS = 7.0
    }
}

/** A seeded random (xorshift64*, Vigna 2016, seeded through splitmix64): the same seed, the same drone. */
internal class DroneRandom(seed: Long) {
    private var state: Long

    init {
        var z = seed + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        z = z xor (z ushr 31)
        state = if (z == 0L) 1L else z
    }

    private fun nextLong(): Long {
        var x = state
        x = x xor (x ushr 12)
        x = x xor (x shl 25)
        x = x xor (x ushr 27)
        state = x
        return x * 0x2545F4914F6CDD1DL
    }

    /** Uniform in 0..1. */
    fun nextDouble(): Double = (nextLong() ushr 11) * (1.0 / (1L shl 53))

    /** Uniform in −1..1. */
    fun nextSigned(): Double = 2 * nextDouble() - 1

    fun nextNonZeroInt(): Int {
        val x = (nextLong() ushr 32).toInt()
        return if (x == 0) 0x1F3D5B79 else x
    }
}
