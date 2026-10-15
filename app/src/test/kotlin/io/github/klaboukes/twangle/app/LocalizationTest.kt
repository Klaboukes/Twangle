package io.github.klaboukes.twangle.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the translations. The supported languages are listed once, as `twangle.languages` in
 * gradle.properties (the first is the default, in `res/values/`). Every module's default strings must
 * have a copy in each other language with the same placeholders, no folder may hold a language that
 * isn't listed, and Kotlin code must not show hard-coded text. Lint's MissingTranslation only works
 * once a module has a translation folder, and it doesn't look inside Composables at all.
 */
class LocalizationTest {

    private val root: File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    private val modules: List<File> =
        listOf("app", "wear", "feature", "core").map { File(root, it) }
            .flatMap { if (File(it, "src").isDirectory) listOf(it) else it.listFiles().orEmpty().toList() }
            .filter { File(it, "src/main").isDirectory }

    /** `twangle.languages` from gradle.properties: the default language first, then the translations. */
    private val languages: List<String> = Properties()
        .apply { File(root, "gradle.properties").reader().use { load(it) } }
        .getProperty("twangle.languages").orEmpty()
        .split(",").map { it.trim() }.filter { it.isNotEmpty() }

    private val translations: List<String> get() = languages.drop(1)

    @Test
    fun `the language list starts with the default language and has no repeats`() {
        assertTrue("twangle.languages in gradle.properties is empty", languages.isNotEmpty())
        assertEquals("Repeated languages in twangle.languages", languages.distinct(), languages)
        languages.forEach { assertTrue("Not a resource qualifier: $it", LANGUAGE_QUALIFIER.matches(it)) }
        val resources = Properties()
            .apply { File(root, "app/src/main/res/resources.properties").reader().use { load(it) } }
        assertEquals(
            "unqualifiedResLocale must be the first of twangle.languages",
            languages.first(),
            resources.getProperty("unqualifiedResLocale"),
        )
    }

    @Test
    fun `both apps take their languages from the shared list`() {
        val apps = modules.filter { File(it, "build.gradle.kts").readText().contains("plugins.android.application") }
        assertTrue("No app modules found", apps.isNotEmpty())
        apps.forEach { app ->
            val build = File(app, "build.gradle.kts").readText()
            assertTrue(
                "${app.name}/build.gradle.kts must set localeFilters from twangle.languages",
                build.contains("""localeFilters += providers.gradleProperty("twangle.languages")"""),
            )
        }
    }

    @Test
    fun `every translation folder is a supported language`() {
        val found = modules.flatMap { module ->
            File(module, "src/main/res").listFiles().orEmpty()
                .filter { it.name.startsWith("values-") && File(it, "strings.xml").isFile }
                .map { it.name.removePrefix("values-") to module.name }
        }
        val unknown = found.filter { (language, _) -> language !in translations }
        assertTrue(
            "Texts in languages missing from twangle.languages (gradle.properties): " +
                unknown.joinToString { (language, module) -> "values-$language in $module" },
            unknown.isEmpty(),
        )
        translations.forEach { language ->
            assertTrue("No module has texts in $language", found.any { it.first == language })
        }
    }

    @Test
    fun `every translatable string has a translation with the same placeholders in every language`() {
        assertTrue("Only the default language is listed; nothing to check", translations.isNotEmpty())
        var checked = 0
        for (module in modules) {
            val english = translatableStrings(File(module, "src/main/res/values/strings.xml"))
            if (english.isEmpty()) continue
            for (language in translations) {
                val where = "values-$language/strings.xml in ${module.name}"
                val translated = translatableStrings(File(module, "src/main/res/values-$language/strings.xml"))
                assertEquals("Strings in $where", english.keys, translated.keys)
                english.forEach { (name, text) ->
                    val other = translated.getValue(name)
                    assertEquals("Placeholders of $name in $where", placeholders(text.main), placeholders(other.main))
                    other.quantities.forEach { (quantity, item) ->
                        assertTrue("Plural quantity \"$quantity\" of $name in $where", quantity in QUANTITIES)
                        assertTrue(
                            "Placeholders of $name ($quantity) in $where must be among those of \"other\"",
                            placeholders(text.main).containsAll(placeholders(item)),
                        )
                    }
                }
                checked += english.size
            }
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

    /**
     * One translatable text: a `<string>`'s text, or a `<plurals>`' "other" item as [main] with every
     * item by quantity in [quantities].
     */
    private class Text(val main: String, val quantities: Map<String, String> = emptyMap())

    /** Name → text of each translatable `<string>` and `<plurals>`. */
    private fun translatableStrings(file: File): Map<String, Text> {
        if (!file.isFile) return emptyMap()
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val result = mutableMapOf<String, Text>()
        doc.documentElement.childElements().filter { it.getAttribute("translatable") != "false" }.forEach { e ->
            val name = e.getAttribute("name")
            when (e.tagName) {
                "string" -> result[name] = Text(e.textContent)
                "plurals" -> {
                    val items = e.childElements().associate { it.getAttribute("quantity") to it.textContent }
                    val other = items["other"]
                    assertTrue("Plurals $name in ${file.relativeTo(root)} has no \"other\" item", other != null)
                    result[name] = Text(other!!, items)
                }
            }
        }
        return result
    }

    private fun Element.childElements(): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>()

    private fun placeholders(text: String): List<String> = PLACEHOLDER.findAll(text).map { it.value }.sorted().toList()

    private companion object {
        /** A language as a resource qualifier: `nl`, `pt-rBR`, or BCP 47 like `b+sr+Latn`. */
        val LANGUAGE_QUALIFIER = Regex("""[a-z]{2,3}(-r[A-Z]{2})?|b(\+[A-Za-z0-9]+)+""")

        /** The plural quantities Android knows. */
        val QUANTITIES = setOf("zero", "one", "two", "few", "many", "other")

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
