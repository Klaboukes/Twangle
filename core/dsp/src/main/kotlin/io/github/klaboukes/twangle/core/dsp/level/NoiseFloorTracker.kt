package io.github.klaboukes.twangle.core.dsp.level

import io.github.klaboukes.twangle.core.dsp.smoothing.MedianFilter
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Follows the room's background level between notes and, while a note sounds, how far the note
 * rises above it (its signal-to-noise ratio).
 *
 * Feed it every frame's level (RMS in dBFS, as `SignalGate` measures it) with whether a note is
 * sounding (the detector found a pitch). Only frames without a note count towards the background.
 * Their levels first pass a running median of [medianFrames] (≈ 0.1 s), and the floor is the
 * lowest of those among the last [windowSeconds] worth of such frames: the "minimum statistics"
 * idea of R. Martin (2001), *Noise power spectral density estimation based on optimal smoothing
 * and minimum statistics*, IEEE Trans. Speech Audio Process. 9(5), simplified to one band, with
 * a median instead of Martin's recursive power smoothing (which took ~0.6 s to follow a 20 dB drop).
 *
 * So the floor drops within a few frames when the room gets quieter, rises within
 * [windowSeconds] when it gets louder, ignores notes completely, and isn't fooled by a ringing
 * tail unless the tail lasts longer than the whole window. The median keeps an odd silent frame
 * or two (a glitch, the mic being taken over for a moment) from dragging the floor down. Being a
 * minimum it reads slightly low on fluctuating noise, which is the point of a floor.
 *
 * Nothing allocates after construction.
 *
 * @param frameSeconds time between two frames (the hop), e.g. 1024 / 48000 s.
 */
class NoiseFloorTracker(
    frameSeconds: Float,
    val windowSeconds: Float = DEFAULT_WINDOW_SECONDS,
    val medianFrames: Int = DEFAULT_MEDIAN_FRAMES,
) {

    init {
        require(frameSeconds > 0f) { "frameSeconds must be positive, was $frameSeconds" }
        require(windowSeconds >= frameSeconds) { "windowSeconds must hold at least one frame, was $windowSeconds" }
        require(medianFrames > 0) { "medianFrames must be positive, was $medianFrames" }
    }

    /** How many note-free frames the floor looks back over. */
    val windowFrames: Int = max(1, (windowSeconds / frameSeconds).roundToInt())

    private val median = MedianFilter(medianFrames)

    // Median levels of the last windowFrames note-free frames (dBFS), as a ring.
    private val history = FloatArray(windowFrames)
    private var stored = 0
    private var next = 0

    /** Background level in dBFS, or NaN until a frame without a note has been heard. */
    var floorDbfs: Float = Float.NaN
        private set

    /** Whether [floorDbfs] holds an estimate yet. */
    val hasFloor: Boolean get() = !floorDbfs.isNaN()

    /**
     * How far the latest note rises above [floorDbfs], in dB, at least [MIN_SNR_DB]. NaN when the
     * latest frame had no note or there is no floor yet.
     */
    var snrDb: Float = Float.NaN
        private set

    /** Takes one frame's [levelDbfs] and whether a note was [noteSounding] in it. */
    fun update(levelDbfs: Float, noteSounding: Boolean) {
        if (!noteSounding) addBackground(levelDbfs)
        snrDb = if (noteSounding && hasFloor) snrDb(levelDbfs, floorDbfs) else Float.NaN
    }

    /** Forgets everything, e.g. when the microphone restarts. */
    fun reset() {
        stored = 0
        next = 0
        median.reset()
        floorDbfs = Float.NaN
        snrDb = Float.NaN
    }

    private fun addBackground(levelDbfs: Float) {
        history[next] = median.add(levelDbfs)
        next = (next + 1) % windowFrames
        if (stored < windowFrames) stored++
        var lowest = history[0]
        for (i in 1 until stored) if (history[i] < lowest) lowest = history[i]
        floorDbfs = lowest
    }

    companion object {
        /** Long enough to outlast a note's quiet tail, short enough to follow a fan switching on. */
        const val DEFAULT_WINDOW_SECONDS = 2f

        /** Five frames (≈ 0.1 s at any rate): up to two odd frames in a row are ignored. */
        const val DEFAULT_MEDIAN_FRAMES = 5

        /** Lowest SNR reported: a note at or below the background. */
        const val MIN_SNR_DB = -20f

        /**
         * Signal-to-noise ratio of a frame at [levelDbfs] over a background at [floorDbfs], in dB.
         * Uncorrelated powers add, so the note's own power is the frame's minus the background's
         * (as in S. Boll (1979), *Suppression of acoustic noise in speech using spectral
         * subtraction*): `SNR = 10 · log10(10^((L − N) / 10) − 1)`, at least [MIN_SNR_DB].
         */
        fun snrDb(levelDbfs: Float, floorDbfs: Float): Float {
            val ratio = 10.0.pow((levelDbfs - floorDbfs) / 10.0) - 1.0
            val floorRatio = 10.0.pow(MIN_SNR_DB / 10.0)
            return (10.0 * log10(max(ratio, floorRatio))).toFloat()
        }
    }
}
