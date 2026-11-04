package io.github.klaboukes.twangle.feature.tuner

import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.feature.tuner.components.MARK_GAP
import io.github.klaboukes.twangle.feature.tuner.components.MARK_SIZE
import io.github.klaboukes.twangle.feature.tuner.components.StrumMark

// The sizes the tuner's layouts share.

/** Between the wide layout's pegs and the strum check's button beside them. */
internal val TOOL_STRINGS_GAP = 8.dp

internal val SECTION_SPACING = 24.dp

/** Upright, the ruler is drawn big (the needle, 2026-11-03) from this column height; a shorter one keeps it plain. */
internal val LARGE_RULER_MIN_HEIGHT = 640.dp

/** Between the ruler and the lens under it. */
internal val RULER_METER_GAP = 12.dp

/**
 * Above the pegs, room kept for a strum's marks ([StrumMark], Phase 74): the mark, its gap to the face and a little
 * clear of what is above (upright the strum check's button, whose outline sits 2 dp inside its row).
 */
internal val MARKS_ROOM = MARK_SIZE + MARK_GAP + 2.dp

/** The signal bars as drawn, and their touch area's width at the picker chip row's start. */
internal val SIGNAL_BARS_SIZE = 24.dp
internal val SIGNAL_BARS_SLOT = 48.dp

/** The single column's text margin at each side. */
internal val COLUMN_PADDING = 16.dp

/** The upright gauge's margin at each side of the screen (the approved 1.1 mock-up's 8 dp; a tool's panel spans it). */
internal val METER_MARGIN = 8.dp

/** The lens's sides, this far in from the gauge's margin: 20 dp from the screen's edges (*The lens*, Size). */
internal val LENS_INSET = 12.dp

/** On its side the readout pane leaves this much above and below the lens and the word, for the lens's glow. */
internal val LENS_GLOW_ROOM = 24.dp

/** The free height above the lens, against 1 above the pegs (the mock-up's `.grow.top`). */
internal const val TOP_GAP_WEIGHT = 0.5f

/** The single column, and the controls pane, never get wider than this. */
internal val COLUMN_MAX_WIDTH = 480.dp

/** On a wide screen the gauge stops growing here. */
internal val READOUT_PANE_MAX_WIDTH = 560.dp

/** A phone on its side shrinks the gauge to fit, but not below this. */
internal val GAUGE_MIN_WIDTH = 160.dp

/** Readout panes shorter than this (a phone on its side) set the word, or a tool's lines, a little smaller. */
internal val COMPACT_PANE_HEIGHT = 420.dp

/** In the compact layout a tool's panel grows with the screen, up to this. */
internal val COMPACT_GAUGE_MAX_WIDTH = 840.dp

/** The room at each end of the picker chip's row (the [TopBar]'s least): focus mode's 44 dp button outline and 8 dp clear of the chip. */
internal val FOCUS_BUTTON_SLOT = 52.dp

/** The gear's 48 dp touch area reaches this far past its 44 dp outline at each side. */
internal val GEAR_OUTLINE_INSET = 2.dp

/** Above and below the compact layout's column of a tool's lines: focus mode's exit button (48 dp at 8 dp from the top) stays clear. */
internal val COMPACT_CORNER_CLEARANCE = 56.dp

/** Between a tool's lines and what's under them in the compact layout. */
internal val COMPACT_NOTE_SPACING = 12.dp

/** Between the gauge and the word in the two-pane layout, where height is scarce. */
internal val GAUGE_NOTE_SPACING = 8.dp

/** The upright column keeps this far from the top and bottom of its space. */
internal val EDGE_INSET = 8.dp

/** Above and below the controls pane's column, so it keeps a margin when it scrolls (a large font on a phone on its side). */
internal val CONTROLS_PANE_CLEARANCE = 56.dp

/** The strum check's button's row above the pegs: its 48 dp touch target, kept whether it shows or not. */
internal val STRUM_ROW_HEIGHT = 48.dp

/**
 * The strum check's button's row, the room for a strum's marks and the string row (48 dp). Until the 12th-fret check
 * moved to the picker sheet (Phase 71, 2026-11-03) its button stood in that row, at the other end.
 */
internal val GUITAR_SLOT_HEIGHT = STRUM_ROW_HEIGHT + MARKS_ROOM + 48.dp
