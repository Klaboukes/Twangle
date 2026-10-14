package io.github.klaboukes.twangle.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Note
import kotlin.math.abs

/**
 * The watch app: the microphone permission first, then the tuner. [tuner] is only asked for (and
 * so the microphone only opened) once the permission is granted.
 */
@Composable
fun WatchTunerApp(tuner: @Composable () -> WatchTunerViewModel) {
    MaterialTheme {
        AppScaffold {
            ScreenScaffold {
                val permission = rememberWatchMicPermission()
                if (permission.granted) {
                    val viewModel = tuner()
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    WatchTuner(state, onRetry = viewModel::retry)
                } else {
                    Message(
                        text = stringResource(R.string.wear_mic_needed),
                        action = stringResource(if (permission.needsSettings) R.string.wear_open_settings else R.string.wear_allow_mic),
                        onAction = permission.onAction,
                    )
                }
            }
        }
    }
}

/** The tuner: the note and how many cents off it is, or what's in the way. Stateless. */
@Composable
fun WatchTuner(state: WatchTunerState, onRetry: () -> Unit) {
    when (state) {
        WatchTunerState.Listening -> Message(text = stringResource(R.string.wear_play_note))
        is WatchTunerState.Detected -> Reading(state.note, state.cents)
        is WatchTunerState.Failed -> Message(
            text = stringResource(
                when (state.error) {
                    WatchTunerError.PERMISSION_MISSING -> R.string.wear_mic_needed
                    WatchTunerError.MICROPHONE_BUSY -> R.string.wear_mic_busy
                    WatchTunerError.MICROPHONE_UNAVAILABLE -> R.string.wear_mic_unavailable
                },
            ),
            action = stringResource(R.string.wear_try_again),
            onAction = onRetry,
        )
    }
}

@Composable
private fun Reading(note: Note, cents: Int) {
    Column(
        modifier = Modifier.fillMaxSize().semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(WatchNoteName.letter(note), style = MaterialTheme.typography.displayLarge)
            Text(
                WatchNoteName.octave(note),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Text(
            pluralStringResource(R.plurals.wear_cents, abs(cents), WatchNoteName.signedCents(cents)),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A short text centred on the round screen, with an optional button under it. */
@Composable
private fun Message(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Box(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            if (action != null) {
                Button(onClick = onAction) { Text(action) }
            }
        }
    }
}

/** How the watch writes a note: letter names with sharps (A♯), the octave apart, cents with a sign. */
object WatchNoteName {
    fun letter(note: Note): String = Notation.SHARPS.nameOf(note.pitchClass)

    fun octave(note: Note): String = note.octave.toString()

    /** "+12", "−3" (a real minus sign), or "0". */
    fun signedCents(cents: Int): String = when {
        cents > 0 -> "+$cents"
        cents < 0 -> "−${-cents}"
        else -> "0"
    }
}
