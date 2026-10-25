package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.PressIndication
import io.github.klaboukes.twangle.core.designsystem.neonFill
import io.github.klaboukes.twangle.core.designsystem.pickerButtonShape
import io.github.klaboukes.twangle.core.designsystem.pressFlash
import io.github.klaboukes.twangle.core.designsystem.pressSink
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.settings.VisualStyle
import io.github.klaboukes.twangle.feature.tuner.R
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags
import io.github.klaboukes.twangle.feature.tuner.tour.tourControl

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
 * Focus mode's button, drawn like the "more tools" button (the same outline, fill, size and press feel
 * in every style), so it stands out on every style's background (owner, 2026-10-19: the bare icon in
 * the top corner was hardly visible, in Space Age least of all). No word, only the icon for its
 * [action]; TalkBack and the long-press tooltip say its name. It has no tour step of its own;
 * the tour's balloons keep clear of it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FocusModeButton(action: FocusAction, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.styleTokens.style
    val neon = style == VisualStyle.FUTURISTIC
    val shape = pickerButtonShape(style, MaterialTheme.shapes.small)
    val label = stringResource(
        when (action) {
            FocusAction.ENTER -> R.string.tuner_focus_mode
            FocusAction.EXIT -> R.string.tuner_exit_focus_mode
            FocusAction.HIDE_CONTROLS -> R.string.tuner_hide_controls
        },
    )
    val outline = if (neon) scheme.primary.copy(alpha = 0.55f) else scheme.onSurfaceVariant.copy(alpha = 0.6f)
    val fill = if (neon) neonFill(scheme) else scheme.surface.copy(alpha = 0.45f)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        val press = remember { MutableInteractionSource() }
        Box(
            Modifier
                .minimumInteractiveComponentSize()
                .testTag(
                    when (action) {
                        FocusAction.ENTER -> TunerTestTags.ENTER_FOCUS
                        FocusAction.EXIT -> TunerTestTags.EXIT_FOCUS
                        FocusAction.HIDE_CONTROLS -> TunerTestTags.HIDE_CONTROLS
                    },
                )
                .size(STRUM_TOGGLE_SIZE)
                .tourControl()
                .pressSink(press)
                .clip(shape)
                .background(fill, shape)
                .border(1.dp, outline, shape)
                .clickable(press, PressIndication(shape, face = true), role = Role.Button) {
                    press.pressFlash()
                    onClick()
                }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (action == FocusAction.EXIT) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen),
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(FOCUS_ICON_SIZE),
            )
        }
    }
}

private val FOCUS_ICON_SIZE = 24.dp
