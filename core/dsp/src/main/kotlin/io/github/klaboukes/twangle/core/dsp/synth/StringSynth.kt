package io.github.klaboukes.twangle.core.dsp.synth

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A note of one instrument ([voice]) at an exact frequency, for the reference-tone player: a
 * plucked string sounds, dies away and ends by itself after [durationSeconds]; a bowed one swells
 * in, holds, and the bow lifts [END_FADE_SECONDS] before the end so the string rings out.
 *
 * Additive synthesis, one decaying phasor per partial (`z ← r·e^{iω}·z`, output `Im z`): the pitch
 * is exact (no wavetable rounding) and a sample costs a few multiply-adds per partial. What the
 * partials are, how loud they start and how fast they die comes from the [StringVoice]: the pluck
 * point's comb, the body's resonances, the string's stiffness (partial k at `k·f·√(1 + B·k²)`, the
 * fundamental exactly on pitch), two decay rates per partial, a mandolin's two courses. Partials
 * stop below [MAX_PARTIAL_HZ] and 0.45 × the sample rate, so nothing aliases, with at most
 * [MAX_PARTIALS] per course; low notes keep many partials, so a phone speaker that can't play the
 * fundamental still gives the right pitch.
 *
 * On top of the partials run the sounds a keyboard lacks: a plucked note starts a few cents sharp
 * and settles (the phasors' steps are turned [GLIDE_UPDATES_PER_SECOND] times a second while it
 * matters), a pick's scrape and the body's knock at the start (highpassed noise, and the body's
 * modes as two-pole resonators rung by the same burst), and under a bowed note the bow's noise
 * through the same resonators, with a slow sway of the level. The noise is the same every note (a
 * fixed seed), so a tone is reproducible and its loudest sample is known.
 *
 * Never clips: the partials' start levels add up to the headroom left under [PEAK] by the loudest
 * the noise path gets (found once by running it, in the constructor), and partials only get quieter;
 * in practice a note peaks near half of [PEAK], as a pluck's partials alternate in sign.
 * The envelope keeps it free of clicks: the first sample is 0, the partials fade in over the voice's
 * attack, the noise over [NOISE_RAMP_SECONDS], and a raised-cosine fade-out ends on exactly 0, over
 * the last [END_FADE_SECONDS] or, after [release], over the time asked for.
 *
 * [start], [release] and [render] allocate nothing. Not thread-safe: one thread renders.
 */
class StringSynth(
    val sampleRate: Int,
    val voice: StringVoice,
    val durationSeconds: Float = DEFAULT_DURATION_SECONDS,
) {

    init {
        require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }
        require(durationSeconds > END_FADE_SECONDS + voice.attackSeconds + MIN_HOLD_SECONDS) {
            "durationSeconds must be longer than the attack, a moment of tone and the end fade, was $durationSeconds"
        }
    }

    /** Highest frequency [start] accepts: partials above it would alias or be inaudible. */
    val maxFrequencyHz: Float = maxFrequencyHz(sampleRate)

    private val totalSamples = (durationSeconds * sampleRate).roundToInt()
    private val attackSamples = (voice.attackSeconds * sampleRate).roundToInt().coerceAtLeast(1)
    private val endFadeSamples = (END_FADE_SECONDS * sampleRate).roundToInt().coerceAtLeast(1)
    private val noiseRampSamples = (NOISE_RAMP_SECONDS * sampleRate).roundToInt().coerceAtLeast(1)
    private val glideStep = (sampleRate / GLIDE_UPDATES_PER_SECOND).coerceAtLeast(1)

    /** The glide is recomputed until here, where less than [GLIDE_FLOOR_CENTS] of it is left. */
    private val glideSamples =
        if (voice.glideCents > 0) ceil(voice.glideSeconds * ln(voice.glideCents / GLIDE_FLOOR_CENTS) * sampleRate).toInt().coerceAtLeast(1) else 0

    // Per partial (both decay planes of every course): the phasor (re, im), its per-sample step
    // r·(cos ω, sin ω), the angle ω itself and the decaying radius r, for when the steps change.
    private val capacity = MAX_PARTIALS * voice.courses * (if (voice.fastShare > 0) 2 else 1)
    private val re = DoubleArray(capacity)
    private val im = DoubleArray(capacity)
    private val stepRe = DoubleArray(capacity)
    private val stepIm = DoubleArray(capacity)
    private val omega = DoubleArray(capacity)
    private val radius = DoubleArray(capacity)
    private var partials = 0

    // Body modes as two-pole resonators: y = x + f1·y1 + f2·y2, scaled to a peak gain of 1.
    private val modes = voice.body.size
    private val modeFeedback1 = DoubleArray(modes)
    private val modeFeedback2 = DoubleArray(modes)
    private val modeGain = DoubleArray(modes)
    private val modeY1 = DoubleArray(modes)
    private val modeY2 = DoubleArray(modes)

    // The noise path: a seeded generator, one-pole filters, and the envelopes of the burst and the bow's catch.
    private var noiseState = 0
    private val highpassA = exp(-2.0 * PI * voice.attackNoiseHz / sampleRate)
    private var highpassX1 = 0.0
    private var highpassY1 = 0.0
    private val lowpassA = exp(-2.0 * PI * voice.bowNoiseHz / sampleRate)
    private var lowpassY = 0.0
    private val burstDecay = exp(-1.0 / (voice.attackNoiseSeconds * sampleRate))
    private var burst = 0.0
    private val scratchDecay = exp(-1.0 / (BOW_SCRATCH_SECONDS * sampleRate))
    private var scratch = 0.0
    private val swayA = exp(-2.0 * PI * SWAY_HZ / sampleRate)
    private var sway1 = 0.0
    private var sway2 = 0.0
    private var noiseOut = 0.0
    private var swayOut = 0.0

    /** Scales the sway filter's output to a swing of at most ±[StringVoice.sway]; 0 for a voice without sway. */
    private val swayScale: Double

    /** The loudest sample the noise path (pick or bow noise, the body's knock) makes in a tone; the same every tone. */
    val noisePeak: Double

    /** What the partials' start levels add up to: the headroom under [PEAK] left by [noisePeak] and the sway. */
    val harmonicPeak: Double

    private var position = 0
    private var sounding = false
    private var bowLifted = false

    // Fade-out in progress: fadeLength samples from level fadeFrom down to 0. fadeLength 0 = none.
    private var fadePosition = 0
    private var fadeLength = 0
    private var fadeFrom = 1.0

    /** True from [start] until the tone has faded to silence. */
    val isSounding: Boolean get() = sounding

    /** Frequency of the tone last started, in Hz (0 before the first [start]). */
    var frequencyHz: Float = 0f
        private set

    init {
        for (b in 0 until modes) {
            val mode = voice.body[b]
            val theta = 2.0 * PI * mode.hz / sampleRate
            // A −3 dB bandwidth of hz / q: the poles' radius is 1 − π·bandwidth / rate.
            val r = exp(-PI * (mode.hz / mode.q) / sampleRate)
            modeFeedback1[b] = 2.0 * r * cos(theta)
            modeFeedback2[b] = -r * r
            // |H(e^{iθ})| = 1 / |1 − f1·e^{−iθ} − f2·e^{−2iθ}|, the resonator's gain at its own frequency.
            val denominatorRe = 1.0 - modeFeedback1[b] * cos(theta) - modeFeedback2[b] * cos(2 * theta)
            val denominatorIm = modeFeedback1[b] * sin(theta) + modeFeedback2[b] * sin(2 * theta)
            modeGain[b] = sqrt(denominatorRe * denominatorRe + denominatorIm * denominatorIm)
        }
        // Run the noise path and the sway once, as a tone will, to learn how loud they get.
        resetNoise()
        var loudest = 0.0
        var swayPeak = 0.0
        for (n in 0 until totalSamples) {
            advanceNoise(n)
            loudest = maxOf(loudest, abs(noiseOut))
            swayPeak = maxOf(swayPeak, abs(swayOut))
        }
        noisePeak = loudest
        swayScale = if (voice.sway > 0 && swayPeak > 0) voice.sway / swayPeak else 0.0
        harmonicPeak = (PEAK - noisePeak) / (1.0 + voice.sway)
        require(harmonicPeak >= PEAK * MIN_HARMONIC_SHARE) { "${voice.id}: the noise leaves too little room for the strings" }
    }

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
        val f = frequencyHz.toDouble()
        val stiffness = voice.stiffnessAt(f)
        partials = 0
        var levelSum = 0.0
        for (course in 0 until voice.courses) {
            // The courses sit evenly around the pitch (a pair at ±half the spread), so their mean is exact.
            val offsetCents = if (voice.courses == 1) 0.0 else (course - (voice.courses - 1) / 2.0) * voice.courseSpreadCents / (voice.courses - 1)
            val courseHz = f * 2.0.pow(offsetCents / 1200)
            var k = 1
            while (k <= MAX_PARTIALS) {
                val partialHz = k * courseHz * sqrt((1 + stiffness * k * k) / (1 + stiffness))
                if (partialHz > maxFrequencyHz) break
                val level = voice.partialLevel(k, partialHz)
                val decaySeconds = voice.decaySecondsAt(partialHz)
                val w = 2.0 * PI * partialHz / sampleRate
                addPartial(w, exp(-1.0 / (decaySeconds * sampleRate)), (1 - voice.fastShare) * level)
                if (voice.fastShare > 0) addPartial(w, exp(-1.0 / (decaySeconds * voice.fastRatio * sampleRate)), voice.fastShare * level)
                levelSum += abs(level)
                k++
            }
        }
        // The partials' levels (unsigned) add up to harmonicPeak, so even all aligned they can't clip.
        val scale = harmonicPeak / levelSum
        for (i in 0 until partials) {
            re[i] *= scale
            im[i] = 0.0
        }
        bowLifted = false
        setSteps(if (glideSamples > 0) glideFactor(0) else 1.0)
        position = 0
        fadePosition = 0
        fadeLength = 0
        fadeFrom = 1.0
        resetNoise()
        sounding = true
    }

    private fun addPartial(w: Double, r: Double, level: Double) {
        val i = partials++
        omega[i] = w
        radius[i] = r
        re[i] = level
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
                if (voice.bowed) liftBow()
            }
            if (position in 1..glideSamples && (position % glideStep == 0 || position == glideSamples)) {
                setSteps(if (position == glideSamples) 1.0 else glideFactor(position))
            }
            var strings = 0.0
            for (i in 0 until partials) {
                val r = re[i]
                val j = im[i]
                re[i] = r * stepRe[i] - j * stepIm[i]
                im[i] = r * stepIm[i] + j * stepRe[i]
                strings += im[i]
            }
            if (position < attackSamples) strings *= raisedCosine(position.toDouble() / attackSamples)
            advanceNoise(position)
            if (swayScale > 0) strings *= 1.0 + swayOut * swayScale
            var gain = 1.0
            if (fadeLength > 0) {
                fadePosition++
                gain = currentFadeLevel()
                if (fadePosition >= fadeLength) sounding = false
            }
            out[offset + written] = ((strings + noiseOut) * gain).toFloat()
            written++
            position++
        }
        return written
    }

    /** The pitch factor of the pluck's glide at sample [n]: 2^(cents/1200) of what is left of it. */
    private fun glideFactor(n: Int): Double =
        2.0.pow(voice.glideCents * exp(-n / (voice.glideSeconds * sampleRate)) / 1200)

    /**
     * Sets every partial's step from its angle × [pitchFactor] and its radius: 1 (a steady level)
     * while a bow is on the string, otherwise the decay.
     */
    private fun setSteps(pitchFactor: Double) {
        val steady = voice.bowed && !bowLifted
        for (i in 0 until partials) {
            val r = if (steady) 1.0 else radius[i]
            val w = omega[i] * pitchFactor
            stepRe[i] = r * cos(w)
            stepIm[i] = r * sin(w)
        }
    }

    /** The bow leaves the string: from here the partials decay as a plucked string's would. */
    private fun liftBow() {
        bowLifted = true
        setSteps(1.0)
    }

    private fun resetNoise() {
        noiseState = SEED
        highpassX1 = 0.0
        highpassY1 = 0.0
        lowpassY = 0.0
        burst = if (voice.bowed) 0.0 else 1.0
        scratch = 1.0
        sway1 = 0.0
        sway2 = 0.0
        noiseOut = 0.0
        swayOut = 0.0
        modeY1.fill(0.0)
        modeY2.fill(0.0)
    }

    /**
     * One sample of the noise path at sample [n], into [noiseOut]: the pick's scrape or the bow's
     * noise, direct and through the body's resonators, ramped in over the first samples. Also
     * advances the sway ([swayOut]), a slow drift of the bow's pressure.
     */
    private fun advanceNoise(n: Int) {
        val white = nextRandom()
        val excitation: Double
        val direct: Double
        if (voice.bowed) {
            lowpassY += (1 - lowpassA) * (white - lowpassY)
            val level = voice.bowNoise * (1 + BOW_SCRATCH * scratch)
            scratch *= scratchDecay
            excitation = lowpassY * level
            direct = excitation * BOW_NOISE_DIRECT
        } else {
            val highpassed = highpassA * (highpassY1 + white - highpassX1)
            highpassX1 = white
            highpassY1 = highpassed
            excitation = white * burst * voice.bodyThump
            direct = highpassed * burst * voice.attackNoise
            burst *= burstDecay
        }
        var sum = direct
        for (b in 0 until modes) {
            val y = excitation + modeFeedback1[b] * modeY1[b] + modeFeedback2[b] * modeY2[b]
            modeY2[b] = modeY1[b]
            modeY1[b] = y
            sum += y * modeGain[b]
        }
        noiseOut = if (n < noiseRampSamples) sum * n / noiseRampSamples else sum
        sway1 += (1 - swayA) * (white - sway1)
        sway2 += (1 - swayA) * (sway1 - sway2)
        swayOut = sway2
    }

    /** Uniform noise in −1..1 (xorshift32, Marsaglia 2003). */
    private fun nextRandom(): Double {
        var x = noiseState
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        noiseState = x
        return x * (1.0 / Int.MAX_VALUE)
    }

    // Level of the fade-out at fadePosition: fadeFrom → 0 along half a cosine, exactly 0 at the end.
    private fun currentFadeLevel(): Double =
        if (fadeLength == 0) 1.0 else fadeFrom * (1.0 - raisedCosine(fadePosition.toDouble() / fadeLength))

    // 0 → 1 along half a cosine as x goes 0 → 1.
    private fun raisedCosine(x: Double): Double = 0.5 - 0.5 * cos(PI * x)

    companion object {
        /** Length of a tone from [start] to silence. */
        const val DEFAULT_DURATION_SECONDS = 4f

        /** Fade-out at the natural end of a tone; a bow lifts off the string as it begins. */
        const val END_FADE_SECONDS = 0.6f

        /** Default fade-out for [release], e.g. when the player is stopped: quick, but no click. */
        const val STOP_FADE_SECONDS = 0.03f

        /**
         * Largest possible sample value: the partials' levels add up to what the noise leaves of it,
         * so even all aligned they can't pass it. A note's real peak is nearer half of it, since a
         * pluck's partials alternate in sign and a bow's spread out over the period.
         */
        const val PEAK = 0.98

        /** Most partials a course uses (a low bass note). */
        const val MAX_PARTIALS = 60

        /** Most courses (strings sounding together on one pitch) a voice has: a mandolin's pair. */
        const val MAX_COURSES = 2

        /** Most body modes a voice has. */
        const val MAX_MODES = 4

        /** No partial above this frequency: enough brightness for a pick or a bow, little work per sample. */
        const val MAX_PARTIAL_HZ = 8_000f

        /** The noise and the body's knock fade in over this, so the first sample is 0 and nothing jumps. */
        const val NOISE_RAMP_SECONDS = 0.002f

        /** A plucked note's glide is recomputed this often while any of it is left. */
        const val GLIDE_UPDATES_PER_SECOND = 1_000

        /** The glide is over when less than this is left of it. */
        const val GLIDE_FLOOR_CENTS = 0.02

        /** The bow's noise is this many times louder as it catches the string, dying over [BOW_SCRATCH_SECONDS]. */
        const val BOW_SCRATCH = 4.0
        const val BOW_SCRATCH_SECONDS = 0.08

        /** How much of the bow's noise is heard direct, next to what the body's modes make of it. */
        const val BOW_NOISE_DIRECT = 0.5

        /** The sway of a bowed note's level is this slow (two one-pole lowpasses of noise). */
        const val SWAY_HZ = 1.5

        /** A tone must hold at least this long between its attack and its end fade. */
        const val MIN_HOLD_SECONDS = 0.3f

        /** The strings must get at least this share of [PEAK]; a voice whose noise is louder is refused. */
        const val MIN_HARMONIC_SHARE = 0.6

        private const val NYQUIST_FRACTION = 0.45f
        private const val SEED = 0x2545F491

        /** Highest frequency a synth at [sampleRate] plays: [MAX_PARTIAL_HZ], or less at a low rate. */
        fun maxFrequencyHz(sampleRate: Int): Float = minOf(MAX_PARTIAL_HZ, NYQUIST_FRACTION * sampleRate)
    }
}
