package io.github.klaboukes.twangle.feature.tuner.components

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
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
