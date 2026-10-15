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

/** Where the app stands with the `RECORD_AUDIO` permission. */
enum class MicPermissionStatus {
    /** Not granted, and not refused for good as far as the app knows: offer "Allow microphone". */
    NOT_REQUESTED,

    /** The user said no once: explain why the tuner needs the mic, then ask again. */
    DENIED,

    /** Android won't show the dialog any more ("don't ask again", or refused twice): only Settings can grant it. */
    PERMANENTLY_DENIED,

    GRANTED,
}

/**
 * Pure rules for [MicPermissionStatus], kept apart from Android so they can be unit tested.
 *
 * Android only reveals a permanent denial indirectly, through `shouldShowRequestPermissionRationale`
 * ("rationale" below), which is false both before the first refusal and after a permanent one. A
 * dialog dismissed without an answer (a tap outside it, Back) changes nothing, so it also comes back
 * denied without a rationale. The rules tell these apart by the rationale before the request and by
 * whether the player ever refused the dialog on this install (`refusedBefore`, saved in the settings).
 */
object MicPermissionRules {

    /** Status when the screen opens, before any request in this session. */
    fun initial(granted: Boolean, shouldShowRationale: Boolean): MicPermissionStatus = when {
        granted -> MicPermissionStatus.GRANTED
        shouldShowRationale -> MicPermissionStatus.DENIED
        // Never asked, or permanently denied in an earlier session. Asking tells them apart.
        else -> MicPermissionStatus.NOT_REQUESTED
    }

    /**
     * Status after the system permission dialog returns. [rationaleBefore] and [rationaleAfter] are
     * the rationale just before the request and after it; [refusedBefore] says whether the player
     * refused the dialog in an earlier request on this install.
     */
    fun afterRequest(
        granted: Boolean,
        rationaleBefore: Boolean,
        rationaleAfter: Boolean,
        refusedBefore: Boolean,
    ): MicPermissionStatus = when {
        granted -> MicPermissionStatus.GRANTED
        // A refusal Android will still ask about again.
        rationaleAfter -> MicPermissionStatus.DENIED
        // The rationale went away: "don't ask again" (Android 10 and older) or a second refusal (11+).
        rationaleBefore -> MicPermissionStatus.PERMANENTLY_DENIED
        // Refused before and no rationale now: Android answered without showing the dialog.
        refusedBefore -> MicPermissionStatus.PERMANENTLY_DENIED
        // Never refused: the dialog was dismissed without an answer. Asking again still works.
        else -> MicPermissionStatus.NOT_REQUESTED
    }

    /** Whether a request with this outcome is a refusal worth remembering for [afterRequest]'s `refusedBefore`. */
    fun isRefusal(status: MicPermissionStatus): Boolean =
        status == MicPermissionStatus.DENIED || status == MicPermissionStatus.PERMANENTLY_DENIED

    /**
     * Status after the app comes back to the foreground (e.g. from Settings), or when the microphone
     * turns out to be refused while the tuner runs. A grant wins. A permission lost while the app was
     * open (taken back in Settings, or a one-time grant that ran out) goes back to the prompt, as
     * [initial] would show it; otherwise the status stays.
     */
    fun onResume(current: MicPermissionStatus, granted: Boolean, shouldShowRationale: Boolean): MicPermissionStatus = when {
        granted -> MicPermissionStatus.GRANTED
        current == MicPermissionStatus.GRANTED -> initial(granted = false, shouldShowRationale = shouldShowRationale)
        else -> current
    }
}

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
