package io.github.klaboukes.twangle.core.designsystem

/**
 * One bundled typeface, credited on the About page: its [name], its [designer], the
 * font file in res/font ([fontFile]) and its licence text in assets ([licenceAsset], read with
 * `AssetManager.open`). The names are proper names, the same in every language.
 */
data class FontCredit(
    val name: String,
    val designer: String,
    val fontFile: String,
    val licenceAsset: String,
)

/** Every typeface the app bundles, all under the SIL Open Font License 1.1 (see [MusicSignsFont]; the text itself is the phone's own font). */
val bundledFontCredits: List<FontCredit> = listOf(
    FontCredit("Noto Sans Symbols", "Google", "music_signs.ttf", "licenses/OFL-notosanssymbols.txt"),
)
