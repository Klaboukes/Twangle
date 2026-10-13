package io.github.klaboukes.twangle.feature.tuner.components

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import io.github.klaboukes.twangle.feature.tuner.InTuneGlowRule
import kotlinx.coroutines.delay

/**
 * Whether the meter's in-tune glow is lit, from [inTune] and the [target] note (null without one),
 * by the rules in [InTuneGlowRule]: it lights together with the haptic tick ([InTuneHaptic]) and
 * outlasts a brief dropout of the same note, unless the note comes back [retuning] (the hint asks to
 * tune it up or down). Recomposes only when the glow goes on or off. Like the
 * tick, whether it was in tune survives rotation and a trip to settings, so coming back to a note
 * that is still in tune shows the glow without easing it in again.
 */
@Composable
fun rememberInTuneGlow(
    inTune: Boolean,
    target: Any?,
    retuning: Boolean = false,
    clock: () -> Long = SystemClock::uptimeMillis,
): Boolean {
    val currentClock by rememberUpdatedState(clock)
    val rule = rememberSaveable(saver = GLOW_RULE_SAVER) { InTuneGlowRule(initiallyInTune = inTune) }
    val lit = remember(rule) { mutableStateOf(rule.lit) }
    LaunchedEffect(rule, inTune, target, retuning) {
        lit.value = rule.update(inTune, target, currentClock(), retuning)
        // A glow held through a dropout goes out when its hold ends, unless the note is back first
        // (which restarts this effect).
        val wait = rule.holdEndsAtMillis
        if (wait != Long.MAX_VALUE) {
            delay((wait - currentClock()).coerceAtLeast(0L))
            lit.value = rule.update(inTune, target, currentClock(), retuning)
        }
    }
    return lit.value
}

private val GLOW_RULE_SAVER = Saver<InTuneGlowRule, Boolean>(
    save = { it.wasInTune },
    restore = { InTuneGlowRule(initiallyInTune = it) },
)

/**
 * Whether Android's "Remove animations" is on (Settings → Accessibility; the animator duration scale
 * is 0), followed while the screen shows. The meter then switches its in-tune colour and glow at
 * once. Compose's own animations also finish at once with that setting, but the meter doesn't rely
 * on it.
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
