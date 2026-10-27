package io.github.klaboukes.twangle.feature.tuner.components

/**
 * How a row of string buttons fits its width, from a 4-string bass to an 8-string guitar. All
 * lengths in dp.
 *
 * While the buttons fit at full size they keep it and spread out ([squeezed] false, the six-string
 * guitar's look). Otherwise each gets an equal [slotDp] of the row, and the button and the room
 * round it for the lock ring shrink together ([buttonScale], [ringGapDp]) so neighbours keep
 * [SQUEEZED_GAP_DP] apart.
 */
internal data class StringRowFit(
    val squeezed: Boolean,
    /** Each button's share of the row while [squeezed]; its full touch width otherwise. */
    val slotDp: Float,
    /** The style's button size times this; 1 unless [squeezed]. */
    val buttonScale: Float,
    /** Room between the button and the lock ring round it. */
    val ringGapDp: Float,
)

/**
 * The row of [count] string buttons in [availableDp]: each [buttonWidthDp] wide with [ringGapDp]
 * of room round it, in a touch target at least [minSlotDp] wide. With [spacingDp] the row packs
 * the buttons that far apart (the compact layout's small row); without, it spreads them evenly over
 * the whole width.
 */
internal fun stringRowFit(
    availableDp: Float,
    count: Int,
    buttonWidthDp: Float,
    ringGapDp: Float,
    minSlotDp: Float,
    spacingDp: Float = 0f,
): StringRowFit {
    val outer = buttonWidthDp + ringGapDp * 2f
    val slot = maxOf(minSlotDp, outer)
    val strings = count.coerceAtLeast(1)
    val needed = slot * strings + spacingDp * (strings - 1)
    if (needed <= availableDp) {
        return StringRowFit(squeezed = false, slotDp = slot, buttonScale = 1f, ringGapDp = ringGapDp)
    }
    val share = availableDp / strings
    val room = (share - SQUEEZED_GAP_DP).coerceAtLeast(0f)
    val ringGap = (ringGapDp * room / outer).coerceIn(minOf(MIN_RING_GAP_DP, ringGapDp), ringGapDp)
    val scale = ((room - ringGap * 2f) / buttonWidthDp).coerceIn(0f, 1f)
    return StringRowFit(squeezed = true, slotDp = share, buttonScale = scale, ringGapDp = ringGap)
}

/**
 * How far in from each end of a row [availableDp] wide the first button's face starts and the last
 * one's ends, for [count] buttons [buttonWidthDp] wide laid out by [fit]: spread evenly, each in its
 * touch width, or squeezed, each in the middle of its equal share. The same at both ends. The strum
 * check's switch and the tuning's name line up with the faces, not the row's edges. [extraGapDp] is
 * taken by a gap between two buttons (after a banjo's short string), so it is no part of the spread.
 */
internal fun stringFaceInset(availableDp: Float, count: Int, buttonWidthDp: Float, fit: StringRowFit, extraGapDp: Float = 0f): Float {
    val strings = count.coerceAtLeast(1)
    val outer = buttonWidthDp * fit.buttonScale + fit.ringGapDp * 2f
    val slotStart = if (fit.squeezed) 0f else (availableDp - extraGapDp - fit.slotDp * strings) / (strings + 1)
    return slotStart + (fit.slotDp - outer) / 2f + fit.ringGapDp
}

/** Squeezed buttons keep at least this far apart, lock rings included. */
internal const val SQUEEZED_GAP_DP = 4f

/** The lock ring never comes closer to a squeezed button than this. */
internal const val MIN_RING_GAP_DP = 2f
