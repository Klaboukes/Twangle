package com.intune.core.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsTest {

    @Test
    fun `normalizeA4 rounds to whole Hz`() {
        assertEquals(440f, AppSettings.normalizeA4(440.4f))
        assertEquals(441f, AppSettings.normalizeA4(440.6f))
    }

    @Test
    fun `normalizeA4 keeps the calibration range`() {
        assertEquals(415f, AppSettings.normalizeA4(415f))
        assertEquals(466f, AppSettings.normalizeA4(466f))
        assertEquals(415f, AppSettings.normalizeA4(0f))
        assertEquals(466f, AppSettings.normalizeA4(Float.POSITIVE_INFINITY))
        assertEquals(440f, AppSettings.normalizeA4(Float.NaN))
    }
}
