package io.github.klaboukes.twangle.wear

import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.audio.AudioRecorderException
import io.github.klaboukes.twangle.core.audio.ScreenReaderSpeech
import io.github.klaboukes.twangle.core.audio.watching
import io.github.klaboukes.twangle.core.dsp.guide.GuidePulse
import io.github.klaboukes.twangle.core.dsp.guide.GuideTiming
import io.github.klaboukes.twangle.core.dsp.guide.VibrationGuide
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.NoteMapper
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
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
 * The watch names any note, but is worn by a player with a guitar, so its smoother knows the strings of [tuning]
 * (standard tuning; the watch has no tuning to pick) as the phone's does in guitar mode: the next string plucked over
 * the last one still ringing, two octaves or a twelfth away (low E → high E, low E → B, A → high E), is followed as soon
 * as its frames rise 7 dB, rather than held for a second as a likely slip (`PitchSmoother.strings`,
 * docs/architecture/signal-pipeline.md, *Smoothing*).
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
 * reader is on (Wear OS's TalkBack plays with the phone's usage: checked on the Galaxy Watch 5, 2026-11-07).
 *
 * With the vibration guide on ([guideOn], Phase 85), [guideVibrator] taps the wrist while a note is shown: once to
 * tighten, twice to loosen, quicker as it nears the note, quiet within its last 5 cents, one long buzz on coming in tune
 * ([VibrationGuide] with the watch's [GuideTiming.WATCH], fed what the watch shows each frame, so the taps, the word and
 * the colour agree); the buzz then takes the place of the screen's in-tune tap. The microphone hears every pulse, so the
 * pipeline looks away for each as for the tap ([guideFrames]: the pulse's length plus the tap's margins and one window),
 * the reading holding still; with nothing shown the guide is quiet, so the motor can never make a note on its own.
 * While a screen reader's voice is set aside the guide waits (its time stands still), so it never taps on a stale
 * reading. It stops the moment [states] ends or [guideOn] turns false.
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
    private val guideVibrator: WatchGuideVibrator = WatchGuideVibrator.NONE,
    private val guideOn: () -> Boolean = { false },
    private val tuning: Tuning? = Tunings.STANDARD,
) {

    /** [WatchTunerState.Listening] first, then each change of the reading. Failures end the flow. */
    fun states(): Flow<WatchTunerState> = flow {
        var analysis = Analysis(supportedRate())
        val smoother = PitchSmoother().also { it.strings = tuning }
        val mapper = NoteMapper(a4Hz)
        val inTune = InTuneDetector()
        val guide = VibrationGuide(GuideTiming.WATCH)
        var guiding = false
        var lostFrames = 0
        var shownMidi = NO_NOTE
        var shownCents = 0
        var shownInTune = false
        // Frames still to pass by: the motor (the in-tune tap or the guide's pulse) may be heard.
        var tapFrames = 0
        // Frames still to pass by: a screen reader's voice plays, or its echo may still be in the room.
        var spokenFrames = 0
        var first = true

        /**
         * One frame of the vibration guide, on what the watch shows now ([on]: whether it is on): plays its pulse and
         * looks away while the motor may be heard. Off, it starts afresh and stops what it played.
         */
        fun guideFrame(on: Boolean) {
            if (!on) {
                if (guiding) {
                    guide.reset()
                    guideVibrator.stop()
                    guiding = false
                }
                return
            }
            guiding = true
            val heard = shownMidi != NO_NOTE
            val cue = watchGuideCue(heard, shownInTune, shownCents)
            val pulse = guide.update(cue, shownCents.toFloat(), shownMidi, analysis.frameSeconds)
            if (pulse != GuidePulse.NONE) {
                guideVibrator.play(pulse)
                tapFrames = maxOf(tapFrames, analysis.guideFrames(pulse))
            }
        }

        emit(WatchTunerState.Listening)
        try {
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
                        val guided = guideOn()
                        if (speaking.value) spokenFrames = analysis.speechTailFrames + 1
                        if (spokenFrames > 0) {
                            // These frames may hear the screen reader's voice: the reading holds as it is, and the
                            // smoothing starts afresh after it, so nothing of the voice carries over. The guide waits.
                            chain.skipFrame()
                            if (tapFrames > 0) tapFrames--
                            if (--spokenFrames == 0) smoother.reset()
                            if (!guided) guideFrame(false)
                            continue
                        }
                        if (tapFrames > 0) {
                            // These frames may hear the motor: the reading holds as it is, and the guide goes on with it.
                            chain.skipFrame()
                            tapFrames--
                            guideFrame(guided)
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
                            guideFrame(guided)
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
                            // The screen taps as the note comes in tune, unless the guide's buzz says so instead.
                            if (isInTune && !shownInTune && !guided) tapFrames = analysis.tapFrames
                            shownMidi = midi
                            shownCents = cents
                            shownInTune = isInTune
                            emit(
                                if (midi == NO_NOTE) WatchTunerState.Listening else WatchTunerState.Detected(Note(midi), cents, isInTune),
                            )
                        }
                        guideFrame(guided)
                    }
                }
            }
        } finally {
            // The tuner stopped listening (the screen went, the microphone failed): the guide stops at once.
            guideFrame(false)
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

        private val windowSeconds = settings.windowSize.toFloat() / rate

        // Every frame whose window may hold some of the tap's buzz.
        val tapFrames = ceil((TAP_SECONDS + windowSeconds) / frameSeconds).toInt()

        // The same for each of the guide's pulses: its length on the motor plus the tap's margins.
        private val tightenFrames = motorFrames(GuideTiming.WATCH.tapSeconds)
        private val loosenFrames = motorFrames(GuideTiming.WATCH.pairLengthSeconds)
        private val buzzFrames = motorFrames(GuideTiming.WATCH.buzzSeconds)

        /** How many frames from now may hear [pulse] on the motor. */
        fun guideFrames(pulse: GuidePulse): Int = when (pulse) {
            GuidePulse.NONE -> 0
            GuidePulse.TIGHTEN -> tightenFrames
            GuidePulse.LOOSEN -> loosenFrames
            GuidePulse.IN_TUNE -> buzzFrames
        }

        private fun motorFrames(drivenSeconds: Float): Int =
            ceil((MOTOR_MARGIN_SECONDS + drivenSeconds + windowSeconds) / frameSeconds).toInt()

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

        /**
         * The same margins around one of the guide's pulses ([TAP_SECONDS] without the tap's own length): the guide
         * plays from this thread, sooner than the screen's tap, but a chunk's frames heard before the call still
         * count down meanwhile, so the screen's 60 ms stays as a margin.
         */
        const val MOTOR_MARGIN_SECONDS = (60 + 90 + 60) / 1000f
    }
}
