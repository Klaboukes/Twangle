package io.github.klaboukes.twangle.core.audio

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen

/**
 * These chunks, with a microphone that is busy as it opens tried again every [pauseMillis], [tries] times more, before
 * the busy is passed on. Before Android 10 only one recording runs at a time and a second one fails to start
 * (`AndroidAudioRecorder.start`); the tuner and the pitch trail keep theirs 5 s after their screen goes (hard rule 4),
 * so a tool that listens (the trail, Ear training, Chords' or the metronome's Listen) opened within those 5 s finds it
 * busy. With the defaults it waits a little longer than that grace (24 × 250 ms, plus each try's opening) before it
 * gives up. Only the start is tried again: a microphone that goes busy once its chunks have come (a call, an
 * assistant) ends the flow at once, as before.
 */
internal fun Flow<FloatArray>.retryingBusyStart(
    tries: Int = BUSY_START_TRIES,
    pauseMillis: Long = BUSY_START_PAUSE_MILLIS,
): Flow<FloatArray> = flow {
    var started = false
    emitAll(
        onEach { started = true }.retryWhen { cause, attempt ->
            val again = cause is AudioRecorderException.MicrophoneBusy && !started && attempt < tries
            if (again) delay(pauseMillis)
            again
        },
    )
}

/** How many times more a busy start is tried before Android 10 ([retryingBusyStart]). */
internal const val BUSY_START_TRIES = 24

/** The pause between those tries. */
internal const val BUSY_START_PAUSE_MILLIS = 250L
