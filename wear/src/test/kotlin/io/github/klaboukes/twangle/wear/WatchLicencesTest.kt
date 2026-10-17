package io.github.klaboukes.twangle.wear

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The watch app has no About page, so its licence texts ship as assets: Apache 2.0 for AndroidX,
 * Compose and Kotlin, and the OFL for the music signs font.
 */
class WatchLicencesTest {

    // Gradle runs unit tests in the module folder.
    private val licences = File("src/main/assets/licenses")

    @Test
    fun theApacheLicenceShipsWithTheWatchApp() {
        val text = File(licences, "Apache-2.0.txt").readText()
        assertTrue(text.contains("Apache License") && text.contains("Version 2.0, January 2004"))
    }

    @Test
    fun theMusicSignsFontLicenceShipsWithTheWatchApp() {
        val text = File(licences, "OFL-notosanssymbols.txt").readText()
        assertTrue(text.contains("licensed under the SIL Open Font License, Version 1.1"))
    }
}
