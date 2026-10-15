package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.klaboukes.twangle.feature.tuner.R
import io.github.klaboukes.twangle.feature.tuner.SignalBars
import io.github.klaboukes.twangle.feature.tuner.SignalFigures
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags
import kotlinx.coroutines.flow.Flow
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Small signal bars, like a phone's, for the optional "Show signal bars" setting: how many light
 * follows how well the note is heard ([reception], 0..1, null without a note; see [SignalBars], which
 * keeps them from flickering). Without a note the last count stays, dimmed. Drawn in [tint].
 *
 * A tap opens a small panel with the [figures] behind them: the volume, the room's background and the
 * note's clarity, collected only while the panel is open. TalkBack hears "Signal bars: 3 of 4" and
 * can open the panel the same way; the count isn't announced as it changes. The button reaches
 * past the bars to 48 dp each way, so it's easy to hit, while the bars keep their small slot.
 */
@Composable
fun SignalBarsButton(
    reception: () -> Float?,
    figures: Flow<SignalFigures?>,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val currentReception by rememberUpdatedState(reception)
    val counter = remember { SignalBars() }
    // Recomposes only when the count or "a note sounds" changes, not with every reading.
    val shown by remember { derivedStateOf { counter.update(currentReception()) to counter.hasNote } }
    val (bars, hasNote) = shown
    var open by remember { mutableStateOf(false) }
    val description = if (hasNote || bars > 0) {
        stringResource(R.string.tuner_signal_bars, bars, SignalBars.MAX_BARS)
    } else {
        stringResource(R.string.tuner_signal_no_note)
    }
    val showFigures = stringResource(R.string.tuner_signal_show_figures)
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().testTag(TunerTestTags.SIGNAL_BARS_ICON)) {
            val slot = size.width / SignalBars.MAX_BARS
            val barWidth = slot * BAR_WIDTH_SHARE
            val bottom = size.height * BARS_BOTTOM
            val tallest = size.height * BARS_HEIGHT
            val lit = tint.copy(alpha = if (hasNote) 1f else DIMMED_ALPHA)
            val unlit = tint.copy(alpha = UNLIT_ALPHA)
            for (i in 0 until SignalBars.MAX_BARS) {
                val height = tallest * (i + 1) / SignalBars.MAX_BARS
                drawRoundRect(
                    color = if (i < bars) lit else unlit,
                    topLeft = Offset(slot * i + (slot - barWidth) / 2f, bottom - height),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(barWidth / 3f),
                )
            }
        }
        // The button: at least 48 dp each way around the small bars, without taking more room than they do.
        Box(
            Modifier
                .touchArea(MIN_TOUCH)
                .testTag(TunerTestTags.SIGNAL_BARS)
                .clickable(onClickLabel = showFigures, role = Role.Button) { open = true }
                .semantics { contentDescription = description },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SignalFiguresPanel(figures)
        }
    }
}

/**
 * At least [side] each way, centred on the space this is given, which it reports as its own size:
 * a touch area that reaches past a small drawing without moving anything around it.
 */
private fun Modifier.touchArea(side: Dp): Modifier = layout { measurable, constraints ->
    val min = side.roundToPx()
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val placeable = measurable.measure(Constraints.fixed(max(min, width), max(min, height)))
    layout(width, height) { placeable.place((width - placeable.width) / 2, (height - placeable.height) / 2) }
}

/** The figures behind the bars, read live while the panel is open. */
@Composable
private fun SignalFiguresPanel(figures: Flow<SignalFigures?>) {
    val current by figures.collectAsStateWithLifecycle(initialValue = null)
    val unknown = stringResource(R.string.tuner_signal_unknown)
    Column(
        modifier = Modifier
            .widthIn(min = 200.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(TunerTestTags.SIGNAL_FIGURES),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.tuner_signal_title), style = MaterialTheme.typography.titleSmall)
        FigureRow(stringResource(R.string.tuner_signal_volume), decibels(current?.levelDbfs) ?: unknown)
        FigureRow(stringResource(R.string.tuner_signal_background), decibels(current?.noiseFloorDbfs) ?: unknown)
        val clarity = current?.clarity?.takeUnless { it.isNaN() }
        FigureRow(
            stringResource(R.string.tuner_signal_clarity),
            if (clarity != null) stringResource(R.string.tuner_signal_percent, (clarity * 100f).roundToInt()) else unknown,
        )
    }
}

/** "−32 dB", or null when [dbfs] isn't known. */
@Composable
private fun decibels(dbfs: Float?): String? =
    dbfs?.takeUnless { it.isNaN() }?.let { stringResource(R.string.tuner_signal_db, it.roundToInt()) }

@Composable
private fun FigureRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** The smallest touch area, each way (Android's accessibility guideline). */
private val MIN_TOUCH = 48.dp

/** Share of a bar's slot it fills; the rest is the gap. */
private const val BAR_WIDTH_SHARE = 0.62f

/** The bars stand on this share of the box's height and reach up to [BARS_HEIGHT] of it. */
private const val BARS_BOTTOM = 0.88f
private const val BARS_HEIGHT = 0.76f

/** The last note's bars, while no note sounds. */
private const val DIMMED_ALPHA = 0.45f
private const val UNLIT_ALPHA = 0.2f
