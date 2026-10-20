package io.github.klaboukes.twangle.feature.tuner

/** How [TunerContent] arranges itself in the space it gets. */
enum class TunerLayout {
    /** Everything in one centred column: phones held upright, tablets in portrait, split screen. */
    SINGLE_COLUMN,

    /** Gauge and note on one side, mode switch, tuning and strings on the other: landscape, wide screens. */
    TWO_PANE,

    /**
     * Landscape with only what matters while tuning: a big meter beside the note, the cents and (in
     * guitar mode) a small string row. A tap brings back the [TWO_PANE] layout with the controls for
     * a few seconds.
     */
    COMPACT,
}

/**
 * Landscape once the space is wider than tall and wide enough for two columns of controls
 * ([TWO_PANE_MIN_WIDTH_DP], the Material 3 "medium" width class): [TunerLayout.TWO_PANE]. A phone on
 * its side is too short for the single column (it would have to scroll), and a near-square window
 * keeps the column, which still fits there.
 *
 * With [compactLandscape] (focus mode) every window wider than tall is [TunerLayout.COMPACT], however
 * narrow: its full-screen meter hides the rail and the system bars, which changes the width, so the
 * layout must not follow the width. With a minimum width a Galaxy S26 with navigation buttons (578 dp
 * beside the rail and the buttons, 663 dp without) switched between the two about 40 times a second.
 */
fun tunerLayoutFor(widthDp: Float, heightDp: Float, compactLandscape: Boolean): TunerLayout = when {
    widthDp <= heightDp -> TunerLayout.SINGLE_COLUMN
    compactLandscape -> TunerLayout.COMPACT
    widthDp < TWO_PANE_MIN_WIDTH_DP -> TunerLayout.SINGLE_COLUMN
    else -> TunerLayout.TWO_PANE
}

const val TWO_PANE_MIN_WIDTH_DP = 600f

/** How long the controls stay after a tap in the [TunerLayout.COMPACT] layout, before TalkBack's own timeout setting. */
const val COMPACT_CONTROLS_SHOWN_MS = 5_000L
