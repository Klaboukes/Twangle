package io.github.klaboukes.twangle.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import io.github.klaboukes.twangle.app.TwangleApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Android's side of [TuningWidget]: draws a widget when it is placed or the launcher asks, and
 * redraws them all when the phone's language changes (the names are in the app's language).
 * The settings are read off the main thread ([goAsync]).
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
                TuningWidget.update(app, WidgetState.from(app.container.settingsRepository.settings.first()), ids)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
