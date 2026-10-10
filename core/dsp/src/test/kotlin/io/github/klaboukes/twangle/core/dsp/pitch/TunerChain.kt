package io.github.klaboukes.twangle.core.dsp.pitch

import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.smoothing.PitchSmoother

/**
 * The per-frame DSP chain as the app runs it (frame assembly → gate → YIN → smoother), for tests
 * that feed whole recordings through it. Mirrors `PitchPipeline` plus `TunerInterpreter`.
 */
internal class TunerChain(
    val settings: AnalysisSettings = AnalysisSettings.forSampleRate(YinPitchDetector.DEFAULT_SAMPLE_RATE),
    private val mapper: NoteMapper = NoteMapper(),
) {
    /** What the chain produced for one frame. */
    class Frame(
        /** Sample index just past the end of this frame's window. */
        val endSample: Int,
        val gateOpen: Boolean,
        val levelDbfs: Float,
        /** Raw detector output in Hz, or 0. */
        val rawHz: Float,
        val confidence: Float,
        /** Smoothed reading in Hz, or 0 when nothing is shown. */
        val shownHz: Float,
    ) {
        val isShown: Boolean get() = shownHz > 0f
    }

    /** Feeds [stream] in chunks of [chunkSize] samples and returns one [Frame] per hop. */
    fun run(stream: FloatArray, chunkSize: Int = settings.hopSize): List<Frame> {
        val assembler = settings.newFrameAssembler()
        val gate = SignalGate()
        val detector = settings.newPitchDetector()
        val smoother = PitchSmoother()
        val window = FloatArray(settings.windowSize)
        val frames = ArrayList<Frame>()
        var position = 0
        var start = 0
        while (start < stream.size) {
            val end = minOf(start + chunkSize, stream.size)
            var pos = start
            while (pos < end) {
                val consumed = assembler.push(stream, pos, end - pos)
                pos += consumed
                position += consumed
                if (!assembler.isFrameReady) continue
                assembler.readFrame(window)
                val open = gate.process(window)
                val pitch = if (open) detector.detect(window) else PitchResult.NONE
                if (pitch.isPitched) smoother.update(mapper.midiOf(pitch.frequencyHz), pitch.confidence) else smoother.noReading()
                val shown = if (smoother.hasReading) mapper.frequencyOf(smoother.midi) else 0f
                frames += Frame(position, open, gate.levelDbfs, pitch.frequencyHz, pitch.confidence, shown)
            }
            start = end
        }
        return frames
    }
}
