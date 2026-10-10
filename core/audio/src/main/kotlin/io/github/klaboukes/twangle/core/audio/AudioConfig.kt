package io.github.klaboukes.twangle.core.audio

/**
 * Format of the audio an [AudioRecorder] delivers: mono float samples in −1..1.
 *
 * @param sampleRate samples per second, in Hz.
 * @param chunkSize samples per emitted chunk. 1024 at 48 kHz matches the DSP hop, so one chunk ≈
 *   one pitch estimate (~47 per second). [forSampleRate] keeps that at other rates.
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

        /**
         * Config for [sampleRate] whose chunks last as long as [DEFAULT_CHUNK_SIZE] samples at
         * 48 kHz (about 21 ms, one DSP hop), e.g. 941 samples at 44.1 kHz or 171 at 8 kHz.
         */
        fun forSampleRate(sampleRate: Int): AudioConfig = AudioConfig(
            sampleRate = sampleRate,
            chunkSize = maxOf(1, Math.round(DEFAULT_CHUNK_SIZE.toDouble() * sampleRate / DEFAULT_SAMPLE_RATE).toInt()),
        )
    }
}
