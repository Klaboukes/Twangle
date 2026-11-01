package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.klaboukes.twangle.core.designsystem.NoteNameSpeech
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.chord.ChordSpelling
import io.github.klaboukes.twangle.core.dsp.chord.SettledChord
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.feature.tuner.ChordPhase
import io.github.klaboukes.twangle.feature.tuner.R
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags
import io.github.klaboukes.twangle.feature.tuner.TunerUiState

/**
 * The chord name's readout (Phase 30, option B of docs/design/chord-mode-preview.html), in the meter's
 * place and at its 2:1 shape, on a calm field like the meter's, in the style's colours and faces and
 * sized by its width (like the meter, not by the font size):
 * - the chord's symbol big ("Am", "B♭7", a slash chord's bass smaller and muted: "C/E"), its name in
 *   words under it ("A minor", "C major, E in the bass"), then its notes as small circles (A · C · E)
 *   with the bass filled and labelled, and the last three chords faint along the bottom (the current
 *   one in the ink colour; not shown while the current one is the only chord so far). As the strum
 *   dies away, or while a new strum is heard, it stays faded;
 * - waiting for a strum: a faint ♪; a strum heard but not named yet: the ♪ with soft dots under it;
 * - a strum that named nothing: a faint "?".
 * TalkBack hears it as one item, in words: "A minor, notes A, C, E, before that G major, C major".
 */
@Composable
fun ChordPanel(state: TunerUiState.ChordName, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val ink = scheme.onSurface
    val muted = scheme.onSurfaceVariant
    val accent = scheme.primary
    val onAccent = scheme.onPrimary
    val background = scheme.background
    val measurer = rememberTextMeasurer()
    val speech = rememberNoteNameSpeech()
    val separator = stringResource(R.string.tuner_spoken_separator)
    val chord = state.chord
    val notation = state.notation
    val symbol = chord?.let { symbolText(it, notation, muted) }
    val words = chord?.let { chordWords(it, notation, speech = null) }.orEmpty()
    val tones = chord?.let { c -> ChordSpelling.tonesOf(c.chord).map { it to ChordSpelling.nameOf(it, c.chord, notation) } }.orEmpty()
    val bassLabel = stringResource(R.string.tuner_chord_bass)
    // The last chords, the one shown last and in the ink colour; none while the one shown is all there is.
    val recent = state.recent.takeIf { list -> list.any { it != chord } }.orEmpty()
        .map { it to ChordSpelling.symbolOf(it.chord, it.bass, notation) }
    val history = buildAnnotatedString {
        recent.forEachIndexed { i, (settled, text) ->
            if (i > 0) append(HISTORY_SEPARATOR)
            if (settled == chord && i == recent.lastIndex) {
                withStyle(SpanStyle(color = ink, fontWeight = FontWeight.Medium)) { append(musicText(text)) }
            } else {
                append(musicText(text))
            }
        }
    }
    val spoken = spokenPanel(state, speech, separator)
    val alpha by animateFloatAsState(if (state.faded) FADED_ALPHA else 1f, tween(FADE_MILLIS), label = "chordFade")
    val displayStyle = MaterialTheme.typography.displayLarge.copy(lineHeight = TextUnit.Unspecified)
    val labelStyle = MaterialTheme.typography.labelLarge.copy(lineHeight = TextUnit.Unspecified)
    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = TextUnit.Unspecified)
    val waitSign = when (state.phase) {
        ChordPhase.UNCLEAR -> "?"
        else -> "♪"
    }
    Box(
        modifier
            .aspectRatio(PANEL_ASPECT_RATIO)
            .drawBehind {
                val u = size.width / UNITS_WIDE
                val centre = Offset(size.width / 2f, size.height / 2f)
                val field = Brush.radialGradient(
                    0f to background.copy(alpha = FIELD_ALPHA),
                    0.62f to background.copy(alpha = FIELD_ALPHA * 0.75f),
                    1f to background.copy(alpha = 0f),
                    center = centre,
                    radius = FIELD_RX * u,
                )
                scale(1f, FIELD_RY / FIELD_RX, centre) { drawCircle(field, FIELD_RX * u, centre) }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .testTag(TunerTestTags.CHORD_PANEL)
                .clearAndSetSemantics { contentDescription = spoken }
                .drawWithCache {
                    val u = size.width / UNITS_WIDE
                    val x = size.width / 2f
                    val width = size.width * TEXT_WIDTH_SHARE
                    val symbolLayout = symbol?.let { measurer.fitted(it, displayStyle.copy(color = ink, fontSize = (SYMBOL_UNITS * u).toSpNoScale(this)), width) }
                    val wordsLayout = if (words.isEmpty()) null else measurer.fitted(musicText(words), bodyStyle.copy(color = muted, fontSize = (WORDS_UNITS * u).toSpNoScale(this)), width)
                    val toneLayouts = tones.map { (pc, name) ->
                        val bass = pc == chord?.bass
                        measurer.fitted(musicText(name), labelStyle.copy(color = if (bass) onAccent else ink, fontSize = (TONE_UNITS * u).toSpNoScale(this)), TONE_RADIUS * 1.7f * u)
                    }
                    val bassLayout = measurer.fitted(AnnotatedString(bassLabel), labelStyle.copy(color = muted, fontSize = (BASS_LABEL_UNITS * u).toSpNoScale(this)), TONE_SPACING * u)
                    val historyLayout = if (history.isEmpty()) null else measurer.fitted(history, labelStyle.copy(color = muted, fontSize = (HISTORY_UNITS * u).toSpNoScale(this)), width)
                    val waitLayout = measurer.fitted(musicText(waitSign), displayStyle.copy(color = muted, fontSize = (WAIT_UNITS * u).toSpNoScale(this)), width)
                    onDrawBehind {
                        if (symbolLayout != null) {
                            drawText(symbolLayout, topLeft = Offset(x - symbolLayout.size.width / 2f, SYMBOL_Y * u - symbolLayout.size.height / 2f), alpha = alpha)
                            wordsLayout?.let { drawText(it, topLeft = Offset(x - it.size.width / 2f, WORDS_Y * u - it.size.height / 2f), alpha = (alpha + 1f) / 2f) }
                            val first = x - (tones.size - 1) * TONE_SPACING * u / 2f
                            tones.forEachIndexed { i, (pc, _) ->
                                val centre = Offset(first + i * TONE_SPACING * u, TONES_Y * u)
                                val bass = pc == chord?.bass
                                drawCircle(if (bass) accent else accent.copy(alpha = TONE_FILL_ALPHA), TONE_RADIUS * u, centre, alpha = alpha)
                                val name = toneLayouts[i]
                                drawText(name, topLeft = Offset(centre.x - name.size.width / 2f, centre.y - name.size.height / 2f), alpha = alpha)
                                if (bass) {
                                    drawText(bassLayout, topLeft = Offset(centre.x - bassLayout.size.width / 2f, BASS_LABEL_Y * u - bassLayout.size.height / 2f), alpha = alpha)
                                }
                            }
                        } else {
                            val signAlpha = if (state.phase == ChordPhase.WAITING) WAIT_ALPHA else WAIT_ALPHA * 1.2f
                            drawText(waitLayout, topLeft = Offset(x - waitLayout.size.width / 2f, WAIT_Y * u - waitLayout.size.height / 2f), alpha = signAlpha)
                            if (state.phase == ChordPhase.LISTENING) {
                                for (i in 0 until 3) {
                                    drawCircle(accent, DOT_RADIUS * u, Offset(x + (i - 1) * DOT_SPACING * u, DOTS_Y * u), alpha = DOT_ALPHAS[i])
                                }
                            }
                        }
                        historyLayout?.let { drawText(it, topLeft = Offset(x - it.size.width / 2f, HISTORY_Y * u - it.size.height / 2f)) }
                    }
                },
        )
    }
}

/**
 * The line under the chord name's panel, in the note's place: "Strum a chord" with "All the strings of
 * it" under it while waiting, then "Listening…" / "Let it ring", once named "Strum again for the next",
 * or "Not a clear chord". Each line takes the room of its longest text ([TallestOf]), so nothing moves.
 * It is one polite live region, and once a chord is named TalkBack hears the chord's name in words
 * there ("A minor, Strum again for the next").
 */
@Composable
fun ChordStatus(state: TunerUiState.ChordName, modifier: Modifier = Modifier, compact: Boolean = false) {
    val speech = rememberNoteNameSpeech()
    val separator = stringResource(R.string.tuner_spoken_separator)
    val titles = listOf(
        stringResource(R.string.tuner_chord_strum),
        stringResource(R.string.tuner_strum_hearing),
        stringResource(R.string.tuner_chord_unclear),
    )
    val details = listOf(
        stringResource(R.string.tuner_chord_strum_detail),
        stringResource(R.string.tuner_chord_let_ring),
        stringResource(R.string.tuner_chord_next),
        stringResource(R.string.tuner_chord_unclear_detail),
    )
    val title = when (state.phase) {
        ChordPhase.WAITING, ChordPhase.NAMED -> 0
        ChordPhase.LISTENING -> 1
        ChordPhase.UNCLEAR -> 2
    }
    val detail = when (state.phase) {
        ChordPhase.WAITING -> 0
        ChordPhase.LISTENING -> 1
        ChordPhase.NAMED -> 2
        ChordPhase.UNCLEAR -> 3
    }
    val chord = state.chord
    val spoken = if (state.phase == ChordPhase.NAMED && chord != null) {
        chordWords(chord, state.notation, speech) + separator + details[detail]
    } else {
        titles[title] + separator + details[detail]
    }
    val titleStyle = MaterialTheme.typography.headlineSmall
    val detailStyle = MaterialTheme.typography.bodyLarge
    Column(
        modifier
            .testTag(TunerTestTags.CHORD_STATUS)
            .semantics(mergeDescendants = true) {
                contentDescription = spoken
                liveRegion = LiveRegionMode.Polite
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // "Strum a chord", waiting and once named, is the tuner views' one waiting line ([WaitingLine]).
        TallestOf(titles, title) {
            if (it === titles[0]) WaitingLine(it, compact) else Text(it, style = titleStyle, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        }
        TallestOf(details, detail) { Text(it, style = detailStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
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

/** The panel as TalkBack says it: the chord in words and its notes, and the chords before it; or what it waits for. */
@Composable
private fun spokenPanel(state: TunerUiState.ChordName, speech: NoteNameSpeech, separator: String): String {
    val chord = state.chord
    val before = state.recent.let { if (chord != null && it.lastOrNull() == chord) it.dropLast(1) else it }
        .reversed()
        .map { chordWords(it, state.notation, speech) }
    val main = when {
        chord != null -> {
            val notes = ChordSpelling.tonesOf(chord.chord).joinToString(separator) { speech.name(ChordSpelling.nameOf(it, chord.chord, state.notation)) }
            chordWords(chord, state.notation, speech) + separator + stringResource(R.string.tuner_chord_spoken_notes, notes)
        }
        state.phase == ChordPhase.UNCLEAR -> stringResource(R.string.tuner_chord_unclear)
        state.phase == ChordPhase.LISTENING -> stringResource(R.string.tuner_strum_hearing)
        else -> stringResource(R.string.tuner_chord_none)
    }
    return if (before.isEmpty()) main else main + separator + stringResource(R.string.tuner_chord_spoken_recent, before.joinToString(separator))
}

/** The panel's shape: the meter's, so either fills the same place. */
private const val PANEL_ASPECT_RATIO = 2f

/** Lengths are in units of the panel: 340 wide and 170 high, about 1 dp on a phone (like the meter's u). */
private const val UNITS_WIDE = 340f
private const val TEXT_WIDTH_SHARE = 0.9f

private const val SYMBOL_Y = 46f
private const val SYMBOL_UNITS = 58f
private const val SLASH_SIZE = 0.55f
private const val WORDS_Y = 88f
private const val WORDS_UNITS = 15f
private const val TONES_Y = 116f
private const val TONE_RADIUS = 11f
private const val TONE_SPACING = 32f
private const val TONE_UNITS = 11f
private const val TONE_FILL_ALPHA = 0.16f
private const val BASS_LABEL_Y = 135f
private const val BASS_LABEL_UNITS = 8.5f
private const val HISTORY_Y = 158f
private const val HISTORY_UNITS = 12f
private const val HISTORY_SEPARATOR = " · "

private const val WAIT_Y = 70f
private const val WAIT_UNITS = 44f
private const val WAIT_ALPHA = 0.42f
private const val DOTS_Y = 112f
private const val DOT_RADIUS = 3.2f
private const val DOT_SPACING = 12f
private val DOT_ALPHAS = floatArrayOf(0.35f, 0.6f, 0.9f)

private const val FADED_ALPHA = 0.38f
private const val FADE_MILLIS = 300

/** The calm field under the panel, as under the meter: a soft oval wash of the background. */
private const val FIELD_ALPHA = 0.75f
private const val FIELD_RX = 200f
private const val FIELD_RY = 120f
