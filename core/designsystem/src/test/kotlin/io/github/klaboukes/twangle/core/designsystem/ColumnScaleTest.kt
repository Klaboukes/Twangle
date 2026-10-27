package io.github.klaboukes.twangle.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class ColumnScaleTest {
    @Test
    fun phonesKeepTheColumnAtItsOwnSize() {
        // Upright: a Galaxy S26, the Xiaomi 12X, a large phone; on its side beside the rail (focus mode's controls).
        for ((width, height) in listOf(360f to 780f, 393f to 873f, 480f to 1000f, 578f to 330f, 780f to 360f)) {
            assertEquals("$width × $height", 1f, columnScaleFor(width, height), 0f)
        }
    }

    @Test
    fun anUprightTabletGrowsTheColumnWithItsWidth() {
        // The Lenovo Tab M11 upright (the tuner's or the metronome's space under the status bar, over the navigation bar).
        assertEquals(800f / COLUMN_SCALE_BASE_WIDTH_DP, columnScaleFor(800f, 1100f), 0.001f)
        // A short window keeps it from growing past what its height has room for.
        assertEquals(840f / COLUMN_SCALE_BASE_HEIGHT_DP, columnScaleFor(840f, 840f), 0.001f)
    }

    @Test
    fun theColumnGrowsSmoothlyAndStopsAtItsLargest() {
        assertEquals(1f, columnScaleFor(COLUMN_SCALE_BASE_WIDTH_DP, 1200f), 0f)
        assertEquals(1.01f, columnScaleFor(COLUMN_SCALE_BASE_WIDTH_DP * 1.01f, 1200f), 0.001f)
        assertEquals(COLUMN_MAX_SCALE, columnScaleFor(1200f, 1900f), 0f)
    }
}
