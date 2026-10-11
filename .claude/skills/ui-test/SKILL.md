---
name: ui-test
description: Run Twangle's on-device UI tests (connectedDebugAndroidTest) on the Galaxy S26, scoped to what changed, with tight timeouts and a short summary. Use whenever UI or instrumented tests need to run, while iterating on a UI change, before landing a change the user can see or hear, or when the user says "run the UI tests", "/ui-test", or "/ui-test <filter>".
argument-hint: "[optional: test-name regex, a :module, or --all]"
---

# UI tests on the phone

The on-device suite takes about 4 minutes, so never run all of it by habit. Run the smallest set that proves the change, and the affected modules in full **once** before landing.

Always go through `scripts/ui-tests.sh`. It works out which modules a change touches, runs each one with a time limit, and prints one line per module plus the failing tests. Don't call `./gradlew connectedDebugAndroidTest` directly: it runs every module, prints hundreds of log lines into the session, and `-Pandroid.testInstrumentationRunnerArguments.class=` would apply to every module in the call, failing the modules that don't have that class.

**When called as a command:** `/ui-test` runs `scripts/ui-tests.sh` (affected modules); `/ui-test :feature:settings` or `/ui-test --all` passes the argument on; any other text is a name filter, `scripts/ui-tests.sh <module> -f '<text>'`, on the module that has those tests (usually `:feature:tuner`). Report the summary lines in plain language.

## Scope ladder

1. **Iterating on one test or a few** (15–30 s, Bash `timeout: 180000`):
   `scripts/ui-tests.sh :feature:tuner -c TunerContentTest#someTest`
2. **Iterating on one area**, e.g. the tip or the hint (30–60 s, Bash `timeout: 180000`):
   `scripts/ui-tests.sh :feature:tuner -f 'Tip|Hint'`
3. **Once before landing**: the affected modules in full (up to ~4 min, Bash `timeout: 480000`):
   `scripts/ui-tests.sh`
4. **Build files or the version catalog changed, or the owner asks for everything** (~4 min, Bash `timeout: 480000`):
   `scripts/ui-tests.sh --all`

- `-f` is a regex matched against `Class#method`, so `-f 'Tip'` runs every test with "Tip" in its name, in any class of that module. Name tests so an area is one word away (they already do: `…Tip…`, `…Hint…`, `…Readout…`, `…OnItsSide…`, `…LargestFont…`).
- `-c` takes a class, `Class#method`, or a comma-separated list; a short class name gets the module's package.
- With no module, the script picks the affected modules from the branch's diff against `main` plus uncommitted and new files:
  - `feature/tuner` → tuner + app; `feature/settings` → settings + app; `core/audio` → audio + tuner + app
  - `core/dsp`, `core/settings`, `core/designsystem` → tuner + settings + app
  - a module's own `src/androidTest` → just that module
  - build files, the version catalog, the Gradle wrapper → everything
  - docs, `.github`, `.claude`, `scripts`, JVM unit tests → nothing (it says so and exits 0)
- After the full run, a fix doesn't need another full run. Re-run the tests that failed plus the tests for the area the fix touched (`-f`). Run the affected modules in full again only if the fix changed shared layout code (`TunerContent`, `GuitarControls`, `ModeSlot`, the theme).

## Measured on the S26 (October 2026)

| Module | Tests | Wall time (incl. build + install) | On the phone |
|---|---|---|---|
| `:feature:tuner` | 79 | 167 s | 104 s |
| `:feature:settings` | 16 | 39 s | 19 s |
| `:core:audio` | 5 | 12 s | 1 s |
| `:app` | 1 | 18 s | 7 s |

Every module call costs about 12–28 s of Gradle and install overhead, even for one test. The slowest single test takes about 2 s.

## Timeouts

- **Per test: 10 s.** `TunerContentTest` wraps every test in a JUnit `Timeout` rule (`TEST_TIMEOUT_SECONDS`), so a hang fails in 10 s instead of leaving a frozen window on the phone. A new UI test class that could hang (anything that scrolls, waits, or animates) gets the same rule, outermost around the compose rule. If a test legitimately needs longer, raise the constant for a reason you can name, not "to be safe".
- **Per module: 300 s.** The script kills a module's Gradle call after `MODULE_TIMEOUT` seconds (env var, default 300) and reports TIMED OUT. The tuner module takes ~170 s, so it fails fast if something stalls outside a test (install, a stuck device).
- **Bash tool:** use the timeouts in the scope ladder above, never the 10-minute maximum. If a run times out, look at the log before running it again.

## Reading results

- Output is one line per module: `:feature:tuner 48s  21 tests, 0 failed, 32s on the phone`, followed by `FAIL Class#method: message` lines. Exit code 0 means all passed, 1 a failure or timeout, 3 no phone.
- Gradle uninstalls the app after `:app`'s tests; the script then reinstalls the debug build, so the owner's phone always ends with Twangle on it ("Twangle reinstalled on the phone.").
- The full Gradle log is in `build/ui-tests-<module>.log`. Read it only when a test fails or there are "no results", and grep it rather than reading all of it.
- Exit code 3 (no phone): ask the owner to connect the S26 and check that Samsung Auto Blocker is off (see CLAUDE.md). Don't land a change the owner can see or hear without a run, unless they say to skip it, and say so in the PR.

## Known traps

- Don't call `performScrollTo` on the string-lock balloon. It lies outside its parent's bounds and Compose's scroll-into-view never returns. Scroll to the picker or the strings instead.
- `:core:audio` records from the real mic. Don't play test tones during it, and keep any tone short and self-stopping.
