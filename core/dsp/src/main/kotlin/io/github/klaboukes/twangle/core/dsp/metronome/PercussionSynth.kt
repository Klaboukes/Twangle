package io.github.klaboukes.twangle.core.dsp.metronome

import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/**
 * Modal synthesis of short, struck clicks: the cowbell, claves and rim click of [ClickSounds]
 * (recipes in [ClickRecipes]). Used only while [ClickSounds] renders, once per sample rate, so it
 * may allocate.
 *
 * A hit is what happens physically, in three parts:
 * 1. **Contact.** The stick or mallet presses on the instrument for a fraction of a millisecond,
 *    modelled as a raised-cosine force pulse. Hertz's contact law gives a contact time that shortens
 *    with the striking speed v (∝ v^−1/5) and a peak force that grows as v^6/5, so the pulse's area
 *    (the momentum handed over) grows as v (Hertz's law as used for mallets by Chaigne & Doutaut,
 *    "Numerical simulations of xylophones I", JASA 101, 1997). A shorter pulse reaches higher modes,
 *    so a harder hit is louder *and* brighter.
 * 2. **Modes.** The force drives a bank of two-pole resonators, one per vibrating mode, each with
 *    its own frequency, decay and strength (its shape at the strike point, signed as heard).
 * 3. **Strike noise.** A short band-passed noise burst for the impact itself, which no mode explains.
 *
 * Clicks to keep time by, not instruments: every mode is damped hard, there is no room, and a hit
 * is the same every time. The noise comes from a [Random] seeded by the recipe's name and the
 * listener's place, so it is the same on every run and every device (java.util.Random's generator
 * and its nextGaussian are fixed by the Java specification). Only the accent changes how hard it is
 * struck and the pitch.
 */
internal object PercussionSynth {

    /** One mode: [hz], its strength [amp] before the contact's filtering, and its time constant. */
    class Mode(val hz: Double, val amp: Double, val decaySeconds: Double)

    /** A band-passed noise burst: raised-cosine attack after [delaySeconds], then exponential decay. */
    class Burst(
        val centerHz: Double,
        val q: Double,
        val decaySeconds: Double,
        val level: Double,
        val delaySeconds: Double = 0.0,
        val attackSeconds: Double = 0.0002,
    )

    class Recipe(
        /** Seeds the strike noise (with the listener's place); also the preview's file name. */
        val name: String,
        val lengthSeconds: Double,
        val fadeSeconds: Double,
        /** Contact time of a hit at striking speed 1. */
        val contactSeconds: Double,
        /** Striking speed of a plain beat. */
        val plainVelocity: Double,
        /** Striking speed of the downbeat: harder, so louder and brighter (a group accent sits halfway). */
        val downbeatVelocity: Double,
        /** How much higher the downbeat's modes ring than a plain beat's. */
        val downbeatPitch: Double,
        /** How much higher a group accent's modes ring than a plain beat's: between the two. */
        val groupPitch: Double,
        val bursts: List<Burst>,
        /**
         * Where each accent is heard from (a seed for the modes' signs and the noise): of 300
         * places, the one with the least peak for its energy whose energy still peaks within
         * 1.6 ms of the onset, found at 48 kHz by `ClickCandidatesReport`.
         */
        val downbeatListener: Long,
        val groupListener: Long,
        val plainListener: Long,
        /** The modes at [pitchFactor]; [listener] gives each mode's sign where it is heard. */
        val modes: (pitchFactor: Double, listener: Random) -> List<Mode>,
    )

    fun velocityOf(recipe: Recipe, accent: BeatAccent): Double = when (accent) {
        BeatAccent.DOWNBEAT -> recipe.downbeatVelocity
        BeatAccent.GROUP -> (recipe.downbeatVelocity + recipe.plainVelocity) / 2
        BeatAccent.PLAIN -> recipe.plainVelocity
    }

    fun pitchOf(recipe: Recipe, accent: BeatAccent): Double = when (accent) {
        BeatAccent.DOWNBEAT -> recipe.downbeatPitch
        BeatAccent.GROUP -> recipe.groupPitch
        BeatAccent.PLAIN -> 1.0
    }

    fun listenerOf(recipe: Recipe, accent: BeatAccent): Long = when (accent) {
        BeatAccent.DOWNBEAT -> recipe.downbeatListener
        BeatAccent.GROUP -> recipe.groupListener
        BeatAccent.PLAIN -> recipe.plainListener
    }

    /**
     * One hit of [recipe] at [accent] and [sampleRate], heard from the place [listenerSeed] picks
     * (the recipe's own by default): the same every time, at full strength (not yet scaled).
     * Starts and ends on exactly 0.
     */
    fun hit(
        recipe: Recipe,
        accent: BeatAccent,
        sampleRate: Int,
        listenerSeed: Long = listenerOf(recipe, accent),
    ): DoubleArray {
        val frames = (recipe.lengthSeconds * sampleRate).roundToInt()
        val v = velocityOf(recipe, accent)
        val contact = recipe.contactSeconds * v.pow(-0.2)
        val force = v.pow(1.2)

        // Contact: a raised-cosine force pulse, 0 at both ends.
        val pulseFrames = (contact * sampleRate).roundToInt().coerceAtLeast(2)
        val excitation = DoubleArray(frames)
        for (n in 0..minOf(pulseFrames, frames - 1)) excitation[n] = force * (0.5 - 0.5 * cos(2 * PI * n / pulseFrames))
        val area = force * pulseFrames / 2

        val out = DoubleArray(frames)
        val pitch = pitchOf(recipe, accent)
        for (mode in recipe.modes(pitch, Random(listenerSeed))) {
            // A mode past 0.45 × the rate is left out, so nothing folds back.
            if (mode.hz < 0.45 * sampleRate) resonate(excitation, mode.hz, mode.amp, mode.decaySeconds, sampleRate, out)
        }
        val noise = Random(recipe.name.hashCode().toLong() * 1000 + listenerSeed)
        // The strike noise moves with the pitch too (a smaller, higher body has a brighter impact).
        for (burst in recipe.bursts) {
            val centerHz = minOf(burst.centerHz * pitch, 0.4 * sampleRate)
            addBurst(burst, centerHz, area * burst.level * sqrt(v), noise, sampleRate, out)
        }

        // Raised-cosine fade over the last fadeSeconds, ending on exactly 0.
        val fade = recipe.fadeSeconds * sampleRate
        for (n in out.indices) {
            val left = frames - 1 - n
            if (left < fade) out[n] *= raisedCosine(left / fade)
        }
        return out
    }

    // A two-pole resonator: an impulse gives amp · e^(−t/τ) · sin(2π f t) exactly.
    private fun resonate(x: DoubleArray, hz: Double, amp: Double, decaySeconds: Double, sampleRate: Int, out: DoubleArray) {
        val w = 2 * PI * hz / sampleRate
        val r = exp(-1.0 / (decaySeconds * sampleRate))
        val c1 = 2 * r * cos(w)
        val c2 = -r * r
        val g = amp * r * sin(w)
        var y1 = 0.0
        var y2 = 0.0
        for (n in x.indices) {
            val y = c1 * y1 + c2 * y2 + g * (if (n > 0) x[n - 1] else 0.0)
            out[n] += y
            y2 = y1
            y1 = y
        }
    }

    private fun addBurst(burst: Burst, centerHz: Double, level: Double, rnd: Random, sampleRate: Int, out: DoubleArray) {
        val bp = BandPass(centerHz, burst.q, sampleRate)
        val delay = burst.delaySeconds * sampleRate
        val attack = (burst.attackSeconds * sampleRate).coerceAtLeast(1.0)
        for (n in out.indices) {
            val t = n - delay
            val env = when {
                t <= 0 -> 0.0
                t < attack -> raisedCosine(t / attack)
                else -> exp(-(t - attack) / (burst.decaySeconds * sampleRate))
            }
            out[n] += level * env * bp.filter(rnd.nextGaussian())
        }
    }

    private fun raisedCosine(x: Double): Double = 0.5 - 0.5 * cos(PI * x)

    /** Band-pass of R. Bristow-Johnson's "Audio EQ Cookbook", 0 dB at [centerHz]. */
    private class BandPass(centerHz: Double, q: Double, sampleRate: Int) {
        private val b0: Double
        private val a1: Double
        private val a2: Double
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        init {
            val w0 = 2 * PI * centerHz / sampleRate
            val alpha = sin(w0) / (2 * q)
            val a0 = 1 + alpha
            b0 = alpha / a0
            a1 = -2 * cos(w0) / a0
            a2 = (1 - alpha) / a0
        }

        fun filter(x: Double): Double {
            val y = b0 * x - b0 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
            return y
        }
    }

    // ---- Mode sets from the physics of each kind of vibrating body. ----

    // βL of a free-free bar's bending modes (Fletcher & Rossing §2.15; (2n + 1)π/2 from the third on).
    private val BAR_BETA = doubleArrayOf(4.7300, 7.8532, 10.9956, 14.1372, 17.2788, 20.4204, 23.5619)

    /**
     * A free-free bar's bending-mode ratios. Euler–Bernoulli gives (β_n / β_1)² = 1 : 2.756 : 5.404
     * : 8.933 …; a thick bar's shear and rotary inertia lower the higher modes, here the
     * Timoshenko-beam estimate f ∝ β² / √(1 + (β·g)² (1 + E/κG)), g the radius of gyration over the
     * length. Wood is soft in shear (E/G ≈ 12–16), so a thick wooden bar is lowered a lot.
     */
    fun barRatios(count: Int, gyrationOverLength: Double = 0.0, eOverKappaG: Double = 0.0): DoubleArray {
        val f = DoubleArray(count) { n ->
            val b = BAR_BETA[n]
            b * b / sqrt(1 + (b * gyrationOverLength).pow(2) * (1 + eOverKappaG))
        }
        return DoubleArray(count) { f[it] / f[0] }
    }

    /** A free-free bar's bending mode [n] (from 0) at [x] along it (0..1), 1 at the ends (Fletcher & Rossing §2.15). */
    fun barShape(n: Int, x: Double): Double {
        val b = BAR_BETA[n]
        val s = (cosh(b) - cos(b)) / (sinh(b) - sin(b))
        return (cosh(b * x) + cos(b * x) - s * (sinh(b * x) + sin(b * x))) / 2
    }

    /**
     * Zeros j_mn of the Bessel functions: an ideal circular membrane's mode (m, n) rings at
     * j_mn / j_01 × f01 (Fletcher & Rossing §3.5).
     */
    val MEMBRANE: List<Triple<Int, Int, Double>> = listOf(
        Triple(0, 1, 2.4048), Triple(1, 1, 3.8317), Triple(2, 1, 5.1356), Triple(0, 2, 5.5201),
        Triple(3, 1, 6.3802), Triple(1, 2, 7.0156), Triple(4, 1, 7.5883), Triple(2, 2, 8.4172),
    )

    /** Bessel function J_m(x) by its power series (Abramowitz & Stegun 9.1.10; fine for x below about 20). */
    fun besselJ(m: Int, x: Double): Double {
        var term = (x / 2).pow(m)
        for (i in 1..m) term /= i
        var sum = 0.0
        for (k in 0 until 60) {
            sum += term
            term *= -(x / 2) * (x / 2) / ((k + 1) * (k + 1 + m))
        }
        return sum
    }

    /** A thin ring's in-plane bending modes n = 2, 3, …: f ∝ n(n² − 1)/√(n² + 1) (Fletcher & Rossing, ch. 2), 1 at n = 2. */
    fun ringRatio(n: Int): Double = n * (n * n - 1.0) / sqrt(n * n + 1.0) / (2 * 3.0 / sqrt(5.0))

    /** Decay of wood: 1/τ = a + b·f², the loss law Chaigne & Doutaut (1997) fitted to xylophone bars. */
    fun woodDecay(hz: Double, a: Double, b: Double): Double = 1.0 / (a + b * hz * hz)

    /** +1 or −1: a mode's sign where it is heard, so the modes don't all add up in the first instant. */
    fun Random.sign(): Double = if (nextBoolean()) 1.0 else -1.0
}
