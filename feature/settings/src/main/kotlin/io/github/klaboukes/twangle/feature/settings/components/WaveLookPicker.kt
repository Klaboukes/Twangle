package io.github.klaboukes.twangle.feature.settings.components

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.dial.WaveMotion
import io.github.klaboukes.twangle.core.designsystem.dial.drawWave
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.settings.WaveLook
import io.github.klaboukes.twangle.feature.settings.R
import kotlin.math.abs
import kotlin.math.exp

/**
 * A titled grid of cards, three to a row, to pick the wave view's look from. Each card shows its
 * look moving, drawn in the current style, with its [label] under it. All cards play the same short
 * loop ([WaveDemo]): flat, in tune, sharp, in tune. The [selected] card has a ring; tapping a card
 * calls [onSelect]. On the note they breathe, or hold still without [breathesInTune], as the tuner's
 * wave does. The cards form one selectable group for TalkBack, which reads each card's name.
 *
 * The loop only runs while some of the cards are on screen, and each frame only redraws the cards'
 * previews: nothing is recomposed.
 */
@Composable
fun WaveLookPicker(
    title: String,
    selected: WaveLook,
    onSelect: (WaveLook) -> Unit,
    modifier: Modifier = Modifier,
    breathesInTune: Boolean = true,
    label: @Composable (WaveLook) -> String,
) {
    val demo = remember { WaveDemo() }
    SideEffect { demo.motion.breathesInTune = breathesInTune }
    var onScreen by remember { mutableStateOf(false) }
    LaunchedEffect(onScreen) {
        if (!onScreen) return@LaunchedEffect
        // An "infinite" animation, like the tuner's wave view: UI tests skip these.
        var last = withInfiniteAnimationFrameNanos { it }
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                demo.advance((now - last) / 1_000_000_000f)
                last = now
                demo.frame.longValue = now
            }
        }
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Column(
            modifier = Modifier
                .selectableGroup()
                .onVisibilityChanged(minFractionVisible = MIN_VISIBLE) { onScreen = it },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WaveLook.entries.chunked(PER_ROW).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { look ->
                        WaveLookCard(
                            look = look,
                            demo = demo,
                            label = label(look),
                            selected = look == selected,
                            onClick = { onSelect(look) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Keeps the cards of a shorter last row as wide as the others.
                    repeat(PER_ROW - row.size) { Column(Modifier.weight(1f)) {} }
                }
            }
        }
    }
}

@Composable
private fun WaveLookCard(
    look: WaveLook,
    demo: WaveDemo,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val tokens = MaterialTheme.styleTokens
    val style = tokens.style
    val offColor = if (selected) colors.primary else colors.onSurfaceVariant
    val tuneColor = tokens.inTune
    val path = remember { Path() }
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
        // Its own layer, so a new frame redraws only the preview.
        Canvas(modifier = Modifier.fillMaxWidth().height(PREVIEW_HEIGHT).graphicsLayer()) {
            demo.frame.longValue // Redraw on every animation frame.
            val motion = demo.motion
            drawWave(
                look = look,
                style = style,
                color = lerp(offColor, tuneColor, demo.tuned),
                beats = motion.beats,
                phase = motion.phase,
                depth = motion.depth,
                level = motion.swell,
                glow = demo.tuned,
                lean = motion.lean,
                breath = motion.breath,
                path = path,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
        )
    }
}

/**
 * The short loop the previews play, the same for every card, over [DEMO_SECONDS]: [DEMO_FLAT_CENTS]
 * off, then tuned up to the note (slowing down over the last few cents, as when turning a peg), in
 * tune, [DEMO_SHARP_CENTS] off, tuned down to the note, in tune again. [tuned] eases between 0 and
 * 1 for the "in tune" colour and halo.
 */
private class WaveDemo {
    val motion = WaveMotion()
    val frame = mutableLongStateOf(0L)
    var tuned = 0f
        private set
    private var clock = 0f

    fun advance(seconds: Float) {
        val dt = seconds.coerceIn(0f, MAX_STEP_SECONDS)
        clock = (clock + dt) % DEMO_SECONDS
        val cents = when {
            clock < 2f -> DEMO_FLAT_CENTS
            clock < 3f -> DEMO_FLAT_CENTS * pegTurn(clock - 2f)
            clock < 5f -> 0f
            clock < 7f -> DEMO_SHARP_CENTS
            clock < 8f -> DEMO_SHARP_CENTS * pegTurn(clock - 7f)
            else -> 0f
        }
        motion.advance(dt, cents)
        val inTune = abs(cents) < DEMO_IN_TUNE_CENTS
        tuned += ((if (inTune) 1f else 0f) - tuned) * (1f - exp(-dt * TUNE_RATE))
    }

    /** How much of the way off is left [t] (0..1) into turning the peg: fast at first, slow near the note. */
    private fun pegTurn(t: Float): Float {
        val left = 1f - t
        return left * left * left
    }
}

private const val DEMO_SECONDS = 10f
private const val DEMO_FLAT_CENTS = -18f
private const val DEMO_SHARP_CENTS = 14f

/** The previews turn the "in tune" colour this close to the note, as the tuner does when set to precise. */
private const val DEMO_IN_TUNE_CENTS = 2f

/** How quickly the colour turns to "in tune" and back (per second). */
private const val TUNE_RATE = 3f

/** A long pause (e.g. coming back to the app) counts as one short step. */
private const val MAX_STEP_SECONDS = 0.1f

/** The previews move once this much of the grid is on screen (a sliver is enough). */
private const val MIN_VISIBLE = 0.01f

private const val PER_ROW = 3
private val PREVIEW_HEIGHT = 32.dp

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun WaveLookPickerPreview() {
    MaterialTheme {
        WaveLookPicker(
            title = stringResource(R.string.settings_wave_look_title),
            selected = WaveLook.STRIPES,
            onSelect = {},
            modifier = Modifier.padding(16.dp),
        ) { it.name.lowercase() }
    }
}
