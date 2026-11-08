package io.github.klaboukes.twangle.app

import androidx.test.core.app.ApplicationProvider
import io.github.klaboukes.twangle.core.testing.isHighContrastPass
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/**
 * In the full check's high-contrast pass ([isHighContrastPass], Phase 86) shows the app in the high-contrast look
 * through its own setting, and afterwards puts back the choice it found (none: following the phone). Outside the pass
 * it changes nothing. The look is read when the activity starts, so put it around any rule that launches `MainActivity`.
 */
class HighContrastPassRule : ExternalResource() {

    private val settings = ApplicationProvider.getApplicationContext<TwangleApplication>().container.settingsRepository
    private var found: Boolean? = null

    override fun before() {
        if (!isHighContrastPass) return
        runBlocking {
            found = settings.settings.first().highContrast
            settings.setHighContrast(true)
        }
    }

    override fun after() {
        if (isHighContrastPass) runBlocking { settings.setHighContrast(found) }
    }
}
