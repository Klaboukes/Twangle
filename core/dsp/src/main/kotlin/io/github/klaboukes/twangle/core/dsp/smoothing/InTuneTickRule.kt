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
    private var lastInTuneMillis = Long.MIN_VALUE

    /** Feeds the current [inTune] for [target] at [nowMillis]; true means "tick now". */
    fun update(inTune: Boolean, target: Any?, nowMillis: Long): Boolean {
        val rose = inTune && !wasInTune
        val tick = rose && (target != lastTarget || nowMillis - lastInTuneMillis >= quietMillis)
        if (inTune) lastTarget = target
        // Remembers when the note was last in tune, up to the moment it stopped being in tune.
        if (inTune || wasInTune) lastInTuneMillis = nowMillis
        wasInTune = inTune
        return tick
    }

    companion object {
        const val DEFAULT_QUIET_MILLIS = 1_500L
    }
}
