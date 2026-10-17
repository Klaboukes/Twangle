package io.github.klaboukes.twangle.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/**
 * Guards the Play Store texts in `fastlane/metadata/android/<Play locale>/` (the fastlane / Triple-T
 * layout, which F-Droid reads too): every language in `twangle.languages` has exactly one folder with a
 * title, a short and a full description, no folder is for an unlisted language, and each text keeps
 * within Google Play's limits, counted in Unicode code points as Play counts characters. The title
 * also keeps clear of the price and promotion words Google bans there (docs/store-listing.md). Each
 * folder also has the eight captions of the phone screenshots (`screenshot_captions.txt`, one a line,
 * in the order of the plan), which the store-screenshot test draws above each screen: short, and
 * without those words either, since Google bans them in screenshots too.
 */
class StoreListingTest {

    private val root: File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    private val listings = File(root, "fastlane/metadata/android")

    /** `twangle.languages` from gradle.properties, as Android resource qualifiers. */
    private val languages: List<String> = Properties()
        .apply { File(root, "gradle.properties").reader().use { load(it) } }
        .getProperty("twangle.languages").orEmpty()
        .split(",").map { it.trim() }.filter { it.isNotEmpty() }

    private val folders: List<String> =
        listings.listFiles().orEmpty().filter { it.isDirectory }.map { it.name }.sorted()

    /** Language → its store-listing folder; fails when a language has none, or more than one. */
    private val folderOf: Map<String, String> by lazy {
        languages.associateWith { language ->
            val tag = languageTag(language)
            val matches = folders.filter { it == tag || (!tag.contains('-') && it.startsWith("$tag-")) }
            assertEquals(
                "Expected one folder for $language (Play locale $tag or $tag-<region>) in " +
                    "${listings.relativeTo(root)}, found $matches",
                1,
                matches.size,
            )
            matches.single()
        }
    }

    @Test
    fun `every language has a title, short and full description`() {
        assertTrue("twangle.languages in gradle.properties is empty", languages.isNotEmpty())
        folderOf.forEach { (language, folder) ->
            FILES.keys.forEach { name ->
                assertTrue(
                    "Empty or missing $folder/$name (language $language)",
                    text(folder, name)?.isNotBlank() == true,
                )
            }
        }
    }

    @Test
    fun `no store listing for a language that is not listed`() {
        val extra = folders - folderOf.values.toSet()
        assertTrue(
            "Store listings for languages missing from twangle.languages (gradle.properties): $extra",
            extra.isEmpty(),
        )
    }

    @Test
    fun `every text keeps within Google Play's length limits`() {
        val tooLong = folderOf.values.flatMap { folder ->
            FILES.mapNotNull { (name, limit) ->
                val length = text(folder, name)?.let { it.codePointCount(0, it.length) } ?: 0
                if (length > limit) "$folder/$name: $length characters, limit $limit" else null
            }
        }
        assertTrue("Too long for Google Play:\n" + tooLong.joinToString("\n"), tooLong.isEmpty())
    }

    @Test
    fun `the title is Twangle plus what it is, without promotion words`() {
        folderOf.values.forEach { folder ->
            val title = text(folder, "title.txt").orEmpty()
            assertTrue("$folder/title.txt must start with Twangle: $title", title.startsWith("Twangle"))
            BANNED_IN_TITLE.forEach { banned ->
                assertTrue(
                    "$folder/title.txt has \"${banned.pattern}\", which Google bans in titles: $title",
                    !banned.containsMatchIn(title),
                )
            }
        }
    }

    @Test
    fun `the texts are plain text without emoji`() {
        folderOf.values.forEach { folder ->
            FILES.keys.forEach { name ->
                val emoji = text(folder, name).orEmpty().codePoints()
                    .filter { it in 0x1F000..0x1FAFF || it == 0xFE0F }.toArray()
                assertTrue("Emoji in $folder/$name", emoji.isEmpty())
            }
        }
    }

    @Test
    fun `every language has the eight screenshot captions, short and without promotion words`() {
        folderOf.forEach { (language, folder) ->
            val lines = text(folder, CAPTIONS).orEmpty().lines()
            assertEquals("$folder/$CAPTIONS (language $language) needs one caption a line, one for each screenshot", SCREENSHOTS, lines.size)
            lines.forEachIndexed { i, caption ->
                val where = "$folder/$CAPTIONS line ${i + 1}"
                assertTrue("$where is empty", caption.isNotBlank())
                val length = caption.codePointCount(0, caption.length)
                assertTrue("$where has $length characters, more than $CAPTION_LIMIT fit above the screen: $caption", length <= CAPTION_LIMIT)
                BANNED_IN_TITLE.forEach { banned ->
                    assertTrue("$where has \"${banned.pattern}\", which Google bans in screenshots: $caption", !banned.containsMatchIn(caption))
                }
                val emoji = caption.codePoints().filter { it in 0x1F000..0x1FAFF || it == 0xFE0F }.toArray()
                assertTrue("Emoji in $where", emoji.isEmpty())
            }
        }
    }

    /** The file's text as it is pasted into the Play Console: line endings as \n, no trailing newline. */
    private fun text(folder: String, name: String): String? =
        File(listings, "$folder/$name").takeIf { it.isFile }?.readText()?.replace("\r\n", "\n")?.trimEnd()

    private companion object {
        /** Google Play's limits, in characters. */
        val FILES = mapOf("title.txt" to 30, "short_description.txt" to 80, "full_description.txt" to 4000)

        /** The screenshots' captions, one a line (docs/store-listing.md, *Phone screenshots*). */
        const val CAPTIONS = "screenshot_captions.txt"
        const val SCREENSHOTS = 8

        /** At most this many characters, so a caption fits in three lines of large type above the screen. */
        const val CAPTION_LIMIT = 80

        val BANNED_IN_TITLE = listOf("""\bfree\b""", """\bno ads\b""", """\bbest\b""", """#1\b""")
            .map { Regex(it, RegexOption.IGNORE_CASE) }

        /**
         * An Android resource qualifier as a BCP 47 tag, the way Play names its locales: `pt-rBR` →
         * `pt-BR`, `b+sr+Latn` → `sr-Latn`, and Android's old codes `in`, `iw`, `ji` → `id`, `he`, `yi`.
         * A tag without a region matches Play's folder for that language with its region (`de` →
         * `de-DE`), since Play lists most languages by region and some (`id`, `ar`) without.
         */
        fun languageTag(qualifier: String): String {
            val parts = if (qualifier.startsWith("b+")) {
                qualifier.removePrefix("b+").split("+")
            } else {
                qualifier.split("-r")
            }
            val language = when (parts.first()) {
                "in" -> "id"
                "iw" -> "he"
                "ji" -> "yi"
                else -> parts.first()
            }
            return (listOf(language) + parts.drop(1)).joinToString("-")
        }
    }
}
