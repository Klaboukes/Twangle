package io.github.klaboukes.twangle.wear

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.klaboukes.twangle.core.dsp.metronome.ClickTrack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** What the watch's metronome keeps between visits: its tempo, and whether the speaker clicks (off: taps only). */
data class WatchMetronomeChoices(val bpm: Int, val click: Boolean) {
    companion object {
        /** The phone's starting tempo, and taps only: a metronome on the wrist is quiet unless asked. */
        val DEFAULT = WatchMetronomeChoices(bpm = ClickTrack.DEFAULT_BPM, click = false)
    }
}

/** Where the watch's metronome keeps its [WatchMetronomeChoices]; behind an interface so the view model's tests use a fake. */
interface WatchMetronomeStore {
    /** The kept choices, [WatchMetronomeChoices.DEFAULT] before anything was kept, then again on every change. */
    val choices: Flow<WatchMetronomeChoices>

    suspend fun setTempo(bpm: Int)

    suspend fun setClick(click: Boolean)

    companion object {
        /** The watch's own file (`files/datastore/watch_metronome.preferences_pb`), which Android's backup leaves out. */
        fun of(context: Context): WatchMetronomeStore = DataStoreWatchMetronomeStore(context.applicationContext.watchMetronomeData)
    }
}

/**
 * [WatchMetronomeStore] in a small DataStore of its own, read and written off the main thread. A tempo
 * outside 30–250 BPM (written by a later version, say) reads clamped; a broken file reads as the defaults.
 */
class DataStoreWatchMetronomeStore(private val data: DataStore<Preferences>) : WatchMetronomeStore {

    override val choices: Flow<WatchMetronomeChoices> = data.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            WatchMetronomeChoices(
                bpm = ClickTrack.clampBpm(prefs[TEMPO] ?: WatchMetronomeChoices.DEFAULT.bpm),
                click = prefs[CLICK] ?: WatchMetronomeChoices.DEFAULT.click,
            )
        }

    override suspend fun setTempo(bpm: Int) {
        save { it[TEMPO] = ClickTrack.clampBpm(bpm) }
    }

    override suspend fun setClick(click: Boolean) {
        save { it[CLICK] = click }
    }

    /** A failed write (a full disk) is let go: the choice still holds until the app closes. */
    private suspend fun save(change: (MutablePreferences) -> Unit) {
        try {
            data.edit(change)
        } catch (_: IOException) {
        }
    }

    private companion object {
        val TEMPO = intPreferencesKey("tempo_bpm")
        val CLICK = booleanPreferencesKey("click")
    }
}

/** The one DataStore for the file in this process: the app and, later, its tile read the same. */
private val Context.watchMetronomeData: DataStore<Preferences> by preferencesDataStore(name = WATCH_METRONOME_FILE)

/** The DataStore's name; on disk `files/datastore/watch_metronome.preferences_pb` (left out of backups). */
internal const val WATCH_METRONOME_FILE = "watch_metronome"
