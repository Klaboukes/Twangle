package io.github.klaboukes.twangle.feature.tuner.tour

import androidx.annotation.StringRes
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.feature.tuner.R

/**
 * One step of the first-start tour: the part of the window it lights up, the side its balloon would
 * rather be on ([preferredSide], [BalloonSide.ABOVE] or [BalloonSide.BELOW]), whose corner the
 * spotlight copies ([corner]), the balloon's [text], and whether the part is a control people tap
 * ([interactive]), which other steps' balloons keep clear of where they can. Listed in the tour's
 * order upright; the tour itself goes in the current layout's ([tourOrder]). Four steps (Phase 43):
 * the mode switch, focus mode and the navigation tabs explain themselves, so they have none.
 */
enum class TourPart(
    val preferredSide: BalloonSide,
    val corner: TourCorner,
    @param:StringRes val text: Int,
    val interactive: Boolean,
) {
    /**
     * The meter and the note under it, lit together: the meter's look (also with the wave display; the
     * strum panel in its place) and the note's block (its name with the cents pill and frequency, or
     * with the wave).
     */
    READOUT(BalloonSide.BELOW, TourCorner.CARD, R.string.tour_readout, interactive = false),

    /** The instrument button (a headstock) at the start of the row above the strings, which opens the instrument and tuning panel. */
    PICKER(BalloonSide.ABOVE, TourCorner.BUTTON, R.string.tour_picker, interactive = true),

    /** The string buttons: a tap locks one, a long press plays its reference tone (only the tour says so). */
    STRINGS(BalloonSide.BELOW, TourCorner.STRING, R.string.tour_strings, interactive = true),

    /** The "more tools" button at the end of the row above the strings (or the strum check's switch in its place, [tourTextOf]). */
    STRUM(BalloonSide.ABOVE, TourCorner.BUTTON, R.string.tour_strum, interactive = true),
    ;

    companion object {
        /**
         * The tour's steps in [mode]: all four in guitar mode, without the tools button where the
         * tuner shows none ([toolsButton]: no tool offered; the 12-string, without the strum check,
         * still has the 12th-fret check under it); in chromatic mode (only when started again from
         * Settings) the meter and note alone, as the instrument button, the tools button and the
         * strings aren't shown there.
         */
        fun stepsFor(mode: TunerMode, toolsButton: Boolean = true): List<TourPart> = when (mode) {
            TunerMode.AUTO -> entries.filter { toolsButton || it != STRUM }
            TunerMode.CHROMATIC -> listOf(READOUT)
        }
    }
}

/** Which of the style's corners a spotlight copies: its buttons', its string buttons' or its cards'. */
enum class TourCorner { BUTTON, STRING, CARD }

/**
 * [part]'s balloon text. Where the tuner offers more than one tool ([moreTools], see
 * `tunerToolsFor`), the "more tools" button stands in the strum check's switch's place, so that step
 * names it ("More tools, like strum check"); otherwise every step's own [TourPart.text].
 */
@StringRes
fun tourTextOf(part: TourPart, moreTools: Boolean): Int = when {
    moreTools && part == TourPart.STRUM -> R.string.tour_more_tools
    else -> part.text
}
