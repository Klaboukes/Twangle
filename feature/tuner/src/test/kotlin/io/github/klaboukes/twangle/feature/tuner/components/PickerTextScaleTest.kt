package io.github.klaboukes.twangle.feature.tuner.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The instrument-and-tuning button's names keep their size where they fit and shrink just enough where they don't. */
class PickerTextScaleTest {

    @Test
    fun `names that fit keep their size`() {
        assertEquals(1f, pickerTextScale(fullWidth = 200, room = 300))
        assertEquals(1f, pickerTextScale(fullWidth = 299, room = 300))
    }

    @Test
    fun `names a little too wide shrink just enough, with a margin`() {
        val scale = pickerTextScale(fullWidth = 330, room = 300)
        assertTrue("shrunk to $scale", scale < 300f / 330f)
        assertTrue("shrunk to $scale", scale > 0.85f)
        assertTrue("still too wide at $scale", 330 * scale <= 300)
    }

    @Test
    fun `names never shrink below the minimum, they wrap instead`() {
        assertEquals(MIN_PICKER_TEXT_SCALE, pickerTextScale(fullWidth = 900, room = 300))
        assertEquals(MIN_PICKER_TEXT_SCALE, pickerTextScale(fullWidth = 300, room = 0))
    }
}
