package io.github.klaboukes.twangle.feature.practice

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.klaboukes.twangle.core.designsystem.SegmentedSwitch
import io.github.klaboukes.twangle.core.settings.PracticeTool

/**
 * The Practice tab's tool switch (Phase 32, the owner's pick A in docs/design/practice-preview.html):
 * the app's segmented switch, the same as the tuner's mode switch, one segment per tool in [tools]
 * ("Metronome · Chords"), [tool] picked. Each tool shows it at its top. It has room for three tools.
 */
@Composable
fun PracticeToolSwitch(
    tool: PracticeTool,
    onToolChange: (PracticeTool) -> Unit,
    tools: List<PracticeTool>,
    modifier: Modifier = Modifier,
) {
    SegmentedSwitch(
        options = tools,
        selected = tool,
        onSelect = onToolChange,
        label = { stringResource(it.label) },
        tag = PracticeTestTags::tool,
        modifier = modifier.testTag(PracticeTestTags.SWITCH),
    )
}

private val PracticeTool.label: Int
    get() = when (this) {
        PracticeTool.METRONOME -> R.string.practice_tool_metronome
        PracticeTool.CHORDS -> R.string.practice_tool_chords
    }
