package io.github.klaboukes.twangle.app

import androidx.test.core.app.ApplicationProvider
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import org.junit.rules.ExternalResource

/**
 * Shows the app without the music tools, as the Play build does until they go live, whatever the
 * debug build shows (Phase 28): every tool switched off ([ToolSwitches.ALL_OFF]) for the test, and
 * this build's switches back after. A UI test that checks a hidden tool leaves no trace uses it.
 *
 * It must start before the activity does: make it the outer rule of a `RuleChain`, around any rule
 * that launches `MainActivity`, or launch the activity in the test itself.
 */
class ToolSwitchesOffRule : ExternalResource() {

    private val container = ApplicationProvider.getApplicationContext<TwangleApplication>().container
    private var before: ToolSwitches? = null

    override fun before() {
        before = container.toolSwitches
        container.toolSwitches = ToolSwitches.ALL_OFF
    }

    override fun after() {
        before?.let { container.toolSwitches = it }
    }
}
