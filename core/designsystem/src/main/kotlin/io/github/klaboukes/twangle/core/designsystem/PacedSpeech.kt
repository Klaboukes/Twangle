package io.github.klaboukes.twangle.core.designsystem

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * What a polite live region gives TalkBack, no faster than [SpokenReadingThrottle] allows, for texts
 * that change many times a second (the tuner's reading, Listen's tempo). Remember it above anything
 * that comes and goes with the state (e.g. a `when` over the screen's states): a fresh one starts the
 * pacing afresh, so its first text is said at once.
 */
@Stable
class PacedSpeech {
    private val throttle = SpokenReadingThrottle()

    /** The text TalkBack has been given, "" before the first. */
    var spoken by mutableStateOf("")
        private set

    /**
     * Whether the live region should announce [spoken] ([SpokenReadingThrottle.announced]): false for
     * "Play a string" said again after a pluck dies away. Set the live region only while this is true.
     */
    var announced by mutableStateOf(true)
        private set

    /** Waits as long as the throttle says, then gives TalkBack [text]; nothing if it already has it. */
    internal suspend fun offer(text: String, key: Any?) {
        val wait = throttle.delayFor(text, key, SystemClock.uptimeMillis()) ?: return
        if (wait > 0) delay(wait)
        throttle.publish(text, key, SystemClock.uptimeMillis())
        announced = throttle.announced
        spoken = text
    }
}

/** A [PacedSpeech] that starts afresh when any of [keys] changes. */
@Composable
fun rememberPacedSpeech(vararg keys: Any?): PacedSpeech = remember(*keys) { PacedSpeech() }

/**
 * [text], passed on to TalkBack through [speech] (see [SpokenReadingThrottle] for [key]): what the
 * live region should say now.
 */
@Composable
fun pacedSpeech(text: String, key: Any?, speech: PacedSpeech = rememberPacedSpeech()): String {
    LaunchedEffect(speech, text, key) { speech.offer(text, key) }
    return speech.spoken
}
