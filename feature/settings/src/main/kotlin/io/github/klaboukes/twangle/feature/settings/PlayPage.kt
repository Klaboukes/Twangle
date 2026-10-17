package io.github.klaboukes.twangle.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** Package of the Google Play Store app, so `market://` links don't open another phone maker's store. */
private const val PLAY_STORE_PACKAGE = "com.android.vending"

/** The Play Store app's link to [appId]'s page. */
fun playStoreAppLink(appId: String): String = "market://details?id=$appId"

/** The same page on the web, for phones without the Play Store app. */
fun playStoreWebLink(appId: String): String = "https://play.google.com/store/apps/details?id=$appId"

/**
 * Opens [appId]'s page on Google Play: in the Play Store app when it is there, else in the browser.
 * Only the page, never a rating pop-up, and nothing is sent by Twangle itself (no internet
 * permission): the other app does the rest. If neither can open it, nothing happens.
 */
fun openPlayPage(context: Context, appId: String) {
    val inPlayStore = Intent(Intent.ACTION_VIEW, playStoreAppLink(appId).toUri()).setPackage(PLAY_STORE_PACKAGE)
    val inBrowser = Intent(Intent.ACTION_VIEW, playStoreWebLink(appId).toUri())
    for (intent in listOf(inPlayStore, inBrowser)) {
        try {
            context.startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next way.
        }
    }
}
