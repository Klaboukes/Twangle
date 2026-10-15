package io.github.klaboukes.twangle.feature.tuner

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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.klaboukes.twangle.core.audio.MicPermissionRules
import io.github.klaboukes.twangle.core.audio.MicPermissionStatus

/** UI handle for the microphone permission: the current [status] plus the actions the prompt and the tuner need. */
class MicPermissionState internal constructor(
    val status: MicPermissionStatus,
    private val onRequest: () -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onRecheck: () -> Unit,
) {
    /** Shows the system permission dialog. */
    fun request() = onRequest()

    /** Opens this app's page in system Settings, where a permanently denied permission can be granted. */
    fun openSettings() = onOpenSettings()

    /** Checks the permission again, e.g. when the microphone refused to open: a lost one goes back to the prompt. */
    fun recheck() = onRecheck()
}

/**
 * Tracks `RECORD_AUDIO` for the tuner screen. Asks once automatically the first time the screen is
 * shown, and checks again whenever the app resumes (the user may have granted it in Settings, or
 * taken it back). [refusedBefore] is whether the player refused the dialog before on this install
 * (saved in the settings); [onRefused] is called on each refusal so it can be saved.
 */
@Composable
fun rememberMicPermissionState(refusedBefore: Boolean, onRefused: () -> Unit): MicPermissionState {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var status by rememberSaveable {
        mutableStateOf(MicPermissionRules.initial(isGranted(context), shouldShowRationale(activity)))
    }
    var autoRequested by rememberSaveable { mutableStateOf(false) }
    // The rationale just before the dialog opened: its change tells a real "don't ask again" apart.
    var rationaleBefore by rememberSaveable { mutableStateOf(false) }
    val currentRefusedBefore by rememberUpdatedState(refusedBefore)
    val currentOnRefused by rememberUpdatedState(onRefused)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status = MicPermissionRules.afterRequest(
            granted = granted,
            rationaleBefore = rationaleBefore,
            rationaleAfter = shouldShowRationale(activity),
            refusedBefore = currentRefusedBefore,
        )
        if (MicPermissionRules.isRefusal(status)) currentOnRefused()
    }
    val request = {
        rationaleBefore = shouldShowRationale(activity)
        launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
    val recheck = { status = MicPermissionRules.onResume(status, isGranted(context), shouldShowRationale(activity)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { recheck() }
    LaunchedEffect(Unit) {
        if (!autoRequested && status == MicPermissionStatus.NOT_REQUESTED) {
            autoRequested = true
            request()
        }
    }
    return MicPermissionState(
        status = status,
        onRequest = request,
        onOpenSettings = { context.startActivity(appSettingsIntent(context)) },
        onRecheck = recheck,
    )
}

private fun isGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun shouldShowRationale(activity: Activity?): Boolean =
    activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)

private fun appSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
