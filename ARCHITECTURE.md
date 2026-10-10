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
| Persistence | **Jetpack DataStore (Preferences)** (Phase 4) | Stores settings: A4 reference, selected tuning, notation, theme. |
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
:core:audio        Microphone capture: AudioRecord wrapper exposing Flow<FloatArray>, permission helpers
:core:dsp          Pure Kotlin/JVM (NO Android deps): pitch detection, note math, tunings, smoothing
```

Dependency graph (arrows = "depends on"):

```
            :app
           /    \
 :feature:tuner  |
     |     \     |
     |   :core:audio
     |      /
   :core:dsp
```

Rules:
- `:core:dsp` is a plain `kotlin("jvm")` module. It must never import `android.*`. This keeps the most important code fast to test and reusable.
- `:core:audio` knows about Android audio but knows nothing about notes or UI.
- `:feature:*` modules never depend on each other.
- Only `:app` wires concrete implementations together (`AppContainer`).

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
 PitchDetector (YIN)   difference fn → CMNDF → absolute threshold → parabolic interpolation
    │                  → PitchResult(frequencyHz, confidence) or null
    ▼
 PitchSmoother         median of last N estimates + outlier/octave-jump rejection + EMA on cents
    ▼
 NoteMapper            Hz → (note, octave, cents) given A4; in Auto mode → nearest string in tuning
    ▼
 TunerViewModel        combine with settings → StateFlow<TunerUiState>
    ▼
 Compose UI            needle gauge, note name, cents, string row, in-tune indicator + haptic
```

### Key numbers
- **Sample rate**: 48 000 Hz. This is the device's native rate on most phones, which avoids resampling. Read it from `AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE` and fall back to 44 100.
- **Analysis window**: 4096 samples, or about 85 ms. Low E (82.41 Hz) has a 12.1 ms period, so the window holds about 7 periods. That is enough for YIN, which needs at least 2. Bass and 7-string support (down to ~30 Hz) will need 8192 samples.
- **Hop**: 1024 samples, or about 21 ms. That gives ~47 pitch estimates per second, which the smoother turns into a calm needle.
- **Detection range**: 60 Hz to 1500 Hz for the default guitar profile. This sets YIN's τ range (τ_max = sampleRate / f_min).
- **YIN threshold**: 0.10–0.15 (tunable). Below it, the first CMNDF dip is accepted as the period.
- **Accuracy target**: ±1 cent on synthetic test signals and ±3 cents on recorded guitar fixtures.
- **"In tune"**: |cents| ≤ 5 held for at least 300 ms. Then show a green state and trigger one haptic tick.

### Note math
```
midi  = 69 + 12 · log2(f / A4)        (A4 default 440 Hz, configurable 415–466)
note  = ceil(midi − 0.5)               (round half down, so exactly half-way reads +50)
cents = 100 · (midi − note)            range (−50, +50]
```
Octaves use scientific pitch notation (MIDI 60 = C4). `Note` is a value class over the MIDI number, so passing notes through the pipeline doesn't allocate.
In **Auto (guitar) mode** the target is the closest string of the selected tuning, measured in cents, not Hz. In **Chromatic mode** the target is the nearest equal-tempered note.

### Audio source selection
1. Use `MediaRecorder.AudioSource.UNPROCESSED` if `AudioManager.getProperty(PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)` is `"true"`.
2. Otherwise use `VOICE_RECOGNITION`. It is required to have AGC and noise suppression turned off.
3. Never use `MIC` or `VOICE_COMMUNICATION`. Their AGC and noise suppression distort pitch and level.

## 4. Threading & performance
- `AudioRecord.read()` blocks, so it runs on a dedicated single-thread dispatcher (`Dispatchers.IO.limitedParallelism(1)`). That keeps it from starving other coroutines.
- DSP runs on `Dispatchers.Default`.
- **No allocations in the per-frame loop.** The ring buffer, YIN scratch arrays and the window buffer are allocated once and reused. Per-frame garbage at ~47 Hz causes GC jank.
- The YIN difference function starts as the direct O(N·τ_max) form, roughly 1.6 M multiply-adds per frame. If profiling shows a hotspot, switch to the FFT-based autocorrelation form, which is O(N log N). The `PitchDetector` interface stays the same.
- UI updates go through one `StateFlow<TunerUiState>`. Compose only recomposes what changed. The needle animates with `animateFloatAsState` so it moves smoothly between estimates.

## 5. Lifecycle & permissions
- `RECORD_AUDIO` is requested at runtime from the tuner screen. Three states are handled: not yet asked, denied (show rationale, ask again), and permanently denied (deep-link to app settings).
- The microphone is open **only while the tuner screen is visible**. The ViewModel shares the pipeline with `SharingStarted.WhileSubscribed(5_000)`. The UI collects with `collectAsStateWithLifecycle()`, which stops in the background, so `AudioRecord` is released and the privacy indicator turns off.
- There is no foreground service. Background microphone capture is neither needed nor allowed for this use case.
- If another app holds the mic, or `AudioRecord` fails to initialize, the UI shows a recoverable error state.

## 6. UI state model

```kotlin
sealed interface TunerUiState {
    data object PermissionRequired : TunerUiState
    data class  Error(val reason: TunerError) : TunerUiState
    data class  Listening(val mode: TunerMode, val tuning: Tuning) : TunerUiState   // no usable signal
    data class  Detected(
        val mode: TunerMode,
        val tuning: Tuning,
        val noteName: String,          // e.g. "E"
        val octave: Int,               // e.g. 2
        val frequencyHz: Float,
        val cents: Float,              // smoothed, −50..+50
        val targetString: Int?,        // index into tuning, Auto mode only
        val inTune: Boolean,
    ) : TunerUiState
}
```

## 7. Package layout (per module)

```
core/dsp/src/main/kotlin/com/intune/core/dsp/
    pitch/      PitchDetector (interface), YinPitchDetector, PitchResult, SignalGate
    music/      Note, NoteMapper, Tuning, Tunings (presets), Notation
    smoothing/  PitchSmoother, MedianFilter, ExponentialSmoother
    buffer/     FrameAssembler (ring buffer)

core/audio/src/main/kotlin/com/intune/core/audio/
    AudioRecorder (interface), AndroidAudioRecorder, AudioConfig, AudioSourceSelector

feature/tuner/src/main/kotlin/com/intune/feature/tuner/
    TunerViewModel, TunerUiState, TunerScreen
    components/  NeedleGauge, NoteDisplay, StringSelector, PermissionPrompt

app/src/main/kotlin/com/intune/app/
    InTuneApplication, AppContainer, MainActivity, ui/theme/
```

## 8. Testing strategy
| Layer | How |
|---|---|
| `:core:dsp` | JVM unit tests with signals synthesized in tests: pure sines, sawtooth/square waves (rich harmonics), a weak fundamental with strong 2nd harmonic, added white noise, and decaying plucks. Assert ±1 cent. Recorded WAV fixtures go in `src/test/resources/`. |
| `:core:audio` | A `FakeAudioRecorder` emits fixture or synthetic frames, so everything downstream can be tested without a device. |
| `:feature:tuner` | ViewModel tests with `kotlinx-coroutines-test` and the fake recorder. Compose UI tests for each `TunerUiState`. |
| Device | Manual checklist per release: real guitar, noisy room, Bluetooth headset connected, permission denial paths. |

## 9. Privacy
Audio is processed in memory and thrown away immediately. Nothing is recorded to disk or sent over the network, and the app requests no `INTERNET` permission. The Play Store data-safety form and privacy policy will say so.
