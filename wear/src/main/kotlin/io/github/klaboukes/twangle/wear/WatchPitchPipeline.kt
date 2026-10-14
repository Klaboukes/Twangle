package io.github.klaboukes.twangle.wear

import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.audio.AudioRecorderException
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.pitch.AnalysisSettings
import io.github.klaboukes.twangle.core.dsp.pitch.PitchResult
import io.github.klaboukes.twangle.core.dsp.pitch.SignalGate
import io.github.klaboukes.twangle.core.dsp.smoothing.PitchSmoother
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.math.roundToInt

/**
 * The watch's chromatic tuner chain, the phone's pitch engine from `:core:dsp` without the phone's
 * extras (reception, tips, strum check): microphone → `FrameAssembler` → [SignalGate] → YIN
 * (`AnalysisSettings.newPitchDetector`) → [PitchSmoother] → note and cents (ARCHITECTURE §3).
 *
 * Frame sizes follow the recorder's sample rate ([AnalysisSettings.forSampleRate]), so a watch
 * that records at 16 kHz gets the same ≈ 85 ms window and ≈ 21 ms hop as a phone at 48 kHz.
 *
 * Everything is made once per collection and the per-frame loop allocates nothing: a new
 * [WatchTunerState] is only emitted when what the watch shows changes (the note or the whole
 * cents), a few times a second at most while a note rings. DSP runs on [dspDispatcher].
 */
class WatchPitchPipeline(
    private val recorder: AudioRecorder,
    private val a4Hz: Float = NoteMapper.DEFAULT_A4_HZ,
    private val dspDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    /** [WatchTunerState.Listening] first, then each change of the reading. Failures end the flow. */
    fun states(): Flow<WatchTunerState> = flow {
        val rate = recorder.config.sampleRate
        if (rate !in AnalysisSettings.MIN_SAMPLE_RATE..AnalysisSettings.MAX_SAMPLE_RATE) {
            throw AudioRecorderException.InitializationFailed("Unsupported sample rate $rate Hz")
        }
        val settings = AnalysisSettings.forSampleRate(rate)
        val assembler = settings.newFrameAssembler()
        val frame = FloatArray(settings.windowSize)
        val gate = SignalGate()
        val detector = settings.newPitchDetector()
        val smoother = PitchSmoother()
        val mapper = NoteMapper(a4Hz)
        var shownMidi = NO_NOTE
        var shownCents = 0

        emit(WatchTunerState.Listening)
        recorder.chunks().collect { chunk ->
            var pos = 0
            while (pos < chunk.size) {
                pos += assembler.push(chunk, pos, chunk.size - pos)
                if (!assembler.isFrameReady) continue
                assembler.readFrame(frame)
                val pitch = if (gate.process(frame)) detector.detect(frame) else PitchResult.NONE
                if (pitch.isPitched) {
                    smoother.update(mapper.midiOf(pitch.frequencyHz), pitch.confidence)
                } else {
                    smoother.noReading()
                }
                val midi = if (smoother.hasReading) smoother.note.midi else NO_NOTE
                val cents = if (smoother.hasReading) smoother.cents.roundToInt() else 0
                if (midi != shownMidi || cents != shownCents) {
                    shownMidi = midi
                    shownCents = cents
                    emit(if (midi == NO_NOTE) WatchTunerState.Listening else WatchTunerState.Detected(Note(midi), cents))
                }
            }
        }
    }.flowOn(dspDispatcher)

    private companion object {
        const val NO_NOTE = Int.MIN_VALUE
    }
}
