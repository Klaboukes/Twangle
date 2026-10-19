package io.github.klaboukes.twangle.feature.practice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
 * says the same. Without it, the name alone, heard as [spokenTuningName].
 */
@Composable
internal fun TuningLine(tuningName: String, spokenTuningName: String, onPickInstrument: (() -> Unit)?) {
    // One press for the button and the name: a tap on either sinks and lights both.
    val press = remember { MutableInteractionSource() }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
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

/** Between the instrument button's touch target and the name. */
private val NAME_GAP = 6.dp
