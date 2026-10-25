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
 * Modal synthesis of struck percussion, for the Phase 45 click candidates ([ClickCandidatesReport]).
 * Test sources only: nothing here is in the app.
 *
 * A hit is what happens physically, in three parts:
 * 1. **Contact.** The mallet or stick presses on the instrument for a short time, modelled as a
 *    raised-cosine force pulse. Hertz's contact law gives a contact time that shortens with the
 *    striking speed v (∝ v^−1/5) and a peak force that grows as v^6/5, so the pulse's area (the
 *    momentum handed over) grows as v (Hertz's law as used for mallets by Chaigne & Doutaut,
 *    "Numerical simulations of xylophones I", JASA 101, 1997). A shorter pulse reaches higher modes, so a harder hit is louder *and* brighter.
 * 2. **Modes.** The force drives a bank of two-pole resonators, one per vibrating mode, each
 *    with its own frequency, decay and strength. The strength comes from the mode's shape at the
 *    strike point (a node isn't excited at all), so where the instrument is hit matters.
 * 3. **Strike noise.** A short band-passed noise burst for the impact itself (and, for a snare,
 *    the wires), which no mode explains.
 *
 * Then a little of one small room ([ROOM]): a few early reflections and a short diffuse tail
 * (velvet noise, Järveläinen & Karjalainen 2007), filtered dull, mixed low and arriving after the
 * direct sound so the attack stays crisp. Every hit gets a seeded, slightly different speed, contact
 * time, strike point and noise, so repeated beats aren't copies; the same seed gives the same hit.
 */
internal object PercussionSynth {

    const val RATE = 48_000

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

    class Sound(
        val file: String,
        val lengthSeconds: Double,
        val fadeSeconds: Double,
        /** Contact time at the downbeat's speed. */
        val contactSeconds: Double,
        val pitches: Map<BeatAccent, Double>,
        val bursts: List<Burst>,
        /** Level of the room next to the dry sound. */
        val ambience: Double,
        /** Strike point jitter between hits, as a fraction of each mode's strength. */
        val strikeJitter: Double = 0.06,
        val modes: (pitchHz: Double) -> List<Mode>,
    )

    /** Striking speed per accent, the downbeat being 1: plain beats are hit about 5 dB softer. */
    fun velocityOf(accent: BeatAccent): Double = when (accent) {
        BeatAccent.DOWNBEAT -> 1.0
        BeatAccent.GROUP -> 0.75
        BeatAccent.PLAIN -> 0.55
    }

    /** One hit of [sound] at [accent]; [seed] picks its small variations. Starts and ends on exactly 0. */
    fun hit(sound: Sound, accent: BeatAccent, seed: Long, ambience: Double = sound.ambience): DoubleArray {
        val rnd = Random(seed)
        val frames = (sound.lengthSeconds * RATE).roundToInt()
        val v = velocityOf(accent) * (1 + 0.03 * rnd.nextGaussian())
        val contact = sound.contactSeconds * v.pow(-0.2) * (1 + 0.03 * rnd.nextGaussian())
        val force = v.pow(1.2)

        // Contact: a raised-cosine force pulse, 0 at both ends.
        val pulseFrames = (contact * RATE).roundToInt().coerceAtLeast(2)
        val excitation = DoubleArray(frames)
        for (n in 0..minOf(pulseFrames, frames - 1)) excitation[n] = force * (0.5 - 0.5 * cos(2 * PI * n / pulseFrames))
        val area = force * pulseFrames / 2

        val out = DoubleArray(frames)
        for (mode in sound.modes(sound.pitches.getValue(accent))) {
            if (mode.hz >= 0.45 * RATE) continue
            val amp = mode.amp * (1 + sound.strikeJitter * rnd.nextGaussian())
            resonate(excitation, mode.hz, amp, mode.decaySeconds, out)
        }
        for (burst in sound.bursts) addBurst(burst, area * burst.level * sqrt(v), rnd, out)

        val wet = ROOM.apply(out)
        for (n in out.indices) out[n] += ambience * wet[n]

        // Raised-cosine fade over the last fadeSeconds, ending on exactly 0.
        val fade = sound.fadeSeconds * RATE
        for (n in out.indices) {
            val left = frames - 1 - n
            if (left < fade) out[n] *= raisedCosine(left / fade)
        }
        return out
    }

    // A two-pole resonator: an impulse gives amp · e^(−t/τ) · sin(2π f t) exactly.
    private fun resonate(x: DoubleArray, hz: Double, amp: Double, decaySeconds: Double, out: DoubleArray) {
        val w = 2 * PI * hz / RATE
        val r = exp(-1.0 / (decaySeconds * RATE))
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

    private fun addBurst(burst: Burst, level: Double, rnd: Random, out: DoubleArray) {
        val bp = BandPass(burst.centerHz, burst.q)
        val delay = burst.delaySeconds * RATE
        val attack = (burst.attackSeconds * RATE).coerceAtLeast(1.0)
        for (n in out.indices) {
            val t = n - delay
            val env = when {
                t <= 0 -> 0.0
                t < attack -> raisedCosine(t / attack)
                else -> exp(-(t - attack) / (burst.decaySeconds * RATE))
            }
            out[n] += level * env * bp.filter(rnd.nextGaussian())
        }
    }

    private fun raisedCosine(x: Double): Double = 0.5 - 0.5 * cos(PI * x)

    /** RBJ cookbook band-pass, 0 dB at [centerHz]. */
    private class BandPass(centerHz: Double, q: Double) {
        private val b0: Double
        private val a1: Double
        private val a2: Double
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        init {
            val w0 = 2 * PI * centerHz / RATE
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

    /**
     * A small, fairly dead room: eight early reflections 3–23 ms after the direct sound, then a
     * velvet-noise tail (sparse ±1 taps, 1500 a second) dying with RT60 ≈ 0.3 s, all through a
     * 5 kHz one-pole low-pass (walls and air take the highs). The same room for every sound.
     */
    private object ROOM {
        private val taps: List<Pair<Int, Double>> = buildList {
            val early = listOf(2.9 to 0.12, 4.3 to -0.10, 6.1 to 0.09, 8.7 to -0.08, 11.3 to 0.07, 14.2 to 0.06, 17.9 to -0.05, 22.6 to 0.04)
            for ((ms, gain) in early) add((ms * RATE / 1000).roundToInt() to gain)
            val rnd = Random(20261025)
            val tau = 0.3 / 6.91
            var t = 0.020
            while (t < 0.35) {
                val position = t + rnd.nextDouble() / 1500
                add((position * RATE).roundToInt() to (if (rnd.nextBoolean()) 1 else -1) * 0.07 * exp(-(position - 0.02) / tau))
                t += 1.0 / 1500
            }
        }

        fun apply(dry: DoubleArray): DoubleArray {
            val wet = DoubleArray(dry.size)
            for ((delay, gain) in taps) {
                for (n in 0 until dry.size - delay) wet[n + delay] += gain * dry[n]
            }
            val k = 1 - exp(-2 * PI * 5000.0 / RATE)
            var y = 0.0
            for (n in wet.indices) {
                y += k * (wet[n] - y)
                wet[n] = y
            }
            return wet
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

    /** A free-free bar's bending mode [n] (from 0) at [x] along it (0..1), 1 at the ends. */
    fun barShape(n: Int, x: Double): Double {
        val b = BAR_BETA[n]
        val s = (cosh(b) - cos(b)) / (sinh(b) - sin(b))
        return (cosh(b * x) + cos(b * x) - s * (sinh(b * x) + sin(b * x))) / 2
    }

    /** Zeros j_mn of the Bessel functions: an ideal circular membrane's mode (m, n) rings at j_mn / j_01 × f01. */
    val MEMBRANE: List<Triple<Int, Int, Double>> = listOf(
        Triple(0, 1, 2.4048), Triple(1, 1, 3.8317), Triple(2, 1, 5.1356), Triple(0, 2, 5.5201),
        Triple(3, 1, 6.3802), Triple(1, 2, 7.0156), Triple(4, 1, 7.5883), Triple(2, 2, 8.4172),
        Triple(0, 3, 8.6537), Triple(5, 1, 8.7715), Triple(3, 2, 9.7610), Triple(6, 1, 9.9361),
        Triple(1, 3, 10.1735),
    )

    /** Bessel function J_m(x) by its power series (fine for x below about 20). */
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

    /** A thin ring's in-plane bending modes n = 2, 3, …: f ∝ n(n² − 1)/√(n² + 1) (Fletcher & Rossing, *The Physics of Musical Instruments*, ch. 2). */
    fun ringRatio(n: Int): Double = n * (n * n - 1.0) / sqrt(n * n + 1.0) / (2 * 3.0 / sqrt(5.0))

    /** Decay of wood: 1/τ = a + b·f², the loss law Chaigne & Doutaut (1997) fitted to xylophone bars. */
    fun woodDecay(hz: Double, a: Double, b: Double): Double = 1.0 / (a + b * hz * hz)

}
