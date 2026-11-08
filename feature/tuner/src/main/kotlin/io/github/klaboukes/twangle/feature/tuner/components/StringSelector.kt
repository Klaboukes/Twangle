package io.github.klaboukes.twangle.feature.tuner.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import io.github.klaboukes.twangle.core.dsp.strum.StrumVerdict
import io.github.klaboukes.twangle.feature.tuner.StrumString
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.klaboukes.twangle.core.designsystem.HIGH_CONTRAST_LINES
import io.github.klaboukes.twangle.core.designsystem.PressIndication
import io.github.klaboukes.twangle.core.designsystem.styleTokens
import io.github.klaboukes.twangle.core.designsystem.controlOutline
import io.github.klaboukes.twangle.core.designsystem.musicText
import io.github.klaboukes.twangle.core.designsystem.pressFlash
import io.github.klaboukes.twangle.core.designsystem.pressSink
import io.github.klaboukes.twangle.core.designsystem.rememberAnimationsOff
import io.github.klaboukes.twangle.core.designsystem.rememberNoteNameSpeech
import io.github.klaboukes.twangle.core.designsystem.stringChipFill
import io.github.klaboukes.twangle.core.designsystem.StringChipShape
import io.github.klaboukes.twangle.core.designsystem.StringChipSize
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tuning
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.feature.tuner.R
import io.github.klaboukes.twangle.feature.tuner.TunedStrings
import io.github.klaboukes.twangle.feature.tuner.TunerTestTags

/**
 * The pegs (2.0, Phase 71; docs/design/v2-tuner-screens-preview.html): the open strings of [tuning] as a plain row
 * of round buttons in the tuning's order, lowest on the left (in right-to-left languages too), each alike, its note
 * name in ink with the octave as a small subscript, e.g. E₂. The [targetString] (if any: the string heard, or the
 * locked one waiting to be) is ringed: a 2 dp ring in the reading's [tone] round its face with a soft glow outside
 * it, so it is amber far off, eases toward green and is green in tune, as the lens's border and the word (the same
 * [LiveTone], read while drawing: a reading recomposes nothing); without a [tone] (the tools' rows) the accent,
 * green while [inTune]. Until 2.0 the target was filled in the accent.
 *
 * With [onStringClick] the strings are buttons: tapping one locks the tuner to it, tapping the
 * [lockedString] again unlocks it. The locked peg carries a small padlock at its bottom end corner, outside its
 * face so nothing moves (until 2.0 an outer ring, which the heard ring now is). With [onStringLongClick]
 * a long-press plays the string's note (TalkBack: the named long-press action "Play A 2"); the
 * [soundingString], whose tone sounds now, sends rings out round its button like sound waves (one
 * still ring with Android's animations off), drawn outside it so nothing moves. While a tone sounds
 * a tap on any string stops it, and TalkBack says so.
 *
 * A course with a second string an octave up ([Tuning.octaveCourses], a 12-string's lower four)
 * carries a small "8" in its button's top left corner, across from a badge, and TalkBack says "with octave string" after its
 * name; the compact layout's small chips leave it out.
 *
 * A string that starts partway up the neck ([Tuning.shortStrings], a 5-string banjo's fifth, first
 * in the row) is set a little apart from the next: the same button, a small extra gap after it
 * ([shortStringGapFor]; none where the row is squeezed).
 *
 * With [marks] (the 12th-fret check) each string with a result carries its [StringMark] as a small
 * badge, and TalkBack says it after the string's name; [checking] makes a tap's TalkBack action
 * "Check A 2" instead of locking.
 *
 * The strings in [tuned] (in tune since the tuner opened, Phase 65; given only with the string tick-off on, 2.0's
 * beginner's help, off by default) carry the same badge as a string
 * whose saddle is fine: a check mark on a disc in the "in tune" colour, growing in once, outside the
 * button's box so nothing moves; TalkBack hears "Tuned" as the button's state ("Locked, tuned" on the
 * locked string). [tuned] must be of this [tuning]. The [small] chips leave it out.
 *
 * With [strumMarks] (a strum of the open strings heard, 2.0 Phase 74) each string's verdict stands above its peg
 * ([StrumMark]: ↑ tighten, ↓ loosen, ✓ in tune, – not heard), [MARK_SIZE] high and [MARK_GAP] above the face, outside
 * the peg's box so nothing moves (the row above the pegs keeps room for it, `GuitarControls`), and TalkBack hears it
 * as the peg's state ("Tighten"; "Locked, Tighten" on the locked peg).
 *
 * [small] draws a narrow row of small pegs, for the compact landscape layout: it only shows which
 * string is the target, and the lock. It takes no more width than it needs and is hidden from TalkBack, since the
 * note's reading already names the string.
 */
@Composable
fun StringSelector(
    tuning: Tuning,
    targetString: Int?,
    modifier: Modifier = Modifier,
    notation: Notation = Notation.DEFAULT,
    inTune: Boolean = false,
    lockedString: Int? = null,
    onStringClick: ((Int) -> Unit)? = null,
    small: Boolean = false,
    onStringLongClick: ((Int) -> Unit)? = null,
    soundingString: Int? = null,
    marks: List<StringMark?>? = null,
    checking: Boolean = false,
    tuned: TunedStrings? = null,
    tone: LiveTone? = null,
    strumMarks: List<StrumString>? = null,
) {
    val checkLabel = stringResource(R.string.tuner_intonation_check_string)
    val markLabels = StringMark.entries.associateWith { spokenMark(it) }
    val lockLabel = stringResource(R.string.tuner_string_lock)
    val unlockLabel = stringResource(R.string.tuner_string_unlock)
    val stopLabel = stringResource(R.string.tuner_string_stop_tone)
    val lockedState = stringResource(R.string.tuner_string_locked)
    val soundingState = stringResource(R.string.tuner_string_sounding)
    val lockedSoundingState = stringResource(R.string.tuner_string_locked_sounding)
    val tunedState = stringResource(R.string.tuner_string_tuned)
    val lockedTunedState = stringResource(R.string.tuner_string_locked_tuned)
    val lockedMarkState = stringResource(R.string.tuner_string_locked_mark)
    val strumLabels = StrumVerdict.entries.associateWith { strumMarkLabel(it) }
    val marksAnimationsOff = strumMarks != null && rememberAnimationsOff()
    val playLabel = stringResource(R.string.tuner_string_play)
    val animationsOff = soundingString != null && rememberAnimationsOff()
    val speech = rememberNoteNameSpeech()
    val spokenString = stringResource(R.string.tuner_spoken_string)
    val spokenOctaveCourse = stringResource(R.string.tuner_spoken_octave_course)
    BoxWithConstraints(if (small) modifier else modifier.fillMaxWidth()) {
        // 4 to 8 strings: full-size buttons while they fit, else equal shares of the row, shrunk.
        val width = if (constraints.hasBoundedWidth) maxWidth else Dp.Infinity
        val shortGap = shortStringGapFor(width, tuning, small)
        val fit = stringRowFitFor(width, tuning.strings.size, small, shortGap)
        // Low to high, left to right, in every language: like a keyboard and the meter, not mirrored
        // in right-to-left languages (Material's rule for graphs and gauges).
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = if (small && !fit.squeezed) Modifier else Modifier.fillMaxWidth(),
                horizontalArrangement = when {
                    fit.squeezed -> Arrangement.Start
                    small -> Arrangement.spacedBy(SMALL_CHIP_SPACING)
                    else -> Arrangement.SpaceEvenly
                },
            ) {
                tuning.strings.forEachIndexed { index, note ->
                    val locked = index == lockedString
                    val sounding = index == soundingString
                    val name = notation.nameIn(tuning, note.pitchClass)
                    val spokenName = spokenString.format(speech.name(name), note.octave)
                    val mark = marks?.getOrNull(index)
                    val octaveString = tuning.hasOctaveString(index)
                    val ticked = !small && tuned != null && index in tuned
                    val strum = strumMarks?.getOrNull(index)?.takeIf { !small }
                    val strumLabel = strum?.let { strumLabels.getValue(it.verdict) }
                    PegWithMark(
                        mark = strum?.let { { StrumMark(it, index, animationsOff = marksAnimationsOff) } },
                        modifier = when {
                            fit.squeezed -> Modifier.weight(1f)
                            // Outside the button, so the gap is no part of it to touch.
                            endsShortStrings(tuning, index) -> Modifier.padding(end = shortGap)
                            else -> Modifier
                        },
                        faceInset = fit.ringGapDp.dp,
                    ) {
                    StringChip(
                        name = name,
                        spokenName = if (octaveString) spokenOctaveCourse.format(spokenName) else spokenName,
                        octaveString = octaveString,
                        octave = note.octave,
                        highlighted = index == targetString,
                        inTune = inTune,
                        tone = tone,
                        locked = locked,
                        onClick = onStringClick?.let { click -> { click(index) } },
                        clickLabel = when {
                            soundingString != null -> stopLabel
                            checking -> checkLabel.format(spokenName)
                            locked -> unlockLabel
                            else -> lockLabel
                        },
                        onLongClick = onStringLongClick?.let { play -> { play(index) } },
                        longClickLabel = playLabel.format(spokenName),
                        state = when {
                            locked && sounding -> lockedSoundingState
                            locked && strumLabel != null -> lockedMarkState.format(strumLabel)
                            locked && ticked -> lockedTunedState
                            locked -> lockedState
                            sounding -> soundingState
                            mark != null -> markLabels.getValue(mark)
                            strumLabel != null -> strumLabel
                            ticked -> tunedState
                            else -> null
                        },
                        mark = mark,
                        tuned = ticked,
                        sounding = sounding,
                        animationsOff = animationsOff,
                        fit = fit,
                        small = small,
                        modifier = if (fit.squeezed) Modifier.fillMaxWidth() else Modifier,
                    )
                    }
                }
            }
        }
    }
}

/**
 * A [peg] with its strum [mark] (if any) centred above it, [MARK_GAP] above its face (its box less [faceInset] at the
 * top), placed outside the peg's box: the row and everything round it keep their places with or without it.
 */
@Composable
private fun PegWithMark(mark: (@Composable () -> Unit)?, modifier: Modifier, faceInset: Dp, peg: @Composable () -> Unit) {
    val markSlot = @Composable { if (mark != null) mark() }
    Layout(contents = listOf(peg, markSlot), modifier = modifier) { (pegMeasurables, markMeasurables), constraints ->
        val pegPlaceable = pegMeasurables.single().measure(constraints)
        val markPlaceable = markMeasurables.firstOrNull()?.measure(Constraints())
        layout(pegPlaceable.width, pegPlaceable.height) {
            pegPlaceable.placeRelative(0, 0)
            markPlaceable?.placeRelative(
                (pegPlaceable.width - markPlaceable.width) / 2,
                faceInset.roundToPx() - MARK_GAP.roundToPx() - markPlaceable.height,
            )
        }
    }
}

/** How far in from each end of a full-size row [width] wide of [tuning]'s string buttons the faces are, in dp ([stringFaceInset]). */
internal fun stringFaceInsetFor(width: Dp, tuning: Tuning): Float {
    val gap = shortStringGapFor(width, tuning, small = false)
    val count = tuning.strings.size
    return stringFaceInset(width.value, count, StringChipSize.width.value, stringRowFitFor(width, count, small = false, gap), gap.value)
}

/**
 * The extra gap after [tuning]'s short strings ([Tuning.shortStrings], a banjo's fifth) in a row
 * [width] wide: [SHORT_STRING_GAP] (the [small] chips' [SMALL_SHORT_STRING_GAP]) while the buttons
 * still fit at full size with it, else none, as squeezed buttons take equal shares.
 */
internal fun shortStringGapFor(width: Dp, tuning: Tuning, small: Boolean): Dp {
    if (!tuning.hasShortString) return 0.dp
    val gap = if (small) SMALL_SHORT_STRING_GAP else SHORT_STRING_GAP
    return if (stringRowFitFor(width, tuning.strings.size, small, gap).squeezed) 0.dp else gap
}

/** Whether string [index] is the last of a run of short strings with another string after it, so the gap follows it. */
private fun endsShortStrings(tuning: Tuning, index: Int): Boolean =
    index in tuning.shortStrings && index + 1 < tuning.stringCount && index + 1 !in tuning.shortStrings

/**
 * How [count] string buttons fit a row [width] wide (the [small] chips or the full-size
 * buttons), [extraGap] of it taken by the short strings' gap ([shortStringGapFor]).
 */
internal fun stringRowFitFor(width: Dp, count: Int, small: Boolean, extraGap: Dp = 0.dp): StringRowFit {
    val available = if (width == Dp.Infinity) Float.MAX_VALUE else width.value - extraGap.value
    return if (small) {
        stringRowFit(available, count, SMALL_CHIP.width.value, SMALL_RING_GAP.value, minSlotDp = 0f, spacingDp = SMALL_CHIP_SPACING.value)
    } else {
        stringRowFit(available, count, StringChipSize.width.value, RING_GAP.value, minSlotDp = MIN_TOUCH.value)
    }
}

/** The compact layout's chip: the button shrunk into the small square, lock ring included. */
private val SMALL_CHIP: DpSize get() = StringChipSize.fitInside(SMALL_CHIP_SIZE - SMALL_RING_GAP * 2)

@Composable
private fun StringChip(
    name: String,
    spokenName: String,
    octaveString: Boolean,
    octave: Int,
    highlighted: Boolean,
    inTune: Boolean,
    tone: LiveTone?,
    locked: Boolean,
    onClick: (() -> Unit)?,
    clickLabel: String,
    onLongClick: (() -> Unit)?,
    longClickLabel: String,
    state: String?,
    mark: StringMark?,
    tuned: Boolean,
    sounding: Boolean,
    animationsOff: Boolean,
    fit: StringRowFit,
    small: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // The high-contrast look (Phase 86): the edge and the heard ring thicker, no glow round the ring.
    val highContrast = MaterialTheme.styleTokens.highContrast
    // The ring's colour without a reading's tone (the tools' rows): the accent, green in tune.
    val accent = animateInTuneColor(inTune, colors.primary, "pegAccent")
    val ringColour = remember(tone, accent) { ColorProducer { tone?.colour ?: accent.value } }
    // The heard peg's ring comes and goes calmly (the mock-up's quarter second), never jumping between pegs.
    val ringShown = animateFloatAsState(if (highlighted) 1f else 0f, tween(RING_FADE_MS), label = "pegRing")
    // A round button, with room round it for the heard ring's glow.
    val shape = StringChipShape
    val ringGap = fit.ringGapDp.dp
    val natural = if (small) SMALL_CHIP else StringChipSize
    val button = DpSize(natural.width * fit.buttonScale, natural.height * fit.buttonScale)
    // The press effect: the whole button sinks, and the light lies over its fill, under its name.
    val press = remember { MutableInteractionSource() }
    // A string ticked off as tuned grows its check in once, calmly; one already ticked shows it at once.
    val tick = animateFloatAsState(if (tuned) 1f else 0f, tween(TICK_GROW_MS, easing = FastOutSlowInEasing), label = "tick")
    val badge = mark ?: StringMark.FINE.takeIf { tuned }
    // The lock on the peg grows in and out the same way.
    val lock = animateFloatAsState(if (locked) 1f else 0f, tween(LOCK_GROW_MS, easing = FastOutSlowInEasing), label = "lock")
    Box(
        modifier = modifier
            .then(
                when {
                    small -> Modifier
                    // A squeezed button takes its share of the row and is 48 dp tall to touch; a touch
                    // just beside it still reaches the nearest button (Compose widens small touch targets).
                    fit.squeezed -> Modifier.heightIn(min = MIN_TOUCH).wrapContentSize()
                    // Big buttons are at least 48 dp to touch each way, however narrow or short they look.
                    else -> Modifier.minimumInteractiveComponentSize()
                },
            )
            .size(button.width + ringGap * 2, button.height + ringGap * 2)
            // Outside the clip, so the rings reach past the button without taking room.
            .then(if (sounding) Modifier.soundingRings(shape, ringGap, colors.primary, animationsOff) else Modifier)
            // Over the button and outside its clip, so the row doesn't move.
            .stringMarkBadge(
                badge,
                ringGap,
                fill = if (badge == StringMark.FINE) inTuneColor() else colors.primary,
                on = if (badge == StringMark.FINE) onInTuneColor() else colors.onPrimary,
                background = colors.background,
                scale = if (mark != null) ({ 1f }) else ({ tick.value }),
            )
            // The lock, across the bottom end corner from the tick, outside the clip too.
            .lockBadge(
                inset = ringGap,
                radius = if (small) SMALL_LOCK_BADGE_RADIUS else LOCK_BADGE_RADIUS,
                fill = colors.onSurface,
                on = colors.surface,
                background = colors.background,
                scale = { lock.value },
            )
            // The heard peg's ring and its glow, over the face's edge and in the room round it.
            .heardRing(ringGap, ringColour, highContrast) { ringShown.value }
            .pressSink(press)
            .clip(shape)
            .then(
                when {
                    onClick == null -> Modifier
                    // A long-press gives Android's long-press haptic tick by itself.
                    onLongClick != null -> Modifier.combinedClickable(
                        interactionSource = press,
                        indication = null,
                        onClickLabel = clickLabel,
                        role = Role.Button,
                        onLongClickLabel = longClickLabel,
                        onLongClick = onLongClick,
                        onClick = {
                            press.pressFlash()
                            onClick()
                        },
                    )
                    else -> Modifier.clickable(press, indication = null, onClickLabel = clickLabel, role = Role.Button) {
                        press.pressFlash()
                        onClick()
                    }
                },
            )
            .padding(ringGap)
            .background(stringChipFill(colors), shape)
            // An edge 3:1 against the background, as the round buttons' (Phase 65); the heard ring lies over it.
            .border(if (highContrast) HIGH_CONTRAST_EDGE else 1.dp, controlOutline(colors), shape)
            .indication(press, PressIndication(shape))
            .semantics(mergeDescendants = true) {
                contentDescription = spokenName
                selected = highlighted
                if (state != null) stateDescription = state
                if (small) hideFromAccessibility()
            },
        contentAlignment = Alignment.Center,
    ) {
        // The button has a fixed size, so its label grows with the system font only up to a point.
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(
                density.density,
                density.fontScale.coerceAtMost(if (small) MAX_SMALL_CHIP_FONT_SCALE else MAX_CHIP_FONT_SCALE),
            ),
        ) {
            // Solfège names like "Sol♯" need a smaller size to fit the button.
            val named = when {
                small && name.length <= 2 -> MaterialTheme.typography.labelMedium
                small -> MaterialTheme.typography.labelSmall
                name.length <= 2 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.labelMedium
            }
            // The line height relative to the size, so a name that shrinks to fit gets shorter lines too.
            val textStyle = if (named.lineHeight.isSp && named.fontSize.isSp) {
                named.copy(lineHeight = (named.lineHeight.value / named.fontSize.value).em)
            } else {
                named
            }
            // The octave a subscript at labelSmall's size, relative to the name so it shrinks with it.
            val octaveStyle = SpanStyle(
                fontSize = (MaterialTheme.typography.labelSmall.fontSize.value / textStyle.fontSize.value).em,
                baselineShift = BaselineShift.Subscript,
            )
            Text(
                text = buildAnnotatedString {
                    append(musicText(name))
                    withStyle(octaveStyle) { append(octave.toString()) }
                },
                style = textStyle,
                color = colors.onSurface,
                maxLines = 1,
                // The name keeps its size where it fits; in a squeezed button (or at 2× text) it shrinks
                // as far as it must to fit. Never wrapped, so the
                // auto-size sees a name too wide (Ear training's AnswerButton in IntervalsPart.kt says why).
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = MIN_LABEL, maxFontSize = textStyle.fontSize, stepSize = 0.5.sp),
            )
            // A 12-string's octave pair: a small "8" in the top left corner, as an octave is marked in
            // music (8va), across from a tick's badge; TalkBack has it in the button's name.
            if (octaveString && !small) {
                Text(
                    OCTAVE_MARK,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = MaterialTheme.typography.labelSmall.fontSize * OCTAVE_MARK_SIZE * fit.buttonScale,
                        lineHeight = 1.em,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.onSurface.copy(alpha = OCTAVE_MARK_ALPHA),
                    maxLines = 1,
                    modifier = Modifier
                        .align(OCTAVE_MARK_PLACE)
                        .testTag(TunerTestTags.OCTAVE_MARK)
                        .clearAndSetSemantics {},
                )
            }
        }
    }
}

/**
 * Rings in [color] that grow out of the button (the box less [inset] each side, in [shape]) and fade,
 * two at a time, like sound waves; with [steady] (Android's animations off) one still ring instead.
 * Drawn behind the button, in the draw phase only.
 */
@Composable
private fun Modifier.soundingRings(shape: Shape, inset: Dp, color: Color, steady: Boolean): Modifier {
    val progress = if (steady) {
        null
    } else {
        rememberInfiniteTransition(label = "soundingRings").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(SOUND_RING_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "soundingRing",
        )
    }
    return drawWithCache {
        val gap = inset.toPx()
        val inner = Size((size.width - gap * 2f).coerceAtLeast(1f), (size.height - gap * 2f).coerceAtLeast(1f))
        val outline = Path().apply {
            addOutline(shape.createOutline(inner, layoutDirection, this@drawWithCache))
            translate(Offset(gap, gap))
        }
        val reach = gap + SOUND_RING_REACH.toPx()
        val stroke = SOUND_RING_STROKE.toPx()
        onDrawBehind {
            if (progress == null) {
                soundRing(outline, inner, gap + SOUND_RING_STEADY.toPx(), color.copy(alpha = color.alpha * SOUND_RING_STEADY_ALPHA), stroke)
            } else {
                for (wave in 0 until SOUND_RING_WAVES) {
                    val p = (progress.value + wave.toFloat() / SOUND_RING_WAVES) % 1f
                    soundRing(outline, inner, reach * p, color.copy(alpha = color.alpha * (1f - p) * SOUND_RING_ALPHA), stroke)
                }
            }
        }
    }
}

/**
 * The heard peg's ring (2.0, Phase 71, the mock-up's `.peg.on`): a [HEARD_RING_STROKE] ring in [colour] over the
 * round face's edge (the box less [inset] each side) and a soft glow round it, the same colour at
 * [HEARD_GLOW_ALPHA], in the room outside the face, at most [HEARD_GLOW] wide. [shown] (0..1, read while drawing)
 * fades both; the colour is read while drawing too, so a reading's tone recolours it without recomposing.
 */
private fun Modifier.heardRing(inset: Dp, colour: ColorProducer, highContrast: Boolean, shown: () -> Float): Modifier = drawWithContent {
    drawContent()
    val alpha = shown()
    if (alpha <= 0f) return@drawWithContent
    val gap = inset.toPx()
    val face = (minOf(size.width, size.height) / 2f - gap).coerceAtLeast(0f)
    if (face <= 0f) return@drawWithContent
    val tone = colour()
    val stroke = HEARD_RING_STROKE.toPx() * (if (highContrast) HIGH_CONTRAST_LINES else 1f)
    val glow = minOf(HEARD_GLOW.toPx(), gap)
    if (glow > 0f && !highContrast) {
        drawCircle(tone.copy(alpha = tone.alpha * HEARD_GLOW_ALPHA * alpha), radius = face + glow / 2f, style = Stroke(glow))
    }
    drawCircle(tone.copy(alpha = tone.alpha * alpha), radius = face - stroke / 2f, style = Stroke(stroke))
}

/**
 * The locked peg's mark: a small padlock in [on] on a disc in [fill], [radius] round, at the box's bottom end corner
 * (the face less [inset]), ringed in the [background] so it reads apart from the peg, drawn over it and outside its
 * face so nothing moves; across from the tick or the 12th-fret result at the top end corner. [scale] (0..1, read
 * while drawing) grows it in and out. Left to right in every language, as the row is.
 */
private fun Modifier.lockBadge(inset: Dp, radius: Dp, fill: Color, on: Color, background: Color, scale: () -> Float): Modifier =
    drawWithContent {
        drawContent()
        val grown = scale()
        if (grown <= 0f) return@drawWithContent
        val full = radius.toPx()
        val r = full * grown
        val centre = Offset(size.width - inset.toPx() - full * 0.35f, size.height - inset.toPx() - full * 0.35f)
        drawCircle(background, r + LOCK_BADGE_HALO.toPx() * grown, centre)
        drawCircle(fill, r, centre)
        drawPadlock(centre, r, on)
    }

/** A padlock [r] px round about [centre] in [colour]: a rounded body under an arched shackle, as the usual lock icon. */
private fun DrawScope.drawPadlock(centre: Offset, r: Float, colour: Color) {
    val bodyWidth = r * 1.0f
    val bodyHeight = r * 0.72f
    val bodyTop = centre.y - r * 0.12f
    drawRoundRect(
        colour,
        topLeft = Offset(centre.x - bodyWidth / 2f, bodyTop),
        size = Size(bodyWidth, bodyHeight),
        cornerRadius = CornerRadius(r * 0.14f),
    )
    val shackle = r * 0.62f
    val stroke = r * 0.2f
    drawArc(
        colour,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(centre.x - shackle / 2f, bodyTop - shackle * 0.62f),
        size = Size(shackle, shackle),
        style = Stroke(stroke),
    )
    // The shackle's legs down into the body.
    drawLine(colour, Offset(centre.x - shackle / 2f, bodyTop - shackle * 0.12f), Offset(centre.x - shackle / 2f, bodyTop + stroke), stroke)
    drawLine(colour, Offset(centre.x + shackle / 2f, bodyTop - shackle * 0.12f), Offset(centre.x + shackle / 2f, bodyTop + stroke), stroke)
}

/** [outline] (the button, [inner] in size) widened by [grow] px all round, drawn as a [stroke] px line. */
private fun DrawScope.soundRing(outline: Path, inner: Size, grow: Float, color: Color, stroke: Float) {
    val sx = (inner.width + grow * 2f) / inner.width
    val sy = (inner.height + grow * 2f) / inner.height
    scale(sx, sy) {
        drawPath(outline, color, style = Stroke(stroke / ((sx + sy) / 2f)))
    }
}

/** This size shrunk to fit a [side] square, keeping its proportions. */
private fun DpSize.fitInside(side: Dp): DpSize {
    val scale = minOf(side / width, side / height)
    return DpSize(width * scale, height * scale)
}

/** Largest font scale the string labels follow; beyond it "Sol♯₂" no longer fits its circle. */
private const val MAX_CHIP_FONT_SCALE = 1.3f

/** The octave pair's mark: "8", as music marks an octave up (8va), not a word to translate. */
private const val OCTAVE_MARK = "8"

/** The mark's size as a share of labelSmall's, and how strongly it shows. */
private const val OCTAVE_MARK_SIZE = 0.75f
private const val OCTAVE_MARK_ALPHA = 0.8f

/**
 * Up and to the left of the button, clear of the name, inside a round button's edge too: across from
 * the tick or the 12th-fret result, whose badge sits at the top right (`stringMarkBadge`), so both show
 * (until Phase 65 the 8 sat at the top right, under the badge). Left in a right-to-left language too,
 * as the badge stays right there.
 */
private val OCTAVE_MARK_PLACE = BiasAbsoluteAlignment(-0.62f, -0.82f)

/** The small chips' labels don't grow with the system font: they are only 32 dp. */
private const val MAX_SMALL_CHIP_FONT_SCALE = 1f

private val SMALL_CHIP_SIZE = 32.dp

/** The heard peg's ring over its face's edge, its glow outside it (the mock-up's 2 px and 3 px at 22 %), and how long it takes to come or go. */
private val HEARD_RING_STROKE = 2.dp

/** The high-contrast look's edge round each string button (the mock-up's pegs). */
private val HIGH_CONTRAST_EDGE = 2.5.dp
private val HEARD_GLOW = 3.dp
private const val HEARD_GLOW_ALPHA = 0.22f
private const val RING_FADE_MS = 250

/** The lock's badge on a peg (the tick's size) and on the compact layout's small pegs, the background's ring round it, and its growing in. */
private val LOCK_BADGE_RADIUS = 8.dp
private val SMALL_LOCK_BADGE_RADIUS = 5.5.dp
private val LOCK_BADGE_HALO = 1.5.dp
private const val LOCK_GROW_MS = 200

/** Room round a button for the heard ring's glow (until 2.0 the lock ring's): full-size buttons and the small chips. */
private val RING_GAP = 4.dp
private val SMALL_RING_GAP = 2.dp

/** The smallest touch target a full-size string button gets each way. */
private val MIN_TOUCH = 48.dp

/** A string's name shrinks to fit its button, but no further than this. */
private val MIN_LABEL = 8.sp

private val SMALL_CHIP_SPACING = 4.dp

/** How long a string's tick takes to grow in when it is ticked off as tuned. */
private const val TICK_GROW_MS = 400

/** The extra gap after a banjo's short fifth string (option A of docs/design/phase57-preview.html), and in the small row. */
private val SHORT_STRING_GAP = 12.dp
private val SMALL_SHORT_STRING_GAP = 6.dp

/** The sounding string's rings: how often one starts, how many at once, how far past the lock ring they reach, line and alpha. */
private const val SOUND_RING_PERIOD_MS = 1_200
private const val SOUND_RING_WAVES = 2
private val SOUND_RING_REACH = 10.dp
private val SOUND_RING_STROKE = 2.dp
private const val SOUND_RING_ALPHA = 0.9f

/** With animations off: one still ring this far outside the lock ring's place, at this alpha. */
private val SOUND_RING_STEADY = 4.dp
private const val SOUND_RING_STEADY_ALPHA = 0.8f

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun StringSelectorPreview() {
    MaterialTheme { StringSelector(Tunings.STANDARD, targetString = 1, lockedString = 1, onStringClick = {}) }
}
