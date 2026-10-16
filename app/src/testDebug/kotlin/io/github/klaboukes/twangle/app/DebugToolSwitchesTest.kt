package io.github.klaboukes.twangle.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** Debug builds (the owner's test builds and the UI tests) show every after-v1.0 tool (Phase 28). */
class DebugToolSwitchesTest {

    @Test
    fun everySwitchIsOn() {
        assertEquals(emptyList<String>(), buildToolSwitches.byName.filterValues { !it }.keys.toList())
    }
}
