package io.github.klaboukes.twangle.app.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRedrawsTest {

    private val english = WidgetLook("en-GB", dark = false)
    private val redraws = WidgetRedraws(english)

    // Until Phase 89 every configuration change redrew it, a turn of the phone too.
    @Test
    fun `a change that leaves the language and dark mode as they were redraws nothing`() {
        assertFalse(redraws.needed(english))
        assertFalse(redraws.needed(WidgetLook("en-GB", dark = false)))
    }

    @Test
    fun `a new language or dark mode redraws it, once`() {
        assertTrue(redraws.needed(WidgetLook("nl-NL", dark = false)))
        assertFalse(redraws.needed(WidgetLook("nl-NL", dark = false)))
        assertTrue(redraws.needed(WidgetLook("nl-NL", dark = true)))
        assertFalse(redraws.needed(WidgetLook("nl-NL", dark = true)))
        assertTrue("back as before", redraws.needed(english))
    }
}
