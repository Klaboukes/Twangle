package com.intune.feature.tuner

import com.intune.core.audio.AudioRecorder
import com.intune.core.dsp.buffer.FrameAssembler
import com.intune.core.dsp.pitch.PitchResult
import com.intune.core.dsp.pitch.SignalGate
import com.intune.core.dsp.pitch.YinPitchDetector
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
 * The DSP objects and the frame buffer are created once per collection, so the per-frame loop
 * doesn't allocate; only the small immutable reading handed to the UI is new each frame. Chunks
 * are copied into the assembler inside `collect`, as [AudioRecorder.chunks] requires. DSP runs on
 * [dspDispatcher] (`Dispatchers.Default` in the app).
 */
class PitchPipeline(
    private val recorder: AudioRecorder,
    private val dspDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    fun readings(): Flow<PitchReading> = flow {
        val assembler = FrameAssembler()
        val gate = SignalGate()
        val detector = YinPitchDetector(sampleRate = recorder.config.sampleRate, windowSize = assembler.windowSize)
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
}
