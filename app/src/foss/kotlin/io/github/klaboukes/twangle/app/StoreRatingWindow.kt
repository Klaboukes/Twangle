package io.github.klaboukes.twangle.app

import android.content.Context
import io.github.klaboukes.twangle.feature.tuner.RatingWindow

/**
 * The free build (-Ptwangle.foss=true, for F-Droid and IzzyOnDroid; Phase 92) has no rating window:
 * Google's In-App Review library is not open source, so this build leaves it out entirely. Its twin
 * in src/play shows Google Play's window.
 */
@Suppress("UNUSED_PARAMETER")
fun storeRatingWindow(appContext: Context): RatingWindow = RatingWindow.NONE
