package io.github.klaboukes.twangle.feature.practice

import org.junit.Assert.assertEquals
import org.junit.Test

class BreakableAtHyphensTest {

    @Test
    fun `a compound may break after its hyphen`() {
        assertEquals("A♭ majeur-${ZERO_WIDTH_SPACE}pentatonisch", breakableAtHyphens("A♭ majeur-pentatonisch"))
    }

    @Test
    fun `a flat or sharp before the hyphen counts as a letter`() {
        assertEquals("A♭-${ZERO_WIDTH_SPACE}Mixolydisch", breakableAtHyphens("A♭-Mixolydisch"))
        assertEquals("G♯-${ZERO_WIDTH_SPACE}Moll", breakableAtHyphens("G♯-Moll"))
    }

    @Test
    fun `a hyphen not followed by a letter stays as it is`() {
        assertEquals("A♭ -5", breakableAtHyphens("A♭ -5"))
        assertEquals("C-", breakableAtHyphens("C-"))
    }
}
