package io.github.klaboukes.twangle.core.dsp.pitch

import io.github.klaboukes.twangle.core.dsp.buffer.SampleFilter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Takes mains hum (50 and 60 Hz and their first [harmonics]) out of the low-note range's audio
 * before YIN, where those frequencies lie inside the range ([PitchRange.LOW]).
 *
 * Each frequency has an adaptive notch: Widrow's adaptive noise canceller with a sinusoidal
 * reference (Widrow et al. 1975, "Adaptive noise cancelling: principles and applications",
 * Proc. IEEE 63(12), section on cancelling periodic interference). The hum is estimated as
 * `h[n] = Σ a_k·cos(ω_k n) + b_k·sin(ω_k n)` and subtracted, and every weight follows the LMS rule
 * `a_k += 2μ·y[n]·cos(ω_k n)` (likewise `b_k`) on the output `y = x − h`. That is a notch at each
 * ω_k, `μ·fs/π` Hz wide ([bandwidthHz]).
 *
 * A notch narrow enough to pass G1 (49.0 Hz) next to 50 Hz still rings when a note starts beside
 * it: a note Δ Hz from a notch B Hz wide sets off a tone at the notch's own frequency, B / 2Δ of
 * the note's level, that lasts about `1 / (π·B)` s. On G1, B1, G2 and B2 that moved the first second
 * of a pluck by 3 to 20 cents even in a quiet room. So the notches only learn **between notes**:
 * - a sample far above the room's level (5×, [TRIGGER]) or a short-term level 6 dB over it (a
 *   swelling note) freezes the weights at once, and the hum goes on being subtracted as learned,
 *   at the frequency tracked last (below). The note itself passes untouched.
 * - once the sound is back within 3 dB of the room's level, they learn again. While frozen the
 *   room's level creeps up ([ROOM_RISE_DB_PER_SECOND]) so a lasting change (an amplifier switched
 *   on) is learned in the end.
 *
 * Mains is never exactly 50 or 60 Hz (±0.05 Hz is common), and a frozen estimate a few thousandths
 * of a hertz off drifts from it (over 2 s, enough to take G2 an octave low), so the references follow
 * the hum. With them held still, a settled estimate turns at exactly the hum's distance from them:
 * once the strongest fundamental's estimate has been steady for a while, its turn averaged over a
 * few blocks is what every reference moves by, in one step. Frozen, they run on at that frequency
 * plus the turn measured since. A slow feedback loop instead rang on the notches' own lag.
 *
 * The notches learn whatever the room holds, a note ringing from the start too, so the output loses
 * only the estimates that turn like hum: still, and each harmonic k times as far as its fundamental
 * (`followHum`). In a quiet room the weights stay at the noise's tiny share, so a note reads as it
 * would without the canceller (within 0.02 cents, `LowPitchRangeTest`). Six harmonics, not three: hum from
 * `TestSignals.hum` (harmonics at 1/k) 6 dB under a string left its 4th to 6th harmonics enough to
 * take strings from 98 to 220 Hz an octave off, and others up to 9 cents; with six, every low string
 * stays within half a cent. A 1 Hz learning width beat 2 and 4 Hz once mains is off nominal.
 * Allocation-free after construction. Not thread-safe.
 */
class MainsHumCanceller(
    val sampleRate: Int,
    /** Harmonics of 50 and of 60 Hz cancelled, the fundamentals included (a shared one once). */
    val harmonics: Int = DEFAULT_HARMONICS,
    /** Width of each notch while it learns, in Hz. */
    val bandwidthHz: Double = DEFAULT_BANDWIDTH_HZ,
) : SampleFilter {

    init {
        require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }
        require(harmonics >= 1) { "harmonics must be at least 1, was $harmonics" }
        require(bandwidthHz > 0.0) { "bandwidthHz must be positive, was $bandwidthHz" }
    }

    // Each reference: its base (0 = 50 Hz, 1 = 60 Hz) and multiple k of it, below 0.45 × the rate.
    private val refBase: IntArray
    private val refMultiple: IntArray

    init {
        val bases = IntArray(MAX_REFS)
        val multiples = IntArray(MAX_REFS)
        var count = 0
        for (k in 1..harmonics) {
            for ((base, hz) in MAINS_HZ.withIndex()) {
                val f = k * hz
                if (f >= 0.45 * sampleRate) continue
                // 300 Hz is the 6th of 50 and the 5th of 60: one reference for both.
                if ((0 until count).any { multiples[it] * MAINS_HZ[bases[it]] == f }) continue
                check(count < MAX_REFS) { "too many harmonics: $harmonics" }
                bases[count] = base
                multiples[count] = k
                count++
            }
        }
        refBase = bases.copyOf(count)
        refMultiple = multiples.copyOf(count)
    }

    private val refs = refBase.size

    // The reference oscillators (cos, sin of ω_k n), their per-sample rotation, and the weights.
    private val cosRef = DoubleArray(refs)
    private val sinRef = DoubleArray(refs)
    private val cosStep = DoubleArray(refs)
    private val sinStep = DoubleArray(refs)
    private val weightA = DoubleArray(refs)
    private val weightB = DoubleArray(refs)

    // How much of each estimate is taken out of the output (0..1, ramped over a block), what it is
    // heading for, how many blocks in a row it turned like hum, and its weights at the block's start.
    private val gain = DoubleArray(refs)
    private val gainTarget = DoubleArray(refs)
    private val humBlocks = IntArray(refs)
    private val startA = DoubleArray(refs)
    private val startB = DoubleArray(refs)
    private val turns = DoubleArray(refs)

    // Each base's fundamental (multiple 1), whose turn its harmonics follow.
    private val fundamentalRef = IntArray(MAINS_HZ.size) { base -> (0 until refs).first { refBase[it] == base && refMultiple[it] == 1 } }

    private val mu = PI * bandwidthHz / sampleRate
    private val shortAlpha = 1.0 / (SHORT_SECONDS * sampleRate)
    private val roomAlpha = 1.0 / (ROOM_SECONDS * sampleRate)
    private val roomRise = 10.0.pow(ROOM_RISE_DB_PER_SECOND / 10 / sampleRate)
    private val holdSamples = (HOLD_SECONDS * sampleRate).toInt()
    private val warmUpSamples = (WARM_UP_SECONDS * sampleRate).toInt()
    private val stepSamples = (BLOCK_SECONDS * sampleRate / TURN_STEPS).toInt()
    private val blockSamples = stepSamples * TURN_STEPS
    private val gainStep = 1.0 / blockSamples

    // The mains frequency's tracked relative error (e.g. 0.001 for 50.05 Hz), and the strongest
    // fundamental's weights at the start of the block, to see how fast they turn.
    private var drift = 0.0
    // The error the weights still turned at in the last block, relative like [drift].
    private var heldError = 0.0
    private var previousA = 0.0
    private var previousB = 0.0
    private var previousRef = -1
    private var steadyBlocks = 0
    private var turnSum = 0.0
    private var turnCount = 0
    private var blockLearning = true

    // Mean square of the output: the room's level (while learning), and over the last few ms.
    private var room = 0.0
    private var short = 0.0
    private var learning = true
    private var hold = 0
    private var warmUp = 0
    private var blockPos = 0
    private var stepPos = 0

    /** True while the notches learn (no note), false while they hold their estimate (a note). */
    val isLearning: Boolean get() = learning

    /** The mains frequency the references follow now, for [base] 50 or 60 Hz. */
    fun trackedHz(base: Int): Double = base * (1 + drift)

    init {
        reset()
    }

    override fun reset() {
        drift = 0.0
        for (r in 0 until refs) {
            cosRef[r] = 1.0
            sinRef[r] = 0.0
            weightA[r] = 0.0
            weightB[r] = 0.0
            gain[r] = 0.0
            gainTarget[r] = 0.0
            humBlocks[r] = 0
            startA[r] = 0.0
            startB[r] = 0.0
        }
        heldError = 0.0
        setSteps(drift)
        room = 0.0
        short = 0.0
        learning = true
        hold = 0
        warmUp = warmUpSamples
        blockPos = 0
        stepPos = 0
        turns.fill(0.0)
        previousRef = -1
        steadyBlocks = 0
        turnSum = 0.0
        turnCount = 0
        blockLearning = true
    }

    override fun process(samples: FloatArray, offset: Int, length: Int) {
        for (i in offset until offset + length) {
            val x = samples[i].toDouble()
            // The notches learn on the whole estimate; the output loses only what turned like hum.
            var hum = 0.0
            var heardHum = 0.0
            for (r in 0 until refs) {
                val h = weightA[r] * cosRef[r] + weightB[r] * sinRef[r]
                hum += h
                heardHum += gain[r] * h
            }
            val y = x - hum
            updateLearning(y * y)
            if (learning) {
                val step = 2 * mu * y
                for (r in 0 until refs) {
                    weightA[r] += step * cosRef[r]
                    weightB[r] += step * sinRef[r]
                    val g = gain[r]
                    val target = gainTarget[r]
                    if (g < target) gain[r] = minOf(target, g + gainStep) else if (g > target) gain[r] = maxOf(target, g - gainStep)
                }
            } else {
                blockLearning = false
            }
            for (r in 0 until refs) {
                val c = cosRef[r]
                val s = sinRef[r]
                cosRef[r] = c * cosStep[r] - s * sinStep[r]
                sinRef[r] = s * cosStep[r] + c * sinStep[r]
            }
            samples[i] = (x - heardHum).toFloat()
            if (++stepPos == stepSamples) {
                stepPos = 0
                addTurns()
            }
            if (++blockPos == blockSamples) endBlock()
        }
    }

    // Learning or holding, from the output's power [y2] against the room's.
    private fun updateLearning(y2: Double) {
        short += shortAlpha * (y2 - short)
        if (warmUp > 0) {
            // The first few ms of sound set the room's level: at first everything is room, hum or
            // not. Digital silence (some microphones start with it) waits for the sound.
            if (y2 < SILENCE_POWER) {
                warmUp = warmUpSamples
                room = 0.0
                return
            }
            warmUp--
            room += (y2 - room) / (warmUpSamples - warmUp)
            if (warmUp == 0) short = room
            return
        }
        val level = roomLevel()
        if (learning) {
            if (y2 > TRIGGER * TRIGGER * level || short > SWELL * level) {
                learning = false
                hold = holdSamples
                // Held, the estimate runs on at the frequency the hum has now: the tracked one plus
                // the error the weights still turn at.
                setSteps(drift + heldError)
            } else {
                room += roomAlpha * (y2 - room)
            }
        } else {
            room *= roomRise
            if (hold > 0) {
                hold--
            } else if (short < RELEASE * level) {
                learning = true
                setSteps(drift)
            }
        }
    }

    private fun roomLevel(): Double = maxOf(room, ROOM_FLOOR)

    // Every few ms: keep the oscillators on the unit circle, and follow the mains frequency.
    private fun endBlock() {
        blockPos = 0
        for (r in 0 until refs) {
            val norm = 1.0 / sqrt(cosRef[r] * cosRef[r] + sinRef[r] * sinRef[r])
            cosRef[r] *= norm
            sinRef[r] *= norm
        }
        followHum(judge = blockLearning)
        // The strongest fundamental (multiple 1) leads the frequency tracking.
        var strongest = -1
        var strongestPower = 0.0
        for (r in 0 until refs) {
            if (refMultiple[r] != 1) continue
            val power = weightA[r] * weightA[r] + weightB[r] * weightB[r]
            if (power > strongestPower) {
                strongestPower = power
                strongest = r
            }
        }
        // Only a hum that stands clear of what is left (the room) says where mains is, and only once
        // the notches have settled on it: while they still converge (from the start, after a note,
        // after the references moved), its estimate grows and turns on its way, which is not the
        // hum's frequency. With the references held still, a settled estimate turns at exactly the
        // hum's distance from them: averaged over a few blocks, that is what they then move by.
        val previousPower = previousA * previousA + previousB * previousB
        val steady = blockLearning && strongest >= 0 && strongest == previousRef &&
            strongestPower < SETTLED * previousPower && previousPower < SETTLED * strongestPower &&
            strongestPower / 2 > HUM_CLEAR * roomLevel()
        steadyBlocks = if (steady) steadyBlocks + 1 else 0
        if (steadyBlocks > SETTLE_BLOCKS) {
            // h = Re((a − jb)·e^{jωn}): a hum ε Hz above the reference turns a − jb by 2πε per second.
            val a = weightA[strongest]
            val b = weightB[strongest]
            val turn = atan2(-b * previousA + a * previousB, a * previousA + b * previousB)
            turnSum += turn * sampleRate / (2 * PI * blockSamples) / MAINS_HZ[refBase[strongest]]
            turnCount++
            heldError = turnSum / turnCount
            if (turnCount == MEASURE_BLOCKS) {
                drift = (drift + heldError).coerceIn(-MAX_DRIFT, MAX_DRIFT)
                setSteps(drift)
                heldError = 0.0
                steadyBlocks = 0
            }
        }
        if (steadyBlocks <= SETTLE_BLOCKS) {
            turnSum = 0.0
            turnCount = 0
        }
        previousRef = strongest
        if (strongest >= 0) {
            previousA = weightA[strongest]
            previousB = weightB[strongest]
        }
        blockLearning = learning
    }

    // Every tenth of a block: how far each estimate turned since the last, added up over the block.
    // In 10 ms steps the turn is unambiguous up to 50 Hz from a notch; measured once a block, a
    // partial a whole number of 10 Hz from a notch (every notch is a multiple of 10 Hz, so A2 at
    // 110 Hz from all of them) would turn by whole circles and look still, like hum.
    private fun addTurns() {
        for (r in 0 until refs) {
            val a = weightA[r]
            val b = weightB[r]
            // h = Re((a − jb)·e^{jωn}): the turn of a − jb since the last step.
            turns[r] += if (startA[r] == 0.0 && startB[r] == 0.0) {
                Double.NaN
            } else {
                atan2(-b * startA[r] + a * startB[r], a * startA[r] + b * startB[r])
            }
            startA[r] = a
            startB[r] = b
        }
    }

    // Which estimates are hum, after a block of learning. A sound in the room from the start (a
    // note ringing as the low range opens) is learned too, and taking that out of the output would
    // ring the notches beside it (B0 read 7.6 cents off). Hum is told apart by how its estimate
    // turns over a block: hardly (mains is within 0.25 Hz of its tracked frequency), and each
    // harmonic k times as far as its fundamental. A partial Δ Hz from a notch turns its estimate by
    // 2πΔ per second, whatever the other notches do.
    // [judge] is false after a block in which a note held the weights: their turn then says nothing.
    private fun followHum(judge: Boolean) {
        for (r in 0 until refs) {
            if (!judge) continue
            // 300 Hz belongs to both bases: hum on either is hum.
            val hz = refMultiple[r] * MAINS_HZ[refBase[r]]
            var isHum = false
            for (base in MAINS_HZ.indices) {
                if (hz % MAINS_HZ[base] != 0) continue
                val fundamental = turns[fundamentalRef[base]]
                val off = abs(turns[r] - hz / MAINS_HZ[base] * fundamental)
                if (abs(fundamental) < HUM_TURN && off < HUM_TURN) isHum = true
            }
            humBlocks[r] = if (isHum) humBlocks[r] + 1 else 0
            gainTarget[r] = if (humBlocks[r] >= HUM_BLOCKS) 1.0 else 0.0
        }
        turns.fill(0.0)
    }

    // The references' rotation per sample, at nominal × (1 + [relative]).
    private fun setSteps(relative: Double) {
        for (r in 0 until refs) {
            val w = 2 * PI * refMultiple[r] * MAINS_HZ[refBase[r]] * (1 + relative) / sampleRate
            cosStep[r] = cos(w)
            sinStep[r] = sin(w)
        }
    }

    companion object {
        /** 50 Hz (most of the world) and 60 Hz (the Americas, parts of Asia). */
        private val MAINS_HZ = intArrayOf(50, 60)

        const val DEFAULT_HARMONICS = 6
        const val DEFAULT_BANDWIDTH_HZ = 1.0

        /**
         * True for a pitch at mains hum's own frequency, 50 or 60 Hz within [MAINS_GUARD_HZ]: what
         * hum alone reads in the low range before the notches have learned it (or what is left
         * of a hum with more harmonics than they take out). No string is tuned there (G1 + 35 cents,
         * B1 − 47), and a note held there loses its fundamental to the notches anyway.
         */
        fun isMainsPitch(frequencyHz: Float): Boolean =
            MAINS_HZ.any { abs(frequencyHz - it) <= MAINS_GUARD_HZ }

        /**
         * Mains rarely strays 0.2 Hz from nominal, but while the tracking still catches up, what is
         * left of a hum 0.1 Hz off read up to 0.35 Hz off: ±17 cents at 50 Hz, ±14 at 60.
         */
        const val MAINS_GUARD_HZ = 0.5f

        private const val MAX_REFS = 32

        // Digital silence, and the quietest room level compared against (-120 dBFS), so float
        // rounding alone never counts as a note.
        private const val SILENCE_POWER = 1e-14
        private const val ROOM_FLOOR = 1e-12

        // A sample above 5× the room's RMS (a pluck), or the last few ms 6 dB above it (a swell),
        // is a note; back within 3 dB, it is room again.
        private const val TRIGGER = 5.0
        private const val SWELL = 4.0
        private const val RELEASE = 2.0
        private const val SHORT_SECONDS = 0.01
        private const val ROOM_SECONDS = 0.2
        private const val HOLD_SECONDS = 0.02
        private const val WARM_UP_SECONDS = 0.02

        /** While a note holds the weights, the room's level creeps up this fast. */
        const val ROOM_RISE_DB_PER_SECOND = 1.0

        // Frequency tracking, in blocks of 100 ms (a whole period of the 10 Hz between 50 and 60 Hz
        // and their harmonics, which ripples the weights): while the hum's fundamental stands above
        // what is left and its estimate's power stays within 2 % from block to block, 1.5 s to let
        // the notches settle (about 5 of their time constants: after only 2.5 they still turned at
        // a tenth of the last step, and the next step went the wrong way), then the turn averaged
        // over 0.5 s; never more than 0.5 % off nominal (±0.25 Hz at 50 Hz).
        private const val BLOCK_SECONDS = 0.1
        private const val HUM_CLEAR = 1.0
        private const val SETTLED = 1.02
        private const val SETTLE_BLOCKS = 15
        private const val MEASURE_BLOCKS = 5
        private const val MAX_DRIFT = 0.005

        // An estimate is hum once, two blocks in a row, it turned less than 0.25 rad in a block
        // (0.4 Hz from its notch; mains strays 0.25 Hz at most) and as its fundamental times k.
        private const val HUM_TURN = 0.25
        private const val HUM_BLOCKS = 2
        private const val TURN_STEPS = 10
    }
}
