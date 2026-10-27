package io.github.klaboukes.twangle.feature.settings.components

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.PressIndication
import io.github.klaboukes.twangle.core.designsystem.pressFlash
import io.github.klaboukes.twangle.core.designsystem.pressSink
import io.github.klaboukes.twangle.core.designsystem.rememberAnimationsOff

/**
 * A titled grid of look cards, three to a row (two from 1.3× system font on a small window,
 * [lookCardsPerRow]), shared by the style, wave look and meter look pickers so all three work the
 * same. Each card shows [preview] above its [label]. The [selected] card has a ring; tapping a card calls [onSelect]. A name too long for its card gets
 * smaller rather than breaking in the middle of a word. The cards form one selectable group for
 * TalkBack, which reads each card as a radio button with its name and whether it is selected.
 *
 * [demo] is advanced on every animation frame while some of the cards are on screen; the previews
 * read what it moves in their layer and draw phases only, so nothing is recomposed. It is an
 * "infinite" animation, like the tuner's wave view: UI tests skip these. With Android's "Remove
 * animations" ([rememberAnimationsOff]) the cards hold still instead, on the loop's first moment
 * ([STILL_SECONDS] in, so a wave has its shape), and follow the setting if it changes.
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
    val animationsOff = rememberAnimationsOff()
    // The still picture is set up once per demo; the loop then carries on from it if animations come back.
    LaunchedEffect(demo) { holdStill(demo) }
    LaunchedEffect(onScreen, demo, animationsOff) {
        if (!onScreen || animationsOff) return@LaunchedEffect
        var last = withInfiniteAnimationFrameNanos { it }
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                demo.advance((now - last) / 1_000_000_000f, now)
                last = now
            }
        }
    }
    val perRow = lookCardsPerRow(LocalDensity.current.fontScale, LocalSettingsWindow.current)
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
    val shape = MaterialTheme.shapes.medium
    // The press effect, as the metronome's meter cards: the card sinks and lights up, the light over
    // its preview (which fills most of it).
    val press = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .pressSink(press)
            .clip(shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.primary else colors.outlineVariant,
                shape = shape,
            )
            .selectable(selected = selected, interactionSource = press, indication = PressIndication(shape, face = true, above = true), role = Role.RadioButton) {
                press.pressFlash()
                onClick()
            }
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

/** Plays [demo]'s first [STILL_SECONDS] at once, in the steps a smooth frame rate would take. */
internal fun holdStill(demo: LookDemo) {
    repeat(STILL_STEPS) { step -> demo.advance(STILL_SECONDS / STILL_STEPS, (step + 1) * STILL_STEP_NANOS) }
}

/** How far into its loop a still card shows: still flat, with the wave drawn and the needle at rest. */
internal const val STILL_SECONDS = 1f
private const val STILL_STEPS = 60
private const val STILL_STEP_NANOS = 1_000_000_000L / STILL_STEPS

/** A long pause (e.g. coming back to the app) counts as one short step in a preview loop. */
internal const val MAX_STEP_SECONDS = 0.1f

/** The previews move once this much of the grid is on screen (a sliver is enough). */
private const val MIN_VISIBLE = 0.01f

/**
 * How many look cards a row holds at [fontScale] in a settings page of [window]: three, or two from
 * [LARGE_FONT_SCALE] so their names stay big enough to read on a phone's narrow column (about 115 dp a
 * card three to a row on the Xiaomi). A large window ([LARGE_WINDOW_MIN] both ways, which no phone is:
 * the tallest on their side are about 430 dp high) keeps three at any font size (Phase 52): there the
 * column is 600 dp, so three cards are about 184 dp across, as wide as two on a phone, where two came
 * to about 280 × 385 dp each on the Lenovo Tab M11 at its 1.3× text and the six styles took a screen
 * and a half upright, three screens on its side. An unknown window ([DpSize.Unspecified], a picker
 * shown outside a settings page) counts as small.
 */
internal fun lookCardsPerRow(fontScale: Float, window: DpSize): Int {
    val large = window.isSpecified && minOf(window.width, window.height) >= LARGE_WINDOW_MIN
    return if (fontScale >= LARGE_FONT_SCALE && !large) PER_ROW_LARGE_FONT else PER_ROW
}

/** The size of the settings page the cards are in, for [lookCardsPerRow] (`SettingsPage` provides it). */
internal val LocalSettingsWindow = staticCompositionLocalOf { DpSize.Unspecified }

private const val PER_ROW = 3

/** From this system font size on, two cards to a row on a small window, so their names stay big enough to read. */
private const val LARGE_FONT_SCALE = 1.3f
private const val PER_ROW_LARGE_FONT = 2

/** A large window from this much both ways, as the tuner's bigger upright column and Practice's large layouts. */
private val LARGE_WINDOW_MIN = 560.dp

/**
 * The smallest a card's name gets to fit its card: at 2× text that is 12 sp's size, which French
 * "Barres lumineuses" needs in Classic's serif.
 */
private val LABEL_MIN_SIZE = 6.sp
