package io.github.klaboukes.twangle.feature.tuner.components

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.TwangleTheme
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.designsystem.PacedSpeech
import io.github.klaboukes.twangle.core.designsystem.numberFontFor
import io.github.klaboukes.twangle.core.designsystem.pacedSpeech
import io.github.klaboukes.twangle.core.designsystem.rememberPacedSpeech
import io.github.klaboukes.twangle.core.designsystem.rememberAnimationsOff
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.settings.TunerDisplay
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.core.settings.WaveLook
import io.github.klaboukes.twangle.feature.tuner.InTuneTickRule
import io.github.klaboukes.twangle.feature.tuner.R
import io.github.klaboukes.twangle.feature.tuner.ReceptionTip
import io.github.klaboukes.twangle.feature.tuner.SteadyHint
import io.github.klaboukes.twangle.feature.tuner.TunerHint
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags
import io.github.klaboukes.twangle.feature.tuner.tunerHintFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
 * colour, a soft glow lights up behind it with a small pop, and a small check mark shows at the pill's
 * start, so the state doesn't rely on colour alone (colour-blind players see it too). With the needle's
 * cents the pill also fills with that colour; in the wave view the pill stays clear, the check is
 * drawn in the "in tune" colour, and the wave holds still on the note. Without a [readout] it shows a
 * faint ♪ and [hint], e.g. "Play a string". [reception] (read while drawing) makes the wave crisp or
 * faint and soft by how well the note is heard. A ♯ or ♭ is set smaller and raised, like in sheet
 * music ([musicText]).
 *
 * With [showTuneHint], an off-pitch note also says which way to turn the peg ("Tune up ↑" /
 * "Tune down ↓", the readout's [NoteReadout.hint], held steady by [SteadyHint] so it doesn't flicker
 * near the note); in tune it says nothing extra. With a picture the hint
 * fills the otherwise empty pill; with cents it gets its own line under the pill, which keeps its
 * height while empty so nothing jumps when the note comes in tune.
 *
 * A [tip] about the sound ("Play a bit louder", "Too noisy here", "Too loud, move the phone back")
 * takes the place of something less needed at that moment, so nothing is added to the screen and
 * nothing moves: without a note it replaces [hint]; with the wave it fills the pill instead of the
 * direction; with the needle it replaces the frequency, so "Tune up" / "Tune down" stays in view.
 * TalkBack says it instead of the direction. A tip keeps to one line, set smaller when it doesn't
 * fit, so a long one ("Te hard, leg de telefoon verder weg") never pushes the note up.
 *
 * The glow and the pop are read only while drawing, so they animate without recomposing.
 * For TalkBack the whole block is one polite live region that says e.g. "E 2, 12 cents flat" or
 * "E 2, in tune" (with [showTuneHint] also "E 2, 12 cents flat, tune up"), paced by [speech] so it
 * doesn't talk over itself; pass the same [speech] to every [NoteDisplay] the screen switches between,
 * so the pacing carries over from a note to "Play a string". Note names are spoken through
 * [rememberNoteNameSpeech] ("C sharp 4", in Dutch "cis 4"); without a readout it says [spokenHint].
 * The live region is a layer of its own under the block, so nothing changing inside the block can
 * make TalkBack read it again early.
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
    showTuneHint: Boolean = false,
    reception: () -> Float? = { null },
    tip: ReceptionTip? = null,
    speech: PacedSpeech = rememberPacedSpeech(),
) {
    val inTune = readout?.inTune == true
    // The wave shows how far off the note is in a picture instead of in cents.
    val pictured = display != TunerDisplay.NEEDLE
    val direction = if (showTuneHint) readout?.hint?.takeIf { it.isDirection } else null
    val directionText = direction?.let { stringResource(it.label) }.orEmpty()
    val tipText = tip?.let { stringResource(it.label) }
    val colors = MaterialTheme.colorScheme
    val tuneColor = inTuneColor()
    val noteColor by animateInTuneColor(inTune, colors.onSurface, "noteColor")
    // In the wave view too: the wave holding still says it as well, but a mark says it to everyone.
    val checkShown by animateFloatAsState(if (inTune) 1f else 0f, label = "check")
    val glow = animateFloatAsState(if (inTune) 1f else 0f, tween(GLOW_MS), label = "glow")
    val pop = remember { Animatable(1f) }
    LaunchedEffect(inTune) {
        if (inTune) {
            pop.animateTo(POP_SCALE, tween(POP_MS))
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }
    val pillBase = animateColorAsState(
        if (readout == null || pictured) Color.Transparent else colors.surfaceContainerHigh.copy(alpha = 0.85f),
        label = "pill",
    )
    // The "in tune" colour over the pill: a little as the note comes close ([pillTint]), all of it in
    // tune. A slow spring, so it glides with the note instead of following every jitter of the cents.
    val pillFill = animateFloatAsState(
        when {
            readout == null || pictured -> 0f
            inTune -> 1f
            else -> pillTint(readout.cents)
        },
        spring(stiffness = Spring.StiffnessVeryLow),
        label = "pillFill",
    )
    // One soft sheen across the pill as the note gets in tune. A real string drifts a few cents and
    // drops in and out of tune, so the same note shines again only after [SHEEN_QUIET_MILLIS] out of
    // tune (InTuneTickRule, as the haptic tick but with a longer quiet time); another note shines at
    // once. Launched apart from the effect, so the note leaving tune doesn't freeze it half way.
    val sheen = remember { Animatable(1f) }
    val sheenScope = rememberCoroutineScope()
    val animationsOff = rememberAnimationsOff()
    val sheenRule = rememberSaveable(saver = SHEEN_RULE_SAVER) {
        InTuneTickRule(initiallyInTune = inTune, quietMillis = SHEEN_QUIET_MILLIS)
    }
    val sheenTarget = readout?.let { "${it.noteName}${it.octave}" }
    val currentSheenTarget by rememberUpdatedState(sheenTarget)
    val sheenShown by rememberUpdatedState(!pictured && !animationsOff)
    LaunchedEffect(inTune) {
        if (sheenRule.update(inTune, currentSheenTarget, SystemClock.uptimeMillis()) && sheenShown) {
            sheenScope.launch {
                sheen.snapTo(0f)
                delay(SHEEN_DELAY_MS)
                sheen.animateTo(1f, tween(SHEEN_MS, easing = FastOutSlowInEasing))
            }
        }
    }
    // The text switches (with a short fade) to whichever of the normal and the "in tune" text colour
    // reads better on the fill behind it at that moment; a blend of the two would be grey on
    // mid-green. It can only switch past the "getting close" tint, so the cents never change colour
    // while the note is off. Derived, so the fill's gliding doesn't recompose the rest.
    val onSurface = colors.onSurface
    val onTune = onInTuneColor()
    val pillSurface = colors.surfaceContainerHigh
    val pillTextTarget by remember(onSurface, onTune, pillSurface, tuneColor) {
        derivedStateOf {
            val fill = pillFill.value
            val behind = lerp(pillSurface, tuneColor, bloomCentre(fill))
            if (fill > PILL_NEAR_TINT && contrast(onTune, behind) > contrast(onSurface, behind)) onTune else onSurface
        }
    }
    val pillText by animateColorAsState(pillTextTarget, tween(PILL_TEXT_MS), label = "pillText")
    val spoken = pacedSpeech(
        text = if (readout != null) spokenReading(readout, direction, tipText) else tipText ?: spokenHint,
        key = readout?.let { "${it.noteName}${it.octave}/${it.inTune}" },
        speech = speech,
    )
    val density = LocalDensity.current
    val noteSize = if (compact) COMPACT_NOTE_SIZE else BIG_NOTE_SIZE
    val placeholder = stringResource(R.string.tuner_placeholder_note)
    val noteText = remember(readout?.noteName, placeholder) {
        // Spaces in the style's face around the ♪ give its line the height of a note's line, so
        // nothing moves when a note comes in or goes.
        musicText(readout?.noteName ?: " $placeholder ")
    }
    // The block takes the size it is given, as it did before it had a layer under it.
    Box(modifier, propagateMinConstraints = true) {
        // The texts below are hidden from TalkBack; this layer speaks for them. It lies under the
        // block, not around it, so nothing inside the block is part of the live region.
        Box(
            Modifier
                .matchParentSize()
                .semantics {
                    contentDescription = spoken
                    liveRegion = LiveRegionMode.Polite
                },
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
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
            }
            val pictureSize = Modifier.size(WAVE_WIDTH, if (compact) COMPACT_WAVE_HEIGHT else WAVE_HEIGHT)
            when (display) {
                TunerDisplay.WAVE -> WaveView(
                    cents = readout?.cents,
                    inTune = inTune,
                    modifier = pictureSize.testTag(TunerTestTags.WAVE),
                    look = waveLook,
                    reception = reception,
                )
                TunerDisplay.NEEDLE -> Unit
            }
            // In the wave view the pill is empty in tune (unless a tip shows): there the check mark sits in
            // its middle, under the wave, instead of at its start. Where it sat in tune it stays while it
            // fades out, so it doesn't jump aside as the direction hint comes back.
            val centredInTune = pictured && (tipText ?: directionText).isEmpty()
            var checkCentred by remember { mutableStateOf(centredInTune) }
            if (inTune && checkCentred != centredInTune) checkCentred = centredInTune
            if (!pictured && checkCentred) checkCentred = false
            val check = @Composable { shown: Boolean ->
                Icon(
                    painter = painterResource(R.drawable.ic_check_circle),
                    contentDescription = null,
                    // Over the clear pill of the wave view, in the "in tune" colour; over the filled pill, in its text colour.
                    tint = if (pictured) tuneColor else pillText,
                    modifier = Modifier
                        .size(CHECK_SIZE)
                        .graphicsLayer {
                            val amount = if (shown) checkShown else 0f
                            alpha = amount
                            scaleX = 0.5f + 0.5f * amount
                            scaleY = 0.5f + 0.5f * amount
                        },
                )
            }
            Box(contentAlignment = Alignment.Center) {
                // The pill's fill, clipped to its shape on a layer of its own.
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(MaterialTheme.shapes.small)
                        .drawBehind {
                            drawRect(pillBase.value)
                            drawPillBloom(tuneColor, pillFill.value)
                            drawPillSheen(sheen.value)
                        },
                )
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // The check mark's slot is mirrored on the right, so the text stays centred.
                    check(!checkCentred)
                    val pillStyle = when {
                        pictured -> MaterialTheme.typography.titleMedium
                        readout != null -> MaterialTheme.typography.titleLarge.copy(fontFamily = numberFontFor(MaterialTheme.styleTokens.style))
                        else -> MaterialTheme.typography.titleLarge
                    }
                    val tipInPill = tipText != null && (readout == null || pictured)
                    val pillLine = rememberLineHeight(pillStyle)
                    // As wide as the widest cents text, so the pill keeps its size while the cents change.
                    val centsWidth = if (readout != null && !pictured) rememberCentsTextWidth(pillStyle) else 0.dp
                    Text(
                        text = when {
                            // The hint can name a note: "Play the C♯ string".
                            readout == null -> if (tipText != null) AnnotatedString(tipText) else musicText(hint)
                            // Empty (or the hint) but still there, so the screen doesn't jump when a note starts or stops.
                            pictured -> AnnotatedString(tipText ?: directionText)
                            else -> AnnotatedString(centsText(readout.cents))
                        },
                        style = pillStyle,
                        color = when {
                            readout == null -> colors.onSurface
                            pictured -> colors.primary
                            else -> pillText
                        },
                        textAlign = TextAlign.Center,
                        maxLines = if (tipInPill) 1 else Int.MAX_VALUE,
                        autoSize = if (tipInPill) tipAutoSize(pillStyle.fontSize) else null,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .widthIn(min = centsWidth)
                            .then(if (tipInPill) Modifier.keepLineHeight(pillLine) else Modifier)
                            .hiddenFromTalkBack(),
                    )
                    Spacer(Modifier.size(CHECK_SIZE))
                }
                if (pictured) check(checkCentred)
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
                val tipInLine = tipText != null && readout != null
                val frequencyLine = rememberLineHeight(MaterialTheme.typography.bodyLarge)
                Text(
                    text = when {
                        readout == null -> ""
                        tipText != null -> tipText
                        else -> stringResource(R.string.tuner_frequency, readout.frequencyHz)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (tipInLine) colors.primary else colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    autoSize = if (tipInLine) tipAutoSize(MaterialTheme.typography.bodyLarge.fontSize) else null,
                    modifier = Modifier
                        .then(if (tipInLine) Modifier.keepLineHeight(frequencyLine) else Modifier)
                        .hiddenFromTalkBack(),
                )
            }
        }
    }
}

/**
 * The note in focus mode's corner, beside the full-screen meter: its name and octave at [FOCUS_NOTE_SIZE]
 * (under half the big note's size) and nothing else while a note sounds, since the meter shows how far
 * off it is. In tune it turns the "in tune" colour. Without a note, [hint] ("Play a string") stands in
 * its place; a [tip] about the sound ("Play a bit louder") goes in a line under it. TalkBack hears it
 * as [NoteDisplay] (one polite live region, paced by [speech]).
 */
@Composable
fun FocusNote(
    readout: NoteReadout?,
    hint: String,
    modifier: Modifier = Modifier,
    spokenHint: String = hint,
    showTuneHint: Boolean = false,
    tip: ReceptionTip? = null,
    speech: PacedSpeech = rememberPacedSpeech(),
) {
    val colors = MaterialTheme.colorScheme
    val direction = if (showTuneHint) readout?.hint?.takeIf { it.isDirection } else null
    val tipText = tip?.let { stringResource(it.label) }
    val noteColor by animateInTuneColor(readout?.inTune == true, colors.onSurface, "focusNoteColor")
    val spoken = pacedSpeech(
        text = if (readout != null) spokenReading(readout, direction, tipText) else tipText ?: spokenHint,
        key = readout?.let { "${it.noteName}${it.octave}/${it.inTune}" },
        speech = speech,
    )
    val density = LocalDensity.current
    Box(modifier.testTag(TunerTestTags.FOCUS_NOTE), propagateMinConstraints = true) {
        // As in NoteDisplay: this layer speaks for the texts, which are hidden from TalkBack.
        Box(
            Modifier
                .matchParentSize()
                .semantics {
                    contentDescription = spoken
                    liveRegion = LiveRegionMode.Polite
                },
        )
        Column(Modifier.widthIn(max = FOCUS_NOTE_MAX_WIDTH), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (readout != null) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row {
                        Text(
                            text = remember(readout.noteName) { musicText(readout.noteName) },
                            // In dp, as the big note: it doesn't grow with the system font size.
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = with(density) { FOCUS_NOTE_SIZE.toSp() },
                                lineHeight = with(density) { (FOCUS_NOTE_SIZE * BIG_NOTE_LINE_RATIO).toSp() },
                            ),
                            color = noteColor,
                            maxLines = 1,
                            modifier = Modifier.alignByBaseline().hiddenFromTalkBack(),
                        )
                        Text(
                            text = readout.octave.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.alignByBaseline().hiddenFromTalkBack(),
                        )
                    }
                }
            } else if (tipText == null) {
                // The hint can name a note: "Play the C♯ string".
                Text(
                    text = musicText(hint),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.hiddenFromTalkBack(),
                )
            }
            if (tipText != null) {
                Text(
                    text = tipText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.primary,
                    modifier = Modifier.hiddenFromTalkBack(),
                )
            }
        }
    }
}

/** Left out for screen readers (the block's description says it), but still there for UI tests. */
private fun Modifier.hiddenFromTalkBack(): Modifier = semantics { hideFromAccessibility() }

private val FOCUS_NOTE_SIZE = 48.dp

/** Longer hints and tips wrap rather than reach far under the meter. */
private val FOCUS_NOTE_MAX_WIDTH = 240.dp

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

/** A tip set no larger than [maxSize], and smaller (down to [TIP_MIN_SIZE]) when it doesn't fit one line. */
private fun tipAutoSize(maxSize: TextUnit): TextAutoSize =
    TextAutoSize.StepBased(minFontSize = TIP_MIN_SIZE, maxFontSize = maxSize, stepSize = 1.sp)

private val TIP_MIN_SIZE = 11.sp

/** The height in px of one line of [style] at its own size, as a [Text] in it would have. */
@Composable
private fun rememberLineHeight(style: TextStyle): Int {
    val measurer = rememberTextMeasurer(cacheSize = 1)
    val merged = LocalTextStyle.current.merge(style)
    return remember(measurer, merged) { measurer.measure(" ", merged, maxLines = 1).size.height }
}

/**
 * The width of the widest cents text ([centsText]) from 0 to 99 cents, flat or sharp, in [style] and
 * the current language. The pill takes this width while it shows cents: sized to each reading, it
 * grew and shrank with every change ("9 cents flat", "12 cents sharp"), which looked like flicker.
 * Measured once per language, style and font size; a reading of 100 cents or more may widen it.
 */
@Composable
private fun rememberCentsTextWidth(style: TextStyle): Dp {
    val measurer = rememberTextMeasurer(cacheSize = 1)
    val merged = LocalTextStyle.current.merge(style)
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    return remember(measurer, merged, resources, configuration, density) {
        fun width(text: String) = measurer.measure(text, merged, maxLines = 1).size.width
        var widest = width(resources.getString(R.string.tuner_cents_zero))
        for (amount in 1..MAX_STEADY_CENTS) {
            widest = maxOf(
                widest,
                width(resources.getQuantityString(R.plurals.tuner_cents_flat, amount, amount)),
                width(resources.getQuantityString(R.plurals.tuner_cents_sharp, amount, amount)),
            )
        }
        with(density) { widest.toDp() }
    }
}

/** Cents up to this keep the pill at one width; more (a string far off) may widen it. */
private const val MAX_STEADY_CENTS = 99

/**
 * Keeps a text exactly [height] px tall (the line it replaces, from [rememberLineHeight]), with the
 * text centred in it. A tip set smaller to fit ([tipAutoSize]) needs this: with Android 14's
 * non-linear font scaling, Compose keeps an sp line height in proportion to the font size, and a
 * smaller font is enlarged by a larger factor, so the shrunk line would come out a pixel or two
 * taller and push the note up. Nothing is clipped: a slightly taller text just reaches past the slot.
 */
private fun Modifier.keepLineHeight(height: Int): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val slot = constraints.constrainHeight(height)
    layout(placeable.width, slot) { placeable.place(0, (slot - placeable.height) / 2) }
}

/**
 * The same note sheens again only after this long out of tune: a real string drifts a few cents and
 * drops in and out of tune while it rings, which shouldn't keep restarting the sheen.
 */
private const val SHEEN_QUIET_MILLIS = 6_000L

/** The sheen starts this long after the note gets in tune, as the pill fills, and crosses it in [SHEEN_MS]. */
private const val SHEEN_DELAY_MS = 250L
private const val SHEEN_MS = 900

private val SHEEN_RULE_SAVER = Saver<InTuneTickRule, Boolean>(
    save = { it.wasInTune },
    restore = { InTuneTickRule(initiallyInTune = it, quietMillis = SHEEN_QUIET_MILLIS) },
)

/** The pill's text fades to its other colour over this. */
private const val PILL_TEXT_MS = 150

/** WCAG contrast ratio of two colours, from 1 to 21. */
private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

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

/** A tip as it is shown and said. */
private val ReceptionTip.label: Int
    get() = when (this) {
        ReceptionTip.TOO_SOFT -> R.string.tuner_tip_too_soft
        ReceptionTip.TOO_NOISY -> R.string.tuner_tip_too_noisy
        ReceptionTip.TOO_LOUD -> R.string.tuner_tip_too_loud
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
 * "E 2, 12 cents flat, tune up". A [tip] is said in its place: "E 2, 12 cents flat, Play a bit louder".
 */
@Composable
private fun spokenReading(readout: NoteReadout, direction: TunerHint?, tip: String?): String {
    val note = rememberNoteNameSpeech().name(readout.noteName)
    val state = if (readout.inTune) stringResource(R.string.tuner_in_tune) else centsText(readout.cents)
    val extra = tip ?: direction?.let { stringResource(it.spokenLabel) }
    return if (extra == null) {
        stringResource(R.string.tuner_spoken_reading, note, readout.octave, state)
    } else {
        stringResource(R.string.tuner_spoken_reading_hint, note, readout.octave, state, extra)
    }
}

@Preview(showBackground = true)
@Composable
private fun NoteDisplayPreview() {
    TwangleTheme(style = VisualStyle.CLASSIC, darkTheme = false) {
        NoteDisplay(NoteReadout(noteName = "E", octave = 2, frequencyHz = 81.84f, cents = -3f, inTune = true), hint = "")
    }
}
