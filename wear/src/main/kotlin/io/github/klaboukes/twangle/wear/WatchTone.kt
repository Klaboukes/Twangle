package io.github.klaboukes.twangle.wear

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.klaboukes.twangle.core.dsp.smoothing.InTuneDetector
import kotlin.math.abs

/**
 * The reading's tone, the phone's (`readingToneShare` in `:core:designsystem`'s `lens/LensTone.kt`, with the
 * phone's default in-tune window), copied as [WatchColors] are: the dot and the word go from the amber of a note
 * far off ([WatchColors.farOff]) toward green as the note nears the in-tune window, up to [NEAR_SHARE] of the way,
 * and the rest of the way while the watch says in tune ([tuned], eased 0..1), letting go as the reading leaves.
 * `WatchToneTest` keeps it equal to the phone's.
 */
object WatchTone {
    /** How far from amber toward green (0..1) a reading [cents] off is, with "in tune" eased in to [tuned]. */
    fun share(cents: Float, tuned: Float): Float {
        val near = if (cents.isNaN()) 0f else NEAR_SHARE * (1f - (abs(cents) - WINDOW) / EASE_CENTS).coerceIn(0f, 1f)
        val letGo = if (cents.isNaN()) 1f else (1f - (abs(cents) - EXIT) / LET_GO_CENTS).coerceIn(0f, 1f)
        return near + (1f - near) * tuned.coerceIn(0f, 1f) * letGo
    }

    /** The tone's colour for [share]. */
    fun colour(share: Float): Color = lerp(WatchColors.farOff, WatchColors.inTune, share)

    /** The phone's Relaxed window, which the watch's `InTuneDetector` uses: in at 5 cents, out past 7. */
    const val WINDOW = InTuneDetector.DEFAULT_ENTER_CENTS
    const val EXIT = InTuneDetector.DEFAULT_EXIT_CENTS

    /** The most of the way to green a note gets before it is in tune. */
    const val NEAR_SHARE = 0.35f

    /** Over how many cents outside the window the tone eases from amber. */
    const val EASE_CENTS = 5f

    /** How far past the release the in-tune green has gone. */
    const val LET_GO_CENTS = 4f
}
