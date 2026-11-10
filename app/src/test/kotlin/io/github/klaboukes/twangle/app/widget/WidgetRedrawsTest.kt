package io.github.klaboukes.twangle.app.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRedrawsTest {

    private val english = WidgetLook("en-GB", dark = false)
    private val redraws = WidgetRedraws(english)

    // Until Phase 89 every configuration change redrew it, a turn of the phone too.
    @Test
    fun `a change that leaves the language, dark mode and text size as they were redraws nothing`() {
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

    // Phase 91: the text size decides whether "Open the tuner" fits under the chip.
    @Test
    fun `a new text size redraws it, once`() {
        assertTrue(redraws.needed(WidgetLook("en-GB", dark = false, fontScale = 2f)))
        assertFalse(redraws.needed(WidgetLook("en-GB", dark = false, fontScale = 2f)))
        assertTrue("back as before", redraws.needed(english))
    }
}
