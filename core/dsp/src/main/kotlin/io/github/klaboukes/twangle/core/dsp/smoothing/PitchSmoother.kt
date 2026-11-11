package io.github.klaboukes.twangle.core.dsp.smoothing

import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
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
 * 1. **Octave-jump rejection.** A reading a whole number of periods from the recent median, an
 *    octave or more or a twelfth, up or down (±[octaveToleranceCents]), is dropped. That is the
 *    classic detector glitch on decaying or weak-fundamental notes, and what a string reads as
 *    while it sinks into a noisy room: for a few tenths of a second the string and the room (a TV's
 *    voice, say) repeat together at a multiple of the string's period, until the detector loses the
 *    string. A new note comes with an attack, so the jump is followed after [octaveConfirmFrames]
 *    frames loud enough to start a reading (`loudEnough`) when one of those frames rose [ATTACK_DB]
 *    over the quietest of the [ATTACK_LOOKBACK_FRAMES] frames before it (`levelDbfs`; a muted
 *    string before the 12th-fret harmonic makes the rise bigger, not smaller); without an attack
 *    only once it has lasted [octaveHoldFrames], about a second, by when it is a sound of its own.
 *    In guitar mode ([strings] set) a jump that lands on another string of the tuning needs only a
 *    rise of [STRING_CHANGE_DB] in [STRING_CHANGE_FRAMES] of its frames, and restarts on a frame on
 *    that string: the next string plucked over the last one still ringing, which rises less than a
 *    pluck in a quiet room (low E → high E two octaves up, low E → B and A → high E a twelfth up, in
 *    standard tuning). One such frame was not enough: in a noisy room a G string re-plucked over
 *    itself read G2, a string of Open G, in a frame that rose 7 dB, and the needle went to G2. A
 *    jump of exactly one octave up needs the full attack, also onto a string (the high D of Drop D
 *    and Open G over the low one): that is where the detector slips most, a low string read an
 *    octave up. One octave down, the lower string plucked over the ringing higher one, it is read at
 *    once and stays a string change.
 *    Until then the reading stays, so the needle holds the note as it sinks and then goes blank.
 *    In frames too quiet to start a reading the jump is never followed: that is the fundamental
 *    dying away ahead of the 2nd partial. Once the needle has gone blank, the same readings can't
 *    start a reading either for [octaveHoldFrames], unless an attack came in between: what is left
 *    of the note in the room is not a new note.
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
    val octaveHoldFrames: Int = DEFAULT_OCTAVE_HOLD_FRAMES,
    val maxMissedFrames: Int = DEFAULT_MAX_MISSED_FRAMES,
    val onsetFrames: Int = DEFAULT_ONSET_FRAMES,
) {

    init {
        require(snapCents > 0f) { "snapCents must be positive, was $snapCents" }
        require(hysteresisCents in 0f..50f) { "hysteresisCents must be in 0..50, was $hysteresisCents" }
        require(octaveToleranceCents in 0f..100f) { "octaveToleranceCents must be in 0..100, was $octaveToleranceCents" }
        require(octaveConfirmFrames >= 1) { "octaveConfirmFrames must be at least 1, was $octaveConfirmFrames" }
        require(octaveHoldFrames >= octaveConfirmFrames) { "octaveHoldFrames must be at least octaveConfirmFrames, was $octaveHoldFrames" }
        require(maxMissedFrames >= 0) { "maxMissedFrames must not be negative, was $maxMissedFrames" }
        require(onsetFrames in 1..medianSize) { "onsetFrames must be in 1..$medianSize, was $onsetFrames" }
    }

    private val medianFilter = MedianFilter(medianSize)
    private val ema = ExponentialSmoother(emaAlpha)
    private var noteMidi = 0
    private var hasNote = false
    private var pendingOctaveFrames = 0
    private var pendingAttack = false

    // The string of the tuning the pending jump lands on (an index into [strings], or NO_STRING), and
    // how many of its frames rose STRING_CHANGE_DB: a string change needs STRING_CHANGE_FRAMES of them.
    private var pendingString = NO_STRING
    private var pendingStringRises = 0
    private var missedFrames = 0

    // The reading last lost to dropouts, how many frames ago, and whether an attack came since: what
    // is left of it in the room can't start a reading at a multiple of its period for a while.
    private var lostMidi = Float.NaN
    private var lostFrames = 0
    private var attackSinceLost = false

    // Levels of the last frames (dBFS), as a ring, to see a new note's attack.
    private val recentLevels = FloatArray(ATTACK_LOOKBACK_FRAMES)
    private var recentLevelCount = 0
    private var recentLevelNext = 0
    private var onsetCount = 0
    private var lastOnsetMidi = 0f

    /**
     * The tuning whose strings a jump of a whole number of periods may land on as a string change
     * (stage 1), in guitar mode; null in any-note mode or with a string locked, where every such jump
     * needs a full attack. Setting it allocates nothing and keeps the reading.
     */
    var strings: Tuning? = null

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
     * [levelDbfs] is the frame's level, which tells a new note's attack from a note sinking into the
     * room (stage 1); NaN when the caller has none, and then an octave is followed as if it came
     * with an attack.
     *
     * @return false if it was rejected as an octave glitch or too quiet to start a reading, true if
     * it was used (which, before a reading starts, may only count towards [onsetFrames]).
     */
    fun update(midi: Float, confidence: Float = 1f, loudEnough: Boolean = true, levelDbfs: Float = Float.NaN): Boolean {
        require(midi.isFinite()) { "midi must be finite, was $midi" }
        val rise = riseDb(levelDbfs)
        val attack = rise >= ATTACK_DB
        if (attack) attackSinceLost = true
        rememberLevel(levelDbfs)
        missedFrames = 0
        if (!hasReading) {
            if (lostFrames < octaveHoldFrames) lostFrames++
            if (!loudEnough || isRemainsOfLostNote(midi)) {
                medianFilter.reset()
                onsetCount = 0
                return false
            }
            confirmOnset(midi)
            return true
        }
        if (medianFilter.count > 0 && isPeriodMultipleJump(midi - medianFilter.median)) {
            // A frame too quiet to start a reading can't move it an octave either: that is the
            // fundamental dying into the room's noise ahead of the 2nd partial, as a picked high E
            // does about 2 s in (Phase 46's recordings), not a new note, which comes with an attack.
            if (!loudEnough) return false
            pendingOctaveFrames++
            if (attack) pendingAttack = true
            val string = stringChangeTarget(midi, medianFilter.median)
            if (string != NO_STRING && rise >= STRING_CHANGE_DB) {
                if (string != pendingString) pendingStringRises = 0
                pendingString = string
                pendingStringRises++
            }
            // A string change (guitar mode) restarts only on a frame that is itself on the new string,
            // so a stray reading at another multiple can't complete it.
            val stringChange = pendingStringRises >= STRING_CHANGE_FRAMES && string == pendingString
            // Without an attack it is the note sinking into the room, read at a multiple of its
            // period (the same recordings, a TV on: G2 for the G string, A2 for the high E), unless
            // it goes on for about a second.
            if (pendingOctaveFrames < (if (pendingAttack || stringChange) octaveConfirmFrames else octaveHoldFrames)) return false
            // The jump persisted, so the player really did move by an octave: start over there.
            medianFilter.reset()
            ema.reset()
            hasNote = false
        }
        clearPendingJump()

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
     * until it leaves the window a few frames later. [levelDbfs] as in [update]: a muted string's
     * quiet frames are what the next note's attack rises over.
     */
    fun noReading(sustained: Boolean = true, levelDbfs: Float = Float.NaN) {
        if (riseDb(levelDbfs) >= ATTACK_DB) attackSinceLost = true
        rememberLevel(levelDbfs)
        if (!hasReading) {
            if (lostFrames < octaveHoldFrames) lostFrames++
            medianFilter.reset()
            onsetCount = 0
            return
        }
        if (!sustained) return
        missedFrames++
        if (missedFrames > maxMissedFrames) lose()
    }

    /** Clears everything, e.g. when the tuner screen restarts listening. */
    fun reset() {
        clearReading()
        recentLevelCount = 0
        recentLevelNext = 0
        lostMidi = Float.NaN
        lostFrames = 0
        attackSinceLost = false
    }

    // The reading is gone (too many frames without a pitch): clear it, remembering what it was.
    // The recent levels stay, so the next frames are judged for an attack against the frames before.
    private fun lose() {
        val lost = ema.value
        clearReading()
        lostMidi = lost
        lostFrames = 0
        attackSinceLost = false
    }

    private fun clearReading() {
        medianFilter.reset()
        ema.reset()
        hasNote = false
        clearPendingJump()
        missedFrames = 0
        onsetCount = 0
    }

    private fun clearPendingJump() {
        pendingOctaveFrames = 0
        pendingAttack = false
        pendingString = NO_STRING
        pendingStringRises = 0
    }

    // True for a reading a whole number of periods from the note lost less than octaveHoldFrames
    // ago, with no attack since: what is left of that note in the room (its 2nd partial, or the
    // period it shares with the room's talk), not a new note. Never after a frame without a level.
    private fun isRemainsOfLostNote(midi: Float): Boolean =
        !lostMidi.isNaN() && lostFrames < octaveHoldFrames && !attackSinceLost && isPeriodMultipleJump(midi - lostMidi)

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
        clearPendingJump()
        lostMidi = Float.NaN
        ema.snapTo(median)
        updateNote(median)
    }

    // 1 at CONFIDENT and above, falling linearly to MIN_WEIGHT at UNSURE and below. A clean tone
    // has confidence ≈ 1; white noise 10 dB below the tone ≈ 0.97, at the same level ≈ 0.86.
    // A confidence that isn't a number (a detector that couldn't say) is the least sure: coerceIn
    // would pass NaN on, and the needle would blank for a frame.
    private fun weightOf(confidence: Float): Float =
        if (confidence.isNaN()) MIN_WEIGHT else ((confidence - UNSURE) / (CONFIDENT - UNSURE)).coerceIn(MIN_WEIGHT, 1f)

    // A whole number of octaves or a twelfth (three periods), either way: the periods the detector's
    // whole-period checks can land on (2τ or 3τ, τ/k) when a sound nearly repeats there.
    private fun isPeriodMultipleJump(semitones: Float): Boolean {
        val octaves = (semitones / SEMITONES_PER_OCTAVE).roundToInt()
        if (octaves != 0 && CENTS_PER_SEMITONE * abs(semitones - octaves * SEMITONES_PER_OCTAVE) <= octaveToleranceCents) return true
        return CENTS_PER_SEMITONE * abs(abs(semitones) - TWELFTH_SEMITONES) <= octaveToleranceCents
    }

    // How far [levelDbfs] rose over the quietest of the last frames: ATTACK_DB or more is a new
    // note's attack (a pluck rises 15–40 dB within a frame or two; a sinking string falls, a TV's
    // talk wobbles a few dB). Without a level, or before any, an attack is assumed (infinite).
    private fun riseDb(levelDbfs: Float): Float {
        if (levelDbfs.isNaN() || recentLevelCount == 0) return Float.POSITIVE_INFINITY
        var lowest = Float.MAX_VALUE
        for (i in 0 until recentLevelCount) lowest = min(lowest, recentLevels[i])
        return levelDbfs - lowest
    }

    // In guitar mode, the string of the tuning [midi] is on (within STRING_SEMITONES) when that is
    // another string than the one nearest [from], the reading shown: the player may have moved to
    // that string. NO_STRING otherwise. A course's octave string (a 12-string's) is its own string,
    // so a slip to it is no change; and a string exactly an octave up (the high D of Drop D and Open
    // G over the low one) is where the detector's commonest slip lands, so that jump needs the full
    // attack. An octave down stays a string change: a fresh pluck rarely slips down, and the lower
    // string plucked over the ringing higher one is what the detector reads at once.
    private fun stringChangeTarget(midi: Float, from: Float): Int {
        val tuning = strings ?: return NO_STRING
        if (isOctaveUp(midi - from)) return NO_STRING
        val target = tuning.nearestStringIndex(midi)
        val onString = tuning.distanceToString(midi, target) <= STRING_SEMITONES && target != tuning.nearestStringIndex(from)
        return if (onString) target else NO_STRING
    }

    private fun isOctaveUp(semitones: Float): Boolean =
        CENTS_PER_SEMITONE * abs(semitones - SEMITONES_PER_OCTAVE) <= octaveToleranceCents

    private fun rememberLevel(levelDbfs: Float) {
        if (levelDbfs.isNaN()) return
        recentLevels[recentLevelNext] = levelDbfs
        recentLevelNext = (recentLevelNext + 1) % recentLevels.size
        if (recentLevelCount < recentLevels.size) recentLevelCount++
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

        /**
         * 47 frames ≈ 1 s: how long a reading a whole number of periods away must last to be followed
         * without an attack. A string sinking into a TV's talk read a multiple of its period for 7 to
         * 16 frames (Phase 46's recordings), and a slow low string sinks for longer.
         */
        const val DEFAULT_OCTAVE_HOLD_FRAMES = 47

        /** A new note's first readings come within this many frames of its attack (a low string's later than a high one's). */
        const val ATTACK_LOOKBACK_FRAMES = 10

        /** A rise over the quietest recent frame that only an attack makes; a TV's talk rose 6 dB in the recordings. */
        const val ATTACK_DB = 10f

        /**
         * The rise that makes a jump onto another string of the tuning a string change (guitar mode),
         * in [STRING_CHANGE_FRAMES] of its frames: more than a TV's talk (6 dB) and than a string
         * sinking into it read a multiple of its period (under 1 dB, the multiple on a string
         * included); the high E plucked over a low E ringing a second, its first 85 ms 6 dB louder,
         * rose 8–9 dB.
         */
        const val STRING_CHANGE_DB = 7f

        /**
         * Frames on the new string that must rise [STRING_CHANGE_DB]: two, so one frame of the room's
         * talk or of a re-plucked string's slip peaking over 7 dB can't make a string change (the G
         * string re-plucked in a noisy room, `StringChangeTest`); a string plucked over another rises
         * for as long as its attack lasts, several frames.
         */
        const val STRING_CHANGE_FRAMES = 2

        private const val NO_STRING = -1

        /** How near a string a reading must be to land on it: half a semitone. */
        private const val STRING_SEMITONES = 0.5f

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

        /** 12 · log2(3): three periods, an octave and a fifth. */
        private const val TWELFTH_SEMITONES = 19.019550f
    }
}
