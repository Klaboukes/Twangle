package io.github.klaboukes.twangle.core.designsystem

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

    @Test
    fun `a Korean word is kept whole and may break only at its spaces`() {
        assertEquals(
            "G♯ 프${WORD_JOINER}리${WORD_JOINER}지${WORD_JOINER}안 도${WORD_JOINER}미${WORD_JOINER}넌${WORD_JOINER}트",
            koreanWordsKeptWhole("G♯ 프리지안 도미넌트"),
        )
    }

    @Test
    fun `text in other scripts stays as it is`() {
        assertEquals("A♭ majeur-pentatonisch", koreanWordsKeptWhole("A♭ majeur-pentatonisch"))
        assertEquals("大调五声音阶", koreanWordsKeptWhole("大调五声音阶"))
    }
}
