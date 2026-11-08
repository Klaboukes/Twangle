package io.github.klaboukes.twangle.core.designsystem

import android.app.UiModeManager
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Whether the phone itself asks for high contrast (Phase 86), which the high-contrast look follows while the player
 * hasn't set its switch (`AppSettings.highContrastOn`): Android 14's contrast level is high (`UiModeManager.getContrast`,
 * 0 standard to 1 high, [HIGH_CONTRAST_LEVEL] or more; *Medium* stays Ink, which already reads at 4.5:1), or the phone's
 * high-contrast text is on (every Android since 5, the only sign there is before Android 14, as on the Xiaomi 12X's
 * Android 13). Samsung's high-contrast theme can't be read by an app; its players find the switch in Settings.
 */
fun phoneWantsHighContrast(context: Context): Boolean = contrastIsHigh(context) || highContrastTextOn(context)

/**
 * [phoneWantsHighContrast] now and again whenever the phone's contrast level or its high-contrast text changes, for as
 * long as it is collected.
 */
fun phoneHighContrastFlow(context: Context): Flow<Boolean> = callbackFlow {
    val app = context.applicationContext
    val send = { trySend(phoneWantsHighContrast(app)) }
    send()
    val main = Handler(Looper.getMainLooper())
    val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) {
            send()
        }
    }
    val resolver = app.contentResolver
    resolver.registerContentObserver(Settings.Secure.getUriFor(HIGH_TEXT_CONTRAST_KEY), false, observer)
    // Held as a plain object: a ContrastChangeListener named here crashed the release build on Android 13 (the Xiaomi,
    // 2026-11-09: R8 folded the listener's code into this one, which every phone runs, and Android 13 hasn't the class).
    val contrastListener: Any? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ContrastListener.add(app) { send() } else null
    awaitClose {
        resolver.unregisterContentObserver(observer)
        if (contrastListener != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ContrastListener.remove(app, contrastListener)
        }
    }
}.distinctUntilChanged()

/** The contrast level from which the phone counts as asking for high contrast: Android's *High* is 1, *Medium* 0.5. */
const val HIGH_CONTRAST_LEVEL = 0.75f

private fun contrastIsHigh(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
    val uiMode = context.getSystemService(UiModeManager::class.java) ?: return false
    return uiMode.contrast >= HIGH_CONTRAST_LEVEL
}

/**
 * Android 14's contrast listener, in a class of its own that only Android 14 and later load, so no code an older phone
 * runs names `UiModeManager.ContrastChangeListener`: the listener goes out and comes back as a plain object, and only
 * [remove] casts it back. Both are kept, so R8 doesn't inline them into their caller, which every phone runs.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private object ContrastListener {
    @Keep
    fun add(context: Context, changed: () -> Unit): Any? {
        val uiMode = context.getSystemService(UiModeManager::class.java) ?: return null
        val listener = UiModeManager.ContrastChangeListener { changed() }
        uiMode.addContrastChangeListener(context.mainExecutor, listener)
        return listener
    }

    @Keep
    fun remove(context: Context, listener: Any) {
        val uiMode = context.getSystemService(UiModeManager::class.java) ?: return
        uiMode.removeContrastChangeListener(listener as UiModeManager.ContrastChangeListener)
    }
}

/**
 * Whether the phone's high-contrast text is on: Android 16 asks [AccessibilityManager] in public; before it, the secure
 * setting Android itself keeps it in (readable by apps). Unreadable, it counts as off.
 */
private fun highContrastTextOn(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        val accessibility = context.getSystemService(AccessibilityManager::class.java)
        if (accessibility != null) return accessibility.isHighContrastTextEnabled
    }
    return runCatching { Settings.Secure.getInt(context.contentResolver, HIGH_TEXT_CONTRAST_KEY, 0) == 1 }.getOrDefault(false)
}

/** Android's own name for the high-contrast text setting (`Settings.Secure.ACCESSIBILITY_HIGH_TEXT_CONTRAST_ENABLED`). */
private const val HIGH_TEXT_CONTRAST_KEY = "high_text_contrast_enabled"
