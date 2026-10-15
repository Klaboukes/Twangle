package io.github.klaboukes.twangle.wear

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.klaboukes.twangle.core.audio.MicPermissionRules
import io.github.klaboukes.twangle.core.audio.MicPermissionStatus

/** The watch's microphone permission: whether it is [granted], and the one action the prompt offers. */
class WatchMicPermission(
    val granted: Boolean,
    /** Android won't show its dialog again: only the watch's settings can grant it now. */
    val needsSettings: Boolean,
    val onAction: () -> Unit,
)

/**
 * Asks for `RECORD_AUDIO` once when the tuner first opens, then offers a button: "Allow microphone"
 * (ask again), or, once Android stops showing its dialog, Twangle's page in the watch's settings.
 * Follows the phone's rules ([MicPermissionRules]): a dialog dismissed without an answer keeps
 * "Allow microphone", and only a real "don't ask again" (a second refusal) leads to settings. A
 * refusal is remembered on the watch ([MIC_REFUSED]), so a permanent one from an earlier session
 * is told apart from a dismissed dialog. Checked again on every resume, so a grant made in settings
 * is picked up, and a permission taken back brings the prompt back.
 */
@Composable
fun rememberWatchMicPermission(): WatchMicPermission {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var status by rememberSaveable {
        mutableStateOf(MicPermissionRules.initial(isGranted(context), shouldShowRationale(activity)))
    }
    var autoRequested by rememberSaveable { mutableStateOf(false) }
    // The rationale just before the dialog opened: its change tells a real "don't ask again" apart.
    var rationaleBefore by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status = MicPermissionRules.afterRequest(
            granted = granted,
            rationaleBefore = rationaleBefore,
            rationaleAfter = shouldShowRationale(activity),
            refusedBefore = prefs.getBoolean(MIC_REFUSED, false),
        )
        if (MicPermissionRules.isRefusal(status)) prefs.edit { putBoolean(MIC_REFUSED, true) }
    }
    val request = {
        rationaleBefore = shouldShowRationale(activity)
        launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        status = MicPermissionRules.onResume(status, isGranted(context), shouldShowRationale(activity))
    }
    LaunchedEffect(Unit) {
        if (!autoRequested && status == MicPermissionStatus.NOT_REQUESTED) {
            autoRequested = true
            request()
        }
    }
    val needsSettings = needsSettings(status)
    return WatchMicPermission(status == MicPermissionStatus.GRANTED, needsSettings) {
        if (needsSettings) {
            // From the activity, without a new task, so Recents keeps it with Twangle (lint: WearRecents).
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        } else {
            request()
        }
    }
}

/** Whether the prompt's button opens the watch's settings rather than asking: only once Android won't ask again. */
internal fun needsSettings(status: MicPermissionStatus): Boolean = status == MicPermissionStatus.PERMANENTLY_DENIED

private fun isGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun shouldShowRationale(activity: Activity?): Boolean =
    activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)

/** The watch app's own small store: it has no settings screen, only this one flag. */
private const val PREFS = "twangle_wear"

/** Whether the microphone was ever refused in Android's dialog on this watch (as the phone's `mic_refused`). */
private const val MIC_REFUSED = "mic_refused"
