package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.chord.ChordSpelling
import io.github.klaboukes.twangle.core.dsp.chord.SettledChord
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.speech.NoteNameSpeech
import io.github.klaboukes.twangle.feature.tuner.R
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags

/**
 * A fretted chord's name in the ruler's place (2.0, Phase 74; the fresh-start mock-up: "Strum a fretted chord and its
 * name appears where the string name is"): its symbol ("Am", "B♭7", a slash chord's bass smaller and muted: "C/E"),
 * big and bold as the ruler's heard note, centred in the ruler's height ([RULER_HEIGHT]), so nothing under it moves
 * as the chord comes and goes. Once its strum has died away it is [faded]. Its words ("A minor") are the word's line
 * under the lens, which TalkBack speaks; this is a drawing, as the ruler is, sized in dp, not by the font size. No
 * name reaches within [edgeClear] of either side (focus mode's exit button, as the ruler's). UI tests find it by
 * [TunerTestTags.CHORD_NAME] and read its symbol ([ChordSymbol]).
 */
@Composable
fun ChordInRuler(
    chord: SettledChord,
    notation: Notation,
    faded: Boolean,
    modifier: Modifier = Modifier,
    animationsOff: Boolean = false,
    edgeClear: Dp = 0.dp,
) {
    val colors = MaterialTheme.colorScheme
    val symbol = symbolText(chord, notation, colors.onSurfaceVariant)
    val alpha by animateFloatAsState(
        targetValue = if (faded) FADED_ALPHA else 1f,
        animationSpec = if (animationsOff) snap() else tween(FADE_MILLIS),
        label = "chordFade",
    )
    val density = LocalDensity.current
    // Sizes in dp, not sp: like the ruler it stands in, a drawing a 2× font would not fit.
    val style = TextStyle(
        fontSize = with(density) { SYMBOL_SIZE.toSp() },
        fontWeight = FontWeight.ExtraBold,
        color = colors.onSurface,
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(RULER_HEIGHT)
            .padding(horizontal = edgeClear),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = symbol,
            style = style,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(
                minFontSize = with(density) { SYMBOL_MIN_SIZE.toSp() },
                maxFontSize = style.fontSize,
                stepSize = with(density) { 1.dp.toSp() },
            ),
            modifier = Modifier
                .alpha(alpha)
                .clearAndSetSemantics {
                    testTag = TunerTestTags.CHORD_NAME
                    chordSymbol = symbol.text
                },
        )
    }
}

/**
 * [chord] in words: "A minor", "B♭ major", "C major, E in the bass" (a slash chord). With [speech] the
 * note names are as TalkBack says them ("B flat major").
 */
@Composable
fun chordWords(chord: SettledChord, notation: Notation, speech: NoteNameSpeech?): String {
    fun said(name: String) = speech?.name(name) ?: name
    val root = said(ChordSpelling.rootOf(chord.chord, notation))
    val words = stringResource(qualityWords(chord.chord.quality), root)
    val bass = chord.bass
    return if (chord.isSlash && bass != null) {
        stringResource(R.string.tuner_chord_slash, words, said(ChordSpelling.nameOf(bass, chord.chord, notation)))
    } else {
        words
    }
}

private fun qualityWords(quality: ChordQuality): Int = when (quality) {
    ChordQuality.MAJOR -> R.string.tuner_chord_major
    ChordQuality.MINOR -> R.string.tuner_chord_minor
    ChordQuality.DOMINANT_SEVENTH -> R.string.tuner_chord_seventh
    ChordQuality.MAJOR_SEVENTH -> R.string.tuner_chord_major_seventh
    ChordQuality.MINOR_SEVENTH -> R.string.tuner_chord_minor_seventh
    ChordQuality.SUS2 -> R.string.tuner_chord_sus2
    ChordQuality.SUS4 -> R.string.tuner_chord_sus4
    ChordQuality.POWER_CHORD -> R.string.tuner_chord_power_chord
    ChordQuality.SEVENTH_SHARP_NINE -> R.string.tuner_chord_seventh_sharp_nine
    ChordQuality.MINOR_NINTH -> R.string.tuner_chord_minor_ninth
    ChordQuality.MAJOR_NINTH -> R.string.tuner_chord_major_ninth
    else -> error("The tuner names only the detected chord types, not $quality")
}

/** The symbol as drawn: a slash chord's "/E" smaller and in [muted], like a footnote to the chord. */
private fun symbolText(chord: SettledChord, notation: Notation, muted: Color): AnnotatedString {
    val main = ChordSpelling.rootOf(chord.chord, notation) + ChordSpelling.suffixOf(chord.chord.quality)
    val bass = chord.bass
    return buildAnnotatedString {
        append(musicText(main))
        if (chord.isSlash && bass != null) {
            withStyle(SpanStyle(fontSize = SLASH_SIZE.em, color = muted)) { append(musicText("/" + ChordSpelling.nameOf(bass, chord.chord, notation))) }
        }
    }
}

/** The chord's symbol as shown in the ruler's place ("Am", "C/E"), for UI tests (TalkBack hears the words). */
val ChordSymbol = SemanticsPropertyKey<String>("ChordSymbol")
var SemanticsPropertyReceiver.chordSymbol by ChordSymbol

/** The symbol: a little bigger than the ruler's heard note (36 dp), as a chord is the one thing then shown there. */
private val SYMBOL_SIZE = 44.dp
private val SYMBOL_MIN_SIZE = 20.dp
private const val SLASH_SIZE = 0.55f

private const val FADED_ALPHA = 0.45f
private const val FADE_MILLIS = 300
