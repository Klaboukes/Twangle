package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.designsystem.numberFontFor
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.feature.tuner.R

/**
 * The numbers for chromatic mode with the wave view: how far off the note is ("12 cents flat") and
 * its frequency ("81.8 Hz"), in the space the string row uses in guitar mode. The wave view leaves
 * them out under the note, but in chromatic mode there is no string to aim for, and experienced
 * players expect the numbers. The cents fade to the style's "in tune" colour while [readout] is in
 * tune. Without a [readout] both lines are empty.
 *
 * Hidden from TalkBack: the note's live region already says the cents ([NoteDisplay]).
 */
@Composable
fun ChromaticReadout(readout: NoteReadout?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val centsColor by animateInTuneColor(readout?.inTune == true, colors.onSurface, "chromaticCents")
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = if (readout != null) centsText(readout.cents) else "",
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = numberFontFor(MaterialTheme.styleTokens.style)),
            color = centsColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { hideFromAccessibility() },
        )
        Text(
            text = if (readout != null) stringResource(R.string.tuner_frequency, readout.frequencyHz) else "",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { hideFromAccessibility() },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChromaticReadoutPreview() {
    TwangleTheme(style = VisualStyle.CLASSIC, darkTheme = false) {
        ChromaticReadout(NoteReadout(noteName = "A", octave = 4, frequencyHz = 437.0f, cents = -11.9f))
    }
}
