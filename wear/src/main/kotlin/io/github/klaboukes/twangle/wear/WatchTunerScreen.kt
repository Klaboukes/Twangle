package io.github.klaboukes.twangle.wear

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Note
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The watch app: the microphone permission first, then the tuner. [tuner] is only asked for (and
 * so the microphone only opened) once the permission is granted. While the tuner is shown, the
 * watch's screen state goes to the view model, which lets go of the mic when the screen goes off,
 * and the screen is held on while the tuner hears playing.
 */
@Composable
fun WatchTunerApp(tuner: @Composable () -> WatchTunerViewModel) {
    WatchTheme {
        AppScaffold {
            val permission = rememberWatchMicPermission()
            if (permission.granted) {
                val viewModel = tuner()
                val awake = rememberWatchScreenAwake(viewModel.state)
                ScreenOnEffect { on ->
                    viewModel.setScreenOn(on)
                    if (on) awake.touched()
                }
                LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.shown() }
                val state by viewModel.state.collectAsStateWithLifecycle()
                // The ring runs along the edge, where the time would be, so the tuner leaves it out.
                ScreenScaffold(timeText = {}) {
                    WatchTuner(state, onRetry = viewModel::retry)
                }
            } else {
                ScreenScaffold {
                    Message(
                        text = stringResource(R.string.wear_mic_needed),
                        action = stringResource(if (permission.needsSettings) R.string.wear_open_settings else R.string.wear_allow_mic),
                        onAction = permission.onAction,
                    )
                }
            }
        }
    }
}

/**
 * The tuner: a ring along the edge of the screen with the cents scale at the top (♭ and ♯ at its
 * ends) and a needle pointing in from the ring, the note large in the middle, the cents under it and
 * the ribbon under them. When the note is in tune the ring, the needle, the note and the ribbon glow
 * in the in-tune colour, easing in and out. A failure shows a message instead. Stateless.
 */
@Composable
fun WatchTuner(state: WatchTunerState, onRetry: () -> Unit) {
    if (state is WatchTunerState.Failed) {
        Message(
            text = stringResource(
                when (state.error) {
                    WatchTunerError.PERMISSION_MISSING -> R.string.wear_mic_needed
                    WatchTunerError.MICROPHONE_BUSY -> R.string.wear_mic_busy
                    WatchTunerError.MICROPHONE_UNAVAILABLE -> R.string.wear_mic_unavailable
                },
            ),
            action = stringResource(R.string.wear_try_again),
            onAction = onRetry,
        )
        return
    }
    val detected = state as? WatchTunerState.Detected
    val glow by animateFloatAsState(
        targetValue = if (detected?.inTune == true) 1f else 0f,
        animationSpec = tween(WatchDial.GLOW_MILLIS, easing = FastOutSlowInEasing),
        label = "inTuneGlow",
    )
    val markerAlpha by animateFloatAsState(
        targetValue = if (detected != null) 1f else 0f,
        animationSpec = tween(WatchDial.FADE_MILLIS),
        label = "markerAlpha",
    )
    // Without a note the marker drifts back to the centre while it fades.
    val markerCents by animateFloatAsState(
        targetValue = detected?.cents?.toFloat() ?: 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "markerCents",
    )

    val needle = remember { Path() }

    BoxWithConstraints(Modifier.fillMaxSize().testTag(WatchDial.TAG), contentAlignment = Alignment.Center) {
        val dial = min(maxWidth, maxHeight)
        Canvas(Modifier.fillMaxSize()) {
            drawDial(cents = markerCents, markerAlpha = markerAlpha, glow = glow, needle = needle)
        }
        DialSign("♭", -WatchDial.MAX_CENTS, dial)
        DialSign("♯", WatchDial.MAX_CENTS, dial)
        AnimatedContent(
            targetState = state,
            contentKey = { it is WatchTunerState.Detected },
            transitionSpec = { fadeIn(tween(WatchDial.FADE_MILLIS)) togetherWith fadeOut(tween(WatchDial.FADE_MILLIS)) },
            label = "reading",
        ) { shown ->
            if (shown is WatchTunerState.Detected) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Reading(shown, dial, glow = { glow })
                    WatchRibbon(
                        cents = shown.cents,
                        glow = { glow },
                        modifier = Modifier
                            .absoluteOffset(y = dial * WatchDial.RIBBON_Y)
                            .size(dial * WatchDial.RIBBON_WIDTH, dial * WatchDial.RIBBON_HEIGHT),
                    )
                }
            } else {
                Box(Modifier.fillMaxSize().padding(horizontal = dial * 0.18f), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.wear_play_note),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        color = WatchColors.muted,
                    )
                }
            }
        }
    }
}

/** The note (letter large, octave small beside it) and the cents under it, sized to the [dial]. */
@Composable
private fun Reading(reading: WatchTunerState.Detected, dial: Dp, glow: () -> Float) {
    val density = LocalDensity.current
    // Sized to the screen, not the font setting: the note is already as large as the dial allows.
    fun Dp.fixedSp() = with(density) { toSp() }
    val noteColor = lerp(WatchColors.ink, WatchColors.inTune, glow())
    val inTuneText = stringResource(R.string.wear_in_tune)
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {
            if (reading.inTune) stateDescription = inTuneText
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val locale = LocalConfiguration.current.locales[0]
        // Written as players of the watch's language usually write notes (Do Re Mi in French, H in
        // German); the watch has no notation setting.
        val name = WatchNoteName.letter(reading.note, Notation.forLanguage(locale.language))
        // A note name reads "A4" in every language, so the octave stays right of the letter in
        // right-to-left languages too.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row {
                Text(
                    noteNameText(name),
                    modifier = Modifier.alignByBaseline(),
                    color = noteColor,
                    style = TextStyle(
                        fontSize = (dial * WatchDial.NOTE_SIZE * WatchNoteName.sizeFor(name)).fixedSp(),
                        fontWeight = FontWeight.Light,
                    ),
                )
                Text(
                    WatchNoteName.octave(reading.note),
                    modifier = Modifier.alignByBaseline(),
                    color = WatchColors.muted,
                    style = TextStyle(fontSize = (dial * WatchDial.OCTAVE_SIZE).fixedSp()),
                )
            }
        }
        WatchCents(
            reading.cents,
            style = TextStyle(color = WatchColors.muted, fontSize = (dial * WatchDial.CENTS_SIZE).fixedSp()),
        )
    }
}

/**
 * ♭ or ♯ just beyond one end of the cents scale (its [cents], −50 or +50), inside the ring, so the
 * scale says which way is flat. It stays on its side in right-to-left languages too, as the scale does.
 */
@Composable
private fun BoxScope.DialSign(sign: String, cents: Float, dial: Dp) {
    val density = LocalDensity.current
    val degrees = WatchDial.angleFor(cents) + if (cents < 0f) -WatchDial.SIGN_SPREAD_DEGREES else WatchDial.SIGN_SPREAD_DEGREES
    val radians = Math.toRadians(degrees.toDouble())
    val reach = dial / 2 - dial * (WatchDial.INSET + WatchDial.SIGN_INSET)
    Text(
        sign,
        modifier = Modifier
            .align(Alignment.Center)
            .absoluteOffset(x = reach * cos(radians).toFloat(), y = reach * sin(radians).toFloat())
            .clearAndSetSemantics {},
        color = WatchColors.muted.copy(alpha = WatchDial.SIGN_ALPHA),
        style = TextStyle(fontFamily = MusicSigns, fontSize = with(density) { (dial * WatchDial.SIGN_SIZE).toSp() }),
    )
}

/**
 * A note name with its ♯ set as on the phone (`musicText` in `:core:designsystem`): from the same
 * cut of Noto Sans Symbols, smaller than the letter and raised, so "C♯" reads as one name.
 */
private fun noteNameText(name: String): AnnotatedString = buildAnnotatedString {
    for (c in name) {
        if (c == '♯') {
            withStyle(RaisedSharp) { append(c) }
        } else {
            append(c)
        }
    }
}

/** The phone's cut of Noto Sans Symbols: ♭ ♮ ♯ and the notes. */
private val MusicSigns = FontFamily(Font(R.font.music_signs))

private val RaisedSharp = SpanStyle(
    fontFamily = MusicSigns,
    fontSize = 0.7f.em,
    baselineShift = BaselineShift(0.5f),
    letterSpacing = 0.sp,
)

/** The ring: track, cents ticks at the top, the in-tune glow, and the needle at [cents] (drawn in [needle]). */
private fun DrawScope.drawDial(cents: Float, markerAlpha: Float, glow: Float, needle: Path) {
    val d = size.minDimension
    val stroke = d * WatchDial.STROKE
    val radius = d / 2f - d * WatchDial.INSET
    val c = center

    drawCircle(WatchColors.track, radius, c, style = Stroke(stroke))

    // A tick every 5 cents, the tens longer and the middle longest.
    for (step in -WatchDial.TICKS..WatchDial.TICKS) {
        val angle = WatchDial.angleFor(step * WatchDial.CENTS_PER_TICK.toFloat())
        val centre = step == 0
        val ten = step % 2 == 0
        val length = d * when {
            centre -> WatchDial.CENTRE_TICK
            ten -> WatchDial.TICK
            else -> WatchDial.MINOR_TICK
        }
        val outer = radius - stroke
        drawLine(
            color = when {
                centre -> WatchColors.ink
                ten -> WatchColors.muted.copy(alpha = WatchDial.TICK_ALPHA)
                else -> WatchColors.muted.copy(alpha = WatchDial.MINOR_TICK_ALPHA)
            },
            start = pointAt(c, outer, angle),
            end = pointAt(c, outer - length, angle),
            strokeWidth = stroke * if (centre) 0.9f else 0.6f,
            cap = StrokeCap.Round,
        )
    }

    if (glow > 0f) {
        // A soft band of light around the ring, brightest on the ring itself.
        val band = d * WatchDial.GLOW_BAND
        val outer = radius + band
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.Transparent,
                (radius - band) / outer to Color.Transparent,
                radius / outer to WatchColors.inTune.copy(alpha = WatchDial.GLOW_ALPHA * glow),
                1f to Color.Transparent,
                center = c,
                radius = outer,
            ),
            radius = outer,
            center = c,
        )
        drawCircle(WatchColors.inTune.copy(alpha = glow), radius, c, style = Stroke(stroke))
    }

    if (markerAlpha > 0f) {
        drawNeedle(cents, markerAlpha, lerp(WatchColors.accent, WatchColors.inTune, glow), radius, stroke, needle)
    }
}

/**
 * The needle (the owner's pick C of docs/design/watch-look-preview.html): a slim taper pointing in
 * from the ring at [cents], its head a dot on the ring with a soft light round it.
 */
private fun DrawScope.drawNeedle(cents: Float, alpha: Float, color: Color, radius: Float, stroke: Float, needle: Path) {
    val degrees = WatchDial.angleFor(cents)
    val radians = Math.toRadians(degrees.toDouble())
    // Across the needle: a right angle to the line from the middle.
    val acrossX = -sin(radians).toFloat()
    val acrossY = cos(radians).toFloat()
    val head = pointAt(center, radius, degrees)
    val tip = pointAt(center, radius - size.minDimension * WatchDial.NEEDLE_LENGTH, degrees)
    val half = stroke * WatchDial.NEEDLE_HALF_WIDTH
    val tipHalf = half * WatchDial.NEEDLE_TIP
    needle.reset()
    needle.moveTo(head.x + acrossX * half, head.y + acrossY * half)
    needle.lineTo(tip.x + acrossX * tipHalf, tip.y + acrossY * tipHalf)
    needle.lineTo(tip.x - acrossX * tipHalf, tip.y - acrossY * tipHalf)
    needle.lineTo(head.x - acrossX * half, head.y - acrossY * half)
    needle.close()
    drawPath(needle, color, alpha = alpha)
    val light = stroke * WatchDial.HALO
    drawCircle(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = WatchDial.HALO_ALPHA * alpha),
            1f to color.copy(alpha = 0f),
            center = head,
            radius = light,
        ),
        radius = light,
        center = head,
    )
    drawCircle(color.copy(alpha = alpha), stroke * WatchDial.HEAD, head)
}

private fun pointAt(center: Offset, radius: Float, degrees: Float): Offset {
    val radians = Math.toRadians(degrees.toDouble())
    return Offset(center.x + radius * cos(radians).toFloat(), center.y + radius * sin(radians).toFloat())
}

/** The ring's proportions (as fractions of the screen's smaller side) and timings. */
object WatchDial {
    const val TAG = "watchDial"

    const val RIBBON_TAG = "watchRibbon"

    /** The scale runs ±50 cents over ±60° either side of 12 o'clock, a tick every 5 cents. */
    const val MAX_CENTS = 50f
    const val MAX_SWEEP_DEGREES = 60f
    const val TICKS = 10
    const val CENTS_PER_TICK = 5

    const val INSET = 0.06f
    const val STROKE = 0.014f
    const val TICK = 0.03f
    const val MINOR_TICK = 0.017f
    const val CENTRE_TICK = 0.045f
    const val TICK_ALPHA = 0.45f
    const val MINOR_TICK_ALPHA = 0.28f
    const val GLOW_BAND = 0.05f
    const val GLOW_ALPHA = 0.35f

    /**
     * The needle, in the ring's line widths ([STROKE]) unless said: it reaches 15 % of the screen in
     * from the ring (about 30 dp on the Watch 5), 1.7 lines wide at the head narrowing to a quarter of
     * that, and its head is a dot 4.8 lines across in a soft light 14 lines across.
     */
    const val NEEDLE_LENGTH = 0.15f
    const val NEEDLE_HALF_WIDTH = 0.85f
    const val NEEDLE_TIP = 0.25f
    const val HEAD = 2.4f
    const val HALO = 7f
    const val HALO_ALPHA = 0.3f

    /** ♭ and ♯: 4° beyond the scale's ends, 8.5 % of the screen inside the ring. */
    const val SIGN_SIZE = 0.062f
    const val SIGN_INSET = 0.085f
    const val SIGN_SPREAD_DEGREES = 4f
    const val SIGN_ALPHA = 0.75f

    const val NOTE_SIZE = 0.30f
    const val OCTAVE_SIZE = 0.11f
    const val CENTS_SIZE = 0.085f

    /** The ribbon: half the screen wide, 6.5 % tall, its middle 27.5 % below the screen's, soft. */
    const val RIBBON_WIDTH = 0.5f
    const val RIBBON_HEIGHT = 0.065f
    const val RIBBON_Y = 0.275f
    const val RIBBON_ALPHA = 0.45f
    const val RIBBON_HALO_ALPHA = 0.3f

    /** The in-tune glow eases in and out over this long: calm, never a flash. */
    const val GLOW_MILLIS = 700
    const val FADE_MILLIS = 400

    /** Where [cents] sits on the ring, in degrees clockwise from 3 o'clock (−90 is 12 o'clock). */
    fun angleFor(cents: Float): Float = -90f + cents.coerceIn(-MAX_CENTS, MAX_CENTS) / MAX_CENTS * MAX_SWEEP_DEGREES
}

/** A short text centred on the round screen, with an optional button under it. */
@Composable
private fun Message(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Box(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            if (action != null) {
                Button(onClick = onAction) { Text(action) }
            }
        }
    }
}

/** How the watch writes a note: its name in a notation (A♯, La♯, H), the octave apart, cents with a sign. */
object WatchNoteName {
    fun letter(note: Note, notation: Notation = Notation.DEFAULT): String = notation.nameOf(note.pitchClass)

    /**
     * How large [name] is drawn, as a share of the full note size: a one-letter name in full, a
     * solfège syllable smaller, so "Sol♯4" fits the ring as well as "G♯4" does.
     */
    fun sizeFor(name: String): Float = when (name.count { it != '♯' && it != '♭' }) {
        1 -> 1f
        2 -> 0.8f
        else -> 0.65f
    }

    fun octave(note: Note): String = note.octave.toString()

    /**
     * "+12", "−3" (a real minus sign), or "0", in the digits of [locale] as the texts around it write
     * them (Arabic-Indic in Arabic: "+١٢"). The octave stays 0–9, as part of the note's name.
     */
    fun signedCents(cents: Int, locale: Locale = Locale.ROOT): String {
        val digits = String.format(locale, "%d", abs(cents))
        return when {
            cents > 0 -> "+$digits"
            cents < 0 -> "−$digits"
            else -> digits
        }
    }
}
