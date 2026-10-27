package io.github.klaboukes.twangle.feature.practice

/**
 * How Chords, Scales and the Capo helper arrange themselves, picked from the room they get (in dp), not from the
 * device ([practiceLayoutFor]).
 */
internal enum class PracticeLayout {
    /** Upright on a phone: one column, at most 480 dp wide, from the top down. */
    COLUMN,

    /**
     * Upright on a large window (a tablet held upright, Phase 52): the same column, its rows at most 480 dp wide,
     * but the chart or the neck may use the window's whole width, and the capo helper stands in the middle of the
     * height, as the metronome's column does.
     */
    LARGE_COLUMN,

    /**
     * On its side on a phone (≥ 600 dp wide and wider than tall, but short): two halves side by side, the tool
     * button's row at the top of the start half and the rest in the end half, as the height is too short to stack.
     */
    HALVES,

    /**
     * On its side on a large window (a tablet on its side, Phase 52): the tool button's row and the name button share
     * one row at the top, and the chart or the neck runs the whole width under it; the capo helper's two halves
     * stand under that row, in the middle of the height.
     */
    LARGE_SIDE,
}

/**
 * The arrangement for a window of [widthDp] × [heightDp]: on its side from [WIDE_MIN_WIDTH_DP] wide (and wider
 * than tall), as the metronome; large once both sides are at least [LARGE_MIN_DP], which no phone is (the tallest
 * phones on their side are about 430 dp high, the widest upright about 440 dp across) and every tablet is.
 */
internal fun practiceLayoutFor(widthDp: Float, heightDp: Float): PracticeLayout {
    val wide = widthDp >= WIDE_MIN_WIDTH_DP && widthDp > heightDp
    val large = minOf(widthDp, heightDp) >= LARGE_MIN_DP
    return when {
        wide && large -> PracticeLayout.LARGE_SIDE
        wide -> PracticeLayout.HALVES
        large -> PracticeLayout.LARGE_COLUMN
        else -> PracticeLayout.COLUMN
    }
}

/** On its side from this width, as the metronome's layout. */
internal const val WIDE_MIN_WIDTH_DP = 600f

/** A large window from this much both ways, as the tuner's bigger upright column (`columnScaleFor`). */
internal const val LARGE_MIN_DP = 560f
