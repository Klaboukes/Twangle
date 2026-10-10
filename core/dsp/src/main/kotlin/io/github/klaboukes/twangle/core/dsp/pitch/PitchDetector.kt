package io.github.klaboukes.twangle.core.dsp.pitch

/**
 * One pitch estimate: a frequency and how sure the detector is about it, or [NONE] when the
 * frame has no clear pitch.
 *
 * Both floats are packed into a single Long, so returning a result from the per-frame loop does
 * not allocate. Don't make it nullable (`PitchResult?`); that would box it. Use [NONE] instead.
 */
@JvmInline
value class PitchResult private constructor(private val bits: Long) {

    /** Estimated fundamental frequency in Hz, or 0 for [NONE]. */
    val frequencyHz: Float get() = Float.fromBits((bits ushr 32).toInt())

    /** 0..1, where 1 means a perfectly periodic frame. 0 for [NONE]. */
    val confidence: Float get() = Float.fromBits(bits.toInt())

    /** True if a pitch was found. */
    val isPitched: Boolean get() = frequencyHz > 0f

    override fun toString(): String = if (isPitched) "PitchResult($frequencyHz Hz, confidence $confidence)" else "NONE"

    companion object {
        /** No pitch found in the frame. */
        val NONE = of(0f, 0f)

        fun of(frequencyHz: Float, confidence: Float): PitchResult =
            PitchResult((frequencyHz.toRawBits().toLong() shl 32) or (confidence.toRawBits().toLong() and 0xFFFF_FFFFL))
    }
}

/** Finds the fundamental frequency of a mono audio frame. Implementations must not allocate per call. */
interface PitchDetector {

    /** Sample rate of the frames passed to [detect], in Hz. */
    val sampleRate: Int

    /** Number of samples [detect] reads from the start of each frame. */
    val windowSize: Int

    /** Estimates the pitch of the first [windowSize] samples of [frame] (values in −1..1). */
    fun detect(frame: FloatArray): PitchResult
}
