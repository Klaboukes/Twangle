package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The small parts every panel shares (Phase 77, docs/architecture/ui.md *Shared controls*): the floating panels
 * ([FloatingPanel]), Ear training's "What is asked" among them. Until Phase 77 the metronome, the drone, Chords
 * and Ear training each drew their own, a little apart in size, colour and room (Ear training's headings in the
 * accent, the switch rows 48, 52 and 56 dp tall).
 */

/**
 * A section's name in a panel ("Between the beats", "Root", "Sound", "Intervals"): one small line in the muted
 * colour, set smaller where a language needs it, [PANEL_LABEL_INSET] in from the panel's parts. TalkBack skips it:
 * the group under it says the name instead (its `contentDescription`, as [PickGrid] does).
 */
@Composable
fun PanelLabel(text: String, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.labelLarge
    // In dp, so it holds at any font size.
    val smallest = with(LocalDensity.current) { MIN_TEXT_SIZE.toSp() }
    Text(
        text,
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        // Never wrapped, so the auto-size sees a name too wide (Ear training's AnswerButton in IntervalsPart.kt says why).
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = smallest, maxFontSize = style.fontSize, stepSize = 0.5.sp),
        modifier = modifier
            .padding(start = PANEL_LABEL_INSET, end = PANEL_LABEL_INSET, top = 4.dp, bottom = 2.dp)
            .semantics { hideFromAccessibility() },
    )
}

/**
 * A choice that is on or off in a panel ("Vibrate on the beat", the drone's "Lower", "Any octave counts"): its [text]
 * [PANEL_ROW_INSET] in, as a [ChoiceRow]'s, on up to two lines (whole words, set smaller where they don't fit), and a
 * switch at the end. The whole row, at least [SWITCH_ROW_HEIGHT] tall, toggles it with the press effect; TalkBack
 * hears a switch named [text]. A test tag in [modifier] finds the switch itself.
 */
@Composable
fun SwitchRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp)
    val smallest = with(LocalDensity.current) { MIN_TEXT_SIZE.toSp() }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = SWITCH_ROW_HEIGHT)
            .pressFeedback(source, MaterialTheme.shapes.medium)
            .toggleable(value = checked, interactionSource = source, indication = null, role = Role.Switch) {
                source.pressFlash()
                onCheckedChange(it)
            }
            .padding(start = PANEL_ROW_INSET, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = style,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            autoSize = remember(style.fontSize, smallest) { WholeWordsAutoSize(style.fontSize, smallest) },
            modifier = Modifier.weight(1f).padding(vertical = 6.dp),
        )
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A panel row's text stands this far in from its edge ([ChoiceRow], [SwitchRow], the metronome's Listen). */
val PANEL_ROW_INSET = 12.dp

/** A [PanelLabel] stands this far in from the panel's parts, over its faces as over its rows. */
val PANEL_LABEL_INSET = 6.dp

/** A [SwitchRow]'s least height: Material's one-line row with a switch. */
val SWITCH_ROW_HEIGHT = 56.dp

private val MIN_TEXT_SIZE = 8.dp
