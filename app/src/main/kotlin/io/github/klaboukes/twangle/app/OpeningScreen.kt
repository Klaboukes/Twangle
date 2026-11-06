package io.github.klaboukes.twangle.app

import android.os.SystemClock
import androidx.core.splashscreen.SplashScreenViewProvider

/** How long the opening screen takes to fade into the app. */
private const val FADE_MS = 200L

/**
 * Lets the opening screen's icon finish its animation (the harmonics swinging into place, Android 12 and up,
 * `@integer/opening_animation_ms`) before the screen fades into the app, so a fast start never cuts the
 * string off mid-swing. A start slower than the animation fades at once; on Android 8 to 11, with no
 * animation, both times read 0 and it fades at once too.
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
