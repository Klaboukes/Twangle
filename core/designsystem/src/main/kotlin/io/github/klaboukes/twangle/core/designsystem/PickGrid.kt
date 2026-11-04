package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Twangle's one grid of pick buttons in a panel (Phase 77, docs/architecture/ui.md *Shared controls*): Chords' roots and
 * chord types, Scales' roots and scales, Capo's shapes, Ear training's intervals, ways of playing and chord types. Until Phase 77
 * Chords drew its own (`PickerGrid`) and Ear training its chips (`ChipGrid`), 8 dp taller and muted.
 *
 * [options] as see-through buttons [perRow] to a row (the last row centred), each as wide as the row allows up to
 * [maxFaceWidth] and [PICK_FACE_HEIGHT] tall, its [label] on up to [lines] lines (set smaller where it doesn't fit,
 * whole words); the [picked] ones in the container colour with the 2 dp accent outline, the app's one picked mark.
 * Each touch area is its share of the row and 48 dp tall, the gap between the faces inside it, so six roots still fit
 * a 320 dp phone with 48 dp each; all of one steady size whatever their labels say.
 *
 * Pick one (the default): a tap calls [onPick], and TalkBack hears a radio group named [groupLabel], each button a
 * radio button by its [spoken] name. With [toggles] (pick several): each is a checkbox a tap turns on or off, in a
 * group named [groupLabel]. [tag] gives each its test tag.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> PickGrid(
    options: List<T>,
    picked: (T) -> Boolean,
    onPick: (T) -> Unit,
    perRow: Int,
    groupLabel: String,
    label: @Composable (T) -> String,
    spoken: @Composable (T) -> String,
    tag: (T) -> String,
    modifier: Modifier = Modifier,
    toggles: Boolean = false,
    maxFaceWidth: Dp = Dp.Infinity,
    lines: Int = 1,
) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Whole pixels, rounded down, so a row of [perRow] never comes out a pixel too wide and wraps.
        val share = with(LocalDensity.current) { (constraints.maxWidth / perRow).toDp() }
        val slot = if (maxFaceWidth == Dp.Infinity) share else minOf(maxFaceWidth + PICK_FACE_GAP, share)
        FlowRow(
            modifier = Modifier
                .then(if (toggles) Modifier else Modifier.selectableGroup())
                .semantics { contentDescription = groupLabel },
            horizontalArrangement = Arrangement.Center,
            maxItemsInEachRow = perRow,
        ) {
            for (option in options) {
                PickFace(
                    text = label(option),
                    spoken = spoken(option),
                    picked = picked(option),
                    toggles = toggles,
                    slot = slot,
                    lines = lines,
                    onClick = { onPick(option) },
                    modifier = Modifier.testTag(tag(option)),
                )
            }
        }
    }
}

/** One button of the grid: its face in its [slot] of the row, 48 dp tall. */
@Composable
private fun PickFace(
    text: String,
    spoken: String,
    picked: Boolean,
    toggles: Boolean,
    slot: Dp,
    lines: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    val press = {
        source.pressFlash()
        onClick()
    }
    Box(
        modifier = modifier
            .then(
                if (toggles) {
                    Modifier.toggleable(value = picked, interactionSource = source, indication = null, role = Role.Checkbox) { press() }
                } else {
                    Modifier.selectable(selected = picked, interactionSource = source, indication = null, role = Role.RadioButton, onClick = press)
                },
            )
            .semantics { contentDescription = spoken }
            .size(slot, PICK_TOUCH_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(slot - PICK_FACE_GAP, PICK_FACE_HEIGHT)
                .pressFeedback(source, MaterialTheme.shapes.small, fill = faceFill(picked), outline = faceOutline(picked))
                .padding(horizontal = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            PickLabel(text, lines, if (picked) scheme.onPrimaryContainer else scheme.onSurface)
        }
    }
}

/** A button's label in [color] on up to [lines] lines, set smaller where it would not fit. */
@Composable
private fun PickLabel(text: String, lines: Int, color: Color) {
    val style = MaterialTheme.typography.labelLarge
    Text(
        musicText(breakableAtHyphens(koreanWordsKeptWhole(text))),
        style = style.copy(
            textAlign = TextAlign.Center,
            // Two lines take the size the text is set at, so they fit the face as it shrinks.
            lineHeight = if (lines > 1) TWO_LINE_HEIGHT else style.lineHeight,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        ),
        color = color,
        maxLines = lines,
        // A one-line label never wraps, so the auto-size sees it too wide (Ear training's AnswerButton in
        // IntervalsPart.kt says why).
        softWrap = lines > 1,
        autoSize = if (lines > 1) {
            LabelAutoSize
        } else {
            // The largest size that fits: below MIN_LABEL_SIZE only where the label would otherwise be cut
            // off (Classic's La♭maj7 at 2× text on a 320 dp phone fits at 7.5 sp).
            TextAutoSize.StepBased(minFontSize = LAST_RESORT_LABEL_SIZE, maxFontSize = LABEL_SIZE, stepSize = 0.5.sp)
        },
    )
}

/** A pick button's face height, in its 48 dp touch area. */
val PICK_FACE_HEIGHT = 40.dp

/** The faces of a row stand this far apart (inside their touch areas). */
val PICK_FACE_GAP = 6.dp

private val PICK_TOUCH_HEIGHT = 48.dp
private val LABEL_SIZE = 16.sp
private val MIN_LABEL_SIZE = 8.sp
private val LAST_RESORT_LABEL_SIZE = 6.sp
private val TWO_LINE_HEIGHT = 1.15.em
private val TWO_LINE_MARGIN = 3.dp

/** A long label keeps this much clear at either side inside the face's own 3 dp, where it can (Phase 52). */
private val LABEL_SIDE_ROOM = 6.dp

/**
 * A label on two lines: from [LABEL_SIZE] down to [MIN_LABEL_SIZE], with [TWO_LINE_MARGIN] where it can.
 * Written after those sizes: top-level values are set in the order they are written.
 */
private val LabelAutoSize = WholeWordsAutoSize(LABEL_SIZE, MIN_LABEL_SIZE, TWO_LINE_MARGIN, LABEL_SIDE_ROOM)
