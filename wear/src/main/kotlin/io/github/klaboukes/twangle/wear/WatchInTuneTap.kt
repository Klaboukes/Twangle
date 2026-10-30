package io.github.klaboukes.twangle.wear

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
 * When the watch taps the wrist: the moment the shown note comes in tune, which is when the ring
 * starts to glow. Once per tuned note, by the phone's own rule ([InTuneTickRule], as its
 * `InTuneHaptic`): again only after the note has left tune for 1.5 s, or at once for another note.
 * Starts from [initiallyInTune], so the tuner appearing on a note already in tune doesn't tap.
 * Time comes from the caller; pure and not thread-safe.
 */
class WatchInTuneTap(initiallyInTune: Boolean = false) {
    private val rule = InTuneTickRule(initiallyInTune)

    /** Whether the note was in tune at the last [update]. */
    val wasInTune: Boolean get() = rule.wasInTune

    /** Feeds what the tuner shows at [nowMillis]; true means "tap now". */
    fun update(state: WatchTunerState, nowMillis: Long): Boolean {
        val detected = state as? WatchTunerState.Detected
        return rule.update(detected?.inTune == true, detected?.note, nowMillis)
    }
}

/**
 * Plays [tap] on the wrist when [state] comes in tune ([WatchInTuneTap]). Whether it was in tune
 * survives the activity being made again, so coming back to a note still in tune doesn't tap twice.
 * The watch only hears while the tuner is on screen, so it only taps then too.
 */
@Composable
fun WatchInTuneTapEffect(
    state: WatchTunerState,
    tap: () -> Unit = rememberWristTap(),
    clock: () -> Long = SystemClock::uptimeMillis,
) {
    val currentState by rememberUpdatedState(state)
    val currentTap by rememberUpdatedState(tap)
    val inTune = (state as? WatchTunerState.Detected)?.inTune == true
    val rule = rememberSaveable(saver = TAP_RULE_SAVER) { WatchInTuneTap(initiallyInTune = inTune) }
    LaunchedEffect(inTune) {
        if (rule.update(currentState, clock())) currentTap()
    }
}

private val TAP_RULE_SAVER = Saver<WatchInTuneTap, Boolean>(
    save = { it.wasInTune },
    restore = { WatchInTuneTap(initiallyInTune = it) },
)

/**
 * One soft, short tap of the watch's motor: the system's click primitive at [TAP_SCALE] of its
 * strength where the motor can play it (a crisp tap of about 10–20 ms, felt but gentle), else the
 * system's predefined click, as the phone's tick. Short, so the microphone hears it for as little
 * as possible (`WatchPitchPipelineTest` checks the reading stays steady across it). It drives the
 * motor directly, as the phone does; Android files it as touch feedback (the emulator's vibrator
 * history: usage TOUCH), so the watch's touch-vibration strength applies to it.
 */
@Composable
fun rememberWristTap(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        val vibrator = context.vibrator()
        val effect = if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, TAP_SCALE)
                .compose()
        } else {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
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

/** How strong the tap is, as a share of the motor's full click: soft, still felt on the wrist. */
const val TAP_SCALE = 0.6f
