package io.github.klaboukes.twangle.feature.metronome.components

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.SegmentedSwitch
import io.github.klaboukes.twangle.core.designsystem.appLocale
import io.github.klaboukes.twangle.core.designsystem.buttonLook
import io.github.klaboukes.twangle.core.designsystem.localDigits
import io.github.klaboukes.twangle.core.designsystem.pressFlash
import io.github.klaboukes.twangle.core.designsystem.styleButtonFace
import io.github.klaboukes.twangle.core.designsystem.R as DesignR
import io.github.klaboukes.twangle.core.dsp.metronome.ClickSound
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.feature.metronome.MetronomeTestTags
import io.github.klaboukes.twangle.feature.metronome.R

/*
 * The metronome's controls under the tempo (2.0, Phase 75; the first 2.0 mock-up,
 * docs/design/fresh-start-preview.html, `.segrow` and `.bigrow`): the meter as a segmented choice (the
 * tuner's [SegmentedSwitch]; the click sound beside it went to the options panel on 2026-11-04,
 * [SoundChoice]), then Tap the beat and Start side by side, the same width (Start the larger until
 * 2026-11-04), Start in the accent. Every one shows Twangle's press effect instead of Material's ripple; Tap
 * the beat its own crisp flash.
 */

/** The meter's label, e.g. "6/8", in the app language's digits (٦/٨ in Arabic). */
@Composable
internal fun meterLabel(meter: Meter): String {
    val locale = appLocale()
    return localDigits(meter.beatsPerBar, locale) + "/" + localDigits(meter.noteValue, locale)
}

/** The meter as TalkBack says it, e.g. "Six-eight time" (it would read "6/8" as a date or a fraction). */
@Composable
internal fun meterSpoken(meter: Meter): String = stringResource(
    when (meter) {
        Meter.TWO_FOUR -> R.string.metronome_meter_2_4_spoken
        Meter.THREE_FOUR -> R.string.metronome_meter_3_4_spoken
        Meter.FOUR_FOUR -> R.string.metronome_meter_4_4_spoken
        Meter.FIVE_FOUR -> R.string.metronome_meter_5_4_spoken
        Meter.SIX_EIGHT -> R.string.metronome_meter_6_8_spoken
        Meter.SEVEN_EIGHT -> R.string.metronome_meter_7_8_spoken
    },
)

/** [sound]'s line icon (wood block A3, claves A5, cowbell and rim click as on the 1.1 mock-up). */
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
 * The meter: 2/4, 3/4, 4/4, 5/4, 6/8 and 7/8 as one segmented choice ([SegmentedSwitch]) across the
 * column, the picked one filled. TalkBack hears a radio group "Beats in a bar" and each meter by its
 * spoken name ("Six-eight time").
 */
@Composable
internal fun MeterChoice(meter: Meter, onMeterChange: (Meter) -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.metronome_beats_in_bar)
    SegmentedSwitch(
        options = Meter.entries,
        selected = meter,
        onSelect = onMeterChange,
        label = { meterLabel(it) },
        tag = { MetronomeTestTags.meter(it) },
        spoken = { meterSpoken(it) },
        modifier = modifier
            .widthIn(max = CHOICE_WIDEST)
            .fillMaxWidth()
            .semantics { contentDescription = label },
    )
}

/** The segmented choices reach no wider than this (a tablet, a phone on its side); a phone's column below it. */
private val CHOICE_WIDEST = 400.dp

/**
 * Tap the beat and Start side by side across the column, the same width, [BIG_HEIGHT] tall whatever the
 * language or font size. Their words shrink to one line where they must; the buttons never change size. [playing], [onToggle] and [startResting] are Start / Stop's.
 */
@Composable
internal fun BigButtons(
    onTap: (atMillis: Long) -> Unit,
    playing: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    startResting: Boolean = false,
) {
    Row(
        modifier.widthIn(max = BIG_ROW_WIDEST).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BIG_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TapTheBeatButton(onTap, Modifier.weight(1f))
        StartStopButton(playing, onToggle, Modifier.weight(1f), resting = startResting)
    }
}

/**
 * Tap the beat, a fingertip with two ripples and the words. A tap counts the moment the finger touches
 * (not when it lifts), so the tempo follows the player's taps as closely as it can; the same touch gives
 * the crisp flash (full for 70 ms, gone 110 ms after the touch, never smeared into the next tap).
 * TalkBack's double tap counts too.
 */
@Composable
private fun TapTheBeatButton(onTap: (atMillis: Long) -> Unit, modifier: Modifier = Modifier) {
    val look = buttonLook()
    val currentOnTap by rememberUpdatedState(onTap)
    val source = remember { MutableInteractionSource() }
    val label = stringResource(R.string.metronome_tap)
    BigFace(
        icon = R.drawable.ic_tap,
        words = label,
        color = look.content(),
        modifier = modifier
            .testTag(MetronomeTestTags.TAP)
            .height(BIG_HEIGHT)
            .styleButtonFace(source, look, crisp = true)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = label
                onClick(label) {
                    currentOnTap(SystemClock.uptimeMillis())
                    source.tryEmit(PressInteraction.Press(Offset.Zero))
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    currentOnTap(down.uptimeMillis)
                    source.tryEmit(PressInteraction.Press(down.position))
                }
            },
    )
}

/**
 * Start / Stop: a play or stop icon and its word in the accent. It keeps its size whatever it does;
 * TalkBack hears "Start" or "Stop". While Listen listens it rests at 38 % and takes no taps ([resting]).
 */
@Composable
internal fun StartStopButton(playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, resting: Boolean = false) {
    val look = buttonLook()
    val source = remember { MutableInteractionSource() }
    val label = stringResource(if (playing) R.string.metronome_stop else R.string.metronome_start)
    BigFace(
        icon = if (playing) DesignR.drawable.ic_stop else DesignR.drawable.ic_play,
        words = label,
        color = look.content(main = true),
        modifier = modifier
            .testTag(MetronomeTestTags.START)
            .height(BIG_HEIGHT)
            .alpha(if (resting) RESTING_ALPHA else 1f)
            .styleButtonFace(source, look, main = true)
            .clickable(interactionSource = source, indication = null, enabled = !resting, role = Role.Button) {
                source.pressFlash()
                onClick()
            }
            .semantics(mergeDescendants = true) { contentDescription = label },
    )
}

/** A big button's face: [icon] and [words] side by side in the middle, the words on one line, smaller where they must be. */
@Composable
private fun BigFace(icon: Int, words: String, color: Color, modifier: Modifier) {
    val style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp)
    val smallest = with(LocalDensity.current) { MIN_NAME_SIZE.toSp() }
    Row(modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(BIG_ICON))
        Spacer(Modifier.width(8.dp))
        BigWords(words, color, style, smallest)
    }
}

@Composable
private fun RowScope.BigWords(words: String, color: Color, style: androidx.compose.ui.text.TextStyle, smallest: androidx.compose.ui.unit.TextUnit) {
    Text(
        words,
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = smallest, maxFontSize = style.fontSize, stepSize = 0.5.sp),
        modifier = Modifier.weight(1f, fill = false),
    )
}

/** Tap the beat and Start: the mock-up's 72, a little lower so the screen fits a short phone. */
private val BIG_HEIGHT = 64.dp
private val BIG_ICON = 24.dp
private val BIG_GAP = 12.dp

/** The row reaches no wider than this. */
private val BIG_ROW_WIDEST = 400.dp

/** Start while Listen listens, as a disabled button. */
private const val RESTING_ALPHA = 0.38f

/** Icons in a row of the options panel. */
internal val ROW_ICON = 24.dp

/** A text button's press light (Listen's status line): a pill with 12 dp corners, 8 dp in from the top and bottom of its 48 dp. */
internal val PILL = RoundedCornerShape(12.dp)

/** The smallest a name or word is set where it must shrink to one line. */
internal val MIN_NAME_SIZE: Dp = 8.dp
