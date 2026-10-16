package io.github.klaboukes.twangle.feature.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The Practice tab's Chords tool (Phase 32). For now a placeholder: the tool switch ([header]) where
 * the metronome has it, at the top (on its side at the top of the start half, as the metronome's),
 * and a quiet line that chord charts and scales come here. The chord charts and scales fill it next.
 */
@Composable
fun ChordsScreen(header: @Composable () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).testTag(PracticeTestTags.CHORDS)) {
        val wide = maxWidth >= WIDE_MIN_WIDTH && maxWidth > maxHeight
        val placeholder = @Composable { modifier: Modifier ->
            Box(modifier.padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.practice_chords_coming),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.Center) {
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(HEADER_TOP))
                    header()
                }
                placeholder(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(HEADER_TOP))
                header()
                placeholder(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

/** On its side from this width, as the metronome's layout. */
private val WIDE_MIN_WIDTH = 600.dp

/** The tool switch stands this far down from the top, as the metronome's and the tuner's mode switch. */
private val HEADER_TOP = 8.dp
