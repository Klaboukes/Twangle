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
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import io.github.klaboukes.twangle.core.settings.VisualStyle

/**
 * The twelve roots in two rows of six, [root] picked. Each is written as the chord would be with the
 * [quality] picked, in the player's [notation] (B♭ for B♭ major, A♯… never: B♭m; but F♯ and C♯m), so
 * the buttons and the chart always agree. Buttons of one steady size whatever their names say: a
 * longer name (Sol♯ at 2× text) is set smaller to fit. TalkBack hears one radio group ("Root"),
 * each root by its spoken name ("B flat").
 */
@Composable
internal fun RootPicker(
    root: PitchClass,
    quality: ChordQuality,
    notation: Notation,
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
        label = { ChordSpelling.rootOf(Chord.of(it, quality), notation) },
        spoken = { speech.name(ChordSpelling.rootOf(Chord.of(it, quality), notation)) },
        tag = PracticeTestTags::root,
        onPick = onRootChange,
        modifier = modifier,
    )
}

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
                    onClick = { onPick(option) },
                    modifier = Modifier.testTag(tag(option)),
                )
            }
        }
    }
}

@Composable
private fun PickButton(text: String, spoken: String, picked: Boolean, slot: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
                musicText(text),
                style = style.copy(
                    textAlign = TextAlign.Center,
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                ),
                color = if (picked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = MIN_LABEL_SIZE, maxFontSize = LABEL_SIZE, stepSize = 0.5.sp),
            )
        }
    }
}

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
private val BUTTON_GAP = 6.dp
private val BUTTON_HEIGHT = 40.dp
private val TOUCH_HEIGHT = 48.dp
private val LABEL_SIZE = 16.sp
private val MIN_LABEL_SIZE = 8.sp
private const val NEON_OUTLINE_ALPHA = 0.55f
private const val MUTED_OUTLINE_ALPHA = 0.6f
