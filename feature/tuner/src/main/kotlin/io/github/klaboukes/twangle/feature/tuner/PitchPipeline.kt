package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.audio.AudioRecorderException
import io.github.klaboukes.twangle.core.dsp.buffer.FrameAssembler
import io.github.klaboukes.twangle.core.dsp.level.NoiseFloorTracker
import io.github.klaboukes.twangle.core.dsp.pitch.AnalysisSettings
import io.github.klaboukes.twangle.core.dsp.pitch.Clarity
import io.github.klaboukes.twangle.core.dsp.pitch.MainsHumCanceller
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import io.github.klaboukes.twangle.core.dsp.pitch.PitchResult
import io.github.klaboukes.twangle.core.dsp.pitch.SignalGate
import io.github.klaboukes.twangle.core.dsp.pitch.SustainCheck
import io.github.klaboukes.twangle.core.dsp.pitch.YinPitchDetector
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

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
    /**
     * False when the frame's sound had died away by its end ([SustainCheck]: a tick that has passed).
     * Such a frame isn't analysed, so it has no pitch, but it doesn't mean a shown note is gone.
     */
    val sustained: Boolean = true,
) : PipelineReading {
    val isPitched: Boolean get() = frequencyHz > 0f
}

/**
 * Microphone → [FrameAssembler] (in the low range through the [MainsHumCanceller]) → [SignalGate] → [SustainCheck] → [YinPitchDetector], one [PitchReading] per hop
 * (~47 per second), with the room's noise floor and the note's SNR ([NoiseFloorTracker]) and
 * clarity ([Clarity]). See docs/architecture/signal-pipeline.md.
 *
 * Frame sizes follow the recorder's sample rate ([AnalysisSettings.forSampleRate]), so 44.1 kHz,
 * 16 kHz or 8 kHz input gives the same ≈ 85 ms window and ≈ 21 ms hop as 48 kHz. A rate outside
 * [AnalysisSettings.MIN_SAMPLE_RATE]..[AnalysisSettings.MAX_SAMPLE_RATE] ends the flow with
 * [AudioRecorderException.InitializationFailed]. If the microphone opens at another rate than
 * the recorder expected, the frame sizes follow it from the first chunk.
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

    /** One reading per hop; [range] is asked every frame how low to listen (it must not allocate). */
    fun readings(range: () -> PitchRange = { PitchRange.STANDARD }): Flow<PitchReading> =
        listen(range) { null }.map { it as PitchReading }

    /**
     * Like [readings], but while [strumStrings] gives the open strings' frequencies (Hz, in the
     * tuning's order) the microphone's chunks go to the strum check instead: a [StrumReading] when
     * it starts, when a strum is heard or stops ringing, and with each reading of the strings
     * (about every 0.1 s while they ring). It starts over whenever the frequencies change. The
     * microphone stays open through every switch. Likewise, while [chordA4Hz] gives a concert pitch
     * (not NaN) the chunks go to the chord name: a [ChordReading] when it starts, when a strum is heard
     * or stops ringing, with each reading of a strum named at that A4, and about every 0.1 s between.
     * [strumStrings], [chordA4Hz] and [range] are asked for every chunk and must not allocate.
     */
    fun listen(
        range: () -> PitchRange = { PitchRange.STANDARD },
        chordA4Hz: () -> Float = { Float.NaN },
        strumStrings: () -> FloatArray?,
    ): Flow<PipelineReading> = flow {
        var tuner = TunerAnalysis(supportedRate())
        // Made the first time strum check starts: its buffers are large.
        var strum: StrumAnalysis? = null
        var strumming = false
        // Made the first time the chord name starts: its buffers are large too.
        var chords: ChordAnalysis? = null
        var naming = false
        var first = true

        recorder.chunks().collect { chunk ->
            if (first) {
                first = false
                // The microphone may have opened at another rate than expected (AudioRecorder.config).
                val rate = supportedRate()
                if (rate != tuner.rate) tuner = TunerAnalysis(rate)
            }
            val rate = tuner.rate
            val standard = tuner.standard
            val low = tuner.low
            val assembler = tuner.assembler
            val gate = tuner.gate
            val noise = tuner.noise
            val strings = strumStrings()
            val chordA4 = if (strings == null) chordA4Hz() else Float.NaN
            if (!chordA4.isNaN()) {
                val analysis = chords ?: ChordAnalysis(rate).also { chords = it }
                if (!naming) {
                    emit(analysis.start())
                    naming = true
                }
                strumming = false
                var pos = 0
                while (pos < chunk.size) {
                    pos += analysis.push(chunk, pos, chunk.size - pos)
                    analysis.next(chordA4)?.let { emit(it) }
                }
                return@collect
            }
            if (strings != null) {
                naming = false
                val analysis = strum ?: StrumAnalysis(rate).also { strum = it }
                val expected = analysis.expected
                if (!strumming || (strings !== expected && !strings.contentEquals(expected))) {
                    emit(analysis.start(strings, PitchRange.forLowestNote(strings.minOrNull() ?: 0f)))
                    strumming = true
                }
                var pos = 0
                while (pos < chunk.size) {
                    pos += analysis.push(chunk, pos, chunk.size - pos)
                    analysis.next()?.let { emit(it) }
                }
                return@collect
            }
            if (strumming || naming) {
                // Back to the tuner: its window starts afresh rather than joining audio from before,
                // and the hum canceller learns the hum afresh (it missed the audio in between).
                strumming = false
                naming = false
                assembler.reset()
                assembler.filter = null
            }
            var pos = 0
            while (pos < chunk.size) {
                // The low range hears 50 and 60 Hz, so its audio goes through the hum canceller.
                val lowRange = range() == PitchRange.LOW
                assembler.filter = if (lowRange) tuner.humCanceller else null
                pos += assembler.push(chunk, pos, chunk.size - pos)
                if (assembler.isFrameReady) {
                    val analysis = if (lowRange) low else standard
                    if (assembler.available < analysis.windowSize) {
                        // The mic opened less than one long window ago: wait for the next hop.
                        assembler.skipFrame()
                        continue
                    }
                    val frame = analysis.frame
                    val detector = analysis.detector
                    val sustain = analysis.sustain
                    assembler.readFrame(frame, analysis.windowSize)
                    val open = gate.process(frame)
                    // A tick that has already died away is not a note (see SustainCheck).
                    val sustained = !open || sustain.isSustained(frame, floorDbfs = noise.floorDbfs)
                    val heard = if (open && sustained) detector.detect(frame) else PitchResult.NONE
                    // Hum the canceller hasn't learned yet reads at mains frequency: no note.
                    val pitch = if (lowRange && MainsHumCanceller.isMainsPitch(heard.frequencyHz)) PitchResult.NONE else heard
                    noise.update(gate.levelDbfs, noteSounding = pitch.isPitched)
                    emit(
                        PitchReading(
                            pitch.frequencyHz, pitch.confidence, gate.levelDbfs, open,
                            noise.floorDbfs, noise.snrDb, Clarity.of(pitch.confidence), gate.peakDbfs, sustained,
                        ),
                    )
                }
            }
        }
    }.flowOn(dspDispatcher)

    private fun supportedRate(): Int = sampleRate.also { rate ->
        if (rate !in SUPPORTED_RATES) throw AudioRecorderException.InitializationFailed("Unsupported sample rate $rate Hz")
    }

    // Everything the tuner needs at one sample rate, made once per collection (again only if the
    // microphone opened at another rate).
    private class TunerAnalysis(val rate: Int) {
        val standard = Analysis(AnalysisSettings.forSampleRate(rate, PitchRange.STANDARD))
        val low = Analysis(AnalysisSettings.forSampleRate(rate, PitchRange.LOW))
        val assembler = FrameAssembler(standard.windowSize, standard.settings.hopSize, capacity = low.windowSize)
        val humCanceller = low.settings.newHumCanceller()
        val gate = SignalGate()
        val noise = standard.settings.newNoiseFloorTracker()
    }

    // The frame buffer and detector for one range, made once per collection.
    private class Analysis(val settings: AnalysisSettings) {
        val windowSize: Int = settings.windowSize
        val frame = FloatArray(windowSize)
        val detector = settings.newPitchDetector()
        val sustain = settings.newSustainCheck()
    }

    private companion object {
        val SUPPORTED_RATES = AnalysisSettings.MIN_SAMPLE_RATE..AnalysisSettings.MAX_SAMPLE_RATE
    }
}
