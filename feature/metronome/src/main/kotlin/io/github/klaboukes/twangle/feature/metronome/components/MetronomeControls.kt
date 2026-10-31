package io.github.klaboukes.twangle.feature.metronome.components

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.LINING_FIGURES
import io.github.klaboukes.twangle.core.designsystem.appLocale
import io.github.klaboukes.twangle.core.designsystem.localDigits
import io.github.klaboukes.twangle.core.designsystem.meter.numberScaleFor
import io.github.klaboukes.twangle.core.designsystem.numberFontFor
import io.github.klaboukes.twangle.core.designsystem.pressFeedback
import io.github.klaboukes.twangle.core.designsystem.pressFlash
import io.github.klaboukes.twangle.core.designsystem.OPTIONS_FACE
import io.github.klaboukes.twangle.core.designsystem.OptionsButton
import io.github.klaboukes.twangle.core.designsystem.StepperButton
import io.github.klaboukes.twangle.core.designsystem.StyleButton
import io.github.klaboukes.twangle.core.designsystem.buttonLook
import io.github.klaboukes.twangle.core.designsystem.styleButtonFace
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.feature.metronome.MetronomeTestTags
import io.github.klaboukes.twangle.feature.metronome.R

/*
 * The metronome's buttons, as on the approved mock-ups (docs/design/metronome-nav-preview.html and,
 * since Phase 27, docs/design/metronome-listen-preview.html): the style's own button ([StyleButton],
 * shared with the other screens since Phase 63), see-through, picked ones in the container colour
 * with an accent outline, and Start in the accent. Every one shows Twangle's press effect
 * ([pressFeedback]) instead of Material's ripple; Tap tempo its own crisp flash.
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

/**
 * The meter picker at the top: 2/4, 3/4, 4/4, 5/4, 6/8 and 7/8 in a row, one radio group for
 * TalkBack ("Beats in a bar", each meter by its spoken name). Where six 50 dp buttons don't fit the
 * width (a phone on its side beside navigation buttons) each button's touch area is its share of the
 * row (whole pixels, so the row never wraps) and 48 dp tall, as the chord buttons, its face 4 dp
 * narrower and its label set smaller where it must. Otherwise, with a very large font, they wrap
 * onto a second row.
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
    val labels = Meter.entries.map { meterLabel(it) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // The widest label at its smallest size, with the face's padding.
    val smallestFace = remember(labels, numbers, density) {
        with(density) { labels.maxOf { measurer.measure(it, numbers.copy(fontSize = MIN_LABEL_SIZE)).size.width }.toDp() } + 12.dp
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val count = Meter.entries.size
        val share = with(density) { (constraints.maxWidth / count).toDp() }
        // One row of shrunk faces only while the labels still fit them at their smallest; else (wide faces
        // at 2× text on a phone on its side) the full-size faces wrap onto a second row.
        val tight = maxWidth < METER_BUTTON_WIDTH * count + METER_GAP * (count - 1) && share - METER_GAP >= smallestFace
        val faceWidth = if (tight) share - METER_GAP else METER_BUTTON_WIDTH
        val touch = if (tight) Modifier.width(share).heightIn(min = MIN_TOUCH) else Modifier.minimumInteractiveComponentSize()
        FlowRow(
            modifier = Modifier.selectableGroup().semantics { contentDescription = label },
            horizontalArrangement = Arrangement.spacedBy(if (tight) 0.dp else METER_GAP, Alignment.CenterHorizontally),
        ) {
            for (option in Meter.entries) {
                val picked = option == meter
                val spoken = meterSpoken(option)
                val source = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .testTag(MetronomeTestTags.meter(option))
                        .selectable(
                            selected = picked,
                            interactionSource = source,
                            indication = null,
                            role = Role.RadioButton,
                            onClick = {
                                source.pressFlash()
                                onMeterChange(option)
                            },
                        )
                        .semantics { contentDescription = spoken }
                        .then(touch),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = faceWidth, minHeight = 40.dp)
                            .pressFeedback(source, look.shape, fill = look.fill(picked), outline = look.outline(picked))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (tight) {
                            // Never wrapped, so the auto-size sees a label too wide (Ear training's AnswerButton in
                            // IntervalsPart.kt says why).
                            Text(meterLabel(option), style = numbers, maxLines = 1, softWrap = false, autoSize = TextAutoSize.StepBased(minFontSize = MIN_LABEL_SIZE, maxFontSize = numbers.fontSize, stepSize = 0.5.sp))
                        } else {
                            Text(meterLabel(option), style = numbers)
                        }
                    }
                }
            }
        }
    }
}

private val METER_GAP = 4.dp
private val MIN_TOUCH = 48.dp
private val MIN_LABEL_SIZE = 9.sp

/** The mock-up's 52 dp, less 2, so all six fit in a row on a 360 dp phone. */
private val METER_BUTTON_WIDTH = 50.dp

/**
 * − or + (one BPM slower or faster) either side of Start in the row under the dial: the shared
 * stepper ([StepperButton]) with a [face] across, in a touch area of at least 48 dp.
 */
@Composable
fun StepButton(faster: Boolean, onClick: () -> Unit, face: Dp, modifier: Modifier = Modifier) {
    StepperButton(
        more = faster,
        label = stringResource(if (faster) R.string.metronome_faster else R.string.metronome_slower),
        onClick = onClick,
        modifier = modifier.testTag(if (faster) MetronomeTestTags.FASTER else MetronomeTestTags.SLOWER),
        face = face,
        icon = face * STEP_ICON_SHARE,
    )
}

/** − and +'s icon: this share of the face. */
private const val STEP_ICON_SHARE = 0.5f

/**
 * The one row under the dial (Phase 62, the owner's pick II of docs/design/metronome-layout-preview.html):
 * Tap tempo · − · Start · + · options. Every button keeps one size whatever the language or font size
 * (icons, no words; TalkBack hears each by name): Tap tempo and options [SIDE_BUTTON] across, − and +
 * [STEP_FACE], Start [START_FACE]. − Start + stand together in the middle, [TRIO_GAP] apart, and Tap
 * tempo and options at the row's ends, as far out as [ROW_WIDEST] allows; on a narrow window the gaps
 * close up evenly. − is slower on the left and + faster on the right in every language, like the
 * dial; Tap tempo is at the start and options at the end. [playing], [onToggle] and [startResting] are
 * Start / Stop's ([StartStopButton]); [optionsOpen] shows the options button picked while its panel is
 * open ([onOptions], [OptionsPanel]); [onOptionsPlaced] gives its place, for the panel and Listen's
 * suggestion to open from.
 */
@Composable
fun TempoRow(
    onTap: (atMillis: Long) -> Unit,
    onStep: (Int) -> Unit,
    playing: Boolean,
    onToggle: () -> Unit,
    optionsOpen: Boolean,
    onOptions: () -> Unit,
    modifier: Modifier = Modifier,
    startResting: Boolean = false,
    onOptionsPlaced: (Rect) -> Unit = {},
) {
    BoxWithConstraints(modifier.widthIn(max = ROW_WIDEST).fillMaxWidth(), contentAlignment = Alignment.Center) {
        val buttons = SIDE_BUTTON * 2 + STEP_FACE * 2 + START_FACE
        // Six gaps' worth of room: the trio's two gaps take a share each, the two ends the rest.
        val trioGap = ((maxWidth - buttons) / 6).coerceIn(0.dp, TRIO_GAP)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TapTempoButton(onTap, Modifier.size(SIDE_BUTTON))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(horizontalArrangement = Arrangement.spacedBy(trioGap), verticalAlignment = Alignment.CenterVertically) {
                    StepButton(faster = false, onClick = { onStep(-1) }, face = STEP_FACE)
                    StartStopButton(playing = playing, onClick = onToggle, face = START_FACE, resting = startResting)
                    StepButton(faster = true, onClick = { onStep(+1) }, face = STEP_FACE)
                }
            }
            // The shared options button (OPTIONS_FACE, as SIDE_BUTTON); TalkBack hears "Options" as a drop-down list.
            OptionsButton(
                open = optionsOpen,
                onClick = onOptions,
                label = stringResource(R.string.metronome_options),
                role = Role.DropdownList,
                modifier = Modifier.testTag(MetronomeTestTags.OPTIONS).onGloballyPositioned { onOptionsPlaced(it.boundsInRoot()) },
            )
        }
    }
}

/** Tap tempo and the options button: the shared options face (52 dp), so the row fits a 320 dp phone. */
private val SIDE_BUTTON = OPTIONS_FACE

/** − and +'s faces, a whole touch area. */
private val STEP_FACE = 48.dp

/** Start / Stop, the row's largest. */
private val START_FACE = 72.dp

/** Between − , Start and + at most. */
private val TRIO_GAP = 12.dp

/** The row reaches no wider than this (the mock-up's, on the Xiaomi); it keeps to the column's width below it. */
private val ROW_WIDEST = 344.dp
internal val ROW_ICON = 24.dp

/**
 * Tap tempo, a fingertip with two ripples. A tap counts the moment the finger touches (not when it
 * lifts), so the tempo follows the player's taps as closely as it can; the same touch gives the crisp
 * flash (full for 70 ms, gone 110 ms after the touch, never smeared into the next tap). TalkBack's
 * double tap counts too. Round in Contemporary and Space Age, else in the style's shape.
 */
@Composable
private fun TapTempoButton(onTap: (atMillis: Long) -> Unit, modifier: Modifier = Modifier) {
    val look = buttonLook()
    val currentOnTap by rememberUpdatedState(onTap)
    val source = remember { MutableInteractionSource() }
    val label = stringResource(R.string.metronome_tap)
    Box(
        modifier = modifier
            .testTag(MetronomeTestTags.TAP)
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
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_tap), contentDescription = null, tint = look.content(), modifier = Modifier.size(ROW_ICON))
    }
}

/**
 * Start / Stop in the middle of the row under the dial: a play or stop icon on a [face] across in the accent, round in
 * Contemporary and Space Age, else in the style's shape (Futuristic: a neon outline on a solid tint).
 * It keeps its size whatever it does; TalkBack hears "Start" or "Stop". While Listen listens it rests
 * at 38 % and takes no taps ([resting]).
 */
@Composable
fun StartStopButton(playing: Boolean, onClick: () -> Unit, face: Dp, modifier: Modifier = Modifier, resting: Boolean = false) {
    StyleButton(
        onClick = onClick,
        label = stringResource(if (playing) R.string.metronome_stop else R.string.metronome_start),
        modifier = modifier.testTag(MetronomeTestTags.START),
        face = face,
        enabled = !resting,
        main = true,
    ) {
        Icon(
            painterResource(if (playing) R.drawable.ic_stop else R.drawable.ic_play),
            contentDescription = null,
            modifier = Modifier.size(face * START_ICON_SHARE),
        )
    }
}

/** Start / Stop's icon: this share of its face. */
private const val START_ICON_SHARE = 0.42f


/** A text button's press light (Listen's status line): a pill with 12 dp corners, 8 dp in from the top and bottom of its 48 dp. */
internal val PILL = RoundedCornerShape(12.dp)

/** The dial is this many units across: lengths in it are in dp at a 288 dp dial. */
internal const val DIAL_UNITS = 288f
