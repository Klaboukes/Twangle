package io.github.klaboukes.twangle.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

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
        assertEquals(Instruments.GUITAR, settings.instrument)
        assertEquals(Tunings.STANDARD, settings.tuning)
        assertEquals("a fresh install follows the app's language", null, settings.notationChoice)
        assertEquals(Notation.SHARPS, settings.notationFor("en"))
        assertEquals(Notation.GERMAN, settings.notationFor("de"))
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
        assertEquals("a fresh install has the calm glow", InTuneEffect.CALM, settings.inTuneEffect)
        assertEquals("a fresh install is due the tour", false, settings.tourSeen)
        assertEquals(false, settings.keepTwoPaneLandscape)
        assertEquals("the metronome starts at 120 BPM", 120, settings.metronomeBpm)
        assertEquals("the metronome starts in 4/4", Meter.FOUR_FOUR, settings.metronomeMeter)
        assertEquals("the metronome doesn't vibrate until asked", false, settings.metronomeVibrate)
        assertEquals("Practice opens on the metronome", PracticeTool.METRONOME, settings.practiceTool)
        assertEquals("the chord charts open on C major", PitchClass.C to ChordQuality.MAJOR, settings.practiceRoot to settings.practiceChordQuality)
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
        repo.setMeterLook(MeterLook.LIGHT_ARC)
        repo.setInTuneEffect(InTuneEffect.BOLD)
        repo.setTourSeen(true)
        repo.setKeepTwoPaneLandscape(true)
        repo.setMetronomeBpm(96)
        repo.setMetronomeMeter(Meter.SEVEN_EIGHT)
        repo.setMetronomeVibrate(true)
        repo.setPracticeTool(PracticeTool.CHORDS)
        repo.setPracticeRoot(PitchClass.A_SHARP)
        repo.setPracticeChordQuality(ChordQuality.MINOR_SEVENTH)

        val expected = AppSettings(
            a4Hz = 432f,
            mode = TunerMode.CHROMATIC,
            tuning = Tunings.DADGAD,
            notationChoice = Notation.SOLFEGE,
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
            meterLook = MeterLook.LIGHT_ARC,
            inTuneEffect = InTuneEffect.BOLD,
            tourSeen = true,
            keepTwoPaneLandscape = true,
            metronomeBpm = 96,
            metronomeMeter = Meter.SEVEN_EIGHT,
            metronomeVibrate = true,
            practiceTool = PracticeTool.CHORDS,
            practiceRoot = PitchClass.A_SHARP,
            practiceChordQuality = ChordQuality.MINOR_SEVENTH,
        )
        assertEquals(expected, repo.settings.first())
    }

    @Test
    fun `a picked notation can go back to automatic`() = runTest {
        val repo = repository()
        repo.setNotation(Notation.GERMAN)
        assertEquals(Notation.GERMAN, repo.settings.first().notationChoice)
        repo.setNotation(null)
        assertEquals(null, repo.settings.first().notationChoice)
        assertEquals(Notation.SOLFEGE, repo.settings.first().notationFor("it"))
    }

    @Test
    fun `settings survive an app restart`() = runTest {
        repository().apply {
            setA4(442f)
            setMode(TunerMode.CHROMATIC)
            setTuning(Tunings.DROP_D)
            setNotation(Notation.FLATS)
            setStyle(VisualStyle.ANCIENT)
            setInTunePrecision(InTunePrecision.PRECISE)
            setKeepTwoPaneLandscape(true)
            setMeterLook(MeterLook.SPIRIT_LEVEL)
            setInTuneEffect(InTuneEffect.BOLD)
            setMetronomeBpm(72)
            setMetronomeMeter(Meter.SIX_EIGHT)
            setMetronomeVibrate(true)
            setPracticeTool(PracticeTool.CHORDS)
            setPracticeRoot(PitchClass.F_SHARP)
            setPracticeChordQuality(ChordQuality.SUS4)
        }
        close()

        val settings = repository().settings.first()
        assertEquals(442f, settings.a4Hz)
        assertEquals(TunerMode.CHROMATIC, settings.mode)
        assertEquals(Tunings.DROP_D, settings.tuning)
        assertEquals(Notation.FLATS, settings.notationChoice)
        assertEquals("a picked notation wins over the language", Notation.FLATS, settings.notationFor("fr"))
        assertEquals(VisualStyle.ANCIENT, settings.style)
        assertEquals(InTunePrecision.PRECISE, settings.inTunePrecision)
        assertEquals("the two-pane landscape choice is kept", true, settings.keepTwoPaneLandscape)
        assertEquals("the meter look is kept", MeterLook.SPIRIT_LEVEL, settings.meterLook)
        assertEquals("the bold in-tune effect is kept", InTuneEffect.BOLD, settings.inTuneEffect)
        assertEquals("the metronome's tempo is kept", 72, settings.metronomeBpm)
        assertEquals("the metronome's meter is kept", Meter.SIX_EIGHT, settings.metronomeMeter)
        assertEquals("the metronome's vibration is kept", true, settings.metronomeVibrate)
        assertEquals("the Practice tool used last is kept", PracticeTool.CHORDS, settings.practiceTool)
        assertEquals("the chord picked last is kept", PitchClass.F_SHARP to ChordQuality.SUS4, settings.practiceRoot to settings.practiceChordQuality)
    }

    @Test
    fun `the metronome's tempo is kept within 30 to 250 BPM, saved or read`() = runTest {
        val repo = repository()
        repo.setMetronomeBpm(10)
        assertEquals(30, repo.settings.first().metronomeBpm)
        repo.setMetronomeBpm(400)
        assertEquals(250, repo.settings.first().metronomeBpm)
        close()

        val store = dataStore()
        store.edit { it[intPreferencesKey("metronome_bpm")] = 9999 }
        assertEquals("a saved tempo out of range is clamped", 250, repository(store).settings.first().metronomeBpm)
    }

    @Test
    fun `every meter is saved under metronome_meter by its name`() = runTest {
        val store = dataStore()
        val repo = repository(store)
        for (meter in Meter.entries) {
            repo.setMetronomeMeter(meter)
            assertEquals(meter.name, store.data.first()[stringPreferencesKey("metronome_meter")])
            assertEquals(meter, repo.settings.first().metronomeMeter)
        }
    }

    @Test
    fun `picking an instrument picks its standard tuning, and both survive a restart`() = runTest {
        repository().setInstrument(Instruments.BASS_5)
        close()

        val settings = repository().settings.first()
        assertEquals(Instruments.BASS_5, settings.instrument)
        assertEquals(Instruments.BASS_5.defaultTuning, settings.tuning)
    }

    @Test
    fun `picking the instrument already picked keeps its tuning`() = runTest {
        val repo = repository()
        repo.setTuning(Instruments.UKULELE.tuningById("ukulele_low_g")!!)
        repo.setInstrument(Instruments.UKULELE)

        assertEquals("ukulele_low_g", repo.settings.first().tuning.id)
    }

    @Test
    fun `picking a tuning picks its instrument too`() = runTest {
        val repo = repository()
        repo.setInstrument(Instruments.CELLO)
        repo.setTuning(Tunings.DROP_D)

        val settings = repo.settings.first()
        assertEquals(Instruments.GUITAR, settings.instrument)
        assertEquals(Tunings.DROP_D, settings.tuning)
    }

    @Test
    fun `a tuning saved before the instrument picker opens on the guitar`() = runTest {
        dataStore().edit { it[stringPreferencesKey("tuning_id")] = Tunings.OPEN_G.id }
        close()

        val settings = repository().settings.first()
        assertEquals(Instruments.GUITAR, settings.instrument)
        assertEquals(Tunings.OPEN_G, settings.tuning)
    }

    @Test
    fun `a saved tuning that isn't the saved instrument's gives the instrument's standard tuning`() = runTest {
        dataStore().edit {
            it[stringPreferencesKey("instrument_id")] = Instruments.VIOLIN.id
            it[stringPreferencesKey("tuning_id")] = Tunings.DROP_D.id
        }
        close()

        val settings = repository().settings.first()
        assertEquals(Instruments.VIOLIN, settings.instrument)
        assertEquals(Instruments.VIOLIN.defaultTuning, settings.tuning)
    }

    @Test
    fun `an unknown saved instrument goes with its saved tuning`() = runTest {
        dataStore().edit {
            it[stringPreferencesKey("instrument_id")] = "banjo"
            it[stringPreferencesKey("tuning_id")] = "bass_4_standard"
        }
        close()

        val settings = repository().settings.first()
        assertEquals(Instruments.BASS_4, settings.instrument)
        assertEquals("bass_4_standard", settings.tuning.id)
    }

    @Test
    fun `a known instrument with an unknown tuning gets its standard tuning`() = runTest {
        dataStore().edit {
            it[stringPreferencesKey("instrument_id")] = Instruments.MANDOLIN.id
            it[stringPreferencesKey("tuning_id")] = "removed_in_v2"
        }
        close()

        val settings = repository().settings.first()
        assertEquals(Instruments.MANDOLIN, settings.instrument)
        assertEquals(Instruments.MANDOLIN.defaultTuning, settings.tuning)
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
            it[stringPreferencesKey("instrument_id")] = "theremin"
            it[stringPreferencesKey("notation")] = "SHAPE_NOTES"
            it[stringPreferencesKey("in_tune_precision")] = "PERFECT"
            it[stringPreferencesKey("theme")] = "SEPIA"
            it[stringPreferencesKey("visual_style")] = "BAROQUE"
            it[stringPreferencesKey("wave_look")] = "LAVA_LAMP"
            it[stringPreferencesKey("meter_look")] = "SUNDIAL"
            it[stringPreferencesKey("in_tune_effect")] = "FIREWORKS"
            it[stringPreferencesKey("tuner_display")] = "HOLOGRAM"
            it[floatPreferencesKey("a4_hz")] = 1000f
            it[stringPreferencesKey("metronome_meter")] = "THIRTEEN_EIGHT"
        }

        val settings = repository(store).settings.first()
        assertEquals(TunerMode.AUTO, settings.mode)
        assertEquals(Instruments.DEFAULT, settings.instrument)
        assertEquals(Tunings.DEFAULT, settings.tuning)
        assertEquals("an unknown notation counts as never picked", null, settings.notationChoice)
        assertEquals(InTunePrecision.DEFAULT, settings.inTunePrecision)
        assertEquals(ThemeMode.SYSTEM, settings.theme)
        assertEquals(VisualStyle.DEFAULT, settings.style)
        assertEquals(WaveLook.DEFAULT, settings.waveLook)
        assertEquals("an unknown meter look gives Classic", MeterLook.CLASSIC, settings.meterLook)
        assertEquals("an unknown in-tune effect gives Calm", InTuneEffect.CALM, settings.inTuneEffect)
        assertEquals(TunerDisplay.DEFAULT, settings.display)
        assertEquals(466f, settings.a4Hz)
        assertEquals("an unknown meter gives 4/4", Meter.DEFAULT, settings.metronomeMeter)
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
    fun `every style saved by an older version opens as its closest new style`() = runTest {
        // The seven styles before October 2026, and the style each one opens as now.
        val expected = mapOf(
            "PREHISTORIC" to VisualStyle.ANCIENT,
            "MIDDLE_AGES" to VisualStyle.ANCIENT,
            "CLASSIC" to VisualStyle.CLASSIC,
            "RETRO" to VisualStyle.RETRO,
            "CONTEMPORARY" to VisualStyle.CONTEMPORARY,
            "FUTURISTIC" to VisualStyle.FUTURISTIC,
            "SPACE_AGE" to VisualStyle.SPACE_AGE,
        )
        val store = dataStore()
        val repo = repository(store)
        for ((saved, style) in expected) {
            store.edit { it[stringPreferencesKey("visual_style")] = saved }
            assertEquals(saved, style, repo.settings.first().style)
        }
    }

    @Test
    fun `an old style survives a restart and picking a style rewrites it`() = runTest {
        dataStore().edit { it[stringPreferencesKey("visual_style")] = "MIDDLE_AGES" }
        close()
        assertEquals(VisualStyle.ANCIENT, repository().settings.first().style)
        close()

        val store = dataStore()
        repository(store).setStyle(VisualStyle.ANCIENT)
        assertEquals("ANCIENT", store.data.first()[stringPreferencesKey("visual_style")])
    }

    @Test
    fun `an unknown or damaged saved style opens as the default`() = runTest {
        val store = dataStore()
        val repo = repository(store)
        for (saved in listOf("", "prehistoric", "Middle Ages", "STONE_AGE", "ANCIENT ")) {
            store.edit { it[stringPreferencesKey("visual_style")] = saved }
            assertEquals(saved, VisualStyle.CONTEMPORARY, repo.settings.first().style)
        }
    }

    @Test
    fun `every style is saved by its name`() = runTest {
        val store = dataStore()
        val repo = repository(store)
        for (style in VisualStyle.entries) {
            repo.setStyle(style)
            assertEquals(style, repo.settings.first().style)
            assertEquals(style.name, store.data.first()[stringPreferencesKey("visual_style")])
        }
    }

    @Test
    fun `the six styles come in the owner's order, Contemporary the default`() {
        assertEquals(
            listOf(
                VisualStyle.ANCIENT,
                VisualStyle.CLASSIC,
                VisualStyle.RETRO,
                VisualStyle.CONTEMPORARY,
                VisualStyle.FUTURISTIC,
                VisualStyle.SPACE_AGE,
            ),
            VisualStyle.entries,
        )
        assertEquals(VisualStyle.CONTEMPORARY, VisualStyle.DEFAULT)
    }

    @Test
    fun `an unreadable file gives the defaults instead of crashing`() = runTest {
        file.writeText("this is not a preferences file")

        assertEquals(AppSettings(), repository().settings.first())
    }

    /** [real] for saving, while each read runs [read] with its number (from 0), to make reads fail. */
    private class FlakyDataStore(
        private val real: DataStore<Preferences>,
        private val read: suspend FlowCollector<Preferences>.(n: Int) -> Unit,
    ) : DataStore<Preferences> {
        private var reads = 0

        /** How many writes went through. */
        var writes = 0
            private set

        override val data: Flow<Preferences> = flow { read(reads++) }
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            writes++
            return real.updateData(transform)
        }
    }

    @Test
    fun `a custom tuning saved and picked is one write, so nothing can stop it half way`() = runTest {
        val real = dataStore()
        val counted = FlakyDataStore(real) { emitAll(real.data) }
        val repo = repository(counted)
        val fifths = repo.addCustomTuning("Fifths", Instruments.BASS_4, fifthsBass, pick = true)
        assertEquals(1, counted.writes)
        assertEquals(fifths.tuning, repo.settings.first().tuning)
        assertEquals(Instruments.BASS_4, repo.settings.first().instrument)

        repo.setInstrument(Instruments.GUITAR)
        val changed = repo.updateCustomTuning(fifths.id, "Fourths", fifthsBass, pick = true)!!
        assertEquals(3, counted.writes)
        assertEquals(changed.tuning, repo.settings.first().tuning)
        assertEquals(Instruments.BASS_4, repo.settings.first().instrument)
        assertEquals("Fourths", repo.settings.first().customTunings.single().name)

        assertNull("an unknown id picks nothing", repo.updateCustomTuning("custom_gone", "X", fifthsBass, pick = true))
        assertEquals(changed.tuning, repo.settings.first().tuning)
    }

    @Test
    fun `a failed read shows the defaults, then the saved settings once a read works`() = runTest {
        val real = dataStore()
        repository(real).setA4(442f)
        val flaky = FlakyDataStore(real) { n -> if (n == 0) throw IOException("read failed") else emitAll(real.data) }

        val seen = repository(flaky).settings.take(2).toList()

        assertEquals(listOf(440f, 442f), seen.map { it.a4Hz })
    }

    @Test
    fun `a read failing after the settings were shown never shows the defaults, and later changes still arrive`() = runTest {
        val real = dataStore()
        repository(real).setA4(442f)
        val flaky = FlakyDataStore(real) { n ->
            if (n == 0) {
                emit(real.data.first())
                // Saved while reading was broken: the next read must bring it.
                real.edit { it[floatPreferencesKey("a4_hz")] = 445f }
                throw IOException("read failed")
            }
            emitAll(real.data)
        }

        val seen = repository(flaky).settings.take(2).toList()

        assertEquals(listOf(442f, 445f), seen.map { it.a4Hz })
    }

    @Test
    fun `a failed read is tried again after half a second, then less and less often`() {
        assertEquals(listOf(500L, 1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), (0L..7L).map(DataStoreSettingsRepository::readRetryMillis))
        assertEquals(30_000L, DataStoreSettingsRepository.readRetryMillis(Long.MAX_VALUE))
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
                MeterLook.ECLIPSE,
                MeterLook.ARROWS,
                MeterLook.LIGHT_BARS,
            ),
            MeterLook.entries,
        )
        assertEquals(MeterLook.CLASSIC, MeterLook.DEFAULT)
    }

    @Test
    fun `a meter look that went in Phase 25 reads as Classic`() = runTest {
        val store = dataStore()
        val repo = repository(store)
        for (gone in listOf("ROLLING_SCALE", "COMET", "STUDIO_METER")) {
            store.edit { it[stringPreferencesKey("meter_look")] = gone }
            assertEquals(gone, MeterLook.CLASSIC, repo.settings.first().meterLook)
        }
    }

    @Test
    fun `the old when-in-tune choice is ignored, whichever way it was saved`() = runTest {
        val store = dataStore()
        val repo = repository(store)
        for (breathes in listOf(true, false)) {
            store.edit {
                it[booleanPreferencesKey("wave_breathes_in_tune")] = breathes
                it[booleanPreferencesKey("tour_seen")] = true
            }
            assertEquals("saved $breathes", AppSettings(tourSeen = true), repo.settings.first())
        }
    }

    // --- Custom tunings ---

    private val dropC = listOf(36, 43, 48, 53, 57, 62).map(::Note)
    private val openC = listOf(36, 43, 48, 55, 60, 64).map(::Note)
    private val fifthsBass = listOf(24, 31, 38, 45).map(::Note)
    private val customKey = stringPreferencesKey("custom_tunings")

    @Test
    fun `a new custom tuning is saved after the others, not picked, and survives a restart`() = runTest {
        val repo = repository()
        val first = repo.addCustomTuning("  Drop   C ", Instruments.GUITAR, dropC)
        val second = repo.addCustomTuning("Fifths", Instruments.BASS_4, fifthsBass)

        assertEquals("the name is normalized", "Drop C", first.name)
        assertTrue(first.id.startsWith(CustomTuning.ID_PREFIX))
        assertTrue("every tuning gets its own id", first.id != second.id)
        assertEquals(Tunings.STANDARD, repo.settings.first().tuning)
        close()

        val settings = repository().settings.first()
        assertEquals(listOf(first, second), settings.customTunings)
        assertEquals(listOf(second), settings.customTuningsFor(Instruments.BASS_4))
        assertNull(settings.pickedCustomTuning)
    }

    @Test
    fun `a custom tuning without a name or with the wrong number of notes is refused and nothing is saved`() = runTest {
        val repo = repository()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.addCustomTuning(" \n ", Instruments.GUITAR, dropC) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.addCustomTuning("Drop C", Instruments.BASS_4, dropC) }
        }
        assertEquals(emptyList<CustomTuning>(), repo.settings.first().customTunings)
    }

    @Test
    fun `picking a custom tuning picks its instrument too, and both survive a restart`() = runTest {
        val repo = repository()
        val fifths = repo.addCustomTuning("Fifths", Instruments.BASS_4, fifthsBass)
        repo.setTuning(fifths.tuning)
        close()

        val settings = repository().settings.first()
        assertEquals(Instruments.BASS_4, settings.instrument)
        assertEquals(fifths.tuning, settings.tuning)
        assertEquals(fifths, settings.pickedCustomTuning)
    }

    @Test
    fun `picking the instrument of the picked custom tuning keeps it`() = runTest {
        val repo = repository()
        val drop = repo.addCustomTuning("Drop C", Instruments.GUITAR, dropC)
        repo.setTuning(drop.tuning)
        repo.setInstrument(Instruments.GUITAR)
        assertEquals(drop.tuning, repo.settings.first().tuning)

        repo.setInstrument(Instruments.UKULELE)
        assertEquals(Instruments.UKULELE.defaultTuning, repo.settings.first().tuning)
    }

    @Test
    fun `editing a custom tuning keeps its id and place, and it stays picked with the new notes`() = runTest {
        val repo = repository()
        val drop = repo.addCustomTuning("Drop C", Instruments.GUITAR, dropC)
        val fifths = repo.addCustomTuning("Fifths", Instruments.BASS_4, fifthsBass)
        repo.setTuning(drop.tuning)

        val changed = repo.updateCustomTuning(drop.id, " Open  C", openC)
        assertEquals(CustomTuning(drop.id, "Open C", Instruments.GUITAR, openC), changed)
        assertNull("an unknown id changes nothing", repo.updateCustomTuning("custom_gone", "X", openC))
        close()

        val settings = repository().settings.first()
        assertEquals(listOf(changed, fifths), settings.customTunings)
        assertEquals(Instruments.GUITAR, settings.instrument)
        assertEquals(openC, settings.tuning.strings)
        assertEquals(changed, settings.pickedCustomTuning)
    }

    @Test
    fun `an edit that doesn't fit its instrument is refused and the tuning is kept`() = runTest {
        val repo = repository()
        val drop = repo.addCustomTuning("Drop C", Instruments.GUITAR, dropC)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.updateCustomTuning(drop.id, "Bass", fifthsBass) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.updateCustomTuning(drop.id, "", dropC) }
        }
        assertEquals(listOf(drop), repo.settings.first().customTunings)
    }

    @Test
    fun `deleting the picked custom tuning picks its instrument's standard tuning`() = runTest {
        val repo = repository()
        val fifths = repo.addCustomTuning("Fifths", Instruments.BASS_4, fifthsBass)
        val drop = repo.addCustomTuning("Drop C", Instruments.GUITAR, dropC)
        repo.setTuning(fifths.tuning)

        repo.deleteCustomTuning(fifths.id)
        close()

        val settings = repository().settings.first()
        assertEquals(listOf(drop), settings.customTunings)
        assertEquals(Instruments.BASS_4, settings.instrument)
        assertEquals(Instruments.BASS_4.defaultTuning, settings.tuning)
    }

    @Test
    fun `deleting another custom tuning keeps the picked one, and an unknown id changes nothing`() = runTest {
        val store = dataStore()
        val repo = repository(store)
        val drop = repo.addCustomTuning("Drop C", Instruments.GUITAR, dropC)
        val open = repo.addCustomTuning("Open C", Instruments.GUITAR, openC)
        repo.setTuning(open.tuning)

        repo.deleteCustomTuning(drop.id)
        repo.deleteCustomTuning("custom_gone")
        assertEquals(listOf(open), repo.settings.first().customTunings)
        assertEquals(open.tuning, repo.settings.first().tuning)

        repo.deleteCustomTuning(open.id)
        assertNull("nothing is left behind", store.data.first()[customKey])
        assertEquals(Tunings.STANDARD, repo.settings.first().tuning)
    }

    @Test
    fun `damaged custom tunings fall back to the presets`() = runTest {
        val store = dataStore()
        store.edit {
            it[customKey] = "custom_1|guitar|36,43,48|Too+few\n\u0000garbage"
            it[stringPreferencesKey("instrument_id")] = Instruments.GUITAR.id
            it[stringPreferencesKey("tuning_id")] = "custom_1"
        }

        val settings = repository(store).settings.first()
        assertEquals(emptyList<CustomTuning>(), settings.customTunings)
        assertEquals(Instruments.GUITAR, settings.instrument)
        assertEquals(Tunings.STANDARD, settings.tuning)
    }

    @Test
    fun `a damaged custom tuning costs only itself`() = runTest {
        val store = dataStore()
        store.edit {
            it[customKey] = "custom_1|guitar|36,43,48,53,57,62|Drop+C\ncustom_2|banjo|1,2,3,4,5|Banjo"
            it[stringPreferencesKey("tuning_id")] = "custom_2"
        }

        val settings = repository(store).settings.first()
        assertEquals(listOf(CustomTuning("custom_1", "Drop C", Instruments.GUITAR, dropC)), settings.customTunings)
        assertEquals("the lost tuning gives the guitar's standard", Tunings.STANDARD, settings.tuning)
    }

    @Test
    fun `a saved custom tuning of another instrument gives the instrument's standard tuning`() = runTest {
        val store = dataStore()
        val repo = repository(store)
        val drop = repo.addCustomTuning("Drop C", Instruments.GUITAR, dropC)
        store.edit {
            it[stringPreferencesKey("instrument_id")] = Instruments.CELLO.id
            it[stringPreferencesKey("tuning_id")] = drop.id
        }

        val settings = repo.settings.first()
        assertEquals(Instruments.CELLO, settings.instrument)
        assertEquals(Instruments.CELLO.defaultTuning, settings.tuning)
    }

    @Test
    fun `a custom tuning saved without its instrument opens on its own instrument`() = runTest {
        val store = dataStore()
        val repo = repository(store)
        val fifths = repo.addCustomTuning("Fifths", Instruments.BASS_4, fifthsBass)
        store.edit { it[stringPreferencesKey("tuning_id")] = fifths.id }

        val settings = repo.settings.first()
        assertEquals(Instruments.BASS_4, settings.instrument)
        assertEquals(fifths.tuning, settings.tuning)
    }

    @Test
    fun `a fresh install is due the tour until it is skipped or finished, also after a restart`() = runTest {
        val repo = repository()
        repo.decideTourOnce()
        assertEquals("fresh: the tour shows", false, repo.settings.first().tourSeen)
        repo.setTourSeen(true)
        close()

        assertEquals("skipped or finished: never again by itself", true, repository().settings.first().tourSeen)
    }

    @Test
    fun `settings saved before the tour is decided make an update, which never shows the tour`() = runTest {
        // An install from before the tour that only dismissed the string-lock tip (a key no longer
        // read): it saved other settings, never the tour.
        val store = dataStore()
        store.edit { it[booleanPreferencesKey("string_lock_tip_seen")] = true }
        val repo = repository(store)
        assertEquals("read as seen before anything is decided", true, repo.settings.first().tourSeen)

        repo.decideTourOnce()
        close()
        assertEquals("and saved as seen", true, repository().settings.first().tourSeen)
    }

    @Test
    fun `any saved setting at all counts as an update`() = runTest {
        val store = dataStore()
        store.edit { it[stringPreferencesKey("visual_style")] = VisualStyle.RETRO.name }
        val repo = repository(store)
        repo.decideTourOnce()
        assertEquals(true, repo.settings.first().tourSeen)
    }

    @Test
    fun `once a fresh install is decided, saving other settings doesn't cancel the tour`() = runTest {
        val repo = repository()
        repo.decideTourOnce()
        // E.g. the microphone was refused at first and the style picked in Settings first.
        repo.setStyle(VisualStyle.FUTURISTIC)
        repo.setKeepTwoPaneLandscape(true)
        repo.decideTourOnce()
        close()

        assertEquals("still due after a restart", false, repository().settings.first().tourSeen)
    }

    @Test
    fun `deciding again never brings a seen tour back`() = runTest {
        val repo = repository()
        repo.decideTourOnce()
        repo.setTourSeen(true)
        repo.decideTourOnce()
        assertEquals(true, repo.settings.first().tourSeen)
    }

    @Test
    fun `a refused microphone is remembered across restarts, and doesn't cancel a fresh install's tour`() = runTest {
        val repo = repository()
        repo.decideTourOnce()
        assertEquals("not refused on a fresh install", false, repo.settings.first().micRefused)
        repo.setMicRefused(true)
        close()

        val reopened = repository().settings.first()
        assertEquals(true, reopened.micRefused)
        assertEquals("the tour is still due", false, reopened.tourSeen)
    }
}
