package io.github.klaboukes.twangle.core.audio

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import java.util.concurrent.atomic.AtomicInteger

/**
 * These chunks, with a microphone that is busy as it opens tried again every [pauseMillis] for as long as the busy may
 * be the app's own, before the busy is passed on. Before Android 10 only one recording runs at a time and a second one
 * fails to start (`AndroidAudioRecorder.start`); the tuner and the pitch trail keep theirs 5 s after their screen goes
 * (hard rule 4), so a tool that listens (the trail, Ear training, Chords' or the metronome's Listen) opened within those
 * 5 s finds it busy.
 *
 * A busy start is tried again [freeTries] times (about 1 s), and further while another of the app's own recordings
 * holds the microphone ([appRecording]), until [freeTries] tries after it let go; [tries] times at most (about 6 s, a
 * little longer than that grace). So a tool opened within the tuner's grace starts as soon as the tuner lets go, and
 * another app holding the microphone (a call, a recorder) says "Microphone is busy" after about 1 s, at the first start
 * and on every Try again (Phase 91; until then 6 s each time, long enough for Ear training's answer to run out).
 * Only the start is tried again: a microphone that goes busy once its chunks have come (a call, an assistant) ends the
 * flow at once, as before.
 */
internal fun Flow<FloatArray>.retryingBusyStart(
    appRecording: () -> Boolean,
    tries: Int = BUSY_START_TRIES,
    freeTries: Int = BUSY_START_FREE_TRIES,
    pauseMillis: Long = BUSY_START_PAUSE_MILLIS,
): Flow<FloatArray> = flow {
    var started = false
    // The last try that found another of the app's recordings holding the microphone; the first counts as one.
    var heldAt = 0L
    emitAll(
        onEach { started = true }.retryWhen { cause, attempt ->
            if (appRecording()) heldAt = attempt
            val again = cause is AudioRecorderException.MicrophoneBusy && !started &&
                attempt < tries && attempt - heldAt < freeTries
            if (again) delay(pauseMillis)
            again
        },
    )
}

/** How many of the app's own recordings hold the microphone now, every recorder's together. */
internal class OpenRecordings {
    private val count = AtomicInteger()

    /** Whether one does. */
    val any: Boolean get() = count.get() > 0

    /** A recording started. */
    fun opened() {
        count.incrementAndGet()
    }

    /** A recording let go of the microphone. */
    fun closed() {
        count.decrementAndGet()
    }
}

/** How many times more a busy start is tried before Android 10 at most ([retryingBusyStart]): about 6 s. */
internal const val BUSY_START_TRIES = 24

/** How many times more it is tried when none of the app's own recordings holds the microphone: about 1 s. */
internal const val BUSY_START_FREE_TRIES = 4

/** The pause between those tries. */
internal const val BUSY_START_PAUSE_MILLIS = 250L
