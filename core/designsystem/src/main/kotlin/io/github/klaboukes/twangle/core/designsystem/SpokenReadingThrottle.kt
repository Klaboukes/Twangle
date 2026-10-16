package io.github.klaboukes.twangle.core.designsystem

import kotlin.math.max

/**
 * Decides when TalkBack hears a new reading. The screen changes ~47 times a second; announcing
 * each change would make the screen reader talk non-stop. [key] says what situation a text belongs
 * to (target note and in-tune, or null while nothing is heard):
 * - the first text is spoken straight away;
 * - a new situation is spoken once it has lasted [settleMillis] ([listeningSettleMillis] for "no
 *   note", so a short drop-out isn't announced);
 * - within the same situation (only the cents moved) at most every [repeatMillis].
 *
 * Use: ask [delayFor] whenever the text changes, wait that long, then [publish]. Not thread-safe.
 */
class SpokenReadingThrottle(
    private val settleMillis: Long = DEFAULT_SETTLE_MILLIS,
    private val listeningSettleMillis: Long = DEFAULT_LISTENING_SETTLE_MILLIS,
    private val repeatMillis: Long = DEFAULT_REPEAT_MILLIS,
) {
    /** The text TalkBack was last given, or null before the first one. */
    var published: String? = null
        private set

    private var publishedKey: Any? = null
    private var publishedAtMillis = 0L
    private var candidateKey: Any? = NO_KEY
    private var candidateSinceMillis = 0L

    /** How long to wait before [text] may be published (0 = now), or null if it already is. */
    fun delayFor(text: String, key: Any?, nowMillis: Long): Long? {
        if (text == published) return null
        if (key != candidateKey) {
            candidateKey = key
            candidateSinceMillis = nowMillis
        }
        val readyAtMillis = when {
            published == null -> nowMillis
            key == publishedKey -> publishedAtMillis + repeatMillis
            key == null -> candidateSinceMillis + listeningSettleMillis
            else -> candidateSinceMillis + settleMillis
        }
        return max(0L, readyAtMillis - nowMillis)
    }

    fun publish(text: String, key: Any?, nowMillis: Long) {
        published = text
        publishedKey = key
        publishedAtMillis = nowMillis
    }

    companion object {
        const val DEFAULT_SETTLE_MILLIS = 400L
        const val DEFAULT_LISTENING_SETTLE_MILLIS = 1_500L
        const val DEFAULT_REPEAT_MILLIS = 2_000L

        private val NO_KEY = Any()
    }
}
