package io.github.klaboukes.twangle.feature.settings.components

import io.github.klaboukes.twangle.core.designsystem.bundledFontCredits

/**
 * Something Twangle is built with, credited on the About page: its [name], who made it
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
private const val BSD_3 = "BSD 3-Clause License"

/** Bundled with this module (assets/licenses); the font licences come from `:core:designsystem`. */
private const val APACHE_ASSET = "licenses/Apache-2.0.txt"
private const val PROTOBUF_ASSET = "licenses/BSD-3-Clause-protobuf.txt"

/**
 * The bundled fonts, then the open-source libraries and icons in the app. Besides AndroidX and Kotlin,
 * these ship inside it: kotlinx.serialization, JSpecify and Guava's ListenableFuture with AndroidX,
 * Okio and a copy of Protocol Buffers with DataStore (the settings store), and Google's Material
 * icons (copied into the app's drawables). A new library needs a credit here, and its licence text in
 * assets if it is not one of these.
 */
val credits: List<Credit> =
    bundledFontCredits.map { Credit(it.name, it.designer, OFL, it.licenceAsset) } + listOf(
        Credit("Jetpack Compose & AndroidX", "Google", APACHE, APACHE_ASSET),
        Credit("Kotlin & kotlinx.coroutines", "JetBrains", APACHE, APACHE_ASSET),
        Credit("kotlinx.serialization", "JetBrains", APACHE, APACHE_ASSET),
        Credit("Material Symbols", "Google", APACHE, APACHE_ASSET),
        Credit("Okio", "Square, Inc.", APACHE, APACHE_ASSET),
        Credit("Protocol Buffers", "Google Inc.", BSD_3, PROTOBUF_ASSET),
        Credit("JSpecify", "The JSpecify Authors", APACHE, APACHE_ASSET),
        Credit("Guava: ListenableFuture", "The Guava Authors", APACHE, APACHE_ASSET),
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
