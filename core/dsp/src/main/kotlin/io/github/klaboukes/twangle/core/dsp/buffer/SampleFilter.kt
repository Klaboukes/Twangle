package io.github.klaboukes.twangle.core.dsp.buffer

/**
 * A filter that runs over a continuous stream of samples, in place, keeping its state from one call
 * to the next (see [FrameAssembler.filter]). Implementations must not allocate in [process].
 */
interface SampleFilter {
    /** Filters [length] samples of [samples] from [offset], in place, continuing from the last call. */
    fun process(samples: FloatArray, offset: Int, length: Int)

    /** Forgets the stream so far, as if the next sample were the first. */
    fun reset()
}
