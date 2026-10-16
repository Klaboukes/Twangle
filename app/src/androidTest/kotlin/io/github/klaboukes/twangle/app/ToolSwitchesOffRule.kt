package io.github.klaboukes.twangle.app

import androidx.test.core.app.ApplicationProvider
import io.github.klaboukes.twangle.core.settings.ToolSwitches
import org.junit.rules.ExternalResource

/**
 * Shows the v1.0 app, as the Play build does, whatever the debug build shows (Phase 28): every
 * after-v1.0 tool switched off ([ToolSwitches.ALL_OFF]) for the test, and this build's switches back
 * after. Every UI test whose pictures or checks stand for the released app uses it, the store
 * screenshots (Phase 23) first of all, so a picture never shows a tool v1.0 users don't have.
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
