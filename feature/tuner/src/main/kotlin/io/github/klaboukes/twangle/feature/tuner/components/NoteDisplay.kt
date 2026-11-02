package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import io.github.klaboukes.twangle.core.designsystem.OneLineAutoSize
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.feature.tuner.R
import io.github.klaboukes.twangle.feature.tuner.TunerHint
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags
import io.github.klaboukes.twangle.feature.tuner.tunerHintFor

/** What [NoteDisplay] shows for a detected note. */
data class NoteReadout(
    val noteName: String,
    val octave: Int,
    val frequencyHz: Float,
    val cents: Float,
    val inTune: Boolean = false,
    /** The tuner's cue, held steady by the tuner; by default the plain one for [cents]. */
    val hint: TunerHint? = tunerHintFor(cents, inTune),
)

/**
 * Big target note with its octave, then the frequency, then the [word] (the word under the lens, [TuneWordLine],
 * which says what to do and the cents, and speaks for the tuner). The big note and the hertz go in Phase 71's
 * last step, the ruler naming the note; until then they stay (the cents pill went when the word came, as the
 * word's line has the cents).
 * [compact] makes the note smaller, for short spaces such as a phone on its side.
 * With a [maxHeight] the note (only the note, whatever it says) gets smaller so the block fits in it,
 * down to [FIT_NOTE_MIN_SIZE]; with [noteMayGo] (2.0, Phase 71: the ruler above shows the note) a block that
 * still doesn't fit leaves the note out. The note shrinks to fit when a solfège name ("Sol♯") is wide. While the
 * readout is in tune the note turns the "in tune" colour and a soft glow lights up behind it with a small pop.
 * Without a [readout] the note's slot keeps its height, empty, so nothing moves when a note comes in; the word
 * says "Play a string". A ♯ or ♭ is set smaller and raised, like in sheet music ([musicText]).
 *
 * The glow and the pop are read only while drawing, so they animate without recomposing. TalkBack hears none of
 * this but the [word], which is the one speaking item.
 */
@Composable
fun NoteDisplay(
    readout: NoteReadout?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    maxHeight: Dp = Dp.Unspecified,
    noteMayGo: Boolean = false,
    word: @Composable () -> Unit = {},
) {
    val inTune = readout?.inTune == true
    val colors = MaterialTheme.colorScheme
    val tuneColor = inTuneColor()
    val noteColor by animateInTuneColor(inTune, colors.onSurface, "noteColor")
    val glow = animateFloatAsState(if (inTune) 1f else 0f, tween(GLOW_MS), label = "glow")
    val pop = remember { Animatable(1f) }
    LaunchedEffect(inTune) {
        if (inTune) {
            pop.animateTo(POP_SCALE, tween(POP_MS))
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }
    val density = LocalDensity.current
    val noteSize = if (compact) COMPACT_NOTE_SIZE else BIG_NOTE_SIZE
    val displayLarge = MaterialTheme.typography.displayLarge
    // Already huge, so it doesn't grow further with the system font size; that would
    // push the rest of the tuner off the screen.
    fun noteStyle(size: Dp) = displayLarge.copy(
        fontSize = with(density) { size.toSp() },
        lineHeight = with(density) { (size * BIG_NOTE_LINE_RATIO).toSp() },
    )
    val headline = MaterialTheme.typography.headlineMedium
    // The octave grows with the system font size, but never past half the letter, which a short
    // screen may have shrunk: Polish at 2× text had an octave as tall as the letter beside it.
    // In dp: large text grows non-linearly, so a share of the size in sp would come out too big.
    fun octaveStyle(letterSize: Dp): TextStyle = with(density) {
        val most = letterSize * OCTAVE_MAX_SHARE
        val size = headline.fontSize.toDp()
        if (size <= most) headline else headline.copy(fontSize = most.toSp(), lineHeight = (headline.lineHeight.toDp() * (most / size)).toSp())
    }
    val placeholder = stringResource(R.string.tuner_placeholder_note)
    val noteText = remember(readout?.noteName, placeholder) {
        // Spaces in the face around the ♪ give its line the height of a note's line, so
        // nothing moves when a note comes in or goes.
        musicText(readout?.noteName ?: " $placeholder ")
    }
    val noteLine = @Composable { letterSize: Dp ->
        // The note reads "E2" in every language: the octave stays right of the letter in
        // right-to-left languages too.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier
                    .drawBehind {
                        val strength = glow.value
                        if (strength > 0f) {
                            drawCircle(
                                Brush.radialGradient(listOf(tuneColor.copy(alpha = 0.32f * strength), Color.Transparent)),
                                radius = size.maxDimension * 0.75f,
                            )
                        }
                    }
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    },
            ) {
                Text(
                    text = noteText,
                    style = noteStyle(letterSize),
                    // Tried from the full size down: Compose's StepBased search may stop a step short of the
                    // top through rounding, at some screen densities only (108 dp on the Xiaomi's forced
                    // tablet window, 112 elsewhere), so the note's size depended on the device.
                    autoSize = with(density) {
                        OneLineAutoSize(
                            largest = letterSize.toSp(),
                            smallest = minOf(BIG_NOTE_MIN_SIZE, letterSize).toSp(),
                            step = 4.dp.toSp(),
                        )
                    },
                    // Without a note the ♪ only holds the slot's height: the word says what to do.
                    color = if (readout != null) noteColor else Color.Transparent,
                    maxLines = 1,
                    // Never wrapped, as every one-line auto-sized text (Ear training's AnswerButton in
                    // IntervalsPart.kt says why).
                    softWrap = false,
                    modifier = Modifier
                        .alignByBaseline()
                        .widthIn(max = BIG_NOTE_MAX_WIDTH)
                        .hiddenFromTalkBack(),
                )
                if (readout != null) {
                    Text(
                        text = readout.octave.toString(),
                        style = octaveStyle(letterSize),
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.alignByBaseline().hiddenFromTalkBack(),
                    )
                }
            }
        }
    }
    // Everything under the note: the frequency and the word.
    val rest = @Composable {
        val frequencyLine = rememberLineHeight(MaterialTheme.typography.bodyLarge)
        Text(
            text = if (readout == null) "" else stringResource(R.string.tuner_frequency, readout.frequencyHz),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.keepLineHeight(frequencyLine).hiddenFromTalkBack(),
        )
        word()
    }
    Box(modifier, propagateMinConstraints = true) {
        if (maxHeight.isSpecified) {
            FitNoteColumn(maxHeight, noteSize, rememberLineHeight(noteStyle(noteSize)), noteLine, rest, noteMayGo)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(NOTE_SPACING)) {
                noteLine(noteSize)
                rest()
            }
        }
    }
}

/**
 * [note] above [rest], as [NoteDisplay]'s column, with the note at the size that fills what [rest]
 * leaves of [maxHeight]: at most [fullSize] (whose line is [fullLine] px tall in the face),
 * at least [FIT_NOTE_MIN_SIZE]. It follows the room, not the note's name, so it doesn't change from
 * one note to the next. With [noteMayGo], where even that size doesn't fit, the note is left out and
 * the rest stands alone, in a readout and without one alike, so nothing moves between the two.
 */
@Composable
private fun FitNoteColumn(
    maxHeight: Dp,
    fullSize: Dp,
    fullLine: Int,
    note: @Composable (size: Dp) -> Unit,
    rest: @Composable () -> Unit,
    noteMayGo: Boolean = false,
) {
    SubcomposeLayout { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val restPlaceable = subcompose(FitSlot.REST) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(NOTE_SPACING)) { rest() }
        }.single().measure(loose)
        val spacing = NOTE_SPACING.roundToPx()
        val room = maxHeight.roundToPx() - restPlaceable.height - spacing
        // The line grows with the note, so the size that fills the room is in proportion to it.
        val fitting = fullSize * (room.toFloat() / fullLine)
        if (noteMayGo && fitting < FIT_NOTE_MIN_SIZE) {
            return@SubcomposeLayout layout(restPlaceable.width, restPlaceable.height) { restPlaceable.placeRelative(0, 0) }
        }
        val size = fitting.coerceIn(FIT_NOTE_MIN_SIZE, fullSize)
        val notePlaceable = subcompose(FitSlot.NOTE) { note(size) }.single().measure(loose)
        val width = maxOf(notePlaceable.width, restPlaceable.width)
        layout(width, notePlaceable.height + spacing + restPlaceable.height) {
            notePlaceable.placeRelative((width - notePlaceable.width) / 2, 0)
            restPlaceable.placeRelative((width - restPlaceable.width) / 2, notePlaceable.height + spacing)
        }
    }
}

private enum class FitSlot { NOTE, REST }

/**
 * The note in focus mode's corner, above the full-screen lens: its name and octave at [FOCUS_NOTE_SIZE]
 * (under half the big note's size) and nothing else while a note sounds; nothing without one. In tune it turns the
 * "in tune" colour. TalkBack hears the word under the lens, not this.
 */
@Composable
fun FocusNote(readout: NoteReadout?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val noteColor by animateInTuneColor(readout?.inTune == true, colors.onSurface, "focusNoteColor")
    val density = LocalDensity.current
    Box(modifier.testTag(TunerTestTags.FOCUS_NOTE).hiddenFromTalkBack()) {
        if (readout != null) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.widthIn(max = FOCUS_NOTE_MAX_WIDTH)) {
                    Text(
                        text = remember(readout.noteName) { musicText(readout.noteName) },
                        // In dp, as the big note: it doesn't grow with the system font size.
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = with(density) { FOCUS_NOTE_SIZE.toSp() },
                            lineHeight = with(density) { (FOCUS_NOTE_SIZE * BIG_NOTE_LINE_RATIO).toSp() },
                        ),
                        color = noteColor,
                        maxLines = 1,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        text = readout.octave.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
        }
    }
}

/** Left out for screen readers (the word says it), but still there for UI tests. */
private fun Modifier.hiddenFromTalkBack(): Modifier = semantics { hideFromAccessibility() }

private val FOCUS_NOTE_SIZE = 48.dp

/** A long note name keeps clear of the corner's other end. */
private val FOCUS_NOTE_MAX_WIDTH = 240.dp

private val BIG_NOTE_SIZE = 112.dp
private val BIG_NOTE_MIN_SIZE = 56.dp

/** The octave's size at most, as a share of the letter's: it is a small figure beside it. */
private const val OCTAVE_MAX_SHARE = 0.5f
private const val BIG_NOTE_LINE_RATIO = 120f / 112f
private val COMPACT_NOTE_SIZE = 80.dp

/** A note fitted to a short screen ([NoteDisplay]'s maxHeight) gets no smaller than this. */
private val FIT_NOTE_MIN_SIZE = 32.dp

/** Between the note and the lines under it. */
private val NOTE_SPACING = 6.dp

/** The note shrinks rather than grow wider than this, e.g. "Sol♯". */
private val BIG_NOTE_MAX_WIDTH = 260.dp

/** The "in tune" glow fades in and out over this. */
private const val GLOW_MS = 600

/** The note grows to [POP_SCALE] over this when it becomes in tune, then springs back. */
private const val POP_MS = 110
private const val POP_SCALE = 1.08f

@Preview(showBackground = true)
@Composable
private fun NoteDisplayPreview() {
    TwangleTheme(darkTheme = false) {
        val readout = NoteReadout(noteName = "E", octave = 2, frequencyHz = 81.84f, cents = -3f, inTune = true)
        NoteDisplay(readout, word = { TuneWordLine(readout, readout.hint, chromatic = false, quiet = "") })
    }
}
