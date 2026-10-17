package io.github.klaboukes.twangle.core.dsp.trail

import kotlin.math.ceil

/**
 * The last few seconds of tuner readings, for the pitch trail (Phase 31): a line that draws the
 * note over time, so a held note, a bend or vibrato shows how steady it is.
 *
 * A ring buffer of fixed arrays, allocated once: [push] and [pushGap] write one slot each and
 * allocate nothing, so the tuner's ~47 readings a second make no garbage (CLAUDE.md, hard rule 2).
 * Each entry is a time stamp in seconds (the caller's clock, never going back), the note aimed
 * at ([targetAt], a MIDI number) and how far off it the reading was ([centsAt]), plus whether the
 * tuner showed it in tune ([inTuneAt]). A gap ("no note": silence, a tone of the app's own, a tool
 * using the microphone) has [NO_NOTE] as its target and NaN cents, so the line breaks there. The
 * pitch itself is `target + cents / 100` semitones, so a look can draw cents around the note or
 * the pitch on a scale of notes.
 *
 * **One writer, one reader on another thread.** The tuner's DSP coroutine writes; the draw phase
 * on the main thread reads, like the strobe's motion, so a new reading never recomposes anything.
 * The writer fills a slot and only then moves [end] on (a volatile write), so whatever the reader
 * sees below the [end] it read is complete. The ring holds [SLACK_ENTRIES] (about 0.7 s) more
 * than [readableEntries], so the oldest readable entries are not overwritten while a frame is drawn.
 * A reader that stalled that long can ask [overwritten] afterwards and skip the frame. Entries are
 * numbered from 0 for good (a `Long`, never reused), so a reader walks indices, not slots:
 *
 * ```
 * val end = history.end
 * for (i in history.firstWithin(seconds, end) until end) { history.timeAt(i); history.centsAt(i) ... }
 * ```
 * or [forEachWithin], which does the same without allocating (it is inline).
 *
 * [seconds] is how far back the history reaches (the look is not picked yet, so it is a
 * parameter); [frameSeconds] is the time between two readings, which sizes the ring.
 */
class PitchHistory(
    val seconds: Float = DEFAULT_SECONDS,
    frameSeconds: Float = DEFAULT_FRAME_SECONDS,
) {
    init {
        require(seconds > 0f) { "seconds must be positive, was $seconds" }
        require(frameSeconds > 0f) { "frameSeconds must be positive, was $frameSeconds" }
    }

    /**
     * How many of the newest entries a reader may walk: [seconds] worth of readings, with a margin
     * for readings that come a little closer together (another sample rate's hop is a few % shorter).
     */
    val readableEntries: Int = ceil(seconds / frameSeconds * FRAME_MARGIN).toInt() + 1

    /** Slots in the ring: [readableEntries] plus [SLACK_ENTRIES] that the writer may fill while a frame is drawn. */
    val capacity: Int = readableEntries + SLACK_ENTRIES

    private val times = FloatArray(capacity)
    private val targets = IntArray(capacity)
    private val cents = FloatArray(capacity)
    private val inTune = BooleanArray(capacity)

    /** One past the newest entry: the number of entries ever written. Moves on only after the entry is complete. */
    @Volatile
    var end: Long = 0L
        private set

    /** The first entry after the last [clear]: nothing older is read. */
    @Volatile
    private var start: Long = 0L

    /** Adds a reading at [timeSeconds]: [cents] off the note [targetMidi], and whether it was shown [inTune]. */
    fun push(timeSeconds: Float, targetMidi: Int, cents: Float, inTune: Boolean = false) {
        val index = end
        val slot = slotOf(index)
        times[slot] = timeSeconds
        targets[slot] = targetMidi
        this.cents[slot] = cents
        this.inTune[slot] = inTune
        end = index + 1 // Publishes the entry.
    }

    /** Adds a gap at [timeSeconds]: no note was heard (or none is shown), so the line breaks here. */
    fun pushGap(timeSeconds: Float) = push(timeSeconds, NO_NOTE, Float.NaN)

    /** Forgets every entry so far, e.g. when the tuner starts listening afresh. Allocates nothing. */
    fun clear() {
        start = end
    }

    /** The oldest entry a reader may walk while [end] is [end]: past the last [clear], and not about to be overwritten. */
    fun firstReadable(end: Long = this.end): Long = maxOf(start, end - readableEntries, 0L)

    /** Whether no entry is held: nothing pushed since the start or the last [clear]. */
    val isEmpty: Boolean get() = firstReadable() >= end

    /**
     * The oldest entry no more than [withinSeconds] older than the newest one (below [end]), or
     * [end] itself if there is none. Walks back from the newest entry; allocates nothing.
     */
    fun firstWithin(withinSeconds: Float = seconds, end: Long = this.end): Long {
        val first = firstReadable(end)
        if (first >= end) return end
        val oldest = timeAt(end - 1) - withinSeconds
        var index = end - 1
        while (index > first && timeAt(index - 1) >= oldest) index--
        return index
    }

    /**
     * True when entry [index] may have been written over since it was read: the reader stalled for
     * longer than the slack. Ask after walking from [index]; if true, skip that frame's drawing.
     */
    fun overwritten(index: Long): Boolean = index <= end - capacity

    /** The time stamp of entry [index], in seconds. */
    fun timeAt(index: Long): Float = times[slotOf(index)]

    /** The note entry [index] aimed at (a MIDI number), or [NO_NOTE] for a gap. */
    fun targetAt(index: Long): Int = targets[slotOf(index)]

    /** How far entry [index] was off its note, in cents (positive is sharp), or NaN for a gap. */
    fun centsAt(index: Long): Float = cents[slotOf(index)]

    /** Whether the tuner showed entry [index] as in tune. */
    fun inTuneAt(index: Long): Boolean = inTune[slotOf(index)]

    /** Whether entry [index] is a gap (no note). */
    fun isGapAt(index: Long): Boolean = targetAt(index) == NO_NOTE

    /**
     * Calls [action] for every entry no more than [withinSeconds] older than the newest one, oldest
     * first: its time, note ([NO_NOTE] for a gap), cents (NaN for a gap) and in-tune flag. Inline,
     * so walking the history in the draw phase allocates nothing.
     */
    inline fun forEachWithin(
        withinSeconds: Float = seconds,
        action: (timeSeconds: Float, targetMidi: Int, cents: Float, inTune: Boolean) -> Unit,
    ) {
        val end = end
        var index = firstWithin(withinSeconds, end)
        while (index < end) {
            action(timeAt(index), targetAt(index), centsAt(index), inTuneAt(index))
            index++
        }
    }

    private fun slotOf(index: Long): Int = (index % capacity).toInt()

    companion object {
        /** How far back the trail reaches by default, in seconds. */
        const val DEFAULT_SECONDS = 5f

        /** The tuner's time between readings at 48 kHz: one 1024-sample hop (≈ 21.3 ms). */
        const val DEFAULT_FRAME_SECONDS = 1024f / 48_000f

        /** The target of a gap: no note. */
        const val NO_NOTE = Int.MIN_VALUE

        /** Entries the writer may add while a frame is drawn without touching what the reader walks: ≈ 0.7 s. */
        const val SLACK_ENTRIES = 32

        /** Room for readings up to 10 % closer together than [PitchHistory]'s `frameSeconds` says. */
        private const val FRAME_MARGIN = 1.1f
    }
}
