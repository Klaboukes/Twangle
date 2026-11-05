package io.github.klaboukes.twangle.feature.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.TwangleName
import io.github.klaboukes.twangle.core.designsystem.TwangleTextButton
import io.github.klaboukes.twangle.feature.settings.R
import io.github.klaboukes.twangle.feature.settings.headingStyle
import io.github.klaboukes.twangle.feature.settings.rowTitleStyle
import io.github.klaboukes.twangle.feature.settings.websiteLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The About rows: the app's name with its icon ([TwangleName]) over its [appVersion], where the name comes from
 * ("twang" is English, so most languages need it said), the free-forever promise, the privacy statement,
 * "Help and website" ([onOpenWebsite] gets the website's home page in the app's language, [websiteLink],
 * to open in the browser), a quiet "Rate Twangle" row ([onRate] opens the Play page; Google's own
 * rating window is asked for once from the tuner, Phase 50), and a credit for
 * every bundled font and open-source library. Tapping a credit shows its licence text, read from
 * the app's own assets (the app has no internet permission).
 */
@Composable
fun AboutSection(
    appVersion: String,
    onOpenWebsite: (link: String) -> Unit,
    onRate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The open licence is remembered by credit name, so it stays open when the phone is turned.
    var shown by rememberSaveable { mutableStateOf<String?>(null) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        NameAndVersion(appVersion)
        AboutText(
            title = stringResource(R.string.settings_about_name_title),
            body = stringResource(R.string.settings_about_name_meaning),
        )
        AboutText(
            title = stringResource(R.string.settings_about_free_title),
            body = stringResource(R.string.settings_about_free_summary),
        )
        AboutText(
            title = stringResource(R.string.settings_about_privacy_title),
            body = stringResource(R.string.settings_about_privacy_summary),
        )
        val language = LocalConfiguration.current.locales[0].language
        LinkSetting(
            title = stringResource(R.string.settings_about_website_title),
            summary = stringResource(R.string.settings_about_website_summary),
            onClick = { onOpenWebsite(websiteLink(language)) },
            modifier = Modifier.testTag(WEBSITE_TAG),
        )
        LinkSetting(
            title = stringResource(R.string.settings_about_rate_title),
            summary = stringResource(R.string.settings_about_rate_summary),
            onClick = onRate,
            modifier = Modifier.testTag(RATE_TAG),
        )
        HorizontalDivider()
        Column {
            AboutText(
                title = stringResource(R.string.settings_about_credits_title),
                body = stringResource(R.string.settings_about_credits_summary),
            )
            credits.forEach { credit -> CreditRow(credit, onClick = { shown = credit.name }) }
        }
    }
    credits.firstOrNull { it.name == shown }?.let { LicenceDialog(it, onDismiss = { shown = null }) }
}

/**
 * The page's head: Twangle's icon and name, as on the store's banner and the website (Phase 79), centred over
 * the version, the icon's string plucked as the page opens and again at a tap. TalkBack reads it as the page's
 * first heading, the name.
 */
@Composable
private fun NameAndVersion(appVersion: String) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val name = stringResource(R.string.settings_about_app_name)
        TwangleName(
            capHeight = NAME_CAP_HEIGHT,
            plucked = true,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .semantics {
                    contentDescription = name
                    heading()
                },
        )
        Text(
            text = stringResource(R.string.settings_about_version, appVersion),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The name's capitals: the word about 115 dp across and the icon 50 dp, a heading's size beside the rows under it. */
private val NAME_CAP_HEIGHT = 20.dp

@Composable
private fun AboutText(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = title, style = headingStyle(rowTitleStyle()))
        Text(text = body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * One credit: name, then who made it and the licence, and the › of the rows above ([LinkSetting]), as it opens
 * something too (until Phase 63 it had none). The whole row (at least 48 dp) opens the licence.
 */
@Composable
private fun CreditRow(credit: Credit, onClick: () -> Unit) {
    val byLine = listOf(credit.author, credit.licenceName).joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = stringResource(R.string.settings_about_licence_open), role = Role.Button, onClick = onClick)
            .testTag(creditTag(credit.name)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = credit.name, style = MaterialTheme.typography.bodyLarge)
            Text(text = byLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // The licence's name above says what opens.
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
    }
}

/**
 * The full licence of [credit], scrollable. The text is read from assets off the main thread and
 * reflowed to the screen's width (see [reflowLicence]).
 */
@Composable
private fun LicenceDialog(credit: Credit, onDismiss: () -> Unit) {
    val assets = LocalContext.current.assets
    val text by produceState<String?>(initialValue = null, credit.licenceAsset) {
        value = withContext(Dispatchers.IO) {
            reflowLicence(assets.open(credit.licenceAsset).bufferedReader().use { it.readText() })
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TwangleTextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_about_licence_close)) }
        },
        title = { Text(credit.name) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).testTag(LICENCE_TEXT_TAG),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = credit.licenceName, style = MaterialTheme.typography.titleSmall)
                text?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
            }
        },
    )
}

/** Test tag of a credit's row. */
fun creditTag(name: String): String = "credit:$name"

/** Test tag of the "Help and website" row. */
const val WEBSITE_TAG = "helpAndWebsite"

/** Test tag of the "Rate Twangle" row. */
const val RATE_TAG = "rateTwangle"

/** Test tag of the scrolling licence text in the licence dialog. */
const val LICENCE_TEXT_TAG = "licenceText"
