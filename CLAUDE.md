# CLAUDE.md

Guidance for Claude Code (and humans) working in this repository.

## Project
**InTune** is an Android guitar tuner (chromatic + guitar-string modes). It captures microphone audio, finds pitch with the YIN algorithm, and shows the deviation in cents on a Compose needle gauge.

- Design and rationale: [ARCHITECTURE.md](ARCHITECTURE.md). Read it before making structural changes.
- Plan and progress: [ROADMAP.md](ROADMAP.md). Tick items (☐ → ☑) as you finish them.

## Tech stack
Kotlin 2.x · Jetpack Compose + Material 3 · Coroutines/Flow · AndroidX ViewModel · `AudioRecord` · manual DI · DataStore · Gradle Kotlin DSL with version catalog · JUnit 4.
minSdk 26, targetSdk/compileSdk 35, JVM toolchain 17.

## Modules
| Module | Type | Purpose |
|---|---|---|
| `:app` | Android app | `MainActivity`, `InTuneApplication`, `AppContainer` (DI), theme |
| `:feature:tuner` | Android library | Tuner screen UI, `TunerViewModel`, `TunerUiState` |
| `:core:audio` | Android library | `AudioRecorder` → `Flow<FloatArray>` microphone frames |
| `:core:dsp` | **Pure Kotlin/JVM** | Pitch detection (YIN), note math, tunings, smoothing |

## Commands
Run from the repo root. On Windows use `gradlew.bat`, or `./gradlew` in Git Bash.
```bash
./gradlew assembleDebug                 # build debug APK
./gradlew installDebug                  # install on connected device/emulator
./gradlew test                          # all JVM unit tests
./gradlew :core:dsp:test                # DSP tests only (fast, no Android)
./gradlew lint                          # Android lint
./gradlew connectedDebugAndroidTest     # instrumented tests (device required)
```
> **Environment note:** as of 2026-10-10 the dev machine has no JDK, Android SDK or Gradle wrapper. Install Android Studio and open the project once. That generates `gradlew` and `gradle/wrapper/gradle-wrapper.jar` and sets `sdk.dir` in `local.properties` (which is git-ignored).

## Hard rules
1. **`:core:dsp` must never depend on Android.** No `android.*` imports and no Android library dependencies. It is a `kotlin("jvm")` module so it stays fast to test and portable.
2. **No allocations in the audio/DSP hot loop.** Preallocate buffers (ring buffer, YIN scratch arrays) and reuse them. The loop runs ~47×/s.
3. **Never block the main thread.** `AudioRecord.read()` runs on a dedicated single-thread dispatcher. DSP runs on `Dispatchers.Default`.
4. **Hold the mic only while the tuner is visible.** Use `SharingStarted.WhileSubscribed(5_000)` plus `collectAsStateWithLifecycle()`. No foreground service.
5. **Never use `AudioSource.MIC` or `VOICE_COMMUNICATION`.** Use `UNPROCESSED`, falling back to `VOICE_RECOGNITION`. AGC and noise suppression corrupt pitch.
6. **Any DSP change needs tests.** Add or adjust synthetic-signal tests in `core/dsp/src/test`. Accuracy target: ±1 cent on synthetic signals.
7. **Privacy:** audio is never written to disk or sent anywhere. Do not add the `INTERNET` permission.
8. Dependency versions live only in `gradle/libs.versions.toml`. Never hard-code versions in module build files.

## Conventions
- Kotlin official code style (`kotlin.code.style=official`), 4-space indent, see `.editorconfig`.
- Packages: `com.intune.<module path>`, e.g. `com.intune.core.dsp.pitch`. Source sets use `src/main/kotlin`.
- UI state: one immutable `TunerUiState` sealed interface exposed as `StateFlow` from the ViewModel. Composables are stateless and take state plus lambdas.
- Implementations go behind interfaces (`PitchDetector`, `AudioRecorder`) so they can be swapped (fake recorder, FFT-based YIN, NDK).
- Frequencies are `Float` in Hz, deviations are `Float` in cents, and sample buffers are `FloatArray` in −1..1.
- Strings shown to users go in `res/values/strings.xml`, never hard-coded in Composables.
- Keep functions small and pure in `:core:dsp`. Document any formula with a short comment that cites its source (e.g. de Cheveigné & Kawahara 2002 for YIN).

## Workflow
- Work through ROADMAP phases in order. Phase N+1 work should not start while phase N's builds or tests are broken.
- Before calling a task done, run `./gradlew test` (and `assembleDebug` for UI/app changes).
- When a design decision changes, update ARCHITECTURE.md in the same change.

## Git & GitHub
Remote: `https://github.com/Klaboukes/InTune` (private). Default branch: `main`.

**Every roadmap increment goes through its own branch and pull request. Never commit directly to `main`.**
1. `git switch main && git pull`
2. `git switch -c phase-<N>/<short-kebab-topic>`, e.g. `phase-0/app-shell` or `phase-1/yin-detector`
3. Commit in small, focused commits (imperative subject line, ≤ 72 chars).
4. Tick the finished items in ROADMAP.md (☐ → ☑) in the same branch.
5. Build and test locally (see Workflow above). Don't open a PR on a red build.
6. `git push -u origin <branch>`, then `gh pr create --base main --fill` (title = increment name, body = what changed + how it was verified).
7. Once checks pass, `gh pr merge --squash --delete-branch`, then `git switch main && git pull`.

`gh` is installed at `C:\Program Files\GitHub CLI\gh.exe`. Terminals opened before the install may not have it on PATH.
