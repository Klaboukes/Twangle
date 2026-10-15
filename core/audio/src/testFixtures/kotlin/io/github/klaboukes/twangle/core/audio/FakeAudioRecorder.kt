package io.github.klaboukes.twangle.core.audio

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

/**
 * [AudioRecorder] for tests: plays [signal] back as chunks instead of using a microphone.
 *
 * - The signal is cut into [AudioConfig.chunkSize] chunks; a final partial chunk is padded with
 *   silence. With [loop] it repeats forever, otherwise the flow ends after the last chunk.
 * - Chunks are paced like a real microphone (one chunk duration per chunk) using `delay`, which is
 *   instant under `runTest` virtual time.
 * - [failure], if set, ends the flow after the signal (immediately if the signal is empty), the way
 *   [AndroidAudioRecorder] reports errors.
 * - Like the real recorder it reuses one array, so code that holds on to chunks breaks in tests too.
 * - [openedConfig], if set, becomes [config] when the "mic" opens, like a real microphone that
 *   refuses its usual rate and opens at another one; [signal] is then at that rate.
 * - [isRecording] and [startCount] let tests check that the "mic" is only open while needed.
 *
 * Use [signal] from the `:core:dsp` test fixtures (`TestSignals`) to play synthetic notes.
 */
class FakeAudioRecorder(
    var signal: FloatArray = FloatArray(0),
    config: AudioConfig = AudioConfig(),
    var loop: Boolean = false,
    var failure: AudioRecorderException? = null,
    private val openedConfig: AudioConfig? = null,
) : AudioRecorder {

    override var config: AudioConfig = config
        private set

    private val recording = MutableStateFlow(false)

    /** True while a collector is receiving chunks. */
    val isRecording: StateFlow<Boolean> = recording.asStateFlow()

    /** How many times collection has started, i.e. how often the "mic" was opened. */
    var startCount: Int = 0
        private set

    // At least 1 ms, so a looping fake always suspends and can be cancelled.
    private val chunkMillis: Long get() = (config.chunkSize * 1000L / config.sampleRate).coerceAtLeast(1)

    override fun chunks(): Flow<FloatArray> = flow {
        startCount++
        openedConfig?.let { config = it }
        recording.value = true
        try {
            val chunk = FloatArray(config.chunkSize)
            val source = signal
            do {
                var pos = 0
                while (pos < source.size) {
                    val count = minOf(chunk.size, source.size - pos)
                    System.arraycopy(source, pos, chunk, 0, count)
                    chunk.fill(0f, count, chunk.size)
                    pos += count
                    delay(chunkMillis)
                    emit(chunk)
                }
            } while (loop && source.isNotEmpty())
            failure?.let { throw it }
        } finally {
            recording.value = false
        }
    }

    companion object {
        /** A recorder that keeps delivering silence until cancelled. */
        fun silence(config: AudioConfig = AudioConfig()): FakeAudioRecorder =
            FakeAudioRecorder(FloatArray(config.chunkSize), config, loop = true)
    }
}
