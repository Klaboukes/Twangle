package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.audio.AudioRecorderException
import io.github.klaboukes.twangle.core.dsp.buffer.FrameAssembler
import io.github.klaboukes.twangle.core.dsp.level.NoiseFloorTracker
import io.github.klaboukes.twangle.core.dsp.pitch.AnalysisSettings
import io.github.klaboukes.twangle.core.dsp.pitch.Clarity
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
    /** The room's background level in dBFS ([NoiseFloorTracker]), NaN until a note-free frame was heard. */
    val noiseFloorDbfs: Float = Float.NaN,
    /** How far the note rises above [noiseFloorDbfs], in dB; NaN when there is no pitch or no floor yet. */
    val snrDb: Float = Float.NaN,
    /** 0..1, how clean the pitch is ([Clarity.of] the confidence); 0 when there is no pitch. */
    val clarity: Float = 0f,
    /** Peak of the frame before DC removal, in dBFS ([SignalGate.peakDbfs]); near 0 the input clips. */
    val peakDbfs: Float = SignalGate.SILENCE_DBFS,
) {
    val isPitched: Boolean get() = frequencyHz > 0f
}

/**
 * Microphone → [FrameAssembler] → [SignalGate] → [YinPitchDetector], one [PitchReading] per hop
 * (~47 per second), with the room's noise floor and the note's SNR ([NoiseFloorTracker]) and
 * clarity ([Clarity]). See ARCHITECTURE §3.
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
        val noise = settings.newNoiseFloorTracker()
        val frame = FloatArray(assembler.windowSize)

        recorder.chunks().collect { chunk ->
            var pos = 0
            while (pos < chunk.size) {
                pos += assembler.push(chunk, pos, chunk.size - pos)
                if (assembler.isFrameReady) {
                    assembler.readFrame(frame)
                    val open = gate.process(frame)
                    val pitch = if (open) detector.detect(frame) else PitchResult.NONE
                    noise.update(gate.levelDbfs, noteSounding = pitch.isPitched)
                    emit(
                        PitchReading(
                            pitch.frequencyHz, pitch.confidence, gate.levelDbfs, open,
                            noise.floorDbfs, noise.snrDb, Clarity.of(pitch.confidence), gate.peakDbfs,
                        ),
                    )
                }
            }
        }
    }.flowOn(dspDispatcher)

    private companion object {
        val SUPPORTED_RATES = AnalysisSettings.MIN_SAMPLE_RATE..AnalysisSettings.MAX_SAMPLE_RATE
    }
}
