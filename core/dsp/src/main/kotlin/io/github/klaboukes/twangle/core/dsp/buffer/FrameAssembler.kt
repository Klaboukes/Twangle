package io.github.klaboukes.twangle.core.dsp.buffer

/**
 * Turns a stream of audio chunks of any size into overlapping analysis windows.
 *
 * Samples are written into a ring buffer of [capacity] (by default [windowSize]). The first frame
 * is ready once [windowSize] samples have arrived; after that, a new frame is ready every [hopSize]
 * samples, so consecutive frames overlap by `windowSize − hopSize` samples. A larger [capacity]
 * lets the caller read a longer window at the same moments (`readFrame(out, length)`, once
 * [available] reaches that length), e.g. the low-note window, and switch between the two without
 * losing audio. While a [filter] is set, samples pass through it on their way into the ring (the
 * low-note window's mains hum canceller). Nothing allocates after construction.
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
    /** How many of the latest samples are kept: at least [windowSize]. */
    val capacity: Int = windowSize,
) {

    init {
        require(windowSize > 0) { "windowSize must be positive, was $windowSize" }
        require(hopSize in 1..windowSize) { "hopSize must be in 1..$windowSize, was $hopSize" }
        require(capacity >= windowSize) { "capacity must be at least windowSize $windowSize, was $capacity" }
    }

    private val ring = FloatArray(capacity)

    // Next write position in [ring]. Once the ring is full it also points at the oldest sample.
    private var writePos = 0

    /** Samples received since the start or [reset], up to [capacity]: the longest window [readFrame] can fill. */
    var available: Int = 0
        private set

    /**
     * Runs over every sample pushed while it is set, in place in the ring, so frames hold its output;
     * the caller's chunks are left as they are. Setting another filter (not the same one again)
     * [resets][SampleFilter.reset] it, since it missed the audio in between.
     */
    var filter: SampleFilter? = null
        set(value) {
            if (value !== field) value?.reset()
            field = value
        }

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
            val chunk = minOf(count - copied, capacity - writePos)
            System.arraycopy(samples, offset + copied, ring, writePos, chunk)
            filter?.process(ring, writePos, chunk)
            writePos = (writePos + chunk) % capacity
            copied += chunk
        }
        available = minOf(capacity, available + count)

        samplesUntilFrame -= count
        if (samplesUntilFrame == 0) {
            isFrameReady = true
            samplesUntilFrame = hopSize
        }
        return count
    }

    /**
     * Copies the latest [length] samples (by default [windowSize], at most [capacity]) of the ready
     * frame into [out], oldest first, and clears [isFrameReady]. [out] must hold at least [length]
     * samples. Samples older than [available] read as silence.
     */
    fun readFrame(out: FloatArray, length: Int = windowSize) {
        check(isFrameReady) { "No frame is ready" }
        require(length in 1..capacity) { "length must be in 1..$capacity, was $length" }
        copyLatest(out, length)
        isFrameReady = false
    }

    /**
     * Copies the latest [length] samples (at most [capacity]) into [out], oldest first, at any
     * time and without touching [isFrameReady]: e.g. a long strum frame read right after the
     * hop's own frame. Samples older than [available] read as silence.
     */
    fun copyLatest(out: FloatArray, length: Int) {
        require(length in 1..capacity) { "length must be in 1..$capacity, was $length" }
        require(out.size >= length) { "Output must hold $length samples, was ${out.size}" }
        // The newest sample sits just before writePos: copy start..end of the ring, then the rest from 0.
        val start = (writePos - length + capacity) % capacity
        val first = minOf(length, capacity - start)
        System.arraycopy(ring, start, out, 0, first)
        System.arraycopy(ring, 0, out, first, length - first)
    }

    /** Passes on the ready frame without reading it, e.g. while a longer window is still filling. */
    fun skipFrame() {
        check(isFrameReady) { "No frame is ready" }
        isFrameReady = false
    }

    /** Drops all buffered audio, e.g. after the microphone restarts. */
    fun reset() {
        ring.fill(0f)
        writePos = 0
        available = 0
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
