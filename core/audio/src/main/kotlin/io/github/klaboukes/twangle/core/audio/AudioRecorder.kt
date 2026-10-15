package io.github.klaboukes.twangle.core.audio

import kotlinx.coroutines.flow.Flow

/** Live microphone audio as a stream of fixed-size chunks. */
interface AudioRecorder {

    /**
     * Sample rate and chunk size of the audio from [chunks]. It may change when the microphone
     * opens (a device that refuses its usual rate records at another one), so read it again once
     * the first chunk has come.
     */
    val config: AudioConfig

    /** The microphone input in use while [chunks] is being collected, if known. For diagnostics. */
    val activeInput: AudioInput? get() = null

    /**
     * Cold flow of mono float chunks of [AudioConfig.chunkSize] samples in −1..1. The microphone
     * opens when collection starts and is released when it stops.
     *
     * Implementations may reuse a small pool of arrays so the ~47 chunks per second don't create
     * garbage. **A chunk is only valid until the collector asks for the next one**: copy what you
     * need (e.g. `FrameAssembler.push`) inside `collect`, and don't `buffer()` the chunks
     * themselves downstream.
     */
    fun chunks(): Flow<FloatArray>
}
