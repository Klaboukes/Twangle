package io.github.klaboukes.twangle.feature.tuner.tour

import androidx.annotation.StringRes
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.feature.tuner.R

/**
 * One step of the first-start tour: the part of the window it lights up, the side its balloon would
 * rather be on ([preferredSide], [BalloonSide.ABOVE] or [BalloonSide.BELOW]), whose corner the
 * spotlight copies ([corner]), the balloon's [text], and whether the part is a control people tap
 * ([interactive]), which other steps' balloons keep clear of where they can. Listed in the upright
 * layout's reading order; the tour itself goes in the current layout's ([tourOrder]).
 */
enum class TourPart(
    val preferredSide: BalloonSide,
    val corner: TourCorner,
    @param:StringRes val text: Int,
    val interactive: Boolean,
) {
    /** The guitar/chromatic mode switch. */
    MODE(BalloonSide.BELOW, TourCorner.BUTTON, R.string.tour_mode, interactive = true),

    /** The meter (its look, also with the wave display; the strum panel in its place). */
    METER(BalloonSide.BELOW, TourCorner.CARD, R.string.tour_meter, interactive = false),

    /** The note under the meter: its name with the cents pill and frequency, or with the wave. */
    NOTE(BalloonSide.BELOW, TourCorner.CARD, R.string.tour_note, interactive = false),

    /** The navigation bar's Instrument item (the rail's on a phone on its side), which opens the instrument and tuning panel. */
    PICKER(BalloonSide.ABOVE, TourCorner.CARD, R.string.tour_picker, interactive = true),

    /** The strum check's switch above the strings (or the "more tools" button in its place, [tourTextOf]). */
    STRUM(BalloonSide.ABOVE, TourCorner.BUTTON, R.string.tour_strum, interactive = true),

    /** The string buttons (tap one to lock it). */
    STRINGS(BalloonSide.BELOW, TourCorner.STRING, R.string.tour_strings, interactive = true),

    /** The navigation bar's Metronome and Settings tabs (the rail's on a phone on its side). */
    NAVIGATION(BalloonSide.ABOVE, TourCorner.CARD, R.string.tour_navigation, interactive = true),
    ;

    companion object {
        /**
         * The tour's steps in [mode]: all seven in guitar mode, without the strum check's switch where
         * strum check isn't offered ([strumCheck]; every instrument has it today, up to 8 strings); in
         * chromatic mode (only when started again from Settings) without the strum check and the
         * strings, which aren't shown there.
         */
        fun stepsFor(mode: TunerMode, strumCheck: Boolean = true): List<TourPart> = when (mode) {
            TunerMode.AUTO -> entries.filter { strumCheck || it != STRUM }
            TunerMode.CHROMATIC -> listOf(MODE, METER, NOTE, PICKER, NAVIGATION)
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
fun tourTextOf(part: TourPart, moreTools: Boolean): Int =
    if (moreTools && part == TourPart.STRUM) R.string.tour_more_tools else part.text
