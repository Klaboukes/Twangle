package io.github.klaboukes.twangle.core.dsp.pitch

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max

/**
 * Tells a sound that is still going on from a click or tick that has already died away, so only
 * the first is analysed for pitch.
 *
 * A tap on the desk, a clock's tick or a mouse click often rings at a pitch for a few milliseconds.
 * That ring is periodic enough for YIN, and it stays in the ≈ 85 ms analysis window for four hops,
 * so without this check a single tick showed a note (often far sharp of the high E string) for
 * about a tenth of a second. A string, by contrast, sustains: on the Xiaomi 12X a played E4 never
 * fell by more than 1.8 dB across a window, from its first frame to its fading tail.
 *
 * So the window is cut, from its newest end, into blocks of [blockSize] samples (a hop, or a period
 * of the lowest note if that is longer: see `AnalysisSettings.newSustainCheck`), and [dropDb] is
 * how far its newest block is below its loudest. A note that starts in the newest block is its
 * loudest block, so new notes pass at once. The frame is not sustained when
 * - it dropped more than [maxDropDb]: a loud tick has passed, tens of dB down a hop later, or
 * - it dropped more than [ROOM_DROP_DB] and its newest block ([newestDbfs]) is within
 *   [ROOM_MARGIN_DB] of the room's background: a quiet tick has passed, and what is left is the
 *   room. A tick just above the room can't drop further than the room's level (on the phone,
 *   6.6–8 dB), as little as a high note fading fast, but that note is still well above the room.
 *
 * The idea is the energy-envelope onset detection of J. P. Bello et al. (2005), *A tutorial on
 * onset detection in music signals*, IEEE Trans. Speech Audio Process. 13(5), turned around: a
 * pitch is only trusted while the envelope says the sound is still there.
 *
 * "Below" is measured twice, by each block's energy and by its peak, and the larger drop counts.
 * Energy sees a ping that rings for 30 ms fade by 12 dB within three hops; the peak sees a 2 ms
 * tick louder than a string by its full height, where in energy it is only a few dB. A block is at
 * least one period of any note in range, so each holds the highest peak of a steady note's cycle
 * and nearly the same energy: a steady note reads about 0 dB either way.
 *
 * Call it after `SignalGate.process` (which removes the DC offset). It keeps only the last frame's
 * figures; nothing allocates.
 */
class SustainCheck(
    val blockSize: Int,
    val maxDropDb: Float = DEFAULT_MAX_DROP_DB,
) {

    init {
        require(blockSize > 0) { "blockSize must be positive, was $blockSize" }
        require(maxDropDb > ROOM_DROP_DB) { "maxDropDb must be above $ROOM_DROP_DB, was $maxDropDb" }
    }

    /**
     * How far the newest block of the last frame measured was below its loudest, in dB: 0 when it
     * was the loudest, also for frames shorter than a block and digital silence.
     */
    var dropDb: Float = 0f
        private set

    /** RMS level of the newest block of the last frame measured, in dBFS. */
    var newestDbfs: Float = SignalGate.SILENCE_DBFS
        private set

    /**
     * Whether the sound in the first [length] samples of [frame] is still going on at its end, over
     * a room whose background is [floorDbfs] (NaN while unknown: then only [maxDropDb] counts).
     */
    fun isSustained(frame: FloatArray, length: Int = frame.size, floorDbfs: Float = Float.NaN): Boolean {
        measure(frame, length)
        if (dropDb > maxDropDb) return false
        return dropDb <= ROOM_DROP_DB || floorDbfs.isNaN() || newestDbfs > floorDbfs + ROOM_MARGIN_DB
    }

    /** Sets [dropDb] and [newestDbfs] for the first [length] samples of [frame]. */
    fun measure(frame: FloatArray, length: Int = frame.size) {
        require(length in 0..frame.size) { "length must be in 0..${frame.size}, was $length" }
        var end = length
        var newestEnergy = -1.0
        var newestPeak = 0f
        var loudestEnergy = 0.0
        var loudestPeak = 0f
        while (end - blockSize >= 0) {
            var energy = 0.0
            var peak = 0f
            for (i in end - blockSize until end) {
                val s = frame[i]
                energy += s.toDouble() * s
                peak = max(peak, abs(s))
            }
            if (newestEnergy < 0.0) {
                newestEnergy = energy
                newestPeak = peak
            }
            loudestEnergy = max(loudestEnergy, energy)
            loudestPeak = max(loudestPeak, peak)
            end -= blockSize
        }
        newestDbfs = if (newestEnergy > 0.0) {
            max(SignalGate.SILENCE_DBFS, (10.0 * log10(newestEnergy / blockSize)).toFloat())
        } else {
            SignalGate.SILENCE_DBFS
        }
        dropDb = when {
            loudestEnergy <= 0.0 -> 0f
            newestEnergy <= 0.0 -> MAX_REPORTED_DROP_DB
            else -> {
                val energyDrop = 10.0 * log10(loudestEnergy / newestEnergy)
                val peakDrop = 20.0 * log10(loudestPeak.toDouble() / newestPeak)
                max(energyDrop, peakDrop).toFloat().coerceAtMost(MAX_REPORTED_DROP_DB)
            }
        }
    }

    companion object {
        /**
         * A played string stays within a few dB, a high one fading fast within about 7; a tick that
         * rang for 10 ms is over 20 dB down a hop later.
         */
        const val DEFAULT_MAX_DROP_DB = 12f

        /** Up to this drop a frame is sustained whatever the room: room noise alone varies by 1–2 dB. */
        const val ROOM_DROP_DB = 4f

        /**
         * A newest block within this of the room's background is the room. The background is a
         * minimum (`NoiseFloorTracker`), so the room itself reads a few dB above it.
         */
        const val ROOM_MARGIN_DB = 6f

        /** Reported when the newest block is digital silence after a sound. */
        const val MAX_REPORTED_DROP_DB = 120f
    }
}
