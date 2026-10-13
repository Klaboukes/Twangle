package io.github.klaboukes.twangle.core.dsp.pitch

import io.github.klaboukes.twangle.core.dsp.buffer.FrameAssembler
import io.github.klaboukes.twangle.core.dsp.level.NoiseFloorTracker
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Frame sizes and detector limits for one sample rate, so the pipeline behaves the same in time
 * (≈ 85 ms window, ≈ 21 ms hop) whatever rate the microphone delivers.
 *
 * At 48 kHz this is exactly the tuned default: a 4096-sample window, a 1024-sample hop, 62–1500 Hz
 * and a 4 kHz low-pass. Other rates scale the window and hop with the rate, so the detection range
 * and the smoother's timing (counted in frames) stay the same.
 *
 * Below [UPSAMPLE_BELOW_RATE] a period spans too few samples for ±1 cent, so frames are first
 * upsampled by [upsampleFactor] (see [UpsamplingPitchDetector]) and YIN runs at [analysisRate].
 * There the low-pass also drops to 0.4 × the input rate, below the input's Nyquist frequency.
 * At 8 kHz, e.g. a Bluetooth headset's narrowband mic, that is a 683-sample window, a 171-sample
 * hop, ×4 upsampling to 32 kHz and a 3.2 kHz low-pass.
 *
 * [range] picks how low it listens: [PitchRange.LOW] doubles the window (8192 samples at 48 kHz)
 * and lowers the floor to 28 Hz for bass notes, with the same hop.
 */
data class AnalysisSettings(
    val sampleRate: Int,
    val windowSize: Int,
    val hopSize: Int,
    val upsampleFactor: Int,
    val minFrequencyHz: Float,
    val maxFrequencyHz: Float,
    val lowPassHz: Float,
    val range: PitchRange = PitchRange.STANDARD,
) {

    /** Rate YIN runs at: [sampleRate] × [upsampleFactor]. */
    val analysisRate: Int get() = sampleRate * upsampleFactor

    /** Time between two frames, in seconds. */
    val hopSeconds: Float get() = hopSize.toFloat() / sampleRate

    /** A [FrameAssembler] that cuts audio into frames of these sizes. */
    fun newFrameAssembler(): FrameAssembler = FrameAssembler(windowSize, hopSize)

    /** A [NoiseFloorTracker] for frames this far apart ([hopSeconds]). */
    fun newNoiseFloorTracker(): NoiseFloorTracker = NoiseFloorTracker(hopSeconds)

    /** A pitch detector for frames of [windowSize] samples at [sampleRate]: YIN, upsampled if needed. */
    fun newPitchDetector(): PitchDetector {
        if (upsampleFactor == 1) return yin(windowSize)
        val halfTaps = UPSAMPLER_HALF_TAPS
        return UpsamplingPitchDetector(upsampleFactor, yin((windowSize - 2 * halfTaps) * upsampleFactor), halfTaps)
    }

    private fun yin(window: Int) = YinPitchDetector(
        sampleRate = analysisRate,
        windowSize = window,
        minFrequencyHz = minFrequencyHz,
        maxFrequencyHz = maxFrequencyHz,
        lowPassHz = lowPassHz,
    )

    companion object {
        /** Lowest supported rate: Bluetooth narrowband (CVSD) audio. */
        const val MIN_SAMPLE_RATE = 8_000

        /** Highest supported rate. Above it the direct difference function would cost too much. */
        const val MAX_SAMPLE_RATE = 96_000

        /** Rates below this are upsampled to at least this rate before YIN. */
        const val UPSAMPLE_BELOW_RATE = 32_000

        private const val REFERENCE_RATE = YinPitchDetector.DEFAULT_SAMPLE_RATE
        private const val UPSAMPLER_HALF_TAPS = 16

        // The low-pass stays at 4 kHz while it can. At low input rates it sits at 0.4 × the rate,
        // under the upsampler's cut-off (0.45 × the rate), so no interpolation images get through.
        // At 8 kHz that is 3.2 kHz, still more than twice the 1500 Hz top note.
        private const val LOW_PASS_FRACTION_OF_RATE = 0.4f

        /**
         * Settings for [sampleRate] Hz, which must be in [MIN_SAMPLE_RATE]..[MAX_SAMPLE_RATE], listening
         * as low as [range] says.
         */
        fun forSampleRate(sampleRate: Int, range: PitchRange = PitchRange.STANDARD): AnalysisSettings {
            require(sampleRate in MIN_SAMPLE_RATE..MAX_SAMPLE_RATE) {
                "sampleRate must be in $MIN_SAMPLE_RATE..$MAX_SAMPLE_RATE, was $sampleRate"
            }
            val scale = sampleRate.toDouble() / REFERENCE_RATE
            val factor = if (sampleRate >= UPSAMPLE_BELOW_RATE) 1 else ceil(UPSAMPLE_BELOW_RATE.toDouble() / sampleRate).toInt()
            return AnalysisSettings(
                sampleRate = sampleRate,
                windowSize = (FrameAssembler.DEFAULT_WINDOW_SIZE * range.windowMultiple * scale).roundToInt(),
                hopSize = (FrameAssembler.DEFAULT_HOP_SIZE * scale).roundToInt(),
                upsampleFactor = factor,
                minFrequencyHz = range.minFrequencyHz,
                maxFrequencyHz = YinPitchDetector.DEFAULT_MAX_FREQUENCY_HZ,
                lowPassHz = minOf(YinPitchDetector.DEFAULT_LOW_PASS_HZ, sampleRate * LOW_PASS_FRACTION_OF_RATE),
                range = range,
            )
        }
    }
}
