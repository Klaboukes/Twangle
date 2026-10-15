package io.github.klaboukes.twangle.feature.settings

import java.text.Collator
import java.util.Locale

/**
 * The languages of the Language setting. They come from `twangle.languages` (gradle.properties) as
 * Android resource qualifiers, the same list as the apps' `localeFilters`, so the setting offers
 * exactly the languages Android's per-app language list (Android 13+) offers.
 */
internal object AppLanguages {

    /** The BCP 47 tag of a resource qualifier: "de" → "de", "pt-rBR" → "pt-BR", "b+sr+Latn" → "sr-Latn". */
    fun tagOf(qualifier: String): String =
        if (qualifier.startsWith("b+")) qualifier.removePrefix("b+").replace('+', '-') else qualifier.replace("-r", "-")

    /** The tags of [qualifiers], ordered by their names ([nameOf]) as a reader would look them up. */
    fun tagsOf(qualifiers: List<String>): List<String> {
        val collator = Collator.getInstance(Locale.ROOT)
        return qualifiers.map(::tagOf).distinct().sortedWith(compareBy(collator) { nameOf(it) })
    }

    /**
     * [tag]'s language in its own words, starting with a capital: "Deutsch", "Polski", "Português
     * (Brasil)", "العربية". Chinese is named by its script rather than its country: "简体中文" on
     * Android, whose script name already says Chinese, "中文 (简体)" where it doesn't.
     */
    fun nameOf(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        val name = if (locale.language == "zh") {
            val script = locale.script.ifEmpty { if (locale.country in setOf("TW", "HK", "MO")) "Hant" else "Hans" }
            val scriptName = Locale.Builder().setScript(script).build().getDisplayScript(locale)
            val language = locale.getDisplayLanguage(locale)
            if (language in scriptName) scriptName else "$language ($scriptName)"
        } else {
            locale.getDisplayName(locale)
        }
        return name.replaceFirstChar { it.titlecase(locale) }
    }

    /**
     * Which of [tags] the app's language [current] is: the same tag, else the same language (a
     * regional variant picked in phone Settings, "en-GB" for "en"). Null for the phone's language
     * ([current] null) or a language that isn't offered.
     */
    fun selectedOf(current: Locale?, tags: List<String>): String? {
        if (current == null) return null
        val currentTag = current.toLanguageTag()
        return tags.firstOrNull { it.equals(currentTag, ignoreCase = true) }
            ?: tags.firstOrNull { Locale.forLanguageTag(it).language == current.language }
    }
}
