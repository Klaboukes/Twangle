# Twangle — Architecture

Twangle is a chromatic and guitar-string tuner for Android. It listens to the microphone, finds the fundamental frequency of the note being played, and shows how far that note is from the target pitch, in cents.

The design is split into this overview and topic files in [docs/architecture/](docs/architecture/), each small enough to read whole. They hold the design decisions, the numbers that define behaviour and the reasons behind them. The code is the source of truth for everything else (exact geometry, colours, test lists). **Before a change, read the Contents below and the topic files the change touches.** When a decision changes, update the matching section in the same change. Superseded designs go to [history.md](docs/architecture/history.md) (with the reason they changed), finished work to [ROADMAP-ARCHIVE.md](ROADMAP-ARCHIVE.md), the rest to git.

## Contents
The section numbers (§1–§9) are kept, so older references such as "ARCHITECTURE.md §6, *Languages*" still lead to the right file.

In this file:
1. [Tech stack](#1-tech-stack): [name, app ID and icon](#name-app-id-and-icon) · [rejected alternatives](#rejected-alternatives)
2. [Module structure](#2-module-structure)
7. [Package layout](#7-package-layout-per-module)
9. [Privacy](#9-privacy)

Topic files:
- §3 Signal pipeline:
  - **[Signal pipeline](docs/architecture/signal-pipeline.md)**: Pipeline · Key numbers · Other sample rates · Frame assembly · Low notes · Signal gate · Sustain check (ticks and clicks) · Noise floor, SNR and clarity · Reception · Tips · Signal bars (optional) · Pitch detection (YIN) · Smoothing · Edge cases (measured, EdgeCaseTest)
  - **[Notes, tunings and audio](docs/architecture/notes-and-audio.md)**: Note math · Tunings · Instruments · Chord shapes and scales · Capo · Ear training questions · Intonation check · Strobe model · Pitch history · Audio source selection · Audio capture · Reference tone · Drone
  - **[Strum check and metronome](docs/architecture/strum-and-metronome.md)**: Strum check (polyphonic) · Chord naming · Metronome · Tempo from music
- §4 **[Threading and performance](docs/architecture/performance.md)**: Threads and the main thread · Measured on the Galaxy S26 · Low-note window on the Xiaomi 12X · Watch CPU and the native detector · Meter looks · Baseline Profile · Release build: shrinking and signing
- §5 **[Lifecycle, permissions and settings](docs/architecture/lifecycle.md)**: Microphone, permission and lifetime · Settings · Saved settings · Custom tunings
- §6 UI:
  - **[UI basics](docs/architecture/ui.md)**: UI state model · Navigation bar · Landscape and wide screens · Accessibility
  - **[Tuner screen and tour](docs/architecture/ui-tuner.md)**: Tuner screen · Strum check view · More tools · 12th-fret check view · Chord name view · Pitch trail view · Tour · Display
  - **[Meter, wave and styles](docs/architecture/ui-meter-and-styles.md)**: Meter · Meter art language · Wave view · Strobe look · Wave looks · Visual styles
  - **[Metronome, widget and watch](docs/architecture/ui-other-screens.md)**: Metronome screen · Practice tab · Home-screen widget · Watch app (Wear OS)
  - **[Languages](docs/architecture/languages.md)**: Languages and registers · One language list · Day and night · In-app language (Settings → Language) · Per-app language (Android 13+) · Formatting · Right to left (Arabic) · Guard · Adding a language
  - **[Translation glossary](docs/architecture/glossary.md)**: the app's words for its recurring terms, per language
- §8 **[Testing strategy](docs/architecture/testing.md)**: Test layers · Fast UI tests · Any phone · Every screen in every style · Every language · Quick and thorough UI runs
- **[Website](docs/architecture/website.md)** (klaboukes.github.io, Phase 54): Where it lives · Pages and the frame · Nothing from another site · Languages · Pictures · Publishing
- **[History](docs/architecture/history.md)**: Overview · Threading and performance · Lifecycle, permissions and settings · UI basics · Tuner screen · Meter, wave and styles · Metronome, widget and watch · Strum check and metronome · Languages · Testing

## 1. Tech stack

| Concern | Choice | Why |
|---|---|---|
| Language | **Kotlin 2.x** | First-class Android language, null safety, coroutines. |
| UI | **Jetpack Compose + Material 3** | A screen that redraws ~30–60×/s from one state object; custom drawing with `Canvas`. |
| Audio capture | **`android.media.AudioRecord`** (PCM float, mono) | Raw samples, no dependencies. A tuner tolerates ~50–100 ms latency, so Oboe/AAudio isn't needed. |
| Pitch detection | **YIN in pure Kotlin** (`:core:dsp`) | Sub-cent accuracy on monophonic signals, resistant to octave errors, unit-testable on the JVM. |
| Async | **Coroutines + Flow / StateFlow** | Audio frames → pitch → UI state is a stream. |
| State holder | **AndroidX ViewModel** | Survives configuration changes and owns the pipeline's lifetime. |
| DI | **Manual (`AppContainer`)** | The graph is tiny; Hilt would cost build time. Revisit if it grows. |
| Persistence | **DataStore (Preferences)** (`:core:settings`) | Saved settings (see [Lifecycle](docs/architecture/lifecycle.md)). |
| Ratings | **Google Play In-App Review** (`com.google.android.play:review`, `:app` only) | Google's own rating window, asked for once at a good moment (Phase 50); adds no permission. Not open source, so the GPL build for F-Droid leaves it out (see [Lifecycle](docs/architecture/lifecycle.md), *Google's rating window*). |
| Build | **Gradle 9 + AGP 9 (built-in Kotlin), Kotlin DSL + version catalog** | Versions live only in `gradle/libs.versions.toml`. |
| Testing | JUnit 4, kotlinx-coroutines-test, Compose UI test | DSP checked with synthesized signals (see [Testing strategy](docs/architecture/testing.md)). |
| CI | **GitHub Actions** (`.github/workflows/android.yml`) | test + lint + assembleDebug + unsigned assembleRelease/bundleRelease, once a night on `main` (03:00 UTC, skipped when only documentation changed) and by hand; every change is checked locally first, so per-PR and per-merge runs only duplicated it, and per-merge runs used up the free plan's minutes in a week (2026-10-16). No APK upload: nobody downloaded it, and it filled the free plan's artifact storage (2026-10-16). |
| Min / target SDK | **26** (Android 8.0) / 37 | `ENCODING_PCM_FLOAT` and `UNPROCESSED` work from API 24+; 26 covers ~97 % of devices and needs no PNG icon fallbacks. |

### Name, app ID and icon
- **Name:** Twangle (InTune until October 2026, which clashed with Microsoft Intune). Names with "free" in them are out: Google Play bans price words in titles.
- **App ID:** `io.github.klaboukes.twangle`; packages and module namespaces live under it.
- **Launcher icon:** "twang to tune": a near-black (`#24260F`) string on mustard (`#E3B23C`, the Retro style's colours) whose wobble dies away into a straight line ending in a dot, as a plucked string settles in tune. The owner's pick on 2026-10-15 from five ideas and thirteen colour schemes (`docs/design/logo-preview.html`, `logo-round2-preview.html`). Adaptive icon (`res/drawable/ic_launcher_foreground.xml` + `ic_launcher_background`); the same one-colour foreground is the monochrome layer for themed icons. The phone's launcher picks the outline (circle, squircle, rounded square; the Xiaomi's is a squircle), so the artwork stays within 33 dp of the centre, the safe zone, and no mask clips it.
- **Opening screen:** `androidx.core:core-splashscreen` (`installSplashScreen()`, theme `Theme.Twangle.Starting`), so Android 8–11 look like 12+: the icon's string on a mustard tile, on mustard (light) or `#1C1510` (dark, `values-night`). It follows the phone's dark mode, because the app's theme setting isn't read yet, and stays up until the settings are read and the first frame is composed (`setKeepOnScreenCondition`).

### Rejected alternatives
- **Flutter / React Native:** microphone DSP through platform channels adds latency and complexity for a single-platform app.
- **C++/Oboe via NDK:** lower latency a tuner doesn't need. `PitchDetector` is an interface, so a native version could replace the Kotlin one. Phase 21 measured whether anything asks for it and decided no (2026-10-15): the Kotlin chain takes at most 13 % of a little core on a watch-like CPU, 3.5 % of a phone's big core with the low-note window, and strum check about 3 % ([Threading and performance](docs/architecture/performance.md), *Watch CPU and the native detector*).
- **FFT peak picking:** fooled by strong harmonics (low E often has a weaker fundamental than its 2nd harmonic) and coarse at low frequencies. Time-domain YIN handles both.

## 2. Module structure

```
:app               Android application: Activity, Application, AppContainer (DI), navigation bar and screen switching,
                   home-screen widget
:wear              Wear OS application (same app ID): the watch tuner, with its own mic and its own small UI
:feature:tuner     Tuner screen: Compose UI, TunerViewModel, UI state, meter frame, wave view
:feature:settings  Settings and About screens, SettingsViewModel
:feature:metronome Metronome screen: Compose UI, MetronomeViewModel
:feature:practice  Practice tab (Phase 32, behind its switch): the tool button, Chords and Scales (charts, the neck, the
                   drone button), the Capo helper
:feature:eartraining Ear training (Phase 55, a Practice tool behind its switch): Play it back, Intervals, Chord type;
                   EarTrainingViewModel, the screen, the choices panel; the one Practice tool that listens
:core:audio        Microphone capture (AudioRecord → Flow<FloatArray>), reference tones, metronome clicks and the drone
                   (AudioTrack)
:core:settings     Saved user settings: SettingsRepository backed by DataStore
:core:designsystem Visual styles: TwangleTheme, palettes, fonts, backgrounds, style mood pictures, meter and wave looks
:core:dsp          Pure Kotlin/JVM (NO Android deps): pitch detection, levels, note math, tunings, smoothing,
                   strum check, chord naming, chord/scale/capo charts, string synthesis (the reference tone in
                   each instrument's voice), the drone's synthesis, metronome click track and tempo
:core:testing      UI test helpers only (androidTestImplementation): shared-activity Compose rule and runner, @Thorough,
                   @EveryLanguage, assertWhollyShown, PhoneScreen
:baselineprofile   Android test module: Baseline Profile generator, startup and meter-frame benchmarks
```

Dependencies ("→" = depends on):

```
:app               → every module below (it wires them together)
:wear              → :core:audio, :core:dsp (nothing else: no settings, no design system, no :feature module;
                     only its JVM tests see :core:designsystem, to check the copied colours)
:feature:tuner     → :core:audio, :core:settings, :core:designsystem, :core:dsp
:feature:settings  → :core:settings, :core:designsystem (and through them :core:dsp)
:feature:metronome → :core:audio, :core:settings, :core:designsystem, :core:dsp
:feature:practice  → :core:audio (only DronePlayer, playback: no microphone), :core:settings, :core:designsystem, :core:dsp
:feature:eartraining → :core:audio (TonePlayer, MicPermissionRules; the microphone through PitchListener, which :app
                     backs with the tuner's PitchPipeline), :core:settings, :core:designsystem, :core:dsp
:core:designsystem → :core:settings (VisualStyle, ThemeMode), :core:dsp (StrobeMotion for the wave)
:core:settings     → :core:dsp
:core:audio        → :core:dsp (api: StringSynth makes the reference tone's samples, ClickTrack the metronome's,
                     DroneSynth the drone's;
                     TonePlayer's StringVoice, MetronomePlayer's beats and meters and DronePlayer's sounds
                     are :core:dsp types)
:core:dsp          → no other module
:core:testing      → no other module; used by the UI tests of the :feature modules
:baselineprofile   → drives :app on a device (targetProjectPath); nothing depends on it
```

Rules:
- `:core:dsp` is a plain `kotlin("jvm")` module and never imports `android.*`, so the most important code stays fast to test and portable.
- `:core:audio` knows Android audio, nothing about notes or UI (a tone is asked for in Hz). `:core:settings` knows how settings are saved, nothing about audio or UI.
- `:core:designsystem` holds everything both screens draw with (including the meter and wave art, so the settings previews draw exactly what the tuner draws), and nothing about audio.
- `:feature:*` modules never depend on each other, and `:wear` uses none of them. Only `:app` wires implementations together (`AppContainer`) and switches screens (`MainActivity`).
- `:baselineprofile` runs only on demand with a phone connected ([Threading and performance](docs/architecture/performance.md)). CI never runs it.

## 7. Package layout (per module)

```
core/dsp/src/main/kotlin/io/github/klaboukes/twangle/core/dsp/
    pitch/      PitchDetector (interface), PitchResult, YinPitchDetector, UpsamplingPitchDetector,
                AnalysisSettings, PitchRange, LowPassFilter, SignalGate, SustainCheck, FftDifference,
                Fft, Clarity
    level/      NoiseFloorTracker, ReceptionTracker
    music/      Note, NoteMapper, Tuning (+ Tunings presets), Instrument (+ Instruments), Notation, KeySpelling,
                LoweredTunings (any tuning lowered by up to 2½ steps)
    smoothing/  PitchSmoother, MedianFilter, ExponentialSmoother, InTuneDetector
    strobe/     StrobeMotion
    trail/      PitchHistory (the pitch trail's last seconds of readings)
    synth/      StringSynth, StringVoice (+ BodyMode, StringVoices): the reference tone in each instrument's voice
                (PluckSynth, the tests' ideal plucked string, is in testFixtures)
    drone/      DroneSynth, DroneSound, DroneEngines, PartialBank (the drone under Scales)
    metronome/  ClickTrack (+ BeatListener), ClickSounds (+ ClickSound, ClickRecipes, PercussionSynth, Resampler), Meter (+ BeatAccent), TapTempo (+ TempoMarking),
                TempoDetector (+ TempoStatus), OnsetTempoDetector (tempo from music)
    strum/      StrumDetector (+ PartialFit), StrumResult, StrumListener, StrumVerdict, StrumInstruments (strum check)
    chord/      ChordDetector, ChordResult, ChordNamer, ChordSettler, Chord, ChordQuality, ChordSpelling,
                ChordInstruments (chord naming from a strum);
                ChordShapeFinder, ChordShape, Barre (finger shapes for the chord charts)
    scale/      Scale, ScaleType, ScaleFinder, FretNote (scales on the neck)
    capo/       Capo, CapoPlan, SongKey (the capo helper's maths: fret for a key and shapes, what shapes sound)
    eartraining/ EarTrainingVisit (+ Tally), QuestionSource, NoteQuestions, IntervalQuestions, ChordTypeQuestions,
                NoteChoice, Interval, IntervalDirection, NoteMatch (Ear training's seeded questions and answer checks)
    intonation/ Intonation (+ IntonationResult, IntonationVerdict), SettledPitch, SaddleMove: 12th fret against the
                open string
    buffer/     FrameAssembler
core/dsp/src/testFixtures/…/core/dsp/testsignals/
                TestSignals (sine, saw, square, weak fundamental, pluck, noise, hum, mix, band-limit)

core/audio/src/main/kotlin/io/github/klaboukes/twangle/core/audio/
    AudioRecorder (interface), AndroidAudioRecorder, AudioConfig, AudioRecorderException,
    AudioSourceSelector (+ AudioInput), SampleRateSelector, InputDeviceSelector,
    ChunkReader (+ MicInput: busy, silenced and stuck mics), MicOpener (openFirst: rate fallback),
    BlockingTransfer (transferFully: read/write loops that fail instead of spinning), MicPermissionRules,
    TonePlayer (interface), AndroidTonePlayer, MetronomePlayer (interface, + MetronomeBeat),
    AndroidMetronomePlayer, DronePlayer (interface), AndroidDronePlayer,
    AudioOutput (shared AudioTrack helpers)
core/audio/src/testFixtures/…/core/audio/   FakeAudioRecorder, FakeTonePlayer, FakeMetronomePlayer, FakeDronePlayer

core/settings/src/main/kotlin/io/github/klaboukes/twangle/core/settings/
    AppSettings (+ TunerMode, InTunePrecision, ThemeMode, VisualStyle, MeterLook, WaveLook, …),
    SettingsRepository (interface), DataStoreSettingsRepository, CustomTuning, ToolSwitches (music tools shown)
core/settings/src/testFixtures/…/core/settings/   FakeSettingsRepository

core/designsystem/src/main/kotlin/io/github/klaboukes/twangle/core/designsystem/
    TwangleTheme (+ StyleTokens), StylePalettes, StyleTypography, StyleBackdrop, StyleMood,
    MusicSigns, NoteNameSpeech, SpokenReadingThrottle (+ PacedSpeech), FontCredits,
    PressFeedback (Modifier.pressFeedback, pressSink, PressIndication, PressTone), PressButtons (TwangleTextButton, TwangleButton, TwangleIconButton ...),
    SegmentedSwitch, InstrumentButton, StringChipShape, NavMark, FitColumn, OneLineAutoSize, ScaledUp, ScreenDensity,
    AnimationsOff, AppLanguage, LocalDigits
    dial/       DialGeometry, SoundRibbon, Strobe, WaveLooks, WaveMotion
    meter/      MeterReading, MeterLooks, MeterArt, MeterFeel, MeterStretch, LightArc, LightArcHeat,
                Pointers, Loupe, Arrows, LightBars, MeterFinish
    metronome/  TempoDialArt (+ dialDegrees, bpmAtTouch, isOnDialRing), BeatLightsArt

core/testing/src/main/kotlin/io/github/klaboukes/twangle/core/testing/
    SharedActivityComposeRule, SharedActivityTestRunner, Thorough (+ allIfThorough), EveryLanguage, WhollyShown,
    PhoneScreen, MenuPosition

feature/tuner/src/main/kotlin/io/github/klaboukes/twangle/feature/tuner/
    TunerViewModel, TunerUiState, TunerOptions, TunerInterpreter, TunerLayout, TunerScreen,
    PitchPipeline (+ PitchReading), TunerError, MicPermission, InTunePrecisionDetector (forPrecision), InTuneTickRule,
    InTuneGlowRule, TunerHint (+ TuningCue), SteadyHint, ReceptionTip (+ TipCue),
    SignalBars (+ SignalFigures),
    StrumCheck (StrumPhase, StrumString, StrumReading, StrumCheckInterpreter, StrumAnalysis),
    IntonationCheck, ChordName, PitchTrailFeed, PitchTrailDescription, ScreenAwake (+ ScreenAwakeRule)
    components/ MeterFrame, NoteDisplay, WaveView, ChromaticReadout, StringSelector (+ StringRowFit),
                InstrumentTuningPanel (+ TuningRow), MyTunings, PickerNames, ModeSwitch, InTuneColor, InTuneGlow, InTuneHaptic, ReceptionLook,
                SignalBarsButton, PermissionPrompt, StrumCheckView (StrumPanel, StrumStatus, StrumCheckToggle),
                IntonationView, ChordView, PitchTrailView, FocusModeButton, PillTint
    editor/     TuningEditorScreen (+ TuningEditor), TuningDraft (+ EDITOR_NOTE_RANGE, saveDraft), TuningEditorSaver
    tour/       TourHost + TourOverlay (dim, spotlight, balloon), TourTargets (+ tourTarget), TourPart, TourGeometry (pure)

feature/metronome/src/main/kotlin/io/github/klaboukes/twangle/feature/metronome/
    MetronomeScreen (+ MetronomeContent, BeatClock), MetronomeViewModel
    ListenPermission (rememberListenPermission)
    components/ TempoDial (with − / + in its corners), MetronomeControls (MeterPicker, StepButton, TempoRow with
    Sound, Tap tempo and Listen, StartStopButton), SoundParts (SoundButton, SoundPanel with the vibrate switch),
    ListenParts (ListenLine, ListenBalloon)

feature/practice/src/main/kotlin/io/github/klaboukes/twangle/feature/practice/
    PracticeTools (practiceToolsFor, practiceToolToOpen, PracticeTestTags), PracticeToolButton, PracticeToolIcons,
    ChordsScreen (root and type pickers, chart, ‹ › pager), ChordPicker (RootPicker, ChordTypePicker),
    ChordChart (+ ChartGeometry), ChordSpeech (chordWords, spokenChart),
    ScaleNeck, ScaleSpeech, DroneButton, TuningLine, PracticeLayout (practiceLayoutFor),
    CapoScreen (shapes list, capo ‹ ›, the shape → sounds table), CapoTable (CAPO_SHAPES, capoChordsOf, capoPlanOf)

feature/eartraining/src/main/kotlin/io/github/klaboukes/twangle/feature/eartraining/
    EarTrainingScreen (+ EarTrainingContent, EarTrainingTestTags), EarTrainingViewModel (+ EarTrainingState), Exercise,
    PitchListener (+ HeardFrame), ChoicesPanel, EarMicPermission (rememberEarMicPermission), EarTrainingIcons

feature/settings/src/main/kotlin/io/github/klaboukes/twangle/feature/settings/
    SettingsScreen (+ SettingsContent), AboutScreen, SettingsViewModel, AppLanguages, PlayPage
    components/ A4Calibration, RadioGroupSetting, SwitchSetting, LinkSetting, AboutSection, Credits,
                LookCardGrid (+ LookDemo), StylePicker, MeterLookPicker (+ MeterDemo), WaveLookPicker, LanguageSetting

app/src/main/kotlin/io/github/klaboukes/twangle/app/
    TwangleApplication, AppContainer, MainActivity (+ PracticeTab), FocusMode, FirstStartTour, SystemBars,
    BuildToolSwitches (toolSwitchesFor), TunerPitchListener (Ear training's ears: the tuner's PitchPipeline),
    SyntheticAudioRecorder (benchmark build only)
    navigation/ Navigation (Screen, Destination, back, useRail), NavigationBar (AppNavigationFrame, bar, rail), NavIcons
    widget/     TuningWidget (RemoteViews, colours, tap), TuningWidgetReceiver, WidgetState

wear/src/main/kotlin/io/github/klaboukes/twangle/wear/
    WatchActivity, WatchTunerViewModel, WatchPitchPipeline, WatchTunerState (+ WatchTunerError),
    WatchTunerScreen (WatchTunerApp, WatchTuner, WatchDial, WatchNoteName), WatchTheme (WatchColors),
    ScreenState (ScreenOnEffect), MicPermission (rememberWatchMicPermission)

baselineprofile/src/main/kotlin/io/github/klaboukes/twangle/baselineprofile/
    BaselineProfileGenerator, StartupBenchmark, MeterFramesBenchmark, TunerJourney
```

## 9. Privacy
Audio is processed in memory and thrown away immediately. Nothing is recorded to disk or sent over the network, and there is no `INTERNET` permission. The merged manifest holds only `RECORD_AUDIO`, `VIBRATE` (in-tune tick, metronome beat) and AndroidX's internal `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. The home-screen widget ([Metronome, widget and watch](docs/architecture/ui-other-screens.md)) adds none: it shows the saved instrument and tuning on the home screen and holds no mic. The watch app (`:wear`) asks only for `RECORD_AUDIO`, has no `INTERNET` either and stores only whether the microphone was ever refused.

The privacy policy is [PRIVACY.md](PRIVACY.md), with a Dutch copy in [PRIVACY.nl.md](PRIVACY.nl.md); keep the two in sync. Settings stay in DataStore on the device, and because `android:allowBackup="true"` (no extraction rules) Android's own backup and device transfer may include them. If a change touches permissions, stored data, backup or anything that leaves the device, update the policy (and its effective date) in the same change. The About page shows the gist; the data-safety form will say no data is collected or shared.

**The public copy** (Phase 23) is what Play links to: https://klaboukes.github.io/twangle-privacy/ (Dutch at `nl.html`), GitHub Pages of the small public repo `Klaboukes/twangle-privacy`, because this repo stays private until after release and Pages needs a public repo on the free plan. That repo holds `index.md` and `nl.md` (copies of the two policies with their links rewritten), `_layouts/default.html` (the page frame, from `website/privacy-layout.html`: the GitHub Pages theme's own frame loaded a script from cdnjs.cloudflare.com, and the policy's page keeps the website's promise of nothing from another site, Phase 54), a `_config.yml`, a README and the closed testers' invitation page (`test/`). After a policy change has landed on `main`, run `scripts/publish-privacy.sh`: it copies the policies and the frame, and commits and pushes only if something changed. Commits there use GitHub's no-reply address, since the repo is public. The URL stays the same when this repo goes public later.
