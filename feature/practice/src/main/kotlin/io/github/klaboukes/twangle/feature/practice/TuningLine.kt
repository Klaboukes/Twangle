package io.github.klaboukes.twangle.feature.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.PickerChip
import io.github.klaboukes.twangle.core.designsystem.R as DesignR
import io.github.klaboukes.twangle.core.designsystem.StyleButton
import io.github.klaboukes.twangle.core.designsystem.TwangleTextButton
import io.github.klaboukes.twangle.core.designsystem.musicText

/**
 * The Practice tools' instrument and tuning ("Guitar · Standard", [tuningName], heard as [spokenTuningName]).
 * With [onPickInstrument] it is the picker chip (2.0, Phase 70; [PickerChip], as at the top of the tuner),
 * whose tap opens the same sheet as the tuner's, "What are you tuning?", over the tool; TalkBack hears
 * "Guitar, Standard, button, double-tap to change instrument or tuning", and a long name ends in "…" on
 * its one line. Without it, the name alone as a quiet line, heard as [spokenTuningName]. Centred, or at the [start].
 */
@Composable
internal fun TuningLine(
    tuningName: String,
    spokenTuningName: String,
    onPickInstrument: (() -> Unit)?,
    modifier: Modifier = Modifier,
    start: Boolean = false,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (start) Arrangement.Start else Arrangement.Center) {
        if (onPickInstrument != null) {
            PickerChip(
                label = tuningName,
                spoken = spokenTuningName,
                onClick = onPickInstrument,
                modifier = Modifier.weight(1f, fill = false).testTag(PracticeTestTags.TUNING),
            )
        } else {
            Text(
                musicText(tuningName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                // One line, ending in "…" where the name is longer than its room (Phase 65).
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .testTag(PracticeTestTags.TUNING)
                    .clearAndSetSemantics { contentDescription = spokenTuningName },
            )
        }
    }
}

/**
 * The top row of every Practice tool (2.0, Phase 69; until then the tool button, Phase 47): the back arrow
 * ([PracticeBackButton], [onBack], back to the Practice cards) in the top start corner, where the cards page
 * has the app's gear, then the instrument and tuning ([TuningLine]; the picker chip since Phase 70) in the
 * room left, its name on one line ending in "…" where it is longer. Only where the room left is too narrow
 * for a few letters ([MIN_NAME_EMS] wide, a 320 dp phone at a very large text size) does the chip go on a
 * line of its own under the arrow, at the start too. Public for the tools of other modules (Ear training,
 * `:feature:eartraining`), whose top row `:app` builds with it.
 */
@Composable
fun BackAndTuningRow(
    onBack: () -> Unit,
    tuningName: String,
    spokenTuningName: String,
    onPickInstrument: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.titleSmall
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // A few letters of the name at its size, however large the text is set, and the chip's caret (about an em).
        val em = with(LocalDensity.current) { style.fontSize.toDp() }
        val beforeName = BACK_TOUCH + ROW_GAP + if (onPickInstrument != null) CHIP_PADDING + em else 0.dp
        if (maxWidth - beforeName >= em * MIN_NAME_EMS) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PracticeBackButton(onBack)
                Spacer(Modifier.width(ROW_GAP))
                TuningLine(tuningName, spokenTuningName, onPickInstrument, Modifier.weight(1f), start = true)
            }
        } else {
            Column {
                PracticeBackButton(onBack)
                TuningLine(tuningName, spokenTuningName, onPickInstrument, start = true)
            }
        }
    }
}

/**
 * The back arrow in a Practice tool's top start corner (2.0, Phase 69): [onBack] leads back to the cards, as
 * Back does. Drawn as the app's gear in that corner on the cards page (the button's [BACK_FACE] round face, in
 * a 48 dp touch area), so the corner doesn't change look between the two; the arrow turns round in
 * right-to-left languages. TalkBack hears "Back", a button.
 */
@Composable
fun PracticeBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    StyleButton(
        onClick = onBack,
        label = stringResource(R.string.practice_back),
        modifier = modifier,
        face = BACK_FACE,
        faceModifier = Modifier.testTag(PracticeTestTags.BACK),
    ) {
        Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = null, modifier = Modifier.size(BACK_ICON))
    }
}

/** The back arrow's touch area, its face (the gear's 44 dp) and its icon. */
private val BACK_TOUCH = 48.dp
private val BACK_FACE = 44.dp
private val BACK_ICON = 24.dp

/** The name stays beside the back arrow where its room holds about this many letters' widths (in ems) or more. */
private const val MIN_NAME_EMS = 4f

/** Between the back arrow's touch area and the picker chip; the chip's own room round its name (its padding and the caret's gap). */
private val ROW_GAP = 8.dp
private val CHIP_PADDING = 34.dp

/**
 * The quiet line in the capo table's place for a tuning with a short string (the banjo's fifth,
 * [io.github.klaboukes.twangle.core.dsp.music.Tuning.shortStrings]), which Capo leaves out (Chords
 * and Scales draw it since Phase 59): [text], saying why, in the look of the chart's "No shape within reach in
 * this tuning", centred in its room, set smaller where the room is short. TalkBack reads it as it is. With
 * [onPickInstrument] (Phase 65) a text button under it ([pickLabel], "Pick another instrument") opens the
 * instrument and tuning panel, so the player sees where to go.
 */
@Composable
internal fun ShortStringLine(
    text: String,
    modifier: Modifier = Modifier,
    pickLabel: String? = null,
    onPickInstrument: (() -> Unit)? = null,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        val style = MaterialTheme.typography.bodyLarge
        Text(
            text,
            // The line height relative, so it shrinks with the text.
            style = style.copy(lineHeight = SHORT_STRING_LINE_HEIGHT),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            // Set smaller where its room is short (a phone on its side at 2x text), never cut off.
            autoSize = TextAutoSize.StepBased(minFontSize = SHORT_STRING_MIN_SIZE, maxFontSize = style.fontSize, stepSize = 0.5.sp),
            modifier = Modifier.weight(1f, fill = false).padding(horizontal = 16.dp).testTag(PracticeTestTags.SHORT_STRING),
        )
        if (onPickInstrument != null && pickLabel != null) {
            Spacer(Modifier.height(8.dp))
            val buttonStyle = MaterialTheme.typography.labelLarge
            TwangleTextButton(onClick = onPickInstrument, modifier = Modifier.testTag(PracticeTestTags.SHORT_STRING_PICK)) {
                Text(
                    pickLabel,
                    style = buttonStyle,
                    maxLines = 1,
                    // Never wrapped, so the auto-size sees a long label too wide (Ear training's AnswerButton in
                    // IntervalsPart.kt says why): set smaller rather than cut off.
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = SHORT_STRING_MIN_SIZE, maxFontSize = buttonStyle.fontSize, stepSize = 0.5.sp),
                )
            }
        }
    }
}

private val SHORT_STRING_MIN_SIZE = 10.sp
private val SHORT_STRING_LINE_HEIGHT = 1.4.em
