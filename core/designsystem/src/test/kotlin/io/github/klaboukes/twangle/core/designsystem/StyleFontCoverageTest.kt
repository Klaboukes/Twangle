package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import io.github.klaboukes.twangle.core.settings.VisualStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

/**
 * No word mixes two faces: in every style and language, each run of letters of one script in the
 * app's texts is either wholly in the style's display and title face or wholly not (then the phone's
 * own font draws all of it). And where the face lacks a language's script, no extra letter spacing.
 * Reads the bundled fonts' character maps and every module's strings.xml (see [facesFor]).
 */
class StyleFontCoverageTest {

    private val root: File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    private val fontFiles: Map<FontFamily, String> = mapOf(
        StyleFonts.Roman to "forum",
        StyleFonts.Humanist to "alegreya_sans_medium",
        StyleFonts.Didone to "playfair_display",
        StyleFonts.Bodoni to "bodoni_moda",
        StyleFonts.Groovy to "righteous",
        StyleFonts.Hud to "orbitron",
        StyleFonts.HudNumbers to "orbitron",
        StyleFonts.JetAge to "michroma",
    )

    private val covered = mutableMapOf<String, Set<Int>>()

    private fun charactersOf(font: String): Set<Int> = covered.getOrPut(font) {
        characterMap(File(root, "core/designsystem/src/main/res/font/$font.ttf").readBytes())
    }

    /** `twangle.languages` from gradle.properties, as resource qualifiers (e.g. "pt-rBR"). */
    private val qualifiers: List<String> = Properties()
        .apply { File(root, "gradle.properties").reader().use { load(it) } }
        .getProperty("twangle.languages").orEmpty()
        .split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** All the text of one language's strings.xml files, in every module. */
    private fun textsOf(qualifier: String, default: Boolean): String {
        val folder = if (default) "values" else "values-$qualifier"
        val files = root.walkTopDown()
            .onEnter { it.name != "build" && it.name != ".git" && it.name != ".gradle" }
            .filter { it.name == "strings.xml" && it.parentFile.name == folder && it.path.contains("src${File.separator}main") }
            .toList()
        assertTrue("no strings.xml for $qualifier", files.isNotEmpty())
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        return files.joinToString("\n") { builder.parse(it).documentElement.textContent }
    }

    /** Runs of letters in one script, e.g. "Wyłącz" or "調弦". */
    private fun letterRuns(text: String): List<String> {
        val runs = mutableListOf<String>()
        val run = StringBuilder()
        var script: Character.UnicodeScript? = null
        text.codePoints().forEach { c ->
            val s = if (Character.isLetter(c)) Character.UnicodeScript.of(c) else null
            if (s == null || s != script) {
                if (run.isNotEmpty()) runs += run.toString()
                run.clear()
            }
            if (s != null) run.appendCodePoint(c)
            script = s
        }
        if (run.isNotEmpty()) runs += run.toString()
        return runs.distinct()
    }

    @Test
    fun noWordMixesTwoFaces() {
        val problems = mutableListOf<String>()
        qualifiers.forEachIndexed { index, qualifier ->
            val language = qualifier.substringBefore("-r")
            val runs = letterRuns(textsOf(qualifier, default = index == 0))
            for (style in VisualStyle.entries) {
                val faces = facesFor(style, language)
                for (face in listOf(faces.display, faces.title)) {
                    val font = fontFiles[face] ?: continue // the phone's own font has every letter
                    val chars = charactersOf(font)
                    for (word in runs) {
                        val missing = word.codePoints().toArray().filter { it !in chars }
                        if (missing.isNotEmpty() && missing.size < word.codePointCount(0, word.length)) {
                            problems += "$style $qualifier: \"$word\" lacks ${missing.joinToString("") { String(Character.toChars(it)) }} in $font"
                        }
                    }
                }
            }
        }
        assertEquals("Words set in two faces", emptyList<String>(), problems.distinct().take(20))
    }

    @Test
    fun noExtraLetterSpacingWhereTheFaceLacksTheScript() {
        qualifiers.forEachIndexed { index, qualifier ->
            val language = qualifier.substringBefore("-r")
            val runs = letterRuns(textsOf(qualifier, default = index == 0))
            for (style in VisualStyle.entries) {
                val faces = facesFor(style, language)
                val font = fontFiles[faces.title] ?: continue
                val chars = charactersOf(font)
                val lacking = runs.filter { word -> word.codePoints().noneMatch { it in chars } }
                if (lacking.isNotEmpty()) {
                    assertEquals("$style $qualifier: \"${lacking.first()}\" is not in $font", TextUnit.Unspecified, faces.titleSpacing)
                }
            }
        }
    }

    @Test
    fun theLanguageListsMatchTheFonts() {
        // Every language in the lists is shipped, so a stale entry shows.
        val shipped = qualifiers.map { it.substringBefore("-r") }.toSet()
        assertTrue((EXTENDED_LATIN_LANGUAGES + TONE_LETTER_LANGUAGES + OTHER_SCRIPT_LANGUAGES - shipped).isEmpty())
    }

    private companion object {
        /** The characters a TrueType font maps to a glyph: its cmap's format 4 and 12 subtables. */
        fun characterMap(font: ByteArray): Set<Int> {
            val data = ByteBuffer.wrap(font)
            val tables = data.getShort(4).toInt() and 0xFFFF
            val cmap = (0 until tables).first { i ->
                String(font, 12 + 16 * i, 4, Charsets.US_ASCII) == "cmap"
            }.let { i -> data.getInt(12 + 16 * i + 8) }
            val chars = mutableSetOf<Int>()
            val subtables = data.getShort(cmap + 2).toInt() and 0xFFFF
            for (i in 0 until subtables) {
                val t = cmap + data.getInt(cmap + 4 + 8 * i + 4)
                when (data.getShort(t).toInt() and 0xFFFF) {
                    4 -> {
                        val segments = (data.getShort(t + 6).toInt() and 0xFFFF) / 2
                        val ends = t + 14
                        val starts = ends + 2 * segments + 2
                        val deltas = starts + 2 * segments
                        val offsets = deltas + 2 * segments
                        for (k in 0 until segments) {
                            val start = data.getShort(starts + 2 * k).toInt() and 0xFFFF
                            val end = data.getShort(ends + 2 * k).toInt() and 0xFFFF
                            val delta = data.getShort(deltas + 2 * k).toInt()
                            val offset = data.getShort(offsets + 2 * k).toInt() and 0xFFFF
                            for (c in start..minOf(end, 0xFFFE)) {
                                val glyph = if (offset == 0) {
                                    (c + delta) and 0xFFFF
                                } else {
                                    val g = data.getShort(offsets + 2 * k + offset + 2 * (c - start)).toInt() and 0xFFFF
                                    if (g == 0) 0 else (g + delta) and 0xFFFF
                                }
                                if (glyph != 0) chars += c
                            }
                        }
                    }
                    12 -> {
                        val groups = data.getInt(t + 12)
                        for (k in 0 until groups) {
                            val g = t + 16 + 12 * k
                            chars += data.getInt(g)..data.getInt(g + 4)
                        }
                    }
                }
            }
            return chars
        }
    }
}
