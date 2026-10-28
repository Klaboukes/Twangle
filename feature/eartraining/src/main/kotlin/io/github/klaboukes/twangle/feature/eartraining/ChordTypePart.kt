package io.github.klaboukes.twangle.feature.eartraining

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.chord.ChordSpelling
import io.github.klaboukes.twangle.core.dsp.eartraining.ChordTypeQuestion
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass

/**
 * Chord type under the ▶ (the mock-up's): the question line and a button for each chord type the
 * player chose ([EarTrainingState.chordTypes]), in the Chords panel's order: up to three in one
 * column, more two to a row, every one the same size whatever its name. Each shows the type large
 * ("Major", "m7", "7♯9") with its symbol on C small under it ("C", "Cm7", in the player's [notation]),
 * TalkBack saying the type in words ("minor seventh"). A tap answers ([onPick]) once per question;
 * then the right one glows softly in the in-tune colour and a wrong pick goes quiet, as Intervals'.
 *
 * With many types chosen (up to all twenty) the buttons keep their size and scroll in a box that
 * takes the room left under the question (at least [ANSWERS_MIN_HEIGHT]), so the ▶ and the line stay
 * where they are; the right answer is scrolled into view when it lights. Nothing moves as the question, the line or the glow
 * change.
 */
@Composable
internal fun ColumnScope.ChordTypePart(state: EarTrainingState, question: ChordTypeQuestion, notation: Notation, onPick: (ChordQuality) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val right = state.answered && state.pickedType == question.quality
    val wrong = state.answered && !right
    val line = when {
        right -> stringResource(R.string.ear_right)
        wrong -> stringResource(R.string.ear_not_that_one)
        else -> stringResource(R.string.ear_chord_prompt)
    }
    val spoken = if (wrong) line + ". " + stringResource(R.string.ear_answer_was, stringResource(question.quality.words)) else line
    Text(
        line,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (right) FontWeight.Medium else FontWeight.Normal),
        color = if (right) MaterialTheme.styleTokens.inTune else muted,
        textAlign = TextAlign.Center,
        // The whole width, so the line's box stays put whatever it says.
        modifier = Modifier.fillMaxWidth().testTag(EarTrainingTestTags.CHORD_LINE).semantics {
            contentDescription = spoken
            liveRegion = LiveRegionMode.Polite
        },
    )
    ChordAnswers(state.chordTypes.toList(), question, state, notation, onPick, Modifier.weight(1f, fill = false))
}

/**
 * The answer buttons in their own scrolling box: one column for up to [ONE_COLUMN_MOST], else two
 * to a row, each half the row, an odd last one in the middle, the same size. Room above and below
 * inside the box for the right answer's halo.
 */
@Composable
private fun ChordAnswers(
    types: List<ChordQuality>,
    question: ChordTypeQuestion,
    state: EarTrainingState,
    notation: Notation,
    onPick: (ChordQuality) -> Unit,
    modifier: Modifier,
) {
    val perRow = if (types.size <= ONE_COLUMN_MOST) 1 else 2
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .heightIn(min = ANSWERS_MIN_HEIGHT)
            .verticalScroll(rememberScrollState())
            .testTag(EarTrainingTestTags.CHORD_ANSWERS),
    ) {
        val cell = (maxWidth - ANSWER_GAP * (perRow - 1)) / perRow
        Column(
            Modifier.fillMaxWidth().padding(top = HALO_TOP, bottom = HALO_ROOM),
            verticalArrangement = Arrangement.spacedBy(ANSWER_GAP),
        ) {
            for (row in types.chunked(perRow)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ANSWER_GAP, Alignment.CenterHorizontally)) {
                    for (type in row) {
                        val lit = state.answered && type == question.quality
                        val seen = remember { BringIntoViewRequester() }
                        // The right one scrolled into view as it lights, so a wrong pick high in the list still shows it.
                        LaunchedEffect(lit) { if (lit) seen.bringIntoView() }
                        AnswerButton(
                            sign = chordSign(type),
                            name = musicText(ChordSpelling.rootOf(Chord.of(PitchClass.C, type), notation) + ChordSpelling.suffixOf(type)),
                            spoken = stringResource(type.words),
                            tag = EarTrainingTestTags.chordAnswer(type),
                            lit = lit,
                            quiet = state.answered && type == state.pickedType && type != question.quality,
                            enabled = !state.answered,
                            onClick = { onPick(type) },
                            modifier = Modifier.width(cell).bringIntoViewRequester(seen),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Chord type where it can't ask ([reason]): in the question's place, the quiet line the Chords tool
 * shows for it (the banjo's short string, or no shape within reach), centred, set smaller where the
 * room is short, never cut off. TalkBack reads it as it is.
 */
@Composable
internal fun ChordsUnavailableLine(reason: ChordsUnavailable) {
    Box(Modifier.fillMaxWidth().heightIn(min = UNAVAILABLE_HEIGHT), contentAlignment = Alignment.Center) {
        val style = MaterialTheme.typography.bodyLarge
        Text(
            stringResource(
                when (reason) {
                    ChordsUnavailable.SHORT_STRING -> R.string.ear_chord_short_string
                    ChordsUnavailable.NO_SHAPE -> R.string.ear_chord_none
                },
            ),
            style = style.copy(lineHeight = 1.4.em),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp),
            modifier = Modifier.padding(horizontal = 16.dp).testTag(EarTrainingTestTags.CHORDS_UNAVAILABLE),
        )
    }
    Spacer(Modifier.height(8.dp))
}

/**
 * A chord type as it stands large on its button and its chip: the symbol after the root ("m7",
 * "7♯9"), or a short word for the three whose symbol has no letters of its own (major, minor, the
 * power chord).
 */
@Composable
internal fun chordSign(type: ChordQuality): AnnotatedString = when (type) {
    ChordQuality.MAJOR -> AnnotatedString(stringResource(R.string.ear_chord_sign_major))
    ChordQuality.MINOR -> AnnotatedString(stringResource(R.string.ear_chord_sign_minor))
    ChordQuality.POWER_CHORD -> AnnotatedString(stringResource(R.string.ear_chord_sign_power))
    else -> musicText(ChordSpelling.suffixOf(type))
}

/** Each chord type in words, without a root: what TalkBack says for its button and chip. */
internal val ChordQuality.words: Int
    get() = when (this) {
        ChordQuality.MAJOR -> R.string.ear_chord_major
        ChordQuality.MINOR -> R.string.ear_chord_minor
        ChordQuality.DOMINANT_SEVENTH -> R.string.ear_chord_seventh
        ChordQuality.MAJOR_SEVENTH -> R.string.ear_chord_major_seventh
        ChordQuality.MINOR_SEVENTH -> R.string.ear_chord_minor_seventh
        ChordQuality.SUS2 -> R.string.ear_chord_sus2
        ChordQuality.SUS4 -> R.string.ear_chord_sus4
        ChordQuality.SEVENTH_SUS4 -> R.string.ear_chord_seventh_sus4
        ChordQuality.SIXTH -> R.string.ear_chord_sixth
        ChordQuality.MINOR_SIXTH -> R.string.ear_chord_minor_sixth
        ChordQuality.ADD9 -> R.string.ear_chord_add9
        ChordQuality.NINTH -> R.string.ear_chord_ninth
        ChordQuality.DIMINISHED -> R.string.ear_chord_diminished
        ChordQuality.DIMINISHED_SEVENTH -> R.string.ear_chord_diminished_seventh
        ChordQuality.HALF_DIMINISHED -> R.string.ear_chord_half_diminished
        ChordQuality.AUGMENTED -> R.string.ear_chord_augmented
        ChordQuality.POWER_CHORD -> R.string.ear_chord_power_chord
        ChordQuality.SEVENTH_SHARP_NINE -> R.string.ear_chord_seventh_sharp_nine
        ChordQuality.MINOR_NINTH -> R.string.ear_chord_minor_ninth
        ChordQuality.MAJOR_NINTH -> R.string.ear_chord_major_ninth
    }

/** Up to this many types stand in one column, as the mock-up's three. */
private const val ONE_COLUMN_MOST = 3

/** The answers' box at least this high, a row and its halo, where the room under the question is short. */
private val ANSWERS_MIN_HEIGHT = 120.dp

/** Above the first row, inside the box, room for its halo before the box's edge. */
private val HALO_TOP = 16.dp

/** The quiet line's room, about where the ▶ and the question would stand. */
private val UNAVAILABLE_HEIGHT = 160.dp
