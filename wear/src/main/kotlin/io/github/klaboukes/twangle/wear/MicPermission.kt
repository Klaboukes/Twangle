package io.github.klaboukes.twangle.wear

import android.Manifest
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** The watch's microphone permission: whether it is [granted], and the one action the prompt offers. */
class WatchMicPermission(
    val granted: Boolean,
    /** Android won't show its dialog again: only the watch's settings can grant it now. */
    val needsSettings: Boolean,
    val onAction: () -> Unit,
)

/**
 * Asks for `RECORD_AUDIO` once when the tuner first opens, then offers a button: ask again, or,
 * once Android stops showing its dialog, open Twangle's page in the watch's settings. Checked
 * again on every resume, so a grant made in settings is picked up.
 */
@Composable
fun rememberWatchMicPermission(): WatchMicPermission {
    val context = LocalContext.current
    val activity = LocalActivity.current
    fun isGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(isGranted()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    var needsSettings by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
        needsSettings = !result &&
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == false
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = isGranted() }
    LaunchedEffect(Unit) {
        if (!granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    return WatchMicPermission(granted, needsSettings) {
        if (needsSettings) {
            // From the activity, without a new task, so Recents keeps it with Twangle (lint: WearRecents).
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        } else {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
