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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import kotlin.math.cos
import kotlin.math.sin

/**
 * The watch app: the microphone permission first, then the tuner. [tuner] is only asked for (and
 * so the microphone only opened) once the permission is granted. While the tuner is shown, the
 * watch's screen state goes to the view model, which lets go of the mic when the screen goes off,
 * the screen is held on while the tuner hears playing, and the wrist feels a tap as a note comes
 * in tune.
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
                // One soft tap on the wrist as the note comes in tune, as the arc turns green.
                WatchInTuneTapEffect(state)
                // The arc runs along the edge, where the time would be, so the tuner leaves it out.
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
 * The tuner (2.0, Phase 75; the watch of docs/design/fresh-start-preview.html, *On the wrist*): the top half of the
 * phone's tuner and nothing else. The note large in the middle (the octave small beside it), a dot along an arc
 * that opens at the bottom (±50 cents over ±135° either side of 12 o'clock, a mark at the top for the note, ♭ and ♯
 * inside its ends), and under the note the phone's word, "Tighten", "Loosen" or "In tune" ([WatchWord]), with the
 * cents small under it ([WatchCents]). The dot and the word take the phone's tone ([WatchTone]): amber far off,
 * easing toward green as the note nears, green in tune; in tune the arc turns green too, easing in and out (and the
 * wrist feels a tap, [WatchInTuneTapEffect]). While nothing is heard "Play a note" stands in the word's place, in
 * the muted colour, and the note fades out. A failure shows a message instead, which TalkBack says as it comes (a
 * polite live region). Stateless but for the pacing of what TalkBack says and the word held near the note.
 *
 * For TalkBack the dial is one polite live region under the rest that says the reading in words, "A sharp 2,
 * 12 cents flat, Tighten", "E 2, in tune" or "Play a note" ([spokenWatchReading]), paced as on the phone by [speech]
 * ([WatchPacedSpeech]); the texts on screen are hidden from it.
 */
@Composable
fun WatchTuner(state: WatchTunerState, onRetry: () -> Unit, speech: WatchPacedSpeech = rememberWatchPacedSpeech()) {
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
            live = true,
        )
        return
    }
    val detected = state as? WatchTunerState.Detected
    val held = remember { HeldWatchWord() }
    // Once per reading: the word near the note keeps the direction it had.
    val word = remember(detected) { held.next(detected) }
    val playNote = stringResource(R.string.wear_play_note)
    val spoken = watchPacedSpeech(
        text = if (detected != null) spokenWatchReading(detected, word) else playNote,
        key = detected?.let(::watchReadingKey),
        speech = speech,
    )
    val glow by animateFloatAsState(
        targetValue = if (detected?.inTune == true) 1f else 0f,
        animationSpec = tween(WatchDial.GLOW_MILLIS, easing = FastOutSlowInEasing),
        label = "inTuneGlow",
    )
    val presence by animateFloatAsState(
        targetValue = if (detected != null) 1f else 0f,
        animationSpec = tween(WatchDial.FADE_MILLIS),
        label = "presence",
    )
    // Without a note the dot stays where it was while it fades, and the tone with it.
    val lastCents = remember { LastCents() }
    val dotCents by animateFloatAsState(
        targetValue = lastCents.of(detected),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "dotCents",
    )
    // Read while drawing, so the dot gliding redraws without recomposing.
    val tone = ColorProducer { WatchTone.colour(WatchTone.share(dotCents, glow)) }

    BoxWithConstraints(Modifier.fillMaxSize().testTag(WatchDial.TAG), contentAlignment = Alignment.Center) {
        val dial = min(maxWidth, maxHeight)
        val density = LocalDensity.current
        // Sized to the screen, not the font setting: the note is already as large as the dial allows.
        fun Dp.fixedSp() = with(density) { toSp() }
        // TalkBack hears the reading from this layer only. It lies under the dial, not around it, so
        // nothing changing inside can make TalkBack read it again early (as on the phone).
        Box(
            Modifier
                .fillMaxSize()
                .testTag(WatchDial.SPOKEN_TAG)
                .semantics {
                    contentDescription = spoken
                    // Not for "Play a note" said again after a pluck dies away (WatchPacedSpeech.announced).
                    if (speech.announced) liveRegion = LiveRegionMode.Polite
                },
        )
        Canvas(Modifier.fillMaxSize()) {
            drawDial(cents = dotCents, presence = presence, glow = glow, dot = tone())
        }
        DialSign("♭", flat = true, dial)
        DialSign("♯", flat = false, dial)
        AnimatedContent(
            targetState = detected,
            contentKey = { it != null },
            transitionSpec = { fadeIn(tween(WatchDial.FADE_MILLIS)) togetherWith fadeOut(tween(WatchDial.FADE_MILLIS)) },
            modifier = Modifier.absoluteOffset(y = dial * WatchDial.NOTE_Y),
            label = "note",
        ) { shown ->
            if (shown != null) NoteName(shown.note, dial) else Box(Modifier)
        }
        val wordText = when {
            detected == null -> playNote
            word == WatchWord.TIGHTEN -> stringResource(R.string.wear_word_tighten)
            word == WatchWord.LOOSEN -> stringResource(R.string.wear_word_loosen)
            word == WatchWord.IN_TUNE -> inTuneWord()
            else -> ""
        }
        val quiet = detected == null
        BasicText(
            text = wordText,
            style = TextStyle(
                fontSize = (dial * if (quiet) WatchDial.QUIET_SIZE else WatchDial.WORD_SIZE).fixedSp(),
                fontWeight = if (quiet) FontWeight.SemiBold else FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            color = if (quiet) ColorProducer { WatchColors.muted } else tone,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(
                minFontSize = (dial * WatchDial.WORD_MIN_SIZE).fixedSp(),
                maxFontSize = (dial * if (quiet) WatchDial.QUIET_SIZE else WatchDial.WORD_SIZE).fixedSp(),
                stepSize = 1.sp,
            ),
            modifier = Modifier
                .absoluteOffset(y = dial * WatchDial.WORD_Y)
                .width(dial * WatchDial.WORD_WIDTH)
                .testTag(WatchDial.WORD_TAG)
                .semantics { hideFromAccessibility() },
        )
        if (detected != null) {
            WatchCents(
                detected,
                style = TextStyle(color = WatchColors.muted, fontSize = (dial * WatchDial.CENTS_SIZE).fixedSp()),
                modifier = Modifier
                    .absoluteOffset(y = dial * WatchDial.CENTS_Y)
                    .semantics { hideFromAccessibility() },
            )
        }
    }
}

/** The cents of the last note heard, so the dot stays where it was while it fades. Not state: nothing redraws for it. */
private class LastCents {
    private var cents = 0f

    fun of(reading: WatchTunerState.Detected?): Float {
        if (reading != null) cents = reading.cents.toFloat()
        return cents
    }
}

/** "In tune" as the word: TalkBack's words for it (`wear_in_tune`, "in tune", "zuiver"), with a capital since it stands alone, as on the phone. */
@Composable
private fun inTuneWord(): String {
    val word = stringResource(R.string.wear_in_tune)
    val locale = LocalConfiguration.current.locales[0]
    return remember(word, locale) { capitalised(word, locale) }
}

/** [text] with its first letter as a capital in [locale] ("akortlu" → "Akortlu"); scripts without capitals stay as they are. */
internal fun capitalised(text: String, locale: Locale): String = text.replaceFirstChar { it.titlecase(locale) }

/** The note (letter large, octave small beside it), sized to the [dial]. Hidden from TalkBack, which hears the spoken layer. */
@Composable
private fun NoteName(note: Note, dial: Dp) {
    val density = LocalDensity.current
    fun Dp.fixedSp() = with(density) { toSp() }
    val locale = LocalConfiguration.current.locales[0]
    // Written as players of the watch's language usually write notes (Do Re Mi in French, H in
    // German); the watch has no notation setting.
    val name = WatchNoteName.letter(note, Notation.forLanguage(locale.language))
    // A note name reads "A4" in every language, so the octave stays right of the letter in
    // right-to-left languages too.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(Modifier.semantics(mergeDescendants = true) { hideFromAccessibility() }) {
            Text(
                noteNameText(name),
                modifier = Modifier.alignByBaseline(),
                color = WatchColors.ink,
                style = TextStyle(
                    fontSize = (dial * WatchDial.NOTE_SIZE * WatchNoteName.sizeFor(name)).fixedSp(),
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = WatchDial.NOTE_TRACKING.em,
                ),
            )
            Text(
                WatchNoteName.octave(note),
                modifier = Modifier.alignByBaseline(),
                color = WatchColors.muted,
                style = TextStyle(fontSize = (dial * WatchDial.OCTAVE_SIZE).fixedSp(), fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

/**
 * ♭ or ♯ inside one end of the arc ([flat]: the left one), so the arc says which way is flat. It stays on its side
 * in right-to-left languages too, as the arc does.
 */
@Composable
private fun BoxScope.DialSign(sign: String, flat: Boolean, dial: Dp) {
    val density = LocalDensity.current
    val degrees = -90f + if (flat) -WatchDial.SIGN_DEGREES else WatchDial.SIGN_DEGREES
    val radians = Math.toRadians(degrees.toDouble())
    val reach = dial * (0.5f - WatchDial.INSET - WatchDial.SIGN_INSET)
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

/**
 * The arc (green as far as [glow] says, the note in tune), the mark at its top and the dot at [cents] in the colour
 * [dot], as far as a note is there ([presence]), with a soft light round it.
 */
private fun DrawScope.drawDial(cents: Float, presence: Float, glow: Float, dot: Color) {
    val d = size.minDimension
    val stroke = d * WatchDial.STROKE
    val radius = d * (0.5f - WatchDial.INSET)
    val c = center
    val arcColour = lerp(WatchColors.track, lerp(WatchColors.track, WatchColors.inTune, WatchDial.ARC_GREEN), glow)
    drawArc(
        color = arcColour,
        startAngle = WatchDial.angleFor(-WatchDial.MAX_CENTS),
        sweepAngle = 2 * WatchDial.MAX_SWEEP_DEGREES,
        useCenter = false,
        topLeft = Offset(c.x - radius, c.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(stroke, cap = StrokeCap.Round),
    )
    // The note's place: a small mark on the arc at 12 o'clock.
    drawCircle(WatchColors.muted, d * WatchDial.MARK, pointAt(c, radius, WatchDial.angleFor(0f)))

    if (presence > 0f) {
        val at = pointAt(c, radius, WatchDial.angleFor(cents))
        val light = d * WatchDial.HALO
        drawCircle(
            brush = Brush.radialGradient(
                0f to dot.copy(alpha = WatchDial.HALO_ALPHA * presence),
                1f to dot.copy(alpha = 0f),
                center = at,
                radius = light,
            ),
            radius = light,
            center = at,
        )
        drawCircle(dot.copy(alpha = presence), d * WatchDial.DOT, at)
    }
}

private fun pointAt(center: Offset, radius: Float, degrees: Float): Offset {
    val radians = Math.toRadians(degrees.toDouble())
    return Offset(center.x + radius * cos(radians).toFloat(), center.y + radius * sin(radians).toFloat())
}

/** The dial's proportions (as fractions of the screen's smaller side; places from its middle) and timings. */
object WatchDial {
    const val TAG = "watchDial"

    /** The layer that speaks the reading to TalkBack (a polite live region). */
    const val SPOKEN_TAG = "watchSpoken"

    /** The word under the note, or "Play a note" in its place. */
    const val WORD_TAG = "watchWord"

    /** The arc runs ±50 cents over ±135° either side of 12 o'clock, open at the bottom (the mock-up's). */
    const val MAX_CENTS = 50f
    const val MAX_SWEEP_DEGREES = 135f

    /** The arc: 9 % in from the edge, its line 4 % of the screen, round at the ends. */
    const val INSET = 0.09f
    const val STROKE = 0.04f

    /** In tune the arc goes this far from its grey toward green: the dot is the bright one. */
    const val ARC_GREEN = 0.45f

    /** The dot (8 % across, a touch wider than the arc), its soft light (17 % across) and the note's mark. */
    const val DOT = 0.04f
    const val HALO = 0.085f
    const val HALO_ALPHA = 0.35f
    const val MARK = 0.012f

    /** ♭ and ♯: 118° either side of 12 o'clock, 8.5 % of the screen inside the arc. */
    const val SIGN_SIZE = 0.062f
    const val SIGN_INSET = 0.085f
    const val SIGN_DEGREES = 118f
    const val SIGN_ALPHA = 0.75f

    /** The note: 36 % of the screen (the mock-up's 92 px of 240), extra bold and set close; the octave beside it. */
    const val NOTE_SIZE = 0.36f
    const val NOTE_TRACKING = -0.03f
    const val OCTAVE_SIZE = 0.11f
    const val NOTE_Y = -0.05f

    /** The word (bold, 7.5 % of the screen; "Play a note" a little smaller), set smaller where it doesn't fit its width between the arc's ends. */
    const val WORD_SIZE = 0.075f
    const val QUIET_SIZE = 0.068f
    const val WORD_MIN_SIZE = 0.04f
    const val WORD_WIDTH = 0.5f
    const val WORD_Y = 0.215f

    /** The cents under the word. */
    const val CENTS_SIZE = 0.055f
    const val CENTS_Y = 0.30f

    /** The arc's green eases in and out over this long: calm, never a flash. */
    const val GLOW_MILLIS = 700
    const val FADE_MILLIS = 400

    /** Where [cents] sits on the arc, in degrees clockwise from 3 o'clock (−90 is 12 o'clock). */
    fun angleFor(cents: Float): Float = -90f + cents.coerceIn(-MAX_CENTS, MAX_CENTS) / MAX_CENTS * MAX_SWEEP_DEGREES
}

/**
 * A short text centred on the round screen, with an optional button under it. A [live] text is a
 * polite live region, so TalkBack says it as it comes (the microphone lost while tuning).
 */
@Composable
private fun Message(text: String, action: String? = null, onAction: () -> Unit = {}, live: Boolean = false) {
    Box(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text,
                modifier = if (live) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (action != null) {
                Button(onClick = onAction) { Text(action) }
            }
        }
    }
}

/** How the watch writes a note: its name in a notation (A♯, La♯, H), the octave apart. */
object WatchNoteName {
    fun letter(note: Note, notation: Notation = Notation.DEFAULT): String = notation.nameOf(note.pitchClass)

    /**
     * How large [name] is drawn, as a share of the full note size: a one-letter name in full, a
     * solfège syllable smaller, so "Sol♯4" fits inside the arc as well as "G♯4" does.
     */
    fun sizeFor(name: String): Float = when (name.count { it != '♯' && it != '♭' }) {
        1 -> 1f
        2 -> 0.8f
        else -> 0.65f
    }

    fun octave(note: Note): String = note.octave.toString()
}
