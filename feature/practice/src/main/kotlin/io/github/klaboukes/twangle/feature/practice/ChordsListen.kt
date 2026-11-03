package io.github.klaboukes.twangle.feature.practice

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.klaboukes.twangle.core.audio.MicPermissionRules
import io.github.klaboukes.twangle.core.audio.MicPermissionStatus
import io.github.klaboukes.twangle.core.designsystem.controlOutline
import io.github.klaboukes.twangle.core.designsystem.faceFill
import io.github.klaboukes.twangle.core.designsystem.pressFeedback
import io.github.klaboukes.twangle.core.designsystem.pressFlash
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.pitch.PitchRange

/**
 * Listen in Chords (2.0, Phase 74; the owner's decision of 2026-11-03: the chord name belongs in Chords, not on the
 * tuner): a round button at the end of the name's row, as Scales has its drone button there, the height of the
 * name, with the metronome's microphone icon. A tap asks for the microphone if needed ([rememberChordsMicPermission])
 * and starts listening: the button filled in the accent while it listens, every strum named ([ChordListener]) and
 * handed to [onChord], which shows that chord's name and chart. A tap again stops it; leaving Chords, another
 * tool or the app in the background stops it too (the microphone open only while Chords is on screen). TalkBack
 * hears "Listen" as a button with "Listening" as its state while on.
 */
@Composable
internal fun ChordsListenButton(
    listener: ChordListener,
    a4Hz: Float,
    range: PitchRange,
    micRefused: Boolean,
    onMicRefused: () -> Unit,
    onChord: (Chord) -> Unit,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    var listening by rememberSaveable { mutableStateOf(false) }
    val permission = rememberChordsMicPermission(refusedBefore = micRefused, onGranted = { listening = true }, onRefused = onMicRefused)
    val currentOnChord by rememberUpdatedState(onChord)
    if (listening) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(listener, a4Hz, range, lifecycle) {
            // Only while Chords is on screen and the app in front (hard rule 4).
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                listener.chords(a4Hz, range).collect { currentOnChord(it) }
            }
        }
    }
    val scheme = MaterialTheme.colorScheme
    val press = remember { MutableInteractionSource() }
    val label = stringResource(R.string.practice_chords_listen)
    val state = if (listening) stringResource(R.string.practice_chords_listening) else ""
    Box(
        modifier
            .size(height)
            .testTag(PracticeTestTags.CHORDS_LISTEN)
            .border(1.dp, controlOutline(scheme), CircleShape)
            .pressFeedback(press, CircleShape, fill = if (listening) scheme.primary else faceFill(picked = false), outline = null)
            .clickable(interactionSource = press, indication = null, role = Role.Button) {
                press.pressFlash()
                if (listening) listening = false else permission.listen()
            }
            .semantics {
                contentDescription = label
                stateDescription = state
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_listen),
            contentDescription = null,
            tint = if (listening) scheme.onPrimary else scheme.onBackground,
            modifier = Modifier.size(LISTEN_ICON_SIZE),
        )
    }
}

private val LISTEN_ICON_SIZE = 24.dp

/** Listen's way to the microphone: [listen] when it is tapped. */
internal class ChordsMicPermission(private val onListen: () -> Unit) {
    /** Starts listening if the microphone is allowed; otherwise asks for it first (and listens once allowed). */
    fun listen() = onListen()
}

/**
 * The microphone for Listen, by the tuner's rules ([MicPermissionRules], docs/architecture/lifecycle.md), as the
 * metronome's Listen asks (`rememberListenPermission` in `:feature:metronome`): a tap with the microphone allowed calls
 * [onGranted] at once; otherwise Android's dialog, and [onGranted] if the player allows it. A refusal calls
 * [onRefused] (saved, so a later refusal Android answers without a dialog is told apart, [refusedBefore]); refused
 * for good, the tap opens this app's page in system Settings, where it can be allowed. Nothing is asked when
 * Chords opens.
 */
@Composable
internal fun rememberChordsMicPermission(refusedBefore: Boolean, onGranted: () -> Unit, onRefused: () -> Unit): ChordsMicPermission {
    val context = LocalContext.current
    val activity = LocalActivity.current
    // The rationale just before the dialog opened: its change tells a real "don't ask again" apart.
    var rationaleBefore by rememberSaveable { mutableStateOf(false) }
    var refusedForGood by rememberSaveable { mutableStateOf(false) }
    val currentRefusedBefore by rememberUpdatedState(refusedBefore)
    val currentOnGranted by rememberUpdatedState(onGranted)
    val currentOnRefused by rememberUpdatedState(onRefused)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val status = MicPermissionRules.afterRequest(
            granted = granted,
            rationaleBefore = rationaleBefore,
            rationaleAfter = shouldShowRationale(activity),
            refusedBefore = currentRefusedBefore,
        )
        if (MicPermissionRules.isRefusal(status)) currentOnRefused()
        if (status == MicPermissionStatus.GRANTED) currentOnGranted()
        refusedForGood = status == MicPermissionStatus.PERMANENTLY_DENIED
    }
    return remember(context, launcher) {
        ChordsMicPermission(
            onListen = {
                when {
                    isGranted(context) -> currentOnGranted()
                    refusedForGood -> context.startActivity(appSettingsIntent(context))
                    else -> {
                        rationaleBefore = shouldShowRationale(activity)
                        launcher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            },
        )
    }
}

private fun isGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun shouldShowRationale(activity: Activity?): Boolean =
    activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)

private fun appSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
