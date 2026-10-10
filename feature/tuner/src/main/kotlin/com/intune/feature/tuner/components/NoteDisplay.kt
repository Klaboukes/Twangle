package com.intune.feature.tuner.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intune.feature.tuner.R
import kotlin.math.abs
import kotlin.math.roundToInt

/** What [NoteDisplay] shows for a detected note. */
data class NoteReadout(
    val noteName: String,
    val octave: Int,
    val frequencyHz: Float,
    val cents: Float,
)

/**
 * Big target note with its octave, then how far off it is ("12 cents flat") and the frequency.
 * Without a [readout] it shows a dash and [hint], e.g. "Play a string".
 */
@Composable
fun NoteDisplay(readout: NoteReadout?, hint: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row {
            Text(
                text = readout?.noteName ?: stringResource(R.string.tuner_placeholder_note),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 112.sp, lineHeight = 120.sp),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.alignByBaseline(),
            )
            if (readout != null) {
                Text(
                    text = readout.octave.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        Text(
            text = if (readout != null) centsText(readout.cents) else hint,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = if (readout != null) stringResource(R.string.tuner_frequency, readout.frequencyHz) else "",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

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

@Preview(showBackground = true)
@Composable
private fun NoteDisplayPreview() {
    MaterialTheme {
        NoteDisplay(NoteReadout(noteName = "E", octave = 2, frequencyHz = 81.84f, cents = -12f), hint = "")
    }
}
