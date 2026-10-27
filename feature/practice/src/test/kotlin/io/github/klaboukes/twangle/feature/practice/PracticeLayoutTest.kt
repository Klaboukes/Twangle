package io.github.klaboukes.twangle.feature.practice

import org.junit.Assert.assertEquals
import org.junit.Test

class PracticeLayoutTest {

    @Test
    fun `phones keep the column upright and the halves on their side`() {
        // The Xiaomi 12X, a short narrow phone, a 320 dp phone and a large phone (448 x 998 dp) less their bars.
        for ((width, height) in listOf(393f to 760f, 360f to 570f, 320f to 640f, 448f to 900f)) {
            assertEquals("$width x $height", PracticeLayout.COLUMN, practiceLayoutFor(width, height))
        }
        // On their side beside the rail: the S26 with buttons, the Xiaomi, a tall phone.
        for ((width, height) in listOf(700f to 330f, 793f to 369f, 900f to 430f)) {
            assertEquals("$width x $height", PracticeLayout.HALVES, practiceLayoutFor(width, height))
        }
    }

    @Test
    fun `a tablet gets the large layouts both ways`() {
        // The Lenovo Tab M11 (1280 x 800 dp): upright less the bars, on its side less the rail and the bars.
        assertEquals(PracticeLayout.LARGE_COLUMN, practiceLayoutFor(800f, 1120f))
        assertEquals(PracticeLayout.LARGE_SIDE, practiceLayoutFor(1200f, 716f))
        assertEquals(PracticeLayout.LARGE_SIDE, practiceLayoutFor(1280f, 800f))
        assertEquals("a small tablet upright", PracticeLayout.LARGE_COLUMN, practiceLayoutFor(600f, 900f))
    }

    @Test
    fun `the large layouts start at 560 dp both ways`() {
        assertEquals(PracticeLayout.HALVES, practiceLayoutFor(1000f, 559f))
        assertEquals(PracticeLayout.LARGE_SIDE, practiceLayoutFor(1000f, 560f))
        assertEquals(PracticeLayout.COLUMN, practiceLayoutFor(559f, 1000f))
        assertEquals(PracticeLayout.LARGE_COLUMN, practiceLayoutFor(560f, 1000f))
        assertEquals("not wide below 600 dp", PracticeLayout.LARGE_COLUMN, practiceLayoutFor(590f, 580f))
    }
}
