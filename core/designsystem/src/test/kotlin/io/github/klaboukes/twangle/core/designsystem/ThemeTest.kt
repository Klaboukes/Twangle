package io.github.klaboukes.twangle.core.designsystem

import io.github.klaboukes.twangle.core.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTest {

    @Test
    fun `system follows the phone`() {
        assertEquals(true, ThemeMode.SYSTEM.useDarkTheme(systemDark = true))
        assertEquals(false, ThemeMode.SYSTEM.useDarkTheme(systemDark = false))
    }

    @Test
    fun `light and dark ignore the phone`() {
        assertEquals(false, ThemeMode.LIGHT.useDarkTheme(systemDark = true))
        assertEquals(true, ThemeMode.DARK.useDarkTheme(systemDark = false))
    }
}
