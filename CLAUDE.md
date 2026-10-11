# CLAUDE.md

Guidance for Claude Code (and humans) working in this repository.

## Project
**Twangle** is an Android guitar tuner (chromatic + guitar-string modes). It captures microphone audio, finds pitch with the YIN algorithm, and shows the deviation in cents on a Compose needle gauge.

The app was called InTune until October 2026. The app ID is `io.github.klaboukes.twangle`, and code packages live under it. Classes named `InTune*` (`InTuneDetector`, `InTuneColor`, `InTuneHaptic`, `InTuneTickRule`) are about being *in tune*, not the old name. Keep them.

- Design and rationale: [ARCHITECTURE.md](ARCHITECTURE.md). Read it before making structural changes.
- Plan and progress: [ROADMAP.md](ROADMAP.md). Tick items (☐ → ☑) as you finish them. Finished phases move to [ROADMAP-ARCHIVE.md](ROADMAP-ARCHIVE.md); the owner's hand-run live tests are listed under *Owner checks*.

## Tech stack
Kotlin 2.4 · Jetpack Compose + Material 3 · Coroutines/Flow · AndroidX ViewModel · `AudioRecord` · manual DI · DataStore · Gradle 9 Kotlin DSL with version catalog · JUnit 4.
minSdk 26, targetSdk/compileSdk 37, JVM toolchain 17.

**AGP 9 uses built-in Kotlin:** Android modules must NOT apply `org.jetbrains.kotlin.android`. Only `:core:dsp` applies `kotlin-jvm`, and Compose modules apply `kotlin-compose`. The `kotlin-jvm` entry in the root `build.gradle.kts` pins the Kotlin compiler version.

## Modules
| Module | Type | Purpose |
|---|---|---|
| `:app` | Android app | `MainActivity` (switches tuner ↔ settings, draws the style's background), `TwangleApplication`, `AppContainer` (DI) |
| `:feature:tuner` | Android library | Tuner screen UI, `TunerViewModel`, `TunerUiState` |
| `:feature:settings` | Android library | Settings screen UI, `SettingsViewModel` |
| `:core:audio` | Android library | `AudioRecorder` → `Flow<FloatArray>` microphone frames |
| `:core:settings` | Android library | `SettingsRepository` (DataStore): A4, tuning, notation, style, theme, keep screen on |
| `:core:designsystem` | Android library | `TwangleTheme` and the seven visual styles: palettes, bundled fonts, backgrounds, hand-drawn dials, `StylePicker` |
| `:core:dsp` | **Pure Kotlin/JVM** | Pitch detection (YIN), note math, tunings, smoothing |
| `:baselineprofile` | Android test | Generates the Baseline Profile and measures startup (on demand, device required) |

## Commands
Run from the repo root. On Windows use `gradlew.bat`, or `./gradlew` in Git Bash.
```bash
./gradlew assembleDebug                 # build debug APK
./gradlew installDebug                  # install on connected device/emulator
./gradlew test                          # all JVM unit tests
./gradlew :core:dsp:test                # DSP tests only (fast, no Android)
./gradlew :core:dsp:benchmark           # DSP timing benchmarks (on demand, not in CI)
./gradlew lint                          # Android lint
./gradlew connectedDebugAndroidTest     # instrumented tests (device required)
./gradlew :app:generateBaselineProfile  # regenerate the Baseline Profile (device, ~5 min); commit app/src/release/generated/
```
### Dev machine environment (Windows)
- No system-wide Java. Use Android Studio's bundled JDK for every Gradle command:
  `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` (Git Bash) or
  `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"` (PowerShell).
  Gradle runs on JBR (JDK 25). The foojay plugin auto-provisions JDK 17 for compilation.
- Android SDK: `%LOCALAPPDATA%\Android\Sdk`. `adb` is at `<sdk>/platform-tools/adb.exe` and is not on PATH.
- `local.properties` (git-ignored) holds `sdk.dir=C\:/Users/<user>/AppData/Local/Android/Sdk`. The colon must be escaped or lint fails.
- Emulator AVD `Medium_Phone_API_37.0` exists but needs the **Windows Hypervisor Platform** feature enabled.
- **Physical test device: Samsung Galaxy S26.** Samsung's *Auto Blocker* (Settings → Security and privacy → Auto Blocker) must be OFF, or USB debugging stays greyed out and adb can't connect. If `adb devices` shows nothing or `unauthorized`, check Auto Blocker first, then the USB-debugging prompt on the phone.

### CI
`.github/workflows/android.yml` runs `test`, `lint` and `assembleDebug` on every PR and push to `main`, and uploads the debug APK as the `twangle-debug-apk` artifact.

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
- Strings shown to users go in `res/values/strings.xml` with a Dutch translation in `res/values-nl/strings.xml`, never hard-coded in Composables (`LocalizationTest` checks both).
- Keep functions small and pure in `:core:dsp`. Document any formula with a short comment that cites its source (e.g. de Cheveigné & Kawahara 2002 for YIN).

## Workflow
- **Use the `/roadmap-step` skill** (`.claude/skills/roadmap-step/SKILL.md`) to do roadmap work. Without an argument it runs one increment: announce in plain language → branch → build → verify → land. `/roadmap-step <N>` finishes every remaining item of phase N in one session, still one branch and PR per increment; each increment runs in its own sub-agent so the session stays small.
- Work through ROADMAP phases in order. Phase N+1 work should not start while phase N's builds or tests are broken.
- Before calling a task done, run `./gradlew test lint assembleDebug`. Changes the user can see or hear also get an on-device check on the S26.
- When a design decision changes, update ARCHITECTURE.md in the same change.
- The project owner doesn't write code. Talk to them in plain language about outcomes and the actions they need to take.

## Git & GitHub
Remote: `https://github.com/Klaboukes/Twangle` (private, free plan). Default branch: `main`.

**Every change goes through its own branch and pull request. Never commit directly to `main`.**
1. `git switch main && git pull`
2. Branch: `phase-<N>/<short-kebab-topic>` for roadmap work (e.g. `phase-1/yin-detector`), `fix/<topic>` for repairs, `chore/<topic>` for tooling and docs.
3. Commit in small, focused commits (imperative subject line, ≤ 72 chars).
4. Tick the finished items in ROADMAP.md (☐ → ☑) in the same branch.
5. Verify locally (see Workflow). **The local run is the merge gate.** The free plan for private repos has no branch protection or auto-merge, so nothing on GitHub blocks a bad merge.
6. `git push -u origin <branch>`, then `gh pr create --base main` (title = increment name, body = what changed + how it was verified).
7. `gh pr merge --squash --delete-branch`, then `git switch main && git pull`. Merging right away is the owner's chosen policy. CI still runs on `main`, and the next iteration checks the last `main` run first and fixes any failure before anything else.

`gh` is installed at `C:\Program Files\GitHub CLI\gh.exe`. Terminals opened before the install may not have it on PATH.
