package io.github.klaboukes.twangle.core.dsp.smoothing

import io.github.klaboukes.twangle.core.dsp.music.Note
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Turns the raw per-frame pitch estimates (~47 per second) into a calm reading for the display.
 *
 * Input is a fractional MIDI note number (from `NoteMapper.midiOf`, so A4 calibration is already
 * applied). Each [update] runs five stages:
 * 0. **Onset confirmation.** Without a reading, a new one only starts once [onsetFrames] readings
 *    in a row agree to within [snapCents], each from a frame loud enough to open the signal gate
 *    on its own (`loudEnough`, not merely kept open by its hysteresis). A click or tick that rings
 *    at a pitch gives one or two such frames before `SustainCheck` sees it has died away; a string
 *    gives one every frame. The first reading shown is then already the median of those frames,
 *    rather than the attack's single estimate.
 * 1. **Octave-jump rejection.** A reading a whole number of octaves (±[octaveToleranceCents]) away
 *    from the recent median is dropped. That is the classic detector glitch on decaying or
 *    weak-fundamental notes. If it repeats for [octaveConfirmFrames] frames in a row it's a real
 *    change and is accepted.
 * 2. **Median** of the last [medianSize] readings removes other one-frame outliers.
 * 3. **EMA** with [emaAlpha] averages out remaining jitter. When the median is more than
 *    [snapCents] away (a new string was plucked), it snaps instead of sliding the needle across.
 *    Readings the detector is less sure of (confidence below [CONFIDENT], as in a noisy room or
 *    the quiet end of a note) move it less, down to [MIN_WEIGHT] × [emaAlpha] at [UNSURE].
 * 4. **Note hysteresis.** The displayed [note] only changes once the reading is more than
 *    50 + [hysteresisCents] cents from it, so a note right between two semitones doesn't flicker.
 *    As a result [cents] can reach ±(50 + [hysteresisCents]).
 *
 * Call [noReading] for frames without a pitch. Short dropouts keep the last reading, and more than
 * [maxMissedFrames] in a row clear it, except frames whose sound had died away by their end (a tick
 * that has passed, see [noReading]): they can't start a reading, nor count as a dropout of one.
 * Nothing allocates after construction.
 */
class PitchSmoother(
    val medianSize: Int = DEFAULT_MEDIAN_SIZE,
    val emaAlpha: Float = DEFAULT_EMA_ALPHA,
    val snapCents: Float = DEFAULT_SNAP_CENTS,
    val hysteresisCents: Float = DEFAULT_HYSTERESIS_CENTS,
    val octaveToleranceCents: Float = DEFAULT_OCTAVE_TOLERANCE_CENTS,
    val octaveConfirmFrames: Int = DEFAULT_OCTAVE_CONFIRM_FRAMES,
    val maxMissedFrames: Int = DEFAULT_MAX_MISSED_FRAMES,
    val onsetFrames: Int = DEFAULT_ONSET_FRAMES,
) {

    init {
        require(snapCents > 0f) { "snapCents must be positive, was $snapCents" }
        require(hysteresisCents in 0f..50f) { "hysteresisCents must be in 0..50, was $hysteresisCents" }
        require(octaveToleranceCents in 0f..100f) { "octaveToleranceCents must be in 0..100, was $octaveToleranceCents" }
        require(octaveConfirmFrames >= 1) { "octaveConfirmFrames must be at least 1, was $octaveConfirmFrames" }
        require(maxMissedFrames >= 0) { "maxMissedFrames must not be negative, was $maxMissedFrames" }
        require(onsetFrames in 1..medianSize) { "onsetFrames must be in 1..$medianSize, was $onsetFrames" }
    }

    private val medianFilter = MedianFilter(medianSize)
    private val ema = ExponentialSmoother(emaAlpha)
    private var noteMidi = 0
    private var hasNote = false
    private var pendingOctaveFrames = 0
    private var missedFrames = 0
    private var onsetCount = 0
    private var lastOnsetMidi = 0f

    /** True when there is a reading to show. [midi], [note] and [cents] are only meaningful then. */
    val hasReading: Boolean get() = !ema.value.isNaN()

    /** Smoothed fractional MIDI note number, or NaN without a reading. */
    val midi: Float get() = ema.value

    /** The note to display, with hysteresis applied. */
    val note: Note get() = Note(noteMidi)

    /** Smoothed deviation from [note] in cents. Positive means sharp. */
    val cents: Float get() = CENTS_PER_SEMITONE * (ema.value - noteMidi)

    /**
     * Feeds one pitch estimate as a fractional MIDI note number, with the detector's [confidence]
     * (0..1, see `PitchResult.confidence`). [loudEnough] says whether the frame's level would open
     * the signal gate by itself (`SignalGate.thresholdDbfs`); a frame that isn't can keep a reading
     * going but not start one. The gate's hysteresis is there so a fading note doesn't flicker; on
     * the phone it also kept the gate open for a laptop speaker ringing on after a tick.
     *
     * @return false if it was rejected as an octave glitch or too quiet to start a reading, true if
     * it was used (which, before a reading starts, may only count towards [onsetFrames]).
     */
    fun update(midi: Float, confidence: Float = 1f, loudEnough: Boolean = true): Boolean {
        require(midi.isFinite()) { "midi must be finite, was $midi" }
        missedFrames = 0
        if (!hasReading) {
            if (!loudEnough) {
                medianFilter.reset()
                onsetCount = 0
                return false
            }
            confirmOnset(midi)
            return true
        }
        if (medianFilter.count > 0 && isOctaveJump(midi - medianFilter.median)) {
            pendingOctaveFrames++
            if (pendingOctaveFrames < octaveConfirmFrames) return false
            // The jump persisted, so the player really did move by an octave: start over there.
            medianFilter.reset()
            ema.reset()
            hasNote = false
        }
        pendingOctaveFrames = 0

        val median = medianFilter.add(midi)
        val current = ema.value
        if (current.isNaN() || CENTS_PER_SEMITONE * abs(median - current) > snapCents) {
            ema.snapTo(median)
        } else {
            ema.update(median, weightOf(confidence))
        }
        updateNote(ema.value)
        return true
    }

    /**
     * Records a frame with no pitch: gate closed, detector found nothing, or, when not [sustained],
     * not analysed because its sound had died away by its end (`SustainCheck`). Before a reading
     * starts, any such frame starts the onset over. While one is shown, a frame that isn't sustained
     * doesn't count as a dropout: a tick louder than the string is in its window, hiding the string
     * until it leaves the window a few frames later.
     */
    fun noReading(sustained: Boolean = true) {
        if (!hasReading) {
            medianFilter.reset()
            onsetCount = 0
            return
        }
        if (!sustained) return
        missedFrames++
        if (missedFrames > maxMissedFrames) reset()
    }

    /** Clears everything, e.g. when the tuner screen restarts listening. */
    fun reset() {
        medianFilter.reset()
        ema.reset()
        hasNote = false
        pendingOctaveFrames = 0
        missedFrames = 0
        onsetCount = 0
    }

    // Stage 0: collects agreeing readings in the median filter and starts the reading once there
    // are onsetFrames of them in a row. One that disagrees with the last starts the count afresh.
    private fun confirmOnset(midi: Float) {
        if (onsetCount > 0 && CENTS_PER_SEMITONE * abs(midi - lastOnsetMidi) > snapCents) {
            medianFilter.reset()
            onsetCount = 0
        }
        lastOnsetMidi = midi
        val median = medianFilter.add(midi)
        if (++onsetCount < onsetFrames) return
        onsetCount = 0
        pendingOctaveFrames = 0
        ema.snapTo(median)
        updateNote(median)
    }

    // 1 at CONFIDENT and above, falling linearly to MIN_WEIGHT at UNSURE and below. A clean tone
    // has confidence ≈ 1; white noise 10 dB below the tone ≈ 0.97, at the same level ≈ 0.86.
    private fun weightOf(confidence: Float): Float =
        ((confidence - UNSURE) / (CONFIDENT - UNSURE)).coerceIn(MIN_WEIGHT, 1f)

    private fun isOctaveJump(semitones: Float): Boolean {
        val octaves = (semitones / SEMITONES_PER_OCTAVE).roundToInt()
        if (octaves == 0) return false
        return CENTS_PER_SEMITONE * abs(semitones - octaves * SEMITONES_PER_OCTAVE) <= octaveToleranceCents
    }

    private fun updateNote(smoothed: Float) {
        // ceil(x − 0.5) is the same "half-way reads +50 cents" rounding as NoteMapper.
        if (!hasNote || abs(smoothed - noteMidi) > 0.5f + hysteresisCents / CENTS_PER_SEMITONE) {
            noteMidi = ceil(smoothed - 0.5f).toInt()
            hasNote = true
        }
    }

    companion object {
        /** 5 frames ≈ 107 ms at 47 frames/s. */
        const val DEFAULT_MEDIAN_SIZE = 5
        const val DEFAULT_EMA_ALPHA = 0.3f
        const val DEFAULT_SNAP_CENTS = 50f
        const val DEFAULT_HYSTERESIS_CENTS = 15f
        const val DEFAULT_OCTAVE_TOLERANCE_CENTS = 30f

        /** 3 frames ≈ 64 ms: long enough to outlast a glitch, short enough to feel instant. */
        const val DEFAULT_OCTAVE_CONFIRM_FRAMES = 3

        /** 4 frames ≈ 85 ms of dropout before the reading is cleared. */
        const val DEFAULT_MAX_MISSED_FRAMES = 4

        /**
         * 3 frames: a new note shows ≈ 43 ms later than its first estimate. A tick gives at most 2
         * agreeing frames once `SustainCheck` has dropped the ones after it has died away.
         */
        const val DEFAULT_ONSET_FRAMES = 3

        /** Confidence from which a reading gets the full EMA step. */
        const val CONFIDENT = 0.97f

        /** Confidence at and below which a reading gets only [MIN_WEIGHT] of the step. */
        const val UNSURE = 0.88f

        const val MIN_WEIGHT = 0.2f

        private const val CENTS_PER_SEMITONE = 100f
        private const val SEMITONES_PER_OCTAVE = 12f
    }
}
