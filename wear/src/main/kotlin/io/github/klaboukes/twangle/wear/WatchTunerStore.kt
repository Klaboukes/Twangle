package io.github.klaboukes.twangle.wear

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * What the watch's tuner keeps between visits: its one setting, whether the vibration guide is on (Phase 85). Null until
 * the player flips its switch: the guide then follows the screen reader ([watchVibrationGuideOn]). Behind an interface
 * so the view model's tests keep it in memory ([inMemory]).
 */
interface WatchTunerStore {
    /** The player's choice for the vibration guide, null before they made one, then again on every change. */
    val vibrationGuide: Flow<Boolean?>

    suspend fun setVibrationGuide(on: Boolean)

    companion object {
        /** The watch's own file (`files/datastore/watch_tuner.preferences_pb`), which Android's backup leaves out. */
        fun of(context: Context): WatchTunerStore = DataStoreWatchTunerStore(context.applicationContext.watchTunerData)

        /** Kept in memory only, starting with [choice]: tests and previews. */
        fun inMemory(choice: Boolean? = null): WatchTunerStore = object : WatchTunerStore {
            private val kept = MutableStateFlow(choice)
            override val vibrationGuide: Flow<Boolean?> = kept

            override suspend fun setVibrationGuide(on: Boolean) {
                kept.value = on
            }
        }
    }
}

/** [WatchTunerStore] in a small DataStore of its own, read and written off the main thread; a broken file reads as no choice. */
class DataStoreWatchTunerStore(private val data: DataStore<Preferences>) : WatchTunerStore {

    override val vibrationGuide: Flow<Boolean?> = data.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[VIBRATION_GUIDE] }

    /** A failed write (a full disk) is let go: the switch still holds until the app closes. */
    override suspend fun setVibrationGuide(on: Boolean) {
        try {
            data.edit { it[VIBRATION_GUIDE] = on }
        } catch (_: IOException) {
        }
    }

    private companion object {
        /** The phone's key for the same setting (`AppSettings.vibrationGuide`). */
        val VIBRATION_GUIDE = booleanPreferencesKey("vibration_guide")
    }
}

/** The one DataStore for the file in this process. */
private val Context.watchTunerData: DataStore<Preferences> by preferencesDataStore(name = WATCH_TUNER_FILE)

/** The DataStore's name; on disk `files/datastore/watch_tuner.preferences_pb` (left out of backups). */
internal const val WATCH_TUNER_FILE = "watch_tuner"
