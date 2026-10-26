# CLAUDE.md

Guidance for Claude Code (and humans) working in this repository.

## Project
**Twangle** is an Android guitar tuner (chromatic + guitar-string modes). It captures microphone audio, finds pitch with the YIN algorithm, and shows the deviation in cents on a Compose needle gauge.

The app was called InTune until October 2026. The app ID is `io.github.klaboukes.twangle`, and code packages live under it. Classes named `InTune*` (`InTuneDetector`, `InTuneColor`, `InTuneHaptic`, `InTuneTickRule`) are about being *in tune*, not the old name. Keep them.

- Design and rationale: [ARCHITECTURE.md](ARCHITECTURE.md), a short overview whose Contents lists the topic files in `docs/architecture/` (signal pipeline, UI, settings, performance, testing and more), each small enough to read whole. Before a structural change, read that Contents and the topic files the change touches. Superseded designs go to `docs/architecture/history.md`, not into the live text.
- Plan and progress: [ROADMAP.md](ROADMAP.md). Tick items (☐ → ☑) as you finish them. Finished phases move to [ROADMAP-ARCHIVE.md](ROADMAP-ARCHIVE.md); the owner's hand-run live tests are listed under *Owner checks*. Ideas not planned yet go in [IDEAS.md](IDEAS.md), and become a ROADMAP phase once the owner picks one.

## Tech stack
Kotlin 2.4 · Jetpack Compose + Material 3 · Coroutines/Flow · AndroidX ViewModel · `AudioRecord` · manual DI · DataStore · Gradle 9 Kotlin DSL with version catalog · JUnit 4.
minSdk 26, targetSdk/compileSdk 37, JVM toolchain 17.

**AGP 9 uses built-in Kotlin:** Android modules must NOT apply `org.jetbrains.kotlin.android`. Only `:core:dsp` applies `kotlin-jvm`, and Compose modules apply `kotlin-compose`. The `kotlin-jvm` entry in the root `build.gradle.kts` pins the Kotlin compiler version.

## Modules
| Module | Type | Purpose |
|---|---|---|
| `:app` | Android app | `MainActivity` (switches tuner ↔ metronome ↔ settings ↔ About through the navigation bar or rail, draws the style's background), `TwangleApplication`, `AppContainer` (DI), the home-screen widget (`widget/`) |
| `:wear` | Android app (Wear OS) | The standalone watch tuner, same app ID as `:app`: `WatchActivity`, `WatchTunerViewModel`, `WatchPitchPipeline` (reuses `:core:dsp` and `:core:audio`), Compose for Wear OS |
| `:feature:tuner` | Android library | Tuner screen UI, `TunerViewModel`, `TunerUiState` |
| `:feature:settings` | Android library | Settings and About screen UI (incl. the style, meter and wave look card grids), `SettingsViewModel` |
| `:feature:metronome` | Android library | Metronome screen UI (meter picker, beat lights, tempo dial, tap tempo), `MetronomeViewModel` |
| `:feature:practice` | Android library | Practice tab (Phase 32, behind its switch): the tool button (`PracticeToolButton`) and the Chords tool (`ChordsScreen`); `:app` puts the metronome and chords under it |
| `:core:audio` | Android library | `AudioRecorder` → `Flow<FloatArray>` microphone frames; `TonePlayer` (reference tones) and `MetronomePlayer` (clicks) through `AudioTrack` |
| `:core:settings` | Android library | `SettingsRepository` (DataStore): A4, tuning, notation, style, theme, keep screen on, metronome tempo/meter/vibration, Practice tool used last |
| `:core:designsystem` | Android library | `TwangleTheme` and the six visual styles: palettes, bundled fonts, backgrounds, style mood cards (`StyleMood`), meter and wave looks, the metronome's tempo dial and beat lights, the segmented switch (`SegmentedSwitch`) |
| `:core:dsp` | **Pure Kotlin/JVM** | Pitch detection (YIN), note math, tunings, smoothing, string synthesis (the reference tones), metronome click track and tap tempo |
| `:core:testing` | Android library | UI test helpers only: `createSharedActivityComposeRule()` and `SharedActivityTestRunner` (one activity for the whole run, much faster), `@Thorough` and `allIfThorough` (double checks, only in `--thorough` runs, i.e. overnight), `@EveryLanguage` and `assertEveryTextWhollyShown` (every language, overnight), `assertWhollyShown` (nothing clipped or cut off), `PhoneScreen` (a forced window size with fake system bars, the same on every phone) |
| `:baselineprofile` | Android test | Generates the Baseline Profile and measures startup (on demand, device required) |

## Commands
Run from the repo root. On Windows use `gradlew.bat`, or `./gradlew` in Git Bash.
```bash
./gradlew assembleDebug                 # build debug APK
./gradlew :app:installDebug             # install the phone app on the connected phone (plain installDebug also puts the watch app on it)
./gradlew test                          # all JVM unit tests
./gradlew :core:dsp:test                # DSP tests only (fast, no Android)
./gradlew :core:dsp:benchmark           # DSP timing benchmarks (on demand, not in CI)
./gradlew lint                          # Android lint
scripts/ui-tests.sh                     # on-device UI tests, only the modules this branch touches (see /ui-test)
scripts/ui-tests.sh --since-pass        # before a release: only the modules changed since they last passed whole on this PC
scripts/overnight-ui-tests.sh           # the overnight run: every UI test in every style, then every language, then the store screenshots (~2–3 h, see /overnight-run)
scripts/store-screenshots.sh [locale…]  # the Play Store screenshots on the phone (~2 min a language; git-ignored, docs/store-listing.md)
scripts/meter-frames.sh                 # frame times of every meter look on the phone (synthetic tone, ~4 min)
scripts/dsp-phone-benchmark.sh          # pitch detector CPU per frame on the phone (no app, no sound, ~20 s)
scripts/metronome-drift.sh              # metronome steadiness on the phone: 120 BPM for 10 min, quietly (~11 min)
./gradlew :app:generateBaselineProfile  # regenerate the Baseline Profile (device, ~5 min); commit app/src/release/generated/
scripts/release.sh bump|notes|bundle|upload|promote|rollout|halt|listing  # a Play release: version, notes, signed bundle and tag, then (each after the owner's yes) internal track, promotion, staged rollout, store listing (see /release)
scripts/release.sh status|health [days]|reviews [language]|listing-changes  # read only, no yes needed: what is on each Play track, crash/ANR rates per version vs Play's lines, the last week's reviews translated, what changed for the store listing since it was last sent
```
### Dev machine environment (Windows)
- No system-wide Java. Use Android Studio's bundled JDK for every Gradle command:
  `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` (Git Bash) or
  `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"` (PowerShell).
  Gradle runs on JBR (JDK 25). The foojay plugin auto-provisions JDK 17 for compilation.
- Android SDK: `%LOCALAPPDATA%\Android\Sdk`. `adb` is at `<sdk>/platform-tools/adb.exe` and is not on PATH.
- `local.properties` (git-ignored) holds `sdk.dir=C\:/Users/<user>/AppData/Local/Android/Sdk`. The colon must be escaped or lint fails.
- Emulator AVD `Medium_Phone_API_37.0` exists but needs the **Windows Hypervisor Platform** feature enabled.
- Wear OS emulator: AVD `Wear_OS_Large_Round` (Wear OS 6, API 36, `system-images;android-36;android-wear-signed;x86_64`). Start it with `<sdk>/emulator/emulator.exe -avd Wear_OS_Large_Round`, then `./gradlew :wear:installDebug`. `<sdk>/cmdline-tools/latest` has `sdkmanager` and `avdmanager`; from Git Bash, write package paths with `/` instead of `;`.
- **Test phones.** The owner takes turns between the two for the overnight run. UI tests give the same answer on either (`PhoneScreen` in `:core:testing`).
  - **Xiaomi 12X**, the main development and test phone (adb serial `f813c98c`, Android 13 / MIUI 14, ≈ 393 × 873 dp, gesture navigation, up to 120 Hz), since October 2026. It is the owner's old phone, so it is usually there.
  - **Samsung Galaxy S26**, the owner's daily phone (adb serial `R3GL502L08P`, Android 17 / API 37, 360 × 780 dp, three navigation buttons, always edge to edge). It is plugged in only on some days and every other night, so don't count on it. Measurements in the architecture docs and the archive from before October 2026 come from the S26 (then on Android 16), and say so.
- Xiaomi quirks:
  - MIUI needs *Developer options → Install via USB* for `:app:installDebug`, and *USB debugging (Security settings)* for the UI tests and `adb shell input`. Without them installs fail with `INSTALL_GRANT_RUNTIME_PERMISSIONS` or `INSTALL_FAILED_USER_RESTRICTED`. *MIUI optimization* is off.
  - MIUI also blocks tests from opening an activity unless Xiaomi's background-start app-op is set, which every install resets; `scripts/ui-tests.sh` keeps setting it during a run (more in the ui-test skill).
- Either phone:
  - The owner works from a laptop and a desktop, each with its own debug key, so the phone's Twangle may be signed by the other machine (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). The owner's rule, on both phones: during development, just uninstall it (`adb uninstall io.github.klaboukes.twangle`) and reinstall. Losing Twangle's settings and own tunings is fine.
  - Leave every other setting on the phone as it was, above all on the S26, the owner's daily phone: put back anything a check changes (font size, rotation, screen timeout, developer options); `scripts/ui-tests.sh` restores the rotation itself.
  - `adb devices` empty or `unauthorized`: accept the USB-debugging prompt on the phone and set the USB mode to *File transfer*.

### CI
`.github/workflows/android.yml` runs `test`, `lint`, `assembleDebug` and an unsigned `assembleRelease bundleRelease` (the watch app included) **once a night** at 03:00 UTC on `main`, plus by hand through *Run workflow*, never per push or PR: the local run is the gate. A run per merge repeated that check and used up the free plan's 2,000 minutes a month in one week (2026-10-16). The nightly run covers everything merged since the last successful run and skips the build when only `*.md`, `docs/`, `.claude/` or `scripts/` changed. A run GitHub refused to start (spending limit, storage quota) is not a code failure. It uploads no APK (nobody downloaded it, and the free plan's artifact storage is small), only build reports when it fails (kept 3 days).

## Hard rules
1. **`:core:dsp` must never depend on Android.** No `android.*` imports and no Android library dependencies. It is a `kotlin("jvm")` module so it stays fast to test and portable.
2. **No allocations in the audio/DSP hot loop.** Preallocate buffers (ring buffer, YIN scratch arrays) and reuse them. The loop runs ~47×/s.
3. **Never block the main thread.** `AudioRecord.read()` runs on a dedicated single-thread dispatcher. DSP runs on `Dispatchers.Default`.
4. **Hold the mic only while the tuner is visible.** Use `SharingStarted.WhileSubscribed(5_000)` plus `collectAsStateWithLifecycle()`. No foreground service.
5. **Never use `AudioSource.MIC` or `VOICE_COMMUNICATION`.** Use `UNPROCESSED`, falling back to `VOICE_RECOGNITION`. AGC and noise suppression corrupt pitch.
6. **Any DSP change needs tests.** Add or adjust synthetic-signal tests in `core/dsp/src/test`. Accuracy target: ±1 cent on synthetic signals.
7. **Privacy:** audio is never written to disk or sent anywhere. Do not add the `INTERNET` permission.
8. Dependency versions live only in `gradle/libs.versions.toml`. Never hard-code versions in module build files.
9. **Free forever:** no ads, no account creation, no in-app purchases, no analytics or tracking SDKs. This promise is stated in README.md.

## Conventions
- Kotlin official code style (`kotlin.code.style=official`), 4-space indent, see `.editorconfig`.
- Packages: `io.github.klaboukes.twangle.<module path>`, e.g. `io.github.klaboukes.twangle.core.dsp.pitch`. Source sets use `src/main/kotlin`.
- UI state: one immutable `TunerUiState` sealed interface exposed as `StateFlow` from the ViewModel. Composables are stateless and take state plus lambdas.
- Implementations go behind interfaces (`PitchDetector`, `AudioRecorder`) so they can be swapped (fake recorder, FFT-based YIN, NDK).
- Frequencies are `Float` in Hz, deviations are `Float` in cents, and sample buffers are `FloatArray` in −1..1.
- Strings shown to users go in `res/values/strings.xml` with a translation in `res/values-<language>/strings.xml` for every language in `twangle.languages` (`gradle.properties`; today Dutch), never hard-coded in Composables (`LocalizationTest` checks both). Adding a language: [docs/architecture/languages.md](docs/architecture/languages.md).
- **The music tools (Phases 29–33) are live in every build since Phase 34** (owner, 2026-10-17); each keeps its switch (`ToolSwitches`, all on). A new tool still being built stays hidden in release builds behind its own switch until the owner's check passes ([docs/architecture/performance.md](docs/architecture/performance.md) *Release build*). The release comes next: Phase 53 on Play (Phases 37 and 38 are done); fixes land on `main` as usual.
- Keep functions small and pure in `:core:dsp`. Document any formula with a short comment that cites its source (e.g. de Cheveigné & Kawahara 2002 for YIN).

## Workflow
- **Use the `/roadmap-step` skill** (`.claude/skills/roadmap-step/SKILL.md`) to do roadmap work. Without an argument it runs one increment: announce in plain language → branch → build → verify → land. `/roadmap-step <N>` finishes every remaining item of phase N in one session, still one branch and PR per increment; each increment runs in its own sub-agent so the session stays small.
- Work through ROADMAP phases in order. Phase N+1 work should not start while phase N's builds or tests are broken.
- Before calling a task done, run `./gradlew test lint assembleDebug`. Changes the user can see or hear also get an on-device check on a test phone (the Xiaomi 12X, or the S26 when it is plugged in).
- **Use the `/ui-test` skill** (`.claude/skills/ui-test/SKILL.md`) for on-device UI tests: only the tests you're working on while iterating, the affected modules in full once before landing. Never call `connectedDebugAndroidTest` directly.
- **Use the `/overnight-run` skill** (`.claude/skills/overnight-run/SKILL.md`) when the owner starts the overnight run: every UI test in every style and every language, fixed and landed without asking them anything (questions go in the morning summary).
- **Use the `/release` skill** (`.claude/skills/release/SKILL.md`) to put what is on `main` on Google Play: version, release notes in every language, signed bundle, tag; the owner's Play Console steps are in [docs/release.md](docs/release.md).
- **Use the `/add-language` skill** (`.claude/skills/add-language/SKILL.md`) to add a language: translations, language list, note-name default, fonts, right-to-left, phone check.
- When a design decision changes, update the matching architecture file (ARCHITECTURE.md or its topic file) in the same change.
- The project owner doesn't write code. Talk to them in plain language about outcomes and the actions they need to take.
- Open every mock-up and preview page (`docs/design/*.html`) for the owner in Firefox (`C:\Program Files\Mozilla Firefox\firefox.exe <full path>`), never in the app's built-in browser, which doesn't play their sounds (owner, 2026-10-26).

## Git & GitHub
Remote: `https://github.com/Klaboukes/Twangle` (private, free plan). Default branch: `main`.

**Every change goes through its own branch and pull request. Never commit directly to `main`.**
1. `git switch main && git pull`
2. Branch: `phase-<N>/<short-kebab-topic>` for roadmap work (e.g. `phase-1/yin-detector`), `fix/<topic>` for repairs, `chore/<topic>` for tooling and docs, `release/v<version>` for a release's version and notes.
3. Commit in small, focused commits (imperative subject line, ≤ 72 chars).
4. Tick the finished items in ROADMAP.md (☐ → ☑) in the same branch.
5. Verify locally (see Workflow). **The local run is the merge gate.** The free plan for private repos has no branch protection or auto-merge, so nothing on GitHub blocks a bad merge.
6. `git push -u origin <branch>`, then `gh pr create --base main` (title = increment name, body = what changed + how it was verified).
7. `gh pr merge --squash --delete-branch`, then `git switch main && git pull`. Merging right away is the owner's chosen policy. CI checks `main` overnight, and the next iteration checks the last run first and fixes any failure before anything else.

`gh` is installed at `C:\Program Files\GitHub CLI\gh.exe`. Terminals opened before the install may not have it on PATH.
