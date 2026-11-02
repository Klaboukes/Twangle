package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.StyleButton
import io.github.klaboukes.twangle.feature.tuner.R
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags

/** What [FocusModeButton] does, which decides its icon and name. */
internal enum class FocusAction {
    /** Focus mode off: "full screen" (four corners pointing out; "Focus mode") turns it on. */
    ENTER,

    /** Focus mode on: "exit full screen" (pointing in; "Exit focus mode") turns it off. */
    EXIT,

    /** Focus mode's controls up after a tap: "full screen" again ("Hide controls") takes them away. */
    HIDE_CONTROLS,
}

/**
 * Focus mode's button: the style's button ([StyleButton], Phase 63), the same face and size as the "more tools"
 * button, so it stands out on every style's background (owner, 2026-10-19: the bare icon in the top corner was
 * hardly visible, in Space Age least of all). No word, only the icon for its [action]; TalkBack and the long-press
 * tooltip say its name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FocusModeButton(action: FocusAction, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(
        when (action) {
            FocusAction.ENTER -> R.string.tuner_focus_mode
            FocusAction.EXIT -> R.string.tuner_exit_focus_mode
            FocusAction.HIDE_CONTROLS -> R.string.tuner_hide_controls
        },
    )
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        StyleButton(
            onClick = onClick,
            label = label,
            face = STRUM_TOGGLE_SIZE,
            faceModifier = Modifier
                .testTag(
                    when (action) {
                        FocusAction.ENTER -> TunerTestTags.ENTER_FOCUS
                        FocusAction.EXIT -> TunerTestTags.EXIT_FOCUS
                        FocusAction.HIDE_CONTROLS -> TunerTestTags.HIDE_CONTROLS
                    },
                ),
        ) {
            Icon(
                painterResource(if (action == FocusAction.EXIT) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen),
                contentDescription = null,
                modifier = Modifier.size(FOCUS_ICON_SIZE),
            )
        }
    }
}

private val FOCUS_ICON_SIZE = 24.dp
