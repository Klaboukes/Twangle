package io.github.klaboukes.twangle.wear

import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.audio.AudioRecorderException
import io.github.klaboukes.twangle.core.audio.ScreenReaderSpeech
import io.github.klaboukes.twangle.core.audio.watching
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.pitch.AnalysisSettings
import io.github.klaboukes.twangle.core.dsp.pitch.PitchChain
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
 * extras (reception, tips, strum check): microphone → [PitchChain] (the phone's own frame chain:
 * `FrameAssembler` → [SignalGate] → `SustainCheck` → YIN → noise floor, standard range) →
 * [PitchSmoother] → note and cents (docs/architecture/signal-pipeline.md), and
 * the phone's [InTuneDetector] (within 5 cents for 300 ms, released past 7) for the in-tune ring.
 *
 * Frame sizes follow the recorder's sample rate ([AnalysisSettings.forSampleRate]), so a watch
 * that records at 16 kHz gets the same ≈ 85 ms window and ≈ 21 ms hop as a phone at 48 kHz, also
 * when the microphone opens at another rate than the recorder expected.
 *
 * A note that is lost for a moment stays on screen for [HOLD_SECONDS], and keeps "in tune" if
 * the same note comes back, so the watch never flickers between a reading and "Play a note".
 *
 * The moment a note comes in tune the watch taps the wrist ([WatchInTuneTap]), and the microphone,
 * in the same case, hears the motor's short buzz. So the pipeline looks away for as long as that
 * buzz can be in a frame ([TAP_SECONDS] plus one window): the reading, already in tune, holds still
 * meanwhile instead of being nudged a few cents (`WatchPitchPipelineTest` mixes a motor's buzz in at
 * the tap).
 *
 * While a screen reader's voice plays ([screenReaderSpeech], Phase 81: TalkBack saying the reading through the
 * watch's speaker) and for [ScreenReaderSpeech.TAIL_SECONDS] after, the frames are passed by the same way and the
 * reading holds still; the smoothing then starts afresh, so nothing of the voice is read as a note and a spoken
 * reading can't set off the next one. The voice is watched only while [states] is collected, and only while a screen
 * reader is on (unverified on the watch's own TalkBack: it is taken to play with the phone's usage).
 *
 * Everything is made once per collection and the per-frame loop allocates nothing: a new
 * [WatchTunerState] is only emitted when what the watch shows changes (the note or the whole
 * cents, or whether it is in tune), a few times a second at most while a note rings. DSP runs on [dspDispatcher].
 */
class WatchPitchPipeline(
    private val recorder: AudioRecorder,
    private val a4Hz: Float = NoteMapper.DEFAULT_A4_HZ,
    private val dspDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val screenReaderSpeech: ScreenReaderSpeech = ScreenReaderSpeech.NONE,
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
        var tapFrames = 0
        // Frames still to pass by: a screen reader's voice plays, or its echo may still be in the room.
        var spokenFrames = 0
        var first = true

        emit(WatchTunerState.Listening)
        screenReaderSpeech.watching { speaking ->
            recorder.chunks().collect { chunk ->
                if (first) {
                    first = false
                    // The microphone may have opened at another rate than expected (AudioRecorder.config).
                    val rate = supportedRate()
                    if (rate != analysis.rate) analysis = Analysis(rate)
                }
                val chain = analysis.chain
                val frameSeconds = analysis.frameSeconds
                val holdFrames = analysis.holdFrames
                var pos = 0
                while (pos < chunk.size) {
                    pos += chain.push(chunk, pos, chunk.size - pos)
                    if (!chain.isFrameReady) continue
                    if (speaking.value) spokenFrames = analysis.speechTailFrames + 1
                    if (spokenFrames > 0) {
                        // These frames may hear the screen reader's voice: the reading holds as it is, and the
                        // smoothing starts afresh after it, so nothing of the voice carries over.
                        chain.skipFrame()
                        if (tapFrames > 0) tapFrames--
                        if (--spokenFrames == 0) smoother.reset()
                        continue
                    }
                    if (tapFrames > 0) {
                        // These frames may hear the wrist tap's buzz: the reading holds as it is.
                        chain.skipFrame()
                        tapFrames--
                        continue
                    }
                    chain.analyse()
                    val pitch = chain.pitch
                    if (pitch.isPitched) {
                        smoother.update(mapper.midiOf(pitch.frequencyHz), pitch.confidence, chain.isAboveThreshold, chain.levelDbfs)
                    } else {
                        smoother.noReading(chain.sustained, chain.levelDbfs)
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
                        if (isInTune && !shownInTune) tapFrames = analysis.tapFrames
                        shownMidi = midi
                        shownCents = cents
                        shownInTune = isInTune
                        emit(
                            if (midi == NO_NOTE) WatchTunerState.Listening else WatchTunerState.Detected(Note(midi), cents, isInTune),
                        )
                    }
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

        // The phone's frame chain, in the standard range only (no hum canceller: its 62 Hz floor keeps hum out).
        val chain = PitchChain(rate)
        val frameSeconds = settings.hopSeconds
        val holdFrames = ceil(HOLD_SECONDS / frameSeconds).toInt()

        // Every frame whose window may hold some of the tap's buzz.
        val tapFrames = ceil((TAP_SECONDS + settings.windowSize.toFloat() / rate) / frameSeconds).toInt()

        // A screen reader's voice's tail (its echo, one window).
        val speechTailFrames = ScreenReaderSpeech.tailFrames(frameSeconds)
    }

    private companion object {
        const val NO_NOTE = Int.MIN_VALUE

        /**
         * How long a lost note stays on screen, on top of the smoother's own hold: as long as the
         * phone's in-tune glow outlasts a dropout (`InTuneGlowRule`, 600 ms).
         */
        const val HOLD_SECONDS = 0.6f

        /**
         * How long after a note comes in tune the wrist tap's buzz may still be heard: the screen
         * plays the tap a frame or a few after the reading (up to 60 ms), the microphone's frames
         * hear the motor 45–90 ms after that, it drives the motor for [TAP_MILLIS] and rings out
         * for some 50 ms more. Measured on the Galaxy Watch 5 (2026-11-03): the buzz was over by
         * 280 ms after the tap was asked for. About 450 ms of frames with the window.
         */
        const val TAP_SECONDS = (60 + 90 + TAP_MILLIS + 60) / 1000f
    }
}
