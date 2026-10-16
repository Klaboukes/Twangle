package io.github.klaboukes.twangle.core.dsp.intonation

import kotlin.math.abs
import kotlin.math.ceil

/**
 * One settled pitch from the tuner's smoothed readings, for a step of the intonation check (the open
 * string, then the 12th fret): it waits until the note is steady, then takes the median of what the
 * tuner reads for about a second, so the pluck's attack and the dying end don't count
 * (docs/architecture/notes-and-audio.md, *Intonation check*).
 *
 * Feed it one reading per frame with [update]: the smoothed pitch as a fractional MIDI note, or NaN
 * for a frame without one. A note is steady once [settleSeconds] of readings in a row each stay within
 * [steadyCents] of the one before (the attack's glide is over); then [collectSeconds] of readings are
 * kept, each within [steadyCents] of the first one kept. A jump further than that, or more than
 * [gapSeconds] without a reading, starts over. Once enough are kept, [midi] is their median and
 * [done] is true until [reset].
 *
 * [frameSeconds] is the time between readings. Everything is allocated here, nothing per frame
 * (CLAUDE.md, hard rule 2). Not thread-safe.
 */
class SettledPitch(
    frameSeconds: Float,
    val settleSeconds: Float = DEFAULT_SETTLE_SECONDS,
    val collectSeconds: Float = DEFAULT_COLLECT_SECONDS,
    val steadyCents: Float = DEFAULT_STEADY_CENTS,
    val gapSeconds: Float = DEFAULT_GAP_SECONDS,
) {
    init {
        require(frameSeconds > 0f && frameSeconds.isFinite()) { "frameSeconds must be positive, was $frameSeconds" }
        require(settleSeconds >= 0f) { "settleSeconds must not be negative, was $settleSeconds" }
        require(collectSeconds > 0f) { "collectSeconds must be positive, was $collectSeconds" }
        require(steadyCents > 0f) { "steadyCents must be positive, was $steadyCents" }
        require(gapSeconds >= 0f) { "gapSeconds must not be negative, was $gapSeconds" }
    }

    private val settleFrames = ceil(settleSeconds / frameSeconds).toInt()
    private val gapFrames = ceil(gapSeconds / frameSeconds).toInt()

    /** How many readings make up the median. */
    val collectFrames: Int = ceil(collectSeconds / frameSeconds).toInt().coerceAtLeast(1)

    private val kept = FloatArray(collectFrames)
    private var keptCount = 0
    private var steadyFrames = 0
    private var missedFrames = 0
    private var previous = Float.NaN

    /** The settled pitch, the median of the readings kept, as a fractional MIDI note; NaN until [done]. */
    var midi: Float = Float.NaN
        private set

    /** Whether a settled pitch has been taken; it stays until [reset]. */
    val done: Boolean get() = !midi.isNaN()

    /** How far the collection has got, 0..1: 0 while the note settles, 1 once [done]. */
    val progress: Float get() = if (done) 1f else keptCount.toFloat() / collectFrames

    /** Feeds one frame's smoothed reading [midi] (NaN for none) and returns [done]. */
    fun update(midi: Float): Boolean {
        if (done) return true
        if (midi.isNaN()) {
            if (++missedFrames > gapFrames) startOver()
            return false
        }
        missedFrames = 0
        val first = if (keptCount > 0) kept[0] else previous
        previous = midi
        if (first.isNaN() || cents(midi - first) > steadyCents) {
            // A new note, or one still gliding: it has to settle afresh.
            keptCount = 0
            steadyFrames = 0
            return false
        }
        if (steadyFrames < settleFrames) {
            steadyFrames++
            return false
        }
        kept[keptCount++] = midi
        if (keptCount == collectFrames) this.midi = median()
        return done
    }

    /** Forgets everything, for the next step. */
    fun reset() {
        startOver()
        midi = Float.NaN
    }

    private fun startOver() {
        keptCount = 0
        steadyFrames = 0
        missedFrames = 0
        previous = Float.NaN
    }

    /** Sorts the kept readings in place (insertion sort: one second's worth, once per step). */
    private fun median(): Float {
        for (i in 1 until keptCount) {
            val v = kept[i]
            var j = i - 1
            while (j >= 0 && kept[j] > v) {
                kept[j + 1] = kept[j]
                j--
            }
            kept[j + 1] = v
        }
        val mid = keptCount / 2
        return if (keptCount % 2 == 1) kept[mid] else (kept[mid - 1] + kept[mid]) / 2f
    }

    private fun cents(semitones: Float): Float = abs(semitones) * 100f

    companion object {
        /** The pluck's attack glides for a moment; a quarter of a second of steady readings is past it. */
        const val DEFAULT_SETTLE_SECONDS = 0.25f

        /** About a second of readings, as the dsp comparison was measured with. */
        const val DEFAULT_COLLECT_SECONDS = 1f

        /** A smoothed reading of one held note stays well within this; another note or a slide doesn't. */
        const val DEFAULT_STEADY_CENTS = 20f

        /** A dropout this short is the smoother's business; longer, the note has stopped. */
        const val DEFAULT_GAP_SECONDS = 0.15f
    }
}
