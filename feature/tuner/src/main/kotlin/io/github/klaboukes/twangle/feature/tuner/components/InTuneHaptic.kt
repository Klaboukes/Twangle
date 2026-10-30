package io.github.klaboukes.twangle.feature.tuner.components

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import io.github.klaboukes.twangle.core.dsp.smoothing.InTuneTickRule

/**
 * Plays [tick] when the note becomes in tune, if [enabled] (rules in [InTuneTickRule]). [target]
 * identifies the target note, so a short drop-out on the same note doesn't tick twice. Whether it
 * was in tune survives rotation and a trip to settings, so coming back to a note that is still in
 * tune doesn't tick again.
 */
@Composable
fun InTuneHaptic(
    inTune: Boolean,
    target: Any?,
    enabled: Boolean,
    tick: () -> Unit = rememberVibratorTick(),
    clock: () -> Long = SystemClock::uptimeMillis,
) {
    val currentTarget by rememberUpdatedState(target)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentTick by rememberUpdatedState(tick)
    val rule = rememberSaveable(saver = TICK_RULE_SAVER) { InTuneTickRule(initiallyInTune = inTune) }
    LaunchedEffect(inTune) {
        if (rule.update(inTune, currentTarget, clock()) && currentEnabled) currentTick()
    }
}

private val TICK_RULE_SAVER = Saver<InTuneTickRule, Boolean>(
    save = { it.wasInTune },
    restore = { InTuneTickRule(initiallyInTune = it) },
)

/**
 * One short click on the vibration motor. It uses the motor directly instead of the touch-feedback
 * haptics, which many people switch off for the whole phone; the tuner has its own switch.
 */
@Composable
private fun rememberVibratorTick(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        val vibrator = context.vibrator()
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        } else {
            VibrationEffect.createOneShot(TICK_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        { if (vibrator.hasVibrator()) vibrator.vibrate(effect) }
    }
}

private fun Context.vibrator(): Vibrator =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        getSystemService(Vibrator::class.java)
    }

/** Length of the fallback tick on Android 8–9, which have no predefined click. */
private const val TICK_MILLIS = 20L
