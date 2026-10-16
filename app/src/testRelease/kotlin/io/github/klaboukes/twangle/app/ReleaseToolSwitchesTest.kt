package io.github.klaboukes.twangle.app

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The release build (the Play build) is the v1.0 app: every after-v1.0 tool is hidden (Phase 28)
 * until the owner releases it in 1.1 (Phase 34), when its switch turns on here or goes.
 */
class ReleaseToolSwitchesTest {

    @Test
    fun everySwitchIsOff() {
        assertEquals(emptyList<String>(), buildToolSwitches.byName.filterValues { it }.keys.toList())
    }
}
