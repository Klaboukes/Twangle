package io.github.klaboukes.twangle.core.dsp.metronome

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The metronome's three clicks, one per [BeatAccent], rendered once at [sampleRate] so playing them
 * is only a copy.
 *
 * Each click is a short wood-block "tock": a struck free bar, whose first two bending modes ring at
 * frequencies 1 : 2.76 (Fletcher & Rossing, *The Physics of Musical Instruments*, 2nd ed. 1998,
 * §2.15). Both decay exponentially, the upper mode twice as fast, so the click starts bright and
 * ends round. Envelope: a [ATTACK_SECONDS] raised-cosine attack (no hard edge to crackle) and a
 * raised-cosine fade over the last [END_FADE_SECONDS], ending on exactly 0 after [CLICK_SECONDS].
 * The upper mode is left out where it would pass 0.45 × the sample rate, so nothing aliases.
 *
 * The accent is told apart by pitch and level: the downbeat at A6 (1760 Hz) and the loudest, a group
 * accent at E6 (1318.5 Hz), the other beats at A5 (880 Hz) and the quietest. High, short clicks cut
 * through a strummed guitar, and a phone speaker plays them well.
 */
class ClickSounds(val sampleRate: Int) {

    init {
        require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }
    }

    /** Length of every click, in samples. */
    val clickFrames: Int = (CLICK_SECONDS * sampleRate).roundToInt().coerceAtLeast(1)

    private val clicks: Array<FloatArray> = Array(BeatAccent.entries.size) { i ->
        val accent = BeatAccent.entries[i]
        render(frequencyOf(accent), peakOf(accent))
    }

    /** The click for [accent]. Shared and read-only: callers must not change it. */
    fun waveform(accent: BeatAccent): FloatArray = clicks[accent.ordinal]

    private fun render(frequencyHz: Double, peak: Double): FloatArray {
        val attack = (ATTACK_SECONDS * sampleRate).coerceAtLeast(1.0)
        val endFade = (END_FADE_SECONDS * sampleRate).coerceAtLeast(1.0)
        val upperHz = frequencyHz * UPPER_MODE_RATIO
        val withUpper = upperHz < NYQUIST_FRACTION * sampleRate
        val samples = DoubleArray(clickFrames) { n ->
            val t = n.toDouble() / sampleRate
            var s = sin(2 * PI * frequencyHz * t) * exp(-t / DECAY_SECONDS)
            if (withUpper) s += UPPER_MODE_LEVEL * sin(2 * PI * upperHz * t) * exp(-2 * t / DECAY_SECONDS)
            var gain = if (n < attack) raisedCosine(n / attack) else 1.0
            val left = clickFrames - 1 - n
            if (left < endFade) gain *= raisedCosine(left / endFade)
            s * gain
        }
        val loudest = samples.maxOf { abs(it) }
        return FloatArray(clickFrames) { n -> (samples[n] * peak / loudest).toFloat() }
    }

    // 0 → 1 along half a cosine as x goes 0 → 1.
    private fun raisedCosine(x: Double): Double = 0.5 - 0.5 * cos(PI * x)

    companion object {
        /** Length of a click. Far shorter than a beat at the fastest tempo (0.24 s), so clicks never overlap. */
        const val CLICK_SECONDS = 0.04

        /** Raised-cosine fade-in at the start of a click. */
        const val ATTACK_SECONDS = 0.0008

        /** Raised-cosine fade-out at the end of a click. */
        const val END_FADE_SECONDS = 0.008

        /** Time constant of the lower mode's exponential decay. */
        const val DECAY_SECONDS = 0.009

        /** Second bending mode of a free bar over its first (Fletcher & Rossing §2.15). */
        const val UPPER_MODE_RATIO = 2.756

        /** Starting level of the upper mode next to the lower one. */
        const val UPPER_MODE_LEVEL = 0.35

        private const val NYQUIST_FRACTION = 0.45

        /** Pitch of the click for [accent], in Hz. */
        fun frequencyOf(accent: BeatAccent): Double = when (accent) {
            BeatAccent.DOWNBEAT -> 1760.0
            BeatAccent.GROUP -> 1318.51
            BeatAccent.PLAIN -> 880.0
        }

        /** Largest sample of the click for [accent]; the loudest is 0.8 (about −2 dBFS), so it never clips. */
        fun peakOf(accent: BeatAccent): Double = when (accent) {
            BeatAccent.DOWNBEAT -> 0.8
            BeatAccent.GROUP -> 0.6
            BeatAccent.PLAIN -> 0.45
        }
    }
}
