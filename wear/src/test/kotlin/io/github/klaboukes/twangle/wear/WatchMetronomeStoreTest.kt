package io.github.klaboukes.twangle.wear

import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The watch metronome's kept choices, in a real DataStore file on the JVM. */
class WatchMetronomeStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // DataStore's Okio storage, as :core:settings' tests: the default file storage (the watch's) can't replace
    // an existing file when the tests run on Windows. The mapping under test is the same either way.
    private val data by lazy {
        val file = folder.root.resolve("$WATCH_METRONOME_FILE.preferences_pb")
        PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file.absolutePath.toPath() },
            scope = scope,
        )
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `nothing kept reads as the phone's tempo with taps only`() = runBlocking {
        val store = DataStoreWatchMetronomeStore(data)
        assertEquals(WatchMetronomeChoices(bpm = 120, click = false), store.choices.first())
    }

    @Test
    fun `keeps the tempo and the click`() = runBlocking {
        val store = DataStoreWatchMetronomeStore(data)
        store.setTempo(96)
        store.setClick(true)
        assertEquals(WatchMetronomeChoices(bpm = 96, click = true), store.choices.first())
        store.setClick(false)
        assertEquals(WatchMetronomeChoices(bpm = 96, click = false), store.choices.first())
    }

    @Test
    fun `a tempo outside 30 to 250 is read clamped`() = runBlocking {
        val store = DataStoreWatchMetronomeStore(data)
        store.setTempo(400)
        assertEquals(250, store.choices.first().bpm)
        data.edit { it[intPreferencesKey("tempo_bpm")] = 5 }
        assertEquals(30, store.choices.first().bpm)
    }
}
