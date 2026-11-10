package io.github.klaboukes.twangle.app.widget

import android.content.res.Configuration

/**
 * What of the phone's configuration the widget's drawing depends on: its [languages] (the names are in the app's
 * language), whether [dark] mode is on (its colours, before Android 12 or with a theme of its own) and the text size,
 * [fontScale] (Phase 91: how high a tile must be for "Open the tuner" under the chip, [TuningWidget.roomForBothLines]).
 */
internal data class WidgetLook(val languages: String, val dark: Boolean, val fontScale: Float = 1f) {
    companion object {
        fun of(configuration: Configuration) = WidgetLook(
            languages = configuration.locales.toLanguageTags(),
            dark = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES,
            fontScale = configuration.fontScale,
        )
    }
}

/**
 * Says which configuration changes redraw the widget: only a new [WidgetLook], not every turn of the phone or a
 * keyboard (Phase 89). Starts from [first], the look it was drawn in. Not thread-safe: fed on the main thread.
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
