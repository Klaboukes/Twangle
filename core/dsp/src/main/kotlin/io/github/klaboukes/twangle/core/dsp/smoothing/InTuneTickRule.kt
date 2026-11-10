package io.github.klaboukes.twangle.core.dsp.smoothing

/**
 * Decides when reaching "in tune" earns a haptic tick. Every rise of `inTune` ticks, except when the
 * same target note was in tune less than [quietMillis] ago: a reading that drops out for a moment
 * (a noisy room, a fading string) resets "in tune", and the note earning it again shouldn't buzz
 * again. A different note, or the same note after a real pause, ticks as usual.
 *
 * Starts from [initiallyInTune], so appearing while already in tune doesn't tick. Not thread-safe.
 */
class InTuneTickRule(
    initiallyInTune: Boolean = false,
    private val quietMillis: Long = DEFAULT_QUIET_MILLIS,
) {
    var wasInTune: Boolean = initiallyInTune
        private set

    private var lastTarget: Any? = null

    // When the note was last in tune; meaningless until it has been ([everInTune]), so nothing is subtracted from a
    // made-up "long ago", which overflowed (Long.MIN_VALUE until Phase 89) and held back the first tick of a null target.
    private var lastInTuneMillis = 0L
    private var everInTune = initiallyInTune

    /** Feeds the current [inTune] for [target] at [nowMillis]; true means "tick now". */
    fun update(inTune: Boolean, target: Any?, nowMillis: Long): Boolean {
        val rose = inTune && !wasInTune
        val tick = rose && (!everInTune || target != lastTarget || nowMillis - lastInTuneMillis >= quietMillis)
        if (inTune) {
            lastTarget = target
            everInTune = true
        }
        // Remembers when the note was last in tune, up to the moment it stopped being in tune.
        if (inTune || wasInTune) lastInTuneMillis = nowMillis
        wasInTune = inTune
        return tick
    }

    companion object {
        const val DEFAULT_QUIET_MILLIS = 1_500L
    }
}
