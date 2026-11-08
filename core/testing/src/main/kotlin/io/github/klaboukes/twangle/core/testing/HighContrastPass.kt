package io.github.klaboukes.twangle.core.testing

import androidx.test.platform.app.InstrumentationRegistry

/**
 * True in the full check's high-contrast pass (Phase 86): `scripts/ui-tests.sh --high-contrast` passes
 * `-e highContrast true`. Every test's content in [createSharedActivityComposeRule] then shows in the high-contrast
 * look wherever its `TwangleTheme` names no look of its own (`LocalHighContrastByDefault`), so the tests that loop
 * over light and dark check the high-contrast look's light and dark faces; the app's tests turn the look on through
 * its setting.
 */
val isHighContrastPass: Boolean
    get() = InstrumentationRegistry.getArguments().getString("highContrast") == "true"
