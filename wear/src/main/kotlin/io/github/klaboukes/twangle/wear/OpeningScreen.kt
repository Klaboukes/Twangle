package io.github.klaboukes.twangle.wear

import androidx.core.splashscreen.SplashScreenViewProvider

/** How long the opening screen takes to fade into the tuner. */
private const val FADE_MS = 200L

/**
 * Fades the opening screen (the launcher icon, still, on black: `splash_icon.xml`) into the tuner as soon as the
 * app is ready. The watch's icon doesn't move, so unlike the phone's (`:app`, OpeningScreen.kt) there is no
 * animation to wait for.
 */
internal fun SplashScreenViewProvider.fadeOut() {
    view.animate()
        .alpha(0f)
        .setDuration(FADE_MS)
        .withEndAction { remove() }
}
