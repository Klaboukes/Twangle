package io.github.klaboukes.twangle.core.designsystem

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Call first thing in a `ModalBottomSheet`'s content (Phase 63): the sheet's own colour then runs under
 * the gesture bar, as the app's screens do. A sheet is a window of its own, and Android lays the
 * system's readable strip behind its navigation bar (MIUI a white one along the gesture bar in light,
 * found on the Xiaomi). The sheet's window takes the activity's choice instead (`MainActivity`: no strip,
 * behind the gesture handle or the navigation buttons, since Phase 65) and a see-through bar.
 */
@Composable
fun SheetUnderNavigationBar() {
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(view, context) {
        val sheet = view.dialogWindow()
        val activity = context.findActivity()?.window
        if (sheet != null && activity != null) {
            @Suppress("DEPRECATION") // Still heeded below Android 15, where the bars' colours are the app's to set.
            sheet.navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                sheet.isNavigationBarContrastEnforced = activity.isNavigationBarContrastEnforced
            }
        }
        onDispose {}
    }
}

/** The window of the dialog [this] view is in (a sheet's), found up its parents; null outside one. */
private fun View.dialogWindow(): Window? {
    var at: Any? = this
    while (at is View) {
        if (at is DialogWindowProvider) return at.window
        at = at.parent
    }
    return null
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
