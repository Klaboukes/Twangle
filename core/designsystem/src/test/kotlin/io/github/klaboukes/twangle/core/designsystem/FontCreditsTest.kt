package io.github.klaboukes.twangle.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The About page credits every bundled font, and each credit's licence text is bundled too. */
class FontCreditsTest {

    // Gradle runs unit tests in the module folder.
    private val main = File("src/main")

    @Test
    fun everyBundledFontIsCredited() {
        val fonts = File(main, "res/font").list().orEmpty().filter { it.endsWith(".ttf") || it.endsWith(".otf") }.toSet()
        assertTrue("no fonts found in ${main.absolutePath}", fonts.isNotEmpty())
        assertEquals(fonts, bundledFontCredits.map { it.fontFile }.toSet())
    }

    @Test
    fun everyCreditHasItsOpenFontLicenceBundled() {
        bundledFontCredits.forEach { credit ->
            val licence = File(main, "assets/${credit.licenceAsset}")
            assertTrue("${credit.name}: ${licence.path} missing", licence.isFile)
            assertTrue("${credit.name}: not the OFL", licence.readText().contains("licensed under the SIL Open Font License, Version 1.1"))
        }
    }

    @Test
    fun licenceTextsShowNoBuildDetails() {
        // The About page shows these texts, so no script names or source paths in them.
        val buildDetail = Regex("""scripts/|res/|\.py\b|\.sh\b|\.ttf\b|\.otf\b""")
        bundledFontCredits.forEach { credit ->
            val text = File(main, "assets/${credit.licenceAsset}").readText()
            assertTrue("${credit.name}: ${buildDetail.find(text)?.value}", !buildDetail.containsMatchIn(text))
        }
    }
}
