package io.github.klaboukes.twangle.feature.settings.components

import io.github.klaboukes.twangle.core.designsystem.bundledFontCredits

/**
 * Something Twangle is built with, credited in the About section: its [name], who made it
 * ([author]), its licence's name and the licence text bundled in assets ([licenceAsset]). The app has
 * no internet permission, so every licence text ships inside the app. Names are proper names, the
 * same in every language.
 */
data class Credit(
    val name: String,
    val author: String,
    val licenceName: String,
    val licenceAsset: String,
)

private const val OFL = "SIL Open Font License 1.1"
private const val APACHE = "Apache License 2.0"

/** Bundled with this module (assets/licenses); the font licences come from `:core:designsystem`. */
private const val APACHE_ASSET = "licenses/Apache-2.0.txt"

/** The bundled fonts, then the open-source libraries in the app, all under the Apache License 2.0. */
val credits: List<Credit> =
    bundledFontCredits.map { Credit(it.name, it.designer, OFL, it.licenceAsset) } + listOf(
        Credit("Jetpack Compose & AndroidX", "Google", APACHE, APACHE_ASSET),
        Credit("Kotlin & kotlinx.coroutines", "JetBrains", APACHE, APACHE_ASSET),
    )

/**
 * Licence files are wrapped at about 80 columns, which looks ragged on a phone. This joins the lines
 * of each paragraph (paragraphs are separated by blank lines), so the text wraps to the screen.
 * Ruler lines such as "-----" are dropped, and a heading in capitals at the top of a paragraph
 * ("PREAMBLE", "DISCLAIMER") stays on a line of its own.
 */
fun reflowLicence(text: String): String =
    text.replace("\r", "")
        .split(Regex("""\n\s*\n"""))
        .flatMap { paragraph ->
            val lines = paragraph.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.isRuler() }
            val first = lines.firstOrNull()
            val rest = lines.drop(1)
            val heading = first != null && rest.isNotEmpty() && first.isCapitals() &&
                (first.split(' ').size <= 3 || !rest.first().isCapitals())
            if (heading) listOf(first, rest.joinToString(" ")) else listOf(lines.joinToString(" "))
        }
        .filter { it.isNotEmpty() }
        .joinToString("\n\n")

private fun String.isRuler(): Boolean = all { it in "-=_*" }

private fun String.isCapitals(): Boolean = any { it.isLetter() } && none { it.isLowerCase() }
