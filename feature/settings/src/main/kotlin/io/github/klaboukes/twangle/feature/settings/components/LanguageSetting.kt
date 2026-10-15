package io.github.klaboukes.twangle.feature.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.TwangleTextButton
import io.github.klaboukes.twangle.feature.settings.R

/**
 * The Language setting: a row with the app's language (in its own words, or "Phone's language")
 * and an arrow, like the other rows that open something, that opens a dialog listing "Phone's language" (null, follows the phone) and then [languages]
 * (language tags) named by [nameOf]. Picking one closes the dialog and calls [onSelect].
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
    // Not saved: picking a language recreates the screen, which then shows it closed.
    var open by remember { mutableStateOf(false) }
    val phoneLanguage = stringResource(R.string.settings_language_phone)
    val label = { tag: String? -> if (tag == null) AnnotatedString(phoneLanguage) else inOwnLanguage(nameOf(tag), tag) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button) { open = true }
            .testTag(LANGUAGE_SETTING_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = stringResource(R.string.settings_language_title), style = MaterialTheme.typography.titleMedium)
            Text(text = label(selected), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Like the rows under it, which also open something; the title already says what.
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TwangleTextButton(onClick = { open = false }) { Text(stringResource(R.string.settings_language_cancel)) }
            },
            title = { Text(stringResource(R.string.settings_language_title)) },
            text = {
                // The list is longer than most screens: it scrolls inside a dialog that leaves the
                // screen's edges free, opens at the picked language, and a thin line at its top or
                // bottom shows there are more languages that way.
                val scroll = rememberScrollState()
                val picked = remember { BringIntoViewRequester() }
                LaunchedEffect(Unit) {
                    withFrameNanos { } // Once the list has been laid out.
                    picked.bringIntoView()
                }
                val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
                Column {
                    HorizontalDivider(color = edgeColor(scroll.canScrollBackward))
                    Column(
                        Modifier
                            .heightIn(max = windowHeight * LIST_MAX_SHARE)
                            .verticalScroll(scroll)
                            .selectableGroup(),
                    ) {
                        (listOf(null) + languages).forEach { tag ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .then(if (tag == selected) Modifier.bringIntoViewRequester(picked) else Modifier)
                                    .selectable(
                                        selected = tag == selected,
                                        role = Role.RadioButton,
                                        onClick = {
                                            open = false
                                            if (tag != selected) onSelect(tag)
                                        },
                                    )
                                    .testTag(languageOptionTag(tag)),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                // The row handles the click, so the button itself doesn't.
                                RadioButton(selected = tag == selected, onClick = null)
                                Text(text = label(tag), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    HorizontalDivider(color = edgeColor(scroll.canScrollForward))
                }
            },
        )
    }
}

/** The line at an end of the language list: shown while the list scrolls further that way. */
@Composable
private fun edgeColor(more: Boolean): Color = if (more) MaterialTheme.colorScheme.outlineVariant else Color.Transparent

/** The language list takes at most this share of the window's height, so the dialog fits with room around it. */
private const val LIST_MAX_SHARE = 0.6f

/** [text] marked as written in the language [tag], for TalkBack's voice and the phone's choice of glyphs. */
private fun inOwnLanguage(text: String, tag: String): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(localeList = LocaleList(tag))) { append(text) }
}

/** Test tag of the Language row in Settings. */
const val LANGUAGE_SETTING_TAG = "settings_language"

/** Test tag of a language in the Language dialog: its tag, or "phone" for the phone's language. */
fun languageOptionTag(tag: String?): String = "language:${tag ?: "phone"}"
