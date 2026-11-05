package io.github.klaboukes.twangle.feature.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.withStyle
import io.github.klaboukes.twangle.feature.settings.R

/**
 * The Language setting: a row with the app's language (in its own words, or "Phone's language")
 * that opens the list of "Phone's language" (null, follows the phone) and then [languages]
 * (language tags) named by [nameOf] ([ChoiceDialogSetting]). Picking one closes the list and calls [onSelect].
 *
 * Each name carries its language, so TalkBack reads "Polski" in Polish and the phone draws Chinese
 * and Japanese with their own forms of the shared characters.
 */
@Composable
fun LanguageSetting(
    languages: List<String>,
    selected: String?,
    nameOf: (String) -> String,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Picking a language recreates the screen, which then shows the list closed.
    val phoneLanguage = stringResource(R.string.settings_language_phone)
    ChoiceDialogSetting(
        title = stringResource(R.string.settings_language_title),
        options = listOf(null) + languages,
        selected = selected,
        label = { tag -> if (tag == null) AnnotatedString(phoneLanguage) else inOwnLanguage(nameOf(tag), tag) },
        onSelect = onSelect,
        rowTag = LANGUAGE_SETTING_TAG,
        optionTag = ::languageOptionTag,
        modifier = modifier,
    )
}

/** [text] marked as written in the language [tag], for TalkBack's voice and the phone's choice of glyphs. */
private fun inOwnLanguage(text: String, tag: String): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(localeList = LocaleList(tag))) { append(text) }
}

/** Test tag of the Language row in Settings. */
const val LANGUAGE_SETTING_TAG = "settings_language"

/** Test tag of a language in the Language dialog: its tag, or "phone" for the phone's language. */
fun languageOptionTag(tag: String?): String = "language:${tag ?: "phone"}"
