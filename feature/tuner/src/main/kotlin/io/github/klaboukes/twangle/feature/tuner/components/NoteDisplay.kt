package io.github.klaboukes.twangle.feature.tuner.components

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.designsystem.numberFontFor
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.settings.TunerDisplay
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.settings.WaveLook
import io.github.klaboukes.twangle.feature.tuner.R
import io.github.klaboukes.twangle.feature.tuner.SpokenReadingThrottle
import io.github.klaboukes.twangle.feature.tuner.SteadyHint
import io.github.klaboukes.twangle.feature.tuner.TunerHint
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags
import io.github.klaboukes.twangle.feature.tuner.tunerHintFor
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** What [NoteDisplay] shows for a detected note. */
data class NoteReadout(
    val noteName: String,
    val octave: Int,
    val frequencyHz: Float,
    val cents: Float,
    val inTune: Boolean = false,
    /** The hint line's message, held steady by the tuner; by default the plain one for [cents]. */
    val hint: TunerHint? = tunerHintFor(cents, inTune),
)

/**
 * Big target note with its octave, then how far off it is ("12 cents flat") in a pill, and the
 * frequency. With the [display] set to the wave ([WaveView], in [waveLook], e.g. the strobe),
 * that picture replaces both: the dial already shows how far off the note is, so the pill only shows
 * [hint] and stays empty during a note. [compact] makes the note and the picture smaller, for short spaces such as a phone on its side. The note is set in the style's display face and shrinks to fit when a face or a
 * solfège name ("Sol♯") is wide. While the readout is in tune the note turns the style's "in tune"
 * colour, a soft glow lights up behind it with a small pop, and (with the needle's cents) the pill fills
 * with that colour and shows a check mark, so the state doesn't rely on colour alone; in the wave
 * view the wave settling into its calm shape and standing still does that. Without a [readout] it shows a
 * faint ♪ and [hint], e.g. "Play a string". A ♯ or ♭ is set smaller and raised, like in sheet
 * music ([musicText]).
 *
 * With [showTuneHint], an off-pitch note also says which way to turn the peg ("Tune up ↑" /
 * "Tune down ↓", the readout's [NoteReadout.hint], held steady by [SteadyHint] so it doesn't flicker
 * near the note); in tune it says nothing extra. With a picture the hint
 * fills the otherwise empty pill; with cents it gets its own line under the pill, which keeps its
 * height while empty so nothing jumps when the note comes in tune.
 *
 * The glow and the pop are read only while drawing, so they animate without recomposing.
 * For TalkBack the whole block is one polite live region that says e.g. "E 2, 12 cents flat" or
 * "E 2, in tune" (with [showTuneHint] also "E 2, 12 cents flat, tune up"), throttled by
 * [SpokenReadingThrottle] so it doesn't talk over itself. Note names are spoken through
 * [rememberNoteNameSpeech] ("C sharp 4", in Dutch "cis 4"); without a readout it says [spokenHint].
 */
@Composable
fun NoteDisplay(
    readout: NoteReadout?,
    hint: String,
    modifier: Modifier = Modifier,
    spokenHint: String = hint,
    display: TunerDisplay = TunerDisplay.NEEDLE,
    compact: Boolean = false,
    waveLook: WaveLook = WaveLook.DEFAULT,
    waveBreathesInTune: Boolean = true,
    showTuneHint: Boolean = false,
) {
    val inTune = readout?.inTune == true
    // The wave shows how far off the note is in a picture instead of in cents.
    val pictured = display != TunerDisplay.NEEDLE
    val direction = if (showTuneHint) readout?.hint?.takeIf { it.isDirection } else null
    val directionText = direction?.let { stringResource(it.label) }.orEmpty()
    val colors = MaterialTheme.colorScheme
    val tuneColor = inTuneColor()
    val noteColor by animateInTuneColor(inTune, colors.onSurface, "noteColor")
    val checkShown by animateFloatAsState(if (inTune && !pictured) 1f else 0f, label = "check")
    val glow = animateFloatAsState(if (inTune) 1f else 0f, tween(GLOW_MS), label = "glow")
    val pop = remember { Animatable(1f) }
    LaunchedEffect(inTune) {
        if (inTune) {
            pop.animateTo(POP_SCALE, tween(POP_MS))
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }
    val pillColor = animateColorAsState(
        when {
            readout == null || pictured -> Color.Transparent
            inTune -> tuneColor
            else -> colors.surfaceContainerHigh.copy(alpha = 0.85f)
        },
        label = "pill",
    )
    val pillText by animateColorAsState(if (inTune) onInTuneColor() else colors.onSurface, label = "pillText")
    val spoken = rememberSpokenReading(
        text = if (readout != null) spokenReading(readout, direction) else spokenHint,
        key = readout?.let { "${it.noteName}${it.octave}/${it.inTune}" },
    )
    val density = LocalDensity.current
    val noteSize = if (compact) COMPACT_NOTE_SIZE else BIG_NOTE_SIZE
    val placeholder = stringResource(R.string.tuner_placeholder_note)
    val noteText = remember(readout?.noteName, placeholder) {
        // Spaces in the style's face around the ♪ give its line the height of a note's line, so
        // nothing moves when a note comes in or goes.
        musicText(readout?.noteName ?: " $placeholder ")
    }
    Column(
        // The texts below are hidden from TalkBack; this block speaks for them.
        modifier = modifier.semantics {
            contentDescription = spoken
            liveRegion = LiveRegionMode.Polite
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
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
                // Already huge, so it doesn't grow further with the system font size; that would
                // push the rest of the tuner off the screen.
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = with(density) { noteSize.toSp() },
                    lineHeight = with(density) { (noteSize * BIG_NOTE_LINE_RATIO).toSp() },
                ),
                autoSize = TextAutoSize.StepBased(
                    minFontSize = with(density) { BIG_NOTE_MIN_SIZE.toSp() },
                    maxFontSize = with(density) { noteSize.toSp() },
                    stepSize = with(density) { 4.dp.toSp() },
                ),
                color = if (readout != null) noteColor else colors.onSurfaceVariant.copy(alpha = 0.35f),
                maxLines = 1,
                modifier = Modifier
                    .alignByBaseline()
                    .widthIn(max = BIG_NOTE_MAX_WIDTH)
                    .hiddenFromTalkBack(),
            )
            if (readout != null) {
                Text(
                    text = readout.octave.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline().hiddenFromTalkBack(),
                )
            }
        }
        val pictureSize = Modifier.size(WAVE_WIDTH, if (compact) COMPACT_WAVE_HEIGHT else WAVE_HEIGHT)
        when (display) {
            TunerDisplay.WAVE -> WaveView(
                cents = readout?.cents,
                inTune = inTune,
                modifier = pictureSize.testTag(TunerTestTags.WAVE),
                look = waveLook,
                breathesInTune = waveBreathesInTune,
            )
            TunerDisplay.NEEDLE -> Unit
        }
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .drawBehind { drawRect(pillColor.value) }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The check mark's slot is mirrored on the right, so the text stays centred.
            Icon(
                painter = painterResource(R.drawable.ic_check_circle),
                contentDescription = null,
                tint = pillText,
                modifier = Modifier
                    .size(CHECK_SIZE)
                    .graphicsLayer {
                        alpha = checkShown
                        scaleX = 0.5f + 0.5f * checkShown
                        scaleY = 0.5f + 0.5f * checkShown
                    },
            )
            Text(
                text = when {
                    // The hint can name a note: "Play the C♯ string".
                    readout == null -> musicText(hint)
                    // Empty (or the hint) but still there, so the screen doesn't jump when a note starts or stops.
                    pictured -> AnnotatedString(directionText)
                    else -> AnnotatedString(centsText(readout.cents))
                },
                style = when {
                    pictured -> MaterialTheme.typography.titleMedium
                    readout != null -> MaterialTheme.typography.titleLarge.copy(fontFamily = numberFontFor(MaterialTheme.styleTokens.style))
                    else -> MaterialTheme.typography.titleLarge
                },
                color = when {
                    readout == null -> colors.onSurface
                    pictured -> colors.primary
                    else -> pillText
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false).hiddenFromTalkBack(),
            )
            Box(Modifier.size(CHECK_SIZE))
        }
        if (!pictured && showTuneHint) {
            // Always there (empty without a hint), so the frequency below doesn't move.
            Text(
                text = directionText,
                style = MaterialTheme.typography.titleMedium,
                color = colors.primary,
                modifier = Modifier.hiddenFromTalkBack(),
            )
        }
        if (!pictured) {
            Text(
                text = if (readout != null) stringResource(R.string.tuner_frequency, readout.frequencyHz) else "",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                modifier = Modifier.hiddenFromTalkBack(),
            )
        }
    }
}

/** Left out for screen readers (the block's description says it), but still there for UI tests. */
private fun Modifier.hiddenFromTalkBack(): Modifier = semantics { hideFromAccessibility() }

private val BIG_NOTE_SIZE = 112.dp
private val BIG_NOTE_MIN_SIZE = 56.dp
private const val BIG_NOTE_LINE_RATIO = 120f / 112f
private val COMPACT_NOTE_SIZE = 80.dp
private val CHECK_SIZE = 24.dp

/** The wave view: narrower than the note's pill can grow, and low, so it stays in the background. */
private val WAVE_WIDTH = 240.dp
private val WAVE_HEIGHT = 40.dp
private val COMPACT_WAVE_HEIGHT = 30.dp

/** The note shrinks rather than grow wider than this, e.g. "Sol♯" in the wide Space Age face. */
private val BIG_NOTE_MAX_WIDTH = 260.dp

/** The "in tune" glow fades in and out over this. */
private const val GLOW_MS = 600

/** The note grows to [POP_SCALE] over this when it becomes in tune, then springs back. */
private const val POP_MS = 110
private const val POP_SCALE = 1.08f

/** "12 cents flat", "1 cent sharp" or "0 cents", rounded to whole cents. */
@Composable
internal fun centsText(cents: Float): String {
    val rounded = cents.roundToInt()
    val amount = abs(rounded)
    return when {
        rounded < 0 -> pluralStringResource(R.plurals.tuner_cents_flat, amount, amount)
        rounded > 0 -> pluralStringResource(R.plurals.tuner_cents_sharp, amount, amount)
        else -> stringResource(R.string.tuner_cents_zero)
    }
}

/** The visible direction hint, e.g. "Tune up ↑" (only for [TunerHint.isDirection]). */
private val TunerHint.label: Int
    get() = when (this) {
        TunerHint.TUNE_UP -> R.string.tuner_tune_up
        TunerHint.TUNE_DOWN -> R.string.tuner_tune_down
        TunerHint.IN_TUNE -> error("in tune has no direction hint")
    }

/** The direction hint as TalkBack says it, without the arrow, e.g. "tune up". */
private val TunerHint.spokenLabel: Int
    get() = when (this) {
        TunerHint.TUNE_UP -> R.string.tuner_spoken_tune_up
        TunerHint.TUNE_DOWN -> R.string.tuner_spoken_tune_down
        TunerHint.IN_TUNE -> error("in tune has no direction hint")
    }

/**
 * "E 2, 12 cents flat", "C sharp 4, in tune": the readout as a screen reader says it, also in the
 * wave view, since TalkBack can't see the dial or the ribbon. A [direction] is added in words:
 * "E 2, 12 cents flat, tune up".
 */
@Composable
private fun spokenReading(readout: NoteReadout, direction: TunerHint?): String {
    val note = rememberNoteNameSpeech().name(readout.noteName)
    val state = if (readout.inTune) stringResource(R.string.tuner_in_tune) else centsText(readout.cents)
    return if (direction == null) {
        stringResource(R.string.tuner_spoken_reading, note, readout.octave, state)
    } else {
        stringResource(R.string.tuner_spoken_reading_hint, note, readout.octave, state, stringResource(direction.spokenLabel))
    }
}

/** [text], passed on to TalkBack no faster than [SpokenReadingThrottle] allows. */
@Composable
private fun rememberSpokenReading(text: String, key: Any?): String {
    val throttle = remember { SpokenReadingThrottle() }
    var spoken by remember { mutableStateOf("") }
    LaunchedEffect(text, key) {
        val wait = throttle.delayFor(text, key, SystemClock.uptimeMillis()) ?: return@LaunchedEffect
        if (wait > 0) delay(wait)
        throttle.publish(text, key, SystemClock.uptimeMillis())
        spoken = text
    }
    return spoken
}

@Preview(showBackground = true)
@Composable
private fun NoteDisplayPreview() {
    TwangleTheme(style = VisualStyle.CLASSIC, darkTheme = false) {
        NoteDisplay(NoteReadout(noteName = "E", octave = 2, frequencyHz = 81.84f, cents = -3f, inTune = true), hint = "")
    }
}
