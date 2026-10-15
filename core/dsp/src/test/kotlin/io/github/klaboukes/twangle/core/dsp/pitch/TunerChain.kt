package io.github.klaboukes.twangle.core.dsp.pitch

import io.github.klaboukes.twangle.core.dsp.level.NoiseFloorTracker
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.smoothing.PitchSmoother

/**
 * The per-frame DSP chain as the app runs it (frame assembly → gate → sustain check → YIN →
 * smoother, plus the noise floor and clarity), for tests that feed whole recordings through it. Mirrors
 * `PitchPipeline` plus `TunerInterpreter`.
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
        /** Background level from the [NoiseFloorTracker], NaN until known. */
        val floorDbfs: Float,
        /** How far the note rises above the background, NaN without a note. */
        val snrDb: Float,
        /** [Clarity.of] the detector's confidence. */
        val clarity: Float,
        /** False when [SustainCheck] found the frame's sound had died away by its end. */
        val sustained: Boolean,
    ) {
        val isShown: Boolean get() = shownHz > 0f
    }

    /** Feeds [stream] in chunks of [chunkSize] samples and returns one [Frame] per hop. */
    fun run(stream: FloatArray, chunkSize: Int = settings.hopSize): List<Frame> {
        val assembler = settings.newFrameAssembler()
        val gate = SignalGate()
        val sustain = settings.newSustainCheck()
        val detector = settings.newPitchDetector()
        val smoother = PitchSmoother()
        val noise = settings.newNoiseFloorTracker()
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
                val sustained = !open || sustain.isSustained(window, floorDbfs = noise.floorDbfs)
                val pitch = if (open && sustained) detector.detect(window) else PitchResult.NONE
                if (pitch.isPitched) smoother.update(mapper.midiOf(pitch.frequencyHz), pitch.confidence, gate.isAboveThreshold) else smoother.noReading(sustained)
                val shown = if (smoother.hasReading) mapper.frequencyOf(smoother.midi) else 0f
                noise.update(gate.levelDbfs, pitch.isPitched)
                frames += Frame(
                    position, open, gate.levelDbfs, pitch.frequencyHz, pitch.confidence, shown,
                    noise.floorDbfs, noise.snrDb, Clarity.of(pitch.confidence), sustained,
                )
            }
            start = end
        }
        return frames
    }
}
