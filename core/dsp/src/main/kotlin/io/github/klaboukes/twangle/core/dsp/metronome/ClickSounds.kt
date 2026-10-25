package io.github.klaboukes.twangle.core.dsp.metronome

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The metronome's clicks: for every [ClickSound], one click per [BeatAccent], each rendered once at
 * [sampleRate] so playing one is only a copy.
 *
 * **The wood block** is a short "tock": a struck free bar, whose first two bending modes ring at
 * frequencies 1 : 2.76 (Fletcher & Rossing, *The Physics of Musical Instruments*, 2nd ed. 1998,
 * §2.15). Both decay exponentially, the upper mode twice as fast, so the click starts bright and
 * ends round. Envelope: a [ATTACK_SECONDS] raised-cosine attack (no hard edge to crackle) and a
 * raised-cosine fade over the last [END_FADE_SECONDS], ending on exactly 0 after [CLICK_SECONDS].
 * The upper mode is left out where it would pass 0.45 × the sample rate, so nothing aliases.
 * The accent is told apart by pitch and level: the downbeat at A6 (1760 Hz) and the loudest, a group
 * accent at E6 (1318.5 Hz), the other beats at A5 (880 Hz) and the quietest.
 *
 * **The cowbell, claves and rim click** are struck by [PercussionSynth] from [ClickRecipes]: a
 * contact pulse, a few hard-damped modes and a strike noise, 80–180 ms long. Beat 1 is struck harder
 * and rings higher (an octave, the claves a fifth), a group accent halfway. They are struck at
 * [STRUCK_RATE] and, at any other rate, resampled from it ([Resampler]). Each is then scaled so a
 * bar of 4/4 (beat 1 and three plain beats) carries the wood block's energy, as loud by RMS, unless
 * one of its three clicks would then pass [MAX_PEAK]: then the loudest peaks at [MAX_PEAK].
 *
 * Every click is shorter than a beat at the fastest tempo (0.24 s), so one never runs into the next.
 * Memory per sample rate: the wood block's three clicks (23 KB at 48 kHz) at once, each other sound
 * the first time it is asked for (cowbell 104 KB, claves 69 KB, rim click 46 KB at 48 kHz); all
 * four together hold 60,480 floats at 48 kHz, 242 KB.
 *
 * Not thread-safe: one thread (the audio writer) uses it, as [ClickTrack] does.
 */
class ClickSounds(val sampleRate: Int) {

    init {
        require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }
    }

    // clicks[sound][accent]; a struck sound is rendered when it is first asked for.
    private val clicks = arrayOfNulls<Array<FloatArray>>(ClickSound.entries.size)

    // The wood block, always: the default, and the struck sounds' loudness is matched to it.
    private val woodBlock = Array(BeatAccent.entries.size) { i ->
        val accent = BeatAccent.entries[i]
        renderWoodBlock(frequencyOf(accent), peakOf(accent))
    }.also { clicks[ClickSound.WOOD_BLOCK.ordinal] = it }

    /**
     * The click of [sound] for [accent]. Shared and read-only: callers must not change it. The
     * first call for a sound other than the wood block renders its three clicks (a few
     * milliseconds, see [prepare]); later calls only look them up and allocate nothing.
     */
    fun waveform(sound: ClickSound, accent: BeatAccent): FloatArray = clicksOf(sound)[accent.ordinal]

    /** The wood block's click for [accent]. Shared and read-only: callers must not change it. */
    fun waveform(accent: BeatAccent): FloatArray = woodBlock[accent.ordinal]

    /** Renders [sound]'s clicks now if they aren't yet, so the first beat that plays it only copies. */
    fun prepare(sound: ClickSound) {
        clicksOf(sound)
    }

    /** Length of [sound]'s clicks, in samples (the same for every accent). */
    fun frames(sound: ClickSound): Int = clicksOf(sound)[0].size

    private fun clicksOf(sound: ClickSound): Array<FloatArray> =
        clicks[sound.ordinal] ?: struck(checkNotNull(ClickRecipes.of(sound)), woodBlock).also { clicks[sound.ordinal] = it }

    private fun renderWoodBlock(frequencyHz: Double, peak: Double): FloatArray {
        val clickFrames = (CLICK_SECONDS * sampleRate).roundToInt().coerceAtLeast(1)
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

    // The recipe's three hits, scaled to the wood block's loudness (see the class comment).
    private fun struck(recipe: PercussionSynth.Recipe, wood: Array<FloatArray>): Array<FloatArray> {
        val raw = Array(BeatAccent.entries.size) { a ->
            val hit = PercussionSynth.hit(recipe, BeatAccent.entries[a], STRUCK_RATE)
            if (sampleRate == STRUCK_RATE) hit else resampled(hit)
        }
        var woodEnergy = 0.0
        var energy = 0.0
        for (beat in 0 until LEVEL_METER.beatsPerBar) {
            val accent = LEVEL_METER.accentOf(beat).ordinal
            for (x in wood[accent]) woodEnergy += x.toDouble() * x
            for (x in raw[accent]) energy += x * x
        }
        val peak = raw.maxOf { hit -> hit.maxOf { abs(it) } }
        val gain = minOf(sqrt(woodEnergy / energy), MAX_PEAK / peak)
        return Array(raw.size) { a -> FloatArray(raw[a].size) { n -> (raw[a][n] * gain).toFloat() } }
    }

    // A hit made at STRUCK_RATE, at this rate. The windowed sinc rings a little at both ends, so
    // short raised-cosine fades put the first and last samples back on exactly 0.
    private fun resampled(hit: DoubleArray): DoubleArray {
        val out = Resampler.resample(hit, STRUCK_RATE, sampleRate)
        val fadeIn = (RESAMPLED_FADE_IN_SECONDS * sampleRate).coerceAtLeast(1.0)
        val fadeOut = (RESAMPLED_FADE_OUT_SECONDS * sampleRate).coerceAtLeast(1.0)
        for (n in out.indices) {
            if (n < fadeIn) out[n] *= raisedCosine(n / fadeIn)
            val left = out.size - 1 - n
            if (left < fadeOut) out[n] *= raisedCosine(left / fadeOut)
        }
        return out
    }

    // 0 → 1 along half a cosine as x goes 0 → 1.
    private fun raisedCosine(x: Double): Double = 0.5 - 0.5 * cos(PI * x)

    companion object {
        /** Length of a wood-block click. */
        const val CLICK_SECONDS = 0.04

        /** Raised-cosine fade-in at the start of a wood-block click. */
        const val ATTACK_SECONDS = 0.0008

        /** Raised-cosine fade-out at the end of a wood-block click. */
        const val END_FADE_SECONDS = 0.008

        /** Time constant of the wood block's lower mode's exponential decay. */
        const val DECAY_SECONDS = 0.009

        /** Second bending mode of a free bar over its first (Fletcher & Rossing §2.15). */
        const val UPPER_MODE_RATIO = 2.756

        /** Starting level of the wood block's upper mode next to the lower one. */
        const val UPPER_MODE_LEVEL = 0.35

        /** The loudest any click gets (about −2 dBFS), so none clips. */
        const val MAX_PEAK = 0.8f

        private const val NYQUIST_FRACTION = 0.45

        /**
         * The rate the cowbell, claves and rim click are struck at, the approved samples' own: at
         * any other rate they are resampled from it, so they sound the same everywhere (their
         * strike noise, drawn one value per sample, would otherwise come out different).
         */
        const val STRUCK_RATE = 48_000

        private const val RESAMPLED_FADE_IN_SECONDS = 0.0001
        private const val RESAMPLED_FADE_OUT_SECONDS = 0.001

        // The bar whose loudness the struck clicks match to the wood block's.
        private val LEVEL_METER = Meter.FOUR_FOUR

        /** Pitch of the wood block's click for [accent], in Hz. */
        fun frequencyOf(accent: BeatAccent): Double = when (accent) {
            BeatAccent.DOWNBEAT -> 1760.0
            BeatAccent.GROUP -> 1318.51
            BeatAccent.PLAIN -> 880.0
        }

        /** Largest sample of the wood block's click for [accent]; the loudest is [MAX_PEAK]. */
        fun peakOf(accent: BeatAccent): Double = when (accent) {
            BeatAccent.DOWNBEAT -> 0.8
            BeatAccent.GROUP -> 0.6
            BeatAccent.PLAIN -> 0.45
        }
    }
}
