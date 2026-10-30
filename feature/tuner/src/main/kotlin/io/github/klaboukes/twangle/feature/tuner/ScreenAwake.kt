package io.github.klaboukes.twangle.feature.tuner

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.klaboukes.twangle.core.dsp.awake.ScreenAwakePhase
import io.github.klaboukes.twangle.core.dsp.awake.ScreenAwakeRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Keep screen on's quiet timer on the screen ([ScreenAwakeRule], on the phone's clock): [phase]
 * says whether the screen is held on, the warning shows, or the phone's own timeout has it.
 */
@Stable
internal class ScreenAwake(private val rule: ScreenAwakeRule, private val clock: () -> Long) {
    var phase by mutableStateOf(rule.phase(clock()))
        private set

    // Goes up when activity ends the warning or the screen's release, so [run]'s wait starts afresh.
    private var restarts by mutableIntStateOf(0)

    fun touched() {
        rule.touched(clock())
        update()
    }

    fun heard(state: TunerUiState) {
        rule.heard(soundOf(state), clock())
        update()
    }

    /** How much of the warning is left now, 1 to 0. */
    fun warningLeft(): Float = rule.warningLeft(clock())

    private fun update() {
        val was = phase
        phase = rule.phase(clock())
        if (was != ScreenAwakePhase.ON) restarts++
    }

    /** Moves [phase] on as the quiet time passes; runs until cancelled. */
    suspend fun run() {
        snapshotFlow { restarts }.collectLatest {
            while (true) {
                val now = clock()
                phase = rule.phase(now)
                delay(rule.millisUntilChange(now) ?: break)
            }
        }
    }
}

/** What [state] tells [ScreenAwakeRule] of the player. */
internal fun soundOf(state: TunerUiState): ScreenAwakeRule.Sound = when (state) {
    is TunerUiState.Detected -> ScreenAwakeRule.Sound.SOUNDING
    is TunerUiState.StrumCheck, is TunerUiState.IntonationCheck, is TunerUiState.ChordName -> ScreenAwakeRule.Sound.PLAYED
    is TunerUiState.Listening, is TunerUiState.Error, TunerUiState.PermissionRequired -> ScreenAwakeRule.Sound.QUIET
}

/**
 * The tuner's [ScreenAwake], fed every state of [readings] while [enabled] (Keep screen on, with the
 * microphone allowed). Turning on, and the tuner coming back to the screen, start the quiet time afresh.
 */
@Composable
internal fun rememberScreenAwake(readings: State<TunerUiState>, enabled: Boolean): ScreenAwake {
    val awake = remember { ScreenAwake(ScreenAwakeRule(SystemClock.elapsedRealtime())) { SystemClock.elapsedRealtime() } }
    LaunchedEffect(awake, enabled) {
        if (!enabled) return@LaunchedEffect
        awake.touched()
        awake.run()
    }
    LaunchedEffect(awake, readings) { snapshotFlow { readings.value }.collect(awake::heard) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, awake) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) awake.touched() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return awake
}

/**
 * Tells [awake] of every touch on this element or anything in it, before its children see it and
 * without taking it from them: a tap on a string still picks the string.
 */
internal fun Modifier.touchesKeepAwake(awake: ScreenAwake): Modifier = pointerInput(awake) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        awake.touched()
    }
}

/**
 * The last seconds' warning before the screen is let go: "Screen turns off soon", "Tap to keep it
 * on", and a thin bar that empties as [left] (1 to 0) runs down; [onKeepOn] on a tap. TalkBack
 * reads it as it shows. With [animationsOff] the bar steps once a second instead of gliding.
 */
@Composable
internal fun ScreenOffWarning(left: () -> Float, onKeepOn: () -> Unit, animationsOff: Boolean, modifier: Modifier = Modifier) {
    Surface(
        onClick = onKeepOn,
        modifier = modifier
            .padding(16.dp)
            .widthIn(max = 360.dp)
            .testTag(TunerTestTags.SCREEN_OFF_WARNING)
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.tuner_screen_off_soon),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.tuner_screen_off_keep_on),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            DrainingBar(left, animationsOff)
        }
    }
}

/** A thin bar, full at [left] 1 and empty at 0, emptying towards the start; redrawn without recomposing. */
@Composable
private fun DrainingBar(left: () -> Float, animationsOff: Boolean) {
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(animationsOff) {
        while (true) {
            if (animationsOff) {
                delay(1_000)
                frame++
            } else {
                withFrameMillis { frame = it }
            }
        }
    }
    val color = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.2f))
            .drawBehind {
                frame // Redraws with every frame (or second).
                val width = size.width * left()
                val x = if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f
                drawRect(color, topLeft = Offset(x, 0f), size = Size(width, size.height))
            },
    )
}
