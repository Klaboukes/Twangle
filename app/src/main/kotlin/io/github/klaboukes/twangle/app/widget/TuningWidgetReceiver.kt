package io.github.klaboukes.twangle.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import io.github.klaboukes.twangle.app.TwangleApplication
import io.github.klaboukes.twangle.app.loggingScope
import io.github.klaboukes.twangle.core.designsystem.phoneWantsHighContrast
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Android's side of [TuningWidget]: draws a widget when it is placed or the launcher asks (also after the phone
 * restarts), and redraws them all when the phone's language changes (the names are in the app's language).
 * The settings are read off the main thread ([goAsync]); a drawing that fails is logged, never a crash.
 *
 * Nothing else reaches it: Android sends an app that isn't running no word of a change of the phone's contrast, or of
 * dark mode (`ACTION_CONFIGURATION_CHANGED` goes only to receivers registered in a running app), so those wait for
 * the next time Twangle's process starts, which redraws the widget at once (Phase 91, lifecycle.md).
 */
class TuningWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        redraw(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_LOCALE_CHANGED) redraw(context, TuningWidget.ids(context))
    }

    private fun redraw(context: Context, ids: IntArray) {
        if (ids.isEmpty()) return
        val app = context.applicationContext as TwangleApplication
        val pending = goAsync()
        scope.launch {
            try {
                val state = WidgetState.from(app.container.settingsRepository.settings.first(), phoneWantsHighContrast(app))
                TuningWidget.update(app, state, ids)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = loggingScope("TuningWidget")
    }
}
