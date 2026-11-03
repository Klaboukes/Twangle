package io.github.klaboukes.twangle.feature.tuner

/** How [TunerContent] arranges itself in the space it gets. */
enum class TunerLayout {
    /** Everything in one centred column: phones held upright, tablets in portrait, split screen. */
    SINGLE_COLUMN,

    /**
     * The wide layout (2.0, Phase 73): any window wider than tall. The ruler across the full width, the lens wide
     * under it and the word under that, with the pegs (and the rail beside the screen); in focus mode nothing but
     * the ruler, the lens and the word, the string small beside the word, until a tap brings the pegs and the tabs
     * back for a few seconds.
     */
    WIDE,

    /** The 12th-fret check's panel beside its controls on its side (1.1's arrangement, kept for the check). */
    TWO_PANE,

    /**
     * Focus mode with the 12th-fret check on: its panel as big as the screen allows beside its lines. A tap brings back
     * the controls for a few seconds, as in the [WIDE] layout.
     */
    COMPACT,
}

/**
 * A window wider than tall gets the [TunerLayout.WIDE] layout (2.0, Phase 73), focus mode or not, however narrow: a
 * phone on its side, a tablet on its side, split screen on its side. The layout never follows the width: focus mode's
 * full-screen tuner hides the rail and the system bars, which changes the width (a Galaxy S26 with navigation buttons:
 * 578 dp beside the rail and the buttons, 663 dp without), and a layout that followed it switched about 40 times a
 * second. Upright and square windows keep the [TunerLayout.SINGLE_COLUMN].
 *
 * With a [toolPanel] (the 12th-fret check's panel in the lens's place) a window wider than tall keeps the layouts of
 * 1.1: in [focusMode] [TunerLayout.COMPACT], else two panes once the window is wide enough for two columns of
 * controls ([TWO_PANE_MIN_WIDTH_DP], the Material 3 "medium" width class) and the single column below that. The
 * strum check and the chord name have no panel since Phase 74: they show in the plain tuner's places, so the wide
 * layout and focus mode keep them.
 */
fun tunerLayoutFor(widthDp: Float, heightDp: Float, focusMode: Boolean, toolPanel: Boolean = false): TunerLayout = when {
    widthDp <= heightDp -> TunerLayout.SINGLE_COLUMN
    !toolPanel -> TunerLayout.WIDE
    focusMode -> TunerLayout.COMPACT
    widthDp < TWO_PANE_MIN_WIDTH_DP -> TunerLayout.SINGLE_COLUMN
    else -> TunerLayout.TWO_PANE
}

const val TWO_PANE_MIN_WIDTH_DP = 600f

/** How long the controls stay after a tap in focus mode, before TalkBack's own timeout setting. */
const val COMPACT_CONTROLS_SHOWN_MS = 5_000L
