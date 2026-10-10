package io.github.klaboukes.twangle.core.dsp.buffer

/**
 * Turns a stream of audio chunks of any size into overlapping analysis windows.
 *
 * Samples are written into a ring buffer of [windowSize]. The first frame is ready once
 * [windowSize] samples have arrived; after that, a new frame is ready every [hopSize] samples,
 * so consecutive frames overlap by `windowSize − hopSize` samples. Nothing allocates after
 * construction.
 *
 * Typical loop for one incoming chunk:
 * ```
 * var pos = 0
 * while (pos < chunk.size) {
 *     pos += assembler.push(chunk, pos, chunk.size - pos)
 *     if (assembler.isFrameReady) {
 *         assembler.readFrame(window)
 *         analyse(window)
 *     }
 * }
 * ```
 */
class FrameAssembler(
    val windowSize: Int = DEFAULT_WINDOW_SIZE,
    val hopSize: Int = DEFAULT_HOP_SIZE,
) {

    init {
        require(windowSize > 0) { "windowSize must be positive, was $windowSize" }
        require(hopSize in 1..windowSize) { "hopSize must be in 1..$windowSize, was $hopSize" }
    }

    private val ring = FloatArray(windowSize)

    // Next write position in [ring]. Once the ring is full it also points at the oldest sample.
    private var writePos = 0

    // Samples still needed before the next frame is ready: a full window at first, then one hop.
    private var samplesUntilFrame = windowSize

    /** True when a complete frame is waiting to be read with [readFrame]. */
    var isFrameReady: Boolean = false
        private set

    /**
     * Copies samples from [samples] into the ring buffer, starting at [offset] and taking at most
     * [length] of them. Stops early as soon as a frame becomes ready, so no frame is skipped.
     *
     * @return the number of samples consumed (0 if a frame is already waiting to be read).
     */
    fun push(samples: FloatArray, offset: Int = 0, length: Int = samples.size - offset): Int {
        require(offset >= 0 && length >= 0 && offset + length <= samples.size) {
            "Range $offset+$length is outside an array of ${samples.size}"
        }
        if (isFrameReady) return 0

        val count = minOf(length, samplesUntilFrame)
        var copied = 0
        while (copied < count) {
            val chunk = minOf(count - copied, windowSize - writePos)
            System.arraycopy(samples, offset + copied, ring, writePos, chunk)
            writePos = (writePos + chunk) % windowSize
            copied += chunk
        }

        samplesUntilFrame -= count
        if (samplesUntilFrame == 0) {
            isFrameReady = true
            samplesUntilFrame = hopSize
        }
        return count
    }

    /**
     * Copies the ready frame into [out] (oldest sample first) and clears [isFrameReady].
     * [out] must hold at least [windowSize] samples.
     */
    fun readFrame(out: FloatArray) {
        check(isFrameReady) { "No frame is ready" }
        require(out.size >= windowSize) { "Output must hold $windowSize samples, was ${out.size}" }
        // The ring is full here, so writePos is the oldest sample: copy writePos..end, then 0 until writePos.
        val tail = windowSize - writePos
        System.arraycopy(ring, writePos, out, 0, tail)
        System.arraycopy(ring, 0, out, tail, writePos)
        isFrameReady = false
    }

    /** Drops all buffered audio, e.g. after the microphone restarts. */
    fun reset() {
        ring.fill(0f)
        writePos = 0
        samplesUntilFrame = windowSize
        isFrameReady = false
    }

    companion object {
        /** 4096 samples ≈ 85 ms at 48 kHz: about 7 periods of low E (82.4 Hz). */
        const val DEFAULT_WINDOW_SIZE = 4096

        /** 1024 samples ≈ 21 ms at 48 kHz: about 47 frames per second. */
        const val DEFAULT_HOP_SIZE = 1024
    }
}
