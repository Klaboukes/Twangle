package io.github.klaboukes.twangle.feature.settings

import java.io.File
import java.util.Locale
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLanguagesTest {

    @Test
    fun `resource qualifiers become language tags`() {
        assertEquals("de", AppLanguages.tagOf("de"))
        assertEquals("pt-BR", AppLanguages.tagOf("pt-rBR"))
        assertEquals("zh-CN", AppLanguages.tagOf("zh-rCN"))
        assertEquals("sr-Latn", AppLanguages.tagOf("b+sr+Latn"))
    }

    @Test
    fun `each language is named in its own words with a capital`() {
        assertEquals("English", AppLanguages.nameOf("en"))
        assertEquals("Nederlands", AppLanguages.nameOf("nl"))
        assertEquals("Deutsch", AppLanguages.nameOf("de"))
        assertEquals("Français", AppLanguages.nameOf("fr"))
        assertEquals("Polski", AppLanguages.nameOf("pl"))
        assertEquals("Русский", AppLanguages.nameOf("ru"))
        assertEquals("Türkçe", AppLanguages.nameOf("tr"))
        assertEquals("العربية", AppLanguages.nameOf("ar"))
        assertEquals("日本語", AppLanguages.nameOf("ja"))
        assertTrue(AppLanguages.nameOf("pt-BR").startsWith("Português"))
        assertTrue(AppLanguages.nameOf("pt-BR").contains("Brasil"))
    }

    @Test
    fun `Chinese is named by its script, not its country`() {
        // "简体中文" on Android, "中文 (简体)" on the JVM: Chinese once, and the script.
        val name = AppLanguages.nameOf("zh-CN")
        assertTrue(name, name.contains("简体") && name.split("中文").size == 2)
        assertTrue(AppLanguages.nameOf("zh-TW").contains("繁體"))
    }

    @Test
    fun `every listed language has a name of its own`() {
        val tags = AppLanguages.tagsOf(listedLanguages())
        assertEquals(listedLanguages().size, tags.size)
        for (tag in tags) {
            val name = AppLanguages.nameOf(tag)
            // An unknown language would be shown as its bare code.
            assertNotEquals(tag, name)
            assertTrue("$tag: $name", name.length > 2)
        }
    }

    @Test
    fun `the list is in alphabetical order of the names, Latin letters first`() {
        val names = AppLanguages.tagsOf(listOf("pl", "en", "ru", "de", "nl")).map(AppLanguages::nameOf)
        assertEquals(listOf("Deutsch", "English", "Nederlands", "Polski", "Русский"), names)
    }

    @Test
    fun `the app's language is matched to the offered one`() {
        val tags = listOf("en", "nl", "pt-BR", "zh-CN")
        assertNull(AppLanguages.selectedOf(null, tags))
        assertEquals("nl", AppLanguages.selectedOf(Locale.forLanguageTag("nl"), tags))
        assertEquals("pt-BR", AppLanguages.selectedOf(Locale.forLanguageTag("pt-BR"), tags))
        // A regional variant picked in phone Settings.
        assertEquals("en", AppLanguages.selectedOf(Locale.forLanguageTag("en-GB"), tags))
        assertEquals("pt-BR", AppLanguages.selectedOf(Locale.forLanguageTag("pt-PT"), tags))
        assertNull(AppLanguages.selectedOf(Locale.forLanguageTag("sv"), tags))
    }

    /** `twangle.languages` from the root gradle.properties, the list the app passes to the setting. */
    private fun listedLanguages(): List<String> {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        return Properties().apply { File(root, "gradle.properties").reader().use { load(it) } }
            .getProperty("twangle.languages").split(",").map { it.trim() }
    }
}
