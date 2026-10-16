package io.github.klaboukes.twangle.feature.metronome

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.github.klaboukes.twangle.core.audio.MicPermissionRules
import io.github.klaboukes.twangle.core.audio.MicPermissionStatus

/** Listen's way to the microphone: [listen] when Listen is tapped, [openSettings] from "Open settings". */
class ListenPermission internal constructor(
    private val onListen: () -> Unit,
    private val onOpenSettings: () -> Unit,
) {
    /** Starts listening if the microphone is allowed; otherwise asks for it first (and listens once allowed). */
    fun listen() = onListen()

    /** Opens this app's page in system Settings, where a microphone refused for good can be allowed. */
    fun openSettings() = onOpenSettings()
}

/**
 * The microphone for Listen, by the tuner's rules ([MicPermissionRules], docs/architecture/lifecycle.md): a tap
 * on Listen with the microphone allowed calls [onGranted] at once; otherwise it shows Android's
 * dialog, and [onGranted] follows if the player allows it. A refusal calls [onRefused] (saved, so a
 * later refusal Android answers without a dialog is told apart, [refusedBefore]) and [onMicOff],
 * with true when only Settings can allow it now. Nothing is asked when the screen opens.
 */
@Composable
fun rememberListenPermission(
    refusedBefore: Boolean,
    onGranted: () -> Unit,
    onRefused: () -> Unit,
    onMicOff: (needsSettings: Boolean) -> Unit,
): ListenPermission {
    val context = LocalContext.current
    val activity = LocalActivity.current
    // The rationale just before the dialog opened: its change tells a real "don't ask again" apart.
    var rationaleBefore by rememberSaveable { mutableStateOf(false) }
    val currentRefusedBefore by rememberUpdatedState(refusedBefore)
    val currentOnGranted by rememberUpdatedState(onGranted)
    val currentOnRefused by rememberUpdatedState(onRefused)
    val currentOnMicOff by rememberUpdatedState(onMicOff)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val status = MicPermissionRules.afterRequest(
            granted = granted,
            rationaleBefore = rationaleBefore,
            rationaleAfter = shouldShowRationale(activity),
            refusedBefore = currentRefusedBefore,
        )
        if (MicPermissionRules.isRefusal(status)) currentOnRefused()
        when (status) {
            MicPermissionStatus.GRANTED -> currentOnGranted()
            MicPermissionStatus.DENIED -> currentOnMicOff(false)
            MicPermissionStatus.PERMANENTLY_DENIED -> currentOnMicOff(true)
            // The dialog was dismissed without an answer: Listen asks again next time.
            MicPermissionStatus.NOT_REQUESTED -> Unit
        }
    }
    return remember(context, launcher) {
        ListenPermission(
            onListen = {
                if (isGranted(context)) {
                    currentOnGranted()
                } else {
                    rationaleBefore = shouldShowRationale(activity)
                    launcher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onOpenSettings = { context.startActivity(appSettingsIntent(context)) },
        )
    }
}

private fun isGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun shouldShowRationale(activity: Activity?): Boolean =
    activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)

private fun appSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
