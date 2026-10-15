package io.github.klaboukes.twangle.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemBarsTest {

    @Test
    fun `gesture navigation gets no scrim, also where the gesture bar counts as tappable (MIUI)`() {
        assertFalse(navigationButtonsShown(navigationMode = 2, left = 0, right = 0, bottom = 0))
        assertFalse(navigationButtonsShown(navigationMode = 2, left = 0, right = 0, bottom = 132))
    }

    @Test
    fun `3-button and 2-button navigation keep the scrim`() {
        assertTrue(navigationButtonsShown(navigationMode = 0, left = 0, right = 0, bottom = 132))
        assertTrue(navigationButtonsShown(navigationMode = 1, left = 0, right = 0, bottom = 132))
    }

    @Test
    fun `without the setting, a tappable bar on any side but the top means buttons`() {
        assertFalse(navigationButtonsShown(navigationMode = null, left = 0, right = 0, bottom = 0))
        assertTrue(navigationButtonsShown(navigationMode = null, left = 0, right = 0, bottom = 126))
        assertTrue(navigationButtonsShown(navigationMode = null, left = 126, right = 0, bottom = 0))
        assertTrue(navigationButtonsShown(navigationMode = null, left = 0, right = 126, bottom = 0))
    }
}
