package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.NumberFont
import io.github.klaboukes.twangle.core.designsystem.PacedSpeech
import io.github.klaboukes.twangle.core.designsystem.SCREEN_TINT_SHARE
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.designsystem.pacedSpeech
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.designsystem.rememberPacedSpeech
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.designsystem.tintedBackground
import io.github.klaboukes.twangle.core.dsp.speech.SpokenSide
import io.github.klaboukes.twangle.feature.tuner.R
import io.github.klaboukes.twangle.feature.tuner.ReceptionTip
import io.github.klaboukes.twangle.feature.tuner.TuneWord
import io.github.klaboukes.twangle.feature.tuner.TunerHint
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags
import io.github.klaboukes.twangle.feature.tuner.tuneWordFor
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The word under the lens (2.0, Phase 71; docs/architecture/v2-plan.md, *The lens*): one word that says what to do
 * ([TuneWord], [tuneWordFor]: Tighten · Loosen · In tune, Too low · Too high in Any note, [chromatic]) for the
 * [readout] heard (null: none), and under it, small, the cents ("12 cents flat"; in tune only how many, "2 cents",
 * since the word already says in tune). While nothing is heard it says [quiet] ("Play a string", "Play the A2
 * string", "Playing D3") in a quieter face, the line under it empty. The tuner's [hint] is already held steady
 * ([SteadyHint], so the word never flickers); while a heard note's hint hasn't settled the word stays what it was.
 *
 * Its colour is the reading's [tone] (the [LiveTone] the lens and the screen's tint share: muted at rest, amber far
 * off easing toward green, green in tune), read while drawing, so a reading recolours it without recomposing; on a
 * light background it is taken a little toward the ink, as much as the amber needs to read ([wordInkShare]). A
 * [tip] about the sound ("Play a bit louder") takes the small line's place, in the accent colour.
 *
 * For TalkBack it is the tuner's one speaking item (the lens and the ruler are silent): a polite live region that
 * says "E 2, 12 cents flat, Tighten", "E 2, in tune", "A 3, 8 cents sharp, Too high" (the [tip] in the word's place,
 * then the trail's [motion]: "…, Tighten, wavering"), or [spokenQuiet] without a note, paced by [speech] so it
 * doesn't talk over itself (pass the screen's one [PacedSpeech]). Each line keeps its height whatever it says, and
 * keeps to one line, set smaller where it doesn't fit, so nothing under it moves. [compact] sets both smaller.
 */
@Composable
fun TuneWordLine(
    readout: NoteReadout?,
    hint: TunerHint?,
    chromatic: Boolean,
    quiet: String,
    modifier: Modifier = Modifier,
    spokenQuiet: String = quiet,
    tip: ReceptionTip? = null,
    motion: String? = null,
    tone: LiveTone? = null,
    compact: Boolean = false,
    speech: PacedSpeech = rememberPacedSpeech(),
) {
    val held = remember { HeldWord() }
    val word = tuneWordFor(heard = readout != null, hint = hint, chromatic = chromatic, previous = held.word)
    // While a heard note's word hasn't settled, "Play a string" stays as it was said, not an empty line.
    val quietShown = if (readout == null) quiet else held.quiet
    SideEffect {
        held.word = word
        if (readout == null) held.quiet = quiet
    }
    val wordText = when (word) {
        TuneWord.PLAY -> quietShown
        TuneWord.IN_TUNE -> inTuneLabel()
        else -> stringResource(word.label)
    }
    val tipText = tip?.let { stringResource(it.label) }
    val small = when {
        tipText != null -> tipText
        readout == null -> ""
        readout.inTune -> centsAmountText(readout.cents)
        else -> centsText(readout.cents)
    }
    val spoken = pacedSpeech(
        text = if (readout != null) spokenReading(readout, wordText.takeIf { word.isDirection }, tipText, motion) else tipText ?: spokenQuiet,
        key = readout?.let(::spokenReadingKey),
        speech = speech,
    )
    val colors = MaterialTheme.colorScheme
    val tokens = MaterialTheme.styleTokens
    val ink = colors.onBackground
    val background = colors.background
    val inkShare = remember(tokens.farOff, tokens.inTune, ink, background) { wordInkShare(tokens.farOff, tokens.inTune, ink, background) }
    val muted = colors.onSurfaceVariant
    // The cents under the word, readable on the tinted screen too.
    val quietInk = remember(muted, ink, background, tokens.farOff, tokens.inTune) {
        mixColour(muted, ink, mutedInkShare(muted, ink, background, tokens.farOff, tokens.inTune))
    }
    val wordColour = remember(tone, ink, inkShare, muted) {
        ColorProducer { mixColour(tone?.colour ?: muted, ink, inkShare) }
    }
    val big = if (compact) COMPACT_WORD_SIZE else WORD_SIZE
    val wordStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = big, lineHeight = WORD_LINE_HEIGHT, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    val quietStyle = wordStyle.copy(fontSize = if (compact) COMPACT_QUIET_SIZE else QUIET_SIZE, fontWeight = FontWeight.SemiBold)
    val shownStyle = if (word == TuneWord.PLAY) quietStyle else wordStyle
    val smallStyle = MaterialTheme.typography.bodyMedium.let { if (tipText == null && readout != null) it.copy(fontFamily = NumberFont) else it }
    val wordLine = rememberLineHeight(wordStyle)
    val smallLine = rememberLineHeight(MaterialTheme.typography.bodyMedium)
    val density = LocalDensity.current
    // The texts are hidden from TalkBack; this layer under them speaks for both, so nothing changing inside can
    // make TalkBack read it again early.
    Box(modifier.testTag(TunerTestTags.WORD), contentAlignment = Alignment.Center, propagateMinConstraints = true) {
        Box(
            Modifier
                .matchParentSize()
                .semantics {
                    contentDescription = spoken
                    // Not for "Play a string" said again after a pluck dies away (PacedSpeech.announced).
                    if (speech.announced) liveRegion = LiveRegionMode.Polite
                },
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(WORD_SPACING)) {
            BasicText(
                text = musicText(wordText),
                style = shownStyle,
                color = wordColour,
                maxLines = 1,
                // Never wrapped, so the auto-size sees a word too wide (Ear training's AnswerButton in IntervalsPart.kt says why).
                softWrap = false,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = with(density) { WORD_MIN_SIZE.toSp() },
                    maxFontSize = shownStyle.fontSize,
                    stepSize = 1.sp,
                ),
                modifier = Modifier
                    .keepLineHeight(wordLine)
                    .testTag(TunerTestTags.WORD_TEXT)
                    .semantics { hideFromAccessibility() },
            )
            Text(
                text = small,
                style = smallStyle,
                color = if (tipText != null) colors.primary else quietInk,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = SMALL_MIN_SIZE, maxFontSize = smallStyle.fontSize, stepSize = 1.sp),
                modifier = Modifier
                    .keepLineHeight(smallLine)
                    .testTag(TunerTestTags.WORD_CENTS)
                    .semantics { hideFromAccessibility() },
            )
        }
    }
}

/** The word and the quiet text last shown, so a heard note's word that hasn't settled keeps them (not state: nothing redraws for it). */
private class HeldWord {
    var word: TuneWord? = null
    var quiet: String = ""
}

/**
 * How far the word's colour is taken toward the [ink] so every tone it can have (the amber [farOff] eased toward the
 * [green]) reads at the app's text contrast (WCAG AA, [WORD_CONTRAST]) on [background] tinted toward that same tone
 * as far as the screen's tint goes ([SCREEN_TINT_SHARE]): 0 on a dark background, where the tones are bright
 * already; on a light one the least share, in steps of [INK_STEP], that is enough for the lightest (the amber).
 * The same share is taken of every tone, so the word keeps its hue as it changes.
 */
internal fun wordInkShare(farOff: Color, green: Color, ink: Color, background: Color): Float {
    var most = 0f
    for (step in 0..TONE_STEPS) {
        val tone = mixColour(farOff, green, step.toFloat() / TONE_STEPS)
        most = maxOf(most, inkShareToRead(tone, ink, tintedBackground(background, tone, 1f)))
    }
    return most
}

/**
 * How far a quiet line's [muted] colour (the cents under the word) is taken toward the [ink] so it still reads on
 * [background] at the screen's strongest tint toward any tone from [farOff] to [green] (on a light background
 * 14 % of the green brings the muted colour to 3.9 : 1); 0 where it reads already.
 */
internal fun mutedInkShare(muted: Color, ink: Color, background: Color, farOff: Color, green: Color): Float {
    var most = 0f
    for (step in 0..TONE_STEPS) {
        val tone = mixColour(farOff, green, step.toFloat() / TONE_STEPS)
        most = maxOf(most, inkShareToRead(muted, ink, tintedBackground(background, tone, 1f)))
    }
    return most
}

/** The least share of the way from [text] to [ink], in steps of [INK_STEP], that reads at [WORD_CONTRAST] on [behind]. */
private fun inkShareToRead(text: Color, ink: Color, behind: Color): Float {
    var share = 0f
    while (share < 1f && contrast(mixColour(text, ink, share), behind) < WORD_CONTRAST) share += INK_STEP
    return share.coerceAtMost(1f)
}

/** The tones checked between the amber and the green. */
private const val TONE_STEPS = 10

/** WCAG contrast ratio of two colours, from 1 to 21. */
internal fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/** The word's least contrast on the screen: the app's text rule (StylePaletteTest). */
private const val WORD_CONTRAST = 4.5f
private const val INK_STEP = 0.05f

/**
 * The waiting line of the tuner's tool views (Phase 63's review: until then the strum check and chord name said it
 * smaller in full ink, the 12th-fret check big inside its panel): what to play, in the note's place, in the same
 * form as the tuner's waiting: the display face at the idle hint's size ([compact] smaller), muted, on at most
 * two lines, set smaller where they don't fit (a long language at 2× text). (Moved here from the big note's file in
 * Phase 71: the tool views keep it after the big note goes.)
 */
@Composable
internal fun WaitingLine(text: String, compact: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val style = MaterialTheme.typography.displayLarge.copy(
        fontSize = if (compact) COMPACT_HINT_SIZE else HINT_SIZE,
        lineHeight = HINT_LINE_HEIGHT,
    )
    Text(
        text = musicText(text),
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = HINT_MAX_LINES,
        autoSize = TextAutoSize.StepBased(
            minFontSize = with(density) { HINT_MIN_SIZE.toSp() },
            maxFontSize = style.fontSize,
            stepSize = 1.sp,
        ),
        modifier = modifier.widthIn(max = HINT_MAX_WIDTH),
    )
}

/** The tool views' waiting line: in the display face, at a reading size that grows with the system's text size. */
private val HINT_SIZE = 32.sp
private val COMPACT_HINT_SIZE = 24.sp
private val HINT_LINE_HEIGHT = 1.2.em
private val HINT_MIN_SIZE = 10.dp
private const val HINT_MAX_LINES = 2

/** Wider than a note may grow, so a long waiting line takes two lines rather than three. */
private val HINT_MAX_WIDTH = 300.dp

/** The word (the mock-up's 26 px bold), the quiet "Play a string" (20 px), each smaller [compact]. */
private val WORD_SIZE = 26.sp
private val COMPACT_WORD_SIZE = 22.sp
private val QUIET_SIZE = 20.sp
private val COMPACT_QUIET_SIZE = 18.sp
private val WORD_LINE_HEIGHT = 1.25.em

/** A word or tip that doesn't fit one line is set smaller, down to these. */
private val WORD_MIN_SIZE = 12.dp
private val SMALL_MIN_SIZE = 10.sp

/** Between the word and the cents under it. */
private val WORD_SPACING = 2.dp

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

/** "2 cents", "1 cent", "0 cents": how far off without which way, for under "In tune". */
@Composable
internal fun centsAmountText(cents: Float): String {
    val amount = abs(cents.roundToInt())
    return if (amount == 0) stringResource(R.string.tuner_cents_zero) else pluralStringResource(R.plurals.tuner_cents_off, amount, amount)
}

/** "In tune" as the word: TalkBack's words for it (`tuner_in_tune`, "in tune", "zuiver", "gestimmt"), with a capital since it stands alone. */
@Composable
internal fun inTuneLabel(): String {
    val word = stringResource(R.string.tuner_in_tune)
    val locale = LocalConfiguration.current.locales[0]
    return remember(word, locale) { capitalised(word, locale) }
}

/** [text] with its first letter as a capital in [locale] ("akortlu" → "Akortlu"); scripts without capitals stay as they are. */
internal fun capitalised(text: String, locale: Locale): String = text.replaceFirstChar { it.titlecase(locale) }

/** The word's text. */
private val TuneWord.label: Int
    get() = when (this) {
        TuneWord.TIGHTEN -> R.string.tuner_word_tighten
        TuneWord.LOOSEN -> R.string.tuner_word_loosen
        TuneWord.TOO_LOW -> R.string.tuner_word_too_low
        TuneWord.TOO_HIGH -> R.string.tuner_word_too_high
        TuneWord.PLAY, TuneWord.IN_TUNE -> error("$this has no word of its own")
    }

/** A tip as it is shown and said. */
internal val ReceptionTip.label: Int
    get() = when (this) {
        ReceptionTip.TOO_SOFT -> R.string.tuner_tip_too_soft
        ReceptionTip.TOO_NOISY -> R.string.tuner_tip_too_noisy
        ReceptionTip.TOO_LOUD -> R.string.tuner_tip_too_loud
    }

/**
 * The situation a spoken reading belongs to, for its pacing ([PacedSpeech]): the note and which side
 * of it the string is, flat, sharp or in tune. A new one is said after 400 ms; only the cents moving
 * at most every 2 s. With the side in it, turning a string past the note ("12 cents flat" to "5 cents
 * sharp") is said as soon as a new note would be, not up to 2 s late (Phase 65). The watch keys its
 * reading the same way ([SpokenSide]).
 */
internal fun spokenReadingKey(readout: NoteReadout): String =
    "${readout.noteName}${readout.octave}/${SpokenSide.of(readout.cents.roundToInt(), readout.inTune)}"

/**
 * "E 2, 12 cents flat, Tighten", "C sharp 4, in tune": the readout as a screen reader says it, since TalkBack
 * can't see the lens. The [word] (only a direction: in tune is said already) is added, or a [tip] in its place:
 * "E 2, 12 cents flat, Play a bit louder"; then the trail's [motion]: "E 2, 12 cents flat, Tighten, wavering".
 */
@Composable
private fun spokenReading(readout: NoteReadout, word: String?, tip: String?, motion: String?): String {
    val note = rememberNoteNameSpeech().name(readout.noteName)
    val state = if (readout.inTune) stringResource(R.string.tuner_in_tune) else centsText(readout.cents)
    val first = tip ?: word
    val extra = when {
        first != null && motion != null -> first + stringResource(R.string.tuner_spoken_separator) + motion
        else -> first ?: motion
    }
    return if (extra == null) {
        stringResource(R.string.tuner_spoken_reading, note, readout.octave, state)
    } else {
        stringResource(R.string.tuner_spoken_reading_hint, note, readout.octave, state, extra)
    }
}

/** The height in px of one line of [style] at its own size, as a [Text] in it would have. */
@Composable
internal fun rememberLineHeight(style: TextStyle): Int {
    val measurer = rememberTextMeasurer(cacheSize = 1)
    val merged = LocalTextStyle.current.merge(style)
    return remember(measurer, merged) { measurer.measure(" ", merged, maxLines = 1).size.height }
}

/**
 * Keeps a text exactly [height] px tall (the line it stands in, from [rememberLineHeight]), with the
 * text centred in it. A text set smaller to fit needs this: with Android 14's non-linear font scaling,
 * Compose keeps an sp line height in proportion to the font size, and a smaller font is enlarged by a
 * larger factor, so the shrunk line would come out a pixel or two taller and push what is under it.
 * Nothing is clipped: a slightly taller text just reaches past the slot.
 */
internal fun Modifier.keepLineHeight(height: Int): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val slot = constraints.constrainHeight(height)
    layout(placeable.width, slot) { placeable.place(0, (slot - placeable.height) / 2) }
}
