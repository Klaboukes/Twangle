package io.github.klaboukes.twangle.feature.tuner

import io.github.klaboukes.twangle.core.audio.AudioRecorder
import io.github.klaboukes.twangle.core.audio.AudioRecorderException
import io.github.klaboukes.twangle.core.dsp.buffer.FrameAssembler
import io.github.klaboukes.twangle.core.dsp.level.NoiseFloorTracker
import io.github.klaboukes.twangle.core.dsp.pitch.AnalysisSettings
import io.github.klaboukes.twangle.core.dsp.pitch.Clarity
import io.github.klaboukes.twangle.core.dsp.pitch.MainsHumCanceller
import io.github.klaboukes.twangle.core.dsp.pitch.PitchChain
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import io.github.klaboukes.twangle.core.dsp.pitch.SignalGate
import io.github.klaboukes.twangle.core.dsp.pitch.SustainCheck
import io.github.klaboukes.twangle.core.dsp.pitch.YinPitchDetector
import io.github.klaboukes.twangle.core.dsp.strum.StrumWatcher
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
 * clarity ([Clarity]). That chain is `:core:dsp`'s [PitchChain], the one the watch runs too; this class
 * adds the microphone, and the strum and chord analysis beside the tuner on every chunk (2.0, Phase 74:
 * [StrumWatcher], told what to listen for by a [StrumHearing]). See docs/architecture/signal-pipeline.md.
 *
 * Frame sizes follow the recorder's sample rate ([AnalysisSettings.forSampleRate]), so 44.1 kHz,
 * 16 kHz or 8 kHz input gives the same ≈ 85 ms window and ≈ 21 ms hop as 48 kHz. A rate outside
 * [AnalysisSettings.MIN_SAMPLE_RATE]..[AnalysisSettings.MAX_SAMPLE_RATE] ends the flow with
 * [AudioRecorderException.InitializationFailed]. If the microphone opens at another rate than
 * the recorder expected, the frame sizes follow it from the first chunk.
 *
 * The [PitchChain] is created once per collection (again only if the microphone opened at another
 * rate), so the per-frame loop doesn't allocate; only the small immutable reading handed to the UI is
 * new each frame. Chunks are copied into the chain inside `collect`, as [AudioRecorder.chunks]
 * requires. DSP runs on [dspDispatcher] (`Dispatchers.Default` in the app).
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
        listen(range).map { it as PitchReading }

    /**
     * Like [readings], and while [hearing] says so the strum and chord analysis beside the tuner hears the same
     * chunks ([StrumWatcher]): a [HeardReading] when it starts afresh ([HeardReading.strumCount] 0: what it listens
     * for changed), when a sudden sound is heard, decided or stops ringing, and with each reading of a strum of open
     * strings or a chord. Paused (a tone, the 12th-fret check) it says the latest strum no longer rings; it picks up
     * again without starting afresh, the strums counted on. [range] and [hearing] are asked for every chunk and must
     * not allocate. (Until 2.0's Phase 74 the strum check and the chord name were tools of their own here, which
     * took the microphone's chunks from the tuner while they were on.)
     */
    fun listen(
        range: () -> PitchRange = { PitchRange.STANDARD },
        hearing: StrumHearing? = null,
    ): Flow<PipelineReading> = flow {
        var tuner = PitchChain(supportedRate())
        // Made the first time the analysis beside the tuner starts: its buffers are large.
        var watcher: StrumWatcher? = null
        var watching = false
        val heard = HeardSteps()
        var first = true

        recorder.chunks().collect { chunk ->
            if (first) {
                first = false
                // The microphone may have opened at another rate than expected (AudioRecorder.config).
                val rate = supportedRate()
                if (rate != tuner.sampleRate) tuner = PitchChain(rate)
            }
            val rate = tuner.sampleRate
            var pos = 0
            while (pos < chunk.size) {
                tuner.range = range()
                pos += tuner.push(chunk, pos, chunk.size - pos)
                if (tuner.analyse()) {
                    val pitch = tuner.pitch
                    emit(
                        PitchReading(
                            pitch.frequencyHz, pitch.confidence, tuner.levelDbfs, tuner.gateOpen,
                            tuner.noiseFloorDbfs, tuner.snrDb, Clarity.of(pitch.confidence), tuner.peakDbfs, tuner.sustained,
                        ),
                    )
                }
            }
            // The strum and chord analysis hears the same chunk.
            val a4 = hearing?.a4Hz() ?: Float.NaN
            if (a4.isNaN()) {
                if (watching) {
                    watching = false
                    watcher?.let { heard.paused(it) }?.let { emit(it) }
                }
                return@collect
            }
            val watch = watcher ?: StrumWatcher(rate).also { watcher = it }
            val open = hearing!!.openStrings()
            val names = hearing.namesChords()
            val heardRange = hearing.range()
            val changed = heard.changed(open, a4, names, heardRange)
            if (!watching || changed) {
                heard.started(watch, open, a4, names, heardRange)
                watch.start(open, a4, names, heardRange)
                if (changed) emit(HeardReading.FRESH)
                watching = true
            }
            var at = 0
            while (at < chunk.size) {
                at += watch.push(chunk, at, chunk.size - at)
                while (true) {
                    val event = watch.next()
                    if (event == StrumWatcher.Event.NOTHING) break
                    emit(heard.step(watch, event))
                }
            }
        }
    }.flowOn(dspDispatcher)

    private fun supportedRate(): Int = sampleRate.also { rate ->
        if (rate !in SUPPORTED_RATES) throw AudioRecorderException.InitializationFailed("Unsupported sample rate $rate Hz")
    }

    private companion object {
        val SUPPORTED_RATES = AnalysisSettings.MIN_SAMPLE_RATE..AnalysisSettings.MAX_SAMPLE_RATE
    }
}
