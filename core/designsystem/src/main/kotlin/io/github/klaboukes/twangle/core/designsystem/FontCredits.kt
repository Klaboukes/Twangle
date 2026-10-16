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

/** Every typeface the app bundles, all under the SIL Open Font License 1.1 (see [StyleFonts] and [MusicSignsFont]). */
val bundledFontCredits: List<FontCredit> = listOf(
    FontCredit("Forum", "Denis Masharov", "forum.ttf", "licenses/OFL-forum.txt"),
    FontCredit("Alegreya Sans", "Juan Pablo del Peral, Huerta Tipográfica", "alegreya_sans_medium.ttf", "licenses/OFL-alegreyasans.txt"),
    FontCredit("Playfair Display", "Claus Eggers Sørensen", "playfair_display.ttf", "licenses/OFL-playfairdisplay.txt"),
    FontCredit("Bodoni Moda", "Owen Earl, indestructible type*", "bodoni_moda.ttf", "licenses/OFL-bodonimoda.txt"),
    FontCredit("Righteous", "Astigmatic", "righteous.ttf", "licenses/OFL-righteous.txt"),
    FontCredit("Orbitron", "Matt McInerney", "orbitron.ttf", "licenses/OFL-orbitron.txt"),
    FontCredit("Michroma", "Vernon Adams", "michroma.ttf", "licenses/OFL-michroma.txt"),
    FontCredit("Noto Sans Symbols", "Google", "music_signs.ttf", "licenses/OFL-notosanssymbols.txt"),
)
