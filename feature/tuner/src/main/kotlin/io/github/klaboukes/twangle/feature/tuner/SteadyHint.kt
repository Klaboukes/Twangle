package io.github.klaboukes.twangle.feature.tuner

import kotlin.math.min

/** A message a [SteadyHint] can show, with its own timing. */
interface SteadyMessage {
    /** The message shows only once it has been wanted this long without a break. */
    val settleSeconds: Float

    /** Once shown, the message stays at least this long, whatever is wanted meanwhile. */
    val minShowSeconds: Float
}

/**
 * The hint line's no-flicker rule. What [update] is told to show (the *wanted* message, which may
 * change every frame) only reaches the screen once it has been wanted for its
 * [SteadyMessage.settleSeconds] without a break, and only after the message on screen has stayed
 * for its [SteadyMessage.minShowSeconds]. So a brief blip never shows, and the line never changes
 * faster than the minimum time allows. Margins (hysteresis) belong in the code that picks the
 * wanted message, e.g. [TuningCue]; this class only adds the timing.
 *
 * null means "no message". It needs no settling. When a message goes away, the empty line stays at
 * least [emptyMinShowSeconds] too, so a message can't blink off and on; after [reset] (and at the
 * start) it has no minimum, so the first message of a note shows as soon as it has settled.
 *
 * Time is counted in analysis frames (each [update] takes the frame duration), so it is exact and
 * needs no clock. Allocation-free and not thread-safe.
 */
class SteadyHint<T : SteadyMessage>(private val emptyMinShowSeconds: Float = HINT_MIN_SHOW_SECONDS) {
    /** The message on screen, or null for none. */
    var shown: T? = null
        private set

    private var shownSeconds = 0f
    private var emptyAfterMessage = false
    private var hasPending = false
    private var pending: T? = null
    private var pendingSeconds = 0f

    /** Feeds what should show now, for one frame of [frameSeconds], and returns [shown]. */
    fun update(wanted: T?, frameSeconds: Float): T? {
        // Capped, so hours of one message don't run into float precision.
        shownSeconds = min(shownSeconds + frameSeconds, MAX_COUNT_SECONDS)
        if (wanted == shown) {
            hasPending = false
            return shown
        }
        if (!hasPending || wanted != pending) {
            hasPending = true
            pending = wanted
            pendingSeconds = 0f
        }
        pendingSeconds = min(pendingSeconds + frameSeconds, MAX_COUNT_SECONDS)
        val settled = pendingSeconds >= (wanted?.settleSeconds ?: 0f) - EPSILON_SECONDS
        val minShowSeconds = shown?.minShowSeconds ?: if (emptyAfterMessage) emptyMinShowSeconds else 0f
        if (settled && shownSeconds >= minShowSeconds - EPSILON_SECONDS) {
            emptyAfterMessage = wanted == null
            shown = wanted
            shownSeconds = 0f
            hasPending = false
        }
        return shown
    }

    /** Clears the line at once, e.g. when the note stops or the target changes. */
    fun reset() {
        shown = null
        shownSeconds = 0f
        emptyAfterMessage = false
        hasPending = false
        pending = null
    }

    private companion object {
        /** So times made of float frame durations still reach a limit they add up to exactly. */
        const val EPSILON_SECONDS = 1e-4f
        const val MAX_COUNT_SECONDS = 3_600f
    }
}
