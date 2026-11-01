package io.github.klaboukes.twangle.feature.tuner

/**
 * How many of the [MAX_BARS] signal bars light up for a reception figure (`ReceptionTracker`, 0..1),
 * like a phone's signal bars. A bar lights at its threshold in [THRESHOLDS] and only goes out again
 * once the figure falls [MARGIN] below it, so a figure hovering at a threshold can't make a bar
 * flicker (the figure itself is already smoothed). Without a note ([update] with NaN) the last
 * count stays, shown dimmed ([hasNote] false), so the bars don't empty and refill between plucks.
 * Starts at 0. Allocation-free and not thread-safe.
 */
class SignalBars {
    /** Bars lit, 0..[MAX_BARS]. */
    var bars: Int = 0
        private set

    /** Whether the count is for a note sounding now (false: the last note's, or none yet). */
    var hasNote: Boolean = false
        private set

    /** Takes the current [reception] (NaN without a note) and returns [bars]. */
    fun update(reception: Float): Int {
        hasNote = !reception.isNaN()
        if (!hasNote) return bars
        while (bars < MAX_BARS && reception >= THRESHOLDS[bars]) bars++
        while (bars > 0 && reception < THRESHOLDS[bars - 1] - MARGIN) bars--
        return bars
    }

    companion object {
        const val MAX_BARS = 4

        /** The reception from which bar 1, 2, 3 and 4 light; 4 bars is `ReceptionTracker.CLEAR_RECEPTION`-ish. */
        private val THRESHOLDS = floatArrayOf(0.05f, 0.35f, 0.65f, 0.9f)

        /** A lit bar goes out only this far below its threshold. */
        const val MARGIN = 0.08f
    }
}

/** The figures the signal bars show when tapped: what the tuner hears now. */
data class SignalFigures(
    /** The input's level in dBFS: the note's (smoothed) while one sounds, else the room's. */
    val levelDbfs: Float,
    /** The room's background level in dBFS, NaN until known. */
    val noiseFloorDbfs: Float,
    /** How clean the note's pitch is, 0..1 (smoothed), NaN without a note. */
    val clarity: Float,
)
