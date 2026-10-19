package io.github.klaboukes.twangle.feature.metronome.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.AbsoluteAlignment
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
 * to and a drag round it follows the finger ([onBpmChange]); a touch on the tempo in the middle does
 * nothing ([isOnDialRing]). Lifting the finger calls [onBpmChangeFinished]
 * (to save it). For TalkBack it is one slider ("Tempo, drag to change", "96 beats per minute,
 * Andante") that steps one BPM at a time. [beatGlow] lights the soft glow behind the tempo on each
 * beat, read in the draw phase only. [size] is the dial's width and height.
 *
 * Everything about the tempo stands inside the dial (2026-10-18, the owner's pick A in
 * docs/design/metronome-roomy-preview.html): − and + ([onStep]) either side of the figures, inside
 * the needle's path, slower on the left and faster on the right in every language; Start / Stop
 * ([playing], [onToggle], resting while Listen listens: [startResting]) in the opening at the
 * bottom. Each takes its own touches, never the ring's. [shown] is the tempo the arc, needle and
 * figures show (gliding to a used tempo), [ringBpm] and [ringAlpha] the listening ring,
 * [foundGlow] the glow when a found tempo is used.
 */
@Composable
fun TempoDial(
    bpm: Int,
    onBpmChange: (Int) -> Unit,
    onBpmChangeFinished: () -> Unit,
    onStep: (Int) -> Unit,
    playing: Boolean,
    onToggle: () -> Unit,
    beatGlow: () -> Float,
    size: Dp,
    modifier: Modifier = Modifier,
    startResting: Boolean = false,
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
    val face = maxOf(STEP_FACE_UNITS * u, STEP_FACE_MIN.value).dp
    val stepBox = maxOf(face, MIN_TOUCH)
    val startFace = maxOf(START_UNITS * u, START_MIN.value).dp
    Box(modifier.size(size)) {
        Box(
            modifier = Modifier
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
                        // A touch on the tempo in the middle does nothing: only the ring turns the dial.
                        if (!isOnDialRing(down.position.x - centre.x, down.position.y - centre.y, width)) return@awaitEachGesture
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
                figuresWidest = (2f * (STEP_X_UNITS * u - FIGURES_GAP_UNITS * u) - face.value).dp,
                labelsBottom = (half + START_DOWN_UNITS * u - FIGURES_GAP_UNITS * u).dp - startFace / 2,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
        // After the dial, so TalkBack hears "Slower", "Faster" and Start after it; placed by absolute
        // left and right, each centred on its spot (in units from the dial's middle).
        fun Modifier.centredAt(x: Float, y: Float, box: Dp): Modifier = align(AbsoluteAlignment.TopLeft)
            .absoluteOffset(x = (half + x * u - box.value / 2f).dp, y = (half + y * u - box.value / 2f).dp)
        StepButton(
            faster = false,
            onClick = { onStep(-1) },
            face = face,
            modifier = Modifier.centredAt(-STEP_X_UNITS, -FIGURES_UP_UNITS, stepBox),
        )
        StepButton(
            faster = true,
            onClick = { onStep(+1) },
            face = face,
            modifier = Modifier.centredAt(STEP_X_UNITS, -FIGURES_UP_UNITS, stepBox),
        )
        StartStopButton(
            playing = playing,
            onClick = onToggle,
            face = startFace,
            resting = startResting,
            modifier = Modifier.centredAt(0f, START_DOWN_UNITS, startFace),
        )
    }
}

/**
 * The tempo in the dial's middle, sized with the dial (46, 13 and 15 units at 288), the figures'
 * middle [FIGURES_UP_UNITS] above the dial's, level with − and +: the figures stay that size at any
 * font size, like the tuner's note; "BPM" and the marking grow with the font up to
 * [MAX_LABEL_SCALE]. Measured, so every style's faces keep clear: the figures shrink to
 * [figuresWidest] (Space Age's and Futuristic's wide faces would reach under − and +), and "BPM" and
 * the marking shrink so the marking ends above [labelsBottom] (Start's top, less a gap: on a small dial
 * Start keeps its 56 dp, and some faces' "BPM" is set in a tall line). TalkBack hears it through the dial.
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

/** The tempo's figures are this many units high (times the number face's scale), small enough for − and + beside them. */
private const val BPM_UNITS = 46f

/** The figures' middle, and − and +, stand this many units above the dial's middle. */
private const val FIGURES_UP_UNITS = 14f

/**
 * − and + : faces 32 units across (at least 32 dp, in a 48 dp touch area), their middles 58 units out
 * from the dial's, so they reach no further than 76 units out, inside the needle (from 78).
 */
private const val STEP_FACE_UNITS = 32f
private val STEP_FACE_MIN = 32.dp
private const val STEP_X_UNITS = 58f
private val MIN_TOUCH = 48.dp

/**
 * Start / Stop: 54 units across (at least 56 dp), its middle 104 units below the dial's, in the
 * opening between the arc's ends (which are 47 units either side, 81 down), clear of the marking.
 */
private const val START_UNITS = 54f
private val START_MIN = 56.dp
private const val START_DOWN_UNITS = 104f

/** "BPM" and the marking grow with the font up to this, and shrink, a step at a time, at most to [MIN_LABEL_SCALE] where they would reach Start. */
private const val MAX_LABEL_SCALE = 1.3f
private const val MIN_LABEL_SCALE = 0.7f
private const val LABEL_SCALE_STEP = 0.05f

/** The figures keep this many units clear of − and + , and the marking of Start. */
private const val FIGURES_GAP_UNITS = 3f
