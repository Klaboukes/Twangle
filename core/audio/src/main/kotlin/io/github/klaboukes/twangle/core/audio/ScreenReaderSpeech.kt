package io.github.klaboukes.twangle.core.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlin.math.ceil

/**
 * Whether a screen reader's voice (TalkBack's speech, its earcons) plays on this phone right now (Phase 81). Anything
 * that listens to the microphone sets its frames aside while it does, plus [TAIL_SECONDS] after, so the phone's own
 * speaker saying "12 cents flat" is never read as a played note, and a spoken reading can't set off the next one.
 *
 * [speaking] is cold: the system is watched only while it is collected, and only while a screen reader is on; with
 * none it is a steady false, so nothing changes for anyone else. A listener collects it for as long as it listens,
 * most simply through [watching].
 */
interface ScreenReaderSpeech {

    /** True while a screen reader's voice plays, false otherwise; starts with the answer for now, distinct. */
    val speaking: Flow<Boolean>

    /**
     * True while a screen reader (TalkBack or one like it) is on, whether it speaks or not; starts with the answer for
     * now, distinct, and watches the system only while collected. The vibration guide's default follows it (Phase 85).
     */
    val screenReaderOn: Flow<Boolean> get() = flowOf(false)

    companion object {
        /**
         * How long the microphone's frames stay set aside after the voice has stopped. The system says "stopped" once
         * the speech engine has played its last sample ([AndroidScreenReaderSpeech]); after that the frames still
         * hold it for the speaker's and the microphone's latency (tens of ms each), the room's echo (a living room's
         * falls below the gate in about 0.1 s) and one analysis window (85 ms, 170 ms for a bass's low notes), so
         * 0.4 s covers the longest of them with a little to spare. Longer than the reference tone's 0.3 s tail, whose
         * player waits for its padding of silence to play out too. A player who plucks right after the voice waits
         * that much longer for the reading; the voice's own next phrase follows within 0.1 s, inside the tail.
         */
        const val TAIL_SECONDS = 0.4f

        /** [TAIL_SECONDS] in frames [frameSeconds] apart, rounded up. */
        fun tailFrames(frameSeconds: Float): Int = ceil(TAIL_SECONDS / frameSeconds).toInt()

        /** No screen reader ever speaks: tests, previews and anything without a phone behind it. */
        val NONE: ScreenReaderSpeech = object : ScreenReaderSpeech {
            override val speaking: Flow<Boolean> = flowOf(false)
        }
    }
}

/**
 * Runs [block] with [ScreenReaderSpeech.speaking] watched for as long as it runs: its argument's `value` says whether
 * the voice plays now, a plain read that allocates nothing, so a listener can ask it on every frame from any thread.
 * The watch (and the system's callbacks behind it) ends with [block]. [block] may emit to the flow it runs in.
 */
suspend fun <T> ScreenReaderSpeech.watching(block: suspend CoroutineScope.(speaking: StateFlow<Boolean>) -> T): T = coroutineScope {
    val now = speaking.stateIn(this)
    try {
        block(now)
    } finally {
        coroutineContext.cancelChildren()
    }
}
