package io.github.klaboukes.twangle.app

import org.junit.Assert.assertTrue
import org.w3c.dom.Element
import java.io.File
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The translation check behind [LocalizationTest], on any repo tree (so [TranslationCheckTest] can
 * run it on a made-up one). Every translation must have the placeholders of its English text, and no
 * language may hold a text English lacks. The day languages ([dayLanguages], English and Dutch) must be
 * complete; the others are translated at night and may lack a text or carry a stale one, unless
 * [strict]. Strict (the release gate) wants every language complete and matching the record in
 * `translations/<language>.txt`: the hash of the English each translation was made from, written by
 * `scripts/translations.py mark`, which hashes the same way as [englishHash].
 */
class TranslationCheck(
    private val root: File,
    private val modules: List<File>,
    private val languages: List<String>,
    private val dayLanguages: List<String>,
    private val strict: Boolean,
) {
    /** What is wrong, one line each; empty when all is well. */
    fun problems(): List<String> {
        val problems = mutableListOf<String>()
        val stale = mutableMapOf<String, MutableList<String>>()
        var checked = 0
        val english = modules.associateWith { translatableStrings(File(it, "src/main/res/values/strings.xml")) }
            .filterValues { it.isNotEmpty() }
        for (language in languages.drop(1)) {
            val record = if (strict) readRecord(language) else null
            if (strict && record == null) {
                problems += "No translation record for $language (translations/$language.txt): run /translate"
            }
            for ((module, texts) in english) {
                val where = "values-$language/strings.xml in ${module.name}"
                val translated = translatableStrings(File(module, "src/main/res/values-$language/strings.xml"))
                val extra = translated.keys - texts.keys
                if (extra.isNotEmpty()) problems += "Strings in $where that English doesn't have: ${extra.sorted()}"
                val missing = texts.keys - translated.keys
                if (missing.isNotEmpty() && (strict || language in dayLanguages)) {
                    problems += "Strings missing in $where: ${missing.sorted()}"
                }
                texts.forEach { (name, text) ->
                    val other = translated[name] ?: return@forEach
                    if (placeholders(text.main) != placeholders(other.main)) {
                        problems += "Placeholders of $name in $where differ from English"
                    }
                    other.quantities.forEach { (quantity, item) ->
                        if (quantity !in QUANTITIES) problems += "Plural quantity \"$quantity\" of $name in $where"
                        if (!placeholders(text.main).containsAll(placeholders(item))) {
                            problems += "Placeholders of $name ($quantity) in $where must be among those of \"other\""
                        }
                    }
                    if (record != null && record[module.key to name] != englishHash(text)) {
                        stale.getOrPut(language) { mutableListOf() } += "${module.key}:$name"
                    }
                    checked++
                }
            }
            if (record != null) {
                val gone = record.keys.filter { (module, name) -> english.none { it.key.key == module && name in it.value } }
                if (gone.isNotEmpty()) {
                    problems += "translations/$language.txt lists texts English no longer has: " +
                        gone.joinToString { "${it.first}:${it.second}" }
                }
            }
        }
        stale.forEach { (language, names) ->
            problems += "Stale in $language (English reworded since, or not in translations/$language.txt): " +
                names.joinToString()
        }
        if (checked == 0 && languages.size > 1) problems += "No translations found; is the repo root right?"
        if (problems.isNotEmpty()) {
            problems += if (strict) {
                "Run /translate (scripts/translations.py status lists what it would do)"
            } else {
                "English and ${dayLanguages.drop(1).joinToString()} are written by day; the other languages at night"
            }
        }
        return problems
    }

    private val File.key: String get() = relativeTo(root).invariantSeparatorsPath

    /** `translations/<language>.txt`: (module, name) → hash, or null when there is no record. */
    private fun readRecord(language: String): Map<Pair<String, String>, String>? {
        val file = File(root, "translations/$language.txt")
        if (!file.isFile) return null
        return file.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.associate { line ->
            val (module, name, hash) = line.trim().split(Regex("""\s+"""))
            (module to name) to hash
        }
    }

    /**
     * One translatable text: a `<string>`'s text, or a `<plurals>`' "other" item as [main] with every
     * item by quantity in [quantities] (in the file's order).
     */
    class Text(val main: String, val quantities: Map<String, String> = emptyMap(), val isPlural: Boolean = false)

    companion object {
        /** The plural quantities Android knows. */
        val QUANTITIES = setOf("zero", "one", "two", "few", "many", "other")

        val PLACEHOLDER = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[a-zA-Z]""")

        private val WHITESPACE = Regex("""[ \t\r\n]+""")

        fun placeholders(text: String): List<String> = PLACEHOLDER.findAll(text).map { it.value }.sorted().toList()

        /**
         * The first 8 hex digits of SHA-256 over the text with its whitespace runs made one space (a
         * plural: one `quantity=text` line per item). scripts/translations.py hashes the same way.
         */
        fun englishHash(text: Text): String {
            fun norm(s: String) = s.replace(WHITESPACE, " ").trim(' ', '\t', '\r', '\n')
            val plain = if (text.isPlural) {
                text.quantities.entries.joinToString("\n") { (quantity, item) -> "$quantity=${norm(item)}" }
            } else {
                norm(text.main)
            }
            return MessageDigest.getInstance("SHA-256").digest(plain.toByteArray()).take(4)
                .joinToString("") { "%02x".format(it) }
        }

        /** Name → text of each translatable `<string>` and `<plurals>`. */
        fun translatableStrings(file: File): Map<String, Text> {
            if (!file.isFile) return emptyMap()
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val result = mutableMapOf<String, Text>()
            doc.documentElement.childElements().filter { it.getAttribute("translatable") != "false" }.forEach { e ->
                val name = e.getAttribute("name")
                when (e.tagName) {
                    "string" -> result[name] = Text(e.textContent)
                    "plurals" -> {
                        val items = e.childElements().filter { it.tagName == "item" }
                            .associate { it.getAttribute("quantity") to it.textContent }
                        val other = items["other"]
                        assertTrue("Plurals $name in $file has no \"other\" item", other != null)
                        result[name] = Text(other!!, items, isPlural = true)
                    }
                }
            }
            return result
        }

        private fun Element.childElements(): List<Element> =
            (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>()
    }
}
