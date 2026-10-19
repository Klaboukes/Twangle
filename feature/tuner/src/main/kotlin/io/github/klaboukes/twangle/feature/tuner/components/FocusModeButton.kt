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
import io.github.klaboukes.twangle.feature.tuner.tour.TourPart
import io.github.klaboukes.twangle.feature.tuner.tour.tourTarget

/**
 * Focus mode's button, right above the "more tools" button and drawn like it (the same outline, fill,
 * size and press feel in every style), so it stands out on every style's background (owner,
 * 2026-10-19: the bare icon in the top corner was hardly visible, in Space Age least of all). No
 * word: with focus mode off ([on] false) the "full screen" icon (four corners pointing out; TalkBack
 * and the long-press tooltip "Focus mode"), with it on the "exit full screen" icon (pointing in; "Exit
 * focus mode"). Its outline is the tour's focus mode step.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FocusModeButton(on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.styleTokens.style
    val neon = style == VisualStyle.FUTURISTIC
    val shape = pickerButtonShape(style, MaterialTheme.shapes.small)
    val label = stringResource(if (on) R.string.tuner_exit_focus_mode else R.string.tuner_focus_mode)
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
                .testTag(if (on) TunerTestTags.EXIT_FOCUS else TunerTestTags.ENTER_FOCUS)
                .size(STRUM_TOGGLE_SIZE)
                .tourTarget(TourPart.FOCUS)
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
                painterResource(if (on) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen),
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(FOCUS_ICON_SIZE),
            )
        }
    }
}

private val FOCUS_ICON_SIZE = 24.dp
