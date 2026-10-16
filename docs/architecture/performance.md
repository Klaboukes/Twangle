# Threading and performance

Part of §4 of [ARCHITECTURE.md](../../ARCHITECTURE.md), which lists every topic file.

## Contents
- [Threads and the main thread](#threads-and-the-main-thread)
- [Measured on the Galaxy S26](#measured-on-the-galaxy-s26)
- [Low-note window on the Xiaomi 12X](#low-note-window-on-the-xiaomi-12x)
- [Watch CPU and the native detector](#watch-cpu-and-the-native-detector)
- [Meter looks](#meter-looks)
- [Baseline Profile](#baseline-profile)
- [Release build: shrinking and signing](#release-build-shrinking-and-signing)

## Threads and the main thread
- `AudioRecord.read()` blocks, so it runs on `Dispatchers.IO.limitedParallelism(1)`. DSP runs on `Dispatchers.Default`. The reference tone's and the metronome's blocking `AudioTrack.write()` each have their own single thread too ([Notes, tunings and audio](notes-and-audio.md), *Reference tone*; [Strum check and metronome](strum-and-metronome.md), *Metronome*).
- **No allocations in the per-frame loop.** Buffers are allocated once; per-frame garbage at ~47 Hz causes GC jank.
- **YIN difference function via FFT.** The direct O(W·τ_max) sum (~2.6 M multiply-adds per frame) took 69 % of the app's CPU cycles on the S26 (~7 ms per frame). `FftDifference` computes `d(τ) = e(0) + e(τ) − 2·r(τ)` (paper, eq. 7): energies as a running sum, the cross-correlation from one complex FFT carrying both real inputs plus one inverse (N = 4096, or 8192 for low notes; radix-2 `Fft`, doubles, no allocation). `FftDifferenceTest` checks it within 1e-10 of the direct sum. `./gradlew :core:dsp:benchmark` (kept out of CI): YIN **1.05 → 0.48 ms** per frame on the JVM. Budget: 2 ms.
- **FFT passes block by block.** Run twiddle by twiddle, each pass sweeps the whole arrays once per twiddle; at N = 8192 (128 KB of doubles) they don't stay in the CPU's cache, and the low-note window cost 4× the standard one on the JVM. Walking each pass block by block, in memory order, gives identical results and: JVM 0.48 → **0.18 ms** (4096), 2.0 → **0.49 ms** (8192).
- **Only visible changes reach the main thread:**
  - **Silence:** equal `Listening` states are dropped with `distinctUntilChanged()` on the DSP dispatcher.
  - **ViewModel:** `distinctUntilChanged { old, new -> old.looksLike(new) }` drops readings that would look the same as the last one *sent*: same text (whole cents, 0.1 Hz) and the needle within `NEEDLE_STEP_CENTS` = 0.03 cents (below a pixel) or pinned at the same edge, reception within `RECEPTION_STEP` (0.02). `looksLike` allocates nothing; `TunerUiStateShownTest` fails if `Detected` gains a field it doesn't compare.
  - **Composition:** `TunerScreen` composes from `state.asShown()` through `derivedStateOf`, so it recomposes only when text changes. Exact cents and reception reach the meter and wave as lambdas read only in layer and draw phases.
  - **Needle:** `MeterFrame` follows the lambda with `snapshotFlow` into an `Animatable` spring (each new target `launch`ed so the spring keeps its speed). Moving parts sit in their own `graphicsLayer` and only change its transform, so a moving needle neither recomposes nor re-records drawing.
  - **Frame rate:** on Android 15+ a plain Compose animation gets the "normal" rate category (60 Hz on the S26), so the meter asks for 120 Hz with `Modifier.preferredFrameRate(120f)`, which only counts while it draws.

## Measured on the Galaxy S26
Phase 5. Minified `benchmarkRelease` build, portrait, CPU per thread from `/proc/<pid>/task/*/stat` (percent of one core).

| | Before (direct YIN, JIT only) | After (FFT YIN + Baseline Profile) |
|---|---|---|
| Silence: whole app | 7.8 % (main thread 2.3 %) | 5.6 % (main thread ≈ 0) |
| Note sounding: whole app | ≈ 45 % | ≈ 20 % |
| Note sounding: DSP thread | ≈ 34 % (≈ 7 ms/frame) | ≈ 9 % |

Main thread with identical synthetic input, before and after the recomposition work above: guitar mode 11.6 % → 5.1 %; chromatic 15.8 % at ~75 frames/s → 12.2 % at ~110 frames/s (≈ 2.1 → 1.1 ms per frame). Nothing is drawn in silence. Battery: ≈ 0.8 mAh per hour of listening for the app's CPU; the screen costs far more.

## Low-note window on the Xiaomi 12X
`scripts/dsp-phone-benchmark.sh` dexes `:core:dsp` and runs `DspPhoneBenchmark` with `dalvikvm` on the phone (ART with JIT, not a debuggable build; no app, no sound, ~20 s). 48 kHz, 2,000 warm-up and 1,000 timed frames of bass, guitar and noise test signals; CPU from `/proc/thread-self/schedstat`.

| Per frame (hop = 21.3 ms) | Standard, 4096 | Low notes, 8192 | Low notes, before the FFT change |
|---|---|---|---|
| Detector, wherever the scheduler puts it | 0.31 ms | 0.69 ms | 0.98 ms |
| Gate + detector + smoother | 0.33 ms (1.5 % of a core) | **0.74 ms (3.5 %)** | 1.03 ms |
| Gate + detector + smoother, little cores only (`TASKSET=0f`) | 2.2 ms (10 %) | **5.1 ms (24 %)** | |

So the low-note window costs about 2.3× the standard one and stays far inside the hop even on a little core. Guitar tunings keep the standard window and its cost.

## Watch CPU and the native detector
A C++ (NDK) YIN was planned (Phase 21) only if the Kotlin one proved too slow, for the low-note window, strum check or the watch. The watch had no measurement, so `DspPhoneBenchmark` also times the watch's chain as `WatchPitchPipeline` runs it (gate, sustain check, noise floor, detector, smoother, in-tune detector; standard range) at 16 kHz, where many watch mics record and frames are upsampled ×2 before YIN, and at 48 kHz. The Xiaomi 12X's little cores (`TASKSET=0f`: Cortex-A55, 1.8 GHz) stand in for a watch's CPU (Snapdragon W5: Cortex-A53, 1.7 GHz; Exynos W930/W1000: Cortex-A55, 1.4 GHz); the x86 emulator says nothing. Measured 2026-10-15, same method as above.

| Per frame (hop = 21.3 ms) | Big core, unpinned | Little cores (`TASKSET=0f`) |
|---|---|---|
| Watch chain, 16 kHz (1365-sample window, 341 hop) | 0.46 ms (2.2 %) | **2.7 ms (13 %)** |
| Watch chain, 48 kHz (4096 window, 1024 hop) | 0.33 ms (1.6 %) | 2.2 ms (10.5 %) |
| Phone: gate + detector + smoother, standard / low notes | 0.33 / 0.75 ms (1.5 / 3.5 %) | 2.3 / 5.2 ms (11 / 24 %) |
| Strum check, standard / low notes, every 0.1 s | 2.6 / 5.2 ms (≈ 3 / 5 %) | 16 / 32 ms (16 / 32 %) |

The ×2 upsampling makes 16 kHz a little dearer than 48 kHz. Allowing for a slower watch core (up to ≈ 1.5×) the watch tuner takes about 15–20 % of one of its cores, with a reading ready in a few ms of each 21 ms hop and nothing else heavy running (the screen is the watch's real battery cost). On the phone the tuner runs on a big core at under 4 %.

**Decision: no native detector.** Nothing comes near the budget: the dearest case, the low-note strum check pinned to a little core, still finishes in a third of its 0.1 s, and the phone's scheduler gives it a big core. A C++ YIN would at best halve these numbers while adding the NDK to the build, JNI, and a second detector to keep within ±1 cent. Look again only if a real watch drops readings or warms up, or a new feature runs YIN many times per frame; `scripts/dsp-phone-benchmark.sh` repeats these numbers in about a minute.

## Meter looks
Measured on the S26 (Android 16, portrait) with `scripts/meter-frames.sh`, which runs `MeterFramesBenchmark` against the minified `benchmarkRelease` build (~4 min, phone unlocked and upright; `scripts/meter-frames.sh CLASSIC:LIGHT_ARC` for some looks, `CURRENT` for whatever the app shows).
- **Input:** that build hears `SyntheticAudioRecorder` (`:app`, behind `BuildConfig.SYNTHETIC_AUDIO`, a constant `false` elsewhere, so R8 removes it from release): A4 sweeping 30·sin³ over 8 s plus a 2-cent wobble (`SweepTone`), noise 20 dB below. The marker never stands still and reaches in tune twice per sweep.
- **Method:** per look, chromatic mode, cents display, 3 windows of 7 s; frames from `dumpsys gfxinfo`, CPU from `/proc`. The old needle row is commit 4826b6e with the same input and frame-rate request.

| Chromatic, meter moving | Frames/s | Late frames (of ~2,500) | p50 / p90 / p95 / p99 | Main thread | Main per frame | RenderThread |
|---|---|---|---|---|---|---|
| Old needle (same build and input) | 120 | 1 | 5 / 8 / 9 / 11 ms | 22.0 % | 1.83 ms | 19.0 % |
| Classic | 120 | 3 | 8 / 10 / 11 / 12 ms | 19.3 % | 1.60 ms | 27.0 % |
| Light arc | 119 | 0 | 8 / 10 / 10 / 12 ms | 19.8 % | 1.66 ms | 28.5 % |
| Spirit level | 120 | 3 | 13 / 17 / 17 / 18 ms | 17.1 % | 1.43 ms | 28.2 % |

Every look draws at 120 Hz with at most 0.5 % late frames, and uses less main thread than the old needle (±2 points between runs, brackets = repeat runs). At 120 Hz the pipeline may take more than one vsync, so p50 above 8.3 ms is not a dropped frame. Before the frame-rate request every look ran at 60 Hz.

**On the Xiaomi 12X** (Android 13, 120 Hz; the same script, Contemporary, the style a fresh install opens in; with the style finish of needles, hubs and the larger scale, which changed nothing beyond run-to-run noise: before-and-after in [history](history.md)). Slower than the S26 throughout; brackets = a repeat run.

| Chromatic, meter moving | Frames/s, late (of ~2,400), p50 / p90 / p95 / p99, main per frame |
|---|---|
| Classic | 110, 567, 13 / 23 / 27 / 34 ms, 3.73 ms (114, 398, 11 / 21 / 24 / 34 ms, 3.39 ms) |
| Light arc | 115, 350, 12 / 19 / 22 / 30 ms, 3.34 ms (116, 271, 11 / 18 / 21 / 31 ms, 3.10 ms) |
| Spirit level | 103, 1,258, 16 / 26 / 31 / 40 ms, 3.18 ms |

Eclipse, Arrows and Light bars (Phase 25) have no row yet; `scripts/meter-frames.sh` measures them.

## Baseline Profile
Lists the classes and methods used at startup and on the tuner screen so Android compiles them ahead of time.
- `:baselineprofile` has `BaselineProfileGenerator` (cold start, listening, switching modes, settings and back), `StartupBenchmark` and `MeterFramesBenchmark`. UI Automator finds controls by test tag (`TunerTestTags`, exposed as resource ids with `testTagsAsResourceId`).
- Generate with the phone connected: `./gradlew :app:generateBaselineProfile` (~5 min). It writes `app/src/release/generated/baselineProfiles/`, which is committed; `profileinstaller` installs it for sideloaded APKs.
- Startup: `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest`. On the S26 cold start was ~89 ms with or without the profile; it stays because it is free and matters more on slower phones. Neither task runs in CI.

## Release build: shrinking and signing
- **Shrinking:** R8 (`isMinifyEnabled`) and resource shrinking; the APK went from 9.3 MB to 1.8 MB. The app uses no reflection; `app/proguard-rules.pro` only keeps the enums settings save by name (`TunerMode`, `InTunePrecision`, `ThemeMode`, `VisualStyle`, `TunerDisplay`, `WaveLook`, `MeterLook`, `InTuneEffect`, `Notation`, and the metronome's `Meter`) as a safety net; a new enum saved by name gets a rule too. **Never rename saved enum constants.**
- **Signing:** the release builds of both apps (phone and watch, which share an app ID) use the owner's **upload key**, never in the repo; the root `build.gradle.kts` reads it once and both modules sign with it: `~/.twangle-keys/` (`twangle-upload.jks`, alias `upload`, with `keystore.properties` and a README), or the path in the Gradle property `twangle.keystoreProperties`. Without it (CI, other machines) the release builds are unsigned and nothing fails; CI builds them anyway (`assembleRelease bundleRelease`) so R8, resource shrinking or bundle breakage shows up the next morning. `.gitignore` excludes `*.jks`, `*.keystore` and `keystore.properties`. The Play Store will use Play App Signing, so a lost upload key can be reset.
- `./gradlew assembleRelease` (APK) or `bundleRelease` (AAB); check with `apksigner verify --print-certs`.
- **Version:** one place for both apps, `twangle.version` in `gradle.properties` (`1.0.0` now; major.minor.patch, each 0–99, no suffix). The root `build.gradle.kts` makes it the `versionName` and `versionCode` = major × 10000 + minor × 100 + patch (10000 now); the watch's code adds 1,000,000 (1,010,000). Every build that leaves the PC needs a higher version. About shows `versionName`.
- Release and debug APKs have different signatures: switching on a phone means uninstalling first, which clears saved settings.
- **Hidden until 1.1** (owner, 2026-10-16): work on the after-v1.0 tools (ROADMAP Phases 29–33) lands on `main` while v1.0 is still going through Play. Each tool has its own switch, on in debug builds and off in release builds, so the Play build stays the v1.0 app (Phase 28):
  - **The switches** are `ToolSwitches` (`:core:settings`, which every `:feature` module already sees): one immutable boolean per tool, `intonationCheck`, `chordNaming`, `pitchTrail`, `practiceTab` and `capoHelper` (which needs `practiceTab`, since the capo helper lives in Practice). They are not saved settings: nothing is stored and the player can't change them.
  - **One place decides them:** `toolSwitchesFor(debugBuild)` in `:app` (`BuildToolSwitches.kt`), fed `BuildConfig.DEBUG`. So only the `debug` build type shows the tools; `release`, and the `benchmarkRelease` and `nonMinifiedRelease` builds the Baseline Profile plugin makes from it (frame-time runs, the profile generator), show the v1.0 app.
  - **Passed down, never looked up:** `AppContainer.toolSwitches` starts as this build's; `MainActivity` hands it to `TunerScreen` and `MetronomeScreen` (a required parameter, so the app can't forget it), which give it to `TunerViewModel.tools` and `MetronomeViewModel.tools`. The bar (Practice in place of Metronome) is drawn by `:app` itself. A tool reads its switch where it adds a mode, tab, setting or tour step, so with the switch off it leaves no trace on screen; a new `TunerMode` entry, for one, must be left out of the mode switch, the tour and the saved mode when its switch is off (the release build would otherwise reopen in it).
  - **Tests:** `ToolSwitchesTest` (`:core:settings`) keeps `byName` complete; `DebugToolSwitchesTest` (`app/src/testDebug`) checks every switch is on in debug, `ReleaseToolSwitchesTest` (`app/src/testRelease`, run by `testReleaseUnitTest`, which `:app` enables and `test` includes) every one off in release; `:app` runs it in `nonMinifiedRelease` and `benchmarkRelease` too (their own unit tests, also part of `test`), so the Baseline Profile and the frame-time and startup runs are made on the v1.0 app, whatever the debug build shows. ViewModels default to `ToolSwitches.ALL_ON` in their JVM tests; a feature UI test passes the switches it wants to the screen, and a UI test of the whole app sets `AppContainer.toolSwitches` (e.g. `ALL_OFF`) before it starts the activity and puts it back after: `ToolSwitchesOffRule` (`app/src/androidTest`) does both, as the outer rule. Every UI test whose pictures stand for the released app uses it, the store screenshots (Phase 23) first of all (`ToolSwitchesOffTest`: the v1.0 tuner modes and bar, nothing more; each tool's phase adds what it must not show there).
  - **Going live (Phase 34):** the owner picks the finished tools; each one's line in `toolSwitchesFor` becomes `true`, or the switch and its checks go altogether, and `ReleaseToolSwitchesTest` is updated to match. Unfinished tools stay off.
