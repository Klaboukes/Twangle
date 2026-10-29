package io.github.klaboukes.twangle.feature.practice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.ScreenDensity
import io.github.klaboukes.twangle.core.designsystem.pressFeedback
import io.github.klaboukes.twangle.core.designsystem.pressFlash
import io.github.klaboukes.twangle.core.settings.PracticeTool

/**
 * The Practice tab's tool button (Phase 47, the owner's pick A in docs/design/tool-button-preview.html),
 * in place of the segmented switch it had until then: [tool]'s icon and name with ⌄ after them
 * ("Chords ⌄"), at the top start of every tool. A tap opens a short list of [tools] with their icons,
 * [tool] marked in the container colour with an accent outline, on a soft lift with no border; a
 * pick goes to [onToolChange] and closes it, as do Back and a tap beside it.
 *
 * One steady size whatever the tool and the language: [TOOL_BUTTON_WIDTH] (the longest names,
 * "Kapodaster", "Capotraste", "メトロノーム", fit at the usual text size) in a 48 dp touch target,
 * the name set smaller where it doesn't fit (large text). TalkBack hears "Tool, Chords, drop-down
 * list", and the list as a group ("Tools") of radio buttons by name.
 */
@Composable
fun PracticeToolButton(
    tool: PracticeTool,
    onToolChange: (PracticeTool) -> Unit,
    tools: List<PracticeTool>,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    val label = stringResource(R.string.practice_tool_button)
    val name = stringResource(tool.label)
    Box(modifier) {
        Box(
            Modifier
                .testTag(PracticeTestTags.TOOL_BUTTON)
                .size(TOOL_BUTTON_WIDTH, TOUCH_HEIGHT)
                .clickable(interactionSource = source, indication = null) {
                    source.pressFlash()
                    open = true
                }
                // Its texts stay in the tree for the tests' checks; TalkBack says the description and the tool.
                .semantics {
                    role = Role.DropdownList
                    contentDescription = label
                    stateDescription = name
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier
                    .size(TOOL_BUTTON_WIDTH, FACE_HEIGHT)
                    .pressFeedback(source, MaterialTheme.shapes.small, fill = faceFill(picked = false), outline = faceOutline(picked = false))
                    .padding(start = 10.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(practiceToolIcon(tool), contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(ICON_SIZE))
                Spacer(Modifier.width(8.dp))
                ToolName(name, MaterialTheme.colorScheme.onBackground, Modifier.weight(1f))
                Icon(
                    painterResource(R.drawable.ic_expand_more),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp).size(20.dp),
                )
            }
        }
        ToolList(open, tool, tools, onDismiss = { open = false }, onPick = { picked ->
            open = false
            if (picked != tool) onToolChange(picked)
        })
    }
}

/** The tools' list under the button: a Material menu (a soft lift, no border), each tool a 48 dp row of icon and name. */
@Composable
private fun ToolList(open: Boolean, tool: PracticeTool, tools: List<PracticeTool>, onDismiss: () -> Unit, onPick: (PracticeTool) -> Unit) {
    val density = LocalDensity.current
    val title = stringResource(R.string.practice_tool_list)
    DropdownMenu(
        expanded = open,
        onDismissRequest = onDismiss,
        // The menu is a window of its own: UI Automator finds its rows by tag only when it says so too (as MainActivity's root does).
        modifier = Modifier.semantics { testTagsAsResourceId = true },
    ) {
        ScreenDensity(density) {
            // A known width: a menu asks its content's intrinsic size, which a name set to fit can't give.
            Column(
                Modifier
                    .width(LIST_WIDTH)
                    .padding(horizontal = LIST_PADDING)
                    .selectableGroup()
                    .semantics { contentDescription = title },
            ) {
                for (option in tools) ToolRow(option, picked = option == tool, onPick = { onPick(option) })
            }
        }
    }
}

/** One tool in the list: its icon and name; the picked one in the container colour with a 2 dp accent outline, as the metronome's Sound panel. */
@Composable
private fun ToolRow(tool: PracticeTool, picked: Boolean, onPick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Row(
        Modifier
            .testTag(PracticeTestTags.tool(tool))
            .fillMaxWidth()
            .heightIn(min = TOUCH_HEIGHT)
            .pressFeedback(source, MaterialTheme.shapes.medium, fill = if (picked) faceFill(picked = true) else Color.Transparent, outline = if (picked) faceOutline(picked = true) else null)
            .selectable(selected = picked, interactionSource = source, indication = null, role = Role.RadioButton) {
                source.pressFlash()
                onPick()
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (picked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        Icon(practiceToolIcon(tool), contentDescription = null, tint = color, modifier = Modifier.size(ICON_SIZE))
        Spacer(Modifier.width(12.dp))
        ToolName(stringResource(tool.label), color, Modifier.weight(1f))
    }
}

/** A tool's name on one line in [color], set smaller where it doesn't fit (large text, long names). */
@Composable
private fun ToolName(text: String, color: Color, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.labelLarge.copy(fontSize = NAME_SIZE)
    Text(
        text,
        style = style,
        color = color,
        maxLines = 1,
        // Never wrapped, so the auto-size sees a name too wide (Ear training's AnswerButton in IntervalsPart.kt says
        // why).
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = NAME_MIN_SIZE, maxFontSize = NAME_SIZE, stepSize = 0.5.sp),
        modifier = modifier,
    )
}

/** Each tool's name, as the button and the list show it. A new tool needs its name here (the `when` says so). */
internal val PracticeTool.label: Int
    get() = when (this) {
        PracticeTool.METRONOME -> R.string.practice_tool_metronome
        PracticeTool.CHORDS -> R.string.practice_tool_chords
        PracticeTool.SCALES -> R.string.practice_tool_scales
        PracticeTool.CAPO -> R.string.practice_tool_capo
        PracticeTool.EAR_TRAINING -> R.string.practice_tool_ear_training
    }

/** The button's width, the same for every tool and language; its outline [FACE_HEIGHT] tall in a 48 dp touch target. */
internal val TOOL_BUTTON_WIDTH = 160.dp
private val FACE_HEIGHT = 44.dp
private val TOUCH_HEIGHT = 48.dp
private val ICON_SIZE = 24.dp

/** The list: this wide (a little wider than the button), its rows this far in. */
private val LIST_WIDTH = 200.dp
private val LIST_PADDING = 6.dp

/** The name's size, as the pickers' labels, and the smallest it is set to fit (2× text in the wide styles' letters). */
private val NAME_SIZE = 16.sp
private val NAME_MIN_SIZE = 6.sp
