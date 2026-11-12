# CLAUDE.md

Guidance for Claude Code (and humans) working in this repository.

## Project
**Twangle** is an Android guitar tuner (chromatic + guitar-string modes). It captures microphone audio, finds pitch with the YIN algorithm, and shows the deviation in cents on a Compose needle gauge.

The app was called InTune until October 2026. The app ID is `io.github.klaboukes.twangle`, and code packages live under it. Classes named `InTune*` (`InTuneDetector`, `InTuneColor`, `InTuneHaptic`, `InTuneTickRule`) are about being *in tune*, not the old name. Keep them.

- Design and rationale: [ARCHITECTURE.md](ARCHITECTURE.md), a short overview whose Contents lists the topic files in `docs/architecture/` (signal pipeline, UI, settings, performance, testing and more), each small enough to read whole. Before a structural change, read that Contents and the topic files the change touches. Superseded designs go to `docs/architecture/history.md`, not into the live text.
- Plan and progress: [ROADMAP.md](ROADMAP.md). Tick items (☐ → ☑) as you finish them. Finished phases move to [ROADMAP-ARCHIVE.md](ROADMAP-ARCHIVE.md); the owner's hand-run live tests are listed under *Owner checks*. Ideas not planned yet go in [IDEAS.md](IDEAS.md), and become a ROADMAP phase once the owner picks one.

## Tech stack
Kotlin 2.4 · Jetpack Compose + Material 3 · Coroutines/Flow · AndroidX ViewModel · `AudioRecord` · manual DI · DataStore · Gradle 9 Kotlin DSL with version catalog · JUnit 4.
minSdk 26 (the watch app 30, `:baselineprofile` 28), targetSdk/compileSdk 37, JVM toolchain 17, all set once in the root `build.gradle.kts` (a module sets only a minSdk that differs).

**AGP 9 uses built-in Kotlin:** Android modules must NOT apply `org.jetbrains.kotlin.android`. Only `:core:dsp` applies `kotlin-jvm`, and Compose modules apply `kotlin-compose`. The `kotlin-jvm` entry in the root `build.gradle.kts` pins the Kotlin compiler version.

## Modules
| Module | Type | Purpose |
|---|---|---|
| `:app` | Android app | `MainActivity` (switches Tune ↔ Metronome ↔ Practice through the navigation bar or rail, puts the Practice cards under the Practice tab and opens each Practice tool (Ear training too) full screen from its card, back to the cards, opens Settings from the gear in every tab's top start corner (on its side at the rail's top) and About from Settings, both going back to where they were opened from, opens the tuning editor, draws the app's background), `navigation/` (the bar, the rail, the gear), `TwangleApplication`, `AppContainer` (DI, incl. the build's `ToolSwitches`), the home-screen widget (`widget/`) |
| `:wear` | Android app (Wear OS) | The standalone watch tuner and, a swipe left, its metronome (Phase 80, behind its switch in `WatchToolSwitches`), same app ID as `:app`: `WatchActivity`, `WatchTunerViewModel`, `WatchPitchPipeline`, `WatchMetronomeViewModel` with `WatchBeatTaps` (reuses `:core:dsp` and `:core:audio`), Compose for Wear OS |
| `:feature:tuner` | Android library | Tuner screen UI, `TunerViewModel`, `TunerUiState` |
| `:feature:settings` | Android library | Settings and About screen UI (incl. Meter, the lens's look card grid), `SettingsViewModel` |
| `:feature:metronome` | Android library | Metronome screen UI (the tempo with − and +, the ruler to drag, beat lights, meter and sound choices, Tap the beat and Start, the speed trainer's row, the options panel with Listen), `MetronomeViewModel` |
| `:feature:practice` | Android library | Practice tab (Phase 32, behind its switch): the cards with the "last time" row (`PracticeCards`, Phase 69), Chords and Scales (`ChordsScreen`, `ScaleNeck`), the drone button (`DroneButton`) and the Capo helper (`CapoScreen`), each tool's top row with its back arrow (`BackAndTuningRow`); `:app` opens each tool full screen from its card |
| `:feature:eartraining` | Android library | Ear training (Phase 55, a Practice tool behind its switch): `EarTrainingScreen`, `EarTrainingViewModel` (the visit, the question's sound through `TonePlayer`, the microphone through `PitchListener`, which `:app` backs with the tuner's `PitchPipeline`), the "What is asked" panel |
| `:core:audio` | Android library | `AudioRecorder` → `Flow<FloatArray>` microphone frames; `TonePlayer` (reference tones), `MetronomePlayer` (clicks) and `DronePlayer` (the drone under Scales) through `AudioTrack` |
| `:core:settings` | Android library | `SettingsRepository` (DataStore): A4, tuning, notation, theme and wallpaper colours, keep screen on, metronome tempo/meter/click/vibration, the Practice tool used last with its chord, scale and drone; also `ToolSwitches` (which tools show, not saved) |
| `:core:designsystem` | Android library | `TwangleTheme` and the one look, Ink (`StyleTokens` light and dark, its face Nunito (`TwangleFont`) with a bundled face for ♯ and ♭, the background), the lens's looks (`lens/`: `LensLookArt`, the trail since Phase 71), the metronome's beat lights, the icon beside the name (`TwangleName`), the segmented switch (`SegmentedSwitch`), the tuner's picker chip (`PickerChip`), the Practice tools' floating panel (`FloatingMenu`) |
| `:core:dsp` | **Pure Kotlin/JVM** | Pitch detection (YIN), note math, tunings, smoothing, strum check and chord naming, chord, scale and capo charts, Ear training's questions, string synthesis (the reference tones), the drone's synthesis, metronome click track and tap tempo |
| `:core:testing` | Android library | UI test helpers only: `createSharedActivityComposeRule()` and `SharedActivityTestRunner` (one activity for the whole run, much faster), `@Thorough` (double checks, only in `--thorough` runs, i.e. the full check), `@EveryLanguage` and `assertEveryTextWhollyShown` (every language, the full check), `assertWhollyShown` (nothing clipped or cut off), `PhoneScreen` (a forced window size with fake system bars, the same on every phone); in its own on-device tests `PluckRecorder`, which records plucked strings for `:core:dsp`'s test fixtures (`scripts/record-plucks.sh`, never part of the app) |
| `:baselineprofile` | Android test | Generates the Baseline Profile and measures startup (on demand, device required) |

## Commands
Run from the repo root. On Windows use `gradlew.bat`, or `./gradlew` in Git Bash.
```bash
./gradlew assembleDebug                 # build debug APK
scripts/install-debug.sh [--start]      # build and install the phone app on a test phone under the phone's lock (--start: and start it); not ./gradlew :app:installDebug, whose install stops another session's UI test run
./gradlew test                          # all JVM unit tests
./gradlew :core:dsp:test                # DSP tests only (fast, no Android)
./gradlew :core:dsp:benchmark           # DSP timing benchmarks (on demand, not in CI)
./gradlew lint                          # Android lint
./gradlew :app:assembleRelease -Ptwangle.foss=true  # the free build for F-Droid: no Google Play library (lifecycle.md, *Google's rating window*)
scripts/ui-tests.sh                     # on-device UI tests, only the modules this branch touches (see /ui-test)
scripts/ui-tests.sh --dry-run           # which modules that would run, and why (no build, no phone)
scripts/ui-tests.sh --since-pass        # before a release: only the modules changed since they last passed whole on this PC
scripts/full-check.sh                   # the full check, whenever the owner starts it: the watch app's tests on the watch when it is connected, a smoke test of the release build, every UI test (Ink light and dark), the screens' tests again in the high-contrast look (light and dark), then every language where a screen changed since it passed, shared between every phone and tablet connected (~45 min on one phone when every screen changed, about two thirds of that with the tablet too (36 min on 2026-11-09), a little over half with two phones, 1.1's six styles took ~3 h; exit 0 passed, 1 failed, 5 not finished (a device busy), 3 the watch not tested; its record build/full-check/result.txt is what release.sh full-check and bundle read; --fixed '#<PR>' notes a run whose failures that PR fixed and re-checked; --dry-run says what it would run; see /full-check)
scripts/store-screenshots.sh [--shots 6] [locale…]  # the Play Store screenshots on the phone (--shots: only those pictures, the rest kept; a phone and the tablet both connected: both sets at once; which pictures a change can alter: scripts/store-picture-scopes.txt), or the 10-inch tablet set with the tablet connected (~80 s a language, ~50 min for all 36; before a listing is sent, release.sh listing refuses older ones; git-ignored, shared between the PCs through GitHub, docs/store-listing.md)
scripts/watch-screenshots.sh [locale…]  # the Play Store's Wear OS screenshots on the watch (or the Wear OS emulator): three a language, 960 x 960, a few minutes for all; held back in fastlane/held-back/ until the watch's turn on Play (git-ignored, shared like the phone's; docs/store-listing.md, *Watch screenshots*)
scripts/release-smoke.sh                # the release-type build on the phone: start, leave with Home, start, turn, leave; fails on a crash the debug build can't show (~4 min, the full check's first phone stage)
scripts/meter-frames.sh                 # frame times of the meter and the pitch trail on the phone (synthetic tone, ~4 min)
scripts/dsp-phone-benchmark.sh          # pitch detector CPU per frame on the phone (no app, no sound, ~20 s)
scripts/metronome-drift.sh              # metronome steadiness on the phone: 120 BPM for 10 min, quietly (~11 min)
scripts/watch-metronome-timing.sh [minutes] [silent|audible]  # the watch metronome's beats and wrist taps against a perfect clock, on the Galaxy Watch 5 under its lock (10 min silent by default, ~12 with the build; strum-and-metronome.md, *On the watch*)
scripts/baseline-profile.sh [--dry-run] # regenerate the Baseline Profile on the phone under its lock (~15 min; the class filter, the uninstall, the upright screen and the debug build put back done for you; performance.md, *Baseline Profile*), not ./gradlew :app:generateBaselineProfile, which swaps the app with no lock; commit app/src/release/generated/
scripts/release.sh bump|notes|bundle|upload|promote|rollout|halt|listing  # a Play release: version, notes, signed bundle and tag, then (each after the owner's yes) internal track, promotion, staged rollout, store listing; `watch` after upload/promote/rollout/halt for the watch app's own Wear OS tracks (see /release)
scripts/release.sh status|health [days]|reviews [language]|installs|listing-changes|profile|full-check  # read only, no yes needed: what is on each Play track, crash/ANR rates per version vs Play's lines, the last week's reviews translated, installs from Play's monthly reports (a day or two behind), what changed for the store listing since it was last sent, whether the Baseline Profile is older than the screens, whether the last full check passed whole on this code (bundle refuses otherwise)
python -I scripts/translations.py status  # which texts each night language lacks or has stale (/translate)
python -I website/build.py              # build and check the website into website/build/ (nothing from another site, no broken link)
scripts/publish-website.sh [--dry-run]  # publish website/ as origin/main has it (never the folder's own) to https://klaboukes.github.io/ after it landed on main (pushes only a change; --dry-run: what would change, no push)
scripts/website-pictures.sh             # the website's small screenshots and icon, from the last store-screenshot run (commit them)
scripts/publish-privacy.sh [--dry-run]  # publish PRIVACY.md and PRIVACY.nl.md as origin/main has them to the public policy page after a policy change landed on main
scripts/store-graphics.sh [locale…]     # the Play Store's icon and every language's feature graphic, drawn on the phone (committed; docs/store-listing.md)
scripts/style-screens.sh                # pictures of the tuner in Ink, light and dark, for the owner (~1 min)
scripts/strum-check.sh [bass]           # plays synthetic strums from the PC's speaker to the phone (strum-and-metronome.md, *Phone check*)
scripts/record-plucks.sh                # records plucked strings on the phone into core/dsp's test fixtures, the phone guiding the player (~15 min; --test: one 2 s check, no playing; testing.md, *Real recordings*)
python -I scripts/launcher-icon.py      # draws the launcher icon, themed icon and watch mark (phone and watch) from the harmonics; edit it, not the drawables (ARCHITECTURE.md, *Name, app ID and icon*)
python -I scripts/wordmark.py           # traces the name "Twangle" (Comfortaa Bold, spaced) into the vector beside the icon and the website's top-bar SVG; needs fontTools (python -I -m pip install fonttools)
```
`scripts/repo-move.sh` (once in a clone made before the 2026-11-12 move, *Git & GitHub*), `scripts/ui-test-scope.py`, `scripts/devices.sh`, `scripts/main-export.sh`, `scripts/share-store-screenshots.sh`, `scripts/strum-wav.py`, `scripts/subset-font.py` and `scripts/PlayReports.java` are helpers of the scripts above and of the docs that name them.
### Dev machine environment (Windows)
- Java is Android Studio's bundled JDK (JBR, JDK 25), with no separate install (owner, 2026-10-28). Each PC has it as a Windows user variable,
  `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`, so commands don't set it themselves.
  If `JAVA_HOME` is empty (a new PC, or a session started before the variable was set), set it for that command
  (`export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` in Git Bash) and ask the owner to set it once in PowerShell,
  `[Environment]::SetEnvironmentVariable('JAVA_HOME', 'C:\Program Files\Android\Android Studio\jbr', 'User')`, then restart the Claude app.
  The foojay plugin auto-provisions JDK 17 for compilation.
- Android SDK: `%LOCALAPPDATA%\Android\Sdk`. `adb` is at `<sdk>/platform-tools/adb.exe` and is not on PATH.
- `local.properties` (git-ignored) holds `sdk.dir=C\:/Users/<user>/AppData/Local/Android/Sdk`. The colon must be escaped or lint fails.
- **Devices:** the Xiaomi 12X (main test phone, `f813c98c`), the Galaxy S26 (the owner's daily phone, `R3GL502L08P`, not always there), the Lenovo Tab M11 (tablet checks, `HA244V49`), the Galaxy Watch 5 (over Wi-Fi) and the emulators. **Read [docs/devices.md](docs/devices.md) before any step that uses one**: serials, setup, quirks, the watch's pairing. The owner's rules for every device, in short: uninstalling Twangle is fine, but put back every other setting a check changes; on Play Protect's "Send app for a security check?" tap **Don't send**; with more than one device connected the UI test scripts pick their own (a phone for the phone's tests, the watch for `:wear`); for anything else pick one with `export ANDROID_SERIAL=<serial>`.

### CI
`.github/workflows/android.yml` runs `test`, `lint`, `assembleDebug` and an unsigned `assembleRelease bundleRelease` (the watch app included) in one Gradle call, then the free F-Droid build's release (`:app:assembleRelease -Ptwangle.foss=true`, a call of its own since the property holds for a whole call), **once a night** at 03:00 UTC on `main`, plus by hand through *Run workflow*, never per push or PR: the local run is the gate. A run per merge repeated that check and used up the free plan's 2,000 minutes a month in one week (2026-10-16). The nightly run covers everything merged since the last successful run and skips the build when only `*.md`, `docs/`, `.claude/`, `scripts/` or `website/` changed (a moved file counts at both paths), except `scripts/ui-tests.sh`, which a unit test reads (a test that reads a file names it as an input: [testing.md](docs/architecture/testing.md)). The free build's step runs even when the main one failed. A run GitHub refused to start (spending limit, storage quota) is not a code failure. Dependabot proposes updates of the pinned actions once a month as one PR (`.github/dependabot.yml`), checked by the local run like any other. It uploads no APK (nobody downloaded it, and the free plan's artifact storage is small), only build reports when it fails (kept 3 days).

## Hard rules
1. **`:core:dsp` must never depend on Android.** No `android.*` imports and no Android library dependencies. It is a `kotlin("jvm")` module so it stays fast to test and portable.
2. **No allocations in the audio/DSP hot loop.** Preallocate buffers (ring buffer, YIN scratch arrays) and reuse them. The loop runs ~47×/s. The one exception is the small immutable result each frame hands to the UI (`PitchReading`, then `TunerUiState`); nothing sample-sized or per-sample is ever allocated.
3. **Never block the main thread.** `AudioRecord.read()` and each sound's blocking `AudioTrack.write()` run on a thread of their own at `THREAD_PRIORITY_AUDIO` (`audioThreadExecutor` in `:core:audio`; never a shared `Dispatchers.IO` thread, which would keep the priority), and set-up that calls the audio server runs there too. DSP runs on `Dispatchers.Default`.
4. **Hold the mic only while a screen that listens is visible** (the tuner, the metronome's Listen). The tuner uses `SharingStarted.WhileSubscribed(5_000)` plus `collectAsStateWithLifecycle()`; Listen stops when its screen is left. No foreground service.
5. **Never use `AudioSource.MIC` or `VOICE_COMMUNICATION`.** Use `UNPROCESSED`, falling back to `VOICE_RECOGNITION`. AGC and noise suppression corrupt pitch.
6. **Any DSP change needs tests.** Add or adjust synthetic-signal tests in `core/dsp/src/test`. Accuracy target: ±1 cent on synthetic signals.
7. **Privacy:** audio is never written to disk or sent anywhere. Do not add the `INTERNET` permission.
8. Dependency versions live only in `gradle/libs.versions.toml`. Never hard-code versions in module build files. The one exception is the foojay plugin in `settings.gradle.kts`, whose `plugins` block can't read the catalog.
9. **Free forever:** no ads, no account creation, no in-app purchases, no analytics or tracking SDKs. This promise is stated in README.md.

## Conventions
- Kotlin official code style (`kotlin.code.style=official`), 4-space indent, see `.editorconfig`.
- Packages: `io.github.klaboukes.twangle.<module path>`, e.g. `io.github.klaboukes.twangle.core.dsp.pitch`. Source sets use `src/main/kotlin`.
- UI state: the tuner's reading is one immutable `TunerUiState` sealed interface exposed as `StateFlow` from `TunerViewModel`; its options (notation, tuning, tools and so on) are their own `StateFlow`s next to it. Each screen's top composable makes its ViewModel; every composable below it is stateless and takes state plus lambdas.
- Implementations go behind interfaces (`PitchDetector`, `AudioRecorder`) so they can be swapped (fake recorder, FFT-based YIN, NDK).
- Frequencies are `Float` in Hz, deviations are `Float` in cents, and sample buffers are `FloatArray` in −1..1.
- Strings shown to users go in `res/values/strings.xml`, never hard-coded in Composables, each new one with a translator note right above it (an XML comment: what it means and where it shows, e.g. `<!-- Under the meter when the string is in tune. -->`). **By day write English and Dutch only** (`values-nl`; `twangle.dayLanguages`); every other language in `twangle.languages` (`gradle.properties`; 36 today) is translated by `/translate` (the full check's first step), and a release needs them all complete and up to date (`-Ptwangle.strictTranslations=true`). `LocalizationTest` checks it all; a text removed from English goes from every language. Day and night, the record in `translations/`, adding a language: [docs/architecture/languages.md](docs/architecture/languages.md).
- **Every tool has a switch and keeps it** (`ToolSwitches`, all on; owner, 2026-10-28). A **tool** is something the player picks on its own: a tuner tool (strum check, 12th-fret check, chord name, pitch trail), a Practice tool (Chords, Scales with its drone, Capo; the metronome is what the tab falls back to and has none) or the metronome's speed trainer. If a tool goes wrong on players' phones, a release with its switch off hides it while the fix is made; Twangle has no internet, so a switch changes only with a new version, and the quick brake on a bad version is halting its staged rollout (`scripts/release.sh halt`). A new tool stays off in release builds until the owner's check passes ([docs/architecture/performance.md](docs/architecture/performance.md) *Release build* says how) and keeps its switch after. A new option inside a tool (a sound, a tuning, an instrument, left-handed charts) goes with its tool's switch and needs none of its own. The owner decides when a version goes to Play (ROADMAP.md, *Where things stand*); fixes land on `main` as usual.
- **Store pictures a change can't alter:** a pull request that touches the app's screens or the store screenshot test but surely changes only some store pictures (or none) gets a line in `scripts/store-picture-scopes.txt` (`#<PR> 6`, `#<PR> tablet`, `#<PR> none`, with why), so `scripts/release.sh listing` asks to remake only those; when unsure, leave it out ([docs/store-listing.md](docs/store-listing.md), *Phone screenshots*).
- **The privacy policy is public:** after a change to `PRIVACY.md` or `PRIVACY.nl.md` lands on `main`, run `scripts/publish-privacy.sh`.
- **The website follows the app:** a change to a tool (what it does, its name, its numbers) updates that tool's page in `website/pages/` (English and Dutch) in the same pull request, then `scripts/publish-website.sh` after the merge ([docs/architecture/website.md](docs/architecture/website.md)).
- Keep functions small and pure in `:core:dsp`. Document any formula with a short comment that cites its source (e.g. de Cheveigné & Kawahara 2002 for YIN).

## Workflow
- **Use the `/roadmap-step` skill** (`.claude/skills/roadmap-step/SKILL.md`) to do roadmap work. Without an argument it runs one increment: announce in plain language → branch → build → verify → land. `/roadmap-step <N>` finishes every remaining item of phase N in one session, still one branch and PR per increment; each increment runs in its own sub-agent so the session stays small.
- Work through ROADMAP phases in the order ROADMAP.md lists them, not by number; the owner may move a phase ahead. Don't start the next phase while `main`'s build or tests are broken.
- Before calling a task done, run `./gradlew test lint assembleDebug`. Changes the user can see or hear also get an on-device check on a test phone (the Xiaomi 12X, or the S26 when it is plugged in), and on the Lenovo tablet when the change is about a tablet layout.
- **Use the `/ui-test` skill** (`.claude/skills/ui-test/SKILL.md`) for on-device UI tests: only the tests you're working on while iterating, the affected modules in full once before landing. Never call `connectedDebugAndroidTest` directly.
- **Use the `/full-check` skill** (`.claude/skills/full-check/SKILL.md`) when the owner starts the full check (over lunch, at bedtime, any time; the overnight run until 2026-11-06): every UI test, light and dark and in high contrast, and every language, fixed and landed without asking them anything (questions go in the summary at the end).
- **Use the `/release` skill** (`.claude/skills/release/SKILL.md`) to put what is on `main` on Google Play: version, release notes in every language, signed bundle, tag; the owner's Play Console steps are in [docs/release.md](docs/release.md). A release waits for a clean full check over the code it releases: nothing (not even the version number) changes before that.
- **Use the `/translate` skill** (`.claude/skills/translate/SKILL.md`) to bring the night languages up to date: whatever daily work left in English and Dutch only, translated in parallel batches, recorded, strict-checked and landed through its own PR. The full check starts with it; it can also run by hand any time (no phone).
- **Use the `/add-language` skill** (`.claude/skills/add-language/SKILL.md`) to add a language: translations, language list, note-name default, fonts, right-to-left, phone check.
- **Use the `/away` skill** (`.claude/skills/away/SKILL.md`) when the owner gives a task and goes to bed or away (owner, 2026-11-08): no questions, the recommended choice each time, kept easy to undo and logged, and a report with every choice to check when they're back.
- When a design decision changes, update the matching architecture file (ARCHITECTURE.md or its topic file) in the same change.
- The project owner is a professional software engineer (20+ years: .NET, web, scripting, SQL) but new to Android and Kotlin, and leaves the code here to Claude. Talk to them in plain language about outcomes and the actions they need to take; go technical only where a detail matters for them to know (a tradeoff to weigh, a risk, why something failed). Brief sub-agents the same way, never as "non-coder" (owner, 2026-10-28).
- Open every mock-up and preview page (`docs/design/*.html`; 1.1's replaced ones in `docs/design/1.x/`) for the owner in Firefox (`C:\Program Files\Mozilla Firefox\firefox.exe <full path>`), never in the app's built-in browser, which doesn't play their sounds (owner, 2026-10-26).

## Git & GitHub
Remote: `https://github.com/Klaboukes/Twangle` (private, free plan). Default branch: `main`.
The repository moved on 2026-11-12 (Phase 93): its history was rewritten into a new `Klaboukes/Twangle` (the owner's Gmail address and name replaced by `Klaboukes` and the no-reply address, so every commit has a new SHA), and the old one is the private `Klaboukes/Twangle-archive`, which keeps pull requests #1–#833: a "(#NNN)" in an older commit's subject is an archive number, and new pull requests start again at #1. A clone made before the move is re-pointed once with `scripts/repo-move.sh` (its head says how); never push from one that wasn't.

**Every change goes through its own branch and pull request. Never commit directly to `main`.**
1. `git switch main && git pull` (in a worktree: see *One worktree per session* below)
2. Branch: `phase-<N>/<short-kebab-topic>` for roadmap work (e.g. `phase-1/yin-detector`), `fix/<topic>` for repairs, `chore/<topic>` for tooling and docs, `release/v<version>` for a release's version and notes.
3. Commit in small, focused commits (imperative subject line, ≤ 72 chars).
4. Tick the finished items in ROADMAP.md (☐ → ☑) in the same branch.
5. Verify locally (see Workflow). **The local run is the merge gate.** The free plan for private repos has no branch protection or auto-merge, so nothing on GitHub blocks a bad merge.
6. `git push -u origin <branch>`, then `gh pr create --base main` (title = increment name, body = what changed + how it was verified).
7. `gh pr merge --squash --delete-branch`, then `git switch main && git pull`. Merging right away is the owner's chosen policy. CI checks `main` overnight, and the next iteration checks the last run first and fixes any failure before anything else.

### One worktree per session that changes files
Owner, 2026-10-28: two sessions in one folder switch branches under each other (that day a commit landed on the wrong branch).
- **The main folder** (`D:\source\Twangle` on the desktop) belongs to the full check and releases (`/full-check`, `/release`), one at a time: their records are git-ignored and live there (`build/full-check/`, the store screenshots under `fastlane/`). A session that only reads may stay there too.
- **Every other session that will edit, build or commit** works in a git worktree of its own: start it with the desktop app's worktree option, or make one first thing (`EnterWorktree`, under `.claude/worktrees/`, git-ignored).
- **In a new worktree:** copy `local.properties` from the main folder (git-ignored; the build needs it) and give the branch its name from the list above (`git branch -m <branch>`). The first build there takes a few minutes longer.
- **`main` stays in the main folder**, so in a worktree read `git switch main && git pull` (here and in the skills) as `git fetch -q origin && git switch --detach origin/main`, then branch from there. Merge with `gh pr merge --squash` alone (`--delete-branch` would try to switch the worktree to `main`) and delete the GitHub branch with `git push origin --delete <branch>`.
- **When the work is merged, finish it without asking** (owner, 2026-10-28: "I just want sessions not to clash and update main locally and on GitHub once done"; git is not the owner's thing, so never ask them about it):
  1. Leave and remove the worktree: `ExitWorktree` with *remove*. It warns that the branch has a commit `main` lacks; after a squash merge that commit is in `main` under another name, so remove it anyway (`discard_changes: true`). The branch stays on the PC until step 3.
  2. In the main folder, if it is on `main` and no run is going: `git pull`. If it is on another branch, a run or another session has it: leave it, since GitHub has the work and the next session there pulls it.
  3. Delete the merged local branch (`git branch -D <branch>`).
  Mention git to the owner only when something went wrong.
- **The phone stays shared:** every script that uses it locks it (`scripts/devices.sh`: the UI tests, the release smoke test, the meter and strum checks, `scripts/install-debug.sh`; the watch's own lock for `scripts/watch-metronome-timing.sh`), and exit code 5 means another run has it: wait, never force it. Its notes of passed modules (`build/ui-tests-passed/`) are kept in the main folder for every worktree.

`gh` is installed at `C:\Program Files\GitHub CLI\gh.exe`. Terminals opened before the install may not have it on PATH.
