package com.intune.feature.tuner

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** Where the app stands with the `RECORD_AUDIO` permission. */
enum class MicPermissionStatus {
    /** Not granted, and Android hasn't reported a refusal yet: just ask. */
    NOT_REQUESTED,

    /** The user said no once: explain why the tuner needs the mic, then ask again. */
    DENIED,

    /** Android won't show the dialog any more (refused twice, or "don't ask again"): only Settings can grant it. */
    PERMANENTLY_DENIED,

    GRANTED,
}

/**
 * Pure rules for [MicPermissionStatus], kept apart from Android so they can be unit tested.
 *
 * Android only reveals a permanent denial indirectly: after a request comes back denied,
 * `shouldShowRequestPermissionRationale` is false. A first-time dismissal (tapping outside the
 * dialog) looks the same, so it is treated as permanent too. Settings still fixes it.
 */
object MicPermissionRules {

    /** Status when the screen opens, before any request in this session. */
    fun initial(granted: Boolean, shouldShowRationale: Boolean): MicPermissionStatus = when {
        granted -> MicPermissionStatus.GRANTED
        shouldShowRationale -> MicPermissionStatus.DENIED
        // Never asked, or permanently denied in an earlier session. Asking tells them apart.
        else -> MicPermissionStatus.NOT_REQUESTED
    }

    /** Status after the system permission dialog returns. */
    fun afterRequest(granted: Boolean, shouldShowRationale: Boolean): MicPermissionStatus = when {
        granted -> MicPermissionStatus.GRANTED
        shouldShowRationale -> MicPermissionStatus.DENIED
        else -> MicPermissionStatus.PERMANENTLY_DENIED
    }

    /**
     * Status after the app comes back to the foreground, e.g. from Settings. A grant there wins.
     * Revoking it there restarts the app process, so that case starts again from [initial].
     */
    fun onResume(current: MicPermissionStatus, granted: Boolean): MicPermissionStatus =
        if (granted) MicPermissionStatus.GRANTED else current
}

/** UI handle for the microphone permission: the current [status] plus the two actions the prompt offers. */
class MicPermissionState internal constructor(
    val status: MicPermissionStatus,
    private val onRequest: () -> Unit,
    private val onOpenSettings: () -> Unit,
) {
    /** Shows the system permission dialog. */
    fun request() = onRequest()

    /** Opens this app's page in system Settings, where a permanently denied permission can be granted. */
    fun openSettings() = onOpenSettings()
}

/**
 * Tracks `RECORD_AUDIO` for the tuner screen. Asks once automatically the first time the screen is
 * shown, and checks again whenever the app resumes (the user may have granted it in Settings).
 */
@Composable
fun rememberMicPermissionState(): MicPermissionState {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var status by rememberSaveable {
        mutableStateOf(MicPermissionRules.initial(isGranted(context), shouldShowRationale(activity)))
    }
    var autoRequested by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status = MicPermissionRules.afterRequest(granted, shouldShowRationale(activity))
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        status = MicPermissionRules.onResume(status, isGranted(context))
    }
    LaunchedEffect(Unit) {
        if (!autoRequested && status == MicPermissionStatus.NOT_REQUESTED) {
            autoRequested = true
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    return MicPermissionState(
        status = status,
        onRequest = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
        onOpenSettings = { context.startActivity(appSettingsIntent(context)) },
    )
}

private fun isGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun shouldShowRationale(activity: Activity?): Boolean =
    activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)

private fun appSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
