# InTune — Architecture

InTune is a chromatic and guitar-string tuner for Android. It listens to the microphone, finds the fundamental frequency of the note being played, and shows how far that note is from the target pitch, in cents.

## 1. Tech stack

| Concern | Choice | Why |
|---|---|---|
| Language | **Kotlin 2.x** | First-class Android language, null safety, coroutines. |
| UI | **Jetpack Compose + Material 3** | Declarative UI fits a screen that redraws ~30–60×/s from a single state object. Custom gauge/needle drawing is easy with `Canvas`. |
| Audio capture | **`android.media.AudioRecord`** (PCM float, mono) | Gives raw samples with no extra dependencies. A tuner can tolerate ~50–100 ms of latency, so Oboe/AAudio through the NDK isn't needed. |
| Pitch detection | **YIN algorithm in pure Kotlin** (`:core:dsp`) | Accurate to under a cent on monophonic instrument signals, and resistant to octave errors. Pure Kotlin keeps it unit-testable on the JVM with no device. |
| Async | **Kotlin Coroutines + Flow / StateFlow** | Audio frames → pitch → UI state is naturally a stream. |
| State holder | **AndroidX ViewModel** | Survives configuration changes and owns the audio pipeline's lifetime. |
| DI | **Manual (`AppContainer`)** | The object graph is tiny. Hilt would cost build time and add complexity without much benefit. Revisit if the graph grows. |
| Persistence | **Jetpack DataStore (Preferences)** (`:core:settings`) | Stores settings: A4 reference, selected tuning, notation, theme, keep screen on. |
| Build | **Gradle 9 + AGP 9 (built-in Kotlin), Kotlin DSL + version catalog** (`gradle/libs.versions.toml`) | Standard for modern Android. Versions live in one place. |
| Testing | JUnit 4 (JVM), kotlinx-coroutines-test, Compose UI test | DSP is checked with synthesized signals. UI is tested with Compose test rules. |
| CI | **GitHub Actions** (`.github/workflows/android.yml`) | test + lint + assembleDebug on every PR. The debug APK is uploaded as an artifact. |
| Min / target SDK | **minSdk 26** (Android 8.0) / targetSdk 37 | `ENCODING_PCM_FLOAT` and the `UNPROCESSED` audio source work reliably from API 24+. 26 covers about 97% of active devices. minSdk 26 also means adaptive icons need no PNG fallbacks. |

### Rejected alternatives
- **Flutter / React Native**: microphone DSP would go through platform channels, which adds latency and complexity for a single-platform app.
- **C++/Oboe via NDK**: lower latency, but a tuner doesn't need it. `PitchDetector` is an interface, so a native version can replace the Kotlin one if profiling shows a need.
- **FFT peak picking**: easily fooled by strong harmonics (guitar low E often has a weaker fundamental than its 2nd harmonic), and poor resolution at low frequencies. Time-domain YIN handles both better.

## 2. Module structure

```
:app               Android application: Activity, Application, AppContainer (DI), theme, navigation
:feature:tuner     Tuner screen: Compose UI, TunerViewModel, UI state, gauge components
:feature:settings  Settings screen: A4 calibration and the other preferences, SettingsViewModel
:core:audio        Microphone capture: AudioRecord wrapper exposing Flow<FloatArray>, permission helpers
:core:settings     Saved user settings: SettingsRepository backed by DataStore
:core:dsp          Pure Kotlin/JVM (NO Android deps): pitch detection, note math, tunings, smoothing
```

Dependencies ("→" = depends on):

```
:app               → every module below (it wires them together)
:feature:tuner     → :core:audio, :core:settings, :core:dsp
:feature:settings  → :core:settings (and through it :core:dsp)
:core:settings     → :core:dsp
:core:audio        → no other module
:core:dsp          → no other module
```

Rules:
- `:core:dsp` is a plain `kotlin("jvm")` module. It must never import `android.*`. This keeps the most important code fast to test and reusable.
- `:core:audio` knows about Android audio but knows nothing about notes or UI.
- `:core:settings` knows how settings are saved, and nothing about audio or UI. It uses `Tuning` and `Notation` from `:core:dsp`.
- `:feature:*` modules never depend on each other.
- Only `:app` wires concrete implementations together (`AppContainer`) and switches between screens (`MainActivity`).

## 3. Signal pipeline

```
 Microphone
    │  AudioRecord.read()  (dedicated thread, 48 kHz mono float, blocking)
    ▼
 AudioRecorder ──Flow<FloatArray>(hop = 1024 samples)──►
    ▼
 FrameAssembler        ring buffer → overlapping analysis window (4096 samples ≈ 85 ms)
    ▼
 SignalGate            RMS level check (< ~-50 dBFS → "no signal"), DC offset removal
    ▼
 PitchDetector (YIN)   4 kHz low-pass → difference fn → CMNDF → absolute threshold → least-squares
    │                  refinement → PitchResult(frequencyHz, confidence), or PitchResult.NONE
    ▼
 PitchSmoother         Hz → fractional MIDI (NoteMapper.midiOf, honours A4) → octave-jump rejection
    │                  → median of 5 → EMA (snaps on a new note) → note with ±15-cent hysteresis
    ▼
 NoteMapper / Tuning   note name + octave; in Auto mode → nearest string in tuning, cents from it
    ▼
 TunerViewModel        combine with settings → StateFlow<TunerUiState>
    ▼
 Compose UI            needle gauge, note name, cents, string row, in-tune indicator + haptic
```

### Key numbers
- **Sample rate**: 48 000 Hz. This is the device's native rate on most phones, which avoids resampling. `SampleRateSelector` reads `AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE` and uses it if it is between 44 100 and 48 000 Hz (the DSP window sizes assume that range), then tries 48 000 and 44 100. The first rate `AudioRecord.getMinBufferSize` accepts wins.
- **Analysis window**: 4096 samples, or about 85 ms. Low E (82.41 Hz) has a 12.1 ms period, so the window holds about 7 periods. That is enough for YIN, which needs at least 2. Bass and 7-string support (down to ~30 Hz) will need 8192 samples.
- **Hop**: 1024 samples, or about 21 ms. That gives ~47 pitch estimates per second, which the smoother turns into a calm needle.
- **Detection range**: 60 Hz to 1500 Hz for the default guitar profile. This sets YIN's τ range (τ_max = sampleRate / f_min).
- **YIN threshold**: 0.15 (tunable; the paper suggests 0.10–0.15). The first CMNDF dip below it is accepted as the period.
- **Accuracy target**: ±1 cent on synthetic test signals and ±3 cents on recorded guitar fixtures.
- **"In tune"**: |cents| ≤ 5 held for at least 300 ms (counted in frames: 15 hops at 48 kHz). It then stays in tune until the reading drifts past 7 cents, so a note hovering at 5 cents doesn't flicker. Show a green state (and, in Phase 5, one haptic tick).

### Frame assembly
`FrameAssembler` keeps a 4096-sample ring buffer. Callers `push()` audio chunks of any size. Each call consumes samples until a frame is ready, then stops so no frame is skipped. `readFrame(out)` copies the window oldest-first into a caller-owned array. The first frame arrives after one full window, then one every hop (1024). The ring and the output window are allocated once.

### Signal gate
`SignalGate.process(frame)` removes the DC offset in place (it subtracts the mean), then measures the RMS level in dBFS as `20 · log10(rms)`, so a full-scale square wave reads 0 dBFS and a full-scale sine about −3 dBFS. Silence is floored at −120 dBFS. The gate opens at **−50 dBFS** and closes only below **−56 dBFS** (6 dB hysteresis), so a decaying note doesn't flicker on and off. Frames are only sent to the pitch detector while the gate is open.

### Pitch detection (YIN)
`YinPitchDetector` follows de Cheveigné & Kawahara (2002), steps 2–5, with two additions. Both were added because measurements on synthetic signals missed the ±1 cent target without them.
1. **Low-pass pre-filter.** A 4th-order Butterworth at 4 kHz (two RBJ-cookbook biquads) runs into a scratch buffer, and its ~92-sample start-up transient is skipped. Without it, very bright tones (full-band sawtooth/square near 1.4 kHz) give a V-shaped dip that 3-point parabolic interpolation misjudges by ~2.5 cents. The filter also removes hiss above 4 kHz.
2. **Adaptive least-squares refinement.** Instead of a fixed 3-point parabola, a cubic is fitted to the raw difference function over the lags `τ ± k` that lie within 5% of the way from the dip to the mean (`k` ≤ τ/16). Sharp dips use k = 1, which is the classic parabola. Broad dips (pure low notes) average over dozens of lags. With white noise at 20 dB SNR this cut the worst low-E error from ~23 cents to under 1. The cubic term removes a ~0.3-cent bias that a wide parabola picks up from the fixed integration window.

Search range τ = 32…800 (1500…60 Hz) plus 51 extra lags for the fit. The integration window is `W = 4096 − 92 − 851 = 3153` samples. Confidence = 1 − d′(τ). `PitchResult` is a value class that packs two floats into a `Long`, and `PitchResult.NONE` means "no pitch". It is never nullable, so returning it doesn't box. Measured on synthetic signals: worst error ≈ 0.1 cent for sines, ≈ 0.5 cent for saw/square/pluck/weak-fundamental, < 1 cent at 20 dB SNR, < 5 cents at 10 dB SNR. There are no octave errors. Allocation-free `detect()` is checked by a test that counts the bytes allocated.

### Smoothing
`PitchSmoother` works on fractional MIDI numbers, so cents are just ×100 and A4 calibration is already applied. Each frame passes through four stages:
1. **Octave-jump rejection.** A reading a whole number of octaves (±30 cents) away from the running median is dropped. If it holds for 3 frames in a row (~64 ms), it's a real octave change: the filters restart there.
2. **Median of the last 5** (~107 ms) removes other single-frame outliers.
3. **EMA, α = 0.3,** settles a step within about 10 frames. If the median is more than 50 cents from the EMA (a new string was plucked), the EMA snaps to it instead of sliding the needle across.
4. **Note hysteresis.** The shown note only changes once the reading is more than 65 cents (50 + 15) from it, so the displayed cents can reach ±65.

Frames without a pitch call `noReading()`. Up to 4 missed frames (~85 ms) keep the last reading; after that it is cleared. `MedianFilter` and `ExponentialSmoother` are reusable building blocks. Everything is allocation-free.

### Note math
```
midi  = 69 + 12 · log2(f / A4)        (A4 default 440 Hz, configurable 415–466)
note  = ceil(midi − 0.5)               (round half down, so exactly half-way reads +50)
cents = 100 · (midi − note)            range (−50, +50]
```
Octaves use scientific pitch notation (MIDI 60 = C4). `Note` is a value class over the MIDI number, so passing notes through the pipeline doesn't allocate.
In **Auto (guitar) mode** the target is the closest string of the selected tuning, measured in cents, not Hz. In **Chromatic mode** the target is the nearest equal-tempered note.

### Tunings
`Tuning(id, strings)` lists open-string `Note`s from lowest to highest (index 0 = string 6 on a guitar). `id` is the stable key saved in settings. User-facing names live in the app's string resources, so `:core:dsp` stays free of UI text. `Tunings` holds the presets (Standard, Drop D, Half-step down, Full-step down, DADGAD, Open G, Open D, Open E), with `Tunings.DEFAULT` = Standard and `byId()` for restoring a saved choice.
- `nearestStringIndex(midi)` takes the fractional MIDI from `NoteMapper.midiOf`, so it follows A4 calibration. It picks the string with the smallest |Δ| in semitones (equivalently cents), and ties go to the lower string. No allocation: it loops over a precomputed `IntArray`.
- `centsFromString(midi, index)` = 100 · (midi − string midi). It is **not** wrapped to ±50, so a string tuned far off still shows the full distance to go.

### Audio source selection
1. Use `MediaRecorder.AudioSource.UNPROCESSED` if `AudioManager.getProperty(PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)` is `"true"`.
2. Otherwise use `VOICE_RECOGNITION`. It is required to have AGC and noise suppression turned off.
3. Never use `MIC` or `VOICE_COMMUNICATION`. Their AGC and noise suppression distort pitch and level.

`AudioSourceSelector.candidates()` returns the inputs to try in that order (`AudioInput` lists only these two). `AndroidAudioRecorder` opens the first one whose `AudioRecord` initializes, so a device that claims `UNPROCESSED` support but fails to open it still falls back to `VOICE_RECOGNITION`.

### Audio capture
`AudioRecorder.chunks()` is a cold `Flow<FloatArray>`. The mic opens when collection starts and is released when it stops. `AndroidAudioRecorder` builds an `AudioRecord` (mono, `ENCODING_PCM_FLOAT`, buffer ≥ 4 chunks) and reads 1024-sample chunks with `READ_BLOCKING` on its own single-thread dispatcher. To avoid ~47 new arrays a second it fills a **pool of 4 arrays** in turn, behind a channel of capacity 2. The collector holds one chunk, the channel up to two and the reader fills the fourth, so a chunk is never overwritten while it's in use. The catch: **a chunk is only valid until the collector takes the next one.** Downstream code copies samples out (into `FrameAssembler`) inside `collect` and must not `buffer()` the chunks themselves.

`PitchPipeline` (in `:feature:tuner`, because it joins `:core:audio` and `:core:dsp`) wires the stages above: it collects the chunks, pushes them into a `FrameAssembler`, then runs `SignalGate` and `YinPitchDetector` (built with the recorder's sample rate) on `Dispatchers.Default`, emitting one raw `PitchReading(frequencyHz, confidence, levelDbfs, gateOpen)` per hop. The DSP objects and frame buffer are created once per collection. Only the small immutable reading for the UI is new each frame.

`TunerViewModel` builds on it. For each listening session it creates a `TunerInterpreter` that feeds every reading to `PitchSmoother` (`noReading()` when there's no pitch) and turns the smoothed MIDI value into a `TunerUiState`. That runs on `Dispatchers.Default`, in the same coroutine as the detector. The mode switch and the saved settings are combined into one immutable `TunerOptions` (mode, tuning, A4), rebuilt only when one of them changes and read on every frame. Listening waits until the settings have loaded, so it never starts with the wrong tuning. Switching modes or changing a setting takes effect on the next frame (~21 ms) without reopening the mic or losing the smoothing history. A new A4 is the exception: it restarts the smoother, because the smoothed notes were measured against the old A4.
- **Chromatic:** the smoother's note (with its ±15-cent hysteresis) and cents.
- **Auto:** the closest string (`nearestStringIndex`) and `centsFromString`, not wrapped. The target string is sticky: it only changes once another string is closer by more than 15 cents, so a note half-way between two strings doesn't flicker.
- The frequency shown is the smoothed one (`NoteMapper.frequencyOf(smoother.midi)`), not the raw reading.
- `InTuneDetector` sets `Detected.inTune` from the smoothed cents and the frame duration (`PitchPipeline.frameSeconds` = hop / sample rate). It restarts when the note stops or the target note changes, so a new string always has to earn "in tune".

Failures end the flow with a typed `AudioRecorderException`, which the ViewModel maps to a recoverable error state:
- `PermissionMissing`: `RECORD_AUDIO` isn't granted (checked before opening, and on `SecurityException`).
- `InitializationFailed`: no input could be opened at the chosen rate.
- `MicrophoneBusy`: another app holds the mic. Before Android 10, `startRecording()` fails or the record never reaches `RECORDSTATE_RECORDING`. From Android 10 the mic is shared and a higher-priority app (such as a phone call) silences us with zeros instead, so `isClientSilenced` is checked after the first chunk. Silencing that starts mid-session just looks like silence (the gate closes) and recording resumes when the other app lets go.
- `ReadFailed(code)`: `read()` returned an error, e.g. `ERROR_DEAD_OBJECT` when the audio server restarts. Collecting again reopens the mic.

`Throwable.toTunerError()` maps these to `TunerError` (`MIC_PERMISSION_MISSING`, `MIC_BUSY`, `MIC_UNAVAILABLE`, `MIC_STOPPED`). The UI shows a message and a "Try again" button that restarts collection.

**Measured on the Galaxy S26 (Android 16):** the device doesn't offer `UNPROCESSED`, so the recorder uses `VOICE_RECOGNITION` at 48 kHz. `dumpsys audio` shows no effects attached and `silenced:false`. A 440 Hz tone from a PC speaker read 439.98 Hz (−0.1 cents) at about −31 dBFS, and quiet room noise sits around −52 to −56 dBFS, right at the gate threshold.

## 4. Threading & performance
- `AudioRecord.read()` blocks, so it runs on a dedicated single-thread dispatcher (`Dispatchers.IO.limitedParallelism(1)`). That keeps it from starving other coroutines.
- DSP runs on `Dispatchers.Default`.
- **No allocations in the per-frame loop.** The ring buffer, YIN scratch arrays and the window buffer are allocated once and reused. Per-frame garbage at ~47 Hz causes GC jank.
- The YIN difference function starts as the direct O(N·τ_max) form, roughly 2.7 M multiply-adds per frame (3153 × 851 lags), using four independent accumulators so the additions overlap. `./gradlew :core:dsp:benchmark` (the `*Benchmark` tests, kept out of `test` and CI because timings depend on the machine) measured a median of **1.1 ms per frame** on the JVM, against a 2 ms budget. Phones are slower, so check again on the device in Phase 5. If profiling shows a hotspot, switch to the FFT-based autocorrelation form, which is O(N log N). The `PitchDetector` interface stays the same.
- UI updates go through one `StateFlow<TunerUiState>`. Compose only recomposes what changed. The needle animates with `animateFloatAsState` (a spring, damping 0.8) so it moves smoothly between estimates. `NeedleGauge` reads the animated angle only inside its `Canvas` draw block, so the ~47 updates a second redraw the gauge without recomposing it.

## 5. Lifecycle & permissions
- `RECORD_AUDIO` is requested at runtime from the tuner screen. `rememberMicPermissionState()` tracks a `MicPermissionStatus` (rules in the pure, unit-tested `MicPermissionRules`) and `PermissionPrompt` shows one action per state:
  - `NOT_REQUESTED`: asked automatically once when the screen first appears. The prompt explains why and offers "Allow microphone".
  - `DENIED` (refused, `shouldShowRequestPermissionRationale` is true): rationale text, ask again.
  - `PERMANENTLY_DENIED` (refused and no rationale, so Android won't show the dialog again): "Open settings" deep-links to the app's page (`ACTION_APPLICATION_DETAILS_SETTINGS`). Dismissing the very first dialog by tapping outside looks identical to Android, so it lands here too. Settings still fixes it.
  - `GRANTED`: the tuner. The status is checked again on every `ON_RESUME`, so a grant made in Settings is picked up on return. Revoking in Settings kills the process, so the app starts fresh.
  - The status survives rotation (`rememberSaveable`). It isn't persisted: on a fresh start, an earlier permanent denial shows as `NOT_REQUESTED` until the automatic request comes back denied without a dialog.
- The microphone is open **only while the tuner screen is visible**. The ViewModel shares the pipeline with `SharingStarted.WhileSubscribed(5_000)`. The UI collects with `collectAsStateWithLifecycle()`, which stops in the background, so `AudioRecord` is released and the privacy indicator turns off.
- `MicLifecycleTest` (`:app` instrumented test, needs a device) checks this against Android's own list of active recordings (`AudioManager.activeRecordingConfigurations`): open while the tuner is shown, still open 3 s after `onStop`, released within about 5 s, reopened on return, released when the activity closes. Home, the lock screen and screen-off all go through `onStop`, so they follow the same path.
- There is no foreground service. Background microphone capture is neither needed nor allowed for this use case.
- If another app holds the mic, or `AudioRecord` fails to initialize, the UI shows a recoverable error state.

### Settings
The gear button in the tuner's top corner opens the settings screen (`:feature:settings`). `MainActivity` switches between the two screens with one saved `Screen` value, so the app needs no navigation library. A `SaveableStateHolder` keeps each screen's saved state while the other one is shown, and the back button returns from settings. While settings are open the tuner isn't visible, so the mic is released after the usual 5 s. `SettingsViewModel` shows the saved settings and saves every change straight away; there is no "save" button.
- **A4 calibration:** a slider over 415–466 Hz with one stop per Hz, − and + buttons for single steps, and "Reset to 440 Hz". A drag is saved once the finger lifts. The tuner shows the reference in use as "A4 = 442 Hz" under the mode switch.

`SettingsRepository` (`:core:settings`) exposes `settings: Flow<AppSettings>` plus one suspend setter per value. `AppSettings` holds `a4Hz` (default 440), `tuning` (default Standard), `notation` (`Notation.SHARPS` / `FLATS` / `SOLFEGE`), `theme` (`ThemeMode.SYSTEM` / `LIGHT` / `DARK`), `dynamicColor` (true) and `keepScreenOn` (true).
- `DataStoreSettingsRepository` stores them with DataStore Preferences in `files/datastore/settings.preferences_pb`. `AppContainer` creates the only instance, because DataStore allows one per file. The tuning is saved by its `id` and enums by their `name`, so those must never be renamed.
- Reading is forgiving. A missing, unknown or out-of-range value falls back to its default: a tuning id that no longer exists becomes Standard, and A4 is clamped to 415–466 Hz and rounded to whole Hz (`AppSettings.normalizeA4`, which the setter applies too). An unreadable file gives all defaults, and a corrupt one is replaced with defaults so saving works again.
- `FakeSettingsRepository` (in the `:core:settings` `testFixtures`) keeps settings in memory for ViewModel tests.

## 6. UI state model

```kotlin
enum class TunerMode { AUTO, CHROMATIC }

sealed interface TunerUiState {
    data object PermissionRequired : TunerUiState
    data class  Error(val reason: TunerError) : TunerUiState
    data class  Listening(val mode: TunerMode, val tuning: Tuning, val a4Hz: Float) : TunerUiState   // no usable signal
    data class  Detected(
        val mode: TunerMode,
        val tuning: Tuning,
        val a4Hz: Float,               // concert pitch in use (A4 calibration)
        val noteName: String,          // target note, e.g. "E"
        val octave: Int,               // e.g. 2
        val frequencyHz: Float,        // smoothed
        val cents: Float,              // smoothed; Auto: unwrapped distance to the string, Chromatic: about ±65
        val targetString: Int?,        // index into tuning, Auto mode only
        val inTune: Boolean,           // ≤ 5 cents for ≥ 300 ms, released past 7 cents
    ) : TunerUiState
}
```

`TunerViewModel` exposes it as `state: StateFlow<TunerUiState>`, plus `mode: StateFlow<TunerMode>` so the mode switch reacts instantly. Its inputs are `onPermissionChanged(granted)`, `setMode(mode)` and `retry()`. The permission status itself lives in the UI (`rememberMicPermissionState`), which decides between the permission prompt and the tuner and passes "granted" on to the ViewModel. Until then the state is `PermissionRequired` and the pipeline isn't started, so the mic stays closed even while the screen is visible. Tuning and A4 come from `SettingsRepository`.

`TunerScreen` wires the ViewModel and shows the settings gear (also on the permission prompt); `TunerContent(state, onRetry)` is the stateless screen body used by previews and UI tests. It shows `NeedleGauge` (half circle, −50…+50 cents, a tick every 10; readings beyond ±50, such as a string tuned far off in Auto mode, pin the needle to the edge) above `NoteDisplay` (big note name with its octave, "12 cents flat" rounded to whole cents, and the smoothed frequency). While `Listening`, the needle rests faded in the centre and the text asks to play a string (Auto) or a note (Chromatic). Above the gauge, `ModeSwitch` (Material 3 segmented buttons "Guitar" / "Chromatic") reads `TunerViewModel.mode`. In guitar mode `StringSelector` shows the tuning's open strings (E₂ A₂ D₃ G₃ B₃ E₄) under the readout, with `targetString` highlighted and marked `selected` for accessibility. In chromatic mode its slot stays empty but keeps its height, so switching modes doesn't move the layout under the user's finger. While `inTune`, the needle, centre tick, note, cents text and highlighted string fade to a theme-aware green (`InTuneColor.kt`; Material 3 has no success role). A shape cue for colour-blind users follows in Phase 5.

## 7. Package layout (per module)

```
core/dsp/src/main/kotlin/com/intune/core/dsp/
    pitch/      PitchDetector (interface), PitchResult, YinPitchDetector, LowPassFilter, SignalGate
    music/      Note, NoteMapper, Tuning, Tunings (presets), Notation
    smoothing/  PitchSmoother, MedianFilter, ExponentialSmoother
    buffer/     FrameAssembler (ring buffer)

core/dsp/src/testFixtures/kotlin/com/intune/core/dsp/
    testsignals/ TestSignals (synthetic sine, saw, square, weak fundamental, pluck, noise)

core/audio/src/main/kotlin/com/intune/core/audio/
    AudioRecorder (interface), AndroidAudioRecorder, AudioConfig, AudioRecorderException,
    AudioSourceSelector (+ AudioInput), SampleRateSelector

core/audio/src/testFixtures/kotlin/com/intune/core/audio/
    FakeAudioRecorder

core/settings/src/main/kotlin/com/intune/core/settings/
    AppSettings (+ ThemeMode), SettingsRepository (interface), DataStoreSettingsRepository

core/settings/src/testFixtures/kotlin/com/intune/core/settings/
    FakeSettingsRepository

feature/tuner/src/main/kotlin/com/intune/feature/tuner/
    TunerViewModel, TunerUiState (+ TunerMode), TunerOptions, TunerInterpreter, InTuneDetector, TunerScreen, PitchPipeline (+ PitchReading), TunerError
    MicPermission (status, rules, rememberMicPermissionState)
    components/  NeedleGauge, NoteDisplay, StringSelector, ModeSwitch, InTuneColor, PermissionPrompt

feature/settings/src/main/kotlin/com/intune/feature/settings/
    SettingsScreen (+ SettingsContent), SettingsViewModel
    components/  A4Calibration

app/src/main/kotlin/com/intune/app/
    InTuneApplication, AppContainer, MainActivity, ui/theme/
```

## 8. Testing strategy
| Layer | How |
|---|---|
| `:core:dsp` | JVM unit tests with signals from `TestSignals` (in the `testFixtures` source set, so other modules' tests can use them via `testImplementation(testFixtures(project(":core:dsp")))`): pure sines, sawtooth/square waves (rich harmonics), a weak fundamental with strong 2nd harmonic, added white noise, and decaying plucks. Assert ±1 cent. Recorded WAV fixtures go in `src/test/resources/`. |
| `:core:audio` | JVM tests for the pure parts (source selection). Instrumented tests (`./gradlew :core:audio:connectedDebugAndroidTest`, device needed) record from the real mic: full chunks, pooled arrays, mic released after collection. `FakeAudioRecorder` (in `:core:audio`'s `testFixtures`, used via `testImplementation(testFixtures(project(":core:audio")))`) plays a `FloatArray`, typically from `TestSignals`, as chunks paced like a real mic (instant under `runTest` virtual time). It can loop, end with an `AudioRecorderException`, and reports `isRecording` / `startCount`, so everything downstream, including mic lifetime, can be tested without a device. Like the real recorder it reuses its chunk array. |
| `:core:settings` | JVM tests of `DataStoreSettingsRepository` on a real DataStore file in a temporary folder: defaults, saving, surviving a restart, clamping, and falling back on unknown or unreadable values. |
| `:feature:tuner` | ViewModel tests with `kotlinx-coroutines-test` and the fake recorder. Compose UI tests for each `TunerUiState`. |
| `:feature:settings` | `SettingsViewModel` tests with `FakeSettingsRepository`. Compose UI tests (`SettingsContentTest`, device needed) for each control. |
| `:app` | Instrumented `MicLifecycleTest`: the mic follows the screen's visibility (§5). |
| Device | Manual checklist per release: real guitar, noisy room, Bluetooth headset connected, permission denial paths. |

## 9. Privacy
Audio is processed in memory and thrown away immediately. Nothing is recorded to disk or sent over the network, and the app requests no `INTERNET` permission. The Play Store data-safety form and privacy policy will say so.
