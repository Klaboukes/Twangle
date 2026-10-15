package io.github.klaboukes.twangle.feature.tuner.components

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.feature.tuner.MicPermissionStatus
import io.github.klaboukes.twangle.feature.tuner.R

/**
 * Explains why the tuner needs the microphone and offers the one action that can fix it: ask again,
 * or open Settings once Android won't show the dialog any more. Meant for every status except
 * [MicPermissionStatus.GRANTED].
 */
@Composable
fun PermissionPrompt(
    status: MicPermissionStatus,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val permanentlyDenied = status == MicPermissionStatus.PERMANENTLY_DENIED
    Column(
        modifier = modifier
            // A phone on its side with a large font is shorter than this prompt; then it scrolls.
            .verticalScroll(rememberScrollState())
            .widthIn(max = 420.dp)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_mic),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.mic_permission_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(
                when (status) {
                    MicPermissionStatus.DENIED -> R.string.mic_permission_rationale
                    MicPermissionStatus.PERMANENTLY_DENIED -> settingsHint()
                    else -> R.string.mic_permission_intro
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = if (permanentlyDenied) onOpenSettings else onRequest) {
            Text(
                stringResource(
                    if (permanentlyDenied) R.string.mic_permission_open_settings else R.string.mic_permission_allow,
                ),
            )
        }
    }
}

/**
 * The way to the microphone in Settings. From Android 11 its page offers "only while using the app"
 * among other choices; before, Microphone is a switch in the Permissions list.
 */
private fun settingsHint(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        R.string.mic_permission_settings_hint
    } else {
        R.string.mic_permission_settings_hint_switch
    }

@Preview(showBackground = true)
@Composable
private fun PermissionPromptDeniedPreview() {
    MaterialTheme { PermissionPrompt(MicPermissionStatus.DENIED, onRequest = {}, onOpenSettings = {}) }
}

@Preview(showBackground = true)
@Composable
private fun PermissionPromptSettingsPreview() {
    MaterialTheme { PermissionPrompt(MicPermissionStatus.PERMANENTLY_DENIED, onRequest = {}, onOpenSettings = {}) }
}
