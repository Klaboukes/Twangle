package io.github.klaboukes.twangle.core.dsp.drone

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Up to [capacity] partials at exact frequencies, as one block of the drone ([DroneSynth.BLOCK]
 * samples) at a time: each is a phasor turned by its exact complex step a sample (`z ← e^{iω}·z`,
 * output `level·Im z`), as [io.github.klaboukes.twangle.core.dsp.synth.StringSynth]'s are, so the
 * pitch is exact and never drifts however long it runs. Its level is set once a block by the
 * engine ([next], the level at the end of the coming block) and ramped linearly across it from the
 * last, so an envelope moves smoothly. After each block every phasor is put back on the unit circle,
 * so rounding never changes a level, hour after hour.
 *
 * Everything is allocated here, at construction; [add] and [renderAdd] allocate nothing.
 */
internal class PartialBank(val capacity: Int) {
    private val re = DoubleArray(capacity)
    private val im = DoubleArray(capacity)
    private val stepRe = DoubleArray(capacity)
    private val stepIm = DoubleArray(capacity)
    private val now = DoubleArray(capacity)

    /** Each partial's frequency in Hz, as [add] set it. */
    val hz = DoubleArray(capacity)

    /** The level each partial reaches at the end of the coming block; the engine writes it before [renderAdd]. */
    val next = DoubleArray(capacity)

    /** How many partials sound: those at 0 until [count]. An engine may lower it to drop partials that have died away. */
    var count = 0

    fun clear() {
        count = 0
    }

    /** Adds a partial at [frequencyHz], starting at [phase] (radians) with level 0, at [sampleRate]. */
    fun add(frequencyHz: Double, phase: Double, sampleRate: Int) {
        val i = count++
        hz[i] = frequencyHz
        re[i] = cos(phase)
        im[i] = sin(phase)
        val w = 2 * PI * frequencyHz / sampleRate
        stepRe[i] = cos(w)
        stepIm[i] = sin(w)
        now[i] = 0.0
        next[i] = 0.0
    }

    /** Makes [next] the level the partials have now, so the first block starts there rather than ramping up from 0. */
    fun settle() {
        next.copyInto(now, endIndex = count)
    }

    /** The level partial [i] has now, at the start of the coming block. */
    fun levelNow(i: Int): Double = now[i]

    /**
     * Adds [n] samples into [out] from 0, every level ramped from where it is to [next]; then [next]
     * is where it is. Partial by partial, four at a time, rather than sample by sample: the four
     * phasors and levels stay in registers for the whole block, only [out] (one block, always in the
     * cache) is read and written, and the four independent rotations keep the core busy where one
     * would wait on its own last result.
     */
    fun renderAdd(out: DoubleArray, n: Int) {
        val count = count
        val perSample = 1.0 / n
        var i = 0
        while (i + 3 < count) {
            var r0 = re[i + 0]
            var j0 = im[i + 0]
            val c0 = stepRe[i + 0]
            val s0 = stepIm[i + 0]
            var l0 = now[i + 0]
            val d0 = (next[i + 0] - l0) * perSample
            var r1 = re[i + 1]
            var j1 = im[i + 1]
            val c1 = stepRe[i + 1]
            val s1 = stepIm[i + 1]
            var l1 = now[i + 1]
            val d1 = (next[i + 1] - l1) * perSample
            var r2 = re[i + 2]
            var j2 = im[i + 2]
            val c2 = stepRe[i + 2]
            val s2 = stepIm[i + 2]
            var l2 = now[i + 2]
            val d2 = (next[i + 2] - l2) * perSample
            var r3 = re[i + 3]
            var j3 = im[i + 3]
            val c3 = stepRe[i + 3]
            val s3 = stepIm[i + 3]
            var l3 = now[i + 3]
            val d3 = (next[i + 3] - l3) * perSample
            for (t in 0 until n) {
                out[t] += l0 * j0 + l1 * j1 + l2 * j2 + l3 * j3
                val q0 = r0 * c0 - j0 * s0
                j0 = r0 * s0 + j0 * c0
                r0 = q0
                l0 += d0
                val q1 = r1 * c1 - j1 * s1
                j1 = r1 * s1 + j1 * c1
                r1 = q1
                l1 += d1
                val q2 = r2 * c2 - j2 * s2
                j2 = r2 * s2 + j2 * c2
                r2 = q2
                l2 += d2
                val q3 = r3 * c3 - j3 * s3
                j3 = r3 * s3 + j3 * c3
                r3 = q3
                l3 += d3
            }
            finish(i, r0, j0)
            finish(i + 1, r1, j1)
            finish(i + 2, r2, j2)
            finish(i + 3, r3, j3)
            i += 4
        }
        while (i < count) {
            var r0 = re[i]
            var j0 = im[i]
            val c0 = stepRe[i]
            val s0 = stepIm[i]
            var l0 = now[i]
            val d0 = (next[i] - l0) * perSample
            for (t in 0 until n) {
                out[t] += l0 * j0
                val q0 = r0 * c0 - j0 * s0
                j0 = r0 * s0 + j0 * c0
                r0 = q0
                l0 += d0
            }
            finish(i, r0, j0)
            i++
        }
    }

    // Stores partial i's phasor back on the unit circle (so rounding never changes a level) and its level as reached.
    private fun finish(i: Int, r: Double, j: Double) {
        val m = 1 / sqrt(r * r + j * j)
        re[i] = r * m
        im[i] = j * m
        now[i] = next[i]
    }
}
