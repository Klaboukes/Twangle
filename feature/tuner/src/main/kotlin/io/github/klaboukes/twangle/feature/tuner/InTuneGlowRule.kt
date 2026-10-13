package io.github.klaboukes.twangle.feature.tuner

/**
 * Decides when the meter's in-tune glow is lit, the visual partner of [InTuneTickRule]. It lights on
 * the same update that rises `inTune` (so it eases in together with the vibration tick), stays lit
 * while the note holds, and goes out when the note leaves, so it follows the steady hint's in-tune
 * decision and never flickers near the edge.
 *
 * Like the tick it forgives a brief dropout of the same note: when the reading is lost for a moment
 * (no note at all, [update] with a null target) the glow stays lit for [holdMillis], also while that
 * same note comes back and earns "in tune" again; if it does within that time the glow never went
 * out, so it doesn't restart. A different note, or the same note heard but drifting out of tune
 * without a dropout, puts it out at once. So does the same note coming back clearly off, once the
 * hint asks to tune it up or down ([update]'s `retuning`; a pluck often starts sharp): the glow
 * never lights the meter while the hint says "Tune down". [holdMillis] is shorter than the tick's
 * quiet time, so a dropout the glow bridges never ticks twice.
 *
 * Starts from [initiallyInTune], so appearing while already in tune shows the glow without a new
 * ease-in. Time comes from the caller; pure and not thread-safe.
 */
class InTuneGlowRule(
    initiallyInTune: Boolean = false,
    private val holdMillis: Long = DEFAULT_HOLD_MILLIS,
) {
    /** Whether the glow is lit. */
    var lit: Boolean = initiallyInTune
        private set

    /** Whether the note was in tune at the last [update]. */
    var wasInTune: Boolean = initiallyInTune
        private set

    private var litTarget: Any? = null
    private var lostAtMillis = NOT_LOST

    /**
     * When a glow held through a dropout goes out, if nothing else happens first: the caller feeds
     * [update] again then. [Long.MAX_VALUE] while nothing is held.
     */
    val holdEndsAtMillis: Long
        get() = if (lit && lostAtMillis != NOT_LOST) lostAtMillis + holdMillis else Long.MAX_VALUE

    /**
     * Feeds the current [inTune] for [target] (null without a note) at [nowMillis]; returns [lit].
     * [retuning] is true while the hint asks to tune up or down ([TunerHint.isDirection]).
     */
    fun update(inTune: Boolean, target: Any?, nowMillis: Long, retuning: Boolean = false): Boolean {
        wasInTune = inTune
        when {
            inTune -> {
                lit = true
                litTarget = target
                lostAtMillis = NOT_LOST
            }
            // The note is gone for a moment, or back but still earning "in tune" (no hint to tune it
            // yet): hold the glow, counted from when the note went.
            lit && (target == null || (lostAtMillis != NOT_LOST && target == litTarget && !retuning)) -> {
                if (lostAtMillis == NOT_LOST) lostAtMillis = nowMillis
                if (nowMillis - lostAtMillis >= holdMillis) goOut()
            }
            // Out of tune, another note, or back but asking to be tuned: out at once.
            else -> goOut()
        }
        return lit
    }

    private fun goOut() {
        lit = false
        litTarget = null
        lostAtMillis = NOT_LOST
    }

    companion object {
        /**
         * How long the glow outlasts a lost reading: enough for the same note to come back after a
         * gap of up to 300 ms and earn "in tune" again (another 300 ms), short enough that it fades
         * soon after the string stops.
         */
        const val DEFAULT_HOLD_MILLIS = 600L

        private const val NOT_LOST = Long.MIN_VALUE
    }
}
