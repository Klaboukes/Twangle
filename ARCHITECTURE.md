# Twangle — Architecture

Twangle is a chromatic and guitar-string tuner for Android. It listens to the microphone, finds the fundamental frequency of the note being played, and shows how far that note is from the target pitch, in cents.

This file holds the design decisions, the numbers that define behaviour and the reasons behind them. The code is the source of truth for everything else (exact geometry, colours, test lists). Keep it that way: when a decision changes, update the matching section here in the same change, and leave history to git and [ROADMAP-ARCHIVE.md](ROADMAP-ARCHIVE.md).

## Contents
1. [Tech stack](#1-tech-stack): name, app ID and icon · rejected alternatives
2. [Module structure](#2-module-structure)
3. [Signal pipeline](#3-signal-pipeline): key numbers · other sample rates · frame assembly · signal gate · noise floor, SNR and clarity · reception · tips · signal bars · YIN · smoothing · edge cases · note math · tunings · strobe model · audio source selection · audio capture
4. [Threading & performance](#4-threading--performance): measured on the Galaxy S26 · meter looks · Baseline Profile · release build
5. [Lifecycle & permissions](#5-lifecycle--permissions): settings · saved settings
6. [UI](#6-ui): UI state model · tuner screen · string-lock tip · display · meter · meter art language · wave view · strobe look · wave looks · visual styles · landscape and wide screens · accessibility · languages
7. [Package layout](#7-package-layout-per-module)
8. [Testing strategy](#8-testing-strategy): fast UI tests · quick and thorough UI runs
9. [Privacy](#9-privacy)

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
| Persistence | **DataStore (Preferences)** (`:core:settings`) | Saved settings (see §5). |
| Build | **Gradle 9 + AGP 9 (built-in Kotlin), Kotlin DSL + version catalog** | Versions live only in `gradle/libs.versions.toml`. |
| Testing | JUnit 4, kotlinx-coroutines-test, Compose UI test | DSP checked with synthesized signals (see §8). |
| CI | **GitHub Actions** (`.github/workflows/android.yml`) | test + lint + assembleDebug; debug APK uploaded as an artifact. |
| Min / target SDK | **26** (Android 8.0) / 37 | `ENCODING_PCM_FLOAT` and `UNPROCESSED` work from API 24+; 26 covers ~97 % of devices and needs no PNG icon fallbacks. |

### Name, app ID and icon
- **Name:** Twangle (InTune until October 2026, which clashed with Microsoft Intune). Names with "free" in them are out: Google Play bans price words in titles.
- **App ID:** `io.github.klaboukes.twangle`; packages and module namespaces live under it.
- **Launcher icon:** a dark-brown string on gold that comes in at an angle, makes one loop and leaves straight (tangled, then in tune). Adaptive icon (`res/drawable/ic_launcher_foreground.xml` + `ic_launcher_background`); the same foreground is the monochrome layer for themed icons. The artwork stays within 32.2 dp of the centre, inside the 33 dp safe zone, so no launcher mask clips it.
- **Opening screen:** `androidx.core:core-splashscreen` (`installSplashScreen()`, theme `Theme.Twangle.Starting`), so Android 8–11 look like 12+: the icon's string on a gold tile, on gold (light) or `#1C1510` (dark, `values-night`). It follows the phone's dark mode, because the app's theme setting isn't read yet, and stays up until the settings are read and the first frame is composed (`setKeepOnScreenCondition`).

### Rejected alternatives
- **Flutter / React Native:** microphone DSP through platform channels adds latency and complexity for a single-platform app.
- **C++/Oboe via NDK:** lower latency a tuner doesn't need. `PitchDetector` is an interface, so a native version can replace the Kotlin one if profiling ever asks for it.
- **FFT peak picking:** fooled by strong harmonics (low E often has a weaker fundamental than its 2nd harmonic) and coarse at low frequencies. Time-domain YIN handles both.

## 2. Module structure

```
:app               Android application: Activity, Application, AppContainer (DI), screen switching
:feature:tuner     Tuner screen: Compose UI, TunerViewModel, UI state, meter frame, wave view
:feature:settings  Settings and About screens, SettingsViewModel
:core:audio        Microphone capture: AudioRecord wrapper exposing Flow<FloatArray>
:core:settings     Saved user settings: SettingsRepository backed by DataStore
:core:designsystem Visual styles: TwangleTheme, palettes, fonts, backgrounds, style mood pictures, meter and wave looks
:core:dsp          Pure Kotlin/JVM (NO Android deps): pitch detection, levels, note math, tunings, smoothing
:core:testing      UI test helpers only (androidTestImplementation): shared-activity Compose rule and runner, @Thorough
:baselineprofile   Android test module: Baseline Profile generator, startup and meter-frame benchmarks
```

Dependencies ("→" = depends on):

```
:app               → every module below (it wires them together)
:feature:tuner     → :core:audio, :core:settings, :core:designsystem, :core:dsp
:feature:settings  → :core:settings, :core:designsystem (and through them :core:dsp)
:core:designsystem → :core:settings (VisualStyle, ThemeMode), :core:dsp (StrobeMotion for the wave)
:core:settings     → :core:dsp
:core:audio        → no other module
:core:dsp          → no other module
:core:testing      → no other module; used by the UI tests of :feature:tuner and :feature:settings
:baselineprofile   → drives :app on a device (targetProjectPath); nothing depends on it
```

Rules:
- `:core:dsp` is a plain `kotlin("jvm")` module and never imports `android.*`, so the most important code stays fast to test and portable.
- `:core:audio` knows Android audio, nothing about notes or UI. `:core:settings` knows how settings are saved, nothing about audio or UI.
- `:core:designsystem` holds everything both screens draw with (including the meter and wave art, so the settings previews draw exactly what the tuner draws), and nothing about audio.
- `:feature:*` modules never depend on each other. Only `:app` wires implementations together (`AppContainer`) and switches screens (`MainActivity`).
- `:baselineprofile` runs only on demand with a phone connected (§4). CI never runs it.

## 3. Signal pipeline

```
 Microphone
    │  AudioRecord.read()  (dedicated thread, 48 kHz mono float, blocking; built-in mic while a
    │                      Bluetooth headset is connected)
    ▼
 AudioRecorder ──Flow<FloatArray>(chunk = one hop ≈ 21 ms, 1024 samples at 48 kHz)──►
    ▼
 FrameAssembler        ring buffer → overlapping analysis window (≈ 85 ms, 4096 samples at 48 kHz)
    ▼
 SignalGate            RMS level check (< ~-50 dBFS → "no signal"), DC offset removal
    ▼
 PitchDetector (YIN)   [upsample to ≥ 32 kHz if the rate is lower] → 4 kHz low-pass → difference fn
    │                  → CMNDF → absolute threshold → whole-period checks → least-squares
    │                  refinement → PitchResult(frequencyHz, confidence), or PitchResult.NONE
    ▼
 NoiseFloorTracker     note-free frames' levels → median of 5 → minimum over 2 s = room's floor;
 + Clarity             with a pitch: SNR over that floor, clarity 0..1 from YIN's confidence
    ▼
 ReceptionTracker      level, SNR and clarity scored 0..1, each smoothed (fast up, slow down);
                       reception = the lowest of the three (needle and wave crisp ↔ faint)
    ▼
 PitchSmoother         Hz → fractional MIDI (NoteMapper.midiOf, honours A4) → octave-jump rejection
    │                  → median of 5 → EMA (snaps on a new note, smaller steps when unsure)
    │                  → note with ±15-cent hysteresis
    ▼
 NoteMapper / Tuning   note name + octave; in Auto mode → nearest string in tuning, cents from it
    ▼
 TunerViewModel        combine with settings → StateFlow<TunerUiState>
    ▼
 Compose UI            meter, note name, cents or wave, string row, in-tune indicator + haptic
```

### Key numbers
- **Sample rate:** 48 000 Hz, the native rate on most phones. `SampleRateSelector` uses `AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE` if it is 44 100–48 000 Hz (higher only costs CPU), then tries 48 000, 44 100, 32 000, 16 000 and 8 000; the first rate `AudioRecord.getMinBufferSize` accepts wins. Any rate from 8 to 96 kHz works end to end (see *Other sample rates*).
- **Analysis window:** 4096 samples (≈ 85 ms), about 7 periods of low E (82.41 Hz); YIN needs at least 2. Bass and 7-string (down to ~30 Hz) will need 8192.
- **Hop:** 1024 samples (≈ 21 ms), ~47 estimates per second.
- **Detection range:** 62–1500 Hz for guitar (τ_max = sampleRate / f_min). The floor sits above 60 Hz mains hum and below drop C (65.4 Hz).
- **YIN threshold:** 0.15 (the paper suggests 0.10–0.15).
- **Accuracy target:** ±1 cent on synthetic signals, ±3 cents on recorded guitar.
- **"In tune":** |cents| ≤ 5 held for 300 ms (15 hops at 48 kHz), released past 7 cents, so a note hovering at 5 doesn't flicker. That is **Relaxed**, the default; **Precise** is ≤ 2, released past 3 (§5, Settings). The shown "in tune" goes through the steady hint line (see *Audio capture*), so it also lasts at least 0.5 s.

### Other sample rates
`AnalysisSettings.forSampleRate(rate)` (8–96 kHz) scales window and hop so they always last ≈ 85 ms and ≈ 21 ms and the smoother's frame counts keep their meaning (44.1 kHz: 3763 / 941 samples). `PitchPipeline` builds its `FrameAssembler` and detector from it, and `AudioConfig.forSampleRate` makes the recorder's chunks one hop long. A rate outside 8–96 kHz ends the flow with `InitializationFailed`.

Below 32 kHz a period spans too few samples for ±1 cent, so `UpsamplingPitchDetector` first interpolates by an integer factor to ≥ 32 kHz (polyphase windowed-sinc FIR: Kaiser β = 8, 32 taps per phase, cut-off 0.45 × input rate), and YIN's low-pass drops to 0.4 × the input rate so no images get through. Measured: every rate from 8 to 96 kHz within 1 cent on sines, saws, plucks and weak fundamentals; 8 kHz costs 0.5 ms per frame on the JVM.

### Frame assembly
`FrameAssembler` keeps a 4096-sample ring buffer. `push()` takes chunks of any size and stops as soon as a frame is ready, so none is skipped; `readFrame(out)` copies the window oldest-first into a caller-owned array. First frame after one full window, then one per hop. Allocated once.

### Signal gate
`SignalGate.process(frame)` first measures the **peak** as the mic delivered it (`peakDbfs`, to detect clipping), then removes the DC offset in place and measures the RMS level as `20 · log10(rms)` dBFS (full-scale square 0 dBFS, sine ≈ −3; silence floored at −120). It opens at **−50 dBFS** and closes below **−56 dBFS** (6 dB hysteresis), so a decaying note doesn't flicker. Only frames with the gate open reach the detector.

### Noise floor, SNR and clarity
How well the tuner hears the guitar. Every `PitchReading` carries these besides the level; *Reception* turns them into what the display shows.
- **Noise floor** (`level/NoiseFloorTracker`): the room's background in dBFS. Only frames without a pitch count, gate open or not. Their levels pass a running median of 5 (ignores a glitch), and the floor is the lowest of those over the last **2 s of note-free frames** (scaled with the hop by `AnalysisSettings.newNoiseFloorTracker()`). This is Martin's (2001) minimum statistics on one band, with a median in place of his recursive smoothing (which took ~0.6 s to follow a 20 dB drop). The floor drops within a few frames, rises only once a louder room lasts 2 s, and notes never move it. NaN until the first note-free frame; reads a little low on fluctuating noise.
- **SNR** while a note sounds: `10 · log10(10^((level − floor) / 10) − 1)`, the frame's power minus the background's (as in Boll's spectral subtraction), at least −20 dB; NaN without a pitch or floor. Measured within 1 dB from 0 to 40 dB SNR.
- **Clarity** (`pitch/Clarity`): 0..1 from YIN's confidence `c = 1 − d′(τ)`. `d′(τ)` is the aperiodic share of the power, so `10 · log10(c / (1 − c))` is a periodic-to-aperiodic ratio in dB, like Boersma's (1993) harmonics-to-noise ratio. It depends only on the noise, not pitch or timbre, and sits ≈ 7.8 dB above broadband SNR (YIN's low-pass removes most hiss). Mapped linearly from 7.5 dB (`d′` = 0.15: 0) to 20 dB (`d′` = 0.01: 1): 20 dB SNR or cleaner gives 1, 10 dB ≈ 0.8, 6 dB ≈ 0.5, 0 dB ≈ 0.

All allocation-free (byte-counting tests). **No meters** for these (owner's decision, 2026-10-12): they become one *reception* figure the display shows, tips only when the player can fix something, and optional signal bars behind a setting.

### Reception
`level/ReceptionTracker` (pure, counted in frames) turns each pitched frame's level, SNR and clarity into one steady 0..1 figure: 1 for a strong, clean note, towards 0 when it is quiet, sinks into the room or loses its period.
- **Scores:** level 0 at −50 dBFS to 1 from −35 dBFS; SNR 0 at 0 dB to 1 from 20 dB (unknown room counts as 1); clarity as is. Linear in dB.
- **Smoothing:** a one-pole filter per factor, 0.15 s towards better and 0.5 s towards worse, so a rough frame or two barely dims it. In a note's first 0.1 s a factor rises at once (the window still holds the silence before it). `reset()` (note gone) returns to 1.
- **The figure** is the lowest of the three; `limit` (`ReceptionLimit`: `NONE` from 0.95, else `LEVEL`, `NOISE`, `CLARITY`) names the one holding it down. The smoothed scores and a smoothed `levelDbfs` are public for the signal bars.

`TunerInterpreter` feeds one tracker every pitched frame and resets it when the reading is lost; `Detected.reception` carries it. On screen (`components/ReceptionLook.kt`) it changes **only opacity and sharpness**, never colour, shape or motion, which stay the tuning cue: at worst reception the needle layer and wave fade to 0.62 of their opacity with up to 2 dp blur (`BlurEffect`, 8 steps made once; Android 12+, older phones only fade). The effect scales with how far the picture is from its resting look, so resting looks unchanged, and `HeldReception` keeps the last value while a note fades out. Text never fades.

### Tips
`TipCue` (`ReceptionTip.kt`, pure, counted in frames) picks the `ReceptionTip` the sound asks for, with separate on and off limits so a level hovering at a limit can't flicker:
- **Too loud** ("Too loud, move the phone back"): peak reaches −0.5 dBFS (clipping), until it stays below −3 dBFS. A pluck that clips only in its attack is shorter than the tip's settle time. Comes first, because clipping distorts everything else.
- **Too soft** ("Play a bit louder"): the note's *loudest* level so far is below −44 dBFS, so a well-played note's fading tail never asks.
- **Too noisy** ("Too noisy here"): room background ≥ −38 dBFS, until below −42 dBFS (also between notes); or the note's best SNR stays below 8 dB. Too soft comes first, since playing louder also beats noise.
- A note's verdict holds 2.5 s after it ends, so a tip stays through the gap to the next pluck.

The wanted tip passes through its own `SteadyHint<ReceptionTip>` (not restarted per note): settles in 0.5 s (loud), 0.6 s (soft) or 1 s (noisy), stays at least **3 s**, then the line stays tip-free for 1 s. `Detected.tip` and `Listening.tip` carry it.

On screen (`NoteDisplay`) a tip **replaces something less needed, so nothing moves**: "Play a string" without a note, the direction in the wave's pill, or the frequency under the needle (so "Tune up/down" stays). One line, set smaller (down to 11 sp) when it doesn't fit. TalkBack: "E 2, 12 cents flat, Play a bit louder".

### Signal bars (optional)
Behind "Show signal bars" (off by default). Four bars in the free slot at the right end of the note's pill (mirroring the check mark), so they add and move nothing in any layout.
- **Count** (`SignalBars`, pure): reception lights bars 1–4 from 0.05, 0.35, 0.65 and 0.9, each going out 0.08 below its threshold. Without a note the last count stays, dimmed to 45 %.
- **Drawing** (`components/SignalBarsButton.kt`): a `Canvas` in the pill's text colour, unlit bars at 20 %; the count is a `derivedStateOf`, so only a changed count recomposes. Hosted in `movableContentOf`, so the count and an open panel survive layout changes.
- **Figures:** a tap (TalkBack "Signal bars: 3 of 4", action "show the figures") opens a small panel "How well Twangle hears you": volume, background and clarity. `TunerViewModel.signalFigures` updates them 4×/s, only while the setting is on.

### Pitch detection (YIN)
`YinPitchDetector` follows de Cheveigné & Kawahara (2002), steps 2–5, with `d(τ)` computed by FFT (`FftDifference`, §4) and two additions that synthetic measurements showed were needed for ±1 cent:
1. **Low-pass pre-filter:** 4th-order Butterworth at 4 kHz (two RBJ biquads), skipping its ~92-sample start-up transient. Without it very bright tones near 1.4 kHz give a V-shaped dip that parabolic interpolation misjudges by ~2.5 cents. Also removes hiss.
2. **Adaptive least-squares refinement:** a cubic fitted to raw `d` over the lags `τ ± k` within 5 % of the way from the dip to the mean (`k` ≤ τ/16). Sharp dips use k = 1 (the classic parabola); broad dips (low pure notes) average dozens of lags. At 20 dB SNR this cut the worst low-E error from ~23 cents to under 1; the cubic term removes a ~0.3-cent bias.

**Range edge and whole-period checks.** The first dip is followed to its minimum; if it is still falling at τ_max, the pitch is below 62 Hz and the frame has none (this keeps 60 Hz hum out). Two checks then catch a period off by a whole factor, on raw `d`:
- *Too long (noise):* heavy noise inflates `d′` at short lags, so the first accepted dip can be 2–3 periods. If `d` at a local minimum near τ/k is at most 15 % above `d(τ)` (and `d′` < 0.5), τ/k is taken.
- *Too short (harmonics):* a high note with a strong 2nd and weak odd harmonics nearly repeats every half period. If `d′(τ) ≥ 0.05` and `d` at 2τ or 3τ is below a quarter of `d(τ)`, that lag is the period.

Search range τ = 32…775 (1500…62 Hz) plus 50 lags for the fit; integration window `W = 4096 − 92 − 825 = 3179`. Confidence = 1 − d′(τ). `PitchResult` is a value class packing two floats into a `Long`, with `PitchResult.NONE` instead of null, so it never boxes. Measured: ≈ 0.1 cent for sines, ≈ 0.5 for saw/square/pluck/weak fundamental, < 1 at 20 dB SNR, < 5 at 10 dB; no octave errors. `detect()` is allocation-free (byte-counting test).

### Smoothing
`PitchSmoother` works in fractional MIDI, so cents are ×100 and A4 is already applied:
1. **Octave-jump rejection:** a reading a whole number of octaves (±30 cents) from the running median is dropped; if it holds 3 frames (~64 ms), the filters restart there.
2. **Median of 5** (~107 ms) removes other outliers.
3. **EMA, α = 0.3,** snapping when the median is > 50 cents away (a new string). Steps are weighted by confidence: 1 from 0.97 up, falling linearly to 0.2 at 0.88 and below. This roughly halves needle wobble in a noisy room and at the end of a decay without slowing clean notes.
4. **Note hysteresis:** the shown note changes only past 65 cents (50 + 15), so displayed cents can reach ±65.

`noReading()` keeps the last reading for up to 4 missed frames (~85 ms), then clears it. Allocation-free.

### Edge cases (measured, `EdgeCaseTest`)
What the user sees after the whole chain on synthetic signals:
- **White noise:** always the right note; worst error 0.8 cents at 10 dB SNR, 1.7 at 6, 2.5 at 3, ≈ 10 at 0 dB (low notes; high notes stay under 2).
- **Mains hum 10 dB below the string:** within 3 cents; hum alone shows nothing. *Known limit:* hum within ~6 dB of the string can lock YIN onto a shared period or hide low strings. A notch filter would fix it if real rooms need it.
- **High strings with strong 2nd/3rd harmonics** (B3–C6): within 1 cent, no octave flips while decaying.
- **Sustain decay:** stays within 1 cent until the gate closes (once); over −60 dBFS room noise the last frames wobble up to 6 cents, never another note.
- **Bluetooth-like audio** (300–3400 Hz or 100–7000 Hz band, native 8 or 16 kHz): within 1 cent.

### Note math
```
midi  = 69 + 12 · log2(f / A4)        (A4 default 440 Hz, configurable 415–466)
note  = ceil(midi − 0.5)               (round half down, so exactly half-way reads +50)
cents = 100 · (midi − note)            range (−50, +50]
```
Scientific pitch notation (MIDI 60 = C4). `Note` is a value class over the MIDI number. `Notation` (`SHARPS`, `FLATS`, `SOLFEGE`) names a pitch class with real ♯ and ♭ (C♯ / D♭ / Do♯; fixed-do, B is Si); each entry holds its names in both spellings so `nameOf()` returns the same instances every frame.

**Tuning spelling.** A preset is written the way players write it, and that wins over the sharps/flats setting: Half step down is E♭ A♭ D♭ G♭ B♭ E♭, Open D keeps F♯. `Tuning.spelling` comes from how the preset is written in `Tunings` (`"Eb2"` flats, `"F#3"` sharps; mixing is refused), and `Notation.nameIn(tuning, …)` applies it in the string row, tuning menu, hint and big note in guitar mode. Chromatic mode follows the setting alone.

**Auto (guitar) mode** targets the closest string of the tuning, in cents; **Chromatic** the nearest equal-tempered note.

### Tunings
`Tuning(id, strings)` lists open-string `Note`s lowest first; `id` is the saved key (never rename). Names live in string resources, so `:core:dsp` has no UI text. `Tunings` holds the presets (Standard, Drop D, Half-step down, Full-step down, DADGAD, Open G, Open D, Open E), `DEFAULT` = Standard, `byId()` to restore.
- `nearestStringIndex(midi)` follows A4 calibration; smallest |Δ| in semitones, ties to the lower string, no allocation.
- `centsFromString(midi, index)` is **not** wrapped to ±50, so a string far off shows the full distance.

### Strobe model
`StrobeMotion` (`strobe/`) is the drift of a strobe pattern, one animation frame at a time; every wave look runs one (via `WaveMotion`). A mechanical strobe drifts at the beat rate in Hz, so high strings move faster; here the drift comes from cents, so every string looks the same. `driftSpeed(cents)` in stripe periods per second: **0 within ±0.5 cent** (finer than the ±1 cent target, so an in-tune string stands perfectly still), then **0.4/s per cent beyond**, up to **4/s** from 10.5 cents; positive when sharp. It ignores the in-tune flag (2–5 cents wide) because the strobe exists to show the last cent. `advance(seconds, cents)` eases the shown cents towards the reading (rate 20/s) and moves `phase` (0..1, wrapping); a step is capped at 0.1 s. Without a reading the view stops calling it and the pattern freezes. Tests feed sines 0, ±1, ±3, ±5 and ±30 cents off every string through `TunerChain`.

### Audio source selection
1. `MediaRecorder.AudioSource.UNPROCESSED` if `PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED` is `"true"`.
2. Otherwise `VOICE_RECOGNITION`, which must have AGC and noise suppression off.
3. Never `MIC` or `VOICE_COMMUNICATION`: their AGC and noise suppression distort pitch and level.

`AudioSourceSelector.candidates()` returns these in order; `AndroidAudioRecorder` opens the first whose `AudioRecord` initializes, so a failed `UNPROCESSED` still falls back.

**Bluetooth headsets.** A headset mic only records over SCO/HFP or LE Audio: 8 or 16 kHz, band-limited, often noise-suppressed. Twangle never asks for that route, and in case something else opened it, `InputDeviceSelector` pins the built-in mic with `setPreferredDevice` whenever a Bluetooth input is present. Wired headsets and USB interfaces keep normal routing. Without a built-in mic routing is left to Android; low-rate audio is still handled. *Not yet verified with a real headset* (owner check).

### Audio capture
`AudioRecorder.chunks()` is a cold `Flow<FloatArray>`: the mic opens when collection starts and is released when it stops. `AndroidAudioRecorder` reads one-hop chunks with `READ_BLOCKING` on its own single-thread dispatcher (buffer ≥ 4 chunks). To avoid ~47 arrays a second it fills a **pool of 4 arrays** behind a channel of capacity 2, so **a chunk is only valid until the collector takes the next one**: downstream code copies samples out inside `collect` and must not `buffer()` the chunks.

`PitchPipeline` (`:feature:tuner`, since it joins audio and DSP) collects the chunks into a `FrameAssembler` and runs gate, detector, `NoiseFloorTracker` and `Clarity` on `Dispatchers.Default`, emitting one `PitchReading(frequencyHz, confidence, levelDbfs, gateOpen, noiseFloorDbfs, snrDb, clarity)` (plus peak) per hop. DSP objects are created once per collection; only the small reading is new each frame.

`TunerViewModel` creates a `TunerInterpreter` per listening session, which feeds `PitchSmoother` and turns the smoothed MIDI into a `TunerUiState`, on the same coroutine as the detector. Mode and saved settings are combined into one immutable `TunerOptions` (mode, tuning, A4, locked string, notation, precision), rebuilt only on change. Listening waits until settings have loaded. Changes take effect on the next frame without reopening the mic; only a new A4 restarts the smoother.
- **Chromatic:** the smoother's note and cents.
- **Auto:** the closest string and `centsFromString`. The target is sticky: it changes only once another string is closer by more than 15 cents.
- **Auto, string locked:** tapping a string locks the target to it (`toggleString`, `TunerOptions.lockedString`); tapping again unlocks. Kept in memory only, survives a mode switch, released when the tuning changes.
- The frequency shown is the smoothed one (`NoteMapper.frequencyOf(smoother.midi)`).
- `InTuneDetector` decides "in tune" from smoothed cents and frame duration, and restarts when the note stops or the target changes.
- **Steady hint line** (the owner saw "Tune up/down" flicker near the note). Two pure layers, counted in frames:
  - `TuningCue` (`TunerHint.kt`) picks the *wanted* hint: `IN_TUNE` while `InTuneDetector` says so, else up or down from the rounded cents. Inside the in-tune window the previous direction is kept, so a wobbling reading doesn't swap; a note starting inside it shows no direction.
  - `SteadyHint<T : SteadyMessage>` adds timing: a message shows once wanted for its `settleSeconds` without a break and stays at least its `minShowSeconds`; an empty line after a message also stays 0.5 s. After a reset the first message shows at once. `IN_TUNE` settles at once (the detector already held 300 ms), `TUNE_UP` / `TUNE_DOWN` after 0.2 s; each stays ≥ 0.5 s.
  - `Detected.inTune = hint == IN_TUNE`, so colour, check mark, haptic tick, glow, TalkBack and hint always agree. Tips use a second `SteadyHint` (see *Tips*).

Failures end the flow with a typed `AudioRecorderException`, mapped by `toTunerError()` to a `TunerError` that the UI shows with a "Try again" button:
- `PermissionMissing` → `MIC_PERMISSION_MISSING`.
- `InitializationFailed` (no input opens at the rate) → `MIC_UNAVAILABLE`.
- `MicrophoneBusy` → `MIC_BUSY`. Before Android 10 `startRecording()` fails; from 10 the mic is shared and a higher-priority app silences us, so `isClientSilenced` is checked after the first chunk. Silencing mid-session just looks like silence.
- `ReadFailed(code)` (e.g. `ERROR_DEAD_OBJECT` when the audio server restarts) → `MIC_STOPPED`; collecting again reopens the mic.

**Measured on the Galaxy S26 (Android 16):** no `UNPROCESSED`, so `VOICE_RECOGNITION` at 48 kHz with no effects attached; a 440 Hz tone read 439.98 Hz (−0.1 cents) at −31 dBFS; quiet room noise −52 to −56 dBFS, right at the gate.

## 4. Threading & performance
- `AudioRecord.read()` blocks, so it runs on `Dispatchers.IO.limitedParallelism(1)`. DSP runs on `Dispatchers.Default`.
- **No allocations in the per-frame loop.** Buffers are allocated once; per-frame garbage at ~47 Hz causes GC jank.
- **YIN difference function via FFT.** The direct O(W·τ_max) sum (~2.6 M multiply-adds per frame) took 69 % of the app's CPU cycles on the S26 (~7 ms per frame). `FftDifference` computes `d(τ) = e(0) + e(τ) − 2·r(τ)` (paper, eq. 7): energies as a running sum, the cross-correlation from one complex FFT carrying both real inputs plus one inverse (N = 4096, radix-2 `Fft`, doubles, no allocation). `FftDifferenceTest` checks it within 1e-10 of the direct sum. `./gradlew :core:dsp:benchmark` (kept out of CI): YIN **1.05 → 0.48 ms** per frame on the JVM. Budget: 2 ms.
- **Only visible changes reach the main thread:**
  - **Silence:** equal `Listening` states are dropped with `distinctUntilChanged()` on the DSP dispatcher.
  - **ViewModel:** `distinctUntilChanged { old, new -> old.looksLike(new) }` drops readings that would look the same as the last one *sent*: same text (whole cents, 0.1 Hz) and the needle within `NEEDLE_STEP_CENTS` = 0.03 cents (below a pixel) or pinned at the same edge, reception within `RECEPTION_STEP` (0.02). `looksLike` allocates nothing; `TunerUiStateShownTest` fails if `Detected` gains a field it doesn't compare.
  - **Composition:** `TunerScreen` composes from `state.asShown()` through `derivedStateOf`, so it recomposes only when text changes. Exact cents and reception reach the meter and wave as lambdas read only in layer and draw phases.
  - **Needle:** `MeterFrame` follows the lambda with `snapshotFlow` into an `Animatable` spring (each new target `launch`ed so the spring keeps its speed). Moving parts sit in their own `graphicsLayer` and only change its transform, so a moving needle neither recomposes nor re-records drawing.
  - **Frame rate:** on Android 15+ a plain Compose animation gets the "normal" rate category (60 Hz on the S26), so the meter asks for 120 Hz with `Modifier.preferredFrameRate(120f)`, which only counts while it draws.

### Measured on the Galaxy S26 (Phase 5)
Minified `benchmarkRelease` build, portrait, CPU per thread from `/proc/<pid>/task/*/stat` (percent of one core).

| | Before (direct YIN, JIT only) | After (FFT YIN + Baseline Profile) |
|---|---|---|
| Silence: whole app | 7.8 % (main thread 2.3 %) | 5.6 % (main thread ≈ 0) |
| Note sounding: whole app | ≈ 45 % | ≈ 20 % |
| Note sounding: DSP thread | ≈ 34 % (≈ 7 ms/frame) | ≈ 9 % |

Main thread with identical synthetic input, before and after the recomposition work above: guitar mode 11.6 % → 5.1 %; chromatic 15.8 % at ~75 frames/s → 12.2 % at ~110 frames/s (≈ 2.1 → 1.1 ms per frame). Nothing is drawn in silence. Battery: ≈ 0.8 mAh per hour of listening for the app's CPU; the screen costs far more.

### Meter looks (Phase 13)
Measured on the S26 (Android 16, portrait) with `scripts/meter-frames.sh`, which runs `MeterFramesBenchmark` against the minified `benchmarkRelease` build (~4 min, phone unlocked and upright; `scripts/meter-frames.sh COMET:STUDIO_METER` for some looks, `CURRENT` for whatever the app shows).
- **Input:** that build hears `SyntheticAudioRecorder` (`:app`, behind `BuildConfig.SYNTHETIC_AUDIO`, a constant `false` elsewhere, so R8 removes it from release): A4 sweeping 30·sin³ over 8 s plus a 2-cent wobble (`SweepTone`), noise 20 dB below. The marker never stands still and reaches in tune twice per sweep.
- **Method:** per look, chromatic mode, cents display, 3 windows of 7 s; frames from `dumpsys gfxinfo`, CPU from `/proc`. The old needle row is commit 4826b6e with the same input and frame-rate request.

| Chromatic, meter moving | Frames/s | Late frames (of ~2,500) | p50 / p90 / p95 / p99 | Main thread | Main per frame | RenderThread |
|---|---|---|---|---|---|---|
| Old needle (same build and input) | 120 | 1 | 5 / 8 / 9 / 11 ms | 22.0 % | 1.83 ms | 19.0 % |
| Classic | 120 | 3 | 8 / 10 / 11 / 12 ms | 19.3 % | 1.60 ms | 27.0 % |
| Light arc | 119 | 0 | 8 / 10 / 10 / 12 ms | 19.8 % | 1.66 ms | 28.5 % |
| Spirit level | 120 | 3 | 13 / 17 / 17 / 18 ms | 17.1 % | 1.43 ms | 28.2 % |
| Rolling scale | 119 | 12, repeat run 5 | 12 / 12 / 13 / 14 ms | 19.9 % (22.5 %) | 1.67 ms (1.89 ms) | 30.3 % |
| Comet | 119 | 6, repeat run 0 | 8 / 10 / 10 / 12 ms | 18.6 % (19.7 %) | 1.56 ms (1.65 ms) | 32.7 % |
| Studio meter | 120 | 10, repeat run 0 | 9 / 10 / 11 / 12 ms | 17.1 % (20.0 %) | 1.43 ms (1.67 ms) | 30.1 % |

Every look draws at 120 Hz with at most 0.5 % late frames, and uses less main thread than the old needle (±2 points between runs, brackets = repeat runs). At 120 Hz the pipeline may take more than one vsync, so p50 above 8.3 ms is not a dropped frame. Before the frame-rate request every look ran at 60 Hz.

### Baseline Profile
Lists the classes and methods used at startup and on the tuner screen so Android compiles them ahead of time.
- `:baselineprofile` has `BaselineProfileGenerator` (cold start, listening, switching modes, settings and back), `StartupBenchmark` and `MeterFramesBenchmark`. UI Automator finds controls by test tag (`TunerTestTags`, exposed as resource ids with `testTagsAsResourceId`).
- Generate with the phone connected: `./gradlew :app:generateBaselineProfile` (~5 min). It writes `app/src/release/generated/baselineProfiles/`, which is committed; `profileinstaller` installs it for sideloaded APKs.
- Startup: `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest`. On the S26 cold start was ~89 ms with or without the profile; it stays because it is free and matters more on slower phones. Neither task runs in CI.

### Release build: shrinking and signing
- **Shrinking:** R8 (`isMinifyEnabled`) and resource shrinking; the APK went from 9.3 MB to 1.8 MB. The app uses no reflection; `app/proguard-rules.pro` only keeps the enums settings save by name (`TunerMode`, `ThemeMode`, `VisualStyle`, `WaveLook`, `Notation`) as a safety net. **Never rename saved enum constants.**
- **Signing:** release builds use the owner's **upload key**, never in the repo: `~/.twangle-keys/` (`twangle-upload.jks`, alias `upload`, with `keystore.properties` and a README), or the path in the Gradle property `twangle.keystoreProperties`. Without it (CI, other machines) the release build is unsigned and nothing fails. `.gitignore` excludes `*.jks`, `*.keystore` and `keystore.properties`. The Play Store will use Play App Signing, so a lost upload key can be reset.
- `./gradlew assembleRelease` (APK) or `bundleRelease` (AAB); check with `apksigner verify --print-certs`.
- **Version:** `versionName` `0.9.0-beta`, `versionCode` = major × 10000 + minor × 100 + patch (900 now). Every build that leaves the PC needs a higher `versionCode`; bump both together. About shows `versionName`.
- Release and debug APKs have different signatures: switching on a phone means uninstalling first, which clears saved settings.

## 5. Lifecycle & permissions
- `RECORD_AUDIO` is requested at runtime from the tuner. `rememberMicPermissionState()` tracks a `MicPermissionStatus` (rules in the pure `MicPermissionRules`), and `PermissionPrompt` shows one action per state:
  - `NOT_REQUESTED`: asked automatically once; the prompt explains why and offers "Allow microphone".
  - `DENIED` (rationale shown): ask again.
  - `PERMANENTLY_DENIED`: "Open settings" deep-links to the app's page. Dismissing the first dialog by tapping outside looks identical to Android, so it lands here too.
  - `GRANTED`: the tuner. Checked again on every `ON_RESUME`, so a grant in Settings is picked up.
  - Survives rotation (`rememberSaveable`) but isn't persisted.
- The microphone is open **only while the tuner screen is visible**: `SharingStarted.WhileSubscribed(5_000)` plus `collectAsStateWithLifecycle()`, so `AudioRecord` is released in the background and the privacy indicator turns off. The 5 s grace keeps the session through a rotation. `MicLifecycleTest` (`:app`, device) checks it against `AudioManager.activeRecordingConfigurations`.
- No foreground service: background capture is neither needed nor allowed.

### Settings
The gear button opens settings (`:feature:settings`). `MainActivity` switches between tuner, settings and About with one saved `Screen` value (no navigation library) and a `SaveableStateHolder`; back returns About → settings → tuner. `SettingsViewModel` saves every change at once; there is no save button.

Three groups under headings (in the style's title face and `primary`, marked as headings for TalkBack), then About. Groups are 32 dp apart; rows, including the card grids, keep 16 dp from the edges.
- **Tuning:** A4 calibration, note names, in-tune precision.
- **Look:** style, light or dark, wallpaper colours, meter look, in-tune effect, display, wave look, the temporary "When in tune" choice, full layout on its side, signal bars.
- **While tuning:** keep screen on, vibrate when in tune, tune up/down hint.

What each does:
- **A4 calibration:** slider 415–466 Hz in 1 Hz stops, − and + buttons, "Reset to 440 Hz"; saved when the finger lifts. The tuner shows "A4 = 442 Hz" under the mode switch.
- **Note names:** sharps, flats or Do Re Mi (`RadioGroupSetting`). A preset's own spelling wins in guitar mode (§3, Tuning spelling).
- **In-tune precision:** Relaxed (≤ 5 cents, default) or Precise (≤ 2). `TunerInterpreter` swaps in `InTuneDetector.forPrecision` on the next frame; everything that follows `inTune` follows it.
- **Style:** `StylePicker` (`:feature:settings`), a `LookCardGrid` like the meter and wave looks: six mood cards in the owner's order, three to a row (two from 1.3× font), the style's name under each (§6, Visual styles). It is the only place to pick a style; the tuner's palette button went in Phase 14.
- **Light or dark:** system, light or dark, plus "Wallpaper colours" (dynamic color, Android 12+, Contemporary only). `MainActivity` reads settings before drawing, so the theme never flashes, and calls `enableEdgeToEdge` again on a change so status-bar icons follow the app.
- **Keep screen on** (default on): `View.keepScreenOn` while the tuner is shown with the mic granted; a window flag, not a wake lock.
- **Vibrate when in tune** (default on): `InTuneHaptic` clicks once (`EFFECT_CLICK`, 20 ms one-shot before Android 10) when `inTune` turns true. It uses `Vibrator` directly because Compose/View haptics obey the phone-wide touch-feedback setting, which many people switch off; this needs the normal `VIBRATE` permission. `InTuneTickRule` skips a second tick for the same note within 1.5 s; the state is saved, so rotating doesn't tick.
- **Show tune up/down hint** (default on): `NoteDisplay` shows `Detected.hint` ("Tune up ↑" / "Tune down ↓") in `primary`. In the wave view it fills the empty pill; with the needle it gets its own line that keeps its height while empty, so nothing jumps. TalkBack adds it in words. The switch controls both.
- **Show signal bars** (default off): §3, *Signal bars*.
- **About** (its own page, `AboutScreen`, opened from a `LinkSetting` row showing the version; Settings and About share `SettingsPage`): name and version (`BuildConfig.VERSION_NAME` passed from `MainActivity`), the free-forever promise, a short privacy statement (keep it in step with PRIVACY.md), and a credit for every bundled font and library. Each credit opens an `AlertDialog` with the full licence, read from assets on `Dispatchers.IO` and reflowed (`reflowLicence`). With no internet permission every licence ships in the APK: OFL texts in `:core:designsystem` `assets/licenses` (`bundledFontCredits`, kept in step with `res/font` by a JVM test) and Apache 2.0 in `:feature:settings` `assets/licenses`. A new library under another licence needs its own credit and file.

### Saved settings
`SettingsRepository` (`:core:settings`) exposes `settings: Flow<AppSettings>` plus one suspend setter per value. `DataStoreSettingsRepository` stores them in `files/datastore/settings.preferences_pb`; `AppContainer` creates the only instance (DataStore allows one per file). Tunings are saved by `id` and enums by `name`, so **those must never be renamed**. `FakeSettingsRepository` (`testFixtures`) keeps settings in memory for tests.

| Field | Default | Notes |
|---|---|---|
| `a4Hz` | 440 | Clamped to 415–466 and rounded (`AppSettings.normalizeA4`, also in the setter) |
| `mode` | `AUTO` | `TunerMode.AUTO` / `CHROMATIC` |
| `tuning` | Standard | Unknown id → Standard |
| `notation` | `SHARPS` | `FLATS`, `SOLFEGE` |
| `inTunePrecision` | `RELAXED` | `PRECISE`; key `in_tune_precision` |
| `theme` | `SYSTEM` | `LIGHT`, `DARK` |
| `style` | `CONTEMPORARY` | `VisualStyle`: `ANCIENT`, `CLASSIC`, `RETRO`, `CONTEMPORARY`, `FUTURISTIC`, `SPACE_AGE`; key `visual_style` |
| `dynamicColor`, `keepScreenOn`, `vibrateInTune`, `showTuneHint` | true | |
| `showSignalBars` | false | |
| `display` | `WAVE` | `TunerDisplay.NEEDLE` / `WAVE`; key `tuner_display` (see migrations) |
| `meterLook` | `CLASSIC` | `MeterLook`: `CLASSIC`, `LIGHT_ARC`, `SPIRIT_LEVEL`, `ROLLING_SCALE`, `COMET`, `STUDIO_METER`; key `meter_look` |
| `inTuneEffect` | `CALM` | `BOLD`; key `in_tune_effect` |
| `waveLook` | `RIBBON` | `WaveLook`: `STROBE`, `RIBBON`, `DOTS`, `FIREFLIES`, `LOOP`, `RIPPLES`; key `wave_look` |
| `waveBreathesInTune` | true | **Temporary** "When in tune: Breathe gently / Hold perfectly still"; it and the losing option go after the owner check |
| `keepTwoPaneLandscape` | false | "Full layout on its side" |
| `stringLockTipSeen` | false | Set once the string-lock tip is dismissed; no switch |

- **Forgiving reads:** a missing, unknown or out-of-range value gives its default; an unreadable file gives all defaults, and a corrupt one is replaced so saving works again.
- **Migrations** (in `DataStoreSettingsRepository`, not as enum constants, so no code can pick the old names again; JVM tests cover each case): while no `tuner_display` is saved, the old switch `show_waves` gives `WAVE` (on) or `NEEDLE` (off), and saving a display removes it. A saved display `"STROBE"` reads as `WAVE` with look `STROBE`, rewritten by the first `setDisplay` or `setWaveLook`. A saved look `"STRIPES"` reads as `STROBE`. A saved style `"PREHISTORIC"` or `"MIDDLE_AGES"` (the seven styles before October 2026) reads as `ANCIENT`, rewritten by the next `setStyle`; any other unknown style gives the default.

## 6. UI

### UI state model
```kotlin
enum class TunerMode { AUTO, CHROMATIC }   // in :core:settings, saved by name

sealed interface TunerUiState {
    data object PermissionRequired : TunerUiState
    data class  Error(val reason: TunerError) : TunerUiState
    data class  Listening(val mode: TunerMode, val tuning: Tuning, val a4Hz: Float, val lockedString: Int?, val notation: Notation) : TunerUiState   // no usable signal
    data class  Detected(
        val mode: TunerMode,
        val tuning: Tuning,
        val a4Hz: Float,               // concert pitch in use (A4 calibration)
        val notation: Notation,        // how note names are written
        val noteName: String,          // target note in that notation, e.g. "E", "D♭", "Re"
        val octave: Int,               // e.g. 2
        val frequencyHz: Float,        // smoothed
        val cents: Float,              // smoothed; Auto: unwrapped distance to the string, Chromatic: about ±65
        val targetString: Int?,        // index into tuning, Auto mode only
        val lockedString: Int?,        // string the user locked (then also targetString), else null
        val inTune: Boolean,           // ≤ 5 cents for ≥ 300 ms, released past 7 (Precise: ≤ 2, past 3)
        val hint: TunerHint?,          // the steady hint line: in tune, tune up or tune down
        val reception: Float,          // 0..1 how well the note is heard (ReceptionTracker), drawn not written
        val tip: ReceptionTip?,        // too soft / too noisy / too loud, held steady (Listening has it too)
    ) : TunerUiState
}
```

`TunerViewModel` exposes `state: StateFlow<TunerUiState>` plus `mode: StateFlow<TunerMode>`, so the mode switch reacts instantly. Inputs: `onPermissionChanged(granted)`, `setMode`, `setTuning`, `setStyle`, `toggleString(index)`, `dismissStringLockTip()`, `retry()`. Until permission is granted the state is `PermissionRequired` and the pipeline isn't started; the permission status itself lives in the UI (`rememberMicPermissionState`).

`reception` is never composed (`asShown()` sets it to 1); `TunerScreen` passes it as a lambda read in layer and draw phases only.

**No first-frame flips.** `MainActivity` has already read the settings, so it passes saved values in as each flow's first value: `TunerScreen(savedMode = …)` (otherwise Guitar showed for ~90 ms before Chromatic), `savedStringLockTipSeen` (a dismissed tip never flashes up) and `savedKeepTwoPaneLandscape`. `setMode` switches at once and saves in the background.

### Tuner screen
`TunerScreen` wires the ViewModel; `TunerContent` is the stateless body used by previews and UI tests. From top to bottom:
- **Corner button:** the settings gear in the top end corner, also on the permission prompt, inset 8 dp. The opposite corner stays empty (the palette button that opened a style sheet went in Phase 14; styles are picked in Settings).
- **Mode switch:** `ModeSwitch`, Material 3 segmented buttons "Guitar" / "Chromatic", the selected segment in `primaryContainer` (Material's secondary put Futuristic's purple beside its cyan). "A4 = … Hz" below it.
- **Meter:** `MeterFrame` in the picked look (see *Meter*).
- **Note:** `NoteDisplay`: the big note name and octave in the style's display face (shrinking to fit wide faces and solfège), then the cents in a pill ("12 cents flat", whole cents) and the smoothed frequency, or the wave (see *Display*). Pill numbers use `numberFontFor(style)` (the title face, except Ancient, whose Caesar Dressing 0 looks like a diamond). While listening the meter rests faded and the text asks for a string (Auto) or a note (Chromatic); the waiting note is a faint ♪.
- **Guitar controls** (`GuitarControls`): `TuningPicker` ("Standard ▾", a menu of presets with their strings, the current one ticked) and `StringSelector` (E₂ A₂ D₃ G₃ B₃ E₄, target highlighted and `selected`; each string a button, "Lock to this string" / "Pick strings automatically"; a locked string gets an outer ring, "Locked", and the hint "Play the A2 string"). Chips take the style's outline shape and a see-through backing.
- **Mode slot** (`ModeSlot`): measures both modes' content all the time, takes the taller height (at least 104 dp) and places only the current mode's, so a mode switch moves nothing, also at 2× font. In chromatic mode with the wave it shows `ChromaticReadout` (cents and frequency, hidden from TalkBack since the note's live region says them); with the needle the slot stays empty.
- **In tune:** meter marker, centre mark, note, cents text and highlighted string fade to the style's "in tune" colour (`StyleTokens.inTune`, read through `InTuneColor.kt`; Material 3 has no success role). A check mark appears beside the cents, so it doesn't depend on seeing green; the pill fills, a soft glow lights behind the note and the note pops (grows 8 % and springs back). Each runs once per change, read in the draw phase.

### String-lock tip
So people find the lock, guitar mode shows `StringLockTip` once ("Tap a string to lock it") until a string or the tip is tapped (`stringLockTipSeen`). A speech balloon in the style's `shapes.medium`, `primaryContainer` with a `primary` outline, pointing at the string row. It is **placed without taking room**, so nothing moves when it comes or goes, and it never covers the note, cents, frequency, hint or string chips (UI tests check bounds at 2× font and on a phone on its side):
- **Upright phone:** in the bottom-left corner beside the tuning picker, body just above the strings, pointer on the first string, no close icon; stays while a note sounds. If a very large font leaves no room there, it sits centred above the picker instead and steps aside while a note is detected, coming back after 3 s without one.
- **Two-pane layout:** under the string row, pointer up, with a close icon; text grows only to 1.3× font.
- TalkBack reads it as one button with the action "Dismiss tip". Hidden in chromatic mode like the other guitar controls.
- **Test trap:** never `performScrollTo` on the balloon (it lies outside its parent's bounds and the scroll never returns); scroll to the picker or strings. Every tuner UI test runs under a 10 s timeout rule so a hang can't leave a frozen window on the phone.

### Display
Setting "Display": `TunerDisplay` **needle** (cents in a pill and the frequency) or **wave** (the default, in the picked wave look). The meter is there with both. The wave takes the cents' place, so `NoteDisplay` and `TunerContent` lay it out differently: no pill colour or check mark, no frequency line, `ChromaticReadout` in chromatic mode, the compact note on a phone on its side.

### Meter
The meter above the note is the first thing anyone sees, so players pick one of six looks (setting `meterLook`), in the owner's order: *Classic* (default), *Light arc*, *Spirit level*, *Rolling scale*, *Comet*, *Studio meter*.
- **One frame** (`:feature:tuner` `components/MeterFrame.kt`): every look sits in `MeterFrame`, so changing looks moves nothing else. It owns the 2:1 box (`METER_ASPECT_RATIO`) over the ♭ and ♯ labels, the ±50-cent range (beyond it the marker pins at the edge, `meterCents`; without a reading it rests in the centre at 0.3 opacity), the look's spring (`meterFeel(look)`), the in-tune amount `tuned` (smoothstep, 0.4 s in, 0.6 s out), the in-tune `glow`, the reception fade and blur, and `clearAndSetSemantics {}` (`NoteDisplay` speaks the reading). It hands the look one `MeterReading`: cents, `velocity` (from the spring, without boxing), `presence`, `tuned`, `glow` and `applyMarkerLook(layer)`, read only in layer and draw phases.
- **Art in the design system** (`:core:designsystem` `meter/`): `MeterLookArt` draws each look, so the tuner and the settings cards draw exactly the same thing and `:feature:settings` needs no `:feature:tuner`. A part that only turns or slides gets its own `graphicsLayer` and only changes its transform; a part that changes shape is redrawn in its `Canvas` from paths, brushes and strokes made once. The whole art is clipped to its box.
- **Shared pieces** (`meter/MeterArt.kt`, named like the mock-up's helpers): `MeterRoles` (the colour roles and number face), `PlateArt`, `GlassArt`, `ScaleMarks` (with `numberFit`, which keeps neighbouring numbers 8u apart by setting them up to 15 % smaller), `NeedleArt`, `markerShadowPaint`, `GlowArt`, `StaticArt`, plus `mix` and `blurRadius`. Everything is built in `drawWithCache`, so only a size or colour change rebuilds it; drawing allocates nothing. Shadows need hardware-accelerated `BlurMaskFilter` (Android 9+); on Android 8 the meter has no shadows. `meter/MeterLooks.kt` holds the dial shared by Classic, Light arc and Comet (`DialGeometry`, `DialUnder`, `DialCentre`, `DialOver`).
- **The looks:**
  - *Classic:* the dial with the shared needle; needle and shadow drawn once and only rotated by their layers. Slightly underdamped, so it swings ~9 % past a new string and settles within a second, like a mechanical needle.
  - *Light arc:* 41 segments that light from the centre out to the reading with a soft halo at the lit end; `LightArcHeat` (one preallocated array) gives a fading trail. Its frame loop (`withFrameNanos`) runs only while a segment is still changing, then waits on `snapshotFlow`, so a steady reading moves nothing and UI tests settle.
  - *Spirit level* (`SpiritLevel.kt`): a bubble in a curved glass tube tinted by the accent. The bubble is drawn once; its layer only moves, turns and stretches it (up to 25 % while moving fast, from `velocity`). In tune it rests between two marks that fade to "in tune". No drop shadow (air in liquid).
  - *Rolling scale* (`RollingScale.kt`): a drum printed every 5 cents from −60 to +60, seen through a window, rolling past a fixed centre line; each mark is placed, narrowed and faded by its angle on the drum, and the zero bar sits on the side the note is off.
  - *Comet* (`Comet.kt`, `CometTrail.kt`): a head on a dotted path inside the dial, moved only by its layer, with a tail of the last 0.55 s (a preallocated ring of 160 samples) that thins, fades and shimmers gently, shortening to nothing as the head settles. No sparks.
  - *Studio meter* (`StudioMeter.kt`): the same plate, glass, marks and needle on a face backlit in the accent, a flatter scale around a hidden pivot, and a lamp that blends to "in tune" with `tuned`.
- **Feel per look** (`meter/MeterFeel.kt`, frequency / damping ratio, stiffness = (2π f)²): Classic 1.6 Hz / 0.6, Light arc 2.2 / 0.9, Spirit level 1.0 / 0.5, Rolling scale 1.3 / 0.75, Comet 1.7 / 0.7, Studio meter 1.9 / 0.62.
- **The in-tune moment** (owner: smooth, not gimmicky): a soft glow eases in with the haptic tick, holds while the note rings and fades when it leaves.
  - *When:* `InTuneGlowRule` (pure) lights it on the update that raises `Detected.inTune`, never restarts it while the note holds, and bridges a dropout of the same note for 600 ms (shorter than the tick's 1.5 s, so a bridged dropout never ticks twice). Another note or drifting out of tune puts it out at once. `rememberInTuneGlow` runs it in `TunerScreen` (saved over rotation) → `TunerContent(meterGlow)` → `MeterFrame(glow)`, eased 0.4 s in, 0.6 s out.
  - *Remove animations:* `rememberAnimationsOff()` reads `ANIMATOR_DURATION_SCALE` and follows changes; at 0 `tuned` and `glow` snap.
  - *Drawing:* the shared `TuneGlow`, a `GlowArt` in the "in tune" colour drawn once in its own layer under the marker; `glow` only sets the layer's alpha (`CompositingStrategy.ModulateAlpha`), so easing it redraws and allocates nothing. Clipped to the plate, so it never reaches the readings.
  - *Calm or Bold* (`InTuneEffect`): Bold is the same glow and timing at 1.7 × the alpha, capped at 52 % (`BOLD_GLOW_MAX_ALPHA`) so the scale stays readable, and 1.35 × the radius (`BOLD_GLOW_SIZE`); the studio lamp's halo 1.7 ×, capped at 85 %. It travels into `meterRoles(effect)`, so changing it rebuilds each look's cached art once and is never read per frame.
- **Picker** (setting "Meter", above the display choice): `MeterLookPicker` draws each look's `MeterLookArt` in a 3 × 2 grid of cards (two to a row from 1.3× font). It shares `LookCardGrid` with `WaveLookPicker`: the ring on the picked card, names on one line shrinking to 8 sp, one selectable radio group for TalkBack, test tags, and `LookDemo`, an infinite animation (UI tests skip it). The cards play one loop (`MeterDemo`): 18 cents flat, tuned up slowing like a peg, in tune, drifting 14 sharp, tuned down. Each card previews the chosen in-tune effect.
- **Tests:** JVM tests for each look's maths (`MeterReadingTest`, `MeterFeelTest`, `LightArcHeatTest`, `SpiritLevelTest`, `RollingScaleTest`, `CometTrailTest`, `StudioMeterTest`, `TuneGlowTest`, `InTuneGlowRuleTest`, `MeterDemoTest`, `NumberFitTest`). UI: `MeterLooksTest` (every look × style × light/dark: drawn inside its box, skipped by TalkBack, moves without recomposing, glows inside its box in Calm and more in Bold, snaps with animations off), `CompactLandscapeStylesTest`, `TunerContentTest`, `SettingsContentTest`.
- **Pictures for the owner:** `scripts/meter-look-screens.sh` (~2 min) runs `MeterLookScreenshots` with `-a meterScreenshots=true` (without it the class has no cases): every look in every style, light above dark, 20 cents flat and in tune with Bold, as PNGs plus an `index.html` contact sheet in `build/owner-screens/meter-looks/`.

### Meter art language
The six looks are drawn "as if by the same artist" (owner, after two rounds of moving mock-ups, October 2026). The approved mock-up is the reference: [docs/design/meter-looks-preview.html](docs/design/meter-looks-preview.html) (its top comment lists the same rules). *u* = box width / 340, about 1 dp on a phone.
- **Colours:** roles only, from the current style: background (stage), surface (plate), onSurface (ink), onSurfaceVariant (muted), outlineVariant (track), primary (accent) and the "in tune" colour. No ornaments; each style's own meter touches wait for Phase 14.
- **Font:** only the style's number face (`numberFontFor(style)`); wide faces (Michroma, Orbitron) at 82 %.
- **Plate:** 6u inset, 14u corners, lit from above (top 50 % lighter in light, 5 % in dark), 1u hairline in ink at 10 %, soft drop shadow (14u blur, 5u down).
- **Glass:** each look's window is recessed: sunk toward the background, 1u rim in ink at 14 %, inner shadow under the top edge (7u blur), one sheen from the top (white 28 % light, 7 % dark, gone by 45 % of its height). Inner corners 10u.
- **Scale:** a tick every 5 cents, none at 0. Major (every 10) 9u × 1.5u in ink; minor 5u × 1u at 45 %; round caps. Numbers 10–50 without sign, 9.5u, ink at 62 %, centred 20u from the tick's base, at least 8u apart. ♭ and ♯ at the ends in muted, 13u.
- **Centre:** one 3u rounded bar in the accent.
- **Marker** (needle, bar, bubble or light): round ends and a small soft shadow (3u blur, 1.5u right, 2.5u down). Needles taper 2u → 0.6u with a 6.5u ink hub and an accent dot.
- **Light:** one glow only, radial, in the "in tune" colour (32 % dark, 16 % light at its middle, fading to 0).
- **In tune:** one eased amount (smoothstep, 0.4 s in, 0.6 s out) blends centre mark and marker to "in tune"; the glow eases the same way. Both follow the steady hint's decision; with "Remove animations" both switch at once. Nothing flashes, bursts, flies, pulses or repeats.
- **Motion:** springs with a damping ratio of 0.5 or more.

### Wave view
Most people don't know what a cent is, so the wave display (`WaveView`, 240 × 40 dp, low and frameless so it doesn't compete with the meter) shows how far off the note is as a moving shape under the note. It borrows the beats you hear between two close notes: `WaveMotion` (`dial/WaveMotion.kt`, plain Kotlin) pinches the ribbon into pulses.
- **Drift follows the strobe rule exactly:** `WaveMotion` runs its own `StrobeMotion` (§3), so it stands dead still within half a cent and drifts right when sharp, left when flat, faster the further off (up to 4 pulses/s from 10.5 cents). It uses the exact cents, not `inTune`, so the last few cents still show.
- **Shape shows distance from afar, up to 50 cents:** the pinch deepens (`depthFor`) and more pulses fit across (`beatsFor`, 1.5 to 4.5) the further off; within half a cent the band is perfectly even. Shape eases towards the reading at 5/s, so a new string never makes it snap.
- **Breathing:** the band breathes slowly (one breath every 2.5 s, 10 % of its height). **Temporary choice** (`waveBreathesInTune`): *Breathe gently* keeps breathing on the note; *Hold perfectly still* lets it fade out (`WaveMotion.stir` → 0 in about a second), so on the note nothing moves at all. Off pitch both breathe. The owner compares both on the phone and the other goes.
- **Colour:** off pitch a tint of `primary` at 80 % (60 % was too dim on dark backgrounds); in tune the "in tune" colour with a soft halo; without a reading a faint line.
- **No words for the distance:** during a note the pill stays empty and uncoloured, without check mark (it keeps its place), and the frequency line is left out; in tune shows in the note's colour, glow and pop. With the tune hint on, the pill shows "Tune up ↑" / "Tune down ↓". TalkBack still hears the cents.
- **Drawing:** the frame loop runs only while there is a reading, with `withInfiniteAnimationFrameNanos` (UI tests skip infinite animations), and redraws only the view's own layer, without recomposing. `WaveView` is `clearAndSetSemantics {}` (test tag `TunerTestTags.WAVE` only); `NoteDisplay`'s live region says the reading, and the sentence never changes between frames.
- **Layout:** 240 × 40 dp at any font size (30 dp in the compact note on a phone on its side). In two-pane on a short screen the note and wave get smaller so the readout fits.

### Strobe look
The first wave look: two rows of stripes that drift right when sharp, left when flat, and stand still within half a cent, for the last cent of fine tuning. `drawWave` passes `WaveMotion.phase` to `drawStrobe` (`dial/Strobe.kt`) in the current style's own marks (see *Visual styles*), the lower row at twice the speed (`phase · 2`), like the rings of a strobe disc. The marks fade over the outer fifth at each end (`strobeFade`, by position only, so the wrap stays seamless). Without a reading the rows shrink to half height in the faint resting colour. On the note it follows the temporary breathe/hold choice (swell in height, or freeze). It was a separate third display until October 2026; now every look shares one view and one set of layout tests.

### Wave looks
Setting "Wave look", shown while the display is the wave: six looks (`WaveLook`: strobe, ribbon, dots, fireflies, loop, ripples, in that grid order), picked from `WaveLookPicker` cards with live previews (`LookCardGrid`, as the meter picker).
- **One cue, each in its own way:** off pitch something moves one step of the look per strobe period (`WaveMotion.phase`), right or outwards when sharp, left or inwards when flat; within half a cent everything stands still. `unrest(depth)` (0 on the note, ~0.4 a cent off, 1 at 50 cents) shows distance from afar and eases to nothing, so every look settles into one calm shape on the note.
- *Strobe:* see above. *Ribbon:* pulses drift and smooth out (`dial/SoundRibbon.kt`, in each style's marks). *Dots:* a dot-matrix strip scrolling one dot per period (`dotColumnAt`, `edgeFade`). *Fireflies:* eight specks per pulse riding the pulses, scattered by `unrest`, gliding into an even line on the note (`fireflyAt`). *Loop:* a hoop spinning like a coin at the beat rate, its brighter near half sweeping the way the note is off, settling face-on into a still ring (`loopAt`, `loopWidth`, `loopNearSide`). *Ripples:* five rings spreading out when sharp and drawing in when flat, swelling unevenly off pitch, smooth ovals on the note (`rippleAt`, `rippleWobble`).
- Strobe and ribbon use each style's own marks; the other four have one shape in the style's colours.
- Every position is a continuous periodic function of `phase`, so nothing jumps when the direction flips or the phase wraps. JVM tests check each function for direction, stillness on the note and no jumps.
- Drawing allocates nothing: one `Ribbon` refilled, the caller's `Path` reused, strokes from `roundStroke` / `buttStroke`, cached per quarter-pixel width. `drawWave` (`dial/WaveLooks.kt`) draws them; the settings previews use the same `drawWave` through one shared `WaveMotion`.
- **Tests:** `WaveViewTest` (every look in every style drawn inside its box, at most a 4 dp glow outside; motion without recomposing), `TunerContentTest` (TalkBack skips the wave and hears the reading once), `SettingsContentTest` (radio buttons, names on one line at 1.3× and 2× font). The wave's size is the same for every look, so the layout tests cover all six.

### Visual styles
Six styles, in the owner's order (Phase 14, October 2026): Ancient, Classic, Retro, Contemporary (the default, and the only one that can follow the wallpaper), Futuristic and Space Age. Versions before October 2026 had seven; a saved Prehistoric or Middle Ages opens as Ancient (see *Saved settings*). Until Phase 14 gives Ancient its own look, it wears the old Prehistoric palette, faces, shapes, cave background and marks. Each style sets:
- **Palette** (`StylePalettes.kt`): a light and a dark `Palette`, turned into a Material 3 `ColorScheme`. `StylePaletteTest` holds every palette to WCAG 2.2: 7:1 body text, 4.5:1 secondary text, accents and text on filled controls, 3:1 the "in tune" colour, outlines and marks.
- **Lettering** (`StyleTypography.kt`): a display face for the note, headlines and titles; reading text stays plain. Bundled under the SIL OFL (`core/designsystem/src/main/res/font`, licences in `assets/licenses`): Caesar Dressing, Playfair Display, Righteous, Orbitron, Michroma; Contemporary uses the phone's font (MedievalSharp went with the Middle Ages style). ~0.5 MB, nothing downloaded.
- **Music signs** (`MusicSigns.kt`): none of those faces has ♯ or ♭, and the fallback drew them at a different size in each, so every ♯ ♭ ♮ ♩ ♪ comes from `music_signs.ttf` (Noto Sans Symbols cut to those glyphs, 2.6 KB, `scripts/subset-font.py`). `musicText()` sets a sign after a letter like a chord symbol (0.7 em and raised; ♭ 0.8 em). The text itself is unchanged, so UI tests see "C♯"; TalkBack hears words via `NoteNameSpeech`.
- **Shapes:** soft pebbles for Ancient, fine Classic corners, fat 70s curves, cut corners for the HUD, 60s capsules. String buttons (`stringChipShape`, shared by the tuner and the style cards) are circles, chamfered tiles for Futuristic.
- **Background** (`StyleBackdrop`): drawn once by `MainActivity` behind both see-through screens (`Scaffold(containerColor = Transparent, contentColor = onBackground)`), in its own `graphicsLayer` so readings never redraw it, from seeded random points. Cave wall with hand stencils, an ivory concert hall, 70s walnut with a sunburst, a calm wash, a neon grid with HUD brackets, a starfield with a ringed planet. Frames stay inside the safe-drawing insets and within the 16 dp margin screens keep free of text (frames further in crossed the mode switch or covered settings rows); the settings list ends 24 dp above the bottom inset.
- **Mood card** (`StyleMood.kt`): the style's picture on its card in Settings (4:5, from the approved mock-up `docs/design/theme-styles-preview.html`): its background (`StyleBackdropPreview`, decoration at 0.45), a big "A" in its display face, a strip of its ribbon marks in its accent colour (never "in tune", so each card keeps its own colour) drifting gently 3 cents sharp, and two string buttons (E, A picked) in its shape. Sized by the card's width, so it looks the same at any font size; hidden from TalkBack (the card says the name). The per-style hand-drawn dials of the old style cards went with them; `dial/DialGeometry.kt` keeps only the shared ±50-cent angle.
- **Sound ribbon** (`dial/SoundRibbon.kt`) and **strobe** (`dial/Strobe.kt`): the ribbon and strobe looks in each style's own marks (paint dabs, engraving, phosphor, voice-memo bars, neon, stars), each with its own `stripePeriod` (8–18 dp). Every stripe of a row looks the same, so the pattern wraps without a jump.

`TwangleTheme(style, darkTheme, dynamicColor)` provides the `ColorScheme`, `Typography`, `Shapes` and `LocalStyleTokens` (`StyleTokens`: the style, its "in tune" pair, dial colours, the unfaded scheme), remembered per style and brightness. A style or brightness change fades every colour over 450 ms (one `Animatable`) and cross-fades the background. Each mood card wraps its own `TwangleTheme`, so it previews that style whatever the current one is.

### Landscape and wide screens
`tunerLayoutFor(width, height, compactLandscape)` (`TunerLayout.kt`, JVM-tested) picks the arrangement from the space, not the device: `SINGLE_COLUMN` (max 480 dp wide) unless the window is at least 600 dp wide *and* wider than tall. Then `COMPACT` by default, or `TWO_PANE` with the setting "Full layout on its side" (`keepTwoPaneLandscape`). `TunerScreenTest` checks both layouts and a live change on a phone on its side.

**Compact layout** (`CompactLayout`): only what matters while tuning, big enough to read from a distance. The note column is measured first and the meter takes all the width left (`FitGauge`, at most 840 dp, keeping 2:1; about 450 dp on the S26, twice its two-pane size). Mode switch, A4 label, tuning picker, string buttons and tip are hidden; the corner buttons stay. Guitar mode shows a small string row under the note (32 dp chips, not buttons, hidden from TalkBack because the reading names the string); chromatic with the wave shows the `ChromaticReadout`.
- **Tap for the controls:** a tap anywhere, or TalkBack's "Show controls" button, cross-fades to the two-pane layout for 5 s (`COMPACT_CONTROLS_SHOWN_MS`), lengthened by Android's "time to take action" setting. Every touch restarts the time, and an open tuning menu holds it. Local UI state; a rotation starts compact again.
- `CompactLandscapeStylesTest` checks every style and meter look on a phone and a tablet on their side (fit, no overlap, nothing under the corner buttons, reachable at 2× font, drawn in light and dark, "Show controls" works). Layout runs in the light theme only: a style's fonts and shapes don't change with dark.

**Two-pane layout** (`TwoPaneLayout`, also shown while the compact layout's controls are up):
- **Readout pane** (start): `GaugeAboveNote` measures the note first and shrinks the meter (2:1, 160–560 dp wide) so both fit a phone on its side (~330 dp tall).
- **Controls pane** (end): mode switch, A4 label, tuning picker and strings, max 480 dp, with 48 dp clearance top and bottom for the gear.
- Each pane scrolls on its own with very large fonts; both screens use `WindowInsets.safeDrawing` (the camera cutout is at the side in landscape).
- The activity is recreated on rotation (no `configChanges`); mode and tuning are saved, the locked string lives in the ViewModel, and the 5 s grace keeps the `AudioRecord` session.
- **Settings** scrolls the full width but keeps its rows in a centred column at most 600 dp wide.

### Accessibility
- **TalkBack:** `NoteDisplay` is one node whose description is the reading in words ("E 2, 12 cents flat, tune up", "F sharp 2, in tune", "Play a string"), a *polite live region*; its visible texts are `hideFromAccessibility()`. `SpokenReadingThrottle` (pure) decides what TalkBack gets from ~47 updates a second: the first text at once, a new note or in-tune change after 400 ms, losing the note after 1.5 s, cents-only changes at most every 2 s. The meter and wave are hidden (`clearAndSetSemantics {}`).
- **Spoken note names:** every TalkBack text naming a note goes through `NoteNameSpeech` (`:core:designsystem`, `rememberNoteNameSpeech()`, tested against every name the app can show): readout, string chips, locked-string hint, tuning menu and notation choices. ♯/♭ become words ("C sharp"). In Dutch (`spoken_dutch_letter_names`) letter names are said the Dutch way (cis, des, es, bes; b, not h); solfège keeps "Sol kruis", "Mi mol". The octave is the number on screen ("cis 4"). UI tests check Dutch with `DeviceConfigurationOverride.Locales`, which doesn't reach popups, so the Dutch tuning menu is checked on the phone.
- **Labels:** strings read "A 2" plus "selected" / "Locked" and the lock action; the tuning picker says "double-tap to change tuning"; the A4 slider reads "442 Hz". Settings rows are whole-row radio buttons and switches.
- **Large fonts:** the tuner body scrolls when taller than the screen (each pane on its own in two-pane). The 112-unit note name is in dp, so it doesn't grow further; string labels follow the font scale up to 1.3× (fixed 48 dp circles). A UI test checks every control is reachable at 2× font.

### Languages
- **English (default) and Dutch.** Each module keeps its texts in `res/values/strings.xml` with a Dutch copy in `res/values-nl/strings.xml`; brand and symbols (`app_name`, `DADGAD`, ♯/♭, ♪) are `translatable="false"`. Font and library names are proper names and stay in English.
- **Per-app language (Android 13+):** `:app` sets `androidResources.generateLocaleConfig = true` with `res/resources.properties` (`unqualifiedResLocale=en`); `localeFilters` keeps only `en` and `nl`, so library texts never appear in a third language.
- **Formatting:** plurals for counts (`tuner_cents_flat/sharp`) and placeholders for numbers (`%1$.1f Hz`), so Dutch shows "440,0 Hz". Note names are not translated; they follow the notation setting.
- **Guard:** `LocalizationTest` (JVM, `:app`) fails when an English string has no Dutch copy or different placeholders, and when Kotlin passes a hard-coded literal to `Text(...)`, `contentDescription`, `stateDescription`, `onClickLabel` and similar. Lint's `MissingTranslation` and `StringFormat*` back it up.

## 7. Package layout (per module)

```
core/dsp/src/main/kotlin/io/github/klaboukes/twangle/core/dsp/
    pitch/      PitchDetector (interface), PitchResult, YinPitchDetector, UpsamplingPitchDetector,
                AnalysisSettings, LowPassFilter, SignalGate, FftDifference, Fft, Clarity
    level/      NoiseFloorTracker, ReceptionTracker
    music/      Note, NoteMapper, Tuning (+ Tunings presets), Notation
    smoothing/  PitchSmoother, MedianFilter, ExponentialSmoother
    strobe/     StrobeMotion
    buffer/     FrameAssembler
core/dsp/src/testFixtures/…/core/dsp/testsignals/
                TestSignals (sine, saw, square, weak fundamental, pluck, noise, hum, mix, band-limit)

core/audio/src/main/kotlin/io/github/klaboukes/twangle/core/audio/
    AudioRecorder (interface), AndroidAudioRecorder, AudioConfig, AudioRecorderException,
    AudioSourceSelector (+ AudioInput), SampleRateSelector, InputDeviceSelector
core/audio/src/testFixtures/…/core/audio/   FakeAudioRecorder

core/settings/src/main/kotlin/io/github/klaboukes/twangle/core/settings/
    AppSettings (+ TunerMode, InTunePrecision, ThemeMode, VisualStyle, MeterLook, WaveLook, …),
    SettingsRepository (interface), DataStoreSettingsRepository
core/settings/src/testFixtures/…/core/settings/   FakeSettingsRepository

core/designsystem/src/main/kotlin/io/github/klaboukes/twangle/core/designsystem/
    TwangleTheme (+ StyleTokens), StylePalettes, StyleTypography, StyleBackdrop, StyleMood,
    MusicSigns, NoteNameSpeech, FontCredits
    dial/       DialGeometry, SoundRibbon, Strobe, WaveLooks, WaveMotion
    meter/      MeterReading, MeterLooks, MeterArt, MeterFeel, LightArcHeat, SpiritLevel,
                RollingScale, Comet, CometTrail, StudioMeter

core/testing/src/main/kotlin/io/github/klaboukes/twangle/core/testing/
    SharedActivityComposeRule, SharedActivityTestRunner, Thorough (+ allIfThorough)

feature/tuner/src/main/kotlin/io/github/klaboukes/twangle/feature/tuner/
    TunerViewModel, TunerUiState, TunerOptions, TunerInterpreter, TunerLayout, TunerScreen,
    PitchPipeline (+ PitchReading), TunerError, MicPermission, InTuneDetector, InTuneTickRule,
    InTuneGlowRule, TunerHint (+ TuningCue), SteadyHint, ReceptionTip (+ TipCue),
    SignalBars (+ SignalFigures), SpokenReadingThrottle
    components/ MeterFrame, NoteDisplay, WaveView, ChromaticReadout, StringSelector, StringLockTip,
                TuningPicker, ModeSwitch, InTuneColor, InTuneGlow, InTuneHaptic, ReceptionLook,
                SignalBarsButton, PermissionPrompt

feature/settings/src/main/kotlin/io/github/klaboukes/twangle/feature/settings/
    SettingsScreen (+ SettingsContent), AboutScreen, SettingsViewModel
    components/ A4Calibration, RadioGroupSetting, SwitchSetting, LinkSetting, AboutSection, Credits,
                LookCardGrid (+ LookDemo), StylePicker, MeterLookPicker (+ MeterDemo), WaveLookPicker

app/src/main/kotlin/io/github/klaboukes/twangle/app/
    TwangleApplication, AppContainer, MainActivity, SyntheticAudioRecorder (benchmark build only)

baselineprofile/src/main/kotlin/io/github/klaboukes/twangle/baselineprofile/
    BaselineProfileGenerator, StartupBenchmark, MeterFramesBenchmark, TunerJourney
```

## 8. Testing strategy
| Layer | How |
|---|---|
| `:core:dsp` | JVM tests with `TestSignals` (`testFixtures`, shared via `testImplementation(testFixtures(project(":core:dsp")))`): sines, saw/square, weak fundamentals, white noise, mains hum, band-limited audio at any rate, decaying plucks. Assert ±1 cent. `EdgeCaseTest` runs whole recordings through the app's chain (`TunerChain`) and checks what would be shown (§3, Edge cases). Recorded WAV fixtures go in `src/test/resources/`. Allocation-free paths have byte-counting tests. |
| `:core:audio` | JVM tests for source, rate and Bluetooth selection and chunk sizes. Instrumented tests record from the real mic (don't play test tones during them). `FakeAudioRecorder` (`testFixtures`) plays a `FloatArray` as chunks paced like a mic (instant under `runTest`), can loop or fail, and reports `isRecording` / `startCount`, so everything downstream, mic lifetime included, runs without a device. |
| `:core:settings` | JVM tests on a real DataStore file: defaults, saving, restart, clamping, unknown or unreadable values, migrations. |
| `:core:designsystem` | JVM tests: WCAG contrast of every palette, distinct styles, `ThemeMode.useDarkTheme`, font credits, the meter and wave maths. |
| `:feature:tuner` | ViewModel and interpreter tests with `kotlinx-coroutines-test` and the fake recorder; Compose UI tests for each state, layout, TalkBack and every look. |
| `:feature:settings` | `SettingsViewModel` tests with `FakeSettingsRepository`; `CreditsTest`; Compose UI tests (`SettingsContentTest`) for each control, About and the licence dialogs. |
| `:app` | `LocalizationTest` (JVM); instrumented `MicLifecycleTest` (§5). |
| `:baselineprofile` | On demand, device needed (§4). |
| Running UI tests | Always `scripts/ui-tests.sh` (the [ui-test skill](.claude/skills/ui-test/SKILL.md)), never Gradle's `connectedDebugAndroidTest`, which is 15–25 s slower per module and uninstalls the app. The script builds every test APK in one Gradle call, then installs and runs each module with `adb shell am instrument` under a time limit, prints one line per module and runs only the modules a branch touches. `-a key=value` passes an instrumentation argument. |
| Device | Manual checklist per release: real guitar, noisy room, Bluetooth headset, permission denial paths. |

### Fast UI tests
Compose UI tests use `createSharedActivityComposeRule()` (`:core:testing`): one activity for the whole run, a fresh composition (own recomposer, test clock and `ViewModelStore`) per test. `SharedActivityTestRunner` stops AndroidJUnitRunner closing every activity after each test, which took ~0.7 s per test on the Xiaomi 12X; together they cut the tuner suite from ~8½ to ~3 minutes. Pixel checks take one capture and cut it up (a capture costs a frame), and build assertion messages only on failure. A test that changes the activity itself (rotation, recreation, window flags) still uses `createComposeRule()` or `ActivityScenario`. How to write them: [writing-tests.md](.claude/skills/ui-test/writing-tests.md).

### Quick and thorough UI runs
While the look is still changing (October 2026 until Phase 23), a normal run leaves out the double checks: style test classes (`MeterLooksTest`, `CompactLandscapeStylesTest`, `StringLockTipStylesTest`, two checks in `WaveViewTest`) run in the default style only (`allIfThorough(VisualStyle.entries, VisualStyle.DEFAULT)`), and `@Thorough` tests (variants of a quicker test in another font size, language, orientation or view) are skipped. `--thorough` runs everything. This cut the tuner suite from ~3 minutes to ~1. Each family keeps its hardest case in the quick run (e.g. Dutch at 2× font on a phone on its side), so most layout breaks still show at once.

## 9. Privacy
Audio is processed in memory and thrown away immediately. Nothing is recorded to disk or sent over the network, and there is no `INTERNET` permission. The merged manifest holds only `RECORD_AUDIO`, `VIBRATE` (in-tune tick) and AndroidX's internal `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.

The privacy policy is [PRIVACY.md](PRIVACY.md), with a Dutch copy in [PRIVACY.nl.md](PRIVACY.nl.md); keep the two in sync. Settings stay in DataStore on the device, and because `android:allowBackup="true"` (no extraction rules) Android's own backup and device transfer may include them. If a change touches permissions, stored data, backup or anything that leaves the device, update the policy (and its effective date) in the same change. The About page shows the gist; the Play listing will link to a public copy (Phase 23), and the data-safety form will say no data is collected or shared.
