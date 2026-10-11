package io.github.klaboukes.twangle.feature.settings.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CreditsTest {

    @Test
    fun paragraphLinesAreJoinedAndParagraphsKept() {
        val text = "   Apache License\r\n   Version 2.0\r\n\r\n\r\n 1. Definitions.\n\n  \"License\" shall mean\n  the terms.\n"
        assertEquals("Apache License Version 2.0\n\n1. Definitions.\n\n\"License\" shall mean the terms.", reflowLicence(text))
    }

    @Test
    fun rulersAreDroppedAndCapitalHeadingsKeepTheirOwnLine() {
        val text = """
            -----------------------
            SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007
            -----------------------

            PREAMBLE
            The goals of the Open Font License
            are to stimulate.

            DISCLAIMER
            THE FONT SOFTWARE IS PROVIDED "AS IS",
            WITHOUT WARRANTY.

            PERMISSION & CONDITIONS
            Permission is hereby granted.
        """.trimIndent()
        assertEquals(
            listOf(
                "SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007",
                "PREAMBLE",
                "The goals of the Open Font License are to stimulate.",
                "DISCLAIMER",
                "THE FONT SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY.",
                "PERMISSION & CONDITIONS",
                "Permission is hereby granted.",
            ),
            reflowLicence(text).split("\n\n"),
        )
    }

    @Test
    fun everyCreditHasALicenceName() {
        assertTrue(credits.isNotEmpty())
        credits.forEach { assertTrue(it.name, it.licenceName.isNotBlank() && it.author.isNotBlank()) }
        assertEquals("names are unique", credits.size, credits.map { it.name }.toSet().size)
    }

    @Test
    fun theApacheLicenceIsBundledWithThisModule() {
        // Gradle runs unit tests in the module folder. The font licences are checked in :core:designsystem.
        credits.filter { it.licenceName.startsWith("Apache") }.forEach {
            val file = File("src/main/assets/${it.licenceAsset}")
            assertTrue("${file.path} missing", file.isFile)
            assertTrue(file.readText().contains("Apache License") && file.readText().contains("Version 2.0, January 2004"))
        }
    }
}
