package io.github.klaboukes.twangle.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStoreSettingsRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val file: File get() = folder.root.resolve("settings.preferences_pb")
    private val scopes = mutableListOf<CoroutineScope>()

    /**
     * A DataStore on [file]. Only one may be open per file at a time, see [close].
     *
     * Uses DataStore's Okio storage: the default file storage (used on the phone) can't replace an
     * existing file when the tests run on Windows. The mapping under test is the same either way.
     */
    private fun dataStore(): DataStore<Preferences> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        val storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file.absolutePath.toPath() }
        return PreferenceDataStoreFactory.create(storage = storage, scope = scope)
    }

    private fun repository(dataStore: DataStore<Preferences> = dataStore()) = DataStoreSettingsRepository(dataStore)

    /** Shuts every open DataStore so the next one may use the same file. */
    private suspend fun close() {
        scopes.forEach { it.coroutineContext.job.cancelAndJoin() }
        scopes.clear()
    }

    @After
    fun tearDown() = runTest { close() }

    @Test
    fun `a fresh install has the defaults`() = runTest {
        val settings = repository().settings.first()

        assertEquals(AppSettings(), settings)
        assertEquals(440f, settings.a4Hz)
        assertEquals(Tunings.STANDARD, settings.tuning)
        assertEquals(Notation.SHARPS, settings.notation)
        assertEquals(ThemeMode.SYSTEM, settings.theme)
        assertEquals(VisualStyle.CONTEMPORARY, settings.style)
        assertEquals(true, settings.dynamicColor)
        assertEquals(true, settings.keepScreenOn)
        assertEquals(true, settings.vibrateInTune)
        assertEquals(true, settings.showTuneHint)
        assertEquals(true, settings.showWaves)
        assertEquals(WaveLook.RIBBON, settings.waveLook)
    }

    @Test
    fun `every setting is saved`() = runTest {
        val repo = repository()
        repo.setA4(432f)
        repo.setTuning(Tunings.DADGAD)
        repo.setNotation(Notation.SOLFEGE)
        repo.setTheme(ThemeMode.DARK)
        repo.setStyle(VisualStyle.SPACE_AGE)
        repo.setDynamicColor(false)
        repo.setKeepScreenOn(false)
        repo.setVibrateInTune(false)
        repo.setShowTuneHint(false)
        repo.setShowWaves(false)
        repo.setWaveLook(WaveLook.FIREFLIES)

        val expected = AppSettings(
            a4Hz = 432f,
            tuning = Tunings.DADGAD,
            notation = Notation.SOLFEGE,
            theme = ThemeMode.DARK,
            style = VisualStyle.SPACE_AGE,
            dynamicColor = false,
            keepScreenOn = false,
            vibrateInTune = false,
            showTuneHint = false,
            showWaves = false,
            waveLook = WaveLook.FIREFLIES,
        )
        assertEquals(expected, repo.settings.first())
    }

    @Test
    fun `settings survive an app restart`() = runTest {
        repository().apply {
            setA4(442f)
            setTuning(Tunings.DROP_D)
            setNotation(Notation.FLATS)
            setStyle(VisualStyle.MIDDLE_AGES)
        }
        close()

        val settings = repository().settings.first()
        assertEquals(442f, settings.a4Hz)
        assertEquals(Tunings.DROP_D, settings.tuning)
        assertEquals(Notation.FLATS, settings.notation)
        assertEquals(VisualStyle.MIDDLE_AGES, settings.style)
    }

    @Test
    fun `A4 is rounded to whole Hz and kept within 415 to 466`() = runTest {
        val repo = repository()

        repo.setA4(441.6f)
        assertEquals(442f, repo.settings.first().a4Hz)
        repo.setA4(400f)
        assertEquals(415f, repo.settings.first().a4Hz)
        repo.setA4(500f)
        assertEquals(466f, repo.settings.first().a4Hz)
    }

    @Test
    fun `unknown or out-of-range saved values fall back to the defaults`() = runTest {
        val store = dataStore()
        store.edit {
            it[stringPreferencesKey("tuning_id")] = "removed_in_v2"
            it[stringPreferencesKey("notation")] = "GERMAN"
            it[stringPreferencesKey("theme")] = "SEPIA"
            it[stringPreferencesKey("visual_style")] = "BAROQUE"
            it[stringPreferencesKey("wave_look")] = "LAVA_LAMP"
            it[floatPreferencesKey("a4_hz")] = 1000f
        }

        val settings = repository(store).settings.first()
        assertEquals(Tunings.DEFAULT, settings.tuning)
        assertEquals(Notation.DEFAULT, settings.notation)
        assertEquals(ThemeMode.SYSTEM, settings.theme)
        assertEquals(VisualStyle.DEFAULT, settings.style)
        assertEquals(WaveLook.DEFAULT, settings.waveLook)
        assertEquals(466f, settings.a4Hz)
    }

    @Test
    fun `an unreadable file gives the defaults instead of crashing`() = runTest {
        file.writeText("this is not a preferences file")

        assertEquals(AppSettings(), repository().settings.first())
    }
}
