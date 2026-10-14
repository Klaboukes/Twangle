package io.github.klaboukes.twangle.feature.tuner.tour

import androidx.annotation.StringRes
import io.github.klaboukes.twangle.core.settings.TunerMode
import io.github.klaboukes.twangle.feature.tuner.R

/**
 * One step of the first-start tour: the part of the window it lights up, the side its balloon would
 * rather be on ([preferredSide], [BalloonSide.ABOVE] or [BalloonSide.BELOW]), whose corner the
 * spotlight copies ([corner]) and the balloon's [text]. In the order of the approved mock-up
 * (docs/design/tour-preview.html).
 */
enum class TourPart(val preferredSide: BalloonSide, val corner: TourCorner, @param:StringRes val text: Int) {
    /** The meter with its needle or wave, and the note under it. */
    NOTE(BalloonSide.BELOW, TourCorner.CARD, R.string.tour_note),

    /** The string buttons (tap one to lock it). */
    STRINGS(BalloonSide.ABOVE, TourCorner.STRING, R.string.tour_strings),

    /** The instrument-and-tuning button. */
    PICKER(BalloonSide.ABOVE, TourCorner.BUTTON, R.string.tour_picker),

    /** The guitar/chromatic mode switch. */
    MODE(BalloonSide.BELOW, TourCorner.BUTTON, R.string.tour_mode),

    /** The navigation bar's tabs (the rail's on a phone on its side). */
    NAVIGATION(BalloonSide.ABOVE, TourCorner.CARD, R.string.tour_navigation),
    ;

    companion object {
        /**
         * The tour's steps in [mode]: all five in guitar mode; in chromatic mode (only when started
         * again from Settings) without the strings and the instrument-and-tuning button, which
         * aren't shown there.
         */
        fun stepsFor(mode: TunerMode): List<TourPart> = when (mode) {
            TunerMode.AUTO -> entries
            TunerMode.CHROMATIC -> listOf(NOTE, MODE, NAVIGATION)
        }
    }
}

/** Which of the style's corners a spotlight copies: its buttons', its string buttons' or its cards'. */
enum class TourCorner { BUTTON, STRING, CARD }
