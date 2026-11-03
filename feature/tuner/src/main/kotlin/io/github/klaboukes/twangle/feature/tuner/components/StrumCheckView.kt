package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.github.klaboukes.twangle.core.designsystem.StyleButton
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.dsp.strum.StrumVerdict
import io.github.klaboukes.twangle.feature.tuner.R
import io.github.klaboukes.twangle.feature.tuner.StrumString
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags
import io.github.klaboukes.twangle.feature.tuner.TunerUiState

/**
 * A strum of the open strings' mark above a peg (2.0, Phase 74; the fresh-start mock-up's "Every peg says ↑, ↓ or
 * ✓", docs/design/fresh-start-preview.html): an arrow up for a string to tighten ([StrumVerdict.FLAT]), down for one
 * to loosen ([StrumVerdict.SHARP]), a check mark for one in tune, each drawn (not a font's glyph, so every language
 * and face shows the same), and a short dash for a string the strum didn't let the tuner hear. The arrows in the
 * amber of a string far off, the check in the in-tune green, both taken toward the ink on a light background as the
 * word's colour is ([wordInkShare]); the dash muted. A string read less surely (its partials merged with another's,
 * [StrumString.sure]) is paler. It fades in calmly when it comes or changes (none with [animationsOff]). TalkBack
 * hears it on its peg (the peg's state, [strumMarkLabel]), not here. UI tests find it by [TunerTestTags.strumMark] and
 * read its verdict ([StrumMarkVerdict]).
 */
@Composable
fun StrumMark(string: StrumString, index: Int, modifier: Modifier = Modifier, animationsOff: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val tokens = MaterialTheme.styleTokens
    val ink = colors.onBackground
    val background = colors.background
    val inkShare = remember(tokens.farOff, tokens.inTune, ink, background) { wordInkShare(tokens.farOff, tokens.inTune, ink, background) }
    val colour = when (string.verdict) {
        StrumVerdict.IN_TUNE -> mixColour(tokens.inTune, ink, inkShare)
        StrumVerdict.FLAT, StrumVerdict.SHARP -> mixColour(tokens.farOff, ink, inkShare)
        StrumVerdict.NOT_HEARD -> colors.onSurfaceVariant
    }
    // A new verdict comes in calmly: each mark fades from nothing to full once, as the pegs' ticks grow in.
    val shown = remember(string.verdict) { Animatable(if (animationsOff) 1f else 0f) }
    LaunchedEffect(shown) { shown.animateTo(1f, tween(MARK_FADE_MS)) }
    val alpha = if (string.sure) 1f else UNSURE_ALPHA
    Spacer(
        modifier
            .size(MARK_SIZE)
            .clearAndSetSemantics {
                testTag = TunerTestTags.strumMark(index)
                strumMarkVerdict = string.verdict
            }
            .drawWithCache {
                val w = size.width
                val stroke = Stroke(width = MARK_STROKE.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val centre = Offset(w / 2f, size.height / 2f)
                val path = when (string.verdict) {
                    StrumVerdict.FLAT -> arrowPath(centre, w, up = true)
                    StrumVerdict.SHARP -> arrowPath(centre, w, up = false)
                    StrumVerdict.IN_TUNE -> checkPath(centre, w * CHECK_SHARE)
                    StrumVerdict.NOT_HEARD -> Path().apply {
                        moveTo(centre.x - w * DASH_HALF, centre.y)
                        lineTo(centre.x + w * DASH_HALF, centre.y)
                    }
                }
                onDrawBehind { drawPath(path, colour, alpha = alpha * shown.value, style = stroke) }
            },
    )
}

/** An upright arrow [size] px tall about [centre], pointing [up] or down: a shaft and a head of two strokes. */
private fun arrowPath(centre: Offset, size: Float, up: Boolean): Path {
    val half = size * ARROW_HALF_LENGTH
    val tipY = if (up) centre.y - half else centre.y + half
    val tailY = if (up) centre.y + half else centre.y - half
    val head = size * ARROW_HEAD
    val back = if (up) head else -head
    return Path().apply {
        moveTo(centre.x, tailY)
        lineTo(centre.x, tipY)
        moveTo(centre.x - head, tipY + back)
        lineTo(centre.x, tipY)
        lineTo(centre.x + head, tipY + back)
    }
}

/** A check mark centred on [centre], [size] px wide. */
internal fun checkPath(centre: Offset, size: Float): Path = Path().apply {
    moveTo(centre.x - size * 0.45f, centre.y + size * 0.02f)
    lineTo(centre.x - size * 0.12f, centre.y + size * 0.32f)
    lineTo(centre.x + size * 0.45f, centre.y - size * 0.3f)
}

/** A strum's mark as TalkBack says it on its peg: "Tighten", "Loosen", "in tune", "Not heard". */
@Composable
fun strumMarkLabel(verdict: StrumVerdict): String = stringResource(
    when (verdict) {
        StrumVerdict.FLAT -> R.string.tuner_word_tighten
        StrumVerdict.SHARP -> R.string.tuner_word_loosen
        StrumVerdict.IN_TUNE -> R.string.tuner_in_tune
        StrumVerdict.NOT_HEARD -> R.string.tuner_strum_not_heard
    },
)

/**
 * A strum of the open strings counted, in the word's place while no single string is heard: "4 of 6 in tune", or
 * "All strings in tune" once every string is (the strings tuned on their own after the strum count too, see
 * `HeardShown`). Null before the strum's first reading.
 */
@Composable
fun strumCountText(check: TunerUiState.StrumCheck): String? {
    if (check.strings.isEmpty()) return null
    val inTune = check.strings.count { it.verdict == StrumVerdict.IN_TUNE }
    val total = check.strings.size
    return if (inTune == total) {
        stringResource(R.string.tuner_strum_all_in_tune)
    } else {
        pluralStringResource(R.plurals.tuner_strum_count, inTune, inTune, total)
    }
}

/**
 * The strum check's button (2.0, Phase 74, the owner's pick of 2026-11-03 after the guitar check: deciding by itself
 * whether a sound was one string, a strum or a chord flickered): above the pegs at the row's end, its outline in line
 * with the last peg's face, the app's button ([StyleButton]) with 1.1's strum icon and no word, picked (the container
 * colour with an accent outline) while [on]. A tap turns it on: the next strum of the open strings is read and marked
 * above the pegs, and once it has rung out the button turns itself off; a tap while on turns it off. TalkBack hears it
 * as a switch, "Strum check, on"; a long-press shows its name in a tooltip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrumCheckButton(on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.tuner_strum_check)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        StyleButton(
            onClick = { onChange(!on) },
            label = label,
            modifier = modifier,
            face = TUNER_BUTTON_FACE,
            picked = on,
            role = Role.Switch,
            faceModifier = Modifier.semantics {
                testTag = TunerTestTags.STRUM_BUTTON
                toggleableState = ToggleableState(on)
            },
        ) {
            Icon(painterResource(R.drawable.ic_strum), contentDescription = null, modifier = Modifier.size(STRUM_ICON_SIZE))
        }
    }
}

private val STRUM_ICON_SIZE = 24.dp

/** A strum's mark's verdict, for UI tests (TalkBack hears it on the peg). */
val StrumMarkVerdict = SemanticsPropertyKey<StrumVerdict>("StrumMarkVerdict")
var SemanticsPropertyReceiver.strumMarkVerdict by StrumMarkVerdict

/** A mark's square, above its peg's face: the mock-up's 18 px glyph. */
internal val MARK_SIZE = 18.dp

/** Between a mark and the face of the peg under it. */
internal val MARK_GAP = 4.dp

private val MARK_STROKE = 2.4.dp
private const val ARROW_HALF_LENGTH = 0.4f
private const val ARROW_HEAD = 0.26f
private const val CHECK_SHARE = 0.95f
private const val DASH_HALF = 0.22f
private const val UNSURE_ALPHA = 0.6f
private const val MARK_FADE_MS = 300
