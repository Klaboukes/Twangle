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

/**
 * How much bigger the [TunerLayout.SINGLE_COLUMN] layout is drawn in a window of [widthDp] by
 * [heightDp]: 1 on every phone, more on a tablet held upright (Phase 52). The column is laid out as on
 * a phone of [COLUMN_SCALE_BASE_WIDTH_DP] by [COLUMN_SCALE_BASE_HEIGHT_DP] or larger and then drawn
 * that much bigger, so the meter, the note, the string buttons, the text and the gaps all grow
 * together and keep the phone's proportions. Without it the column stopped at 480 dp: on a Lenovo Tab
 * M11 upright (800 dp) the meter was 464 dp across with about 290 dp of empty screen under the note.
 *
 * It grows only where the window is both wider and taller than those (a phone is at most about
 * 480 dp wide, and on its side far too short), and smoothly with the window, up to [COLUMN_MAX_SCALE].
 */
fun columnScaleFor(widthDp: Float, heightDp: Float): Float =
    minOf(widthDp / COLUMN_SCALE_BASE_WIDTH_DP, heightDp / COLUMN_SCALE_BASE_HEIGHT_DP, COLUMN_MAX_SCALE).coerceAtLeast(1f)

/** The phone-like window the upright column is laid out in before [columnScaleFor] makes it bigger. */
const val COLUMN_SCALE_BASE_WIDTH_DP = 560f
const val COLUMN_SCALE_BASE_HEIGHT_DP = 760f

/** The upright column grows at most this much (on a large tablet: 720 dp across). */
const val COLUMN_MAX_SCALE = 1.5f

/** How long the controls stay after a tap in the [TunerLayout.COMPACT] layout, before TalkBack's own timeout setting. */
const val COMPACT_CONTROLS_SHOWN_MS = 5_000L
