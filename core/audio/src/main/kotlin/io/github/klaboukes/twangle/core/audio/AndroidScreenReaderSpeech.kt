package io.github.klaboukes.twangle.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * [ScreenReaderSpeech] from the phone's own reports (Phase 81).
 *
 * A screen reader is on while touch exploration is (`AccessibilityManager.isTouchExplorationEnabled`, followed through
 * its listener): TalkBack and the screen readers built like it turn it on. Only then is the playback watched, with
 * `AudioManager.registerAudioPlaybackCallback` (API 26, the app's minSdk), which reports every app's players whenever
 * one starts, pauses, stops or goes.
 *
 * What it relies on, checked on the Xiaomi 12X (Android 13) on 2026-11-07:
 * - TalkBack's speech is played by the text-to-speech engine's app, not TalkBack's, as an `AudioTrack` with the usage
 *   `USAGE_ASSISTANCE_ACCESSIBILITY` (and content type unknown); its earcons are a `SoundPool` with the same usage.
 *   So a sound is the screen reader's by its usage alone, never by its app.
 * - Each spoken phrase is a new player, started, then stopped once the engine has played its last sample (Android's
 *   `BlockingAudioTrack` waits for that before it stops), then released; the next phrase starts within ~0.1 s. The
 *   earcons' `SoundPool` goes back to idle when quiet.
 * - An app without the system's privileges is told only of the players that are playing now (Android leaves out the
 *   idle, paused and stopped ones, and blanks their app; their usage stays), and this class can't ask a player's state
 *   (`getPlayerState` is hidden). So "an accessibility player is in the list" means "the screen reader sounds now".
 */
class AndroidScreenReaderSpeech(context: Context) : ScreenReaderSpeech {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val accessibilityManager = context.getSystemService(AccessibilityManager::class.java)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val speaking: Flow<Boolean> = touchExploration()
        .flatMapLatest { on -> if (on) accessibilityPlayback() else flowOf(false) }
        .distinctUntilChanged()

    override val screenReaderOn: Flow<Boolean> = touchExploration()

    /** Whether a screen reader is on, now and on every change. */
    private fun touchExploration(): Flow<Boolean> = callbackFlow {
        // Told on the main thread; the listener only hands the answer on.
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { on -> trySend(on) }
        accessibilityManager.addTouchExplorationStateChangeListener(listener)
        trySend(accessibilityManager.isTouchExplorationEnabled)
        awaitClose { accessibilityManager.removeTouchExplorationStateChangeListener(listener) }
    }.conflate().distinctUntilChanged()

    /** Whether any app plays a sound with the accessibility usage now, and on every change. */
    internal fun accessibilityPlayback(): Flow<Boolean> = callbackFlow {
        // Called on the main thread (no handler given), a few times per spoken phrase; it only looks through a short list.
        val callback = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
                trySend(anyAccessibility(configs))
            }
        }
        audioManager.registerAudioPlaybackCallback(callback, null)
        trySend(anyAccessibility(audioManager.activePlaybackConfigurations))
        awaitClose { audioManager.unregisterAudioPlaybackCallback(callback) }
    }.conflate().distinctUntilChanged()

    private fun anyAccessibility(configs: List<AudioPlaybackConfiguration>): Boolean =
        configs.any { it.audioAttributes.usage == AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY }
}
