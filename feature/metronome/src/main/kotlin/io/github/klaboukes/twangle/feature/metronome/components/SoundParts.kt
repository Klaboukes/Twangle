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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.pressFeedback
import io.github.klaboukes.twangle.core.designsystem.pressFlash
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.metronome.ClickSound
import io.github.klaboukes.twangle.feature.metronome.MetronomeTestTags
import io.github.klaboukes.twangle.feature.metronome.R
import kotlin.math.roundToInt

/*
 * The Sound button and its panel (Phase 45, the owner's pick A of docs/design/metronome-sound-preview.html,
 * round 2): the button first in the row shows the click picked, and opens a calm panel with the four
 * sounds and "Vibrate on the beat".
 */

/** [sound]'s line icon (wood block A3, claves A5, cowbell and rim click as on the mock-up). */
internal fun soundIcon(sound: ClickSound): Int = when (sound) {
    ClickSound.WOOD_BLOCK -> R.drawable.ic_sound_wood_block
    ClickSound.COWBELL -> R.drawable.ic_sound_cowbell
    ClickSound.CLAVES -> R.drawable.ic_sound_claves
    ClickSound.RIM_CLICK -> R.drawable.ic_sound_rim_click
}

/** [sound]'s name, the instrument's common name in the app's language. */
@Composable
internal fun soundName(sound: ClickSound): String = stringResource(
    when (sound) {
        ClickSound.WOOD_BLOCK -> R.string.metronome_sound_wood_block
        ClickSound.COWBELL -> R.string.metronome_sound_cowbell
        ClickSound.CLAVES -> R.string.metronome_sound_claves
        ClickSound.RIM_CLICK -> R.string.metronome_sound_rim_click
    },
)

/**
 * Sound, the row's first button: the icon of the click picked ([sound]), with the two vibration marks
 * either side while "Vibrate on the beat" is on ([vibrate]); a tap opens the panel ([onClick]). It
 * looks like the row's other buttons and keeps their size; the word under it is always "Sound".
 * TalkBack hears "Sound: Cowbell" (", vibrates on the beat" while on) as a drop-down list.
 */
@Composable
internal fun SoundButton(sound: ClickSound, vibrate: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val look = buttonLook()
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val name = soundName(sound)
    val spoken = stringResource(if (vibrate) R.string.metronome_sound_spoken_vibrating else R.string.metronome_sound_spoken, name)
    Box(
        modifier = modifier
            .testTag(MetronomeTestTags.SOUND)
            .pressFeedback(source, look.shape, fill = faceFill(look), outline = faceOutline(look))
            .clickable(interactionSource = source, indication = null) {
                source.pressFlash()
                onClick()
            }
            .semantics {
                role = Role.DropdownList
                contentDescription = spoken
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(soundIcon(sound)), contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(ROW_ICON))
        if (vibrate) {
            Icon(
                painterResource(R.drawable.ic_sound_vibrate_marks),
                contentDescription = null,
                tint = scheme.onSurface,
                modifier = Modifier.requiredSize(MARKS_WIDTH, ROW_ICON).testTag(MetronomeTestTags.SOUND_VIBRATE_MARKS),
            )
        }
    }
}

/**
 * The Sound panel while [open], on its own layer over the screen: the four sounds (icon and name, the
 * picked one, [sound], in the container colour with an accent outline) and the "Vibrate on the beat"
 * switch, on the raised surface colour (`surfaceContainerHigh`, as the tuner's pill: plain `surface` is the background's own colour in some schemes) with a soft lift and no border, as the mock-up. [anchor] is the Sound
 * button, in this layer's coordinates. Upright the panel opens above the row, its start edge in line
 * with Sound's; on a wide screen on its side ([wide]) there is no room above, so it opens beside
 * Sound, towards the dial, its bottom level with the button. Where the room is short (very large
 * text) it scrolls.
 *
 * A tap on a sound calls [onPick] and leaves the panel open for another try; Back or a tap beside it
 * calls [onDismiss]. TalkBack hears the panel by name, the sounds as a radio group and the switch by
 * its name.
 */
@Composable
fun SoundPanel(
    open: Boolean,
    sound: ClickSound,
    vibrate: Boolean,
    anchor: Rect?,
    wide: Boolean,
    onPick: (ClickSound) -> Unit,
    onVibrateChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
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
                Panel(sound, vibrate, onPick, onVibrateChange)
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
            // Upright the room above Sound; on its side the whole height.
            val room = if (wide) height - 2 * margin else place.top.roundToInt() - gap - margin
            val panel = measurables[1].measure(Constraints(minWidth = panelWidth, maxWidth = panelWidth, maxHeight = room.coerceAtLeast(0)))
            val x: Int
            val y: Int
            if (wide) {
                // Beside Sound, towards the dial (the start side), its bottom level with the button.
                x = if (rtl) place.right.roundToInt() + gap else place.left.roundToInt() - gap - panel.width
                y = place.bottom.roundToInt() - panel.height
            } else {
                // Above the row, its start edge in line with Sound's.
                x = if (rtl) place.right.roundToInt() - panel.width else place.left.roundToInt()
                y = place.top.roundToInt() - gap - panel.height
            }
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

@Composable
private fun Panel(sound: ClickSound, vibrate: Boolean, onPick: (ClickSound) -> Unit, onVibrateChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tokens = MaterialTheme.styleTokens
    val shape = MaterialTheme.shapes.large
    val title = stringResource(R.string.metronome_sound)
    Column(
        Modifier
            .testTag(MetronomeTestTags.SOUND_PANEL)
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
    ) {
        Column(Modifier.selectableGroup().semantics { contentDescription = title }) {
            for (option in ClickSound.entries) SoundRow(option, picked = option == sound, onPick = { onPick(option) })
        }
        VibrateSwitch(vibrate, onVibrateChange, Modifier.padding(top = 4.dp))
    }
}

/** One sound in the panel: its icon and name on a 48 dp row, a radio button for TalkBack. */
@Composable
private fun SoundRow(sound: ClickSound, picked: Boolean, onPick: () -> Unit) {
    val look = buttonLook()
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val text = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp)
    // In dp, so it holds at any font size.
    val smallest = with(LocalDensity.current) { MIN_NAME_SIZE.toSp() }
    Row(
        Modifier
            .testTag(MetronomeTestTags.sound(sound))
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .pressFeedback(
                source,
                MaterialTheme.shapes.medium,
                fill = if (picked) faceFill(look, picked = true) else Color.Transparent,
                outline = if (picked) faceOutline(look, picked = true) else null,
            )
            .selectable(selected = picked, interactionSource = source, indication = null, role = Role.RadioButton) {
                source.pressFlash()
                onPick()
            }
            .padding(horizontal = ROW_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(soundIcon(sound)), contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(ROW_ICON))
        Spacer(Modifier.width(ROW_PADDING))
        // One line whatever the language: a long name at large text is set smaller.
        Text(
            soundName(sound),
            style = text,
            color = scheme.onSurface,
            maxLines = 1,
            // Never wrapped, so the auto-size sees a name too wide (Ear training's AnswerButton in IntervalsPart.kt
            // says why).
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = smallest, maxFontSize = text.fontSize, stepSize = 0.5.sp),
        )
    }
}

/** "Vibrate on the beat" with a switch at the end; the whole row toggles it. */
@Composable
private fun VibrateSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
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

/** The marks' grid is 7 units wider each side than the 24 unit icon (ic_sound_vibrate_marks). */
private val MARKS_WIDTH = 38.dp

private const val PANEL_IN_MILLIS = 150
private const val PANEL_OUT_MILLIS = 120

/** As the mock-up: 232 dp, a little wider for longer names; less on a narrow window. */
private val PANEL_WIDTH = 248.dp
private val PANEL_PADDING = 8.dp

/** Between the panel and Sound. */
private val PANEL_GAP = 8.dp

/** The panel keeps this far from the window's edges. */
private val PANEL_MARGIN = 8.dp
private val PANEL_LIFT = 12.dp
private const val LIGHT_SHADOW = 0.3f
private const val DARK_SHADOW = 0.6f
private val ROW_HEIGHT = 48.dp
private val SWITCH_ROW_HEIGHT = 56.dp
private val ROW_PADDING = 14.dp
private val MIN_NAME_SIZE = 8.dp
