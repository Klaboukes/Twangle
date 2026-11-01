package io.github.klaboukes.twangle.core.dsp.pitch

import io.github.klaboukes.twangle.core.dsp.buffer.FrameAssembler
import io.github.klaboukes.twangle.core.dsp.level.NoiseFloorTracker

/**
 * The tuner's frame chain, one copy for every listener: the phone's tuner (`PitchPipeline`, whose
 * readings Ear training hears too), the watch's tuner (`WatchPitchPipeline`) and the tests that feed
 * whole recordings through the tuner (`TunerChain`). Audio in chunks of any size → [FrameAssembler]
 * (in the low range through the [MainsHumCanceller]) → [SignalGate] → [SustainCheck] → the pitch
 * detector (YIN, [AnalysisSettings.newPitchDetector]) → [NoiseFloorTracker], one frame per hop
 * (docs/architecture/signal-pipeline.md). A fix to any of these steps lands here once.
 *
 * [range] may change at any hop: both ranges share one ring buffer, which keeps the low range's long
 * window, one gate and one noise floor, so a switch loses no audio. Right after the microphone opens
 * the low range waits until one long window has arrived ([analyse] passes the frames before by). In
 * the low range a pitch at mains frequency is no note ([MainsHumCanceller.isMainsPitch]): hum the
 * canceller hasn't learned yet. The low range's frame buffer, detector and hum canceller are made the
 * first time it is asked for (the watch never asks).
 *
 * Typical loop for one chunk:
 * ```
 * var pos = 0
 * while (pos < chunk.size) {
 *     chain.range = range
 *     pos += chain.push(chunk, pos, chunk.size - pos)
 *     if (chain.analyse()) use(chain.pitch, chain.levelDbfs, chain.noiseFloorDbfs, …)
 * }
 * ```
 * After construction nothing allocates but the low range's buffers, once. One caller at a time.
 *
 * [refineOnLowestPartials] and [cancelHum] are on for the app; tests turn them off to compare.
 */
class PitchChain(
    /** The microphone's rate, in [AnalysisSettings.MIN_SAMPLE_RATE]..[AnalysisSettings.MAX_SAMPLE_RATE]. */
    val sampleRate: Int,
    private val refineOnLowestPartials: Boolean = true,
    /** False: no [MainsHumCanceller] in the low range, nor its mains-pitch rule. */
    private val cancelHum: Boolean = true,
) {

    private val standard = Window(AnalysisSettings.forSampleRate(sampleRate, PitchRange.STANDARD))
    private val lowSettings = AnalysisSettings.forSampleRate(sampleRate, PitchRange.LOW)
    private var low: Window? = null
    private var current = standard

    private val assembler = FrameAssembler(standard.size, standard.settings.hopSize, capacity = lowSettings.windowSize)
    private val gate = SignalGate()
    private val noise = standard.settings.newNoiseFloorTracker()

    /** Time between two frames (one hop, ≈ 21 ms), the same in both ranges. */
    val hopSeconds: Float = standard.settings.hopSeconds

    /** How low the frames listen; set it before each [push] (it must not allocate after the first switch to the low range). */
    var range: PitchRange = PitchRange.STANDARD
        set(value) {
            field = value
            current = if (value == PitchRange.LOW) low ?: Window(lowSettings).also { low = it } else standard
            // Setting another filter resets it: after the standard range the canceller learns the hum afresh.
            assembler.filter = current.humCanceller
        }

    /** The last analysed frame's pitch: [PitchResult.NONE] when the gate was closed, the sound had died away, or it was mains hum. */
    var pitch: PitchResult = PitchResult.NONE
        private set

    /** False when the last frame's sound had died away by its end ([SustainCheck]: a tick that has passed). */
    var sustained: Boolean = true
        private set

    /** Whether the last frame was loud enough to analyse ([SignalGate.isOpen]). */
    val gateOpen: Boolean get() = gate.isOpen

    /** Whether the last frame was loud enough to open the gate by itself ([SignalGate.isAboveThreshold]). */
    val isAboveThreshold: Boolean get() = gate.isAboveThreshold

    /** The last frame's RMS level after DC removal, in dBFS. */
    val levelDbfs: Float get() = gate.levelDbfs

    /** The last frame's peak before DC removal, in dBFS; near 0 the input clips. */
    val peakDbfs: Float get() = gate.peakDbfs

    /** The room's background level in dBFS, NaN until a note-free frame was heard. */
    val noiseFloorDbfs: Float get() = noise.floorDbfs

    /** How far the last frame's note rises above [noiseFloorDbfs], in dB; NaN without a note or a floor. */
    val snrDb: Float get() = noise.snrDb

    /** Whether a frame is waiting for [analyse] or [skipFrame]. */
    val isFrameReady: Boolean get() = assembler.isFrameReady

    /**
     * Takes samples from [samples] (at most [length] from [offset]) and stops as soon as a frame is
     * ready, as [FrameAssembler.push] does. Returns how many it took. The caller's array is left as it is.
     */
    fun push(samples: FloatArray, offset: Int = 0, length: Int = samples.size - offset): Int =
        assembler.push(samples, offset, length)

    /**
     * Runs the ready frame through gate → sustain check → detector → noise floor in [range]. True with
     * a new reading in [pitch] and the rest; false when no frame was ready, or when the low range's long
     * window hasn't filled since the start (that frame is passed by).
     */
    fun analyse(): Boolean {
        if (!assembler.isFrameReady) return false
        val window = current
        if (assembler.available < window.size) {
            // The microphone opened less than one long window ago: wait for the next hop.
            assembler.skipFrame()
            return false
        }
        val frame = window.frame
        assembler.readFrame(frame, window.size)
        val open = gate.process(frame)
        // A tick that has already died away is not a note (see SustainCheck).
        sustained = !open || window.sustain.isSustained(frame, floorDbfs = noise.floorDbfs)
        val heard = if (open && sustained) window.detector.detect(frame) else PitchResult.NONE
        // Hum the canceller hasn't learned yet reads at mains frequency: no note.
        pitch = if (window.humCanceller != null && MainsHumCanceller.isMainsPitch(heard.frequencyHz)) PitchResult.NONE else heard
        noise.update(gate.levelDbfs, noteSounding = pitch.isPitched)
        return true
    }

    /**
     * Passes the ready frame by unheard, e.g. while the app's own sound may be in it (the watch's tap):
     * the gate, the sustain check and the noise floor don't see it, and the last reading stays as it was.
     */
    fun skipFrame() {
        if (assembler.isFrameReady) assembler.skipFrame()
    }

    /**
     * Drops the buffered audio, so the next frame starts afresh rather than joining audio from before
     * (e.g. after the microphone's chunks went elsewhere for a while). The hum canceller, which missed
     * that audio too, learns the hum afresh. The gate and the noise floor keep what they know.
     */
    fun restart() {
        assembler.reset()
        assembler.filter = null
        assembler.filter = current.humCanceller
    }

    // One range's frame buffer, sustain check, detector and hum canceller (none in the standard range).
    private inner class Window(val settings: AnalysisSettings) {
        val size: Int = settings.windowSize
        val frame = FloatArray(size)
        val sustain = settings.newSustainCheck()
        val detector = settings.newPitchDetector(refineOnLowestPartials)
        val humCanceller: MainsHumCanceller? = if (cancelHum) settings.newHumCanceller() else null
    }
}
