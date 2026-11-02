package io.github.klaboukes.twangle.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The picker chip (2.0, Phase 70; docs/design/v2-tuner-screens-preview.html): what is being tuned,
 * "Guitar · Standard ▾" or "Any note ▾" ([label]), in a pill at the top centre of the tuner, and a tap
 * ([onClick]) opens the one sheet that picks it ("What are you tuning?"). A long name (a large font, a
 * long language, the player's own tuning) stays on one line and ends in "…", so the chip never grows
 * taller than its row. TalkBack hears [spoken] as the button's name and what a double tap does:
 * "Guitar, Standard, button, double-tap to change instrument or tuning".
 */
@Composable
fun PickerChip(label: String, spoken: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val press = remember { MutableInteractionSource() }
    val action = stringResource(R.string.instrument_button_action)
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = CHIP_HEIGHT)
            .pressSink(press)
            .clip(CircleShape)
            .background(scheme.surface, CircleShape)
            .border(1.dp, scheme.outlineVariant, CircleShape)
            .clickable(interactionSource = press, indication = PressIndication(CircleShape), onClickLabel = action, role = Role.Button) {
                press.pressFlash()
                onClick()
            }
            .semantics { contentDescription = spoken }
            .padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            musicText(label),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .clearAndSetSemantics {},
        )
        Text(
            CARET,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** The chip's pill: 40 dp high (the mock-up's), in a 48 dp touch target. */
private val CHIP_HEIGHT = 40.dp

/** The small down arrow after the name: it opens something from below. */
private const val CARET = "▾"
