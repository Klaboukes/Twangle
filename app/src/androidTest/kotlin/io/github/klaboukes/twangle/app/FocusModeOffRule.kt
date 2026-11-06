package io.github.klaboukes.twangle.app

import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource

/**
 * Shows the app with focus mode off (Phase 36), the default, whatever an earlier test of the run left (2.0, Phase
 * 73: the app holds it, nothing saves it, and one activity serves the whole run). In focus mode the whole app turns
 * on its side and the tuner hides its controls until tapped, which a test of the full tuner can't work with. It must
 * start before the activity does: put it around any rule that launches `MainActivity`.
 */
class FocusModeOffRule : ExternalResource() {

    private val focusMode = ApplicationProvider.getApplicationContext<TwangleApplication>().container.focusMode

    override fun before() {
        focusMode.value = false
    }

    override fun after() {
        focusMode.value = false
    }
}
