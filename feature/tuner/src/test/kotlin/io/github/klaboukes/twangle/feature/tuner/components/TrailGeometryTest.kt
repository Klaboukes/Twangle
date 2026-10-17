package io.github.klaboukes.twangle.feature.tuner.components

import io.github.klaboukes.twangle.feature.tuner.components.TrailGeometry.BOTTOM
import io.github.klaboukes.twangle.feature.tuner.components.TrailGeometry.MIDDLE
import io.github.klaboukes.twangle.feature.tuner.components.TrailGeometry.TOP
import io.github.klaboukes.twangle.feature.tuner.components.TrailGeometry.X_NOW
import io.github.klaboukes.twangle.feature.tuner.components.TrailGeometry.X_OLDEST
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailGeometryTest {

    @Test
    fun `sharp is up, flat is down, ±50 cents over the height`() {
        assertEquals(MIDDLE, TrailGeometry.yOf(0f), 1e-4f)
        assertEquals(TOP, TrailGeometry.yOf(50f), 1e-4f)
        assertEquals(BOTTOM, TrailGeometry.yOf(-50f), 1e-4f)
        assertEquals((TOP + MIDDLE) / 2f, TrailGeometry.yOf(25f), 1e-4f)
        assertTrue(TrailGeometry.yOf(10f) < TrailGeometry.yOf(5f))
    }

    @Test
    fun `readings further off are drawn at the edge`() {
        assertEquals(TOP, TrailGeometry.yOf(80f), 1e-4f)
        assertEquals(BOTTOM, TrailGeometry.yOf(-300f), 1e-4f)
    }

    @Test
    fun `time runs from right to left over four seconds`() {
        assertEquals(X_NOW, TrailGeometry.xOf(0f), 1e-4f)
        assertEquals(X_OLDEST, TrailGeometry.xOf(TrailGeometry.SPAN_SECONDS), 1e-4f)
        assertEquals((X_NOW + X_OLDEST) / 2f, TrailGeometry.xOf(2f), 1e-4f)
        // Older readings run out of the left edge.
        assertTrue(TrailGeometry.xOf(4.2f) < X_OLDEST)
        // The newest point, a little in from the panel's right edge.
        assertTrue(X_NOW < TrailGeometry.UNITS_WIDE - 20f)
    }

    @Test
    fun `the oldest third fades out`() {
        assertEquals(0f, TrailGeometry.fadeAt(X_OLDEST), 0f)
        assertEquals(0f, TrailGeometry.fadeAt(0f), 0f)
        assertEquals(1f, TrailGeometry.fadeAt(TrailGeometry.xOf(TrailGeometry.SPAN_SECONDS * 2f / 3f)), 1e-4f)
        assertEquals(0.5f, TrailGeometry.fadeAt(TrailGeometry.xOf(TrailGeometry.SPAN_SECONDS * 5f / 6f)), 1e-4f)
        assertEquals(1f, TrailGeometry.fadeAt(X_NOW), 0f)
    }
}
