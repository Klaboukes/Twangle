package io.github.klaboukes.twangle.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** "Rate Twangle" opens Twangle's own Play page, in the Play Store app or else on the web. */
class PlayPageTest {

    @Test
    fun `the Play Store app link names the app`() {
        assertEquals("market://details?id=io.github.klaboukes.twangle", playStoreAppLink("io.github.klaboukes.twangle"))
    }

    @Test
    fun `the web link is the same page on play google com`() {
        assertEquals(
            "https://play.google.com/store/apps/details?id=io.github.klaboukes.twangle",
            playStoreWebLink("io.github.klaboukes.twangle"),
        )
    }
}
