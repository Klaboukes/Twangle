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
    /** Where the first button's slot starts, from the row's start. */
    val firstSlotStartDp: Float,
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
        // Spread evenly: the same gap before, between and after the buttons (Arrangement.SpaceEvenly).
        val start = if (spacingDp > 0f) 0f else (availableDp - slot * strings) / (strings + 1)
        return StringRowFit(squeezed = false, slotDp = slot, buttonScale = 1f, ringGapDp = ringGapDp, firstSlotStartDp = start)
    }
    val share = availableDp / strings
    val room = (share - SQUEEZED_GAP_DP).coerceAtLeast(0f)
    val ringGap = (ringGapDp * room / outer).coerceIn(minOf(MIN_RING_GAP_DP, ringGapDp), ringGapDp)
    val scale = ((room - ringGap * 2f) / buttonWidthDp).coerceIn(0f, 1f)
    return StringRowFit(squeezed = true, slotDp = share, buttonScale = scale, ringGapDp = ringGap, firstSlotStartDp = 0f)
}

/** Squeezed buttons keep at least this far apart, lock rings included. */
internal const val SQUEEZED_GAP_DP = 4f

/** The lock ring never comes closer to a squeezed button than this. */
internal const val MIN_RING_GAP_DP = 2f
