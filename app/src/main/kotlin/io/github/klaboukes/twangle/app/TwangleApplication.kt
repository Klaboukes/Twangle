package io.github.klaboukes.twangle.app

import android.app.Application
import android.content.res.Configuration
import android.os.StrictMode
import android.util.Log
import io.github.klaboukes.twangle.app.widget.TuningWidget
import io.github.klaboukes.twangle.app.widget.WidgetState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TwangleApplication : Application() {

    /** App-wide dependency graph. See [AppContainer]. */
    lateinit var container: AppContainer
        private set

    /** Work that lasts as long as the app's process: keeping the home-screen widget up to date. */
    private val appScope = loggingScope("TwangleApp")

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) logStrictModeViolations()
        container = AppContainer(this)
        // The home-screen widget shows the tuning picked in the app, so it follows the settings.
        TuningWidget.follow(this, container.settingsRepository.settings, appScope)
    }

    /** A new language, or dark mode on phones before Android 12: the widget's names and colours follow. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        redrawWidgets()
    }

    /** Redraws the home-screen widgets with the current settings, e.g. in a language just picked in Settings. */
    fun redrawWidgets() {
        appScope.launch { TuningWidget.update(this@TwangleApplication, WidgetState.from(container.settingsRepository.settings.first())) }
    }

    /**
     * Debug builds only: StrictMode writes to logcat (tag `StrictMode`) whatever reads or writes files
     * on the main thread, or leaks a closeable, so such slips show while building. Only a log line:
     * nothing is stopped, and release builds never run this.
     */
    private fun logStrictModeViolations() {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
    }
}

/**
 * A scope for work that outlives every screen (the widget's redraws, the settings writes), on [dispatcher]. An
 * exception that escapes a coroutine in it is logged under [tag] instead of closing the app: a
 * widget that can't be redrawn once must not take the tuner down with it.
 */
internal fun loggingScope(tag: String, dispatcher: CoroutineDispatcher = Dispatchers.Default): CoroutineScope = CoroutineScope(
    SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, e -> Log.e(tag, "Background work failed", e) },
)
