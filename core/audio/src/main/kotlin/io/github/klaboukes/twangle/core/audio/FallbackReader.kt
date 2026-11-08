package io.github.klaboukes.twangle.core.audio

/**
 * The inputs that recorded nothing at all on this phone while the next one heard the room. Every
 * recorder skips them for the rest of the app's life (one shared [AndroidAudioRecorder.skippedInputs]);
 * nothing is saved, so a new start of the app tries them again.
 */
internal class SkippedInputs {

    @Volatile
    private var skipped: Set<AudioInput> = emptySet()

    /** [inputs] in their order, without the skipped ones (never none: then all of them). */
    fun usable(inputs: List<AudioInput>): List<AudioInput> = inputs.filterNot { it in skipped }.ifEmpty { inputs }

    operator fun contains(input: AudioInput): Boolean = input in skipped

    @Synchronized
    fun skip(input: AudioInput) {
        skipped = skipped + input
    }
}

/**
 * One listening session's microphone, chunk by chunk: [ChunkReader] on the input that is open, but
 * an input that delivers nothing but exact zeros from its very first sample for
 * [ChunkReader.SILENT_SECONDS] gives way to the next of [inputs], at the same rate, within the same
 * session. Some phones say they support `UNPROCESSED` but record only zeros on it, while
 * `VOICE_RECOGNITION` hears fine; without this the tuner said "Microphone is busy" there forever.
 *
 * The silent input goes into [skipped] only once the next one hears the room. If that one is silent
 * from its first sample too, the microphone itself is muted (the privacy switch, another app) and
 * the session ends with [AudioRecorderException.MicrophoneBusy] as before; nothing is remembered,
 * so the next session tries the first input again. An input that heard the room and then fell
 * silent, or that Android silenced for another app, is busy at once, as before.
 *
 * [open] opens and starts an input at this session's rate (null when it won't open there; it may
 * also throw); [release] stops and lets go of one. The rate stays, so the chunk size does too.
 * Allocates nothing per chunk; giving way makes one new [ChunkReader].
 */
internal class FallbackReader<M : MicInput>(
    first: M,
    firstInput: AudioInput,
    inputs: List<AudioInput>,
    private val sampleRate: Int,
    private val skipped: SkippedInputs,
    private val open: (AudioInput) -> M?,
    private val release: (M) -> Unit,
    private val pause: () -> Unit = ::pauseAfterEmptyStep,
) {
    // The inputs after the first, best first: the ones it may give way to.
    private val later: List<AudioInput> = inputs.drop(inputs.indexOf(firstInput) + 1)
    private var nextLater = 0

    // Inputs silent from their first sample this session, remembered once a later one hears.
    private val silentInputs = ArrayList<AudioInput>(inputs.size)

    private var mic: M? = first
    private var reader = ChunkReader(first, sampleRate, pause)

    /** The input recording now. */
    var input: AudioInput = firstInput
        private set

    /** Blocks until [chunk] is full, from the next input if this one turns out to record nothing. */
    fun fill(chunk: FloatArray) {
        while (true) {
            try {
                reader.fill(chunk)
            } catch (e: AudioRecorderException.MicrophoneBusy) {
                if (!reader.silentFromStart || !giveWay()) throw e
                continue
            }
            if (silentInputs.isNotEmpty() && reader.heardRoom) {
                for (silent in silentInputs) skipped.skip(silent)
                silentInputs.clear()
            }
            return
        }
    }

    /** Stops and lets go of the input recording now, if any. */
    fun close() {
        mic?.let(release)
        mic = null
    }

    /** Closes the silent input and opens the next one that opens; false when none does. */
    private fun giveWay(): Boolean {
        silentInputs += input
        close()
        while (nextLater < later.size) {
            val candidate = later[nextLater++]
            val opened = open(candidate) ?: continue
            mic = opened
            input = candidate
            reader = ChunkReader(opened, sampleRate, pause)
            return true
        }
        return false
    }
}
