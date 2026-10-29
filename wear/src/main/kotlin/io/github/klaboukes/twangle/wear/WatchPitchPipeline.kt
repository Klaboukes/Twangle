package io.github.klaboukes.twangle.wear

import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.audio.AudioRecorderException
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.pitch.AnalysisSettings
import io.github.klaboukes.twangle.core.dsp.pitch.PitchResult
import io.github.klaboukes.twangle.core.dsp.pitch.SignalGate
import io.github.klaboukes.twangle.core.dsp.smoothing.InTuneDetector
import io.github.klaboukes.twangle.core.dsp.smoothing.PitchSmoother
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The watch's chromatic tuner chain, the phone's pitch engine from `:core:dsp` without the phone's
 * extras (reception, tips, strum check): microphone → `FrameAssembler` → [SignalGate] → `SustainCheck` → YIN
 * (`AnalysisSettings.newPitchDetector`) → [PitchSmoother] → note and cents
 * (docs/architecture/signal-pipeline.md), and
 * the phone's [InTuneDetector] (within 5 cents for 300 ms, released past 7) for the in-tune ring.
 *
 * Frame sizes follow the recorder's sample rate ([AnalysisSettings.forSampleRate]), so a watch
 * that records at 16 kHz gets the same ≈ 85 ms window and ≈ 21 ms hop as a phone at 48 kHz, also
 * when the microphone opens at another rate than the recorder expected.
 *
 * A note that is lost for a moment stays on screen for [HOLD_SECONDS], and keeps "in tune" if
 * the same note comes back, so the watch never flickers between a reading and "Play a note".
 *
 * Everything is made once per collection and the per-frame loop allocates nothing: a new
 * [WatchTunerState] is only emitted when what the watch shows changes (the note or the whole
 * cents, or whether it is in tune), a few times a second at most while a note rings. DSP runs on [dspDispatcher].
 */
class WatchPitchPipeline(
    private val recorder: AudioRecorder,
    private val a4Hz: Float = NoteMapper.DEFAULT_A4_HZ,
    private val dspDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    /** [WatchTunerState.Listening] first, then each change of the reading. Failures end the flow. */
    fun states(): Flow<WatchTunerState> = flow {
        var analysis = Analysis(supportedRate())
        val smoother = PitchSmoother()
        val mapper = NoteMapper(a4Hz)
        val inTune = InTuneDetector()
        var lostFrames = 0
        var shownMidi = NO_NOTE
        var shownCents = 0
        var shownInTune = false
        var first = true

        emit(WatchTunerState.Listening)
        recorder.chunks().collect { chunk ->
            if (first) {
                first = false
                // The microphone may have opened at another rate than expected (AudioRecorder.config).
                val rate = supportedRate()
                if (rate != analysis.rate) analysis = Analysis(rate)
            }
            val assembler = analysis.assembler
            val frame = analysis.frame
            val gate = analysis.gate
            val sustain = analysis.sustain
            val noise = analysis.noise
            val detector = analysis.detector
            val frameSeconds = analysis.frameSeconds
            val holdFrames = analysis.holdFrames
            var pos = 0
            while (pos < chunk.size) {
                pos += assembler.push(chunk, pos, chunk.size - pos)
                if (!assembler.isFrameReady) continue
                assembler.readFrame(frame)
                val open = gate.process(frame)
                // A tick that has already died away is not a note, nor the end of one (SustainCheck).
                val sustained = !open || sustain.isSustained(frame, floorDbfs = noise.floorDbfs)
                val pitch = if (open && sustained) detector.detect(frame) else PitchResult.NONE
                noise.update(gate.levelDbfs, noteSounding = pitch.isPitched)
                if (pitch.isPitched) {
                    smoother.update(mapper.midiOf(pitch.frequencyHz), pitch.confidence, gate.isAboveThreshold, gate.levelDbfs)
                } else {
                    smoother.noReading(sustained, gate.levelDbfs)
                }
                if (!smoother.hasReading && shownMidi != NO_NOTE && lostFrames < holdFrames) {
                    // The note is lost for a moment: keep showing it, glow and all, so a brief
                    // dropout doesn't flicker to "Play a note" and back.
                    lostFrames++
                    continue
                }
                lostFrames = 0
                val midi = if (smoother.hasReading) smoother.note.midi else NO_NOTE
                val cents = if (smoother.hasReading) smoother.cents.roundToInt() else 0
                // A new note (or none) has to earn "in tune" again, as on the phone; the same note
                // back after a dropout keeps it.
                if (midi != shownMidi) inTune.reset()
                val isInTune = midi != NO_NOTE && inTune.update(smoother.cents, frameSeconds)
                if (midi != shownMidi || cents != shownCents || isInTune != shownInTune) {
                    shownMidi = midi
                    shownCents = cents
                    shownInTune = isInTune
                    emit(
                        if (midi == NO_NOTE) WatchTunerState.Listening else WatchTunerState.Detected(Note(midi), cents, isInTune),
                    )
                }
            }
        }
    }.flowOn(dspDispatcher)

    private fun supportedRate(): Int = recorder.config.sampleRate.also { rate ->
        if (rate !in AnalysisSettings.MIN_SAMPLE_RATE..AnalysisSettings.MAX_SAMPLE_RATE) {
            throw AudioRecorderException.InitializationFailed("Unsupported sample rate $rate Hz")
        }
    }

    // The frame-size-dependent part of the chain at one sample rate, made once per collection
    // (again only if the microphone opened at another rate).
    private class Analysis(val rate: Int) {
        private val settings = AnalysisSettings.forSampleRate(rate)
        val assembler = settings.newFrameAssembler()
        val frame = FloatArray(settings.windowSize)
        val gate = SignalGate()
        val sustain = settings.newSustainCheck()

        // Only for SustainCheck: what the room sounds like between notes.
        val noise = settings.newNoiseFloorTracker()
        val detector = settings.newPitchDetector()
        val frameSeconds = settings.hopSeconds
        val holdFrames = ceil(HOLD_SECONDS / frameSeconds).toInt()
    }

    private companion object {
        const val NO_NOTE = Int.MIN_VALUE

        /**
         * How long a lost note stays on screen, on top of the smoother's own hold: as long as the
         * phone's in-tune glow outlasts a dropout (`InTuneGlowRule`, 600 ms).
         */
        const val HOLD_SECONDS = 0.6f
    }
}
