package io.github.klaboukes.twangle.feature.practice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.InstrumentButton
import io.github.klaboukes.twangle.core.designsystem.PressIndication
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.designsystem.pressFlash
import io.github.klaboukes.twangle.core.designsystem.pressSink

/**
 * The Practice tools' line naming the instrument and tuning ("Guitar · Standard", [tuningName], heard
 * as [spokenTuningName]), centred. With [onPickInstrument] the instrument button (a headstock, as above
 * the tuner's strings) stands before it, and a tap on either opens the instrument and tuning panel
 * over the tool (a press on either lights both, as one control); TalkBack hears the button ("Instrument, Guitar, Standard") and skips the name, which
 * says the same. Without it, the name alone, heard as [spokenTuningName]. Centred, or at the [start].
 */
@Composable
internal fun TuningLine(
    tuningName: String,
    spokenTuningName: String,
    onPickInstrument: (() -> Unit)?,
    modifier: Modifier = Modifier,
    start: Boolean = false,
) {
    // One press for the button and the name: a tap on either sinks and lights both.
    val press = remember { MutableInteractionSource() }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (start) Arrangement.Start else Arrangement.Center) {
        if (onPickInstrument != null) {
            InstrumentButton(
                interactionSource = press,
                current = spokenTuningName,
                onClick = onPickInstrument,
                faceModifier = Modifier.testTag(PracticeTestTags.INSTRUMENT_BUTTON),
            )
            Spacer(Modifier.width(NAME_GAP))
        }
        val shape = MaterialTheme.shapes.small
        Text(
            musicText(tuningName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = if (onPickInstrument != null) TextAlign.Start else TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .then(
                    if (onPickInstrument != null) {
                        Modifier
                            .pressSink(press)
                            .clip(shape)
                            .clickable(press, PressIndication(shape)) {
                                press.pressFlash()
                                onPickInstrument()
                            }
                            // Clear of the shape's corners, which cut into the letters in the styles with cut corners (Futuristic).
                            .padding(horizontal = NAME_PADDING)
                            .testTag(PracticeTestTags.TUNING)
                            // The instrument button before it says the same to TalkBack.
                            .clearAndSetSemantics {
                                hideFromAccessibility()
                                onClick {
                                    onPickInstrument()
                                    true
                                }
                            }
                    } else {
                        Modifier.testTag(PracticeTestTags.TUNING).clearAndSetSemantics { contentDescription = spokenTuningName }
                    },
                ),
        )
    }
}

/**
 * The top row of the Chords and Capo tools (Phase 47, the owner's pick A in
 * docs/design/tool-button-preview.html): the tool button ([toolButton], [TOOL_BUTTON_WIDTH] wide)
 * at the start, then the instrument and tuning line ([TuningLine]) in the room left, its name on up
 * to two lines. Where even two lines can't hold the name without breaking a word (a 320 dp phone at
 * 2× text), the tuning line goes on a line of its own under the button, at the start too.
 */
@Composable
internal fun ToolAndTuningRow(
    toolButton: @Composable () -> Unit,
    tuningName: String,
    spokenTuningName: String,
    onPickInstrument: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.bodyMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val beforeName = TOOL_BUTTON_WIDTH + ROW_GAP + if (onPickInstrument != null) INSTRUMENT_TOUCH + NAME_GAP + NAME_PADDING * 2 else 0.dp
        val room = with(density) { (maxWidth - beforeName).roundToPx() }
        val fits = remember(tuningName, style, room, density, measurer) {
            room > 0 && measurer.measure(
                musicText(tuningName),
                style,
                maxLines = 2,
                constraints = Constraints(maxWidth = room),
                density = density,
            ).let { !it.hasVisualOverflow && it.breaksBetweenWords(tuningName) }
        }
        if (fits) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                toolButton()
                Spacer(Modifier.width(ROW_GAP))
                TuningLine(tuningName, spokenTuningName, onPickInstrument, Modifier.weight(1f), start = true)
            }
        } else {
            Column {
                toolButton()
                TuningLine(tuningName, spokenTuningName, onPickInstrument, start = true)
            }
        }
    }
}

/** Whether every line but the last of this layout of [text] ends between two words (or where Chinese and Japanese may break). */
internal fun TextLayoutResult.breaksBetweenWords(text: String): Boolean =
    (0 until lineCount - 1).all { line ->
        val end = getLineEnd(line)
        end <= 0 || end >= text.length || text[end - 1].isWhitespace() || text[end - 1] == '-' || text[end - 1] == ZERO_WIDTH_SPACE ||
            text[end].isWhitespace() || (text[end - 1].breaksAnywhere() && text[end].breaksAnywhere())
    }

private fun Char.breaksAnywhere(): Boolean = when (Character.UnicodeScript.of(code)) {
    Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> true
    else -> this == 'ー'
}

/** Between the instrument button's touch target and the name's pressable box; the name this far in from that box's ends. */
private val NAME_GAP = 2.dp
private val NAME_PADDING = 4.dp

/** Between the tool button and the instrument button's touch target; the instrument button's touch target. */
private val ROW_GAP = 8.dp
private val INSTRUMENT_TOUCH = 48.dp

/**
 * The quiet line in the chart's, the neck's or the capo table's place for a tuning with a short string
 * (the banjo's fifth, [io.github.klaboukes.twangle.core.dsp.music.Tuning.shortStrings]), which Chords,
 * Scales and Capo don't draw yet: [text], in the look of the chart's "No shape within reach in this
 * tuning", centred in its room, set smaller where the room is short. TalkBack reads it as it is.
 */
@Composable
internal fun ShortStringLine(text: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        val style = MaterialTheme.typography.bodyLarge
        Text(
            text,
            // The line height relative, so it shrinks with the text.
            style = style.copy(lineHeight = SHORT_STRING_LINE_HEIGHT),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            // Set smaller where its room is short (a phone on its side at 2x text), never cut off.
            autoSize = TextAutoSize.StepBased(minFontSize = SHORT_STRING_MIN_SIZE, maxFontSize = style.fontSize, stepSize = 0.5.sp),
            modifier = Modifier.padding(horizontal = 16.dp).testTag(PracticeTestTags.SHORT_STRING),
        )
    }
}

private val SHORT_STRING_MIN_SIZE = 10.sp
private val SHORT_STRING_LINE_HEIGHT = 1.4.em
