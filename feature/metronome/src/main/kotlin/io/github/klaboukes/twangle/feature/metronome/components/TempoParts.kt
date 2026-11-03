package io.github.klaboukes.twangle.feature.metronome.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.LINING_FIGURES
import io.github.klaboukes.twangle.core.designsystem.NumberFont
import io.github.klaboukes.twangle.core.designsystem.StepperButton
import io.github.klaboukes.twangle.core.designsystem.appLocale
import io.github.klaboukes.twangle.core.designsystem.localDigits
import io.github.klaboukes.twangle.core.dsp.metronome.ClickTrack
import io.github.klaboukes.twangle.core.dsp.metronome.TempoMarking
import io.github.klaboukes.twangle.feature.metronome.MetronomeTestTags
import io.github.klaboukes.twangle.feature.metronome.R

/*
 * The tempo as the screen (2.0, Phase 75; the first 2.0 mock-up, docs/design/fresh-start-preview.html,
 * `.bpm`): the tempo big in the middle with − and + either side, and under it "beats per minute ·
 * Allegro". The ruler under them ([TempoRuler]) sets it by dragging.
 */

/** The Italian name of [marking] ("Andante"), the same in every language. */
@Composable
internal fun markingName(marking: TempoMarking): String = stringResource(
    when (marking) {
        TempoMarking.GRAVE -> R.string.metronome_marking_grave
        TempoMarking.LARGO -> R.string.metronome_marking_largo
        TempoMarking.LARGHETTO -> R.string.metronome_marking_larghetto
        TempoMarking.ADAGIO -> R.string.metronome_marking_adagio
        TempoMarking.ANDANTE -> R.string.metronome_marking_andante
        TempoMarking.MODERATO -> R.string.metronome_marking_moderato
        TempoMarking.ALLEGRO -> R.string.metronome_marking_allegro
        TempoMarking.PRESTO -> R.string.metronome_marking_presto
        TempoMarking.PRESTISSIMO -> R.string.metronome_marking_prestissimo
    },
)

/**
 * The tempo [bpm] in figures, as large as [room] lets them be (from [FIGURES_SMALLEST] to
 * [FIGURES_LARGEST] high, the same size at every tempo: sized by the widest, 250), − on the left and + on
 * the right ([onStep], one BPM; on the left and right in every language, like the ruler), and under them
 * "beats per minute · Andante". The figures do not grow with the font size, like the tuner's note; the
 * words under them do, and shrink to one line where they must. [glow] (0..1, read while drawing) is the
 * soft accent glow behind the figures when a tempo Listen found is used. TalkBack hears − and + by
 * name; the figures and words are the ruler's to say ([TempoRuler]), so they are hidden from it here.
 */
@Composable
internal fun TempoHeading(
    bpm: Int,
    onStep: (Int) -> Unit,
    room: Dp,
    modifier: Modifier = Modifier,
    glow: () -> Float = { 0f },
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val locale = appLocale()
    val widest = localDigits(ClickTrack.MAX_BPM, locale)
    val accent = scheme.primary
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            // The figures' room across: the row less − and + and the gaps beside them.
            val across = maxWidth - (STEP_FACE + STEP_GAP) * 2
            val wordsHeight = WORDS_SIZE * WORDS_LINE * density.fontScale.coerceAtMost(MAX_WORDS_SCALE)
            val down = (room - wordsHeight - WORDS_GAP).coerceIn(FIGURES_SMALLEST, FIGURES_LARGEST)
            val figures = remember(widest, across, down, density) {
                // The figures' size in dp: as tall as [down] allows, as wide as [across] allows.
                val probe = TextStyle(fontFamily = NumberFont, fontSize = with(density) { 100.dp.toSp() }, fontWeight = FontWeight.ExtraBold, letterSpacing = FIGURES_SPACING, fontFeatureSettings = "$LINING_FIGURES, tnum")
                val width = with(density) { measurer.measure(widest, probe).size.width.toDp() }
                minOf(down, 100.dp * (across / width).coerceAtLeast(0f))
            }
            val style = TextStyle(
                fontFamily = NumberFont,
                fontSize = with(density) { figures.toSp() },
                lineHeight = with(density) { figures.toSp() },
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = FIGURES_SPACING,
                fontFeatureSettings = "$LINING_FIGURES, tnum",
                textAlign = TextAlign.Center,
                color = scheme.onBackground,
            )
            // − slower on the left, + faster on the right, in every language.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(STEP_GAP)) {
                    StepButton(faster = false, onClick = { onStep(-1) })
                    Box(
                        Modifier
                            .widthIn(min = across.coerceAtLeast(0.dp))
                            .drawBehind {
                                val level = glow()
                                if (level > 0.004f) {
                                    val radius = size.height * 0.9f
                                    drawCircle(
                                        Brush.radialGradient(listOf(accent.copy(alpha = 0.28f * level), accent.copy(alpha = 0f)), center = center, radius = radius),
                                        radius,
                                        center,
                                    )
                                }
                            }
                            .clearAndSetSemantics {},
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(localDigits(bpm, locale), style = style, maxLines = 1, softWrap = false, modifier = Modifier.testTag(MetronomeTestTags.BPM))
                    }
                    StepButton(faster = true, onClick = { onStep(+1) })
                }
            }
        }
        Box(Modifier.height(WORDS_GAP))
        TempoWords(markingName(TempoMarking.of(bpm)))
    }
}

/** "beats per minute · Andante" under the figures, muted, one line, set smaller where a language or large text needs it. */
@Composable
private fun TempoWords(marking: String) {
    val density = LocalDensity.current
    val scale = density.fontScale.coerceAtMost(MAX_WORDS_SCALE)
    val size = with(density) { (WORDS_SIZE * scale).toSp() }
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = size, lineHeight = WORDS_LINE.em, textAlign = TextAlign.Center)
    // The ruler says the tempo to TalkBack; the words are only for the eye.
    Box(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
        Text(
            stringResource(R.string.metronome_tempo_line, marking),
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = with(density) { MIN_NAME_SIZE.toSp() }, maxFontSize = size, stepSize = 0.5.sp),
            modifier = Modifier.fillMaxWidth().testTag(MetronomeTestTags.TEMPO_WORDS),
        )
    }
}

/** − or + beside the figures: the shared stepper ([StepperButton]), [STEP_FACE] across. */
@Composable
private fun StepButton(faster: Boolean, onClick: () -> Unit) {
    StepperButton(
        more = faster,
        label = stringResource(if (faster) R.string.metronome_faster else R.string.metronome_slower),
        onClick = onClick,
        modifier = Modifier.testTag(if (faster) MetronomeTestTags.FASTER else MetronomeTestTags.SLOWER),
        face = STEP_FACE,
        icon = STEP_ICON,
    )
}

/** − and + : the mock-up's 56 dp faces, their icons half that. */
private val STEP_FACE = 56.dp
private val STEP_ICON = 28.dp

/** Between − or + and the figures' room. */
private val STEP_GAP = 8.dp

/** The figures are at most this high (the mock-up's 132 px on a 390 px phone, a little less) and at least this. */
private val FIGURES_LARGEST = 120.dp
private val FIGURES_SMALLEST = 56.dp

/** The figures stand a little closer together, as the mock-up's (−0.05 em). */
private val FIGURES_SPACING = (-0.04).em

/** The words under the figures: 16 at 1× (in dp), growing with the font up to [MAX_WORDS_SCALE]. */
private val WORDS_SIZE = 16.dp
private const val WORDS_LINE = 1.3f
private const val MAX_WORDS_SCALE = 1.5f
private val WORDS_GAP = 2.dp
