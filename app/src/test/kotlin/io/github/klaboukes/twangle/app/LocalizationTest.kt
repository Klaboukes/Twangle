package io.github.klaboukes.twangle.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the translations. Every module's English strings must have a Dutch version with the same
 * placeholders, and Kotlin code must not show hard-coded text. Lint's MissingTranslation only works
 * once a module has a values-nl folder, and it doesn't look inside Composables at all.
 */
class LocalizationTest {

    private val root: File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    private val modules: List<File> =
        listOf("app", "feature", "core").map { File(root, it) }
            .flatMap { if (File(it, "src").isDirectory) listOf(it) else it.listFiles().orEmpty().toList() }
            .filter { File(it, "src/main").isDirectory }

    @Test
    fun `every translatable string has a Dutch translation with the same placeholders`() {
        var checked = 0
        for (module in modules) {
            val english = translatableStrings(File(module, "src/main/res/values/strings.xml"))
            if (english.isEmpty()) continue
            val dutch = translatableStrings(File(module, "src/main/res/values-nl/strings.xml"))
            assertEquals("Dutch strings in ${module.name}", english.keys, dutch.keys)
            english.forEach { (name, text) ->
                assertEquals("Placeholders of $name in ${module.name}", placeholders(text), placeholders(dutch.getValue(name)))
            }
            checked += english.size
        }
        assertTrue("No strings found; is the repo root right?", checked > 0)
    }

    @Test
    fun `kotlin code has no hard-coded user-facing text`() {
        val offenders = modules
            .flatMap { File(it, "src/main/kotlin").walk().filter { f -> f.extension == "kt" }.toList() }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    val code = line.trim()
                    if (code.startsWith("//") || code.startsWith("*")) return@mapIndexedNotNull null
                    if (HARD_CODED_TEXT.any { it.containsMatchIn(code) }) "${file.relativeTo(root)}:${i + 1}: $code" else null
                }
            }
        assertTrue("Move these texts to res/values/strings.xml:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    /** Name → text of each translatable `<string>`, and of each `<plurals>`' "other" item. */
    private fun translatableStrings(file: File): Map<String, String> {
        if (!file.isFile) return emptyMap()
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val result = mutableMapOf<String, String>()
        doc.documentElement.childElements().filter { it.getAttribute("translatable") != "false" }.forEach { e ->
            when (e.tagName) {
                "string" -> result[e.getAttribute("name")] = e.textContent
                "plurals" -> result[e.getAttribute("name")] =
                    e.childElements().first { it.getAttribute("quantity") == "other" }.textContent
            }
        }
        return result
    }

    private fun Element.childElements(): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>()

    private fun placeholders(text: String): List<String> = PLACEHOLDER.findAll(text).map { it.value }.sorted().toList()

    private companion object {
        val PLACEHOLDER = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[a-zA-Z]""")

        /** A string literal with at least one letter, passed where users would see or hear it. */
        val HARD_CODED_TEXT = listOf(
            Regex("""\bText\(\s*"[^"]*\p{L}"""),
            Regex("""\b(text|title|summary|contentDescription|stateDescription|onClickLabel)\s*=\s*"[^"]*\p{L}"""),
            Regex("""\bonClick\(\s*label\s*=\s*"[^"]*\p{L}"""),
            Regex("""\bToast\.makeText\([^,]*,\s*""""),
        )
    }
}
