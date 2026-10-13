# Writing UI tests that stay fast

Background and measurements: ARCHITECTURE.md §8, *Fast UI tests* and *Quick and thorough UI runs*.

## Rules

- **Use `createSharedActivityComposeRule()`** from `:core:testing`, not `createComposeRule()`. Every test still gets a fresh composition, test clock and `ViewModelStore`, but the activity stays open for the whole run, which saves ~0.7 s per test. Only a test that changes the activity itself (rotation, recreation, window flags) uses `createComposeRule()` or `ActivityScenario.use { }`.
  - A new module with Compose UI tests sets `testInstrumentationRunner = "io.github.klaboukes.twangle.core.testing.SharedActivityTestRunner"` and adds `androidTestImplementation(project(":core:testing"))`.
- **Call `setContent` once per test.** Change state through `mutableStateOf`. A second call fails with "setContent can only be called once per test".
- **Checks over every style** take their list from `allIfThorough(VisualStyle.entries, VisualStyle.DEFAULT)`.
- **Mark a test `@Thorough`** when another test already covers its check in a harder or equal case (font size, language, orientation, view). Never mark the only test of a behaviour.
- **Add a timeout rule.** A class that scrolls, waits or animates wraps every test in a JUnit `Timeout` rule (`TEST_TIMEOUT_SECONDS`, 10 s, or 20 s in `MeterLooksTest`), placed outermost around the compose rule. Raise the limit only for a reason you can name.

## Pixel checks

- Capture once and cut the picture up (`MeterLooksTest.captureCases`), since each capture costs about a frame.
- Build assertion messages only when a check fails: `if (bad) fail("… at $x, $y")`, not `assertTrue("… at $x, $y", ok)`.
- In whole-window loops, compare ARGB ints rather than `Color`s.
- Measure a thing once and assert several facts about it.

## Traps

- Don't call `performScrollTo` on the string-lock balloon. It lies outside its parent's bounds and never finishes scrolling. Scroll to the picker or the strings instead.
- `MeterLookScreenshots` has no cases unless `-a meterScreenshots=true` is passed (`scripts/meter-look-screens.sh` passes it).

## Profiling a slow test

Time `waitForIdle`, the captures and the pixel loops with `System.nanoTime()`, and log the totals in an `@After`. Read them with `adb logcat -s TestRunner:I <tag>:I`. To time every test, capture `adb logcat -v epoch -s TestRunner:I` during a run and subtract each test's `started` time from its `finished` time. Remove the timing code afterwards.
