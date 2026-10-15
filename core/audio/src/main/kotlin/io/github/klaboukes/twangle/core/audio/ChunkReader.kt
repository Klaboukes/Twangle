package io.github.klaboukes.twangle.core.audio

/** An open microphone: `AudioRecord` in the app, a fake in tests. */
internal interface MicInput {

    /**
     * Like `AudioRecord.read(…, READ_BLOCKING)`: reads up to [count] samples into [buffer] from
     * [offset] and returns how many, 0 if none came, or a negative error code.
     */
    fun read(buffer: FloatArray, offset: Int, count: Int): Int

    /**
     * Whether Android is feeding this recording silence because an app with priority took the
     * microphone (Android 10+: a call, an assistant, another recorder in front). Read for every
     * chunk, so it must be cheap and not allocate.
     */
    val isSilenced: Boolean
}

/**
 * Fills the recorder's chunks from a [MicInput] and decides when the microphone is no longer
 * really listening, so the tuner says so instead of showing "Play a note" forever:
 * - a negative read ends with [AudioRecorderException.ReadFailed] carrying its code;
 * - a run of empty reads ([transferFully]) with [AudioRecorderException.ReadFailed] (code 0);
 * - [MicInput.isSilenced] with [AudioRecorderException.MicrophoneBusy];
 * - nothing but exact zeros for [SILENT_SECONDS] with [AudioRecorderException.MicrophoneBusy] as
 *   well: a muted or blocked microphone (the privacy switch, another app muting it) delivers
 *   digital silence, while a working one always hears at least a whisper of noise, even in a
 *   quiet room. The wait also covers a microphone that starts with a few silent chunks.
 *
 * Allocates nothing per chunk.
 */
internal class ChunkReader(
    private val input: MicInput,
    sampleRate: Int,
    private val pause: () -> Unit = ::pauseAfterEmptyStep,
) {
    private val silentLimit: Long = (sampleRate * SILENT_SECONDS).toLong()
    private var silentSamples = 0L

    /** Blocks until [chunk] is full, then checks that the microphone is still really listening. */
    fun fill(chunk: FloatArray) {
        transferFully(
            count = chunk.size,
            pause = pause,
            onError = { throw AudioRecorderException.ReadFailed(it) },
            onStalled = {
                throw AudioRecorderException.ReadFailed(0, "AudioRecord.read returned nothing $MAX_EMPTY_STEPS times in a row")
            },
        ) { done -> input.read(chunk, done, chunk.size - done) }
        if (input.isSilenced) throw AudioRecorderException.MicrophoneBusy("Android silenced the microphone for another app")
        silentSamples = if (isDigitalSilence(chunk)) silentSamples + chunk.size else 0L
        if (silentSamples >= silentLimit) {
            throw AudioRecorderException.MicrophoneBusy("The microphone delivered only zeros for $SILENT_SECONDS s (muted or blocked)")
        }
    }

    // A live microphone's first sample is almost never exactly 0, so this usually stops at once.
    private fun isDigitalSilence(chunk: FloatArray): Boolean {
        for (sample in chunk) if (sample != 0f) return false
        return true
    }

    companion object {
        /** How long only exact zeros may come before the microphone counts as muted. */
        const val SILENT_SECONDS = 2f
    }
}
