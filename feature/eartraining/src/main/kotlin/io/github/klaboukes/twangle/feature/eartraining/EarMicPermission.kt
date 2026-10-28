package io.github.klaboukes.twangle.feature.eartraining

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

/** Ear training's microphone permission: its [status], and the actions the "Microphone is off" line needs. */
class EarMicPermission internal constructor(
    val status: MicPermissionStatus,
    private val onRequest: () -> Unit,
    private val onOpenSettings: () -> Unit,
) {
    /** Shows Android's permission dialog. */
    fun request() = onRequest()

    /** Opens this app's page in system Settings, where a microphone refused for good can be allowed. */
    fun openSettings() = onOpenSettings()
}

/**
 * The microphone for Play it back, asked for as the tuner asks ([MicPermissionRules],
 * docs/architecture/lifecycle.md): once by itself the first time an exercise that listens is shown
 * ([needed]), and checked again whenever the app resumes (the player may have allowed it in Settings,
 * or taken it back). [refusedBefore] is whether the player refused the dialog before on this install
 * (saved); [onRefused] is called on each refusal so it can be saved. Intervals and Chord type never ask.
 */
@Composable
fun rememberEarMicPermission(needed: Boolean, refusedBefore: Boolean, onRefused: () -> Unit): EarMicPermission {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var status by rememberSaveable { mutableStateOf(MicPermissionRules.initial(isGranted(context), shouldShowRationale(activity))) }
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
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        status = MicPermissionRules.onResume(status, isGranted(context), shouldShowRationale(activity))
    }
    LaunchedEffect(needed) {
        if (needed && !autoRequested && status == MicPermissionStatus.NOT_REQUESTED) {
            autoRequested = true
            request()
        }
    }
    return EarMicPermission(
        status = status,
        onRequest = request,
        onOpenSettings = { context.startActivity(appSettingsIntent(context)) },
    )
}

private fun isGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun shouldShowRationale(activity: Activity?): Boolean =
    activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)

private fun appSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
