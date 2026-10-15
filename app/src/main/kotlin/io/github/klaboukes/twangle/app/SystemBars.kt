package io.github.klaboukes.twangle.app

import android.content.ContentResolver
import android.provider.Settings

/** Android's `Settings.Secure.NAVIGATION_MODE` value for gesture navigation (0 is 3-button, 1 is 2-button). */
private const val NAVIGATION_MODE_GESTURES = 2

/**
 * Whether the phone shows navigation buttons (3-button or 2-button navigation) rather than the
 * gesture bar. Only then does MainActivity keep the system's scrim behind the navigation bar, so
 * the buttons stay readable; behind the gesture handle the style's background runs to the edge.
 *
 * The phone's navigation mode ([navigationMode], the secure setting `navigation_mode`) decides when
 * it is known. Without it, the window's tappable-element insets in pixels on the sides a navigation
 * bar can sit on: the gesture bar is not tappable, so all three are 0 with gestures (the status bar,
 * at the top, is left out). MIUI reports the gesture bar as tappable, but it keeps the setting.
 */
internal fun navigationButtonsShown(navigationMode: Int?, left: Int, right: Int, bottom: Int): Boolean =
    if (navigationMode != null) {
        navigationMode != NAVIGATION_MODE_GESTURES
    } else {
        left > 0 || right > 0 || bottom > 0
    }

/** The phone's navigation mode (0 is 3-button, 1 is 2-button, 2 is gestures), or null when the phone doesn't say. */
internal fun ContentResolver.navigationMode(): Int? = try {
    Settings.Secure.getInt(this, "navigation_mode", -1).takeIf { it >= 0 }
} catch (_: SecurityException) {
    null
}
