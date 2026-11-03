package io.github.klaboukes.twangle.feature.practice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.breakableAtHyphens
import io.github.klaboukes.twangle.core.designsystem.koreanWordsKeptWhole
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.designsystem.pressFeedback
import io.github.klaboukes.twangle.core.designsystem.pressFlash
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.dsp.chord.Chord
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.scale.Scale
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType
import io.github.klaboukes.twangle.core.settings.PracticeTool

/**
 * The Practice tab (2.0, Phase 69; the first 2.0 mock-up's Practice, docs/design/fresh-start-preview.html):
 * [title] ("Practice") at the top, after the app's gear ([settingsButton], upright; on its side the gear is
 * at the rail's top), then one big card per tool in [tools] (each tool's switch hides its card,
 * [practiceToolsFor]): its picture in the accent ([practiceToolPicture]), its name and a line saying what
 * it does. A tap on a card calls [onOpen], which opens the tool full screen; its back arrow and Back come
 * back here. Under the cards the "last time" row reopens [lastTime], the tool used last, with what was
 * picked in it ("Chords · C major", "Scales · A minor pentatonic", from [savedRoot], [savedQuality] and
 * [savedScaleType] in the player's [notation]); with nothing used yet ([lastTimeTool] null) there is no row.
 *
 * Upright two cards to a row ([PracticeLayout.COLUMN], at most [COLUMN_MAX_WIDTH] wide; a tablet a little
 * wider), on its side all four in one row ([PracticeLayout.HALVES], [PracticeLayout.LARGE_SIDE]), so the
 * page fits a phone on its side without scrolling at the usual text size; at a large text size on a narrow
 * window fewer to a row (two on its side, one upright), so each card's line breaks only between words. Each card is at least
 * [UPRIGHT_RATIO] (on its side [SIDE_RATIO]) times as tall as wide, so a row's cards are one height at the
 * usual text size; a card grows where large text needs it (the row's others then stay as they are), and
 * the page scrolls where even that doesn't fit.
 *
 * TalkBack hears each card as one button ("Chords, Every shape, left-handed too"), the title as a heading
 * and the row as one button ("Last time, Chords, C major").
 */
@Composable
fun PracticeCards(
    title: String,
    tools: List<PracticeTool>,
    lastTime: PracticeTool?,
    notation: Notation,
    savedRoot: PitchClass,
    savedQuality: ChordQuality,
    savedScaleType: ScaleType,
    onOpen: (PracticeTool) -> Unit,
    modifier: Modifier = Modifier,
    settingsButton: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).testTag(PracticeTestTags.CARDS)) {
        val layout = practiceLayoutFor(maxWidth.value, maxHeight.value)
        val side = layout == PracticeLayout.HALVES || layout == PracticeLayout.LARGE_SIDE
        val preferred = if (side) SIDE_COLUMNS else UPRIGHT_COLUMNS
        val maxContent = when (layout) {
            PracticeLayout.COLUMN -> COLUMN_MAX_WIDTH
            PracticeLayout.LARGE_COLUMN -> LARGE_COLUMN_MAX_WIDTH
            PracticeLayout.HALVES -> maxWidth
            PracticeLayout.LARGE_SIDE -> LARGE_SIDE_MAX_WIDTH
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = SIDE_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoxWithConstraints(Modifier.widthIn(max = maxContent).fillMaxWidth()) {
                // A phone on its side is short: the gaps, the cards' insides and the row close up a little.
                val short = layout == PracticeLayout.HALVES
                val gap = if (short) SHORT_GAP else CARD_GAP
                val padding = if (short) SHORT_GAP else CARD_PADDING
                // Fewer to a row where a card's line would hold less than [MIN_LINE_EMS] letters' widths (large text on a
                // narrow phone): two on its side, one upright, so no word of it is broken.
                val lineEm = with(LocalDensity.current) { LINE_SIZE.toDp() }
                val columns = generateSequence(preferred) { if (it > 1) it / 2 else null }
                    .firstOrNull { (maxWidth - gap * (it - 1)) / it - padding * 2 >= lineEm * MIN_LINE_EMS } ?: 1
                val cardWidth = (maxWidth - gap * (columns - 1)) / columns
                Column {
                    Spacer(Modifier.height(HEADER_TOP))
                    TitleRow(title, settingsButton)
                    Spacer(Modifier.height(if (short) SHORT_GAP / 2 else TITLE_GAP))
                    for (row in tools.chunked(columns)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                            for (tool in row) {
                                ToolCard(
                                    tool,
                                    // One to a row, a card is as tall as its contents, not as its width.
                                    minHeight = if (columns == 1) 0.dp else cardWidth * if (side) SIDE_RATIO else UPRIGHT_RATIO,
                                    picture = if (short) SHORT_PICTURE else (cardWidth * PICTURE_SHARE).coerceIn(MIN_PICTURE, MAX_PICTURE),
                                    padding = padding,
                                    onClick = { onOpen(tool) },
                                    modifier = Modifier.width(cardWidth),
                                )
                            }
                        }
                        Spacer(Modifier.height(gap))
                    }
                    if (lastTime != null) {
                        LastTimeRow(
                            lastTime,
                            lastTimeDetail(lastTime, notation, savedRoot, savedQuality, savedScaleType),
                            short = short,
                            onClick = { onOpen(lastTime) },
                        )
                        Spacer(Modifier.height(gap))
                    }
                }
            }
        }
    }
}

/** The page's top row: the gear (where given) and the title, one line high as the tools' top rows. */
@Composable
private fun TitleRow(title: String, settingsButton: (@Composable () -> Unit)?) {
    Row(Modifier.fillMaxWidth().heightIn(min = TOP_ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        if (settingsButton != null) {
            settingsButton()
            Spacer(Modifier.width(TITLE_AFTER_GEAR))
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = TITLE_SIZE),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** One tool's card: its picture at the top, its name and what it does at the bottom, on the surface with a thin line round it. */
@Composable
private fun ToolCard(tool: PracticeTool, minHeight: Dp, picture: Dp, padding: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(CARD_CORNER)
    Column(
        modifier
            .heightIn(min = minHeight)
            .testTag(PracticeTestTags.card(tool))
            .pressFeedback(source, shape, fill = MaterialTheme.colorScheme.surface, outline = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant))
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                source.pressFlash()
                onClick()
            }
            .padding(padding),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
            Icon(practiceToolPicture(tool), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(picture))
        }
        Column {
            // One line, set smaller where it doesn't fit (Dutch "Akkoorden" at 2x text on a 320 dp phone); never wrapped,
            // so the auto-size sees a name too wide (Ear training's AnswerButton in IntervalsPart.kt says why).
            Text(
                stringResource(tool.label),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = NAME_SIZE),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = NAME_MIN_SIZE, maxFontSize = NAME_SIZE, stepSize = 0.5.sp),
            )
            // Set smaller where a word wouldn't fit its line whole (large text), a line ending only between words.
            Text(
                wholeWords(stringResource(tool.cardLine)),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = LINE_SIZE),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                autoSize = remember { WholeWordsAutoSize(LINE_SIZE, LINE_MIN_SIZE) },
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** The tool used last and what was picked in it, written and as TalkBack says it. */
private class LastTimeLine(val text: String, val spoken: String)

/** "Chords · C major", "Scales · A minor pentatonic"; the capo helper and Ear training by name alone. */
@Composable
private fun lastTimeDetail(tool: PracticeTool, notation: Notation, root: PitchClass, quality: ChordQuality, scaleType: ScaleType): LastTimeLine {
    val name = stringResource(tool.label)
    val speech = rememberNoteNameSpeech()
    val (picked, spoken) = when (tool) {
        PracticeTool.CHORDS -> Chord.of(root, quality).let { chordWords(it, notation) to chordWords(it, notation, speech) }
        PracticeTool.SCALES -> Scale(root, scaleType).let { scaleWords(it, notation) to scaleWords(it, notation, speech) }
        PracticeTool.CAPO, PracticeTool.EAR_TRAINING -> return LastTimeLine(name, name)
    }
    return LastTimeLine(
        stringResource(R.string.practice_last_time_detail, name, picked),
        stringResource(R.string.practice_last_time_detail, name, spoken),
    )
}

/** The "last time" row: the tool's icon, "Last time" over [line], and › at the end; a tap reopens [tool]. */
@Composable
private fun LastTimeRow(tool: PracticeTool, line: LastTimeLine, short: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(ROW_CORNER)
    val heading = stringResource(R.string.practice_last_time)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (short) SHORT_ROW_MIN_HEIGHT else ROW_MIN_HEIGHT)
            .testTag(PracticeTestTags.LAST_TIME)
            .pressFeedback(source, shape, fill = MaterialTheme.colorScheme.surface, outline = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant))
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                source.pressFlash()
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = if (short) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(practiceToolIcon(tool), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).clearAndSetSemantics { contentDescription = heading + ", " + line.spoken }) {
            Text(heading, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val style = MaterialTheme.typography.bodyLarge
            Text(
                musicText(wholeWords(line.text)),
                style = style,
                color = MaterialTheme.colorScheme.onSurface,
                // A long scale's name at large text: smaller, its lines ending only between words ("mineur-pentatonisch" at its hyphen).
                autoSize = remember(style.fontSize) { WholeWordsAutoSize(style.fontSize, LINE_MIN_SIZE) },
            )
        }
        Icon(
            painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp).size(24.dp),
        )
    }
}

/** [text] with a line allowed to end after a hyphen and Korean words kept whole, so the fit checks find no word broken. */
private fun wholeWords(text: String): String = breakableAtHyphens(koreanWordsKeptWhole(text))

/** Each tool's name, on its card and in the "last time" row. A new tool needs its name here (the `when` says so). */
internal val PracticeTool.label: Int
    get() = when (this) {
        PracticeTool.CHORDS -> R.string.practice_tool_chords
        PracticeTool.SCALES -> R.string.practice_tool_scales
        PracticeTool.CAPO -> R.string.practice_tool_capo
        PracticeTool.EAR_TRAINING -> R.string.practice_tool_ear_training
    }

/** The line under each tool's name on its card, saying what it does. */
private val PracticeTool.cardLine: Int
    get() = when (this) {
        PracticeTool.CHORDS -> R.string.practice_card_chords
        PracticeTool.SCALES -> R.string.practice_card_scales
        PracticeTool.CAPO -> R.string.practice_card_capo
        PracticeTool.EAR_TRAINING -> R.string.practice_card_ear_training
    }

/** Two cards to a row upright, four on its side; fewer where a card's line would hold less than this many letters (ems). */
private const val UPRIGHT_COLUMNS = 2
private const val SIDE_COLUMNS = 4
private const val MIN_LINE_EMS = 7f

/** A card's height to its width, at least: the mock-up's upright, a little flatter on its side, where height is short. */
private const val UPRIGHT_RATIO = 1.08f
private const val SIDE_RATIO = 0.9f

/** The cards' width, at most: a phone's column, a tablet's upright, and four in a row on a tablet on its side. */
private val COLUMN_MAX_WIDTH = 480.dp
private val LARGE_COLUMN_MAX_WIDTH = 600.dp
private val LARGE_SIDE_MAX_WIDTH = 960.dp

private val SIDE_PADDING = 16.dp
private val CARD_GAP = 14.dp
private val CARD_CORNER = 22.dp
private val CARD_PADDING = 16.dp

/**
 * The picture's size on a card: a share of the card's width, within its smallest and largest size (owner,
 * 2026-11-03: bigger; until then 88 dp, and 64 dp on a card under 190 dp wide, which is most phones), and on
 * a phone on its side, where the height is short, [SHORT_PICTURE].
 */
private const val PICTURE_SHARE = 0.5f
private val MIN_PICTURE = 64.dp
private val MAX_PICTURE = 120.dp
private val SHORT_PICTURE = 56.dp

/** On a phone on its side: the gaps between the cards and inside them. */
private val SHORT_GAP = 12.dp

/** The title row stands as far down as the tools' top rows ([HEADER_TOP] in ChordsScreen) and is as tall as the gear's touch area. */
private val HEADER_TOP = 8.dp
private val TOP_ROW_HEIGHT = 48.dp
private val TITLE_AFTER_GEAR = 8.dp
private val TITLE_GAP = 12.dp

private val TITLE_SIZE = 22.sp
private val NAME_SIZE = 19.sp
private val NAME_MIN_SIZE = 6.sp
private val LINE_SIZE = 13.5.sp
private val LINE_MIN_SIZE = 7.sp

private val ROW_CORNER = 16.dp
private val ROW_MIN_HEIGHT = 64.dp
private val SHORT_ROW_MIN_HEIGHT = 56.dp
