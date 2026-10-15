package io.github.klaboukes.twangle.feature.metronome.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
 */
@Composable
fun TempoDial(
    bpm: Int,
    onBpmChange: (Int) -> Unit,
    onBpmChangeFinished: () -> Unit,
    beatGlow: () -> Float,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val currentBpm by rememberUpdatedState(bpm)
    val currentOnChange by rememberUpdatedState(onBpmChange)
    val currentOnFinished by rememberUpdatedState(onBpmChangeFinished)
    val marking = markingName(TempoMarking.of(bpm))
    val description = stringResource(R.string.metronome_tempo)
    val value = pluralStringResource(R.plurals.metronome_tempo_value, bpm, bpm, marking)
    val bpmOf = remember { { currentBpm } }
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
        TempoDialArt(bpm = bpmOf, beatGlow = beatGlow, modifier = Modifier.size(size))
        TempoReadout(bpm, marking, size)
    }
}

/**
 * The tempo in the dial's middle, sized with the dial (58, 13 and 15 units at 288): the figures stay
 * that size at any font size, like the tuner's note; "BPM" and the marking grow with the font up to
 * [MAX_LABEL_SCALE], so they keep clear of the needle. TalkBack hears it through the dial.
 */
@Composable
private fun TempoReadout(bpm: Int, marking: String, size: Dp) {
    val style = MaterialTheme.styleTokens.style
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val u = size.value / DIAL_DP
    val labelScale = min(density.fontScale, MAX_LABEL_SCALE)
    fun fixed(units: Float): TextUnit = with(density) { (units * u).dp.toSp() }
    fun label(units: Float): TextUnit = with(density) { (units * u * labelScale).dp.toSp() }
    val numbers = numberFontFor(style)
    Column(
        modifier = Modifier.clearAndSetSemantics {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            localDigits(bpm, appLocale()),
            style = TextStyle(
                fontFamily = numbers,
                fontSize = fixed(BPM_UNITS * numberScaleFor(style)),
                lineHeight = fixed(BPM_UNITS * numberScaleFor(style)),
                fontFeatureSettings = "$LINING_FIGURES, tnum",
                color = scheme.onBackground,
            ),
            modifier = Modifier.testTag(MetronomeTestTags.BPM),
        )
        Spacer(Modifier.height((6 * u).dp))
        Text(
            stringResource(R.string.metronome_bpm),
            style = TextStyle(
                fontFamily = numbers,
                fontSize = label(13f),
                letterSpacing = label(13f * 0.12f),
                fontFeatureSettings = LINING_FIGURES,
                color = scheme.onSurfaceVariant,
            ),
        )
        Spacer(Modifier.height((8 * u).dp))
        Text(
            marking,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = label(15f), lineHeight = label(18f), fontStyle = FontStyle.Italic),
            color = scheme.onSurfaceVariant,
        )
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
private const val DIAL_DP = 288f

/** The tempo's figures are this many units high (times the number face's scale). */
private const val BPM_UNITS = 58f

/** "BPM" and the marking grow with the font up to this. */
private const val MAX_LABEL_SCALE = 1.3f
