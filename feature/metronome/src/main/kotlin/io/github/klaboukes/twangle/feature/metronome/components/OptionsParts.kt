package io.github.klaboukes.twangle.feature.metronome.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.CHOICE_FACE_GAP
import io.github.klaboukes.twangle.core.designsystem.ChoiceFace
import io.github.klaboukes.twangle.core.designsystem.TextChoiceFace
import io.github.klaboukes.twangle.core.designsystem.pressFeedback
import io.github.klaboukes.twangle.core.designsystem.pressFlash
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.dsp.metronome.Subdivision
import io.github.klaboukes.twangle.feature.metronome.MetronomeTestTags
import io.github.klaboukes.twangle.feature.metronome.R
import kotlin.math.roundToInt

/*
 * The options panel (Phase 62; since Phase 75 behind the options button in the screen's top end corner):
 * what is set less often than the tempo. Its rows, top to bottom: the clicks between the beats ("Between
 * the beats", Phase 62), Vibrate on the beat, and Listen (Phase 27). The click sounds left it for the
 * screen in Phase 75 ([SoundChoice]), and the speed trainer for a row of its own ([TrainerRow]), whose
 * settings open in a panel of the same kind ([FloatingPanel]) from that row.
 */

/**
 * [subdivision]'s note-value icon in [meter]: a single note for none, then two, three (with its 3) and
 * four beamed notes. In 6/8 and 7/8, where a click is already an eighth, two to a beat are sixteenths,
 * so the pair has two beams there.
 */
internal fun subdivisionIcon(subdivision: Subdivision, meter: Meter): Int = when (subdivision) {
    Subdivision.NONE -> R.drawable.ic_note_quarter
    Subdivision.TWO -> if (inEighths(meter)) R.drawable.ic_note_sixteenth_pair else R.drawable.ic_note_eighths
    Subdivision.THREE -> R.drawable.ic_note_triplets
    Subdivision.FOUR -> R.drawable.ic_note_sixteenths
}

/** [subdivision]'s name in [meter] (TalkBack): None, Eighths, Triplets, Sixteenths; two to a beat in 6/8 and 7/8 are sixteenths. */
@Composable
internal fun subdivisionName(subdivision: Subdivision, meter: Meter): String = stringResource(
    when (subdivision) {
        Subdivision.NONE -> R.string.metronome_subdivision_none
        Subdivision.TWO -> if (inEighths(meter)) R.string.metronome_subdivision_sixteenths else R.string.metronome_subdivision_eighths
        Subdivision.THREE -> R.string.metronome_subdivision_triplets
        Subdivision.FOUR -> R.string.metronome_subdivision_sixteenths
    },
)

/**
 * What the status line's mark says for [subdivision] in [meter] ("Eighths between the beats"), or null
 * for none, which has no mark.
 */
@Composable
internal fun subdivisionMarkName(subdivision: Subdivision, meter: Meter): String? = when (subdivision) {
    Subdivision.NONE -> null
    Subdivision.TWO -> stringResource(
        if (inEighths(meter)) R.string.metronome_subdivision_sixteenths_on else R.string.metronome_subdivision_eighths_on,
    )
    Subdivision.THREE -> stringResource(R.string.metronome_subdivision_triplets_on)
    Subdivision.FOUR -> stringResource(R.string.metronome_subdivision_sixteenths_on)
}

/** Whether a click in [meter] is an eighth (6/8, 7/8), so two to a beat are sixteenths: those meters offer only none and two. */
private fun inEighths(meter: Meter): Boolean = !Subdivision.THREE.isOfferedIn(meter)

/**
 * A floating panel while [open], on its own layer over the screen, holding [content] (one row under the
 * other) on the raised surface colour (`surfaceContainerHigh`, as the tuner's pill: plain `surface` is
 * the background's own colour in some schemes) with a soft lift and no border, as the 1.1 mock-up;
 * [PANEL_WIDTH] wide (less on a narrow window), as tall as its rows. [anchor] is what opened it (the
 * options button, the speed trainer's row), in this layer's coordinates: the panel opens under it when
 * it is in the top half of the layer, over it otherwise, its end edge in line with the anchor's. Where
 * the room there is short (on its side, very large text) it slides along to stay inside the window, over
 * its anchor if it must; where the window is too short it scrolls.
 *
 * Back or a tap beside it calls [onDismiss], and the tap does nothing else. TalkBack hears the panel by
 * its [title]. [tag] is its test tag.
 */
@Composable
fun FloatingPanel(
    open: Boolean,
    anchor: Rect?,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String = MetronomeTestTags.OPTIONS_PANEL,
    content: @Composable ColumnScope.() -> Unit,
) {
    val showing = open && anchor != null
    BackHandler(enabled = showing, onBack = onDismiss)
    // Where it was opened from stays while the panel fades out.
    var place by remember { mutableStateOf(Rect.Zero) }
    if (anchor != null && open) place = anchor
    AnimatedVisibility(
        visible = showing,
        enter = fadeIn(tween(PANEL_IN_MILLIS)),
        exit = fadeOut(tween(PANEL_OUT_MILLIS)),
        modifier = modifier,
    ) {
        Layout(
            content = {
                // A tap beside the panel closes it, and does nothing else.
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(onDismiss) {
                            awaitEachGesture {
                                awaitFirstDown().consume()
                                onDismiss()
                            }
                        },
                )
                Panel(title, tag, content)
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val margin = PANEL_MARGIN.roundToPx()
            val gap = PANEL_GAP.roundToPx()
            val rtl = layoutDirection == LayoutDirection.Rtl
            val scrim = measurables[0].measure(Constraints.fixed(width, height))
            val panelWidth = minOf(PANEL_WIDTH.roundToPx(), (width - 2 * margin).coerceAtLeast(0))
            val panel = measurables[1].measure(
                Constraints(minWidth = panelWidth, maxWidth = panelWidth, maxHeight = (height - 2 * margin).coerceAtLeast(0)),
            )
            val below = place.center.y < height / 2f
            // Its end edge in line with the anchor's.
            val x = if (rtl) place.left.roundToInt() else place.right.roundToInt() - panel.width
            val y = if (below) place.bottom.roundToInt() + gap else place.top.roundToInt() - gap - panel.height
            layout(width, height) {
                scrim.place(0, 0)
                panel.place(
                    x.coerceIn(margin, (width - margin - panel.width).coerceAtLeast(margin)),
                    y.coerceIn(margin, (height - margin - panel.height).coerceAtLeast(margin)),
                )
            }
        }
    }
}

/** The panel's surface holding its rows. */
@Composable
private fun Panel(title: String, tag: String, content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tokens = MaterialTheme.styleTokens
    val shape = MaterialTheme.shapes.large
    Column(
        Modifier
            .testTag(tag)
            .semantics { paneTitle = title }
            .shadow(
                PANEL_LIFT,
                shape,
                ambientColor = Color.Black.copy(alpha = if (tokens.dark) DARK_SHADOW else LIGHT_SHADOW),
                spotColor = Color.Black.copy(alpha = if (tokens.dark) DARK_SHADOW else LIGHT_SHADOW),
            )
            .background(scheme.surfaceContainerHigh, shape)
            .verticalScroll(rememberScrollState())
            .padding(PANEL_PADDING),
        content = content,
    )
}

/**
 * The panel's first row (Phase 62): "Between the beats" over its four note values as icons side by
 * side, as the sounds: none, eighths, triplets, sixteenths. [subdivision] is the choice saved; the
 * one marked picked is what plays in [meter] ([Subdivision.within]). 6/8 and 7/8 offer only none and
 * two to a beat (sixteenths there, drawn and named so), so triplets and sixteenths stay in their
 * places, dimmed and untouchable, and a triplets or sixteenths choice shows none picked there while it
 * is kept for the next meter that offers it. A tap calls [onPick] and leaves the panel open. TalkBack
 * hears a radio group "Between the beats" and each choice by its name, a dimmed one as unavailable.
 */
@Composable
internal fun SubdivisionChoice(
    subdivision: Subdivision,
    meter: Meter,
    onPick: (Subdivision) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.metronome_subdivision)
    val playing = subdivision.within(meter)
    Column(modifier) {
        PanelHeading(title)
        Row(
            Modifier
                .fillMaxWidth()
                .selectableGroup()
                .semantics { contentDescription = title },
            horizontalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
        ) {
            for (option in Subdivision.entries) {
                IconSegment(
                    icon = subdivisionIcon(option, meter),
                    name = subdivisionName(option, meter),
                    tag = MetronomeTestTags.subdivision(option),
                    picked = option == playing,
                    enabled = option.isOfferedIn(meter),
                    onPick = { onPick(option) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A row's name over its choices: one line in the muted colour, set smaller where a language needs it. */
@Composable
internal fun PanelHeading(text: String) {
    val style = MaterialTheme.typography.labelLarge
    // In dp, so it holds at any font size.
    val smallest = with(LocalDensity.current) { MIN_NAME_SIZE.toSp() }
    Text(
        text,
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        // Never wrapped, so the auto-size sees a name too wide (Ear training's AnswerButton in IntervalsPart.kt says why).
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = smallest, maxFontSize = style.fontSize, stepSize = 0.5.sp),
        modifier = Modifier
            .padding(start = ROW_PADDING, end = ROW_PADDING, top = 4.dp, bottom = 2.dp)
            .semantics { hideFromAccessibility() },
    )
}

/**
 * One choice of a row (a sound, a note value): its [icon] on the shared face ([ChoiceFace]), a radio
 * button TalkBack hears by [name]. Not [enabled]: dimmed in place, untouchable.
 */
@Composable
private fun IconSegment(
    icon: Int,
    name: String,
    tag: String,
    picked: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    ChoiceFace(name, picked, onPick, modifier.testTag(tag), enabled) {
        Icon(painterResource(icon), contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(ROW_ICON))
    }
}

/** One choice of a row shown as a short [text] (the speed trainer's "+5", "4"): the shared [TextChoiceFace]. */
@Composable
internal fun TextSegment(
    text: String,
    name: String,
    tag: String,
    picked: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) = TextChoiceFace(text, name, picked, onPick, modifier.testTag(tag))

/** "Vibrate on the beat" with a switch at the end; the whole row toggles it. */
@Composable
internal fun VibrateSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .testTag(MetronomeTestTags.VIBRATE)
            .fillMaxWidth()
            .heightIn(min = SWITCH_ROW_HEIGHT)
            .pressFeedback(source, MaterialTheme.shapes.medium)
            .toggleable(value = checked, interactionSource = source, indication = null, role = Role.Switch) {
                source.pressFlash()
                onCheckedChange(it)
            }
            .padding(start = ROW_PADDING, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.metronome_vibrate),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            color = scheme.onSurface,
            modifier = Modifier.weight(1f).padding(vertical = 6.dp),
        )
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * Listen: the microphone and its name on a 48 dp row; a tap ([onClick]) closes the panel and starts
 * listening. While [listening] it shows the stop square in the accent and "Stop listening", and a tap
 * stops it (the status line's Stop does the same).
 */
@Composable
internal fun ListenRow(listening: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val text = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp)
    val smallest = with(LocalDensity.current) { MIN_NAME_SIZE.toSp() }
    Row(
        modifier
            .testTag(MetronomeTestTags.LISTEN)
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .pressFeedback(source, MaterialTheme.shapes.medium)
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                source.pressFlash()
                onClick()
            }
            .padding(horizontal = ROW_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(if (listening) R.drawable.ic_listen_stop else R.drawable.ic_listen),
            contentDescription = null,
            tint = if (listening) scheme.primary else scheme.onSurface,
            modifier = Modifier.size(ROW_ICON),
        )
        Spacer(Modifier.width(ROW_PADDING))
        Text(
            stringResource(if (listening) R.string.metronome_listen_stop_spoken else R.string.metronome_listen),
            style = text,
            color = scheme.onSurface,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = smallest, maxFontSize = text.fontSize, stepSize = 0.5.sp),
        )
    }
}

private const val PANEL_IN_MILLIS = 150
private const val PANEL_OUT_MILLIS = 120

/** As the mock-up: 280 dp; less on a narrow window. */
private val PANEL_WIDTH = 280.dp
private val PANEL_PADDING = 8.dp

/** Between the panel and the options button. */
private val PANEL_GAP = 8.dp

/** The panel keeps this far from the window's edges. */
private val PANEL_MARGIN = 8.dp
private val PANEL_LIFT = 12.dp
private const val LIGHT_SHADOW = 0.3f
private const val DARK_SHADOW = 0.6f
internal val ROW_HEIGHT = 48.dp
private val SWITCH_ROW_HEIGHT = 56.dp
internal val ROW_PADDING = 14.dp

/** The faces of a row ([ChoiceFace]: 40 dp tall as the meter picker's buttons, in a 48 dp touch area) stand 4 dp apart. */
internal val SEGMENT_GAP = CHOICE_FACE_GAP
