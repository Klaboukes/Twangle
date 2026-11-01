package io.github.klaboukes.twangle.core.dsp.pitch

import io.github.klaboukes.twangle.core.dsp.level.NoiseFloorTracker
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.smoothing.PitchSmoother

/**
 * The tuner as the app runs it, for tests that feed whole recordings through it: the app's own frame
 * chain ([PitchChain]: frame assembly → gate → sustain check → YIN → noise floor; in the low range,
 * mains hum cancelled on the way into the frames and a pitch at mains hum's own frequency no note) and
 * then the smoother, as `TunerInterpreter` has it. [settings] (from [AnalysisSettings.forSampleRate])
 * give the rate and the range.
 */
internal class TunerChain(
    val settings: AnalysisSettings = AnalysisSettings.forSampleRate(YinPitchDetector.DEFAULT_SAMPLE_RATE),
    private val mapper: NoteMapper = NoteMapper(),
    /** The hum canceller, as the range has it (none in the standard range); `{ null }` for none, to compare. */
    private val humCanceller: () -> MainsHumCanceller? = { settings.newHumCanceller() },
    /** The refinement on the lowest partials, as the tuner has it; false to compare (the whole waveform). */
    private val refineOnLowestPartials: Boolean = true,
    /** The smoother, as the tuner has it; another to compare (e.g. one that follows an octave without an attack). */
    private val smoother: () -> PitchSmoother = { PitchSmoother() },
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
        val chain = PitchChain(settings.sampleRate, refineOnLowestPartials, cancelHum = humCanceller() != null)
        chain.range = settings.range
        val smoother = smoother()
        val frames = ArrayList<Frame>()
        var position = 0
        var start = 0
        while (start < stream.size) {
            val end = minOf(start + chunkSize, stream.size)
            var pos = start
            while (pos < end) {
                val consumed = chain.push(stream, pos, end - pos)
                pos += consumed
                position += consumed
                if (!chain.analyse()) continue
                val pitch = chain.pitch
                if (pitch.isPitched) {
                    smoother.update(mapper.midiOf(pitch.frequencyHz), pitch.confidence, chain.isAboveThreshold, chain.levelDbfs)
                } else {
                    smoother.noReading(chain.sustained, chain.levelDbfs)
                }
                val shown = if (smoother.hasReading) mapper.frequencyOf(smoother.midi) else 0f
                frames += Frame(
                    position, chain.gateOpen, chain.levelDbfs, pitch.frequencyHz, pitch.confidence, shown,
                    chain.noiseFloorDbfs, chain.snrDb, Clarity.of(pitch.confidence), chain.sustained,
                )
            }
            start = end
        }
        return frames
    }
}
