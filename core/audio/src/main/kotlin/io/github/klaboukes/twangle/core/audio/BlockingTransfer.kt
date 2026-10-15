package io.github.klaboukes.twangle.core.audio

/**
 * Moves [count] samples in steps until all are done: the loop behind a blocking `AudioRecord.read`
 * or `AudioTrack.write`, which may move fewer samples than asked.
 *
 * [step] gets how many are done so far and returns how many it moved, or a negative error code,
 * which goes to [onError]. A step that moves nothing is retried after [pause]; after
 * [maxEmptySteps] of them in a row the transfer is stuck (the system stopped the recording or
 * the track without saying so) and [onStalled] ends it, rather than spinning forever.
 *
 * Inline, so the lambdas cost no allocation in the audio loop.
 */
internal inline fun transferFully(
    count: Int,
    maxEmptySteps: Int = MAX_EMPTY_STEPS,
    pause: () -> Unit,
    onError: (code: Int) -> Nothing,
    onStalled: () -> Nothing,
    step: (done: Int) -> Int,
) {
    var done = 0
    var empty = 0
    while (done < count) {
        val moved = step(done)
        when {
            moved < 0 -> onError(moved)
            moved == 0 -> {
                if (++empty >= maxEmptySteps) onStalled()
                pause()
            }
            else -> {
                empty = 0
                done += moved
            }
        }
    }
}

/** Empty steps in a row that make a transfer stuck: with [EMPTY_STEP_PAUSE_MILLIS] between them, about 0.25 s. */
internal const val MAX_EMPTY_STEPS = 50

/** The wait before retrying a step that moved nothing. */
internal const val EMPTY_STEP_PAUSE_MILLIS = 5L

/** [transferFully]'s usual pause: the audio threads may sleep, they are never the main thread. */
internal fun pauseAfterEmptyStep() = Thread.sleep(EMPTY_STEP_PAUSE_MILLIS)
