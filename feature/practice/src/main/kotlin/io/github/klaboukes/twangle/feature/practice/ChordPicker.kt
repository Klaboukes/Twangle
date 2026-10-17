package io.github.klaboukes.twangle.feature.practice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.designsystem.neonFill
import io.github.klaboukes.twangle.core.designsystem.pressFeedback
import io.github.klaboukes.twangle.core.designsystem.pressFlash
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.chord.ChordSpelling
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.scale.Scale
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType
import io.github.klaboukes.twangle.core.settings.PracticeChart
import io.github.klaboukes.twangle.core.settings.VisualStyle

/**
 * The twelve roots in two rows of six, [root] picked, each written by [spell] as the chord or scale
 * picked would write it in the player's notation (B♭ for B♭ major, A♯… never: B♭m; but F♯ and C♯m;
 * see [chordRootSpelling] and [scaleRootSpelling]), so the buttons and the chart always agree.
 * Buttons of one steady size whatever their names say: a longer name (Sol♯ at 2× text) is set
 * smaller to fit. TalkBack hears one radio group ("Root"), each root by its spoken name ("B flat").
 */
@Composable
internal fun RootPicker(
    root: PitchClass,
    spell: (PitchClass) -> String,
    onRootChange: (PitchClass) -> Unit,
    modifier: Modifier = Modifier,
) {
    val speech = rememberNoteNameSpeech()
    PickerGrid(
        options = PitchClass.entries,
        picked = root,
        perRow = ROOTS_PER_ROW,
        maxButtonWidth = ROOT_BUTTON_MAX_WIDTH,
        groupLabel = stringResource(R.string.practice_root),
        label = spell,
        spoken = { speech.name(spell(it)) },
        tag = PracticeTestTags::root,
        onPick = onRootChange,
        modifier = modifier,
    )
}

/** Each root as the chord on it with [quality] writes it in [notation]: B♭ for major, but C♯ for minor. */
internal fun chordRootSpelling(quality: ChordQuality, notation: Notation): (PitchClass) -> String =
    { ChordSpelling.rootOf(Chord.of(it, quality), notation) }

/** Each root as the scale on it of [type] writes it in [notation]: B♭ major, but C♯ minor pentatonic. */
internal fun scaleRootSpelling(type: ScaleType, notation: Notation): (PitchClass) -> String =
    { Scale(it, type).nameOf(it, notation) }

/**
 * The seven chord types, [quality] picked, each by its symbol on the picked [root] ("C", "Cm",
 * "C7", "Cmaj7", "Cm7", "Csus2", "Csus4"), four to a row. TalkBack hears one radio group ("Chord
 * type"), each in words ("C minor seventh").
 */
@Composable
internal fun ChordTypePicker(
    root: PitchClass,
    quality: ChordQuality,
    notation: Notation,
    onQualityChange: (ChordQuality) -> Unit,
    modifier: Modifier = Modifier,
) {
    val speech = rememberNoteNameSpeech()
    val spoken = ChordQuality.entries.associateWith { chordWords(Chord.of(root, it), notation, speech) }
    PickerGrid(
        options = ChordQuality.entries,
        picked = quality,
        perRow = TYPES_PER_ROW,
        maxButtonWidth = TYPE_BUTTON_MAX_WIDTH,
        groupLabel = stringResource(R.string.practice_chord_type),
        label = { chordSymbol(Chord.of(root, it), notation) },
        spoken = { spoken.getValue(it) },
        tag = PracticeTestTags::type,
        onPick = onQualityChange,
        modifier = modifier,
    )
}

/**
 * The five scales, [type] picked, by name ("Major", "Minor pentatonic"), [perRow] to a row: two
 * upright, so long names (French "Pentatonique majeure") keep a fair size, three on its side, where
 * the height is short (two there too at large text sizes); a long name takes two lines rather than getting small. TalkBack hears one
 * radio group ("Scale"), each by its name.
 */
@Composable
internal fun ScaleTypePicker(
    type: ScaleType,
    perRow: Int,
    onTypeChange: (ScaleType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val names = ScaleType.entries.associateWith { stringResource(scaleTypeLabel(it)) }
    PickerGrid(
        options = ScaleType.entries,
        picked = type,
        perRow = perRow,
        maxButtonWidth = SCALE_BUTTON_MAX_WIDTH,
        groupLabel = stringResource(R.string.practice_scale_type),
        label = { names.getValue(it) },
        spoken = { names.getValue(it) },
        tag = PracticeTestTags::scale,
        onPick = onTypeChange,
        modifier = modifier,
        lines = 2,
    )
}

/**
 * The small Chords · Scales choice under the tuning line: what the tool shows, [chart] picked, as
 * two buttons of the pickers' kind. TalkBack hears one radio group ("Chords or scales").
 */
@Composable
internal fun ChartChoice(
    chart: PracticeChart,
    onChartChange: (PracticeChart) -> Unit,
    modifier: Modifier = Modifier,
) {
    val names = mapOf(
        PracticeChart.CHORDS to stringResource(R.string.practice_chart_chords),
        PracticeChart.SCALES to stringResource(R.string.practice_chart_scales),
    )
    PickerGrid(
        options = PracticeChart.entries,
        picked = chart,
        perRow = PracticeChart.entries.size,
        maxButtonWidth = CHOICE_BUTTON_MAX_WIDTH,
        groupLabel = stringResource(R.string.practice_chart_choice),
        label = { names.getValue(it) },
        spoken = { names.getValue(it) },
        tag = PracticeTestTags::chart,
        onPick = onChartChange,
        modifier = modifier,
    )
}

/**
 * [options] as see-through buttons [perRow] to a row (the last row centred), each as wide as the row
 * allows up to [maxButtonWidth] and 40 dp tall; the [picked] one in the accent's container with an
 * accent outline, as the metronome's meter buttons. Each touch area is its share of the row and 48 dp
 * tall, the gap between the faces inside it, so six roots still fit a 320 dp phone with 48 dp each.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> PickerGrid(
    options: List<T>,
    picked: T,
    perRow: Int,
    maxButtonWidth: Dp,
    groupLabel: String,
    label: (T) -> String,
    spoken: (T) -> String,
    tag: (T) -> String,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    lines: Int = 1,
) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Whole pixels, rounded down, so a row of [perRow] never comes out a pixel too wide and wraps.
        val slot = minOf(maxButtonWidth + BUTTON_GAP, with(LocalDensity.current) { (constraints.maxWidth / perRow).toDp() })
        FlowRow(
            modifier = Modifier.selectableGroup().semantics { contentDescription = groupLabel },
            horizontalArrangement = Arrangement.Center,
            maxItemsInEachRow = perRow,
        ) {
            for (option in options) {
                PickButton(
                    text = label(option),
                    spoken = spoken(option),
                    picked = option == picked,
                    slot = slot,
                    lines = lines,
                    onClick = { onPick(option) },
                    modifier = Modifier.testTag(tag(option)),
                )
            }
        }
    }
}

@Composable
private fun PickButton(text: String, spoken: String, picked: Boolean, slot: Dp, lines: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.small
    Box(
        modifier = modifier
            .selectable(
                selected = picked,
                interactionSource = source,
                indication = null,
                role = Role.RadioButton,
                onClick = {
                    source.pressFlash()
                    onClick()
                },
            )
            .semantics { contentDescription = spoken }
            .size(slot, TOUCH_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(slot - BUTTON_GAP, BUTTON_HEIGHT)
                .pressFeedback(source, shape, fill = faceFill(picked), outline = faceOutline(picked))
                .padding(horizontal = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            val style = MaterialTheme.typography.labelLarge
            Text(
                musicText(breakableAtHyphens(text)),
                style = style.copy(
                    textAlign = TextAlign.Center,
                    // Two lines take the size the text is set at, so they fit the face as it shrinks.
                    lineHeight = if (lines > 1) TWO_LINE_HEIGHT else style.lineHeight,
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                ),
                color = if (picked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onBackground,
                maxLines = lines,
                autoSize = if (lines > 1) {
                    WholeWordsAutoSize
                } else {
                    // The largest size that fits: below MIN_LABEL_SIZE only where the label would otherwise be cut
                    // off (Classic's La♭maj7 at 2× text on a 320 dp phone fits at 7.5 sp).
                    TextAutoSize.StepBased(minFontSize = LAST_RESORT_LABEL_SIZE, maxFontSize = LABEL_SIZE, stepSize = 0.5.sp)
                },
            )
        }
    }
}

/**
 * The largest label size from [LABEL_SIZE] down to [MIN_LABEL_SIZE] at which the label fits its face
 * on its lines without breaking a word ("Pentatonique / majeure", never "Pentatoni / que"); Chinese
 * and Japanese, written without spaces, may break between any two characters. Smallest if none.
 * Where it can, it leaves [TWO_LINE_MARGIN] above and below, so two lines never touch the outline
 * (Classic's and Retro's letters did); at large text sizes, where even the smallest wouldn't fit
 * with it, the label may use the whole face.
 */
private object WholeWordsAutoSize : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        val margin = 2 * TWO_LINE_MARGIN.roundToPx()
        return largestFitting(constraints, text, margin) ?: largestFitting(constraints, text, 0) ?: MIN_LABEL_SIZE
    }

    private fun TextAutoSizeLayoutScope.largestFitting(constraints: Constraints, text: AnnotatedString, margin: Int): TextUnit? {
        var size = LABEL_SIZE.value
        while (size > MIN_LABEL_SIZE.value) {
            val layout = performLayout(constraints, text, size.sp)
            val roomy = !constraints.hasBoundedHeight || layout.size.height <= constraints.maxHeight - margin
            if (!layout.didOverflowWidth && !layout.didOverflowHeight && roomy && breaksBetweenWords(layout, text)) return size.sp
            size -= LABEL_STEP
        }
        return null
    }

    private fun breaksBetweenWords(layout: TextLayoutResult, text: AnnotatedString): Boolean =
        (0 until layout.lineCount - 1).all { line ->
            val end = layout.getLineEnd(line)
            end <= 0 || end >= text.length || text[end - 1].isWhitespace() || text[end - 1] == '-' || text[end - 1] == ZERO_WIDTH_SPACE ||
                text[end].isWhitespace() ||
                (text[end - 1].breaksAnywhere() && text[end].breaksAnywhere())
        }

    private fun Char.breaksAnywhere(): Boolean = when (Character.UnicodeScript.of(code)) {
        Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> true
        else -> this == 'ー'
    }

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * [text] with a line break allowed after each hyphen between two letters (a zero-width space). Android
 * breaks a compound such as "majeur-pentatonisch" at its hyphen only when hyphenation is on, which
 * it isn't, so a long one (Dutch "A♭ majeur-pentatonisch" at 2× text in Futuristic) was broken in
 * the middle of a word instead.
 */
internal fun breakableAtHyphens(text: String): String = text.replace(HYPHEN_BETWEEN_LETTERS, "-$ZERO_WIDTH_SPACE")

private val HYPHEN_BETWEEN_LETTERS = Regex("(?<=\\p{L})-(?=\\p{L})")
private const val ZERO_WIDTH_SPACE = '\u200B'

/** A see-through button's backing; picked ones in the container colour (Futuristic: a faint accent). */
@Composable
internal fun faceFill(picked: Boolean): Color {
    val scheme = MaterialTheme.colorScheme
    val neon = MaterialTheme.styleTokens.style == VisualStyle.FUTURISTIC
    return when {
        picked && neon -> scheme.primary.copy(alpha = 0.12f)
        picked -> scheme.primaryContainer
        neon -> neonFill(scheme)
        else -> scheme.surface.copy(alpha = 0.45f)
    }
}

/** A see-through button's outline: muted, Futuristic's in the accent, picked ones 2 dp in the accent. */
@Composable
internal fun faceOutline(picked: Boolean): BorderStroke {
    val scheme = MaterialTheme.colorScheme
    val neon = MaterialTheme.styleTokens.style == VisualStyle.FUTURISTIC
    return when {
        picked -> BorderStroke(2.dp, scheme.primary)
        neon -> BorderStroke(1.dp, scheme.primary.copy(alpha = NEON_OUTLINE_ALPHA))
        else -> BorderStroke(1.dp, scheme.onSurfaceVariant.copy(alpha = MUTED_OUTLINE_ALPHA))
    }
}

private const val ROOTS_PER_ROW = 6
private const val TYPES_PER_ROW = 4
private val ROOT_BUTTON_MAX_WIDTH = 56.dp
private val TYPE_BUTTON_MAX_WIDTH = 84.dp
private val SCALE_BUTTON_MAX_WIDTH = 180.dp
private val CHOICE_BUTTON_MAX_WIDTH = 132.dp
private val BUTTON_GAP = 6.dp
private val BUTTON_HEIGHT = 40.dp
private val TOUCH_HEIGHT = 48.dp
private val LABEL_SIZE = 16.sp
private val MIN_LABEL_SIZE = 8.sp
private val LAST_RESORT_LABEL_SIZE = 6.sp
private const val LABEL_STEP = 0.5f
private val TWO_LINE_HEIGHT = 1.15.em
private val TWO_LINE_MARGIN = 3.dp
private const val NEON_OUTLINE_ALPHA = 0.55f
private const val MUTED_OUTLINE_ALPHA = 0.6f
