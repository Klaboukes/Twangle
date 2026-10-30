package io.github.klaboukes.twangle.wear

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchCentsTest {

    private val mark = ""

    @Test
    fun `the unit after the number keeps its space`() {
        assertEquals("" to " cents", centsWords("$mark cents"))
    }

    @Test
    fun `words before the number stay before it`() {
        assertEquals("حوالي " to " سنت", centsWords("حوالي $mark سنت"))
    }

    @Test
    fun `a text without the number's place keeps all its words after it`() {
        assertEquals("" to " cents", centsWords("cents"))
    }
}
