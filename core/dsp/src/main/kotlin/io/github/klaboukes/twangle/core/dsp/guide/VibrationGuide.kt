package io.github.klaboukes.twangle.core.dsp.guide

import kotlin.math.abs

/**
 * What the tuner tells the player about the note now, as the vibration guide needs it (Phase 85): the same held-steady
 * cue as the word under the meter, so the buzz, the word and the colour always agree.
 */
enum class GuideCue {
    /** No note is heard. */
    NONE,

    /** A note is heard, but which way to turn isn't settled yet (its first 0.2 s). */
    WAIT,

    /** The note is flat: tighten the string (in Any note: too low). */
    TIGHTEN,

    /** The note is sharp: loosen the string (in Any note: too high). */
    LOOSEN,

    /** In tune. */
    IN_TUNE,
}

/** What the phone plays now: nothing, or one of the guide's three patterns ([VibrationGuide] has their lengths). */
enum class GuidePulse {
    NONE,

    /** One short tap: too low, tighten. */
    TIGHTEN,

    /** Two short taps: too high, loosen. */
    LOOSEN,

    /** One long buzz: in tune. */
    IN_TUNE,
}

/**
 * The vibration guide's rule (Phase 85): from the tuner's cue and cents, frame by frame, to the next thing the
 * vibration motor plays, so a player who can't see the screen tunes by feel while the string rings.
 *
 * - While the note is flat, one short tap ([GuidePulse.TIGHTEN]); while it is sharp, two ([GuidePulse.LOOSEN]). The
 *   first comes as soon as the direction is known; then one group every [intervalSeconds], from
 *   [FAR_INTERVAL_SECONDS] 30 cents off or more down to [NEAR_INTERVAL_SECONDS] at the in-tune window's edge, so the
 *   taps come quicker as the string nears pitch. When the direction swaps, the next group comes as soon as two groups
 *   can still be told apart ([NEAR_INTERVAL_SECONDS] after the last).
 * - On becoming in tune, one long buzz ([GuidePulse.IN_TUNE]), then nothing while it holds. Leaving in tune starts the
 *   taps again. The buzz follows *Vibrate when in tune*'s rule (`InTuneTickRule`): the same note in tune again less
 *   than [QUIET_SECONDS] after it last was (a reading that dropped out for a moment) doesn't buzz again.
 * - Nothing while no note is heard ([GuideCue.NONE]) or its direction isn't settled ([GuideCue.WAIT]); a new note
 *   ([update]'s target changing) or a silence starts the taps afresh.
 *
 * Time is counted in analysis frames (each [update] adds its frame's duration), as [GuideCue]'s source does, so it
 * needs no clock. Allocation-free and not thread-safe.
 */
class VibrationGuide {
    private var nowSeconds = 0.0

    /** The note the taps are about, or [NO_TARGET]. */
    private var target = NO_TARGET

    /** When the last tap group or buzz started ([NEVER] before a note's first), which group it was, when the buzz ends. */
    private var lastStart = NEVER
    private var direction = GuidePulse.NONE
    private var buzzEndsAt = NEVER

    private var wasInTune = false
    private var lastInTuneTarget = NO_TARGET
    private var lastInTuneAt = NEVER

    /**
     * Feeds one frame of [frameSeconds]: the tuner's [cue], the reading's [cents] off the target note [targetMidi], and
     * the in-tune window's [edgeCents] (5, or 2 in Precise). Returns what to play now, usually [GuidePulse.NONE].
     */
    fun update(cue: GuideCue, cents: Float, targetMidi: Int, frameSeconds: Float, edgeCents: Float = DEFAULT_EDGE_CENTS): GuidePulse {
        nowSeconds += frameSeconds
        val heard = cue != GuideCue.NONE
        if (!heard || targetMidi != target) {
            // A silence or a new note: its first direction taps at once.
            target = if (heard) targetMidi else NO_TARGET
            lastStart = NEVER
            direction = GuidePulse.NONE
            // A new note has to become in tune on its own.
            if (heard) wasInTune = false
        }
        val buzz = inTuneBuzz(cue == GuideCue.IN_TUNE)
        return when (cue) {
            GuideCue.NONE, GuideCue.WAIT -> GuidePulse.NONE
            GuideCue.IN_TUNE -> {
                direction = GuidePulse.NONE
                if (buzz) {
                    lastStart = nowSeconds
                    buzzEndsAt = nowSeconds + IN_TUNE_BUZZ_SECONDS
                    GuidePulse.IN_TUNE
                } else {
                    GuidePulse.NONE
                }
            }
            GuideCue.TIGHTEN, GuideCue.LOOSEN -> taps(if (cue == GuideCue.TIGHTEN) GuidePulse.TIGHTEN else GuidePulse.LOOSEN, cents, edgeCents)
        }
    }

    /** Starts over, as at the start: nothing played, no note. */
    fun reset() {
        nowSeconds = 0.0
        target = NO_TARGET
        lastStart = NEVER
        direction = GuidePulse.NONE
        buzzEndsAt = NEVER
        wasInTune = false
        lastInTuneTarget = NO_TARGET
        lastInTuneAt = NEVER
    }

    /** The next tap group for [wanted], if it is due now. */
    private fun taps(wanted: GuidePulse, cents: Float, edgeCents: Float): GuidePulse {
        val gap = when {
            lastStart == NEVER -> 0.0
            wanted != direction -> NEAR_INTERVAL_SECONDS.toDouble()
            else -> intervalSeconds(cents, edgeCents).toDouble()
        }
        // Never over the buzz: its last part would hide the first tap.
        val due = maxOf(lastStart + gap, buzzEndsAt + BUZZ_GAP_SECONDS)
        if (nowSeconds < due - EPSILON_SECONDS) return GuidePulse.NONE
        lastStart = nowSeconds
        direction = wanted
        return wanted
    }

    /**
     * Whether becoming in tune now earns the long buzz: every rise does, except the same note in tune again less than
     * [QUIET_SECONDS] after it last was (`InTuneTickRule`'s rule, so the buzz comes exactly when the click would).
     */
    private fun inTuneBuzz(inTune: Boolean): Boolean {
        val rose = inTune && !wasInTune
        val buzz = rose && (target != lastInTuneTarget || nowSeconds - lastInTuneAt >= QUIET_SECONDS - EPSILON_SECONDS)
        if (inTune) lastInTuneTarget = target
        if (inTune || wasInTune) lastInTuneAt = nowSeconds
        wasInTune = inTune
        return buzz
    }

    companion object {
        /** How long one tap lasts on the motor. Short enough to feel as a tap, long enough for a weak motor to start. */
        const val TAP_SECONDS = 0.025f

        /** From one tap's start to the next's in the loosen pair: two taps, not one long one, with 65 ms between. */
        const val PAIR_SECONDS = 0.09f

        /** The in-tune buzz: clearly longer than any tap, so it can't be mistaken for one. */
        const val IN_TUNE_BUZZ_SECONDS = 0.375f

        /** The time between two groups 30 cents off or more: calm, so a string far off doesn't nag. */
        const val FAR_INTERVAL_SECONDS = 0.7f

        /**
         * The time between two groups at the in-tune window's edge: quick, yet still more than three times the pair's
         * length, so two single taps are never felt as a double (at least 0.28 s).
         */
        const val NEAR_INTERVAL_SECONDS = 0.3f

        /** Where the time between groups stops growing. */
        const val FAR_CENTS = 30f

        /** The in-tune window's edge in the Relaxed setting (`InTuneDetector.DEFAULT_ENTER_CENTS`). */
        const val DEFAULT_EDGE_CENTS = 5f

        /** The same note in tune again within this long doesn't buzz again (`InTuneTickRule.DEFAULT_QUIET_MILLIS`). */
        const val QUIET_SECONDS = 1.5f

        /** After the buzz, this much stillness before a tap, so the two are felt apart. */
        const val BUZZ_GAP_SECONDS = 0.15f

        /**
         * The time between tap groups [cents] off a target whose in-tune window ends at [edgeCents]: a straight line
         * from [NEAR_INTERVAL_SECONDS] at the edge (or nearer) to [FAR_INTERVAL_SECONDS] at [FAR_CENTS] (or further),
         * a Geiger counter's way of saying "closer" (the mock-up's rule, docs/design/tuning-guide-preview.html, with the
         * phone's numbers).
         */
        fun intervalSeconds(cents: Float, edgeCents: Float = DEFAULT_EDGE_CENTS): Float {
            val off = abs(cents)
            if (off.isNaN()) return FAR_INTERVAL_SECONDS
            val span = FAR_CENTS - edgeCents
            if (span <= 0f) return FAR_INTERVAL_SECONDS
            val t = ((off - edgeCents) / span).coerceIn(0f, 1f)
            return NEAR_INTERVAL_SECONDS + t * (FAR_INTERVAL_SECONDS - NEAR_INTERVAL_SECONDS)
        }

        private const val NO_TARGET = Int.MIN_VALUE
        private const val NEVER = -1e9
        private const val EPSILON_SECONDS = 1e-4
    }
}
