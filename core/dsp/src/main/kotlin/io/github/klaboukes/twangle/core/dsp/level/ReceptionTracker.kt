package io.github.klaboukes.twangle.core.dsp.level

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** What keeps the [ReceptionTracker]'s figure down, if anything. */
enum class ReceptionLimit {
    /** Reception is good: every factor is at least [ReceptionTracker.CLEAR_RECEPTION]. */
    NONE,

    /** The note is quiet: play louder or bring the phone closer. */
    LEVEL,

    /** The note rises too little above the room's background (low SNR). */
    NOISE,

    /** The pitch itself is unclear: YIN's confidence is low (noise, or several notes at once). */
    CLARITY,
}

/**
 * How well the tuner hears the note, as one steady 0..1 figure ([reception]): 1 for a strong,
 * clean note, falling towards 0 as it gets quiet ([level]), sinks into the room's background
 * ([noise], from the SNR) or loses its clear period ([clarity]). The display draws the needle and
 * the wave crisp at 1 and faint and soft towards 0.
 *
 * Each factor is scored 0..1 per frame ([levelScore], [snrScore], and the clarity as it is), then
 * smoothed on its own with a one-pole filter that moves fast towards better (time constant
 * [RISE_SECONDS]) and slowly towards worse ([FALL_SECONDS]), so one or two rough frames don't dim
 * the display and per-frame jitter never makes it flicker. The figure is the lowest of the three, so
 * [limit] can say which one holds it down (for tips such as "Play a bit louder").
 *
 * Feed it only frames with a pitch ([update]); frames without one leave it as it is. [reset] when
 * the note is gone: the next note's first frame then sets every factor at once, instead of easing
 * from the last note's values. For the note's first [ONSET_SECONDS] every factor rises at once,
 * because those analysis windows still hold the silence before the note and its attack; otherwise a
 * strong note would start faint and take a moment to sharpen. Counted in frames of [frameSeconds], so tests need no clock.
 * Nothing allocates after construction.
 */
class ReceptionTracker(frameSeconds: Float) {

    init {
        require(frameSeconds > 0f) { "frameSeconds must be positive, was $frameSeconds" }
    }

    // One-pole smoothing weights per frame: 1 − e^(−Δt / τ).
    private val riseWeight = 1f - exp(-frameSeconds / RISE_SECONDS)
    private val fallWeight = 1f - exp(-frameSeconds / FALL_SECONDS)
    private val onsetFrames = max(1, ceil(ONSET_SECONDS / frameSeconds).toInt())
    private var framesHeard = 0

    /** Smoothed loudness score, 0..1 ([levelScore]). */
    var level: Float = 1f
        private set

    /** Smoothed SNR score, 0..1 ([snrScore]); 1 while the room's background isn't known yet. */
    var noise: Float = 1f
        private set

    /** Smoothed clarity, 0..1 (`Clarity.of` YIN's confidence). */
    var clarity: Float = 1f
        private set

    /** Smoothed level of the note in dBFS, for e.g. a "too loud" tip; NaN before the first note. */
    var levelDbfs: Float = Float.NaN
        private set

    /** How well the note is heard, 0..1: the lowest of [level], [noise] and [clarity]. 1 before any note. */
    var reception: Float = 1f
        private set

    /** Which factor holds [reception] down, or [ReceptionLimit.NONE] when it is at least [CLEAR_RECEPTION]. */
    var limit: ReceptionLimit = ReceptionLimit.NONE
        private set

    /** Whether a note has been heard since the last [reset]. */
    var hasNote: Boolean = false
        private set

    /**
     * Takes one frame with a pitch: its [levelDbfs], its [snrDb] over the room (NaN while the room is
     * unknown, which counts as no limit) and its [clarity] (0..1).
     */
    fun update(levelDbfs: Float, snrDb: Float, clarity: Float) {
        val levelNow = levelScore(levelDbfs)
        val noiseNow = if (snrDb.isNaN()) 1f else snrScore(snrDb)
        val clarityNow = clarity.coerceIn(0f, 1f)
        if (framesHeard <= onsetFrames) framesHeard++
        if (!hasNote) {
            hasNote = true
            level = levelNow
            noise = noiseNow
            this.clarity = clarityNow
            this.levelDbfs = levelDbfs
        } else {
            level = smooth(level, levelNow)
            noise = smooth(noise, noiseNow)
            this.clarity = smooth(this.clarity, clarityNow)
            // Like a peak meter: a louder note shows fast, a fade slowly.
            this.levelDbfs = smooth(this.levelDbfs, levelDbfs)
        }
        reception = min(level, min(noise, this.clarity))
        limit = when {
            reception >= CLEAR_RECEPTION -> ReceptionLimit.NONE
            reception == level -> ReceptionLimit.LEVEL
            reception == noise -> ReceptionLimit.NOISE
            else -> ReceptionLimit.CLARITY
        }
    }

    /** Forgets the note: back to full reception, and the next note starts afresh. */
    fun reset() {
        hasNote = false
        framesHeard = 0
        level = 1f
        noise = 1f
        clarity = 1f
        levelDbfs = Float.NaN
        reception = 1f
        limit = ReceptionLimit.NONE
    }

    private fun smooth(current: Float, target: Float): Float = when {
        target <= current -> current + (target - current) * fallWeight
        framesHeard <= onsetFrames -> target
        else -> current + (target - current) * riseWeight
    }

    companion object {
        /** Getting better shows within about this (time constant): a note that comes through clearly is crisp at once. */
        const val RISE_SECONDS = 0.15f

        /** Getting worse shows slowly, so a rough frame or two doesn't dim the display. */
        const val FALL_SECONDS = 0.5f

        /** A note's first frames, while the analysis window fills, rise to their level at once (≈ the 85 ms window). */
        const val ONSET_SECONDS = 0.1f

        /** Reception from here up counts as good: no factor is named in [limit]. */
        const val CLEAR_RECEPTION = 0.95f

        /**
         * Level score 0 here: where the signal gate opens (`SignalGate.DEFAULT_THRESHOLD_DBFS`), so
         * a quieter note is about to be lost.
         */
        const val SOFT_DBFS = -50f

        /** Level score 1 from here up: about 20 dB above a quiet phone's own background (−52…−56 dBFS on the S26). */
        const val STRONG_DBFS = -35f

        /** SNR score 0 here: the note no louder than the room (the shown reading starts to wander, see `EdgeCaseTest`). */
        const val NOISY_SNR_DB = 0f

        /** SNR score 1 from here up: within a cent through the whole chain (as for clarity 1). */
        const val CLEAN_SNR_DB = 20f

        /** 0..1 loudness score for a note at [levelDbfs]: 0 at [SOFT_DBFS], 1 from [STRONG_DBFS], linear in dB. */
        fun levelScore(levelDbfs: Float): Float = ((levelDbfs - SOFT_DBFS) / (STRONG_DBFS - SOFT_DBFS)).coerceIn(0f, 1f)

        /** 0..1 noise score for a note [snrDb] above the room: 0 at [NOISY_SNR_DB], 1 from [CLEAN_SNR_DB], linear in dB. */
        fun snrScore(snrDb: Float): Float = ((snrDb - NOISY_SNR_DB) / (CLEAN_SNR_DB - NOISY_SNR_DB)).coerceIn(0f, 1f)
    }
}
