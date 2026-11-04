package io.github.klaboukes.twangle.feature.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.BackButton
import io.github.klaboukes.twangle.core.designsystem.PickerChip
import io.github.klaboukes.twangle.core.designsystem.PickerLine
import io.github.klaboukes.twangle.core.designsystem.TopBar
import io.github.klaboukes.twangle.core.designsystem.TopBarTitle
import io.github.klaboukes.twangle.core.designsystem.TwangleTextButton
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.settings.PracticeTool

/**
 * The Practice tools' instrument and tuning ("Guitar · Standard", [tuningName], heard as [spokenTuningName]).
 * With [onPickInstrument] it is the picker chip (2.0, Phase 70; [PickerChip], as at the top of the tuner),
 * whose tap opens the same sheet as the tuner's, "What are you tuning?", over the tool; TalkBack hears
 * "Guitar, Standard, button, double-tap to change instrument or tuning", and a long name ends in "…" on
 * its one line. Without it, the name alone as a quiet line, heard as [spokenTuningName]. Centred.
 */
@Composable
internal fun TuningLine(
    tuningName: String,
    spokenTuningName: String,
    onPickInstrument: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
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
 * The top of every Practice tool (2.0, Phase 69; until then the tool button, Phase 47): every tab's [TopBar], the back
 * arrow ([BackButton], [onBack], back to the Practice cards) at its start, where the cards page has the app's gear, the
 * [tool]'s name in its middle ([TopBarTitle], "Chords", a heading, as the tabs name themselves there), and a tool's own
 * button at its [end] (Ear training's ⋯, Phase 77), where the tabs have theirs. Under the row, on its own centred line
 * ([PickerLine]), the instrument and tuning ([TuningLine]; the picker chip since Phase 70), in the same place as the
 * tuner's chip, its name on one line ending in "…" where it is longer. Until 2026-11-04 (the owner: "each screen should
 * just have the screen's name there") the chip stood in the row's middle, on a 320 dp phone at a very large text size
 * on a line of its own; beside the arrow before that. Without [onPickInstrument] the plain line stands in the chip's
 * place. Public for the tools of other modules (Ear training, `:feature:eartraining`), whose top `:app` builds with it.
 */
@Composable
fun BackAndTuningRow(
    tool: PracticeTool,
    onBack: () -> Unit,
    tuningName: String,
    spokenTuningName: String,
    onPickInstrument: (() -> Unit)?,
    modifier: Modifier = Modifier,
    end: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        TopBar(
            middle = { TopBarTitle(stringResource(tool.label), Modifier.testTag(PracticeTestTags.TITLE)) },
            modifier = Modifier.fillMaxWidth(),
            start = { BackButton(onBack, stringResource(R.string.practice_back), faceModifier = Modifier.testTag(PracticeTestTags.BACK)) },
            end = end,
        )
        PickerLine { TuningLine(tuningName, spokenTuningName, onPickInstrument) }
    }
}

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
