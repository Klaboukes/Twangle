package io.github.klaboukes.twangle.app

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/**
 * Shows the app with focus mode off (Phase 36), the default, whatever the phone's Twangle was left
 * with, and puts the saved choice back after. In focus mode the whole app turns on its side and the
 * tuner hides its controls until tapped, which a test of the full tuner can't work with. Like
 * [ToolSwitchesOffRule], it must start before the activity does.
 */
class FocusModeOffRule : ExternalResource() {

    private val settings = ApplicationProvider.getApplicationContext<TwangleApplication>().container.settingsRepository
    private var before: Boolean? = null

    override fun before() {
        runBlocking {
            before = settings.settings.first().focusMode
            settings.setFocusMode(false)
        }
    }

    override fun after() {
        before?.let { runBlocking { settings.setFocusMode(it) } }
    }
}
