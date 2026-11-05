package io.github.klaboukes.twangle.feature.tuner.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** The cents big in the word's place (Settings' Under the meter: Cents, Phase 78). */
class SignedCentsTest {

    @Test
    fun `flat is a minus sign, sharp a plus, rounded to whole cents`() {
        assertEquals("\u221212", signedCentsText(-12.3f, Locale.ENGLISH))
        assertEquals("+8", signedCentsText(7.6f, Locale.ENGLISH))
        assertEquals("\u22121", signedCentsText(-0.6f, Locale.ENGLISH))
    }

    @Test
    fun `nothing off has no sign`() {
        assertEquals("0", signedCentsText(0.4f, Locale.ENGLISH))
        assertEquals("0", signedCentsText(-0.4f, Locale.ENGLISH))
    }

    @Test
    fun `the minus is the real minus sign, not a hyphen`() {
        val flat = signedCentsText(-5f, Locale.ENGLISH)
        assertEquals('\u2212', flat.first())
        assertEquals(false, '-' in flat)
    }

    @Test
    fun `the digits are the language's own`() {
        assertEquals("\u2212\u0661\u0662", signedCentsText(-12f, Locale.forLanguageTag("ar-EG")))
        assertEquals("+12", signedCentsText(12f, Locale.forLanguageTag("nl")))
    }
}
