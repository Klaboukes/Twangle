package io.github.klaboukes.twangle.core.audio

/**
 * How the microphone hands over its samples. [FLOAT] is what every recorder asks for first: floats
 * in −1..1, as the pipelines want them. [PCM_16BIT] is the fallback for a phone that refuses float
 * recording at every rate and input (some Android 8–9 phones): 16-bit samples, which [Pcm16Reads]
 * turns into floats in −1..1, so [AudioConfig] and the pipelines see no difference. 16 bits keep
 * about 96 dB between full scale and one step, far more than a quiet room's noise needs.
 */
internal enum class SampleFormat(val bytesPerSample: Int) {
    FLOAT(Float.SIZE_BYTES),
    PCM_16BIT(Short.SIZE_BYTES),
}

/** Reads up to [count] 16-bit samples into [buffer] from [offset], like `AudioRecord.read(short[], …)`. */
internal fun interface ShortReads {
    fun read(buffer: ShortArray, offset: Int, count: Int): Int
}

/**
 * Reads 16-bit samples into one [ShortArray] of [capacity], made once per opened recording and
 * reused, and turns them into floats in the caller's buffer ([toFloats]), so a 16-bit recording
 * allocates nothing per chunk either (hard rule 2).
 */
internal class Pcm16Reads(capacity: Int) {

    private val shorts = ShortArray(capacity)

    /**
     * Like [MicInput.read]: reads up to [count] samples (at most the capacity; the caller's loop
     * reads the rest) from [source] and writes them into [buffer] from [offset] as floats. Returns
     * how many, 0 if none came, or the source's negative error code unchanged.
     */
    fun read(buffer: FloatArray, offset: Int, count: Int, source: ShortReads): Int {
        val read = source.read(shorts, 0, minOf(count, shorts.size))
        if (read > 0) toFloats(shorts, 0, buffer, offset, read)
        return read
    }

    companion object {
        // 2^15: Short.MIN_VALUE becomes exactly −1, Short.MAX_VALUE just under 1, and 0 stays
        // exactly 0, so ChunkReader's check for a microphone recording only zeros still holds.
        private const val SCALE = 1f / 32_768f

        /** Writes [count] samples of [src] from [srcOffset] into [dst] from [dstOffset], as floats in −1..1. */
        fun toFloats(src: ShortArray, srcOffset: Int, dst: FloatArray, dstOffset: Int, count: Int) {
            for (i in 0 until count) dst[dstOffset + i] = src[srcOffset + i] * SCALE
        }
    }
}
