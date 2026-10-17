package io.github.klaboukes.twangle.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.feature.settings.components.AboutSection

/**
 * The About page, opened from the last row of settings: the app's [appVersion], the free-forever
 * promise, the privacy statement, "Rate Twangle" and the credits with their licences. [onRate] opens
 * Twangle's Play page ([openPlayPage]); [onBack] returns to settings.
 */
@Composable
fun AboutScreen(appVersion: String, onRate: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    SettingsPage(title = stringResource(R.string.about_title), onBack = onBack, modifier = modifier) {
        AboutSection(appVersion = appVersion, onRate = onRate, modifier = Modifier.padding(horizontal = 16.dp))
    }
}
