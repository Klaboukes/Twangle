package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.feature.tuner.R

/**
 * The one-time tip above the string row: "Tap a string to tune only that one", with a small ×.
 * The whole tip is one button that dismisses it, so TalkBack reads the tip and offers "Dismiss tip".
 * It has the string chips' see-through backing, so it stays readable on busy backgrounds.
 */
@Composable
fun StringLockTip(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .clickable(onClickLabel = stringResource(R.string.tuner_string_lock_tip_dismiss), role = Role.Button, onClick = onDismiss)
            .background(colors.surfaceContainerHigh.copy(alpha = 0.85f), shape)
            .border(1.dp, colors.outlineVariant, shape)
            .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.tuner_string_lock_tip),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f, fill = false),
        )
        // The click label already says what tapping does, so TalkBack doesn't need the icon.
        Icon(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun StringLockTipPreview() {
    MaterialTheme { StringLockTip(onDismiss = {}) }
}
