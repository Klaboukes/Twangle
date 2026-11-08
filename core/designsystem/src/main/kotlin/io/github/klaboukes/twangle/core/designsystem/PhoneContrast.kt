package io.github.klaboukes.twangle.core.designsystem

import android.app.UiModeManager
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
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
    val contrastListener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) addContrastListener(app) { send() } else null
    awaitClose {
        resolver.unregisterContentObserver(observer)
        if (contrastListener != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            app.getSystemService(UiModeManager::class.java)?.removeContrastChangeListener(contrastListener)
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

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private fun addContrastListener(context: Context, changed: () -> Unit): UiModeManager.ContrastChangeListener? {
    val uiMode = context.getSystemService(UiModeManager::class.java) ?: return null
    val listener = UiModeManager.ContrastChangeListener { changed() }
    uiMode.addContrastChangeListener(context.mainExecutor, listener)
    return listener
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
