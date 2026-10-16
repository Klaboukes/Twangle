package io.github.klaboukes.twangle.app

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The release build (the Play build) hides every music tool (Phase 28) until the owner lets it go
 * live (Phase 34, before v1.0), when its switch turns on here or goes. It runs in
 * the Baseline Profile plugin's `nonMinifiedRelease` and `benchmarkRelease` builds too (app/build.gradle.kts),
 * so the profile generator and the frame-time and startup runs see the release build's app as well.
 */
class ReleaseToolSwitchesTest {

    @Test
    fun everySwitchIsOff() {
        assertEquals(emptyList<String>(), buildToolSwitches.byName.filterValues { it }.keys.toList())
    }
}
