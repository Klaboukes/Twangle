package io.github.klaboukes.twangle.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** "Help & website" opens the website's home page in the app's language where the site has it. */
class WebsiteTest {

    @Test
    fun `English opens the home page`() {
        assertEquals("https://klaboukes.github.io/", websiteLink("en"))
    }

    @Test
    fun `Dutch opens the Dutch home page`() {
        assertEquals("https://klaboukes.github.io/nl/", websiteLink("nl"))
    }

    @Test
    fun `a language the site does not have yet opens the English home page`() {
        listOf("de", "fr", "es", "it", "pt", "zh", "ja", "ko", "hi", "in", "id", "ru", "tr", "pl", "ar").forEach {
            assertEquals(it, "https://klaboukes.github.io/", websiteLink(it))
        }
    }
}
