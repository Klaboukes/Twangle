package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.dsp.trail.PitchHistory
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** How the pitch trail's line runs over its last moments, for TalkBack: see [PitchTrailDescriber]. */
enum class TrailMotion {
    /** No note now: nothing heard, or only a moment of it. */
    NO_NOTE,

    /** A held note that barely moves. */
    STEADY,

    /** Moving up and down (vibrato, an unsteady hand) without going anywhere. */
    WAVERING,

    /** Going up (a bend, a string being tuned up). */
    BENDING_UP,

    /** Going down. */
    BENDING_DOWN,
}

/**
 * What TalkBack says about the pitch trail: its [motion] and, for a steady or wavering note, where it
 * sits: [inTune], or [cents] off the note (positive is sharp), the line's middle over its last moments.
 */
data class TrailDescription(val motion: TrailMotion, val cents: Int = 0, val inTune: Boolean = false) {
    companion object {
        val NO_NOTE = TrailDescription(TrailMotion.NO_NOTE)
    }
}

/**
 * Puts the pitch trail's line into words (Phase 31, docs/architecture/ui-tuner.md, *Pitch trail
 * view*): the readings of the last [WINDOW_SECONDS] in a [PitchHistory], each taken as a pitch in
 * cents (its note · 100 + its cents, so a bend that crosses into the next note is still one bend).
 * A least-squares line through them over time gives the rise; the spread round that line and round
 * their mean says how steady they are:
 * - fewer than [MIN_READINGS] readings, or none in the last [NO_NOTE_AFTER_SECONDS]: no note;
 * - rising or falling at least [BEND_CENTS] over the window, close to a straight line (spread round
 *   it at most [BEND_STRAIGHTNESS] of the rise): bending up or down;
 * - spread round the mean at most [STEADY_SPREAD_CENTS] (a standard deviation): steady;
 * - otherwise wavering (vibrato swings ±10–20 cents).
 * Where it sits is in tune when most of the window's readings were shown in tune (or, wavering, when
 * their middle is within [CENTS_STEP] of the note), else the cents of the readings' middle off the
 * newest note ([shownCents]).
 * Pure and allocation-free (it walks the history's arrays), so it is tested on the JVM.
 */
object PitchTrailDescriber {
    /** How far back the description looks: the last moments, not the whole 4 s line. */
    const val WINDOW_SECONDS = 1.5f

    /** At least this many readings (≈ 0.25 s at 47 a second) make a note worth describing. */
    const val MIN_READINGS = 12

    /** The note is gone when the newest reading is this much older than the newest entry (silence since). */
    const val NO_NOTE_AFTER_SECONDS = 0.3f

    /** A bend rises or falls at least this much over the window. */
    const val BEND_CENTS = 15f

    /** ... and its readings keep this close to a straight line, as a share of the rise. */
    const val BEND_STRAIGHTNESS = 0.35f

    /** A steady note's readings spread at most this much (a standard deviation, in cents). */
    const val STEADY_SPREAD_CENTS = 3.5f

    private const val CENTS_PER_SEMITONE = 100f

    /** The description of [history]'s last [WINDOW_SECONDS] (entries below [end]). */
    fun describe(history: PitchHistory, end: Long = history.end): TrailDescription {
        val first = history.firstWithin(WINDOW_SECONDS, end)
        if (first >= end) return TrailDescription.NO_NOTE
        val newestTime = history.timeAt(end - 1)
        // Sums for the least-squares line, from the first reading (so the floats stay small).
        var n = 0
        var t0 = 0f
        var p0 = 0f
        var sumT = 0.0
        var sumP = 0.0
        var sumTT = 0.0
        var sumTP = 0.0
        var sumPP = 0.0
        var inTune = 0
        var lastTime = Float.NaN
        var lastTarget = PitchHistory.NO_NOTE
        var index = first
        while (index < end) {
            if (!history.isGapAt(index)) {
                val time = history.timeAt(index)
                val pitch = history.targetAt(index) * CENTS_PER_SEMITONE + history.centsAt(index)
                if (n == 0) {
                    t0 = time
                    p0 = pitch
                }
                val t = (time - t0).toDouble()
                val p = (pitch - p0).toDouble()
                sumT += t
                sumP += p
                sumTT += t * t
                sumTP += t * p
                sumPP += p * p
                if (history.inTuneAt(index)) inTune++
                lastTime = time
                lastTarget = history.targetAt(index)
                n++
            }
            index++
        }
        if (n < MIN_READINGS || newestTime - lastTime > NO_NOTE_AFTER_SECONDS) return TrailDescription.NO_NOTE
        val meanT = sumT / n
        val meanP = sumP / n
        val varT = sumTT / n - meanT * meanT
        val varP = (sumPP / n - meanP * meanP).coerceAtLeast(0.0)
        val covTP = sumTP / n - meanT * meanP
        val slope = if (varT > 0.0) covTP / varT else 0.0
        val rise = (slope * (lastTime - t0)).toFloat()
        // What the straight line leaves unexplained: var(p) − slope · cov(t, p).
        val residual = sqrt((varP - slope * covTP).coerceAtLeast(0.0)).toFloat()
        if (abs(rise) >= BEND_CENTS && residual <= abs(rise) * BEND_STRAIGHTNESS) {
            return TrailDescription(if (rise > 0f) TrailMotion.BENDING_UP else TrailMotion.BENDING_DOWN)
        }
        val spread = sqrt(varP).toFloat()
        val motion = if (spread <= STEADY_SPREAD_CENTS) TrailMotion.STEADY else TrailMotion.WAVERING
        // The middle, as cents off the note the newest reading aims at.
        val middle = (p0 + meanP.toFloat()) - lastTarget * CENTS_PER_SEMITONE
        val shown = shownCents(middle)
        // A wavering note's single readings are mostly off the note; its middle is what's in tune.
        val tuned = inTune * 2 > n || shown == 0 || motion == TrailMotion.WAVERING && abs(middle) < CENTS_STEP
        return TrailDescription(motion, cents = if (tuned) 0 else shown, inTune = tuned)
    }

    /** Cents as said: whole cents near the note, in steps of [CENTS_STEP] further off, so a wavering middle doesn't change the words every moment. */
    fun shownCents(cents: Float): Int =
        if (abs(cents) < CENTS_STEP) cents.roundToInt() else (cents / CENTS_STEP).roundToInt() * CENTS_STEP.toInt()

    /** Further off than this, the cents are said in steps of this many. */
    const val CENTS_STEP = 5f
}

/**
 * Keeps TalkBack's description of the trail from chattering: a new [TrailDescription] replaces the one
 * shown only once it has come [SETTLE_SAMPLES] times in a row (the screen asks about twice a second),
 * so a single odd moment (a pluck's first wobble, a short dropout) never changes what is said.
 */
class SettledTrailDescription {
    /** The description to say now. */
    var shown: TrailDescription = TrailDescription.NO_NOTE
        private set

    private var candidate: TrailDescription = TrailDescription.NO_NOTE
    private var seen = 0

    /** Takes the latest description; returns [shown], changed or not. */
    fun offer(description: TrailDescription): TrailDescription {
        if (description == shown) {
            seen = 0
            return shown
        }
        if (description == candidate) seen++ else {
            candidate = description
            seen = 1
        }
        if (seen >= SETTLE_SAMPLES) {
            shown = description
            seen = 0
        }
        return shown
    }

    companion object {
        const val SETTLE_SAMPLES = 2
    }
}
