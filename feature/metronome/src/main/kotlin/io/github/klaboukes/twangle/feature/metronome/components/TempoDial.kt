package io.github.klaboukes.twangle.feature.metronome.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.LINING_FIGURES
import io.github.klaboukes.twangle.core.designsystem.appLocale
import io.github.klaboukes.twangle.core.designsystem.localDigits
import io.github.klaboukes.twangle.core.designsystem.meter.numberScaleFor
import io.github.klaboukes.twangle.core.designsystem.metronome.TempoDialArt
import io.github.klaboukes.twangle.core.designsystem.metronome.bpmAtTouch
import io.github.klaboukes.twangle.core.designsystem.metronome.isClearOfDialMiddle
import io.github.klaboukes.twangle.core.designsystem.metronome.isInDialGap
import io.github.klaboukes.twangle.core.designsystem.metronome.isOnDialRing
import io.github.klaboukes.twangle.core.designsystem.numberFontFor
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.metronome.ClickTrack
import io.github.klaboukes.twangle.core.dsp.metronome.TempoMarking
import io.github.klaboukes.twangle.feature.metronome.MetronomeTestTags
import io.github.klaboukes.twangle.feature.metronome.R
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The tempo dial ([TempoDialArt]) with the tempo over its middle: the figures in the style's number
 * face, "BPM" and the Italian marking under them. A touch on the dial's ring sets the tempo it points
 * to and a drag round it follows the finger ([onBpmChange]); a touch on the tempo in the middle, or
 * deep in the gap at the bottom, does nothing ([isOnDialRing], [isInDialGap]). Lifting the finger
 * calls [onBpmChangeFinished] (to save it). For TalkBack it is one slider ("Tempo, drag to change",
 * "96 beats per minute, Andante") that steps one BPM at a time. [beatGlow] lights the soft glow behind
 * the tempo on each beat, read in the draw phase only. [size] is the dial's width and height.
 *
 * The dial holds only the tempo (Phase 62, the owner's pick II of docs/design/metronome-layout-preview.html):
 * − Start + stand in the row under it ([TempoRow]). [shown] is the tempo the arc, needle and figures
 * show (gliding to a used tempo), [ringBpm] and [ringAlpha] the listening ring, [foundGlow] the glow
 * when a found tempo is used.
 */
@Composable
fun TempoDial(
    bpm: Int,
    onBpmChange: (Int) -> Unit,
    onBpmChangeFinished: () -> Unit,
    beatGlow: () -> Float,
    size: Dp,
    modifier: Modifier = Modifier,
    shown: () -> Float = { bpm.toFloat() },
    shownBpm: Int = bpm,
    ringBpm: () -> Float = { 0f },
    ringAlpha: () -> Float = { 0f },
    foundGlow: () -> Float = { 0f },
) {
    val currentBpm by rememberUpdatedState(bpm)
    val currentOnChange by rememberUpdatedState(onBpmChange)
    val currentOnFinished by rememberUpdatedState(onBpmChangeFinished)
    val marking = markingName(TempoMarking.of(bpm))
    val description = stringResource(R.string.metronome_tempo)
    val value = pluralStringResource(R.plurals.metronome_tempo_value, bpm, bpm, marking)
    val u = size.value / DIAL_UNITS
    val half = size.value / 2f
    Box(
        modifier = modifier
            .size(size)
            .testTag(MetronomeTestTags.DIAL)
            .semantics {
                contentDescription = description
                stateDescription = value
                progressBarRangeInfo = ProgressBarRangeInfo(
                    bpm.toFloat(),
                    ClickTrack.MIN_BPM.toFloat()..ClickTrack.MAX_BPM.toFloat(),
                    steps = ClickTrack.MAX_BPM - ClickTrack.MIN_BPM - 1,
                )
                setProgress { target ->
                    currentOnChange(target.roundToInt())
                    currentOnFinished()
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val width = this.size.width.toFloat()
                    val centre = androidx.compose.ui.geometry.Offset(width / 2f, this.size.height / 2f)
                    val downX = down.position.x - centre.x
                    val downY = down.position.y - centre.y
                    // A touch on the tempo in the middle, or deep in the empty gap, does nothing: only the ring turns the dial.
                    if (!isOnDialRing(downX, downY, width) || isInDialGap(downX, downY)) return@awaitEachGesture
                    fun follow(at: androidx.compose.ui.geometry.Offset) {
                        val dx = at.x - centre.x
                        val dy = at.y - centre.y
                        if (!isClearOfDialMiddle(dx, dy, width)) return
                        val to = bpmAtTouch(dx, dy, currentBpm)
                        if (to != currentBpm) currentOnChange(to)
                    }
                    follow(down.position)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        follow(change.position)
                        change.consume()
                    }
                    currentOnFinished()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        TempoDialArt(
            bpm = shown,
            beatGlow = beatGlow,
            ringBpm = ringBpm,
            ringAlpha = ringAlpha,
            foundGlow = foundGlow,
            modifier = Modifier.size(size),
        )
        TempoReadout(
            bpm = shownBpm,
            marking = markingName(TempoMarking.of(shownBpm)),
            size = size,
            figuresWidest = (FIGURES_WIDEST_UNITS * u).dp,
            labelsBottom = (half + LABELS_BOTTOM_UNITS * u).dp,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/**
 * The tempo in the dial's middle, sized with the dial (46, 13 and 15 units at 288), the figures'
 * middle [FIGURES_UP_UNITS] above the dial's: the figures stay that size at any
 * font size, like the tuner's note; "BPM" and the marking grow with the font up to
 * [MAX_LABEL_SCALE]. Measured, so every style's faces keep clear: the figures shrink to
 * [figuresWidest] (Space Age's and Futuristic's wide faces would reach the ring), and "BPM" and the
 * marking shrink so the marking ends above [labelsBottom] (where the arc's ends are; some faces' "BPM"
 * is set in a tall line). TalkBack hears it through the dial.
 */
@Composable
private fun TempoReadout(bpm: Int, marking: String, size: Dp, figuresWidest: Dp, labelsBottom: Dp, modifier: Modifier = Modifier) {
    val style = MaterialTheme.styleTokens.style
    val scheme = MaterialTheme.colorScheme
    val body = MaterialTheme.typography.bodyLarge
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val u = size.value / DIAL_DP
    fun fixed(units: Float): TextUnit = with(density) { (units * u).dp.toSp() }
    fun label(units: Float, scale: Float): TextUnit = with(density) { (units * u * scale).dp.toSp() }
    val numbers = numberFontFor(style)
    val figures = BPM_UNITS * numberScaleFor(style)
    val topPadding = (DIAL_DP / 2f - FIGURES_UP_UNITS - figures / 2f).coerceAtLeast(0f) * u
    val bpmLabel = stringResource(R.string.metronome_bpm)
    fun bpmStyle(scale: Float) = TextStyle(
        fontFamily = numbers,
        fontSize = label(13f, scale),
        letterSpacing = label(13f * 0.12f, scale),
        fontFeatureSettings = LINING_FIGURES,
        color = scheme.onSurfaceVariant,
    )
    fun markingStyle(scale: Float) = body.copy(fontSize = label(15f, scale), lineHeight = label(18f, scale), fontStyle = FontStyle.Italic)
    val figuresStyle = TextStyle(
        fontFamily = numbers,
        fontSize = fixed(figures),
        lineHeight = fixed(figures),
        fontFeatureSettings = "$LINING_FIGURES, tnum",
        color = scheme.onBackground,
    )
    // Sized by the widest tempo, so the figures keep one size from 40 to 240 BPM.
    val widest = localDigits(ClickTrack.MAX_BPM, appLocale())
    val figuresScale = remember(widest, figuresStyle, figuresWidest, density) {
        val natural = measurer.measure(widest, figuresStyle).size.width
        val room = with(density) { figuresWidest.toPx() }
        if (natural > room) room / natural else 1f
    }
    val labelScale = remember(bpmLabel, marking, size, labelsBottom, density, figuresStyle, figuresScale, body) {
        // What is left for the two lines under the figures (as tall as their face sets them, which may
        // be more than their size) and the gaps between them.
        val shownFigures = figuresStyle.copy(fontSize = figuresStyle.fontSize * figuresScale)
        val figuresHeight = measurer.measure(widest, shownFigures).size.height
        val room = with(density) { (labelsBottom.value - topPadding - (6f + 8f) * u).dp.toPx() } - figuresHeight
        var scale = min(density.fontScale, MAX_LABEL_SCALE)
        while (scale > MIN_LABEL_SCALE &&
            measurer.measure(bpmLabel, bpmStyle(scale)).size.height + measurer.measure(marking, markingStyle(scale)).size.height > room
        ) {
            scale -= LABEL_SCALE_STEP
        }
        scale
    }
    Column(
        modifier = modifier
            .padding(top = topPadding.dp)
            .clearAndSetSemantics {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            localDigits(bpm, appLocale()),
            style = figuresStyle.copy(fontSize = figuresStyle.fontSize * figuresScale),
            maxLines = 1,
            modifier = Modifier.testTag(MetronomeTestTags.BPM),
        )
        Spacer(Modifier.height((6 * u).dp))
        Text(bpmLabel, style = bpmStyle(labelScale), maxLines = 1)
        Spacer(Modifier.height((8 * u).dp))
        Text(marking, style = markingStyle(labelScale), color = scheme.onSurfaceVariant, maxLines = 1)
    }
}

/** The Italian name of [marking]. */
@Composable
internal fun markingName(marking: TempoMarking): String = stringResource(
    when (marking) {
        TempoMarking.GRAVE -> R.string.metronome_marking_grave
        TempoMarking.LARGO -> R.string.metronome_marking_largo
        TempoMarking.LARGHETTO -> R.string.metronome_marking_larghetto
        TempoMarking.ADAGIO -> R.string.metronome_marking_adagio
        TempoMarking.ANDANTE -> R.string.metronome_marking_andante
        TempoMarking.MODERATO -> R.string.metronome_marking_moderato
        TempoMarking.ALLEGRO -> R.string.metronome_marking_allegro
        TempoMarking.PRESTO -> R.string.metronome_marking_presto
        TempoMarking.PRESTISSIMO -> R.string.metronome_marking_prestissimo
    },
)

/** The dial's design size: its units are dp at this size. */
private const val DIAL_DP = DIAL_UNITS

/** The tempo's figures are this many units high (times the number face's scale). */
private const val BPM_UNITS = 46f

/** The figures' middle stands this many units above the dial's. */
private const val FIGURES_UP_UNITS = 14f

/**
 * The figures are at most this many units wide: inside the ring that turns the dial (from 74 units out,
 * isOnDialRing) where they stand. Only the wide number faces (Space Age, Futuristic) reach it.
 */
private const val FIGURES_WIDEST_UNITS = 100f

/**
 * The marking ends this many units below the dial's middle, above the arc's ends (47 units either side,
 * 81 down), where Start's top was before Phase 62.
 */
private const val LABELS_BOTTOM_UNITS = 74f

/** "BPM" and the marking grow with the font up to this, and shrink, a step at a time, at most to [MIN_LABEL_SCALE] where they would pass [LABELS_BOTTOM_UNITS]. */
private const val MAX_LABEL_SCALE = 1.3f
private const val MIN_LABEL_SCALE = 0.7f
private const val LABEL_SCALE_STEP = 0.05f
