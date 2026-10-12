package io.github.klaboukes.twangle.core.dsp.pitch

import kotlin.math.log10

/**
 * How clean a pitch is, from YIN's confidence: 1 for a clear tone, falling towards 0 as noise
 * makes the tuner work harder, 0 at the edge of what YIN accepts and for frames without a pitch.
 *
 * YIN's confidence is `1 − d′(τ)`, and the dip `d′(τ)` is the share of the frame's power that
 * doesn't repeat with the period, which the YIN paper offers as an aperiodicity measure
 * (de Cheveigné & Kawahara 2002). So
 * `c / (1 − c)` is the ratio of periodic to aperiodic power, in dB the same form as Boersma's
 * harmonics-to-noise ratio (P. Boersma (1993), *Accurate short-term analysis of the fundamental
 * frequency and the harmonics-to-noise ratio of a sampled sound*, IFA Proceedings 17).
 * Measured on synthetic notes with white noise it is the same for every pitch and timbre, and
 * about 7.8 dB above the broadband SNR, because YIN's 4 kHz low-pass removes most of the hiss.
 *
 * [of] maps that ratio linearly from [UNCLEAR_DB] (`d′` = 0.15, YIN's threshold) to [CLEAR_DB]
 * (`d′` = 0.01). Measured (48 kHz, white noise): 20 dB SNR or cleaner → 1, 10 dB → 0.8,
 * 6 dB → 0.5, 3 dB → 0.25, 0 dB → about 0.
 */
object Clarity {

    /** Periodic-to-aperiodic power ratio at YIN's default threshold (`d′` = 0.15): clarity 0. */
    val UNCLEAR_DB: Float = periodicityDbOf(1f - YinPitchDetector.DEFAULT_THRESHOLD)

    /** The ratio at `d′` = 0.01, a tone 99 % periodic: clarity 1. */
    val CLEAR_DB: Float = periodicityDbOf(1f - CLEAR_APERIODICITY)

    /** 0..1 clarity for a [confidence] in 0..1 (0 for no pitch). */
    fun of(confidence: Float): Float {
        if (confidence <= 0f) return 0f
        if (confidence >= 1f) return 1f
        return ((periodicityDbOf(confidence) - UNCLEAR_DB) / (CLEAR_DB - UNCLEAR_DB)).coerceIn(0f, 1f)
    }

    /** Periodic-to-aperiodic power ratio in dB, `10 · log10(c / (1 − c))`, for a confidence in (0, 1). */
    fun periodicityDbOf(confidence: Float): Float = (10.0 * log10(confidence / (1.0 - confidence))).toFloat()

    private const val CLEAR_APERIODICITY = 0.01f
}
