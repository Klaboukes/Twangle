package io.github.klaboukes.twangle.feature.tuner.tour

import io.github.klaboukes.twangle.feature.tuner.R
import org.junit.Assert.assertEquals
import org.junit.Test

class TourTextTest {

    @Test
    fun `each step says its own text`() {
        for (part in TourPart.entries) assertEquals(part.name, part.text, tourTextOf(part, moreTools = false))
    }

    @Test
    fun `with more tools the strum check's step names the more tools button`() {
        assertEquals(R.string.tour_more_tools, tourTextOf(TourPart.STRUM, moreTools = true))
        assertEquals(R.string.tour_navigation, tourTextOf(TourPart.NAVIGATION, moreTools = true))
    }

    @Test
    fun `with the Practice tab the last step names Practice, the others don't change`() {
        assertEquals(R.string.tour_navigation_practice, tourTextOf(TourPart.NAVIGATION, moreTools = true, practice = true))
        for (part in TourPart.entries - TourPart.NAVIGATION) {
            assertEquals(part.name, tourTextOf(part, moreTools = false), tourTextOf(part, moreTools = false, practice = true))
        }
    }
}
