package io.github.klaboukes.twangle.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** Twangle's website (docs/architecture/website.md): the English home page. */
const val WEBSITE = "https://klaboukes.github.io/"

/** The languages the website is written in besides English, each under its own folder (`nl/`). */
private val websiteLanguages = setOf("nl")

/**
 * The website's home page in the app's [language] (an ISO 639 code, e.g. "nl"): its own folder when
 * the site has that language, else the English home page.
 */
fun websiteLink(language: String): String = if (language in websiteLanguages) "$WEBSITE$language/" else WEBSITE

/** Asks for [link] to be shown in a browser: a plain view of a web address, which any browser takes. */
fun websiteIntent(link: String): Intent =
    Intent(Intent.ACTION_VIEW, link.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)

/**
 * Opens [link] in the phone's browser. Twangle has no internet permission: the browser loads the page,
 * Twangle only asks for it to be shown. If no app can open a web page, nothing happens.
 */
fun openWebsite(context: Context, link: String) {
    try {
        context.startActivity(websiteIntent(link))
    } catch (_: ActivityNotFoundException) {
        // No browser on the phone.
    }
}
