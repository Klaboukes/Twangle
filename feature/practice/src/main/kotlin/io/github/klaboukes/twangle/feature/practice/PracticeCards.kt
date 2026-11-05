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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.TOP_BAR_TOP
import io.github.klaboukes.twangle.core.designsystem.TopBar
import io.github.klaboukes.twangle.core.designsystem.TopBarTitle
import io.github.klaboukes.twangle.core.designsystem.WholeWordsAutoSize
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
 * The Practice tab (2.0, Phase 69; the first 2.0 mock-up's Practice, docs/design/fresh-start-preview.html; rows since
 * the owner's review of Phase 78, 2026-11-04): [title] ("Practice") in the middle of the top row, every tab's [TopBar],
 * with the app's gear at its start ([settingsButton], upright; on its side the gear is at the rail's top). Under it
 * first the "Last used" row, a quiet shortcut that reopens [lastTime], the tool used last, with what was picked in it
 * ("Chords · C major", "Scales · A minor pentatonic", from [savedRoot], [savedQuality] and [savedScaleType] in the
 * player's [notation]); with nothing used yet ([lastTimeTool] null) there is none. Then one row per tool in [tools]
 * (each tool's switch hides its row, [practiceToolsFor]): its picture in the accent at the start
 * ([practiceToolPicture]), its name and a line saying what it does beside it, on the surface with a thin line round
 * it. A tap on a row calls [onOpen], which opens the tool under the bar; its back arrow and Back come back here.
 *
 * Upright one column of rows ([PracticeLayout.COLUMN], at most [COLUMN_MAX_WIDTH] wide; a tablet a little wider); on
 * its side the rows in [SIDE_COLUMNS] columns ([PracticeLayout.HALVES], [PracticeLayout.LARGE_SIDE]; fewer where a
 * row's text would get narrower than [MIN_TEXT_WIDTH]), so the page isn't one long list and fits a phone on its side
 * without scrolling at the usual text size. The rows of a line are one height; the page scrolls where large text
 * needs it. Until 2026-11-04 the tools were big cards, two to a row upright, with the fifth (the pitch trail) a wide,
 * low card under them, which looked like another kind of thing ([history](docs/architecture/history.md)).
 *
 * TalkBack hears each row as one button ("Chords, Every shape, left-handed too"), the title as a heading and the
 * shortcut as one button ("Last used, Chords, C major").
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
                // A phone on its side is short: the gaps, the rows' insides and their pictures close up a little.
                val short = layout == PracticeLayout.HALVES
                val gap = if (short) SHORT_GAP else ROW_GAP
                val picture = if (short) SHORT_PICTURE else PICTURE
                // On its side fewer columns where a row's words would get too narrow (large text on a small phone).
                val textRoom = { columns: Int -> (maxWidth - gap * (columns - 1)) / columns - TOOL_ROW_PADDING * 2 - picture - PICTURE_GAP }
                val fewest = with(LocalDensity.current) { LINE_SIZE.toDp() * MIN_TEXT_EMS }.coerceAtLeast(MIN_TEXT_WIDTH)
                val columns = if (side) (SIDE_COLUMNS downTo 1).firstOrNull { textRoom(it) >= fewest } ?: 1 else 1
                val rowWidth = (maxWidth - gap * (columns - 1)) / columns
                Column {
                    Spacer(Modifier.height(TOP_BAR_TOP))
                    TitleRow(title, settingsButton)
                    Spacer(Modifier.height(if (short) SHORT_GAP / 2 else TITLE_GAP))
                    if (lastTime != null) {
                        LastTimeRow(
                            lastTime,
                            lastTimeDetail(lastTime, notation, savedRoot, savedQuality, savedScaleType),
                            short = short,
                            onClick = { onOpen(lastTime) },
                        )
                        Spacer(Modifier.height(gap))
                    }
                    for (line in tools.chunked(columns)) {
                        // The rows of a line one height: the tallest's (a row's line wraps where its words need it).
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
                            for (tool in line) {
                                ToolRow(tool, picture = picture, short = short, onClick = { onOpen(tool) }, modifier = Modifier.width(rowWidth).fillMaxHeight())
                            }
                        }
                        Spacer(Modifier.height(gap))
                    }
                }
            }
        }
    }
}

/** The page's top row: every tab's [TopBar], the gear (where given) at its start and the title in its middle. */
@Composable
private fun TitleRow(title: String, settingsButton: (@Composable () -> Unit)?) {
    TopBar(middle = { TopBarTitle(title) }, modifier = Modifier.fillMaxWidth(), start = settingsButton)
}

/**
 * One tool's row: its [picture]-sized picture at the start, its name and what it does beside it, on the surface with a
 * thin line round it, closer together where [short].
 */
@Composable
private fun ToolRow(tool: PracticeTool, picture: Dp, short: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(TOOL_ROW_CORNER)
    Row(
        modifier
            .testTag(PracticeTestTags.card(tool))
            .pressFeedback(source, shape, fill = MaterialTheme.colorScheme.surface, outline = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant))
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                source.pressFlash()
                onClick()
            }
            .padding(horizontal = TOOL_ROW_PADDING, vertical = if (short) SHORT_ROW_VERTICAL else ROW_VERTICAL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(practiceToolPicture(tool), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(picture))
        Spacer(Modifier.width(PICTURE_GAP))
        Box(Modifier.weight(1f)) { ToolRowWords(tool) }
    }
}

/** A row's name and the line saying what its tool does. */
@Composable
private fun ToolRowWords(tool: PracticeTool) {
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
        // Set smaller where a word wouldn't fit its line whole (large text), a line ending only between words; its
        // line height shrinks with it, so a line the row is a little short for fits (Hindi at 2x text, 2026-11-05).
        Text(
            wholeWords(stringResource(tool.cardLine)),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = LINE_SIZE, lineHeight = LINE_HEIGHT),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            autoSize = remember { WholeWordsAutoSize(LINE_SIZE, LINE_MIN_SIZE) },
            modifier = Modifier.padding(top = 2.dp),
        )
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
        PracticeTool.CAPO, PracticeTool.EAR_TRAINING, PracticeTool.PITCH_TRAIL -> return LastTimeLine(name, name)
    }
    return LastTimeLine(
        stringResource(R.string.practice_last_time_detail, name, picked),
        stringResource(R.string.practice_last_time_detail, name, spoken),
    )
}

/**
 * The "Last used" row above the tools: a quiet shortcut, not another tool (owner, 2026-11-04; under the cards and in
 * their outlined look until then, headed "Last time"): no fill or outline at rest (the press shows its shape), the
 * tool's small icon, "Last used" small over [line], and › at the end; a tap reopens [tool].
 */
@Composable
private fun LastTimeRow(tool: PracticeTool, line: LastTimeLine, short: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(TOOL_ROW_CORNER)
    val heading = stringResource(R.string.practice_last_time)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (short) SHORT_LAST_USED_HEIGHT else LAST_USED_HEIGHT)
            .testTag(PracticeTestTags.LAST_TIME)
            .pressFeedback(source, shape)
            .clickable(interactionSource = source, indication = null, role = Role.Button) {
                source.pressFlash()
                onClick()
            }
            .padding(horizontal = TOOL_ROW_PADDING, vertical = if (short) 2.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(practiceToolIcon(tool), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).clearAndSetSemantics { contentDescription = heading + ", " + line.spoken }) {
            Text(heading, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** Each tool's name, on its row and in the "Last used" row. A new tool needs its name here (the `when` says so). */
internal val PracticeTool.label: Int
    get() = when (this) {
        PracticeTool.CHORDS -> R.string.practice_tool_chords
        PracticeTool.SCALES -> R.string.practice_tool_scales
        PracticeTool.CAPO -> R.string.practice_tool_capo
        PracticeTool.EAR_TRAINING -> R.string.practice_tool_ear_training
        PracticeTool.PITCH_TRAIL -> R.string.practice_tool_pitch_trail
    }

/** The line under each tool's name on its row, saying what it does. */
private val PracticeTool.cardLine: Int
    get() = when (this) {
        PracticeTool.CHORDS -> R.string.practice_card_chords
        PracticeTool.SCALES -> R.string.practice_card_scales
        PracticeTool.CAPO -> R.string.practice_card_capo
        PracticeTool.EAR_TRAINING -> R.string.practice_card_ear_training
        PracticeTool.PITCH_TRAIL -> R.string.practice_card_pitch_trail
    }

/** On its side the rows stand in this many columns, fewer where a row's words would get narrower than this. */
private const val SIDE_COLUMNS = 3
private val MIN_TEXT_WIDTH = 120.dp
private const val MIN_TEXT_EMS = 9f

/** The rows' width, at most: a phone's column, a tablet's upright, and the columns on a tablet on its side. */
private val COLUMN_MAX_WIDTH = 480.dp
private val LARGE_COLUMN_MAX_WIDTH = 600.dp
private val LARGE_SIDE_MAX_WIDTH = 960.dp

private val SIDE_PADDING = 16.dp

/** Between the rows; on a phone on its side, where the height is short, less. */
private val ROW_GAP = 10.dp
private val SHORT_GAP = 8.dp

/** Under the title row. */
private val TITLE_GAP = 12.dp

/** A tool's row: its corners, its insides, and its picture at the start (smaller on a phone on its side). */
private val TOOL_ROW_CORNER = 18.dp
private val TOOL_ROW_PADDING = 16.dp
private val ROW_VERTICAL = 12.dp
private val SHORT_ROW_VERTICAL = 8.dp
private val PICTURE = 48.dp
private val SHORT_PICTURE = 36.dp
private val PICTURE_GAP = 14.dp

private val NAME_SIZE = 19.sp
private val NAME_MIN_SIZE = 6.sp
private val LINE_SIZE = 13.5.sp
private val LINE_MIN_SIZE = 7.sp
private val LINE_HEIGHT = 1.2.em

/** The "Last used" row, at least this high (a touch target and a little). */
private val LAST_USED_HEIGHT = 56.dp
private val SHORT_LAST_USED_HEIGHT = 48.dp
