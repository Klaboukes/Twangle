---
name: full-check
description: Run Twangle's full on-device check unattended, whenever the owner starts it (about 45 minutes; until 2026-11-06 it was the overnight run) — first the translations (/translate), then every UI test (light and dark), then the screens' tests again in the high-contrast look, then every language at 2× text where a screen changed since it last passed — fix what it finds, re-check the fixes, tick the roadmap and land it through a PR, without asking the owner anything, and summarize it at the end. Use when the owner says "start the full check", "run the full check", "/full-check", or the older "start the overnight run", "/overnight-run", or plugs the phone in for the tests the roadmap left for it.
argument-hint: "[optional: --sweep, --contrast or --languages to run only one part, --full to skip no language check, --store for the store screenshots]"
---

# The full check

The owner starts it whenever suits them, over lunch or at bedtime, with a test phone on USB and leaves it
alone (owner, 2026-11-06: at about 35 minutes it no longer needs the night, so it is no longer the
overnight run; about 45 since Phase 86's high-contrast pass, 2026-11-08), taking turns between the Xiaomi 12X and the Galaxy S26 from one run to the next.
With a second device connected (the other phone or the Lenovo tablet) it shares the jobs between them
and takes less time: a little over half with two phones, about two thirds with a phone and the tablet (owner, 2026-11-09). Say in the summary which devices ran it. It runs unattended, so the owner isn't asked anything: take the
recommended answer to every question, note it, and put it in the summary at the end for them to double-check. That never covers a step that reaches Google Play
(`scripts/release.sh upload`, `promote`, `rollout`, `halt`, `listing`) or publishes anything (the website,
the privacy policy): those wait for the owner's yes after the summary. CLAUDE.md's hard rules, Git workflow and
environment notes apply; the [ui-test skill](../ui-test/SKILL.md) says how to read results and
what the phone's failures mean.

## 1. Pre-flight (a few minutes, no phone run yet)
Run it in the main folder, which belongs to the full check and releases (CLAUDE.md, *One worktree
per session that changes files*): the release reads its summary there. If another session has the
main folder on a branch, wait for it or ask in the summary; never switch it away under them.

```bash
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"   # adb is not on PATH (CLAUDE.md)
git status --short && git switch main && git pull
gh run list --branch main --limit 3          # a failed nightly run is fixed first (not one GitHub didn't run: roadmap-step, step 0)
"$ADB" devices                                # the phone (f813c98c or R3GL502L08P) "device"; the watch too, when left on
S=<a phone's or the tablet's serial>          # each device in turn; never exported: the run picks its devices itself
"$ADB" -s "$S" shell dumpsys battery | grep -E "temp|AC"   # charging, below ~35 °C
"$ADB" -s "$S" shell settings get system font_scale   # 1.0; the tablet's 1.3 (the owner's) is set to 1.0 for the run and put back after (devices.md)
"$ADB" -s "$S" shell settings get global stay_on_while_plugged_in   # non-zero: the screen stays on
```
- The script keeps Windows awake while it runs (a sleeping PC drops the phone: on 2026-10-19 it
  cost the night, and on 2026-10-27 again, as the keep-awake never loaded until it was fixed that
  day). It prints a warning in its first ten seconds when it can't; then put that in the summary.
  A closed laptop lid still sleeps it, so the laptop stays open.
- **The devices** (owner, 2026-11-06 and 2026-11-09): the run takes every phone and tablet connected,
  and the Galaxy Watch 5, once at the start (`scripts/devices.sh`). One build, then each phone or tablet takes the
  next job from one queue whenever it is free (a module's sweep, its high-contrast run, its every-language check;
  `--dry-run` lists them in order), so a faster device simply does more of them, and one does them all, as before.
  Check each device as above (charging, cool, unlocked, its screen kept on). Start the run without
  `ANDROID_SERIAL`: with it set the run uses that device alone (`FULL_CHECK_SERIALS="<serial> <serial>"` names several).
  A device that drops off mid-job puts that job back for the others, and the summary says so. A device that
  comes or goes during the run changes
  nothing (on 2026-11-05 the watch came on over Wi-Fi a minute in and every phone install stopped on
  "more than one device"). With the watch connected, the watch app's tests run on it first
  (`scripts/ui-tests.sh :wear --thorough`, a few minutes, its screen kept on and its time-out put back).
  No watch: the stage is skipped, which is no failure, but the run then says "watch not tested"
  (exit 3) and the release refuses it until `scripts/full-check.sh --watch` passes; a watch that drops off mid-stage is the same.
  The summary names the devices (with more than one, how many jobs each did) and what the watch did.
  Each device's lines in the log start with its tag (`[phone] `, `[tablet] `), and with more than one
  its logs and pulled pictures carry the tag too (`connected_android_test_additional_output-tablet`).
- A Twangle on the phone signed by the owner's other machine is uninstalled and installed again by
  `scripts/ui-tests.sh` (the owner's rule); it says so in the log.
- The run works on either test phone, the Xiaomi 12X or the Galaxy S26 (`R3GL502L08P`, API 37):
  the tests give the same answer on any phone (docs/architecture/testing.md, *Any phone*). A
  failure on one phone only is a test that still assumes a phone, or a real bug on that kind of
  phone: find out which, never skip it.
- Check no other session or sub-agent holds the phone (ListAgents). Every script that uses a device
  also takes its lock (`take_phone_lock`, `scripts/devices.sh`); a device another run holds puts its
  job back here, and the summary names that run (*Its result*, below).
- Read ROADMAP.md: phases may have items left for the full check (older ones say "Runs overnight"), and
  *Waiting on the owner* may have one open. The run below covers all of them; note which items it closes.
- **Translations first** (owner, 2026-10-28): run the [translate skill](../translate/SKILL.md) on
  `main`. By day texts are written only in English and Dutch; it translates what the other languages
  lack or have stale and lands that through its own PR, so this run's `@EveryLanguage` checks see the new
  texts. When `scripts/translations.py status` says every language is up to date it does nothing. Then
  `git switch main && git pull` again. Its report goes into the summary.
- Branch `chore/full-check-<date>` (or `phase-<N>/<topic>` when it closes a phase's item) from `main`.
- `./gradlew test lint assembleDebug` must pass before the phone run.

## 2. The run

```bash
scripts/full-check.sh --dry-run                     # which devices and jobs, which language checks (no tests run)
scripts/full-check.sh > build/full-check.txt 2>&1   # in the background, ~45 min on one phone (36 with the tablet too, 2026-11-09; less when little changed)
```
It builds every test app once, then starts the watch's tests on the watch beside the phones' and a smoke
test of the release build on the first phone (`scripts/release-smoke.sh`, about 4
minutes: R8's build started, left with Home, started again, turned on its side and back; the UI tests
run the debug build, which missed 2.1's crash on Android 13, 2026-11-09). A crash there is an app bug:
its log (`build/full-check/release-build.txt`) has the stack; reproduce it with the same script.
Then the devices share these jobs, one module each (`scripts/ui-tests.sh <module> --built`): `--thorough --cool` (every module, light and dark, one class at a
time, waiting while the phone is 37 °C or warmer; 1.1's six styles took about 55 minutes, 2.0's one
look about 16 minutes, 2026-11-05), then the screens' modules again in the high-contrast look
(`scripts/ui-tests.sh <the six screen modules> --high-contrast --thorough --cool`, its white on black and
black on white in one pass, as each test loops over light and dark itself; about 12 minutes, 2026-11-08;
testing.md, *Every screen light and dark*; `--contrast` runs only it), then the `@EveryLanguage`
checks one module at a time with cooling pauses (each device cools on its own), and writes `build/full-check/summary.txt`
(each stage's log, `sweep.txt`, `contrast.txt` and `languages.txt`, has its jobs in order, each saying which device ran it). A screen where a
language didn't fit is saved as a picture under the module's
`build/outputs/connected_android_test_additional_output`; look at it with Read.

**Only the language checks that need it run** (owner, 2026-10-30: 1.1's 36 module
and style checks, in the languages it had then, took 6 of the night's 8 hours; 2.0 has one look, so six module checks, now in 36 languages). A module's checks that passed whole are
noted (`build/ui-tests-passed/languages/`), and a later run skips them while nothing the module
shows changed since (the rule `--since-pass` uses) and the pass is under a week old; the log says
"passed …, nothing it shows changed since", and the summary's second line how many ran. A run after
a change every screen shows (the design system, a new language, the run's translations of texts in
every module) runs nearly all of them, about 17 minutes for all six in 2.0 (2026-11-05: Ear training and Practice
about 6 each, the tuner, Settings and the metronome 1 to 1½, `:app` under half a minute); a quiet one, very few. `--full` runs all
(after a change to the test helpers' scoping, or when in doubt). Skipped checks count as passed for the
release, as `--since-pass` modules do.

**The store screenshots are not part of it** (owner, 2026-10-30, they took 50 minutes):
they are made before the store listing is sent, and `scripts/release.sh listing` refuses pictures of
an older app ([release skill](../release/SKILL.md), step 8). Make them only when the owner asks, or
when the summary should show a change to them: `scripts/full-check.sh --store` (or
`scripts/store-screenshots.sh`).

- **While it runs, don't edit code, tests or scripts**: bash reads a running script as it goes, and
  the devices install what the run built at its start. Docs (ARCHITECTURE.md and docs/architecture/, ROADMAP.md, skills) are
  safe to edit: the run hands the commit and clean state it started from to every device's
  `scripts/ui-tests.sh` (`UI_TESTS_COMMIT`, `UI_TESTS_CLEAN`), so a doc edited meanwhile no longer stops
  the later modules' passes from being noted (Phase 90).
- **Its result** (Phase 90): the first line of `build/full-check/summary.txt` says "everything passed",
  "failures below" or "not finished: a device was busy with another run (below)", with ", watch not
  tested" when the watch's tests were asked for and didn't run (no watch, it dropped off, or another
  run had it). The exit code says the same: 0, 1, 5, and 3 for "everything else passed, watch not
  tested". A device another run holds puts its job back for the others (or for itself after a
  five-minute wait), and the summary names the run that had it. The run also writes
  `build/full-check/result.txt`, the record `scripts/release.sh full-check` and `bundle` read: a
  line for the phones and one for the watch, each written only by a run that did all of that part
  (a run with a narrowed module list, or `--sweep` alone, writes no phones line). A failed line
  names what failed (`failed build`, `failed sweep,languages`); a failed or cut-off build is one,
  though nothing ran after it. `scripts/full-check.sh --self-check` checks that logic (seconds).
- Don't poll; you are told when it finishes. To start on failures early (the sweep prints each
  class as it finishes, e.g. `  PhoneScreenTest   3s  1 tests, 0 failed, 2s on the phone`), watch
  the log with the Monitor tool (30 min at most, re-arm on each expiry) and this filter, which
  stays quiet on passing lines: a case-insensitive "fail" matches every "0 failed", and a bare
  "warn" matches test names like `ScreenOffWarningTest`.
  ```bash
  tail -n 0 -F build/full-check.txt | grep -E --line-buffered "(^|[^0-9])[1-9][0-9]* failed|FAILED|[Aa]wake|[Ww]arning:|WARN|exit=|[Ee]rror:|ERROR|locked|[Cc]annot|can't|[Cc]rash|is using the device|busy with|^== "
  ```
  It reports a failed class, a keep-awake or lock warning, each new stage (`== …`) and the end
  (`exit=` when the run was started with `; echo "exit=$?" >> build/full-check.txt`), and a device
  another run holds (`is using the device`, `busy with`).
- Never play test tones, never loop a tone, and never re-run the whole sweep for a fix.

**When store screenshots were made:** look at every language's eight pictures in
`fastlane/metadata/android/<locale>/images/phoneScreenshots/` (a contact sheet per language: this PC
has no ImageMagick, so draw them with PowerShell's System.Drawing, 4 × 2 at 35 %, into
`build/full-check/sheets/`): a caption cut off or set in
the wrong script, a screen without its note or chord, a bubble in the wrong language. Fix and make
only those languages again (`scripts/store-screenshots.sh <locale> …`). The pictures are git-ignored
(docs/store-listing.md, *Phone screenshots*); put the en-US and one other language in the
summary.

## 3. Fix what it finds

For each failure decide first: **app bug or outdated test?**
- Read the test and the code. Reproduce with the smallest run (`scripts/ui-tests.sh <module> -c
  Class#method`, `-f` for parameterized names like `screenshot[TUNE_UP]`, `--thorough` for the double
  checks, `DAY_LANGUAGES=<tag> … --languages` for the failing language's checks, `--all-languages`
  for all of them).
- **Re-check a language fix in every language** (`scripts/ui-tests.sh <module> --thorough
  --all-languages -f 'everyLanguage'`, a few minutes a module), not the failing language
  alone: some fail only after the languages before them. Bengali's About title in Futuristic
  (2026-10-30) never failed on its own, and its first fix ran French off the bar, which only the run
  in every language showed. Then the fixed language alone is no proof either way.
- When it isn't clear, add a temporary probe (a `Log.i("PROBE", …)` read back with
  `adb logcat -d -s PROBE`, or a screenshot to the extra output) and remove it before committing.
- A real bug: fix the app, keep the test. A test that is wrong about the app (a tag on the wrong
  node, a string read in the wrong language, a dialog's own window, rounding): fix the test and say
  why in the commit.
- Things learnt so far: the phone gives Twangle its own language (Dutch), so tests read expected
  strings in the window's language, never from `targetContext` as is; dialogs, menus and sheets are
  windows of their own with the phone's language and text size; Android silences the microphone of
  an app with nothing in the foreground; TalkBack's spoken hint is held back up to 2 s; Compose
  takes function references to a local fun for equal, so a button may hold an old one.
  Scripts Nunito lacks (Burmese, Arabic's digits) are drawn in the phone's own fallback font, which differs by
  maker: the tablet has stock Android's, much taller than the Xiaomi's, so a failure on the tablet only is often
  a real bug the Xiaomi's fonts hide (2026-11-09: Burmese's word lines past their slots; Arabic's tempo dropping
  its last digit). But not always: a text whose words change in place (a test switching the language without
  making the screen anew) can keep its old line height there, which the app never shows, as its language change
  makes the screen anew; a probe that makes the screen anew (`key(locale)`) tells which (the About row after
  Hungarian, 2026-11-09). A test stuck for many minutes on one screen is the screen never settling: a dump of the test
  process's threads (`adb shell run-as <test package> kill -3 <pid>`, then `adb bugreport` for `FS/data/anr/`)
  shows where.
- After a fix run only the failed tests and their area. Land nothing with a red test. A red test on one device
  only that was red before this run's changes (a pre-existing bug, as Arabic's tempo on the tablet) doesn't hold
  back the fixes: land them and put the open bug in the summary with a follow-up.

## 4. Land it

- Small focused commits, `./gradlew test lint assembleDebug` again, tick ROADMAP.md (☐ → ☑) for
  the items the run closed, with what was found in brackets, and update the architecture docs where a
  design changed.
- Push, `gh pr create --base main`, `gh pr merge --squash --delete-branch`, `git switch main &&
  git pull` (the owner's policy: merge right away; `--delete-branch` is fine here, as the run works
  in the main folder, where `main` is checked out, not in a worktree).
- On `main`, when there is time left: `scripts/ui-tests.sh --since-pass --thorough`. Every module
  the sweep passed whole is already noted as passed; a module that failed lost its older pass note, so
  this runs the ones that failed (or changed with the fixes) once more and notes them too, so the next
  release (the [release skill](../release/SKILL.md)) has little or nothing left to run.
- **When the run's failures were fixed** (its phones' line in `build/full-check/result.txt` says 1)
  and every fix re-checked as in step 3, then `--since-pass --thorough` above has nothing left: on
  `main`, run `scripts/full-check.sh --fixed '#<fix PR>'`. It vouches only for a real re-check (Phase
  91): it refuses while a module is left to re-check, while a module whose sweep failed hasn't passed
  again whole and `--thorough` after the run (a quick run leaves out the `@Thorough` tests), and
  always when what failed was the build, the release build's smoke test, the high contrast, a
  language check or no phone at all: those have nothing a fix can be re-checked against by hand, so
  the full check runs again. Otherwise it notes the run
  with its fixes as a pass at `main`'s commit for the release (`scripts/release.sh bundle` refuses
  otherwise) and adds the PR to the summary's first line; don't edit that line by hand. A watch
  whose tests failed is fixed and re-checked with `scripts/full-check.sh --watch` (a few minutes),
  which writes only the watch's line; a run that was "not finished" (a device busy) runs again.
- Put the phone back as it was: its system settings (font size, rotation, developer options), not
  Twangle's own settings (the owner resets those).

## 5. The summary at the end

Its first line says the result as `build/full-check/summary.txt`'s first line has it ("everything
passed", "failures below", "not finished", ", watch not tested"), the commit of `main` the run
checked and why it ran ("you asked for it", or a release waits for it), then which phone or devices, and what the watch did (the summary's *Phone* or *Devices* and *Watch* lines). Then plain language, short: what ran and how long, what it found (app bugs first), what was fixed and
how it was checked, what is left, and every choice made on the owner's behalf as a question to
double-check ("I picked X because Y — fine?"). Picture-worthy changes get a phone screenshot.
