package io.github.klaboukes.twangle.wear

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.wear.tiles.TileService
import io.github.klaboukes.twangle.core.audio.AndroidAudioRecorder
import io.github.klaboukes.twangle.core.audio.AndroidMetronomePlayer

/**
 * The watch app's one activity: the tuner, after the microphone permission, and with the metronome's switch
 * on ([watchToolSwitchesFor]) the metronome a swipe left of it. Opened with [EXTRA_PAGE] it shows that page
 * ([intentFor]), at start and when opened again while it runs.
 */
class WatchActivity : ComponentActivity() {

    private var request by mutableStateOf<WatchPage?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // The opening screen (Theme.Twangle.Watch.Starting): the harmonics swinging into place, as on the phone.
        installSplashScreen().setOnExitAnimationListener { opening -> opening.fadeOutOnceStill() }
        super.onCreate(savedInstanceState)
        // Asks the watch to fetch the tile again: one it fetched before an update stays as it was (the Galaxy
        // Watch 5 kept the old mark after an install whose fetch timed out, 2026-11-05). Cheap; the tile never changes.
        TileService.getUpdater(this).requestUpdate(WatchTileService::class.java)
        val tools = watchToolSwitchesFor(BuildConfig.DEBUG)
        // Only a fresh start opens on the page asked for; a remade activity keeps the page it showed.
        val opening = if (savedInstanceState == null) pageOf(intent) ?: WatchPage.TUNER else WatchPage.TUNER
        val factory = viewModelFactory {
            initializer { WatchTunerViewModel(AndroidAudioRecorder(applicationContext)) }
            initializer {
                WatchMetronomeViewModel(
                    player = AndroidMetronomePlayer(applicationContext),
                    store = WatchMetronomeStore.of(applicationContext),
                    taps = WatchBeatTaps.of(applicationContext),
                )
            }
        }
        setContent {
            WatchApp(
                tools = tools,
                tuner = { viewModel<WatchTunerViewModel>(factory = factory) },
                metronome = { viewModel<WatchMetronomeViewModel>(factory = factory) },
                opening = opening,
                request = request,
                onRequestDone = { request = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pageOf(intent)?.let { request = it }
    }

    companion object {
        /** The page to open on, a [WatchPage]'s name ("METRONOME"); without it, the tuner. */
        const val EXTRA_PAGE = "io.github.klaboukes.twangle.wear.PAGE"

        /** Opens the app on [page] (with the metronome's switch off, always the tuner). */
        fun intentFor(context: Context, page: WatchPage): Intent =
            Intent(context, WatchActivity::class.java).putExtra(EXTRA_PAGE, page.name)

        internal fun pageOf(intent: Intent?): WatchPage? =
            intent?.getStringExtra(EXTRA_PAGE)?.let { name -> WatchPage.entries.firstOrNull { it.name == name } }
    }
}
