package io.github.klaboukes.twangle.feature.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.TwangleTextButton
import io.github.klaboukes.twangle.feature.settings.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The About rows: the app's name and [appVersion], the free-forever promise, the privacy statement,
 * a quiet "Rate Twangle" row ([onRate] opens the Play page; never a rating pop-up), and a credit for
 * every bundled font and open-source library. Tapping a credit shows its licence text, read from
 * the app's own assets (the app has no internet permission).
 */
@Composable
fun AboutSection(appVersion: String, onRate: () -> Unit, modifier: Modifier = Modifier) {
    // The open licence is remembered by credit name, so it stays open when the phone is turned.
    var shown by rememberSaveable { mutableStateOf<String?>(null) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AboutText(
            title = stringResource(R.string.settings_about_app_name),
            body = stringResource(R.string.settings_about_version, appVersion),
        )
        AboutText(
            title = stringResource(R.string.settings_about_free_title),
            body = stringResource(R.string.settings_about_free_summary),
        )
        AboutText(
            title = stringResource(R.string.settings_about_privacy_title),
            body = stringResource(R.string.settings_about_privacy_summary),
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

@Composable
private fun AboutText(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(text = body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One credit: name, then who made it and the licence. The whole row (at least 48 dp) opens the licence. */
@Composable
private fun CreditRow(credit: Credit, onClick: () -> Unit) {
    val byLine = listOf(credit.author, credit.licenceName).joinToString(" · ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = stringResource(R.string.settings_about_licence_open), role = Role.Button, onClick = onClick)
            .testTag(creditTag(credit.name)),
        verticalArrangement = Arrangement.spacedBy(2.dp, alignment = Alignment.CenterVertically),
    ) {
        Text(text = credit.name, style = MaterialTheme.typography.bodyLarge)
        Text(text = byLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** Test tag of the "Rate Twangle" row. */
const val RATE_TAG = "rateTwangle"

/** Test tag of the scrolling licence text in the licence dialog. */
const val LICENCE_TEXT_TAG = "licenceText"
