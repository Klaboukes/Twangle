package io.github.klaboukes.twangle.core.dsp.speech

import kotlin.math.max

/**
 * Decides when TalkBack hears a new reading. The screen changes ~47 times a second; announcing
 * each change would make the screen reader talk non-stop. [key] says what situation a text belongs
 * to (target note and in-tune, or null while nothing is heard):
 * - the first text is spoken straight away;
 * - a new situation is spoken once it has lasted [settleMillis] ([listeningSettleMillis] for "no
 *   note", so a short drop-out isn't announced);
 * - a played note's reading ([SpokenReadingKey]) is spoken once per situation: while only its cents
 *   move, TalkBack keeps the text it was given, cents included, until the note, the word (Tighten ↔
 *   Loosen, in tune) or what else it says in words changes, so a sound TalkBack's own voice slipped
 *   into a reading can't set off the next one in a loop (Phase 81; the watch's "calmly" rule);
 * - any other text within the same situation (Listen's tempo, the tuner's waiting line) at most
 *   every [repeatMillis].
 *
 * A text for "no note" that is one of the last two said with no note ("Play a string" again after a
 * pluck dies away, or after a tip, "Play a bit louder", that comes and goes with the room's sounds) is
 * still published, so TalkBack reads it when the player touches it, but not [announced]: hearing it
 * after every pluck only talks over the next one (Phase 65), and a tip flipping with the waiting line
 * had TalkBack talk every few seconds with nothing played, its own voice clearing the tip it had just
 * said (Phase 81). A no-note text not among them (a locked string's hint) is announced.
 *
 * Use: ask [delayFor] whenever the text changes, wait that long, then [publish]. Not thread-safe.
 * Shared by the phone (`PacedSpeech` in `:core:designsystem`) and the watch (`WatchPacedSpeech`), so
 * both are paced alike; a reading's key is a [SpokenReadingKey].
 */
class SpokenReadingThrottle(
    private val settleMillis: Long = DEFAULT_SETTLE_MILLIS,
    private val listeningSettleMillis: Long = DEFAULT_LISTENING_SETTLE_MILLIS,
    private val repeatMillis: Long = DEFAULT_REPEAT_MILLIS,
) {
    /** The text TalkBack was last given, or null before the first one. */
    var published: String? = null
        private set

    /**
     * Whether TalkBack should say [published] as it comes (a live region) or only hold it for when the
     * player touches it: false for a "no note" text said already (one of the last two said with no note).
     */
    var announced: Boolean = true
        private set

    private var publishedKey: Any? = null
    private var lastIdleText: String? = null
    private var idleTextBefore: String? = null
    private var publishedAtMillis = 0L
    private var candidateKey: Any? = NO_KEY
    private var candidateSinceMillis = 0L

    /** How long to wait before [text] may be published (0 = now), or null if it already is or is held. */
    fun delayFor(text: String, key: Any?, nowMillis: Long): Long? {
        if (text == published) return null
        if (key != candidateKey) {
            candidateKey = key
            candidateSinceMillis = nowMillis
        }
        val readyAtMillis = when {
            published == null -> nowMillis
            // Only the cents of a reading moved: said already, held as it was.
            key is SpokenReadingKey && key == publishedKey -> return null
            key == publishedKey -> publishedAtMillis + repeatMillis
            key == null -> candidateSinceMillis + listeningSettleMillis
            else -> candidateSinceMillis + settleMillis
        }
        return max(0L, readyAtMillis - nowMillis)
    }

    fun publish(text: String, key: Any?, nowMillis: Long) {
        // A no-note text said already, after a reading or flipping with a tip: not again.
        announced = !(key == null && published != null && (text == lastIdleText || text == idleTextBefore))
        if (key == null && text != lastIdleText) {
            idleTextBefore = lastIdleText
            lastIdleText = text
        }
        published = text
        publishedKey = key
        publishedAtMillis = nowMillis
        // What is said is the situation now: a later one is timed from when it comes.
        candidateKey = key
        candidateSinceMillis = nowMillis
    }

    companion object {
        const val DEFAULT_SETTLE_MILLIS = 400L
        const val DEFAULT_LISTENING_SETTLE_MILLIS = 1_500L
        const val DEFAULT_REPEAT_MILLIS = 2_000L

        private val NO_KEY = Any()
    }
}
