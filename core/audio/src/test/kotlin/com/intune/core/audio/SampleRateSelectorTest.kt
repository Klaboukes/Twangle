package com.intune.core.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class SampleRateSelectorTest {

    @Test
    fun `native 48 kHz comes first without duplicates`() {
        assertEquals(listOf(48_000, 44_100, 32_000, 16_000, 8_000), SampleRateSelector.candidates("48000"))
    }

    @Test
    fun `native 44_1 kHz is tried before the fallbacks`() {
        assertEquals(listOf(44_100, 48_000, 32_000, 16_000, 8_000), SampleRateSelector.candidates("44100"))
    }

    @Test
    fun `unknown, unparsable or out-of-range native rates use the fallbacks`() {
        for (property in listOf(null, "", "abc", "96000", "16000", "-1")) {
            assertEquals("property=$property", SampleRateSelector.FALLBACK_RATES, SampleRateSelector.candidates(property))
        }
    }

    @Test
    fun `whitespace around the property is ignored`() {
        assertEquals(44_100, SampleRateSelector.candidates(" 44100 ").first())
    }

    @Test
    fun `select picks the first supported candidate`() {
        assertEquals(44_100, SampleRateSelector.select("48000") { it == 44_100 })
        assertEquals(48_000, SampleRateSelector.select("44100") { it == 48_000 })
    }

    @Test
    fun `select falls back to low rates such as 16 or 8 kHz`() {
        assertEquals(16_000, SampleRateSelector.select("48000") { it <= 16_000 })
        assertEquals(8_000, SampleRateSelector.select(null) { it == 8_000 })
    }

    @Test
    fun `select falls back to 48 kHz when nothing reports support`() {
        assertEquals(AudioConfig.DEFAULT_SAMPLE_RATE, SampleRateSelector.select("44100") { false })
    }
}
