package com.intune.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.intune.core.settings.AppSettings
import com.intune.core.settings.SettingsRepository
import com.intune.feature.settings.components.A4Calibration

/** Settings entry point. Every change is saved straight away; [onBack] returns to the tuner. */
@Composable
fun SettingsScreen(settingsRepository: SettingsRepository, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(settingsRepository) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsContent(settings = settings, onBack = onBack, onA4Change = viewModel::setA4, modifier = modifier)
}

/** The settings screen body. Stateless, so previews and UI tests can drive it. Empty while [settings] load. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    settings: AppSettings?,
    onBack: () -> Unit,
    onA4Change: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (settings != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                A4Calibration(a4Hz = settings.a4Hz, onA4Change = onA4Change)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun SettingsContentPreview() {
    MaterialTheme { SettingsContent(AppSettings(a4Hz = 442f), onBack = {}, onA4Change = {}) }
}
