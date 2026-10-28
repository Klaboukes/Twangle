package io.github.klaboukes.twangle.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/**
 * Guards the translations. The supported languages are listed once, as `twangle.languages` in
 * gradle.properties (the first is the default, in `res/values/`). Every translation must have the
 * placeholders of its English text ([TranslationCheck]); the day languages (`twangle.dayLanguages`,
 * English and Dutch) must have every text, the others are translated at night and may lag behind
 * unless `-Ptwangle.strictTranslations=true` (the release gate), which also holds them to the record in
 * `translations/`. No folder may hold a language that isn't listed, and Kotlin code must not show
 * hard-coded text. Lint's MissingTranslation (a warning by day) doesn't look inside Composables at all.
 */
class LocalizationTest {

    private val root: File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    private val modules: List<File> =
        listOf("app", "wear", "feature", "core").map { File(root, it) }
            .flatMap { if (File(it, "src").isDirectory) listOf(it) else it.listFiles().orEmpty().toList() }
            .filter { File(it, "src/main").isDirectory }

    /** `twangle.languages` from gradle.properties: the default language first, then the translations. */
    private val languages: List<String> = gradleProperty("twangle.languages")

    private val translations: List<String> get() = languages.drop(1)

    /** `twangle.dayLanguages`: English and Dutch, written by day; the rest are translated at night. */
    private val dayLanguages: List<String> = gradleProperty("twangle.dayLanguages")

    /** `-Ptwangle.strictTranslations=true` (the release gate): every language complete and up to date. */
    private val strict: Boolean = System.getProperty("twangle.strictTranslations").toBoolean()

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
    fun `the Language setting offers the shared list`() {
        val build = File(root, "app/build.gradle.kts").readText()
        assertTrue(
            "app/build.gradle.kts must set BuildConfig.LANGUAGES from twangle.languages",
            Regex("""buildConfigField\("String", "LANGUAGES", .*providers\.gradleProperty\("twangle\.languages"\)""").containsMatchIn(build),
        )
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
    fun `the day languages start with the default language and are listed`() {
        assertEquals("twangle.dayLanguages must start with the default language", languages.first(), dayLanguages.firstOrNull())
        assertTrue("twangle.dayLanguages must be in twangle.languages", languages.containsAll(dayLanguages))
        assertTrue(
            "scripts/ui-tests.sh must take its day languages from twangle.dayLanguages",
            File(root, "scripts/ui-tests.sh").readText().contains("twangle.dayLanguages"),
        )
    }

    @Test
    fun `the translations have the English placeholders, and the day languages are complete`() {
        assertTrue("Only the default language is listed; nothing to check", translations.isNotEmpty())
        val problems = TranslationCheck(root, modules, languages, dayLanguages, strict).problems()
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
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

    private fun gradleProperty(name: String): List<String> = Properties()
        .apply { File(root, "gradle.properties").reader().use { load(it) } }
        .getProperty(name).orEmpty()
        .split(",").map { it.trim() }.filter { it.isNotEmpty() }

    private companion object {
        /** A language as a resource qualifier: `nl`, `pt-rBR`, or BCP 47 like `b+sr+Latn`. */
        val LANGUAGE_QUALIFIER = Regex("""[a-z]{2,3}(-r[A-Z]{2})?|b(\+[A-Za-z0-9]+)+""")

        /** A string literal with at least one letter, passed where users would see or hear it. */
        val HARD_CODED_TEXT = listOf(
            Regex("""\bText\(\s*"[^"]*\p{L}"""),
            Regex("""\b(text|title|summary|contentDescription|stateDescription|onClickLabel)\s*=\s*"[^"]*\p{L}"""),
            Regex("""\bonClick\(\s*label\s*=\s*"[^"]*\p{L}"""),
            Regex("""\bToast\.makeText\([^,]*,\s*""""),
        )
    }
}
