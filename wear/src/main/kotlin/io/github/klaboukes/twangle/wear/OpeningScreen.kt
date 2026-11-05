package io.github.klaboukes.twangle.wear

import android.os.SystemClock
import androidx.core.splashscreen.SplashScreenViewProvider

/** How long the opening screen takes to fade into the tuner. */
private const val FADE_MS = 200L

/**
 * Lets the opening screen's icon finish its animation (the harmonics swinging into place, Wear OS 4 and up,
 * `@integer/opening_animation_ms`) before the screen fades into the tuner, so a fast start never cuts the
 * string off mid-swing; the phone app's own (`:app`, OpeningScreen.kt) does the same. A slower start fades at
 * once; on Wear OS 3, with no animation, both times read 0 and it fades at once too.
 */
internal fun SplashScreenViewProvider.fadeOutOnceStill() {
    val stillAt = iconAnimationStartMillis + iconAnimationDurationMillis
    val wait = (stillAt - SystemClock.uptimeMillis()).coerceIn(0L, iconAnimationDurationMillis)
    view.animate()
        .alpha(0f)
        .setStartDelay(wait)
        .setDuration(FADE_MS)
        .withEndAction { remove() }
}
