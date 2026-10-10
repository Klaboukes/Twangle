package com.intune.core.audio

/**
 * Format of the audio an [AudioRecorder] delivers: mono float samples in −1..1.
 *
 * @param sampleRate samples per second, in Hz.
 * @param chunkSize samples per emitted chunk. 1024 matches the DSP hop, so one chunk ≈ one pitch
 *   estimate (~47 per second at 48 kHz).
 */
data class AudioConfig(
    val sampleRate: Int = DEFAULT_SAMPLE_RATE,
    val chunkSize: Int = DEFAULT_CHUNK_SIZE,
) {
    init {
        require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }
        require(chunkSize > 0) { "chunkSize must be positive, was $chunkSize" }
    }

    companion object {
        /** The native rate of most phones, so Android doesn't need to resample. */
        const val DEFAULT_SAMPLE_RATE = 48_000
        const val DEFAULT_CHUNK_SIZE = 1024
    }
}
