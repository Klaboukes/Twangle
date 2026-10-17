package io.github.klaboukes.twangle.app

import android.content.pm.ActivityInfo
import androidx.compose.runtime.saveable.Saver

/**
 * What `MainActivity` asks Android for while focus mode comes and goes (Phase 36). In [focusMode] the
 * whole app stays on its side, every tab, so the screen doesn't turn on each tab switch:
 * `SENSOR_LANDSCAPE` turns it on its side also with auto-rotate off, and still either way round with
 * the phone. Otherwise the request is let go (`UNSPECIFIED`, the manifest's default) and the screen
 * follows the phone and auto-rotate as before. Large screens may ignore the request (Android 16+);
 * there the window's shape decides the layout.
 *
 * With auto-rotate off, Android keeps the screen where it finds it when the request is let go (it locks
 * that rotation: found on the Xiaomi 12X), so focus mode switched off would leave the app on its side.
 * So when the window was upright before focus mode turned it ([uprightBefore]), it asks for upright
 * first ([turningBack]) and lets go once the window is upright again. Kept across the activity's
 * recreation on turning ([Saver]).
 */
data class FocusTurn(
    val focusMode: Boolean,
    val uprightBefore: Boolean,
    val turningBack: Boolean = false,
) {
    /**
     * The next state, once focus mode is [focusMode] and the window [upright]. [autoRotate] (whether
     * the phone's auto-rotate is on) is read only when focus mode goes off: with it on, the phone
     * itself decides and nothing needs turning back.
     */
    fun next(focusMode: Boolean, upright: Boolean, autoRotate: () -> Boolean): FocusTurn = when {
        focusMode && !this.focusMode -> FocusTurn(focusMode = true, uprightBefore = upright)
        !focusMode && this.focusMode ->
            copy(focusMode = false, turningBack = uprightBefore && !upright && !autoRotate())
        turningBack && upright -> copy(turningBack = false)
        else -> this
    }

    /** The orientation to ask for. */
    val orientation: Int
        get() = when {
            focusMode -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            turningBack -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

    companion object {
        val Saver: Saver<FocusTurn, BooleanArray> = Saver(
            save = { booleanArrayOf(it.focusMode, it.uprightBefore, it.turningBack) },
            restore = { FocusTurn(it[0], it[1], it[2]) },
        )
    }
}
