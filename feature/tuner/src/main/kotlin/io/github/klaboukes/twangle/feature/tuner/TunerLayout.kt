package io.github.klaboukes.twangle.feature.tuner

/** How [TunerContent] arranges itself in the space it gets. */
enum class TunerLayout {
    /** Everything in one centred column: phones held upright, tablets in portrait, split screen. */
    SINGLE_COLUMN,

    /** Gauge and note on one side, mode switch, tuning and strings on the other: landscape, wide screens. */
    TWO_PANE,
}

/**
 * Two panes once the space is wider than tall and wide enough for two columns of controls
 * ([TWO_PANE_MIN_WIDTH_DP], the Material 3 "medium" width class). A phone on its side is too short
 * for the single column (it would have to scroll), and a near-square window keeps the column, which
 * still fits there.
 */
fun tunerLayoutFor(widthDp: Float, heightDp: Float): TunerLayout =
    if (widthDp >= TWO_PANE_MIN_WIDTH_DP && widthDp > heightDp) TunerLayout.TWO_PANE else TunerLayout.SINGLE_COLUMN

const val TWO_PANE_MIN_WIDTH_DP = 600f
