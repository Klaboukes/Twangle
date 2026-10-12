package io.github.klaboukes.twangle.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
        assertEquals(TunerMode.AUTO, settings.mode)
        assertEquals(Tunings.STANDARD, settings.tuning)
        assertEquals(Notation.SHARPS, settings.notation)
        assertEquals(InTunePrecision.RELAXED, settings.inTunePrecision)
        assertEquals(ThemeMode.SYSTEM, settings.theme)
        assertEquals(VisualStyle.CONTEMPORARY, settings.style)
        assertEquals(true, settings.dynamicColor)
        assertEquals(true, settings.keepScreenOn)
        assertEquals(true, settings.vibrateInTune)
        assertEquals(true, settings.showTuneHint)
        assertEquals(false, settings.showSignalBars)
        assertEquals(TunerDisplay.WAVE, settings.display)
        assertEquals(WaveLook.RIBBON, settings.waveLook)
        assertEquals(MeterLook.CLASSIC, settings.meterLook)
        assertEquals(true, settings.waveBreathesInTune)
        assertEquals(false, settings.stringLockTipSeen)
        assertEquals(false, settings.keepTwoPaneLandscape)
    }

    @Test
    fun `every setting is saved`() = runTest {
        val repo = repository()
        repo.setA4(432f)
        repo.setMode(TunerMode.CHROMATIC)
        repo.setTuning(Tunings.DADGAD)
        repo.setNotation(Notation.SOLFEGE)
        repo.setInTunePrecision(InTunePrecision.PRECISE)
        repo.setTheme(ThemeMode.DARK)
        repo.setStyle(VisualStyle.SPACE_AGE)
        repo.setDynamicColor(false)
        repo.setKeepScreenOn(false)
        repo.setVibrateInTune(false)
        repo.setShowTuneHint(false)
        repo.setShowSignalBars(true)
        repo.setDisplay(TunerDisplay.NEEDLE)
        repo.setWaveLook(WaveLook.FIREFLIES)
        repo.setMeterLook(MeterLook.COMET)
        repo.setWaveBreathesInTune(false)
        repo.setStringLockTipSeen(true)
        repo.setKeepTwoPaneLandscape(true)

        val expected = AppSettings(
            a4Hz = 432f,
            mode = TunerMode.CHROMATIC,
            tuning = Tunings.DADGAD,
            notation = Notation.SOLFEGE,
            inTunePrecision = InTunePrecision.PRECISE,
            theme = ThemeMode.DARK,
            style = VisualStyle.SPACE_AGE,
            dynamicColor = false,
            keepScreenOn = false,
            vibrateInTune = false,
            showTuneHint = false,
            showSignalBars = true,
            display = TunerDisplay.NEEDLE,
            waveLook = WaveLook.FIREFLIES,
            meterLook = MeterLook.COMET,
            waveBreathesInTune = false,
            stringLockTipSeen = true,
            keepTwoPaneLandscape = true,
        )
        assertEquals(expected, repo.settings.first())
    }

    @Test
    fun `settings survive an app restart`() = runTest {
        repository().apply {
            setA4(442f)
            setMode(TunerMode.CHROMATIC)
            setTuning(Tunings.DROP_D)
            setNotation(Notation.FLATS)
            setStyle(VisualStyle.MIDDLE_AGES)
            setInTunePrecision(InTunePrecision.PRECISE)
            setStringLockTipSeen(true)
            setKeepTwoPaneLandscape(true)
            setWaveBreathesInTune(false)
            setMeterLook(MeterLook.SPIRIT_LEVEL)
        }
        close()

        val settings = repository().settings.first()
        assertEquals(442f, settings.a4Hz)
        assertEquals(TunerMode.CHROMATIC, settings.mode)
        assertEquals(Tunings.DROP_D, settings.tuning)
        assertEquals(Notation.FLATS, settings.notation)
        assertEquals(VisualStyle.MIDDLE_AGES, settings.style)
        assertEquals(InTunePrecision.PRECISE, settings.inTunePrecision)
        assertEquals("the string-lock tip doesn't come back", true, settings.stringLockTipSeen)
        assertEquals("the two-pane landscape choice is kept", true, settings.keepTwoPaneLandscape)
        assertEquals("the wave keeps holding still", false, settings.waveBreathesInTune)
        assertEquals("the meter look is kept", MeterLook.SPIRIT_LEVEL, settings.meterLook)
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
            it[stringPreferencesKey("tuner_mode")] = "BASS"
            it[stringPreferencesKey("tuning_id")] = "removed_in_v2"
            it[stringPreferencesKey("notation")] = "GERMAN"
            it[stringPreferencesKey("in_tune_precision")] = "PERFECT"
            it[stringPreferencesKey("theme")] = "SEPIA"
            it[stringPreferencesKey("visual_style")] = "BAROQUE"
            it[stringPreferencesKey("wave_look")] = "LAVA_LAMP"
            it[stringPreferencesKey("meter_look")] = "SUNDIAL"
            it[stringPreferencesKey("tuner_display")] = "HOLOGRAM"
            it[floatPreferencesKey("a4_hz")] = 1000f
        }

        val settings = repository(store).settings.first()
        assertEquals(TunerMode.AUTO, settings.mode)
        assertEquals(Tunings.DEFAULT, settings.tuning)
        assertEquals(Notation.DEFAULT, settings.notation)
        assertEquals(InTunePrecision.DEFAULT, settings.inTunePrecision)
        assertEquals(ThemeMode.SYSTEM, settings.theme)
        assertEquals(VisualStyle.DEFAULT, settings.style)
        assertEquals(WaveLook.DEFAULT, settings.waveLook)
        assertEquals("an unknown meter look gives Classic", MeterLook.CLASSIC, settings.meterLook)
        assertEquals(TunerDisplay.DEFAULT, settings.display)
        assertEquals(466f, settings.a4Hz)
    }

    @Test
    fun `the old wave view switch carries over to the display`() = runTest {
        val store = dataStore()
        store.edit { it[booleanPreferencesKey("show_waves")] = false }
        assertEquals("wave view off: the needle with cents", TunerDisplay.NEEDLE, repository(store).settings.first().display)

        store.edit { it[booleanPreferencesKey("show_waves")] = true }
        assertEquals("wave view on: the wave", TunerDisplay.WAVE, repository(store).settings.first().display)
    }

    @Test
    fun `a picked display wins over the old wave view switch and survives a restart`() = runTest {
        dataStore().edit { it[booleanPreferencesKey("show_waves")] = false }
        close()
        repository().setDisplay(TunerDisplay.WAVE)
        close()

        val store = dataStore()
        assertEquals(TunerDisplay.WAVE, repository(store).settings.first().display)
        assertEquals("the old switch is cleared", null, store.data.first()[booleanPreferencesKey("show_waves")])
    }

    @Test
    fun `an old strobe display opens as the wave with the strobe look`() = runTest {
        val store = dataStore()
        store.edit {
            it[stringPreferencesKey("tuner_display")] = "STROBE"
            it[stringPreferencesKey("wave_look")] = "FIREFLIES"
        }

        val settings = repository(store).settings.first()
        assertEquals(TunerDisplay.WAVE, settings.display)
        assertEquals("the strobe it showed, not the look picked for the wave back then", WaveLook.STROBE, settings.waveLook)
    }

    @Test
    fun `the old stripes look opens as the strobe look`() = runTest {
        val store = dataStore()
        store.edit {
            it[stringPreferencesKey("tuner_display")] = "WAVE"
            it[stringPreferencesKey("wave_look")] = "STRIPES"
        }

        val settings = repository(store).settings.first()
        assertEquals(TunerDisplay.WAVE, settings.display)
        assertEquals(WaveLook.STROBE, settings.waveLook)
    }

    @Test
    fun `a look picked after an old strobe display shows and survives a restart`() = runTest {
        dataStore().edit { it[stringPreferencesKey("tuner_display")] = "STROBE" }
        close()
        repository().setWaveLook(WaveLook.LOOP)
        close()

        val store = dataStore()
        val settings = repository(store).settings.first()
        assertEquals(TunerDisplay.WAVE, settings.display)
        assertEquals(WaveLook.LOOP, settings.waveLook)
        assertEquals("the old display is rewritten", "WAVE", store.data.first()[stringPreferencesKey("tuner_display")])
    }

    @Test
    fun `switching away from an old strobe display and back keeps the strobe look`() = runTest {
        val store = dataStore()
        store.edit {
            it[stringPreferencesKey("tuner_display")] = "STROBE"
            it[stringPreferencesKey("wave_look")] = "RIBBON"
        }
        val repo = repository(store)

        repo.setDisplay(TunerDisplay.NEEDLE)
        assertEquals(TunerDisplay.NEEDLE, repo.settings.first().display)
        repo.setDisplay(TunerDisplay.WAVE)
        assertEquals(WaveLook.STROBE, repo.settings.first().waveLook)
    }

    @Test
    fun `the old wave view switch still carries over next to an old stripes look`() = runTest {
        val store = dataStore()
        store.edit {
            it[booleanPreferencesKey("show_waves")] = true
            it[stringPreferencesKey("wave_look")] = "STRIPES"
        }

        val settings = repository(store).settings.first()
        assertEquals(TunerDisplay.WAVE, settings.display)
        assertEquals(WaveLook.STROBE, settings.waveLook)
    }

    @Test
    fun `an unreadable file gives the defaults instead of crashing`() = runTest {
        file.writeText("this is not a preferences file")

        assertEquals(AppSettings(), repository().settings.first())
    }

    @Test
    fun `every meter look is saved under meter_look by its name`() = runTest {
        val store = dataStore()
        val repo = repository(store)
        for (look in MeterLook.entries) {
            repo.setMeterLook(look)
            assertEquals(look, repo.settings.first().meterLook)
            assertEquals(look.name, store.data.first()[stringPreferencesKey("meter_look")])
        }
    }

    @Test
    fun `the six meter looks come in the owner's order, Classic first and the default`() {
        assertEquals(
            listOf(
                MeterLook.CLASSIC,
                MeterLook.LIGHT_ARC,
                MeterLook.SPIRIT_LEVEL,
                MeterLook.ROLLING_SCALE,
                MeterLook.COMET,
                MeterLook.STUDIO_METER,
            ),
            MeterLook.entries,
        )
        assertEquals(MeterLook.CLASSIC, MeterLook.DEFAULT)
    }
}
