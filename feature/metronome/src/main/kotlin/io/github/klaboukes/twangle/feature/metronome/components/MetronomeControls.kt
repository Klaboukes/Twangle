package io.github.klaboukes.twangle.feature.metronome.components

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.LINING_FIGURES
import io.github.klaboukes.twangle.core.designsystem.appLocale
import io.github.klaboukes.twangle.core.designsystem.localDigits
import io.github.klaboukes.twangle.core.designsystem.meter.numberScaleFor
import io.github.klaboukes.twangle.core.designsystem.numberFontFor
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.feature.metronome.MetronomeTestTags
import io.github.klaboukes.twangle.feature.metronome.R
import kotlinx.coroutines.delay

/*
 * The metronome's buttons, as on the approved mock-up (docs/design/metronome-nav-preview.html): see-
 * through buttons on the background (the surface at 45 % behind a thin outline in the muted colour)
 * in the style's own shape, picked ones in the container colour with an accent outline, and Start in
 * the accent. Futuristic's are neon outlines in the accent with nothing behind them, as its tuner's.
 */

/** How the buttons look in the current style. */
private class ButtonLook(val neon: Boolean, val round: Boolean, val shape: Shape)

@Composable
private fun buttonLook(): ButtonLook {
    val style = MaterialTheme.styleTokens.style
    return ButtonLook(
        neon = style == VisualStyle.FUTURISTIC,
        round = style == VisualStyle.CONTEMPORARY || style == VisualStyle.SPACE_AGE,
        shape = MaterialTheme.shapes.small,
    )
}

/** A see-through button's backing and outline; [picked] ones are in the container colour with a 2 dp accent outline. */
@Composable
private fun Modifier.buttonFace(look: ButtonLook, shape: Shape, picked: Boolean = false, hit: Boolean = false): Modifier {
    val scheme = MaterialTheme.colorScheme
    val fill = when {
        // Tap tempo's flash: the mock-up's accent at 22 % over the surface (Futuristic: over nothing).
        hit && look.neon -> scheme.primary.copy(alpha = 0.22f)
        hit -> scheme.primary.copy(alpha = 0.22f).compositeOver(scheme.surface)
        picked && look.neon -> scheme.primary.copy(alpha = 0.12f)
        picked -> scheme.primaryContainer
        look.neon -> Color.Transparent
        else -> scheme.surface.copy(alpha = 0.45f)
    }
    val border = when {
        picked -> BorderStroke(2.dp, scheme.primary)
        look.neon -> BorderStroke(1.dp, scheme.primary.copy(alpha = NEON_OUTLINE_ALPHA))
        else -> BorderStroke(1.dp, scheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
    return this.clip(shape).background(fill, shape).border(border, shape)
}

/** Futuristic's outline: the accent at 55 %, as its tuner's neon buttons. */
private const val NEON_OUTLINE_ALPHA = 0.55f

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

/**
 * The meter picker at the top: 2/4, 3/4, 4/4, 5/4, 6/8 and 7/8 in a row, one radio group for
 * TalkBack ("Beats in a bar", each meter by its spoken name). With a very large font they wrap onto
 * a second row.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeterPicker(meter: Meter, onMeterChange: (Meter) -> Unit, modifier: Modifier = Modifier) {
    val look = buttonLook()
    val label = stringResource(R.string.metronome_beats_in_bar)
    val style = MaterialTheme.styleTokens.style
    // The wide number faces (Michroma, Orbitron) a little smaller, as on the meter, so all six fit a row.
    val numbers = TextStyle(
        fontFamily = numberFontFor(style),
        fontSize = 15.sp * numberScaleFor(style),
        fontFeatureSettings = LINING_FIGURES,
        textAlign = TextAlign.Center,
    )
    FlowRow(
        modifier = modifier.selectableGroup().semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(METER_GAP, Alignment.CenterHorizontally),
    ) {
        for (option in Meter.entries) {
            val picked = option == meter
            val spoken = meterSpoken(option)
            Box(
                modifier = Modifier
                    .testTag(MetronomeTestTags.meter(option))
                    .selectable(selected = picked, role = Role.RadioButton, onClick = { onMeterChange(option) })
                    .semantics { contentDescription = spoken }
                    .minimumInteractiveComponentSize(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = METER_BUTTON_WIDTH, minHeight = 40.dp)
                        .buttonFace(look, look.shape, picked = picked)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(meterLabel(option), style = numbers)
                }
            }
        }
    }
}

private val METER_GAP = 4.dp

/** The mock-up's 52 dp, less 2, so all six fit in a row on a 360 dp phone. */
private val METER_BUTTON_WIDTH = 50.dp

/** − and + (one BPM slower, faster) either side of Tap tempo. */
@Composable
fun TempoButtons(onSlower: () -> Unit, onFaster: () -> Unit, onTap: (atMillis: Long) -> Unit, modifier: Modifier = Modifier) {
    // Slower on the left, faster on the right in every language, as on the tempo dial above, which
    // like a clock is not mirrored in right-to-left languages.
    val textDirection = LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StepButton(R.drawable.ic_remove, stringResource(R.string.metronome_slower), onSlower, Modifier.testTag(MetronomeTestTags.SLOWER))
            CompositionLocalProvider(LocalLayoutDirection provides textDirection) { TapTempoButton(onTap) }
            StepButton(R.drawable.ic_add, stringResource(R.string.metronome_faster), onFaster, Modifier.testTag(MetronomeTestTags.FASTER))
        }
    }
}

@Composable
private fun StepButton(icon: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val look = buttonLook()
    val shape = if (look.round) CircleShape else look.shape
    Box(
        modifier = modifier
            .size(56.dp)
            .buttonFace(look, shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(22.dp))
    }
}

/**
 * Tap tempo. A tap counts the moment the finger touches (not when it lifts), so the tempo follows the
 * player's taps as closely as it can; the button lights up briefly with each. TalkBack's double tap
 * counts too.
 */
@Composable
private fun TapTempoButton(onTap: (atMillis: Long) -> Unit) {
    val look = buttonLook()
    val currentOnTap by rememberUpdatedState(onTap)
    var hits by remember { mutableIntStateOf(0) }
    var lit by remember { mutableIntStateOf(0) }
    LaunchedEffect(hits) {
        if (hits == 0) return@LaunchedEffect
        lit = hits
        delay(TAP_FLASH_MILLIS)
        lit = 0
    }
    val label = stringResource(R.string.metronome_tap)
    Box(
        modifier = Modifier
            .testTag(MetronomeTestTags.TAP)
            .heightIn(min = 56.dp)
            .defaultMinSize(minWidth = 132.dp)
            .buttonFace(look, look.shape, hit = lit != 0)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onClick(label) {
                    currentOnTap(SystemClock.uptimeMillis())
                    hits++
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    currentOnTap(down.uptimeMillis)
                    hits++
                }
            }
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

/** How long Tap tempo stays lit after a tap. */
private const val TAP_FLASH_MILLIS = 120L

/** Start / Stop, in the accent (Futuristic: a neon outline). */
@Composable
fun StartStopButton(playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val look = buttonLook()
    val scheme = MaterialTheme.colorScheme
    val shape = look.shape
    val fill = if (look.neon) scheme.primary.copy(alpha = 0.14f) else scheme.primary
    val content = if (look.neon) scheme.primary else scheme.onPrimary
    var face = modifier
        .heightIn(min = 56.dp)
        .defaultMinSize(minWidth = 196.dp)
        .clip(shape)
        .background(fill, shape)
    if (look.neon) face = face.border(1.5.dp, scheme.primary, shape)
    Row(
        modifier = face
            .clickable(role = Role.Button, indication = ripple(color = content), interactionSource = null, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .testTag(MetronomeTestTags.START),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(if (playing) R.drawable.ic_stop else R.drawable.ic_play),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(if (playing) R.string.metronome_stop else R.string.metronome_start),
            color = content,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/** "Vibrate on the beat": the whole row is one switch for TalkBack. */
@Composable
fun VibrateSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .testTag(MetronomeTestTags.VIBRATE)
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_vibrate),
            contentDescription = null,
            tint = if (checked) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Text(stringResource(R.string.metronome_vibrate), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
        Switch(checked = checked, onCheckedChange = null)
    }
}
