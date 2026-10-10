package com.intune.feature.tuner.components

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.intune.feature.tuner.R
import com.intune.feature.tuner.SpokenReadingThrottle
import com.intune.feature.tuner.spokenNoteName
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** What [NoteDisplay] shows for a detected note. */
data class NoteReadout(
    val noteName: String,
    val octave: Int,
    val frequencyHz: Float,
    val cents: Float,
    val inTune: Boolean = false,
)

/**
 * Big target note with its octave, then how far off it is ("12 cents flat") and the frequency.
 * While the readout is in tune the note and cents turn green and a check mark appears, so the state
 * doesn't rely on colour alone. Without a [readout] it shows a dash and [hint], e.g. "Play a string".
 *
 * For TalkBack the whole block is one polite live region that says e.g. "E 2, 12 cents flat" or
 * "E 2, in tune", throttled by [SpokenReadingThrottle] so it doesn't talk over itself.
 */
@Composable
fun NoteDisplay(readout: NoteReadout?, hint: String, modifier: Modifier = Modifier) {
    val inTune = readout?.inTune == true
    val noteColor by animateInTuneColor(inTune, MaterialTheme.colorScheme.onSurface, "noteColor")
    val checkShown by animateFloatAsState(if (inTune) 1f else 0f, label = "check")
    val spoken = rememberSpokenReading(
        text = if (readout != null) spokenReading(readout) else hint,
        key = readout?.let { "${it.noteName}${it.octave}/${it.inTune}" },
    )
    Column(
        // The texts below are hidden from TalkBack; this block speaks for them.
        modifier = modifier.semantics {
            contentDescription = spoken
            liveRegion = LiveRegionMode.Polite
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row {
            Text(
                text = readout?.noteName ?: stringResource(R.string.tuner_placeholder_note),
                // Already huge, so it doesn't grow further with the system font size; that would
                // push the rest of the tuner off the screen.
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = with(LocalDensity.current) { BIG_NOTE_SIZE.toSp() },
                    lineHeight = with(LocalDensity.current) { BIG_NOTE_LINE_HEIGHT.toSp() },
                ),
                fontWeight = FontWeight.Medium,
                color = noteColor,
                modifier = Modifier.alignByBaseline().hiddenFromTalkBack(),
            )
            if (readout != null) {
                Text(
                    text = readout.octave.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline().hiddenFromTalkBack(),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The check mark's slot is mirrored on the right, so the text stays centred.
            Icon(
                painter = painterResource(R.drawable.ic_check_circle),
                contentDescription = null,
                tint = noteColor,
                modifier = Modifier
                    .size(CHECK_SIZE)
                    .graphicsLayer {
                        alpha = checkShown
                        scaleX = 0.5f + 0.5f * checkShown
                        scaleY = 0.5f + 0.5f * checkShown
                    },
            )
            Text(
                text = if (readout != null) centsText(readout.cents) else hint,
                style = MaterialTheme.typography.titleLarge,
                color = noteColor,
                modifier = Modifier.hiddenFromTalkBack(),
            )
            Box(Modifier.size(CHECK_SIZE))
        }
        Text(
            text = if (readout != null) stringResource(R.string.tuner_frequency, readout.frequencyHz) else "",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.hiddenFromTalkBack(),
        )
    }
}

/** Left out for screen readers (the block's description says it), but still there for UI tests. */
private fun Modifier.hiddenFromTalkBack(): Modifier = semantics { hideFromAccessibility() }

private val BIG_NOTE_SIZE = 112.dp
private val BIG_NOTE_LINE_HEIGHT = 120.dp
private val CHECK_SIZE = 24.dp

/** "12 cents flat", "1 cent sharp" or "0 cents", rounded to whole cents. */
@Composable
private fun centsText(cents: Float): String {
    val rounded = cents.roundToInt()
    val amount = abs(rounded)
    return when {
        rounded < 0 -> pluralStringResource(R.plurals.tuner_cents_flat, amount, amount)
        rounded > 0 -> pluralStringResource(R.plurals.tuner_cents_sharp, amount, amount)
        else -> stringResource(R.string.tuner_cents_zero)
    }
}

/** "E 2, 12 cents flat", "C sharp 4, in tune": the readout as a screen reader says it. */
@Composable
private fun spokenReading(readout: NoteReadout): String {
    val sharp = stringResource(R.string.tuner_spoken_sharp)
    val flat = stringResource(R.string.tuner_spoken_flat)
    val note = spokenNoteName(readout.noteName, { sharp.format(it) }, { flat.format(it) })
    val state = if (readout.inTune) stringResource(R.string.tuner_in_tune) else centsText(readout.cents)
    return stringResource(R.string.tuner_spoken_reading, note, readout.octave, state)
}

/** [text], passed on to TalkBack no faster than [SpokenReadingThrottle] allows. */
@Composable
private fun rememberSpokenReading(text: String, key: Any?): String {
    val throttle = remember { SpokenReadingThrottle() }
    var spoken by remember { mutableStateOf("") }
    LaunchedEffect(text, key) {
        val wait = throttle.delayFor(text, key, SystemClock.uptimeMillis()) ?: return@LaunchedEffect
        if (wait > 0) delay(wait)
        throttle.publish(text, key, SystemClock.uptimeMillis())
        spoken = text
    }
    return spoken
}

@Preview(showBackground = true)
@Composable
private fun NoteDisplayPreview() {
    MaterialTheme {
        NoteDisplay(NoteReadout(noteName = "E", octave = 2, frequencyHz = 81.84f, cents = -3f, inTune = true), hint = "")
    }
}
