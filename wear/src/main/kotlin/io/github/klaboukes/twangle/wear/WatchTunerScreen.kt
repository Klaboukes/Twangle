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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
 * The tuner: a ring along the edge of the screen with the cents scale at the top, the note large
 * in the middle and the cents under it. When the note is in tune the ring and the note glow in the
 * in-tune colour, easing in and out. A failure shows a message instead. Stateless.
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

    BoxWithConstraints(Modifier.fillMaxSize().testTag(WatchDial.TAG), contentAlignment = Alignment.Center) {
        val dial = min(maxWidth, maxHeight)
        Canvas(Modifier.fillMaxSize()) {
            drawDial(cents = markerCents, markerAlpha = markerAlpha, glow = glow)
        }
        AnimatedContent(
            targetState = state,
            contentKey = { it is WatchTunerState.Detected },
            transitionSpec = { fadeIn(tween(WatchDial.FADE_MILLIS)) togetherWith fadeOut(tween(WatchDial.FADE_MILLIS)) },
            label = "reading",
        ) { shown ->
            if (shown is WatchTunerState.Detected) {
                Reading(shown, dial, glow = { glow })
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
        Text(
            pluralStringResource(R.plurals.wear_cents, abs(reading.cents), WatchNoteName.signedCents(reading.cents, locale)),
            color = WatchColors.muted,
            style = TextStyle(fontSize = (dial * WatchDial.CENTS_SIZE).fixedSp()),
        )
    }
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

private val RaisedSharp = SpanStyle(
    fontFamily = FontFamily(Font(R.font.music_signs)),
    fontSize = 0.7f.em,
    baselineShift = BaselineShift(0.5f),
    letterSpacing = 0.sp,
)

/** The ring: track, cents ticks at the top, the in-tune glow, and the marker at [cents]. */
private fun DrawScope.drawDial(cents: Float, markerAlpha: Float, glow: Float) {
    val d = size.minDimension
    val stroke = d * WatchDial.STROKE
    val radius = d / 2f - d * WatchDial.INSET
    val c = center

    drawCircle(WatchColors.track, radius, c, style = Stroke(stroke))

    for (step in -WatchDial.TICKS..WatchDial.TICKS) {
        val angle = WatchDial.angleFor(step * WatchDial.CENTS_PER_TICK.toFloat())
        val centre = step == 0
        val length = d * if (centre) WatchDial.CENTRE_TICK else WatchDial.TICK
        val outer = radius - stroke
        drawLine(
            color = if (centre) WatchColors.ink else WatchColors.muted.copy(alpha = WatchDial.TICK_ALPHA),
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
        val color = lerp(WatchColors.accent, WatchColors.inTune, glow)
        drawCircle(color.copy(alpha = markerAlpha), stroke * WatchDial.MARKER, pointAt(c, radius, WatchDial.angleFor(cents)))
    }
}

private fun pointAt(center: Offset, radius: Float, degrees: Float): Offset {
    val radians = Math.toRadians(degrees.toDouble())
    return Offset(center.x + radius * cos(radians).toFloat(), center.y + radius * sin(radians).toFloat())
}

/** The ring's proportions (as fractions of the screen's smaller side) and timings. */
object WatchDial {
    const val TAG = "watchDial"

    /** The scale runs ±50 cents over ±60° either side of 12 o'clock. */
    const val MAX_CENTS = 50f
    const val MAX_SWEEP_DEGREES = 60f
    const val TICKS = 5
    const val CENTS_PER_TICK = 10

    const val INSET = 0.06f
    const val STROKE = 0.014f
    const val TICK = 0.03f
    const val CENTRE_TICK = 0.055f
    const val TICK_ALPHA = 0.45f
    const val MARKER = 2.6f
    const val GLOW_BAND = 0.05f
    const val GLOW_ALPHA = 0.35f

    const val NOTE_SIZE = 0.30f
    const val OCTAVE_SIZE = 0.11f
    const val CENTS_SIZE = 0.085f

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
