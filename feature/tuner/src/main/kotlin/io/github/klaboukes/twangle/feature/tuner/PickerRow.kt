package io.github.klaboukes.twangle.feature.tuner

import kotlin.math.floor
import kotlin.math.min

/**
 * Where the tuning picker and the instrument picker after it sit above the string row, and how wide
 * each may be, in pixels from the row's start. On [oneLine] both share a line, their names set
 * [textScale] times their size; otherwise each gets a line of its own, the instrument under the
 * tuning, at full size.
 */
internal data class PickerRow(
    val oneLine: Boolean,
    val tuningX: Int,
    val instrumentX: Int,
    val tuningWidth: Int,
    val instrumentWidth: Int,
    val textScale: Float,
)

/**
 * The two pickers in a row [width] wide, which may reach [sideRoom] past each end (into the
 * column's margin). [tuningWidth] and [instrumentWidth] are the pickers at full size, of which
 * [fixedWidth] (padding and arrow) never shrinks; only the names do.
 *
 * The tuning picker stays in the middle, where it was before there was an instrument picker, so the
 * string-lock tip keeps its corner beside it; the instrument follows it ("Standard ▾ Guitar ▾"). In
 * that order, until something fits:
 * 1. both names shrink alike, down to [SHRINK_IN_PLACE] (wide faces such as Space Age's),
 * 2. at that size both move towards the start just enough (long names such as "7-snarige gitaar"),
 * 3. both names shrink further, down to [MIN_TEXT_SCALE], filling the row from edge to edge,
 * 4. each goes centred on a line of its own, at full size (very large fonts).
 */
internal fun pickerRowFor(
    width: Int,
    tuningWidth: Int,
    instrumentWidth: Int,
    fixedWidth: Int,
    sideRoom: Int = 0,
): PickerRow {
    val tuningText = (tuningWidth - fixedWidth).coerceAtLeast(0)
    val instrumentText = (instrumentWidth - fixedWidth).coerceAtLeast(0)
    val start = -sideRoom
    val end = width + sideRoom
    fun tuningAt(scale: Float) = fixedWidth + floor(tuningText * scale).toInt()
    fun instrumentAt(scale: Float) = fixedWidth + floor(instrumentText * scale).toInt()
    fun row(tuningX: Int, scale: Float) =
        PickerRow(true, tuningX, tuningX + tuningAt(scale), tuningAt(scale), instrumentAt(scale), scale)

    // 1. The tuning centred, the instrument ending by the row's end at the latest.
    val roomCentred = end - width / 2f - 1.5f * fixedWidth
    val textCentred = tuningText / 2f + instrumentText
    val centredScale = if (textCentred <= 0f) 1f else min(1f, roomCentred / textCentred)
    if (centredScale >= SHRINK_IN_PLACE) {
        val tuningX = (width - tuningAt(centredScale)) / 2
        // Never past the end, whatever the rounding.
        return row(min(tuningX, end - tuningAt(centredScale) - instrumentAt(centredScale)), centredScale)
    }
    // 2. Moved towards the start just enough.
    val pairAtShrunk = tuningAt(SHRINK_IN_PLACE) + instrumentAt(SHRINK_IN_PLACE)
    if (pairAtShrunk <= end - start) return row(end - pairAtShrunk, SHRINK_IN_PLACE)
    // 3. Smaller still, from edge to edge.
    val fillScale = (end - start - 2f * fixedWidth) / (tuningText + instrumentText)
    if (fillScale >= MIN_TEXT_SCALE) return row(end - tuningAt(fillScale) - instrumentAt(fillScale), fillScale)
    // 4. A line each.
    return PickerRow(
        oneLine = false,
        tuningX = (width - tuningWidth) / 2,
        instrumentX = (width - instrumentWidth) / 2,
        tuningWidth = min(tuningWidth, width),
        instrumentWidth = min(instrumentWidth, width),
        textScale = 1f,
    )
}

/** The pickers' names shrink this far to keep the tuning in the middle before they move aside. */
internal const val SHRINK_IN_PLACE = 0.85f

/** The pickers' names never get smaller than this on one line; then each gets its own. */
internal const val MIN_TEXT_SCALE = 0.7f
