package io.github.klaboukes.twangle.app

import android.app.Application
import android.content.res.Configuration
import io.github.klaboukes.twangle.app.widget.TuningWidget
import io.github.klaboukes.twangle.app.widget.WidgetState
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
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
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
}
