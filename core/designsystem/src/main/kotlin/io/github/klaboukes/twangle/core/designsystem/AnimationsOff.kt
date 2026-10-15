package io.github.klaboukes.twangle.core.designsystem

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether Android's "Remove animations" is on (Settings → Accessibility; the animator duration scale
 * is 0), followed while the screen shows. The tuner's meter then switches its in-tune colour and glow at
 * once, and the settings' look cards hold still. Compose's own animations also finish at once with
 * that setting, but these don't rely on it.
 */
@Composable
fun rememberAnimationsOff(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val off = remember(resolver) { mutableStateOf(animationsOff(resolver)) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                off.value = animationsOff(resolver)
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        // It may have changed between reading it and listening.
        off.value = animationsOff(resolver)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return off.value
}

private fun animationsOff(resolver: ContentResolver): Boolean =
    animationsOffAt(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))

/** "Remove animations" sets the animator duration scale to 0; any other scale still animates. */
internal fun animationsOffAt(scale: Float): Boolean = scale == 0f
