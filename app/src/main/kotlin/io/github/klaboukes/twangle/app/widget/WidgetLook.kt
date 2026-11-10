package io.github.klaboukes.twangle.app.widget

import android.content.res.Configuration

/**
 * What of the phone's configuration the widget's drawing depends on: its [languages] (the names are in the app's
 * language) and whether [dark] mode is on (its colours, before Android 12 or with a theme of its own).
 */
internal data class WidgetLook(val languages: String, val dark: Boolean) {
    companion object {
        fun of(configuration: Configuration) = WidgetLook(
            languages = configuration.locales.toLanguageTags(),
            dark = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES,
        )
    }
}

/**
 * Says which configuration changes redraw the widget: only a new [WidgetLook], not every turn of the phone, a new
 * font size or a keyboard (Phase 89; the launcher sizes the widget's text itself). Starts from [first], the look it
 * was drawn in. Not thread-safe: fed on the main thread.
 */
internal class WidgetRedraws(first: WidgetLook) {
    private var last = first

    /** The configuration now looks like [look]: whether the widget must be drawn again. */
    fun needed(look: WidgetLook): Boolean {
        if (look == last) return false
        last = look
        return true
    }
}
