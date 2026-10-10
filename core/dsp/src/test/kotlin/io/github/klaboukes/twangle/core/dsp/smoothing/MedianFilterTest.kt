package io.github.klaboukes.twangle.core.dsp.smoothing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MedianFilterTest {

    @Test
    fun `median of a partly filled window`() {
        val filter = MedianFilter(5)
        assertTrue(filter.median.isNaN())
        assertEquals(3f, filter.add(3f))
        assertEquals(2f, filter.add(1f)) // mean of the two middle values
        assertEquals(3f, filter.add(9f))
        assertEquals(3, filter.count)
    }

    @Test
    fun `oldest value drops out once full`() {
        val filter = MedianFilter(3)
        for (v in listOf(1f, 2f, 3f)) filter.add(v)
        assertEquals(2f, filter.median)
        assertEquals(3f, filter.add(10f)) // window is now 2, 3, 10
        assertEquals(10f, filter.add(10f)) // 3, 10, 10
        assertEquals(3, filter.count)
    }

    @Test
    fun `a single outlier does not move the median`() {
        val filter = MedianFilter(5)
        for (v in listOf(40f, 40.1f, 39.9f, 40f)) filter.add(v)
        assertEquals(40f, filter.add(52f))
    }

    @Test
    fun `reset empties the window`() {
        val filter = MedianFilter(3)
        filter.add(5f)
        filter.reset()
        assertEquals(0, filter.count)
        assertEquals(7f, filter.add(7f))
    }

    @Test
    fun `size must be positive`() {
        assertThrows(IllegalArgumentException::class.java) { MedianFilter(0) }
    }
}
