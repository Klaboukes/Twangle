package io.github.klaboukes.twangle.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.util.Properties

/**
 * Guards the Play Store texts in `fastlane/metadata/android/<Play locale>/` (the fastlane / Triple-T
 * layout, which F-Droid reads too): every language in `twangle.languages` has exactly one folder (or
 * the few [PLAY_FOLDERS] names) with a
 * title, a short and a full description, no folder is for an unlisted language, and each text keeps
 * within Google Play's limits, counted in Unicode code points as Play counts characters. The title
 * also keeps clear of the price and promotion words Google bans there (docs/store-listing.md). Each
 * folder also has the eight captions of the phone screenshots (`screenshot_captions.txt`, one a line,
 * in the order of the plan), which the store-screenshot test draws above each screen: short, and
 * without those words either, since Google bans them in screenshots too. Each release's notes
 * (`changelogs/<versionCode>.txt`) are there in every language, within Play's 500 characters. The icon and the feature
 * graphic, one pair for every language (`en-US/images/`, made by `scripts/store-graphics.sh`), have
 * Play's exact sizes and formats.
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

    /**
     * Language → its store-listing folders: one, or those [PLAY_FOLDERS] names for a language Play
     * lists by region only; fails when a language has none, or more than it should.
     */
    private val foldersOf: Map<String, List<String>> by lazy {
        languages.associateWith { language ->
            PLAY_FOLDERS[language]?.let { wanted ->
                val missing = wanted - folders.toSet()
                assertTrue("Expected the folders $wanted for $language in ${listings.relativeTo(root)}, missing $missing", missing.isEmpty())
                return@associateWith wanted
            }
            val tag = languageTag(language)
            val matches = folders.filter { it == tag || (!tag.contains('-') && it.startsWith("$tag-")) }
            assertEquals(
                "Expected one folder for $language (Play locale $tag or $tag-<region>) in " +
                    "${listings.relativeTo(root)}, found $matches",
                1,
                matches.size,
            )
            matches
        }
    }

    /** Each store-listing folder with its language: (language, folder). */
    private val folderOf: List<Pair<String, String>> by lazy {
        foldersOf.flatMap { (language, folders) -> folders.map { language to it } }
    }

    /** Every listed language's store-listing folders. */
    private val listedFolders: List<String> by lazy { folderOf.map { it.second } }

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
        val extra = folders - listedFolders.toSet()
        assertTrue(
            "Store listings for languages missing from twangle.languages (gradle.properties): $extra",
            extra.isEmpty(),
        )
    }

    @Test
    fun `every text keeps within Google Play's length limits`() {
        val tooLong = listedFolders.flatMap { folder ->
            FILES.mapNotNull { (name, limit) ->
                val length = text(folder, name)?.let { it.codePointCount(0, it.length) } ?: 0
                if (length > limit) "$folder/$name: $length characters, limit $limit" else null
            }
        }
        assertTrue("Too long for Google Play:\n" + tooLong.joinToString("\n"), tooLong.isEmpty())
    }

    @Test
    fun `the title is Twangle plus what it is, without promotion words`() {
        listedFolders.forEach { folder ->
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
        listedFolders.forEach { folder ->
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

    @Test
    fun `release notes are in every language and within Play's limit`() {
        val notes = listedFolders.associateWith { folder ->
            File(listings, "$folder/$CHANGELOGS").listFiles().orEmpty().filter { it.isFile }.map { it.name }.toSet()
        }
        notes.forEach { (folder, names) ->
            names.forEach { name ->
                val where = "$folder/$CHANGELOGS/$name"
                assertTrue("$where must be named after its versionCode, e.g. 10001.txt", RELEASE_NOTES_NAME.matches(name))
                val text = text(folder, "$CHANGELOGS/$name").orEmpty()
                assertTrue("$where is empty", text.isNotBlank())
                val length = text.codePointCount(0, text.length)
                assertTrue("$where has $length characters, Play takes at most $RELEASE_NOTES_LIMIT", length <= RELEASE_NOTES_LIMIT)
                val emoji = text.codePoints().filter { it in 0x1F000..0x1FAFF || it == 0xFE0F }.toArray()
                assertTrue("Emoji in $where", emoji.isEmpty())
            }
        }
        // A release's notes are written in every language at once (scripts/release.sh bundle needs them all).
        notes.values.flatten().toSet().forEach { name ->
            val missing = notes.filterValues { name !in it }.keys
            assertTrue("Release notes $name are missing in: $missing", missing.isEmpty())
        }
    }

    @Test
    fun `the icon and the feature graphic have Play's sizes and formats`() {
        PICTURES.forEach { picture ->
            val file = File(listings, "$PICTURE_FOLDER/${picture.name}")
            val where = file.relativeTo(root).invariantSeparatorsPath
            assertTrue("$where is missing: make it with scripts/store-graphics.sh", file.isFile)
            assertTrue("$where is ${file.length()} bytes, Play takes at most ${picture.maxBytes}", file.length() <= picture.maxBytes)
            val png = Png.read(file)
            assertEquals("$where must be ${picture.width} × ${picture.height} pixels", picture.width to picture.height, png.width to png.height)
            assertEquals("$where must have 8 bits a channel", 8, png.bitDepth)
            assertEquals("$where must be ${picture.format}", picture.colorType, png.colorType)
        }
    }

    /** The size and pixel format from a PNG file's header (its IHDR chunk). */
    private data class Png(val width: Int, val height: Int, val bitDepth: Int, val colorType: Int) {
        companion object {
            private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

            fun read(file: File): Png {
                val head = file.inputStream().use { it.readNBytes(IHDR_END) }
                val isPng = head.size == IHDR_END && head.copyOf(SIGNATURE.size).contentEquals(SIGNATURE) &&
                    String(head, 12, 4, Charsets.US_ASCII) == "IHDR"
                assertTrue("${file.name} is not a PNG", isPng)
                val buffer = ByteBuffer.wrap(head)
                return Png(width = buffer.getInt(16), height = buffer.getInt(20), bitDepth = head[24].toInt(), colorType = head[25].toInt())
            }

            /** The signature, the IHDR chunk's length and type, then width, height, bit depth and colour type. */
            private const val IHDR_END = 26
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

        /** Each release's "What's new", one file a language named after its versionCode (docs/release.md). */
        const val CHANGELOGS = "changelogs"
        val RELEASE_NOTES_NAME = Regex("""[1-9][0-9]*\.txt""")

        /** Google Play's limit for release notes, per language. */
        const val RELEASE_NOTES_LIMIT = 500

        /** One icon and feature graphic for every language, in the default listing's folder. */
        const val PICTURE_FOLDER = "en-US/images"

        /** A PNG's colour types: red, green and blue (24-bit), and with alpha (32-bit). */
        const val RGB = 2
        const val RGBA = 6

        class Picture(val name: String, val width: Int, val height: Int, val colorType: Int, val format: String, val maxBytes: Long)

        /** Play's rules: the icon a 32-bit PNG up to 1 MB, the feature graphic a 24-bit PNG (no alpha) up to 15 MB. */
        val PICTURES = listOf(
            Picture("icon.png", 512, 512, RGBA, "a 32-bit PNG (with alpha)", 1L shl 20),
            Picture("featureGraphic.png", 1024, 500, RGB, "a 24-bit PNG (without alpha)", 15L shl 20),
        )

        /**
         * The languages whose Play folders don't follow from their code: Play has no Traditional
         * Chinese, only Taiwan's and Hong Kong's, so the one `b+zh+Hant` gets both, and Play's
         * Norwegian is `no-NO`, which Android's Bokmål `nb` fills (docs/store-listing.md, *Folders*).
         */
        val PLAY_FOLDERS = mapOf("b+zh+Hant" to listOf("zh-TW", "zh-HK"), "nb" to listOf("no-NO"))

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
