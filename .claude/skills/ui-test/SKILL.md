---
name: ui-test
description: Run Twangle's on-device UI tests on the test phone (Xiaomi 12X), scoped to what changed, with tight timeouts and a short summary. Use whenever UI or instrumented tests need to run, while iterating on a UI change, before landing a change the user can see or hear, or when the user says "run the UI tests", "/ui-test", or "/ui-test <filter>".
argument-hint: "[optional: test-name regex, a :module, or --all]"
---

# UI tests on the phone

The whole on-device suite takes about 2 minutes and the tuner module about 1. A single test takes about 15 s and an area about 30 s. That is the normal, quick run: until the look of the app is final it leaves out the double checks (see *Quick and thorough runs*). Run the smallest set that proves the change while iterating, and the affected modules in full **once** before landing.

Always go through `scripts/ui-tests.sh`. It works out which modules a change touches, builds all their test APKs in one Gradle call, installs each one and runs it with `adb shell am instrument`, with a time limit per module. It prints one line per module plus the failing tests. Don't call `./gradlew connectedDebugAndroidTest`: it adds 15–25 s of Gradle test harness per module, uninstalls the app afterwards (wiping the owner's settings), runs every module, and prints hundreds of log lines into the session.

**When called as a command:** `/ui-test` runs `scripts/ui-tests.sh` (affected modules); `/ui-test :feature:settings` or `/ui-test --all` passes the argument on; any other text is a name filter, `scripts/ui-tests.sh <module> -f '<text>'`, on the module that has those tests (usually `:feature:tuner`). Report the summary lines in plain language.

## Scope ladder

All of these fit in one foreground Bash call. Give it the timeout shown, never more "to be safe".

1. **Iterating on one test or a few** (~15 s, Bash `timeout: 120000`):
   `scripts/ui-tests.sh :feature:tuner -c TunerContentTest#someTest`
2. **Iterating on one area**, e.g. the tip or the hint (~35 s, Bash `timeout: 180000`):
   `scripts/ui-tests.sh :feature:tuner -f 'Tip|Hint'`
3. **Once before landing**: the affected modules in full (tuner + app ~1¼ min, tuner + settings + app ~2 min, Bash `timeout: 300000`):
   `scripts/ui-tests.sh`
4. **Build files or the version catalog changed** (~2 min, Bash `timeout: 300000`):
   `scripts/ui-tests.sh --all`
5. **Only when the owner asks for every check, or for the Phase 23 item "The look is final"** (~4½ min, Bash `timeout: 480000`):
   `scripts/ui-tests.sh --all --thorough`

- `-f` is a regex matched against `Class#method`, so `-f 'Tip'` runs every test with "Tip" in its name, in any class of that module. Name tests so an area is one word away (they already do: `…Tip…`, `…Hint…`, `…Readout…`, `…OnItsSide…`, `…LargestFont…`).
- `-c` takes a class, `Class#method`, or a comma-separated list; a short class name gets the module's package. A test marked `@Thorough` only runs with `--thorough` added (the script says so when nothing ran).
- `-a key=value` passes an instrumentation argument (repeatable), e.g. `-a meterScreenshots=true`.
- With no module, the script picks the affected modules from the branch's diff against `main` plus uncommitted and new files:
  - `feature/tuner` → tuner + app; `feature/settings` → settings + app; `core/audio` → audio + tuner + app
  - `core/dsp`, `core/settings`, `core/designsystem` → tuner + settings + app; `core/testing` → tuner + settings
  - a module's own `src/androidTest` → just that module
  - build files, the version catalog, the Gradle wrapper → everything
  - docs, `.github`, `.claude`, `scripts`, JVM unit tests → nothing (it says so and exits 0)
- After the full run, a fix doesn't need another full run. Re-run the tests that failed plus the tests for the area the fix touched (`-f`). Run the affected modules in full again only if the fix changed shared layout code (`TunerContent`, `GuitarControls`, `ModeSlot`, the theme).

## Measured on the Xiaomi 12X (13 October 2026)

| Module | Quick run (tests, wall time) | `--thorough` | Before the speed-ups |
|---|---|---|---|
| build (one Gradle call) | 8–15 s (≈ 35 s after a big change) | same | per module, inside Gradle's test task |
| `:feature:tuner` | 147 tests, 62 s | 237 tests, 169 s | ≈ 525 s |
| `:feature:settings` | 54 tests, 38 s | 67 tests, 54 s | ≈ 138 s |
| `:core:audio` | 5 tests, 4 s | same | ≈ 17 s |
| `:app` | 1 test, 11 s | same | ≈ 19 s + 20 s reinstall |

In a thorough run, `CompactLandscapeStylesTest` (~70 s) and `MeterLooksTest` (~53 s) take most of the tuner time: every look in all seven styles, pixel by pixel. In the quick run they take ~10 s and ~8 s. `TunerContentTest` takes ~0.2 s a test, the settings tests ~0.6 s (the settings screen has live meter previews). `MeterLookScreenshots` has no cases unless `-a meterScreenshots=true` is passed (`scripts/meter-look-screens.sh` does), so it costs the normal run nothing.

## Quick and thorough runs

The owner restyles the app often and asked (October 2026) not to have every style, theme and size double-checked on every run until the look is final. So a normal run leaves out two kinds of double check, and `--thorough` brings them back:

- **Styles.** The style test classes (`MeterLooksTest`, `CompactLandscapeStylesTest`, `StringLockTipStylesTest`, and the two whole-grid checks in `WaveViewTest`) run in the default style only. Their style list is `allIfThorough(VisualStyle.entries, VisualStyle.DEFAULT)` from `:core:testing`.
- **Variants.** A test marked `@Thorough` (`:core:testing`) is skipped: the same check as a quicker test, in another font size, language, orientation or view. Each family keeps its hardest case in the quick run (in settings, for instance, Dutch at 2× font on a phone on its side), so most layout breaks still show at once.

For new tests: a new check over every style uses `allIfThorough`. Mark a test `@Thorough` when another test already covers its check in a harder or equal case. Never mark the only test of a behaviour. After a deliberate restyle, a failing pixel or layout check in the quick run is usually the check lagging behind the new look: say which check and why, and adjust it rather than the design. The thorough run comes back as the Phase 23 item "The look is final" in ROADMAP.md.

## Why it is fast, and how to keep it fast

- **One activity for the whole run.** By default every Compose test opens its own activity, and after each test the runner closes it and waits for it to go. That took ~0.7 s per test, more than most tests themselves. The UI tests use `createSharedActivityComposeRule()` from `:core:testing` instead of `createComposeRule()`. Every test still gets a fresh composition, test clock and `ViewModelStore` in the same open window. The modules run `SharedActivityTestRunner`, which keeps the runner from closing that window between tests. **A new Compose UI test uses `createSharedActivityComposeRule()`.** Only a test that changes the activity itself (rotation, recreation, window flags) uses `createComposeRule()` or `ActivityScenario.use { }`. A new module with Compose UI tests sets `testInstrumentationRunner = "io.github.klaboukes.twangle.core.testing.SharedActivityTestRunner"` and `androidTestImplementation(project(":core:testing"))`.
- **No Gradle test task.** The script installs the APKs itself and leaves the app installed. Test APKs (`….test` packages) stay on the phone; they are invisible and harmless.
- **Pixel checks:**
  - Capture once and cut the picture up (`MeterLooksTest.captureCases`). A capture costs about a frame whatever it covers.
  - Never build an assertion message per pixel: `if (bad) fail("… at $x, $y")`, not `assertTrue("… at $x, $y", ok)`. The string costs more than the check.
  - For whole-window loops, compare ARGB ints rather than `Color`s.
  - Measure a thing once and assert several facts about it, rather than repeating the same captures in several tests.
- **Profile before guessing.** Time a slow test with `System.nanoTime()` around `waitForIdle`, captures and pixel loops, and log the totals in an `@After`. Read them with `adb logcat -s TestRunner:I <tag>:I`. Remove the timing code afterwards.

## Timeouts

- **Per test: 10 s** (20 s in `MeterLooksTest`). The UI test classes wrap every test in a JUnit `Timeout` rule (`TEST_TIMEOUT_SECONDS`), outermost around the compose rule, so a hang fails quickly instead of leaving a frozen window on the phone. A new UI test class that could hang (anything that scrolls, waits, or animates) gets the same rule. If a test legitimately needs longer, raise the constant for a reason you can name, not "to be safe".
- **Per module: 600 s.** The script stops a module's run after `MODULE_TIMEOUT` seconds (env var, default 600), force-stops the test package on the phone and reports TIMED OUT. The build has its own limit, `BUILD_TIMEOUT` (600 s).
- **Bash tool:** use the timeouts in the scope ladder above. If a run times out, look at the log before running it again.

## Reading results

- Output is a `build` line, then one line per module, e.g. `:feature:tuner 48s  21 tests, 0 failed, 32s on the phone`, followed by `FAIL Class#method: message` lines, or `CRASH in Class#method` if the test process died. Exit code 0 means all passed, 1 a failure, crash, timeout or failed build, and 3 no phone.
- Logs: `build/ui-tests-build.log` (Gradle) and `build/ui-tests-<module>.log` (the raw `am instrument` output, with full stack traces). Read them only when something fails or there are "no results", and grep them rather than reading all of it.
- Exit code 3 (no phone): ask the owner to connect the Xiaomi 12X and accept the USB-debugging prompt (see CLAUDE.md). Don't land a change the owner can see or hear without a run, unless they say to skip it, and say so in the PR.
- `could not install …: INSTALL_FAILED_USER_RESTRICTED` or `INSTALL_GRANT_RUNTIME_PERMISSIONS` means MIUI's *USB debugging (Security settings)* or *Install via USB* is off (see CLAUDE.md).

## Known traps

- Xiaomi (MIUI) blocks test activities from starting unless the test package has app-op MIUIOP 10021, which every install resets. `scripts/ui-tests.sh` runs a loop on the phone that keeps setting it; never bypass the script. A hang with `MIUILOG- Permission Denied Activity` in logcat means the loop wasn't running.
- If the laptop side dies mid-run, the tests carry on on the phone. Wait for the test process (`ps -A | grep twangle`) to end before the next run, or stop it (`adb shell am force-stop <package>.test`). Read results from logcat (`TestRunner: failed`, `run finished`).
- Keep the phone's screen on during a run. If it turns off (power button), the next test waits for a frame that never comes and fails with "test timed out after 10 seconds" in `waitForNextChoreographerFrame`, and the tests after it fail with "Already interrogating!" or "No compose hierarchies found". Re-run; it isn't a bug in the test.
- With the shared activity, one test's content is removed before the next starts. A test that fails with "setContent can only be called once per test" called `setContent` twice. Change state through `mutableStateOf` instead.
- Don't call `performScrollTo` on the string-lock balloon. It lies outside its parent's bounds and Compose's scroll-into-view never returns. Scroll to the picker or the strings instead.
- `:core:audio` records from the real mic. Don't play test tones during it, and keep any tone short and self-stopping.
