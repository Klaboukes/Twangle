package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.audio.AudioRecorderException
import io.github.klaboukes.twangle.core.dsp.buffer.FrameAssembler
import io.github.klaboukes.twangle.core.dsp.pitch.AnalysisSettings
import io.github.klaboukes.twangle.core.dsp.pitch.PitchResult
import io.github.klaboukes.twangle.core.dsp.pitch.SignalGate
import io.github.klaboukes.twangle.core.dsp.pitch.YinPitchDetector
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** One analysis frame's raw result, before smoothing. */
data class PitchReading(
    /** Detected fundamental in Hz, or 0 when there is no pitch (gate closed or no clear period). */
    val frequencyHz: Float,
    /** 0..1, how periodic the frame was. 0 when there is no pitch. */
    val confidence: Float,
    /** RMS level of the frame after DC removal, in dBFS. */
    val levelDbfs: Float,
    /** Whether the frame was loud enough to analyse (see [SignalGate]). */
    val gateOpen: Boolean,
) {
    val isPitched: Boolean get() = frequencyHz > 0f
}

/**
 * Microphone → [FrameAssembler] → [SignalGate] → [YinPitchDetector], one [PitchReading] per hop
 * (~47 per second). See ARCHITECTURE §3.
 *
 * Frame sizes follow the recorder's sample rate ([AnalysisSettings.forSampleRate]), so 44.1 kHz,
 * 16 kHz or 8 kHz input gives the same ≈ 85 ms window and ≈ 21 ms hop as 48 kHz. A rate outside
 * [AnalysisSettings.MIN_SAMPLE_RATE]..[AnalysisSettings.MAX_SAMPLE_RATE] ends the flow with
 * [AudioRecorderException.InitializationFailed].
 *
 * The DSP objects and the frame buffer are created once per collection, so the per-frame loop
 * doesn't allocate; only the small immutable reading handed to the UI is new each frame. Chunks
 * are copied into the assembler inside `collect`, as [AudioRecorder.chunks] requires. DSP runs on
 * [dspDispatcher] (`Dispatchers.Default` in the app).
 */
class PitchPipeline(
    private val recorder: AudioRecorder,
    private val dspDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val sampleRate: Int get() = recorder.config.sampleRate

    /** Time between two readings: one hop at the recorder's sample rate (~21 ms). */
    val frameSeconds: Float get() = AnalysisSettings.forSampleRate(sampleRate.coerceIn(SUPPORTED_RATES)).hopSeconds

    fun readings(): Flow<PitchReading> = flow {
        val rate = sampleRate
        if (rate !in SUPPORTED_RATES) {
            throw AudioRecorderException.InitializationFailed("Unsupported sample rate $rate Hz")
        }
        val settings = AnalysisSettings.forSampleRate(rate)
        val assembler = settings.newFrameAssembler()
        val gate = SignalGate()
        val detector = settings.newPitchDetector()
        val frame = FloatArray(assembler.windowSize)

        recorder.chunks().collect { chunk ->
            var pos = 0
            while (pos < chunk.size) {
                pos += assembler.push(chunk, pos, chunk.size - pos)
                if (assembler.isFrameReady) {
                    assembler.readFrame(frame)
                    val open = gate.process(frame)
                    val pitch = if (open) detector.detect(frame) else PitchResult.NONE
                    emit(PitchReading(pitch.frequencyHz, pitch.confidence, gate.levelDbfs, open))
                }
            }
        }
    }.flowOn(dspDispatcher)

    private companion object {
        val SUPPORTED_RATES = AnalysisSettings.MIN_SAMPLE_RATE..AnalysisSettings.MAX_SAMPLE_RATE
    }
}
