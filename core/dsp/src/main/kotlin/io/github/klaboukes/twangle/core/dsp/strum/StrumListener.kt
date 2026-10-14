package io.github.klaboukes.twangle.core.dsp.strum

import io.github.klaboukes.twangle.core.dsp.buffer.FrameAssembler
import io.github.klaboukes.twangle.core.dsp.pitch.AnalysisSettings
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange
import io.github.klaboukes.twangle.core.dsp.pitch.SignalGate
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Turns microphone chunks into [StrumDetector] frames, timed from each strum: it notices the strum
 * by its sudden rise in level, waits until the strings have started ([ATTACK_SECONDS]) and one
 * whole frame of ringing has arrived, and then offers a frame every [REREAD_SECONDS] while the
 * strings ring. A new strum starts over. See ARCHITECTURE.md, *Strum check (polyphonic)*.
 *
 * The first frame after a strum is ready ≈ [ATTACK_SECONDS] + [frameLength] after it (0.39 s at the
 * standard range, 0.73 s at [PitchRange.LOW]), up to two hops (≈ 43 ms) more.
 *
 * Like [FrameAssembler], [push] stops as soon as a frame is ready, so none is skipped:
 * ```
 * var pos = 0
 * while (pos < chunk.size) {
 *     pos += listener.push(chunk, pos, chunk.size - pos)
 *     if (listener.isFrameReady) {
 *         listener.readFrame(frame)
 *         detector.detect(frame, expectedHz, stringCount, result)
 *     }
 * }
 * ```
 * Nothing allocates after construction. Not thread-safe.
 */
class StrumListener(
    val sampleRate: Int,
    val range: PitchRange = PitchRange.STANDARD,
) {

    /** Samples per level check: the tuner's hop (≈ 21 ms), so recorder chunks line up. */
    val hopSize: Int = AnalysisSettings.forSampleRate(sampleRate).hopSize

    /** Samples per frame, as [StrumDetector.frameLengthFor] this rate and range. */
    val frameLength: Int = StrumDetector.frameLengthFor(sampleRate, range)

    private val assembler = FrameAssembler(hopSize, hopSize, capacity = frameLength)
    private val hop = FloatArray(hopSize)
    private val frame = FloatArray(frameLength)

    private val firstReadingAt = (ATTACK_SECONDS * sampleRate).roundToInt() + frameLength
    private val rereadSamples = (REREAD_SECONDS * sampleRate).roundToInt()
    private val minGapSamples = (MIN_GAP_SECONDS * sampleRate).roundToInt()

    // Levels of the last RECENT_HOPS hops (ring), the reference a strum rises from.
    private val recentDb = FloatArray(RECENT_HOPS) { SignalGate.SILENCE_DBFS }
    private var recentPos = 0
    private var nextReadingAt = 0

    // False until the first hop after construction or reset: that hop sets the level a strum rises from.
    private var heardAny = false

    /** Level of the latest hop in dBFS (RMS, DC removed). */
    var levelDbfs: Float = SignalGate.SILENCE_DBFS
        private set

    /** Samples since the latest strum was noticed (counted in hops), or −1 before the first strum. */
    var samplesSinceStrum: Int = NO_STRUM
        private set

    /** True while the latest strum still rings (above [GATE_DBFS]): readings keep coming. */
    var isRinging: Boolean = false
        private set

    /** Readings offered since the latest strum: 0 until the first is read. */
    var readingsSinceStrum: Int = 0
        private set

    /** Strums noticed since the start or [reset]. */
    var strumCount: Int = 0
        private set

    /** True when a frame is waiting to be read with [readFrame]. */
    var isFrameReady: Boolean = false
        private set

    /**
     * Takes samples from [samples] (offset/length as in [FrameAssembler.push]), stopping as soon as
     * a frame is ready. Returns how many it consumed (0 while a frame waits to be read).
     */
    fun push(samples: FloatArray, offset: Int = 0, length: Int = samples.size - offset): Int {
        require(offset >= 0 && length >= 0 && offset + length <= samples.size) {
            "Range $offset+$length is outside an array of ${samples.size}"
        }
        if (isFrameReady) return 0
        var used = 0
        while (used < length && !isFrameReady) {
            used += assembler.push(samples, offset + used, length - used)
            if (assembler.isFrameReady) {
                assembler.readFrame(hop, hopSize)
                onHop()
            }
        }
        return used
    }

    /** Copies the ready frame ([frameLength] samples, oldest first) into [out] and clears [isFrameReady]. */
    fun readFrame(out: FloatArray) {
        check(isFrameReady) { "No frame is ready" }
        require(out.size >= frameLength) { "Output must hold $frameLength samples, was ${out.size}" }
        System.arraycopy(frame, 0, out, 0, frameLength)
        isFrameReady = false
        readingsSinceStrum++
    }

    /** Forgets all audio and strums, e.g. after the microphone restarts. */
    fun reset() {
        assembler.reset()
        recentDb.fill(SignalGate.SILENCE_DBFS)
        recentPos = 0
        heardAny = false
        levelDbfs = SignalGate.SILENCE_DBFS
        samplesSinceStrum = NO_STRUM
        isRinging = false
        readingsSinceStrum = 0
        strumCount = 0
        isFrameReady = false
    }

    private fun onHop() {
        val db = hopLevelDbfs()
        if (!heardAny) {
            // Whatever already sounds when listening starts (strings still ringing, a steady note,
            // a fan) is where a strum must rise from, not a strum itself.
            heardAny = true
            recentDb.fill(db)
            levelDbfs = db
            return
        }
        var quiet = Float.MAX_VALUE
        for (d in recentDb) if (d < quiet) quiet = d
        recentDb[recentPos] = db
        recentPos = (recentPos + 1) % RECENT_HOPS
        levelDbfs = db
        if (samplesSinceStrum != NO_STRUM) samplesSinceStrum += hopSize

        val gapOk = samplesSinceStrum == NO_STRUM || samplesSinceStrum >= minGapSamples
        if (db >= GATE_DBFS && db - quiet >= ONSET_RISE_DB && gapOk) {
            // A strum began within this hop; count from its end, so the first frame starts at
            // least ATTACK_SECONDS after the strum.
            samplesSinceStrum = 0
            strumCount++
            readingsSinceStrum = 0
            nextReadingAt = firstReadingAt
            isRinging = true
            return
        }
        if (!isRinging) return
        if (db < GATE_DBFS - GATE_HYSTERESIS_DB) {
            isRinging = false
            return
        }
        if (samplesSinceStrum >= nextReadingAt && assembler.available >= frameLength) {
            assembler.copyLatest(frame, frameLength)
            isFrameReady = true
            nextReadingAt = samplesSinceStrum + rereadSamples
        }
    }

    // RMS of the hop after removing its mean (the mic's DC offset), in dBFS.
    private fun hopLevelDbfs(): Float {
        var mean = 0.0
        for (s in hop) mean += s
        mean /= hopSize
        var sum = 0.0
        for (s in hop) {
            val d = s - mean
            sum += d * d
        }
        val rms = sqrt(sum / hopSize)
        return if (rms <= 0.0) SignalGate.SILENCE_DBFS else maxOf(SignalGate.SILENCE_DBFS, (20 * log10(rms)).toFloat())
    }

    companion object {
        /** A strum is a hop at least this loud (the tuner's [SignalGate] threshold)… */
        const val GATE_DBFS = SignalGate.DEFAULT_THRESHOLD_DBFS

        /** …and this much louder than the quietest of the [RECENT_HOPS] before it. */
        const val ONSET_RISE_DB = 8f

        /** Hops (≈ 21 ms each) a strum's rise is measured over: ≈ 130 ms. */
        const val RECENT_HOPS = 6

        /** The strings ring until the level drops this far below [GATE_DBFS] (the gate's hysteresis). */
        const val GATE_HYSTERESIS_DB = SignalGate.DEFAULT_HYSTERESIS_DB

        /** The frame starts at least this long after the strum, once every string has started. */
        const val ATTACK_SECONDS = 0.05

        /** Time between readings while the strings ring. */
        const val REREAD_SECONDS = 0.1

        /** A rise sooner than this after a strum belongs to it (strings start up to ≈ 0.1 s apart). */
        const val MIN_GAP_SECONDS = 0.15

        /** [samplesSinceStrum] before the first strum. */
        const val NO_STRUM = -1
    }
}
