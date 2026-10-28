package io.github.klaboukes.twangle.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [TranslationCheck] on a made-up tree with one module in English, Dutch and German: what daily work
 * may leave for the night, and what the strict check (the release gate) catches.
 */
class TranslationCheckTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val root: File by lazy { folder.root }
    private val module: File by lazy { File(root, "app") }

    private fun strings(language: String?, vararg texts: Pair<String, String>) {
        val dir = File(module, "src/main/res/" + if (language == null) "values" else "values-$language")
        dir.mkdirs()
        File(dir, "strings.xml").writeText(
            "<resources>\n" + texts.joinToString("") { (name, text) -> "    <string name=\"$name\">$text</string>\n" } +
                "</resources>\n",
        )
    }

    /** What `scripts/translations.py mark` writes: today's English hash of every text the language has. */
    private fun mark(language: String) {
        val english = TranslationCheck.translatableStrings(File(module, "src/main/res/values/strings.xml"))
        val translated = TranslationCheck.translatableStrings(File(module, "src/main/res/values-$language/strings.xml"))
        File(root, "translations").mkdirs()
        File(root, "translations/$language.txt").writeText(
            "# record\n" + english.filterKeys { it in translated }
                .entries.joinToString("") { (name, text) -> "app $name ${TranslationCheck.englishHash(text)}\n" },
        )
    }

    private fun problems(strict: Boolean) =
        TranslationCheck(root, listOf(module), listOf("en", "nl", "de"), listOf("en", "nl"), strict).problems()

    private fun allTranslated() {
        strings(null, "in_tune" to "Fine", "cents" to "%1\$d cents")
        strings("nl", "in_tune" to "Goed", "cents" to "%1\$d cent")
        strings("de", "in_tune" to "Gut", "cents" to "%1\$d Cent")
        mark("nl")
        mark("de")
    }

    @Test
    fun `a tree with every language translated and recorded passes both checks`() {
        allTranslated()
        assertEquals(emptyList<String>(), problems(strict = false))
        assertEquals(emptyList<String>(), problems(strict = true))
    }

    @Test
    fun `a new text in English and Dutch only passes by day and fails strict`() {
        allTranslated()
        strings(null, "in_tune" to "Fine", "cents" to "%1\$d cents", "strum" to "Strum all strings")
        strings("nl", "in_tune" to "Goed", "cents" to "%1\$d cent", "strum" to "Sla alle snaren aan")
        assertEquals(emptyList<String>(), problems(strict = false))
        val strict = problems(strict = true)
        assertTrue(strict.toString(), strict.any { "missing in values-de" in it && "strum" in it })
        assertTrue("Dutch is not in the record yet: $strict", strict.any { it.startsWith("Stale in nl") && "app:strum" in it })
    }

    @Test
    fun `a new text in English only fails by day too`() {
        allTranslated()
        strings(null, "in_tune" to "Fine", "cents" to "%1\$d cents", "strum" to "Strum all strings")
        val daily = problems(strict = false)
        assertTrue(daily.toString(), daily.any { "missing in values-nl" in it && "strum" in it })
        assertTrue(daily.toString(), daily.none { "values-de" in it })
    }

    @Test
    fun `reworded English makes its translations stale in strict only`() {
        allTranslated()
        strings(null, "in_tune" to "In tune", "cents" to "%1\$d cents")
        strings("nl", "in_tune" to "Gestemd", "cents" to "%1\$d cent")
        assertEquals(emptyList<String>(), problems(strict = false))
        val strict = problems(strict = true)
        assertTrue(strict.toString(), strict.any { it.startsWith("Stale in de") && "app:in_tune" in it && "cents" !in it })
        assertTrue(strict.toString(), strict.any { it.startsWith("Stale in nl") && "app:in_tune" in it })
        mark("nl")
        strings("de", "in_tune" to "Gestimmt", "cents" to "%1\$d Cent")
        mark("de")
        assertEquals(emptyList<String>(), problems(strict = true))
    }

    @Test
    fun `wrong placeholders and texts English lacks fail by day in every language`() {
        allTranslated()
        strings("de", "in_tune" to "Gut", "cents" to "%1\$s Cent", "old" to "Alt")
        val daily = problems(strict = false)
        assertTrue(daily.toString(), daily.any { "Placeholders of cents in values-de" in it })
        assertTrue(daily.toString(), daily.any { "values-de" in it && "English doesn't have" in it && "old" in it })
    }

    @Test
    fun `strict wants a record for every language`() {
        allTranslated()
        File(root, "translations/de.txt").delete()
        assertEquals(emptyList<String>(), problems(strict = false))
        assertTrue(problems(strict = true).any { it.startsWith("No translation record for de") })
    }

    @Test
    fun `the hash matches the one scripts translations py writes`() {
        // python -I scripts/translations.py hashes "Fine" and a plural the same way; a change on either
        // side would make every text stale.
        assertEquals("fa67303a", TranslationCheck.englishHash(TranslationCheck.Text("  Fine\n ")))
        val plural = TranslationCheck.Text("%1\$d cents", linkedMapOf("one" to "%1\$d cent", "other" to "%1\$d cents"), isPlural = true)
        assertEquals(PLURAL_HASH, TranslationCheck.englishHash(plural))
    }

    private companion object {
        const val PLURAL_HASH = "db1f267c"
    }
}
