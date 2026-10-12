package io.github.klaboukes.twangle.feature.settings.components

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A titled grid of look cards, three to a row (two from 1.3× system font), shared by the wave look
 * and meter look pickers so both work the same. Each card shows [preview] above its [label]. The
 * [selected] card has a ring; tapping a card calls [onSelect]. A name too long for its card gets
 * smaller rather than breaking in the middle of a word. The cards form one selectable group for
 * TalkBack, which reads each card as a radio button with its name and whether it is selected.
 *
 * [demo] is advanced on every animation frame while some of the cards are on screen; the previews
 * read what it moves in their layer and draw phases only, so nothing is recomposed. It is an
 * "infinite" animation, like the tuner's wave view: UI tests skip these.
 */
@Composable
internal fun <T> LookCardGrid(
    title: String,
    looks: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    cardTag: (T) -> String,
    label: @Composable (T) -> String,
    demo: LookDemo,
    modifier: Modifier = Modifier,
    preview: @Composable ColumnScope.(look: T, selected: Boolean) -> Unit,
) {
    var onScreen by remember { mutableStateOf(false) }
    LaunchedEffect(onScreen, demo) {
        if (!onScreen) return@LaunchedEffect
        var last = withInfiniteAnimationFrameNanos { it }
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                demo.advance((now - last) / 1_000_000_000f, now)
                last = now
            }
        }
    }
    val perRow = if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) PER_ROW_LARGE_FONT else PER_ROW
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Column(
            modifier = Modifier
                .selectableGroup()
                .onVisibilityChanged(minFractionVisible = MIN_VISIBLE) { onScreen = it },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            looks.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { look ->
                        val isSelected = look == selected
                        LookCard(
                            label = label(look),
                            selected = isSelected,
                            onClick = { onSelect(look) },
                            modifier = Modifier.weight(1f).testTag(cardTag(look)),
                        ) { preview(look, isSelected) }
                    }
                    // Keeps the cards of a shorter last row as wide as the others.
                    repeat(perRow - row.size) { Column(Modifier.weight(1f)) {} }
                }
            }
        }
    }
}

/** The short loop a picker's previews play, the same for every card. */
internal interface LookDemo {
    /** Moves the loop on by [seconds] (at most [MAX_STEP_SECONDS] are used) to the frame at [frameNanos]. */
    fun advance(seconds: Float, frameNanos: Long)
}

@Composable
private fun LookCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.medium)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.primary else colors.outlineVariant,
                shape = MaterialTheme.shapes.medium,
            )
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        preview()
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            // One line, shrinking to fit: a long name never breaks in the middle of a word.
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = LABEL_MIN_SIZE,
                maxFontSize = MaterialTheme.typography.bodyMedium.fontSize,
                stepSize = 1.sp,
            ),
        )
    }
}

/** A long pause (e.g. coming back to the app) counts as one short step in a preview loop. */
internal const val MAX_STEP_SECONDS = 0.1f

/** The previews move once this much of the grid is on screen (a sliver is enough). */
private const val MIN_VISIBLE = 0.01f

private const val PER_ROW = 3

/** From this system font size on, two cards to a row, so their names stay big enough to read. */
private const val LARGE_FONT_SCALE = 1.3f
private const val PER_ROW_LARGE_FONT = 2

/** The smallest a card's name gets to fit its card. */
private val LABEL_MIN_SIZE = 8.sp
