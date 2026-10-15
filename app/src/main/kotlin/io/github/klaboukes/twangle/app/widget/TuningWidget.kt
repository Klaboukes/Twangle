package io.github.klaboukes.twangle.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.widget.RemoteViews
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.graphics.toArgb
import io.github.klaboukes.twangle.app.MainActivity
import io.github.klaboukes.twangle.app.R
import io.github.klaboukes.twangle.core.designsystem.colorSchemeFor
import io.github.klaboukes.twangle.core.designsystem.useDarkTheme
import io.github.klaboukes.twangle.core.settings.AppSettings
import io.github.klaboukes.twangle.core.settings.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The home-screen widget (Phase 20): the instrument and tuning the tuner is set to ("Guitar ·
 * Standard", or "Chromatic") in the user's style, and one tap opens the tuner. It is plain
 * [RemoteViews] (no Glance): one small layout, no extra library. It never touches the microphone;
 * the tuner it opens does that, as when opened from the launcher.
 *
 * The app redraws it whenever what it shows changes ([follow], from [io.github.klaboukes.twangle.app.TwangleApplication]);
 * Android asks for a drawing when one is placed ([TuningWidgetReceiver]).
 */
object TuningWidget {
    /** The intent action of a tap on the widget: [MainActivity] then shows the tuner, whatever screen was open. */
    const val ACTION_OPEN_TUNER = "io.github.klaboukes.twangle.action.OPEN_TUNER"

    /** The ids of the Twangle widgets on the home screen; none on a phone without widgets. */
    fun ids(context: Context): IntArray =
        AppWidgetManager.getInstance(context)?.getAppWidgetIds(ComponentName(context, TuningWidgetReceiver::class.java))
            ?: IntArray(0)

    /** Redraws the widgets [ids] (by default every one on the home screen) with [state]. */
    internal fun update(context: Context, state: WidgetState, ids: IntArray = ids(context)) {
        if (ids.isEmpty()) return
        AppWidgetManager.getInstance(context)?.updateAppWidget(ids, views(context, state))
    }

    /**
     * Keeps the widgets in step with [settings] for as long as [scope] lives: a redraw at once and
     * then on every change of what they show (not on A4, the meter's look and so on).
     */
    fun follow(context: Context, settings: Flow<AppSettings>, scope: CoroutineScope): Job = scope.launch {
        settings.map(WidgetState::from)
            .distinctUntilChanged()
            .catch { /* Settings that can't be read leave the widget as it is. */ }
            .collect { update(context, it) }
    }

    /** The widget's views for [state], with their texts in the app's language. */
    internal fun views(context: Context, state: WidgetState): RemoteViews {
        val texts = inAppLanguage(context)
        val string = { id: Int, args: Array<out Any> -> if (args.isEmpty()) texts.getString(id) else texts.getString(id, *args) }
        return RemoteViews(context.packageName, R.layout.widget_tuning).apply {
            setTextViewText(R.id.widget_title, state.title(string))
            setContentDescription(R.id.widget_title, state.spoken(string))
            // Set here, not only in the layout, which the launcher would read in the phone's language.
            setTextViewText(R.id.widget_subtitle, texts.getString(R.string.widget_open_tuner))
            setOnClickPendingIntent(android.R.id.background, openTuner(context))
            paint(context, state)
        }
    }

    /**
     * [context] in the language picked in the app's Settings. Android 13+ gives the app that language
     * itself; before, AppCompat sets it only on the app's activities, so the widget asks AppCompat.
     * (In a process started for the widget alone, before the app has been opened, AppCompat hasn't
     * read the saved language yet; the widget then shows the phone's language until the app opens.)
     */
    private fun inAppLanguage(context: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return context
        val picked = AppCompatDelegate.getApplicationLocales()
        if (picked.isEmpty) return context
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList.forLanguageTags(picked.toLanguageTags()))
        return context.createConfigurationContext(configuration)
    }

    /**
     * The style's colours: its background on the tile, ink for the tuning, the muted colour under it
     * and the accent on the tuning fork. With the theme set to follow the phone (Android 12+), the
     * launcher picks the light or dark set itself, so a switch of the phone's dark mode needs no redraw.
     */
    private fun RemoteViews.paint(context: Context, state: WidgetState) {
        fun colors(dark: Boolean) = colorSchemeFor(context, state.style, dark, state.dynamicColor).let {
            intArrayOf(it.background.toArgb(), it.onBackground.toArgb(), it.onSurfaceVariant.toArgb(), it.primary.toArgb())
        }
        val targets = listOf(
            R.id.widget_backdrop to "setColorFilter",
            R.id.widget_title to "setTextColor",
            R.id.widget_subtitle to "setTextColor",
            R.id.widget_icon to "setColorFilter",
        )
        if (state.theme == ThemeMode.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val light = colors(dark = false)
            val dark = colors(dark = true)
            targets.forEachIndexed { i, (id, method) -> setColorInt(id, method, light[i], dark[i]) }
        } else {
            val systemDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
            val set = colors(state.theme.useDarkTheme(systemDark))
            targets.forEachIndexed { i, (id, method) -> setInt(id, method, set[i]) }
        }
    }

    /** Opens the tuner: starts Twangle, or brings it forward and switches it to the tuner. */
    internal fun openTuner(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_TUNER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
