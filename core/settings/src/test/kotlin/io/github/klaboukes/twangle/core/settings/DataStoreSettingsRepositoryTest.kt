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
import io.github.klaboukes.twangle.core.dsp.capo.SongKey
import io.github.klaboukes.twangle.core.dsp.chord.ChordQuality
import io.github.klaboukes.twangle.core.dsp.drone.DroneSound
import io.github.klaboukes.twangle.core.dsp.drone.DroneVoicing
import io.github.klaboukes.twangle.core.dsp.eartraining.ChordTypeQuestions
import io.github.klaboukes.twangle.core.dsp.eartraining.Exercise
import io.github.klaboukes.twangle.core.dsp.eartraining.Interval
import io.github.klaboukes.twangle.core.dsp.eartraining.IntervalDirection
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteChoice
import io.github.klaboukes.twangle.core.dsp.eartraining.NoteMatch
import io.github.klaboukes.twangle.core.dsp.metronome.ClickSound
import io.github.klaboukes.twangle.core.dsp.metronome.Meter
import io.github.klaboukes.twangle.core.dsp.metronome.SpeedTrainer
import io.github.klaboukes.twangle.core.dsp.metronome.Subdivision
import io.github.klaboukes.twangle.core.dsp.music.Instruments
import io.github.klaboukes.twangle.core.dsp.music.Notation
import io.github.klaboukes.twangle.core.dsp.music.Note
import io.github.klaboukes.twangle.core.dsp.music.PitchClass
import io.github.klaboukes.twangle.core.dsp.music.Tunings
import io.github.klaboukes.twangle.core.dsp.scale.ScaleType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals("Ink, not the wallpaper's colours (2026-11-03)", false, settings.dynamicColor)
        assertEquals(true, settings.keepScreenOn)
        assertEquals(true, settings.vibrateInTune)
        assertEquals("the vibration guide was never set: it follows the screen reader", null, settings.vibrationGuide)
        assertEquals("the string tick-off is off on a fresh install", false, settings.stringTickOff)
        assertEquals("the word under the meter on a fresh install", UnderMeter.WORDS, settings.underMeter)
        assertEquals("a fresh install's meter shows the Classic needle", LensLook.CLASSIC, settings.lensLook)
        assertEquals("the metronome starts at 120 BPM", 120, settings.metronomeBpm)
        assertEquals("the metronome starts in 4/4", Meter.FOUR_FOUR, settings.metronomeMeter)
        assertEquals("the metronome doesn't vibrate until asked", false, settings.metronomeVibrate)
        assertEquals("the metronome clicks with the wood block", ClickSound.WOOD_BLOCK, settings.metronomeClickSound)
        assertEquals("the metronome clicks only on the beats", Subdivision.NONE, settings.metronomeSubdivision)
        assertEquals("the speed trainer was never set up", null, settings.metronomeTrainer)
        assertEquals("the speed trainer is off", false, settings.metronomeTrainerOn)
        assertEquals("no Practice tool used yet", null, settings.practiceTool)
        assertEquals("the chord charts open on C major", PitchClass.C to ChordQuality.MAJOR, settings.practiceRoot to settings.practiceChordQuality)
        assertEquals("the scales open on major", ScaleType.MAJOR, settings.practiceScaleType)
        assertEquals("the capo helper opens on C shapes", SongKey(PitchClass.C), settings.practiceCapoShapes)
        assertEquals("the capo helper opens with no capo", 0, settings.practiceCapoFret)
        assertEquals("the drone is the tanpura with the fifth", DroneSound.TANPURA to DroneVoicing.FIFTH, settings.droneSound to settings.droneVoicing)
        assertEquals("the drone isn't lower until asked", false, settings.droneLower)
        assertEquals("the charts are right-handed until asked", false, settings.leftHanded)
        assertEquals("ear training opens on Play it back", Exercise.PLAY_IT_BACK, settings.earExercise)
        assertEquals("asking the open strings, one at a time", NoteChoice.OPEN_STRINGS to 1, settings.earNoteChoice to settings.earNotesInRow)
        assertEquals("any octave counts", NoteMatch.ANY_OCTAVE, settings.earNoteMatch)
        assertEquals("the thirds, fourth, fifth, major sixth and octave", Interval.DEFAULT, settings.earIntervals)
        assertEquals("played up", setOf(IntervalDirection.UP), settings.earDirections)
        assertEquals("major, minor and 7", setOf(ChordQuality.MAJOR, ChordQuality.MINOR, ChordQuality.DOMINANT_SEVENTH), settings.earChordTypes)
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
        repo.setDynamicColor(false)
        repo.setKeepScreenOn(false)
        repo.setVibrateInTune(false)
        repo.setVibrationGuide(true)
        repo.setStringTickOff(true)
        repo.setUnderMeter(UnderMeter.CENTS)
        repo.setLensLook(LensLook.STROBE)
        repo.setMetronomeBpm(96)
        repo.setMetronomeMeter(Meter.SEVEN_EIGHT)
        repo.setMetronomeVibrate(true)
        repo.setMetronomeClickSound(ClickSound.CLAVES)
        repo.setMetronomeSubdivision(Subdivision.THREE)
        repo.setMetronomeTrainer(SpeedTrainer(80, 120, 2, 8))
        repo.setMetronomeTrainerOn(true)
        repo.setPracticeTool(PracticeTool.SCALES)
        repo.setPracticeRoot(PitchClass.A_SHARP)
        repo.setPracticeChordQuality(ChordQuality.MINOR_SEVENTH)
        repo.setPracticeScaleType(ScaleType.BLUES)
        repo.setPracticeCapoShapes(SongKey(PitchClass.A, minor = true))
        repo.setPracticeCapoFret(3)
        repo.setDroneSound(DroneSound.WARM_PAD)
        repo.setDroneVoicing(DroneVoicing.FIFTH_OCTAVE)
        repo.setDroneLower(true)
        repo.setLeftHanded(true)
        repo.setEarExercise(Exercise.INTERVALS)
        repo.setEarNoteChoice(NoteChoice.ALL_TO_12TH)
        repo.setEarNotesInRow(3)
        repo.setEarNoteMatch(NoteMatch.EXACT)
        repo.setEarIntervals(setOf(Interval.PERFECT_FOURTH, Interval.PERFECT_FIFTH))
        repo.setEarDirections(setOf(IntervalDirection.DOWN, IntervalDirection.TOGETHER))
        repo.setEarChordTypes(setOf(ChordQuality.SUS2, ChordQuality.SUS4))

        val expected = AppSettings(
            a4Hz = 432f,
            mode = TunerMode.CHROMATIC,
            tuning = Tunings.DADGAD,
            notationChoice = Notation.SOLFEGE,
            inTunePrecision = InTunePrecision.PRECISE,
            theme = ThemeMode.DARK,
            dynamicColor = false,
            keepScreenOn = false,
            vibrateInTune = false,
            vibrationGuide = true,
            stringTickOff = true,
            underMeter = UnderMeter.CENTS,
            lensLook = LensLook.STROBE,
            metronomeBpm = 96,
            metronomeMeter = Meter.SEVEN_EIGHT,
            metronomeVibrate = true,
            metronomeClickSound = ClickSound.CLAVES,
            metronomeSubdivision = Subdivision.THREE,
            metronomeTrainer = SpeedTrainer(80, 120, 2, 8),
            metronomeTrainerOn = true,
            practiceTool = PracticeTool.SCALES,
            practiceRoot = PitchClass.A_SHARP,
            practiceChordQuality = ChordQuality.MINOR_SEVENTH,
            practiceScaleType = ScaleType.BLUES,
            practiceCapoShapes = SongKey(PitchClass.A, minor = true),
            practiceCapoFret = 3,
            droneSound = DroneSound.WARM_PAD,
            droneVoicing = DroneVoicing.FIFTH_OCTAVE,
            droneLower = true,
            leftHanded = true,
            earExercise = Exercise.INTERVALS,
            earNoteChoice = NoteChoice.ALL_TO_12TH,
            earNotesInRow = 3,
            earNoteMatch = NoteMatch.EXACT,
            earIntervals = setOf(Interval.PERFECT_FOURTH, Interval.PERFECT_FIFTH),
            earDirections = setOf(IntervalDirection.DOWN, IntervalDirection.TOGETHER),
            earChordTypes = setOf(ChordQuality.SUS2, ChordQuality.SUS4),
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
    fun `the vibration guide follows the screen reader until the player sets it`() = runTest {
        val repo = repository()
        val fresh = repo.settings.first()
        assertTrue("on while a screen reader is", fresh.vibrationGuideOn(screenReaderOn = true))
        assertFalse("off without one", fresh.vibrationGuideOn(screenReaderOn = false))

        repo.setVibrationGuide(false)
        val off = repo.settings.first()
        assertFalse("turned off, it stays off with TalkBack on", off.vibrationGuideOn(screenReaderOn = true))

        repo.setVibrationGuide(true)
        val on = repo.settings.first()
        assertTrue("turned on, it stays on without TalkBack", on.vibrationGuideOn(screenReaderOn = false))
        close()
        assertEquals("the choice survives a restart", true, repository().settings.first().vibrationGuide)
    }

    @Test
    fun `high contrast follows the phone until the player sets it`() = runTest {
        val repo = repository()
        val fresh = repo.settings.first()
        assertTrue("on while the phone's contrast is high", fresh.highContrastOn(phoneHighContrast = true))
        assertFalse("off otherwise", fresh.highContrastOn(phoneHighContrast = false))

        repo.setHighContrast(false)
        assertFalse("turned off, it stays off", repo.settings.first().highContrastOn(phoneHighContrast = true))

        repo.setHighContrast(true)
        assertTrue("turned on, it stays on", repo.settings.first().highContrastOn(phoneHighContrast = false))
        close()
        assertEquals("the choice survives a restart", true, repository().settings.first().highContrast)
        close()
        val again = repository()
        again.setHighContrast(null)
        assertEquals("forgotten, it follows the phone again", null, again.settings.first().highContrast)
    }

    @Test
    fun `settings survive an app restart`() = runTest {
        repository().apply {
            setA4(442f)
            setMode(TunerMode.CHROMATIC)
            setTuning(Tunings.DROP_D)
            setNotation(Notation.FLATS)
            setInTunePrecision(InTunePrecision.PRECISE)
            setLensLook(LensLook.LIGHT_ARC)
            setMetronomeBpm(72)
            setMetronomeMeter(Meter.SIX_EIGHT)
            setMetronomeVibrate(true)
            setMetronomeClickSound(ClickSound.COWBELL)
            setMetronomeSubdivision(Subdivision.FOUR)
            setPracticeTool(PracticeTool.CHORDS)
            setPracticeRoot(PitchClass.F_SHARP)
            setPracticeChordQuality(ChordQuality.SUS4)
            setPracticeScaleType(ScaleType.MINOR_PENTATONIC)
            setPracticeTool(PracticeTool.CAPO)
            setPracticeCapoShapes(SongKey(PitchClass.G))
            setPracticeCapoFret(5)
            setDroneSound(DroneSound.HARMONIUM)
            setDroneVoicing(DroneVoicing.ROOT)
            setDroneLower(true)
            setLeftHanded(true)
            setEarExercise(Exercise.CHORD_TYPE)
            setEarNoteChoice(NoteChoice.NATURALS_TO_5TH)
            setEarNotesInRow(4)
            setEarNoteMatch(NoteMatch.EXACT)
            setEarIntervals(setOf(Interval.OCTAVE, Interval.MINOR_SECOND))
            setEarDirections(setOf(IntervalDirection.TOGETHER))
            setEarChordTypes(setOf(ChordQuality.AUGMENTED, ChordQuality.MINOR))
        }
        close()

        val settings = repository().settings.first()
        assertEquals(442f, settings.a4Hz)
        assertEquals(TunerMode.CHROMATIC, settings.mode)
        assertEquals(Tunings.DROP_D, settings.tuning)
        assertEquals(Notation.FLATS, settings.notationChoice)
        assertEquals("a picked notation wins over the language", Notation.FLATS, settings.notationFor("fr"))
        assertEquals(InTunePrecision.PRECISE, settings.inTunePrecision)
        assertEquals("the lens look is kept", LensLook.LIGHT_ARC, settings.lensLook)
        assertEquals("the metronome's tempo is kept", 72, settings.metronomeBpm)
        assertEquals("the metronome's meter is kept", Meter.SIX_EIGHT, settings.metronomeMeter)
        assertEquals("the metronome's vibration is kept", true, settings.metronomeVibrate)
        assertEquals("the metronome's click is kept", ClickSound.COWBELL, settings.metronomeClickSound)
        assertEquals("the clicks between the beats are kept", Subdivision.FOUR, settings.metronomeSubdivision)
        assertEquals("the Practice tool used last is kept", PracticeTool.CAPO, settings.practiceTool)
        assertEquals("the chord picked last is kept", PitchClass.F_SHARP to ChordQuality.SUS4, settings.practiceRoot to settings.practiceChordQuality)
        assertEquals("the scale picked last is kept", ScaleType.MINOR_PENTATONIC, settings.practiceScaleType)
        assertEquals("the capo's shapes are kept", SongKey(PitchClass.G), settings.practiceCapoShapes)
        assertEquals("the capo's fret is kept", 5, settings.practiceCapoFret)
        assertEquals("the drone's sound and notes are kept", DroneSound.HARMONIUM to DroneVoicing.ROOT, settings.droneSound to settings.droneVoicing)
        assertEquals("the drone's Lower is kept", true, settings.droneLower)
        assertEquals("left-handed is kept", true, settings.leftHanded)
        assertEquals("the ear training exercise used last is kept", Exercise.CHORD_TYPE, settings.earExercise)
        assertEquals("what Play it back asks is kept", NoteChoice.NATURALS_TO_5TH to 4, settings.earNoteChoice to settings.earNotesInRow)
        assertEquals("only the exact note, as chosen", NoteMatch.EXACT, settings.earNoteMatch)
        assertEquals("the intervals asked are kept, smallest first", listOf(Interval.MINOR_SECOND, Interval.OCTAVE), settings.earIntervals.toList())
        assertEquals("together, as chosen", setOf(IntervalDirection.TOGETHER), settings.earDirections)
        assertEquals("the chord types asked are kept", setOf(ChordQuality.MINOR, ChordQuality.AUGMENTED), settings.earChordTypes)
    }

    @Test
    fun `Intervals never asks nothing`() = runTest {
        val repo = repository()
        repo.setEarIntervals(emptySet())
        repo.setEarDirections(emptySet())
        repo.setEarChordTypes(emptySet())
        assertEquals(Interval.DEFAULT, repo.settings.first().earIntervals)
        assertEquals(IntervalDirection.DEFAULT, repo.settings.first().earDirections)
        assertEquals(ChordTypeQuestions.DEFAULT, repo.settings.first().earChordTypes)
        val fake = FakeSettingsRepository()
        fake.setEarIntervals(emptySet())
        assertEquals("the fake keeps them so too", Interval.DEFAULT, fake.settings.first().earIntervals)
    }

    @Test
    fun `Play it back's notes in a row are kept within 1 to 4`() = runTest {
        val repo = repository()
        repo.setEarNotesInRow(7)
        assertEquals(4, repo.settings.first().earNotesInRow)
        repo.setEarNotesInRow(0)
        assertEquals(1, repo.settings.first().earNotesInRow)
        val fake = FakeSettingsRepository()
        fake.setEarNotesInRow(9)
        assertEquals("the fake keeps them so too", 4, fake.settings.first().earNotesInRow)
    }

    @Test
    fun `the capo's fret is kept within 0 to 11`() = runTest {
        val repo = repository()
        repo.setPracticeCapoFret(14)
        assertEquals(11, repo.settings.first().practiceCapoFret)
        repo.setPracticeCapoFret(-2)
        assertEquals(0, repo.settings.first().practiceCapoFret)
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
    fun `every click is saved under metronome_click_sound by its name`() = runTest {
        val store = dataStore()
        val repo = repository(store)
        for (sound in ClickSound.entries) {
            repo.setMetronomeClickSound(sound)
            assertEquals(sound.name, store.data.first()[stringPreferencesKey("metronome_click_sound")])
            assertEquals(sound, repo.settings.first().metronomeClickSound)
        }
    }

    @Test
    fun `every subdivision is saved under metronome_subdivision by its name`() = runTest {
        val store = dataStore()
        val repo = repository(store)
        for (subdivision in Subdivision.entries.reversed()) {
            repo.setMetronomeSubdivision(subdivision)
            assertEquals(subdivision.name, store.data.first()[stringPreferencesKey("metronome_subdivision")])
            assertEquals(subdivision, repo.settings.first().metronomeSubdivision)
        }
    }

    @Test
    fun `the speed trainer is saved in four numbers and read back within its lists`() = runTest {
        val store = dataStore()
        val repo = repository(store)
        repo.setMetronomeTrainer(SpeedTrainer(95, 140, 10, 2))
        val prefs = store.data.first()
        assertEquals(
            listOf(95, 140, 10, 2),
            listOf("start", "goal", "step", "bars").map { prefs[intPreferencesKey("metronome_trainer_$it")] },
        )
        assertEquals(SpeedTrainer(95, 140, 10, 2), repo.settings.first().metronomeTrainer)
        store.edit {
            it[intPreferencesKey("metronome_trainer_step")] = 7
            it[intPreferencesKey("metronome_trainer_goal")] = 90
        }
        assertEquals("a step not on the list and a goal under the start are put right", SpeedTrainer(95, 100, 5, 2), repo.settings.first().metronomeTrainer)
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
    fun `a lowered tuning keeps its id, survives a restart and is cut to what it can go`() = runTest {
        val repo = repository()
        repo.setTuning(Tunings.DROP_D)
        repo.setLoweredBy(2)
        close()

        val restarted = repository()
        assertEquals(Tunings.DROP_D.lowered(2), restarted.settings.first().tuning)
        restarted.setLoweredBy(9)
        assertEquals(5, restarted.settings.first().tuning.loweredBy)
        restarted.setLoweredBy(0)
        assertEquals(Tunings.DROP_D, restarted.settings.first().tuning)

        restarted.setInstrument(Instruments.BASS_5)
        restarted.setLoweredBy(1)
        assertEquals("B0 can't go lower", Instruments.BASS_5.defaultTuning, restarted.settings.first().tuning)
    }

    @Test
    fun `picking another tuning or instrument starts it not lowered, the same tuning keeps it`() = runTest {
        val repo = repository()
        repo.setTuning(Tunings.OPEN_G)
        repo.setLoweredBy(1)
        repo.setTuning(repo.settings.first().tuning)
        assertEquals(Tunings.OPEN_G.lowered(1), repo.settings.first().tuning)

        repo.setTuning(Tunings.STANDARD)
        assertEquals(Tunings.STANDARD, repo.settings.first().tuning)

        repo.setLoweredBy(3)
        repo.setInstrument(Instruments.GUITAR)
        assertEquals("the same instrument keeps it", Tunings.STANDARD.lowered(3), repo.settings.first().tuning)
        repo.setInstrument(Instruments.BASS_4)
        repo.setInstrument(Instruments.GUITAR)
        assertEquals(Tunings.STANDARD, repo.settings.first().tuning)
    }

    @Test
    fun `a saved lowering out of range is cut to what the tuning can go`() = runTest {
        val store = dataStore()
        store.edit {
            it[stringPreferencesKey("tuning_id")] = "bass_4_standard"
            it[intPreferencesKey("tuning_lowered_by")] = 7
        }
        assertEquals(Instruments.BASS_4.defaultTuning.lowered(5), repository(store).settings.first().tuning)
        store.edit { it[intPreferencesKey("tuning_lowered_by")] = -2 }
        assertEquals(Instruments.BASS_4.defaultTuning, repository(store).settings.first().tuning)
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
            it[stringPreferencesKey("metronome_click_sound")] = "GONG"
            it[stringPreferencesKey("metronome_subdivision")] = "QUINTUPLETS"
            it[stringPreferencesKey("practice_tool")] = "TUNER"
            it[stringPreferencesKey("practice_scale_type")] = "HUNGARIAN_MINOR"
            it[stringPreferencesKey("practice_capo_shapes")] = "H"
            it[booleanPreferencesKey("practice_capo_minor")] = true
            it[intPreferencesKey("practice_capo_fret")] = 12
        }

        val settings = repository(store).settings.first()
        assertEquals(TunerMode.AUTO, settings.mode)
        assertEquals(Instruments.DEFAULT, settings.instrument)
        assertEquals(Tunings.DEFAULT, settings.tuning)
        assertEquals("an unknown notation counts as never picked", null, settings.notationChoice)
        assertEquals(InTunePrecision.DEFAULT, settings.inTunePrecision)
        assertEquals(ThemeMode.SYSTEM, settings.theme)
        assertEquals("an unknown meter look over an unknown wave gives the default", LensLook.DEFAULT, settings.lensLook)
        assertEquals(466f, settings.a4Hz)
        assertEquals("an unknown meter gives 4/4", Meter.DEFAULT, settings.metronomeMeter)
        assertEquals("an unknown click gives the wood block", ClickSound.WOOD_BLOCK, settings.metronomeClickSound)
        assertEquals("an unknown subdivision gives none", Subdivision.NONE, settings.metronomeSubdivision)
        assertEquals("an unknown tool counts as none used yet", null, settings.practiceTool)
        assertEquals("an unknown scale gives major", ScaleType.MAJOR, settings.practiceScaleType)
        assertEquals("unknown capo shapes give C", SongKey(PitchClass.C), settings.practiceCapoShapes)
        assertEquals("a capo fret out of range gives no capo", 0, settings.practiceCapoFret)
    }

    @Test
    fun `whoever had scales open in Chords opens on the Scales tool`() = runTest {
        val store = dataStore()
        store.edit {
            it[stringPreferencesKey("practice_tool")] = "CHORDS"
            it[stringPreferencesKey("practice_chart")] = "SCALES"
        }
        assertEquals(PracticeTool.SCALES, repository(store).settings.first().practiceTool)

        store.edit { it[stringPreferencesKey("practice_chart")] = "CHORDS" }
        assertEquals("chords open stays on Chords", PracticeTool.CHORDS, repository(store).settings.first().practiceTool)

        store.edit {
            it[stringPreferencesKey("practice_tool")] = "CAPO"
            it[stringPreferencesKey("practice_chart")] = "SCALES"
        }
        assertEquals("another tool used last stays", PracticeTool.CAPO, repository(store).settings.first().practiceTool)
    }

    @Test
    fun `a metronome saved as the Practice tool used last reads as none used yet`() = runTest {
        // 1.1 saved it; 2.0 gives the metronome a tab of its own (Phase 69), so it is no Practice tool any more.
        dataStore().edit { it[stringPreferencesKey("practice_tool")] = "METRONOME" }
        close()
        assertEquals(null, repository().settings.first().practiceTool)
        close()

        repository().setPracticeTool(PracticeTool.EAR_TRAINING)
        close()
        assertEquals("a tool picked after the update is kept", PracticeTool.EAR_TRAINING, repository().settings.first().practiceTool)
        close()

        repository().setPracticeTool(null)
        close()
        val store = dataStore()
        assertEquals("forgotten, none is used yet", null, repository(store).settings.first().practiceTool)
        assertEquals("and the key is gone", null, store.data.first()[stringPreferencesKey("practice_tool")])
    }

    @Test
    fun `the old chords or scales choice is read only until a tool is picked`() = runTest {
        dataStore().edit {
            it[stringPreferencesKey("practice_tool")] = "CHORDS"
            it[stringPreferencesKey("practice_chart")] = "SCALES"
        }
        close()
        repository().setPracticeTool(PracticeTool.CHORDS)
        close()

        val store = dataStore()
        assertEquals("Chords picked after the update stays Chords", PracticeTool.CHORDS, repository(store).settings.first().practiceTool)
        assertEquals("the old choice is cleared", null, store.data.first()[stringPreferencesKey("practice_chart")])
    }

    /** The lens look read from 1.1's keys for the meter and what showed under it (null leaves a key out). */
    private suspend fun lensLookFrom(
        meterLook: String? = null,
        display: String? = null,
        showWaves: Boolean? = null,
        waveLook: String? = null,
    ): LensLook {
        val store = dataStore()
        store.edit {
            it.clear()
            meterLook?.let { look -> it[stringPreferencesKey("meter_look")] = look }
            display?.let { shown -> it[stringPreferencesKey("tuner_display")] = shown }
            showWaves?.let { on -> it[booleanPreferencesKey("show_waves")] = on }
            waveLook?.let { look -> it[stringPreferencesKey("wave_look")] = look }
        }
        val look = repository(store).settings.first().lensLook
        close()
        return look
    }

    @Test
    fun `rule 1, a saved look 2_0 has is that look`() = runTest {
        for (look in LensLook.entries) {
            assertEquals(look.name, look, lensLookFrom(meterLook = look.name))
            assertEquals("${look.name}, whatever showed under the meter", look, lensLookFrom(meterLook = look.name, display = "WAVE", waveLook = "STROBE"))
        }
        assertEquals("1.1's Light arc stays the Light arc", LensLook.LIGHT_ARC, lensLookFrom(meterLook = "LIGHT_ARC", display = "STROBE"))
        assertEquals("1.1's Classic stays Classic", LensLook.CLASSIC, lensLookFrom(meterLook = "CLASSIC", display = "WAVE", waveLook = "STROBE"))
    }

    @Test
    fun `rule 2, a 1_1 meter look 2_0 lacks over the strobe is the strobe`() = runTest {
        for (gone in listOf("TRAIL", "POINTERS", "LOUPE", "ARROWS", "LIGHT_BARS", "SPIRIT_LEVEL", "ECLIPSE", "ROLLING_SCALE", "SUNDIAL")) {
            assertEquals("$gone over the strobe wave", LensLook.STROBE, lensLookFrom(meterLook = gone, display = "WAVE", waveLook = "STROBE"))
        }
        assertEquals("no meter look saved, the strobe wave", LensLook.STROBE, lensLookFrom(display = "WAVE", waveLook = "STROBE"))
    }

    @Test
    fun `rule 2 reads the strobe as 1_1 did, old names included`() = runTest {
        assertEquals("the old strobe display", LensLook.STROBE, lensLookFrom(display = "STROBE"))
        assertEquals("the old strobe display over another wave look", LensLook.STROBE, lensLookFrom(display = "STROBE", waveLook = "FIREFLIES"))
        assertEquals("the old stripes look", LensLook.STROBE, lensLookFrom(display = "WAVE", waveLook = "STRIPES"))
        assertEquals("no display saved: 1.1's default was the wave", LensLook.STROBE, lensLookFrom(waveLook = "STROBE"))
        assertEquals("the old wave-view switch on", LensLook.STROBE, lensLookFrom(showWaves = true, waveLook = "STRIPES"))
        assertEquals("a damaged display read as 1.1's default, the wave", LensLook.STROBE, lensLookFrom(display = "HOLOGRAM", waveLook = "STROBE"))
    }

    @Test
    fun `rule 3, anything else is the Classic needle`() = runTest {
        assertEquals("a fresh install", LensLook.CLASSIC, lensLookFrom())
        assertEquals("1.1's defaults, Classic over the ribbon", LensLook.CLASSIC, lensLookFrom(meterLook = "CLASSIC", display = "WAVE", waveLook = "RIBBON"))
        assertEquals("2.0's trail, a Practice tool since Phase 78", LensLook.CLASSIC, lensLookFrom(meterLook = "TRAIL"))
        assertEquals("the cents under the meter, the strobe picked for the wave", LensLook.CLASSIC, lensLookFrom(display = "NEEDLE", waveLook = "STROBE"))
        assertEquals("the old wave-view switch off", LensLook.CLASSIC, lensLookFrom(showWaves = false, waveLook = "STROBE"))
        assertEquals("the strobe wave's look never saved: the ribbon", LensLook.CLASSIC, lensLookFrom(display = "WAVE"))
        assertEquals("another wave look", LensLook.CLASSIC, lensLookFrom(meterLook = "LOUPE", display = "WAVE", waveLook = "FIREFLIES"))
        assertEquals("a damaged meter look and wave look", LensLook.CLASSIC, lensLookFrom(meterLook = "SUNDIAL", waveLook = "LAVA_LAMP"))
    }

    @Test
    fun `picking a look ends the rule, saved under meter_look, and survives a restart`() = runTest {
        dataStore().edit {
            it[stringPreferencesKey("tuner_display")] = "WAVE"
            it[booleanPreferencesKey("show_waves")] = true
            it[stringPreferencesKey("wave_look")] = "STROBE"
        }
        close()
        repository().setLensLook(LensLook.LIGHT_ARC)
        close()

        val store = dataStore()
        assertEquals("the Light arc picked, not the strobe 1.1 showed", LensLook.LIGHT_ARC, repository(store).settings.first().lensLook)
        val saved = store.data.first()
        assertEquals("LIGHT_ARC", saved[stringPreferencesKey("meter_look")])
        assertEquals("1.1's display is removed", null, saved[stringPreferencesKey("tuner_display")])
        assertEquals("the old wave-view switch is removed", null, saved[booleanPreferencesKey("show_waves")])
        assertEquals("1.1's wave look is removed", null, saved[stringPreferencesKey("wave_look")])
    }

    @Test
    fun `1_1's focus mode on is taken once, removed, and false ever after`() = runTest {
        dataStore().edit { it[booleanPreferencesKey("focus_mode")] = true }
        close()
        val store = dataStore()
        val repo = repository(store)
        assertTrue("the first start opens in focus mode", repo.takeOldFocusMode())
        assertEquals("the switch is removed", null, store.data.first()[booleanPreferencesKey("focus_mode")])
        assertFalse("never again", repo.takeOldFocusMode())
        close()
        assertFalse("nor after a restart", repository().takeOldFocusMode())
    }

    /**
     * The settings file 1.1.0 wrote on the Xiaomi 12X on 2026-11-03 (Phase 76's upgrade check), every
     * value set to something other than its default through the app: read as it lies, nothing is lost.
     */
    @Test
    fun `a real 1_1 file is read with every kept setting, and the gone ones fall back as planned`() = runTest {
        javaClass.getResourceAsStream("/settings-1.1.preferences_pb")!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        val store = dataStore()
        val repo = repository(store)
        val settings = repo.settings.first()

        // Tuning
        assertEquals(442f, settings.a4Hz)
        assertEquals(TunerMode.AUTO, settings.mode)
        assertEquals(Instruments.UKULELE, settings.instrument)
        assertEquals("ukulele_baritone", settings.tuning.id)
        assertEquals(2, settings.tuning.loweredBy)
        val own = settings.customTunings.single()
        assertEquals("UpgradeCheck", own.name)
        assertEquals(Instruments.GUITAR, own.instrument)
        assertEquals(listOf(38, 45, 50, 55, 60, 64), own.notes.map { it.midi })
        assertEquals(Notation.SOLFEGE, settings.notationChoice)
        assertEquals(InTunePrecision.PRECISE, settings.inTunePrecision)
        // While tuning
        assertFalse(settings.keepScreenOn)
        assertFalse(settings.vibrateInTune)
        assertFalse("1.1's Show tuning help on gives no tick-off", settings.stringTickOff)
        assertEquals("and the word under the meter", UnderMeter.WORDS, settings.underMeter)
        // Look: Retro, Bold and the Loupe go; the strobe wave under the Loupe makes the lens the strobe
        assertEquals(ThemeMode.DARK, settings.theme)
        assertFalse("1.1's default, never switched, gives Ink (2026-11-03)", settings.dynamicColor)
        assertEquals(LensLook.STROBE, settings.lensLook)
        // Metronome
        assertEquals(97, settings.metronomeBpm)
        assertEquals(Meter.SEVEN_EIGHT, settings.metronomeMeter)
        assertEquals(ClickSound.CLAVES, settings.metronomeClickSound)
        assertEquals(Subdivision.TWO, settings.metronomeSubdivision)
        assertTrue(settings.metronomeVibrate)
        assertEquals(SpeedTrainer(90, 130, 2, 8), settings.metronomeTrainer)
        assertTrue(settings.metronomeTrainerOn)
        // Practice
        assertEquals(PracticeTool.SCALES, settings.practiceTool)
        assertEquals(PitchClass.F_SHARP, settings.practiceRoot)
        assertEquals(ChordQuality.MINOR_SEVENTH, settings.practiceChordQuality)
        assertEquals(ScaleType.DORIAN, settings.practiceScaleType)
        assertEquals(SongKey(PitchClass.E, minor = true), settings.practiceCapoShapes)
        assertEquals(3, settings.practiceCapoFret)
        assertEquals(DroneSound.WARM_PAD, settings.droneSound)
        assertEquals(DroneVoicing.FIFTH_OCTAVE, settings.droneVoicing)
        assertTrue(settings.droneLower)
        assertTrue(settings.leftHanded)
        assertEquals(Exercise.CHORD_TYPE, settings.earExercise)
        assertEquals(
            setOf(ChordQuality.MAJOR, ChordQuality.MINOR, ChordQuality.DOMINANT_SEVENTH, ChordQuality.MAJOR_SEVENTH, ChordQuality.SUS4),
            settings.earChordTypes,
        )
        // Google's rating window
        assertEquals(1_793_687_587_874L, settings.firstUseMillis)
        assertFalse(settings.ratingAsked)

        // 1.1's focus mode was on: the first start opens in it, and its key is all that is written.
        val before = store.data.first().asMap()
        assertTrue(repo.takeOldFocusMode())
        assertEquals(before - booleanPreferencesKey("focus_mode"), store.data.first().asMap())
    }

    @Test
    fun `1_1's focus mode off is removed too, and the tuner opens as usual`() = runTest {
        dataStore().edit { it[booleanPreferencesKey("focus_mode")] = false }
        close()
        val store = dataStore()
        assertFalse(repository(store).takeOldFocusMode())
        assertEquals("the switch is removed", null, store.data.first()[booleanPreferencesKey("focus_mode")])
    }

    @Test
    fun `without 1_1's focus mode nothing is written`() = runTest {
        val store = dataStore()
        store.edit { it[floatPreferencesKey("a4_hz")] = 442f }
        val before = store.data.first()
        assertFalse(repository(store).takeOldFocusMode())
        assertEquals("the file is as it was", before, store.data.first())
    }

    @Test
    fun `1_1's Calm or Bold glow is left in the file unread`() = runTest {
        val store = dataStore()
        val repo = repository(store)
        // A key no longer read changes nothing.
        val before = repo.settings.first()
        for (effect in listOf("CALM", "BOLD", "FIREWORKS")) {
            store.edit { it[stringPreferencesKey("in_tune_effect")] = effect }
            assertEquals(effect, before, repo.settings.first())
        }
        assertEquals("never removed", "FIREWORKS", store.data.first()[stringPreferencesKey("in_tune_effect")])
    }

    @Test
    fun `a style saved by 1_1 is left in the file unread and changes nothing`() = runTest {
        // 2.0 has one look (Ink): every 1.1 style, old names and damaged values included, reads the same.
        val store = dataStore()
        val repo = repository(store)
        val plain = repo.settings.first()
        for (saved in listOf("ANCIENT", "CLASSIC", "RETRO", "CONTEMPORARY", "FUTURISTIC", "SPACE_AGE", "MIDDLE_AGES", "", "BAROQUE")) {
            store.edit { it[stringPreferencesKey("visual_style")] = saved }
            assertEquals(saved, plain, repo.settings.first())
        }
        repo.setDynamicColor(false)
        assertEquals("the key stays as it lay", "BAROQUE", store.data.first()[stringPreferencesKey("visual_style")])
    }

    @Test
    fun `a 1_1 player of another style keeps the wallpaper colours they had switched on`() = runTest {
        val store = dataStore()
        store.edit {
            it[stringPreferencesKey("visual_style")] = "RETRO"
            it[booleanPreferencesKey("dynamic_color")] = true
        }
        assertEquals(true, repository(store).settings.first().dynamicColor)
        store.edit { it[booleanPreferencesKey("dynamic_color")] = false }
        assertEquals(false, repository(store).settings.first().dynamicColor)
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
        val fifths = repo.addCustomTuning("Fifths", Instruments.BASS_4, fifthsBass, pick = true)!!
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
    fun `every lens look is saved under meter_look by its name`() = runTest {
        val store = dataStore()
        val repo = repository(store)
        for (look in LensLook.entries) {
            repo.setLensLook(look)
            assertEquals(look, repo.settings.first().lensLook)
            assertEquals(look.name, store.data.first()[stringPreferencesKey("meter_look")])
        }
    }

    @Test
    fun `the three meter looks, Classic first and the default`() {
        assertEquals(listOf(LensLook.CLASSIC, LensLook.LIGHT_ARC, LensLook.STROBE), LensLook.entries)
        assertEquals(LensLook.CLASSIC, LensLook.DEFAULT)
    }

    @Test
    fun `the old when-in-tune choice is ignored, whichever way it was saved`() = runTest {
        val store = dataStore()
        val repo = repository(store)
        for (breathes in listOf(true, false)) {
            store.edit { it[booleanPreferencesKey("wave_breathes_in_tune")] = breathes }
            assertEquals("saved $breathes", AppSettings(), repo.settings.first())
        }
    }

    @Test
    fun `the string tick-off is saved under string_tick_off and survives a restart`() = runTest {
        val store = dataStore()
        repository(store).setStringTickOff(true)
        assertEquals(true, store.data.first()[booleanPreferencesKey("string_tick_off")])
        assertEquals(true, repository(store).settings.first().stringTickOff)
        repository(store).setStringTickOff(false)
        assertEquals(false, repository(store).settings.first().stringTickOff)
    }

    @Test
    fun `the 12th-fret check's old switch from a 2_0 test build is removed at start, changing nothing else`() = runTest {
        val store = dataStore()
        store.edit {
            it[booleanPreferencesKey("show_intonation_check")] = true
            it[floatPreferencesKey("a4_hz")] = 442f
        }
        val repo = repository(store)
        val before = repo.settings.first()
        repo.forgetDroppedSettings()
        val saved = store.data.first()
        assertEquals("the switch is removed", null, saved[booleanPreferencesKey("show_intonation_check")])
        assertEquals("the rest stays", 442f, saved[floatPreferencesKey("a4_hz")])
        assertEquals("it was never read", before, repo.settings.first())
    }

    @Test
    fun `the signal bars' old switch is removed at start, changing nothing else`() = runTest {
        val store = dataStore()
        store.edit {
            it[booleanPreferencesKey("show_signal_bars")] = true
            it[floatPreferencesKey("a4_hz")] = 442f
        }
        val repo = repository(store)
        val before = repo.settings.first()
        repo.forgetDroppedSettings()
        val saved = store.data.first()
        assertEquals("the switch is removed", null, saved[booleanPreferencesKey("show_signal_bars")])
        assertEquals("the rest stays", 442f, saved[floatPreferencesKey("a4_hz")])
        assertEquals("it was never read", before, repo.settings.first())
    }

    @Test
    fun `without a dropped setting nothing is written at start`() = runTest {
        val store = dataStore()
        store.edit { it[floatPreferencesKey("a4_hz")] = 442f }
        val before = store.data.first()
        repository(store).forgetDroppedSettings()
        assertEquals("the file is as it was", before, store.data.first())
    }

    @Test
    fun `1_1's Show tuning help has no say in the tick-off, so a 1_1 player starts without it`() = runTest {
        val store = dataStore()
        val repo = repository(store)
        for (shown in listOf(true, false)) {
            store.edit { it[booleanPreferencesKey("show_tune_hint")] = shown }
            assertEquals("show_tune_hint = $shown", false, repo.settings.first().stringTickOff)
        }
        repo.setStringTickOff(true)
        assertEquals("the old key stays as it lay", false, store.data.first()[booleanPreferencesKey("show_tune_hint")])
        assertEquals(true, repo.settings.first().stringTickOff)
    }

    @Test
    fun `a 1_1 player who had turned the tune hint off gets the cents under the meter, one who had it on the words`() = runTest {
        val store = dataStore()
        val repo = repository(store)
        store.edit { it[booleanPreferencesKey("show_tune_hint")] = false }
        assertEquals(UnderMeter.CENTS, repo.settings.first().underMeter)
        store.edit { it[booleanPreferencesKey("show_tune_hint")] = true }
        assertEquals(UnderMeter.WORDS, repo.settings.first().underMeter)
    }

    @Test
    fun `the choice under the meter is saved under under_meter and takes over from 1_1's tune hint for good`() = runTest {
        val store = dataStore()
        store.edit { it[booleanPreferencesKey("show_tune_hint")] = false }
        repository(store).setUnderMeter(UnderMeter.WORDS)
        val saved = store.data.first()
        assertEquals("WORDS", saved[stringPreferencesKey("under_meter")])
        assertEquals("the old key is read only once", null, saved[booleanPreferencesKey("show_tune_hint")])
        assertEquals("a restart keeps the pick", UnderMeter.WORDS, repository(store).settings.first().underMeter)
        repository(store).setUnderMeter(UnderMeter.CENTS)
        assertEquals(UnderMeter.CENTS, repository(store).settings.first().underMeter)
    }

    @Test
    fun `an unknown choice under the meter reads as the words`() = runTest {
        val store = dataStore()
        store.edit { it[stringPreferencesKey("under_meter")] = "PICTURES" }
        assertEquals(UnderMeter.WORDS, repository(store).settings.first().underMeter)
    }

    @Test
    fun `1_1's tour flag is left in the file unread`() = runTest {
        // 2.0 has no tour (Phase 68): whether 1.1 saw it changes nothing, and the key stays as it lay.
        val store = dataStore()
        val repo = repository(store)
        for (seen in listOf(true, false)) {
            store.edit { it[booleanPreferencesKey("tour_seen")] = seen }
            assertEquals("saved $seen", AppSettings(), repo.settings.first())
        }
        repo.setDynamicColor(false)
        assertEquals("never removed", false, store.data.first()[booleanPreferencesKey("tour_seen")])
    }

    // --- Custom tunings ---

    private val dropC = listOf(36, 43, 48, 53, 57, 62).map(::Note)
    private val openC = listOf(36, 43, 48, 55, 60, 64).map(::Note)
    private val fifthsBass = listOf(24, 31, 38, 45).map(::Note)
    private val customKey = stringPreferencesKey("custom_tunings")

    @Test
    fun `a new custom tuning is saved after the others, not picked, and survives a restart`() = runTest {
        val repo = repository()
        val first = repo.addCustomTuning("  Drop   C ", Instruments.GUITAR, dropC)!!
        val second = repo.addCustomTuning("Fifths", Instruments.BASS_4, fifthsBass)!!

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
        val fifths = repo.addCustomTuning("Fifths", Instruments.BASS_4, fifthsBass)!!
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
        val drop = repo.addCustomTuning("Drop C", Instruments.GUITAR, dropC)!!
        repo.setTuning(drop.tuning)
        repo.setInstrument(Instruments.GUITAR)
        assertEquals(drop.tuning, repo.settings.first().tuning)

        repo.setInstrument(Instruments.UKULELE)
        assertEquals(Instruments.UKULELE.defaultTuning, repo.settings.first().tuning)
    }

    @Test
    fun `editing a custom tuning keeps its id and place, and it stays picked with the new notes`() = runTest {
        val repo = repository()
        val drop = repo.addCustomTuning("Drop C", Instruments.GUITAR, dropC)!!
        val fifths = repo.addCustomTuning("Fifths", Instruments.BASS_4, fifthsBass)!!
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
    fun `editing or deleting the picked own tuning while lowered starts it, or the standard, not lowered`() = runTest {
        val repo = repository()
        val drop = repo.addCustomTuning("Drop C", Instruments.GUITAR, dropC)!!
        val open = repo.addCustomTuning("Open C", Instruments.GUITAR, openC)!!
        repo.setTuning(drop.tuning)
        repo.setLoweredBy(2)
        repo.updateCustomTuning(open.id, "Open C", openC)
        assertEquals("editing another keeps it", 2, repo.settings.first().tuning.loweredBy)

        repo.updateCustomTuning(drop.id, "Drop C", openC)
        assertEquals(openC, repo.settings.first().tuning.strings)
        assertEquals(0, repo.settings.first().tuning.loweredBy)

        repo.setLoweredBy(1)
        repo.deleteCustomTuning(drop.id)
        assertEquals(Tunings.STANDARD, repo.settings.first().tuning)

        repo.setLoweredBy(1)
        repo.addCustomTuning("Mine", Instruments.GUITAR, dropC, pick = true)
        assertEquals(0, repo.settings.first().tuning.loweredBy)
    }

    @Test
    fun `an edit that doesn't fit its instrument is refused and the tuning is kept`() = runTest {
        val repo = repository()
        val drop = repo.addCustomTuning("Drop C", Instruments.GUITAR, dropC)!!
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
        val fifths = repo.addCustomTuning("Fifths", Instruments.BASS_4, fifthsBass)!!
        val drop = repo.addCustomTuning("Drop C", Instruments.GUITAR, dropC)!!
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
        val drop = repo.addCustomTuning("Drop C", Instruments.GUITAR, dropC)!!
        val open = repo.addCustomTuning("Open C", Instruments.GUITAR, openC)!!
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
        val drop = repo.addCustomTuning("Drop C", Instruments.GUITAR, dropC)!!
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
        val fifths = repo.addCustomTuning("Fifths", Instruments.BASS_4, fifthsBass)!!
        store.edit { it[stringPreferencesKey("tuning_id")] = fifths.id }

        val settings = repo.settings.first()
        assertEquals(Instruments.BASS_4, settings.instrument)
        assertEquals(fifths.tuning, settings.tuning)
    }

    @Test
    fun `a refused microphone is remembered across restarts`() = runTest {
        val repo = repository()
        assertEquals("not refused on a fresh install", false, repo.settings.first().micRefused)
        repo.setMicRefused(true)
        close()

        assertEquals(true, repository().settings.first().micRefused)
    }

    @Test
    fun `the first use is noted once, and the rating ask is remembered`() = runTest {
        val repo = repository()
        assertEquals("not noted on a fresh install", 0L, repo.settings.first().firstUseMillis)
        assertEquals(false, repo.settings.first().ratingAsked)
        repo.noteFirstUseOnce(1_000L)
        repo.noteFirstUseOnce(9_000L)
        repo.setRatingAsked(true)
        close()

        val reopened = repository().settings.first()
        assertEquals("the first one stays", 1_000L, reopened.firstUseMillis)
        assertEquals(true, reopened.ratingAsked)
    }

    /** [real] for reading, while every write fails as on a full phone. */
    private class FullDataStore(private val real: DataStore<Preferences>) : DataStore<Preferences> {
        override val data: Flow<Preferences> = real.data
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw IOException("No space left on device")
    }

    @Test
    fun `a write the phone cannot make returns normally, logged, and keeps the old values`() = runTest {
        val real = dataStore()
        val kept = DataStoreSettingsRepository(real)
        kept.addCustomTuning("Mine", Instruments.GUITAR, dropC, pick = true)!!
        kept.setInTunePrecision(InTunePrecision.PRECISE)
        real.edit {
            it[booleanPreferencesKey("focus_mode")] = true
            it[booleanPreferencesKey("show_intonation_check")] = true
        }
        val before = kept.settings.first()
        val logged = mutableListOf<IOException>()
        val repo = DataStoreSettingsRepository(FullDataStore(real), logWriteError = { logged += it })
        val mine = before.customTunings.single()

        // Every write of the interface, by name: a new one fails this test until it is added here.
        val writes: Map<String, suspend () -> Unit> = mapOf(
            "setA4" to { repo.setA4(432f) },
            "setMode" to { repo.setMode(TunerMode.entries.last()) },
            "setInstrument" to { repo.setInstrument(Instruments.BASS_4) },
            "setTuning" to { repo.setTuning(Tunings.DROP_D) },
            "setLoweredBy" to { repo.setLoweredBy(2) },
            "addCustomTuning" to { assertNull(repo.addCustomTuning("New", Instruments.GUITAR, openC, pick = true)) },
            "updateCustomTuning" to { assertNull(repo.updateCustomTuning(mine.id, "Changed", openC, pick = true)) },
            "deleteCustomTuning" to { repo.deleteCustomTuning(mine.id) },
            "setNotation" to { repo.setNotation(Notation.entries.last()) },
            "setInTunePrecision" to { repo.setInTunePrecision(InTunePrecision.entries.last()) },
            "setTheme" to { repo.setTheme(ThemeMode.entries.last()) },
            "setDynamicColor" to { repo.setDynamicColor(!before.dynamicColor) },
            "setHighContrast" to { repo.setHighContrast(true) },
            "setKeepScreenOn" to { repo.setKeepScreenOn(!before.keepScreenOn) },
            "setVibrateInTune" to { repo.setVibrateInTune(!before.vibrateInTune) },
            "setVibrationGuide" to { repo.setVibrationGuide(true) },
            "setStringTickOff" to { repo.setStringTickOff(!before.stringTickOff) },
            "setUnderMeter" to { repo.setUnderMeter(UnderMeter.CENTS) },
            "setLensLook" to { repo.setLensLook(LensLook.entries.last()) },
            // 1.1's focus mode switch is there (below), so the read once tries to remove it.
            "takeOldFocusMode" to { assertTrue("it was on", repo.takeOldFocusMode()) },
            // The 12th-fret check's old switch is there (below) too, so the start's clean-up tries to remove it.
            "forgetDroppedSettings" to { repo.forgetDroppedSettings() },
            "setMicRefused" to { repo.setMicRefused(!before.micRefused) },
            // The first-use notes run at every start (MainActivity.onCreate): a full phone must not stop the app.
            "noteFirstUseOnce" to { repo.noteFirstUseOnce(1_000L) },
            "setRatingAsked" to { repo.setRatingAsked(!before.ratingAsked) },
            "setMetronomeBpm" to { repo.setMetronomeBpm(200) },
            "setMetronomeMeter" to { repo.setMetronomeMeter(Meter.entries.last()) },
            "setMetronomeVibrate" to { repo.setMetronomeVibrate(!before.metronomeVibrate) },
            "setMetronomeClickSound" to { repo.setMetronomeClickSound(ClickSound.entries.last()) },
            "setMetronomeSubdivision" to { repo.setMetronomeSubdivision(Subdivision.entries.last()) },
            "setMetronomeTrainer" to { repo.setMetronomeTrainer(SpeedTrainer.of(60, 120, SpeedTrainer.DEFAULT_STEP, SpeedTrainer.DEFAULT_BARS)) },
            "setMetronomeTrainerOn" to { repo.setMetronomeTrainerOn(!before.metronomeTrainerOn) },
            "setPracticeTool" to { repo.setPracticeTool(PracticeTool.entries.last()) },
            "setPracticeRoot" to { repo.setPracticeRoot(PitchClass.entries.last()) },
            "setPracticeChordQuality" to { repo.setPracticeChordQuality(ChordQuality.entries.last()) },
            "setPracticeScaleType" to { repo.setPracticeScaleType(ScaleType.entries.last()) },
            "setPracticeCapoShapes" to { repo.setPracticeCapoShapes(SongKey(PitchClass.entries.last(), minor = true)) },
            "setPracticeCapoFret" to { repo.setPracticeCapoFret(5) },
            "setDroneSound" to { repo.setDroneSound(DroneSound.entries.last()) },
            "setDroneVoicing" to { repo.setDroneVoicing(DroneVoicing.entries.last()) },
            "setDroneLower" to { repo.setDroneLower(!before.droneLower) },
            "setEarExercise" to { repo.setEarExercise(Exercise.entries.last()) },
            "setEarNoteChoice" to { repo.setEarNoteChoice(NoteChoice.entries.last()) },
            "setEarNotesInRow" to { repo.setEarNotesInRow(3) },
            "setEarNoteMatch" to { repo.setEarNoteMatch(NoteMatch.entries.last()) },
            "setEarIntervals" to { repo.setEarIntervals(setOf(Interval.entries.last())) },
            "setEarDirections" to { repo.setEarDirections(setOf(IntervalDirection.entries.last())) },
            "setEarChordTypes" to { repo.setEarChordTypes(setOf(ChordQuality.entries.first())) },
            "setLeftHanded" to { repo.setLeftHanded(!before.leftHanded) },
        )
        val inInterface = SettingsRepository::class.java.methods.map { it.name }
            .filter { it != "getSettings" && '$' !in it }.toSet()
        assertEquals(inInterface, writes.keys)

        for ((name, write) in writes) {
            write()
            assertEquals("$name keeps the old values", before, repo.settings.first())
        }
        assertEquals("every failed write is logged", writes.size, logged.size)
    }

    private val deviceFile: File get() = folder.root.resolve("this_device.preferences_pb")

    /** Storage on [on], Okio's as in [dataStore] (Windows), for the app's own factory. */
    private fun storage(on: File) = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { on.absolutePath.toPath() }

    /** The repository as the app makes it ([DataStoreSettingsRepository.create]): two files, the damage handler and the move. */
    private fun appRepository(): DataStoreSettingsRepository {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        return DataStoreSettingsRepository.create(storage(file), storage(deviceFile), scope)
    }

    /** A plain DataStore on this phone's own file, to look inside it. */
    private fun deviceDataStore(): DataStore<Preferences> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        return PreferenceDataStoreFactory.create(storage = storage(deviceFile), scope = scope)
    }

    @Test
    fun `damaged files through the app's own factory give the defaults, and saving works again`() = runTest {
        file.writeText("this is not a preferences file")
        deviceFile.writeText("nor is this")
        val repo = appRepository()

        assertEquals(AppSettings(), repo.settings.first())
        repo.setTheme(ThemeMode.DARK)
        repo.setMicRefused(true)
        close()

        val reopened = appRepository().settings.first()
        assertEquals("the damaged settings were replaced", ThemeMode.DARK, reopened.theme)
        assertEquals("and this phone's own file too", true, reopened.micRefused)
    }

    @Test
    fun `a refused microphone is kept apart from the settings, which backups carry to a new phone`() = runTest {
        val micRefusedKey = booleanPreferencesKey("mic_refused")
        // A version before Phase 65 kept it with the settings.
        dataStore().edit {
            it[micRefusedKey] = true
            it[stringPreferencesKey("visual_style")] = "RETRO"
        }
        close()

        val repo = appRepository()
        assertEquals("still refused after the update", true, repo.settings.first().micRefused)
        repo.setTheme(ThemeMode.DARK)
        close()

        val settingsFile = dataStore().data.first()
        assertNull("moved out of the settings file", settingsFile[micRefusedKey])
        assertEquals("the rest stays", "RETRO", settingsFile[stringPreferencesKey("visual_style")])
        close()
        assertEquals("into this phone's own file", true, deviceDataStore().data.first()[micRefusedKey])
        close()

        // A refusal saved now goes to this phone's file alone.
        appRepository().setMicRefused(false)
        close()
        assertNull(dataStore().data.first()[micRefusedKey])
        close()
        assertEquals(false, deviceDataStore().data.first()[micRefusedKey])
    }

    @Test
    fun `every collector shares one reading of a change`() = runBlocking {
        val repo = repository()
        repo.settings.first()
        val readers = List(10) { async { repo.settings.first { it.theme == ThemeMode.DARK } } }
        delay(200) // every reader has the settings as they are
        val before = repo.parseCount

        repo.setTheme(ThemeMode.DARK)
        withTimeout(5_000) { readers.awaitAll() }

        assertEquals("one change, read once for all ten", 1, repo.parseCount - before)
    }
}
