---
name: release
description: Makes a Twangle release for Google Play from what is on main and carries it through Play's tracks — the version, release notes in every language, the signed bundle and tag, then, each only after the owner's yes, the internal track, the promotion to the closed test or production, the staged rollout or a halt, and the store listing. Also answers without asking what is on each Play track, the crash and ANR rates per version, and the last week's reviews. Use when the owner says "make a release", "release 1.0.1", "put the fixes on Play", "promote it", "raise the rollout", "halt the rollout", "send the store listing", "what is on Play", "any crashes?", "any reviews?", "/release", or "/release patch|minor".
argument-hint: "[patch | minor | promote | rollout <percent> | halt | listing | status]"
---

# A release for Google Play

Every step is a command of `scripts/release.sh` (its header explains each one). The owner's side of a
release and the background are in [docs/release.md](../../../docs/release.md); open the sections the
steps below point to, not the whole file. Talk to the owner in plain language: what goes out, what
they do next.

**Ask before anything reaches Google Play.** `upload`, `promote`, `rollout`, `halt` and `listing`
change what people get or see (the watch app's too, with `watch`), so each one waits for the owner's clear yes in chat, every time. Name
exactly what goes where: "Send Twangle 1.0.1 (versionCode 10001) and its notes in every language to
the internal track on Google Play, as a draft?" A yes to one step is not a yes to the next. When the
owner's own message asks for that very step ("halt the rollout", "raise it to 50 %"), that is the
yes. Everything else only builds or reads (`bump`, `notes`, `profile`, `full-check`, `bundle`, `status`, `health`,
`reviews`, `listing-changes`) and needs no ask.

**Managed publishing is on** (owner, 2026-11-08): nothing Claude sends goes live by itself. Once
Google has approved a change it waits in the Play Console's *Publishing overview* until the owner
publishes it (docs/release.md, *What the owner does*, step 4). It does **not** hold back the review:
whatever the Google Play API commits plainly goes to Google's review at once (2026-11-09, when the
listing went into review before the owner sent it). So `upload` and `promote closed` make drafts,
which nothing reviews until the owner starts them in the Console and sends them; `listing` commits
"not sent for review", so it waits under *Changes not yet sent for review* until the owner sends it
with the release it goes with; `promote production` (not `draft`) goes to review as soon as it is
sent; a rollout raise and a halt need no review but wait for *Publish changes*. After each such
step, tell the owner what is left to click. Only the internal track and a raise to 100 % go at once.
`status` reads a change that waits there as live.

## Where to start
Begin every request with `scripts/release.sh status`: what is on each Play track now. For a release
that is already tagged, check that `twangle.version` in `gradle.properties` is that version.

| The owner says | Go to |
|---|---|
| "make a release", "put the fixes on Play", `/release [patch\|minor]` | step 1, then the checklist below |
| "it passed on the Xiaomi", "promote it" | step 6 |
| "raise the rollout", "how is the rollout doing?" | step 7 |
| "halt it", "something is wrong" | step 7, *Halt* |
| "send the store listing" | step 8 |
| "what is on Play?", "any crashes?", "any reviews?" | `status`, `health`, `reviews`: answer in plain words |
| `release.sh` says the Play key is missing | step 9 |

A new release, as steps on the task list:
```
- [ ] 1. main is ready
- [ ] 2. version and notes landed
- [ ] 3. bundle built and tagged
- [ ] 4. on the internal track (owner's yes)
- [ ] 5. the owner's Xiaomi check passed
```
Steps 6–8 follow later, each on the owner's word.

## 1. Is main ready?
- Work in the main folder, which belongs to releases and the full check (CLAUDE.md, *One worktree
  per session that changes files*): the full check's record and summary and the store screenshots are there.
- `git switch main && git pull`, then `git describe --tags --abbrev=0 --match 'v*'` (the `listing/*` and `store-screenshots` tags are no versions). If nothing a player would
  notice changed since that tag, say so and stop.
- **A clean full check first** (owner, 2026-10-28; the overnight run until 2026-11-06): nothing is released that the full check
  (every UI test, every language; a language check skipped because nothing it shows
  changed since it passed counts as passed) hasn't passed. `scripts/release.sh full-check` says it
  (only reads; Phase 90): from the run's record (`build/full-check/result.txt`) it checks that the
  phones' part passed, or its failures were fixed and re-checked by the run's own fix PR (`scripts/full-check.sh
  --fixed`, which notes the pass at `main`'s commit after the fix), and that the watch's tests passed,
  each from a clean tree at a commit `main` follows, with nothing changed since that goes into the
  bundles: only `*.md`, `docs/`, `.claude/`, `.github/`, `scripts/`, `website/`, `fastlane/` (the
  release notes), a regenerated Baseline Profile (`app/src/release/generated/`, which the debug builds
  the full check tests don't use) and the version line in `gradle.properties` may differ.
  `scripts/release.sh bundle` checks the same and refuses otherwise. The summary's first line says what the run found ("everything passed",
  "failures below", "not finished" when a device was busy, ", watch not tested"). A watch that
  wasn't tested holds the release up too: run `scripts/full-check.sh --watch` with the watch
  connected (a few minutes); only on the owner's word does the phone go without it
  (`bundle --watch-untested`). Otherwise **stop here, before any branch, version or note**:
  tell the owner in one line that the release waits for a clean full check (`/full-check`, about 45
  minutes on one phone, 36 with the tablet too on 2026-11-09), and that `/release` picks it up after. The record and summary are git-ignored, so on the other PC (or with none) the
  answer is also: run the full check first.
- **Every language complete and up to date** (owner, 2026-10-28): by day only English and Dutch are
  written, and the full check's first step translates the rest (`/translate`). Run
  `./gradlew :app:testDebugUnitTest --tests '*LocalizationTest' -Ptwangle.strictTranslations=true`
  (`scripts/release.sh bundle` runs it again and refuses to build otherwise). If it fails, a language
  lacks a text or has a stale one (`python -I scripts/translations.py status` lists them): stop here
  as for the full check, since new translations need that run's language tests too, and tell the
  owner in one line that the release waits for the next full check.
- The build checks: `gh run list --workflow android.yml --branch main --limit 1 --json headSha,conclusion`.
  A success on this very commit (`headSha` is `git rev-parse HEAD`) already ran test, lint and the
  builds: skip them. Otherwise (an older commit, a failure, a run GitHub refused to start) run
  `./gradlew test lint assembleDebug`.
- The phone: `scripts/ui-tests.sh --since-pass` on a test phone (the [ui-test skill](../ui-test/SKILL.md)).
  It runs only the modules changed since they last passed whole on this PC, so after a good full
  check there is often nothing to run. A phone that still has Twangle from Play (the last release's
  check) can't take the test build (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`): once that check is over,
  uninstall it as CLAUDE.md says and run again.
- **The Baseline Profile** (the launch review, 2026-11-01): `scripts/release.sh profile` says whether
  it was regenerated after the last change to the app's screens; `bundle` refuses otherwise. When it
  is older, regenerate it now, with the Xiaomi connected (about 15 minutes;
  [performance.md](../../../docs/architecture/performance.md), *Baseline Profile*):
  `scripts/baseline-profile.sh`, which picks the phone, takes its lock (exit 5: another run has it,
  wait and run again), uninstalls the debug build and puts it back after, then land
  `app/src/release/generated/` through a `chore/baseline-profile` branch. Not `./gradlew
  :app:generateBaselineProfile` by hand: it swaps the app with no lock, under another session's UI
  test run. Without the phone, ask the owner whether to release with the old profile (`bundle --keep-old-profile`) or wait.
- Fix failures first, each through its own `fix/` branch.

## 2. The version and the notes
- `git log --oneline <last tag>..main`: only fixes → `patch`; anything new to see or hear → `minor` (an
  argument from the owner wins). Tell the owner the new version in one line and go on.
- `git switch -c release/v<version>`, then `scripts/release.sh bump patch|minor`. It prints the
  versionCode and where the notes go. It counts from the last tag, not from `gradle.properties`: a
  version that was raised but never released (no `v<version>` tag; the release stopped before its
  bundle) is kept when it is already high enough (it says so), or raised with its notes moved to the
  new versionCode. Either way those notes are an old draft: **rewrite them** in every language for
  everything since the last tag, rather than adding to them.
- Write `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`: what changed for the player,
  in the app's own words, short lines starting with "- ", at most 500 characters, no emoji, no
  promotion words, nothing about code or tests. Calm and plain, like the store texts
  ([docs/store-listing.md](../../../docs/store-listing.md)).
- Translate it into every other folder in `fastlane/metadata/android/`, using the app's own words for
  that language (`res/values-<language>/strings.xml`: "Strings", "Focus mode" and so on as the app
  calls them). Play counts code points: CJK and Hindi texts come out short, German and Russian long.
  Two folders share a language: `es-419` is `es-ES` with Latin American words (*celular*, not
  *teléfono*), `zh-HK` is `zh-TW` with Hong Kong's (結他 for guitar) ([store listing](../../../docs/store-listing.md), *Folders*).
- `./gradlew :app:testDebugUnitTest --tests '*.StoreListingTest'` checks every language and the limit.
- Commit (`Release <version>`), push, `gh pr create` (body: what goes out, the notes in English),
  `gh pr merge --squash --delete-branch`, then `git switch main && git pull` (the main folder, step 1,
  where `--delete-branch` is fine; a worktree would merge as CLAUDE.md says for one).

## 3. The bundle and the tag
- `scripts/release.sh bundle` on main: the signed bundle and its notes in `release/`, the tag
  `v<version>` made and pushed. If the push fails it takes the tag back: run it again.
- Without the upload key (`~/.twangle-keys/`, only on the owner's machines) it stops: tell the owner to
  run the release from a machine that has it. Never make or move a key.
- A version that already has a tag is refused: raise the version again rather than reusing it.
- Code that the last full check on this PC didn't pass whole is refused, before anything is built
  (as `scripts/release.sh full-check`, step 1); `--watch-untested` only on the owner's word.
- An out-of-date Baseline Profile is refused (step 1 regenerates it); `--keep-old-profile` only on the
  owner's word.
- It builds the watch app's bundle too (`release/Twangle-<version>-watch.aab`), which goes to Play only
  through the `watch` steps (*The watch app* below).

## 4. To the internal track (ask first)
- Ask: "Send Twangle <version> (versionCode <code>) and its notes in every language to the internal
  track on Google Play, as a draft?" On a yes: `scripts/release.sh upload` (see *For now* about drafts).
- The owner starts the draft in the Play Console (docs/release.md, *What the owner does*, step 2).
  Offer to walk them through it in the browser; the clicks are theirs.
- After an upload, Play may make a pre-launch report within a few hours, only when Google's test lab
  has room (none came for any version up to 2.1.0). If one is there, offer to read it with the owner,
  crashes first, then its accessibility findings; if not, nothing waits on it.

## 5. The owner checks it on the Xiaomi
Nothing moves on until this check passes. Give the owner docs/release.md, *What the owner does*,
step 3, in a few lines, with what this release fixed added. Wait for them to say it passed.
Something wrong: fix it on `main` through a `fix/` branch and make the next release from step 1. The
bad build just stays on the internal track.

## 6. Promote the same build (ask first)
Only after the Xiaomi check passed. Nothing is built or uploaded again: the build the owner checked
moves on.
- **Closed test** (until Google gives production access): ask "Move Twangle <version>, the build you
  checked, from the internal track to the closed test, as a draft?" → `scripts/release.sh promote closed`.
  The owner starts the draft in *Closed testing*, as in step 4, then sends it for review and
  publishes it once approved.
- **Production:** ask "Move Twangle <version> to production for 20 % of people? It goes to Google's
  review at once; that takes a few hours to a few days, then you publish it." (The first production
  release: "for everyone", since Play allows no staged rollout for an app's first release; the script
  finds that out itself, and step 7 starts with the next version.) →
  `scripts/release.sh promote production`
  (`promote production draft` leaves it for the owner to start in the Play Console). When the store
  listing changed too, send it first (step 8) and promote with `draft`: then the owner sends both from
  *Publishing overview* as one review (a plain promotion would go to review on its own, and the listing
  sent later would restart that review). Then offer the rollout watch (step 7).

## 7. The staged rollout (ask first)
- **Before every raise:** `scripts/release.sh health` (crash and ANR rates per version against Play's
  lines of 1.09 % and 0.47 %, with a verdict) and `scripts/release.sh reviews`. Show the owner both in
  plain words with the question. A verdict above Play's line: don't offer the raise, offer a halt.
  "No data yet" (few users) is not a fault: say so, and lean on the owner's own look in *Quality →
  Android vitals*.
- **Raise:** after two or three quiet days, ask "Raise Twangle <version> on production from 20 % to
  50 %?" → `scripts/release.sh rollout 50`; it waits for the owner's *Publish changes* (no
  review). A few days later the same for 100 % (`rollout 100`: everyone, the staged rollout ends;
  it goes at once).
- **Halt:** ask "Halt Twangle <version> on production? Nobody new gets it; who has it keeps it." →
  `scripts/release.sh halt`, then ask the owner to look in *Publishing overview* and publish it if
  it waits there. Then fix it through a `fix/` branch and make the next release (a halted
  version can't be uploaded again). A false alarm: `rollout <percent>` starts it again, also behind a yes.
- **The rollout watch:** after the promotion to production, offer a daily look while the rollout
  runs. On a yes, make a scheduled task (`create_scheduled_task`, taskId `twangle-rollout-watch`,
  cron `0 9 * * *`) whose prompt stands on its own: in this machine's repo root (the working directory, written out in full: `D:\source\Twangle` on the desktop), run
  `scripts/release.sh status`, `health` and `reviews`; tell the owner in plain words how Twangle
  <version> is doing; say a raise looks fine only after two quiet days at the current step, and urge a
  halt when a rate is over Play's line; never run `upload`, `promote`, `rollout`, `halt` or `listing`.
  Delete the task once the rollout is at 100 % or halted. It runs only while the Claude app is open.

## 8. The store listing (only when it changed, ask first)
- `scripts/release.sh listing-changes` says what changed since the listing was last sent: the texts
  or pictures, a new language, and the app's screen changes since (do the screenshots still show the
  app as it looks?). Nothing changed: there is nothing to send.
- Every language needs its eight screenshots of the app as it is now: `scripts/store-screenshots.sh`
  (phone connected, about 80 seconds a language, about 50 minutes for all 38 store folders on 2026-11-09; [docs/store-listing.md](../../../docs/store-listing.md)).
  They are no longer made every night (owner, 2026-10-30), so make them when `listing-changes` says
  the app's screens changed since they were made: `listing` refuses them then (it reads the commit
  each picture was last made at, `build/phone-screenshots.made-at`), and names the command, often
  `--shots` for only the pictures a change can alter. A pull request that touches the screens or the
  screenshot test but can't change some pictures gets a line in `scripts/store-picture-scopes.txt`
  (or `Store-pictures: <scope>` in its commit message); without one, every picture counts as changed. Look them over as the
  full-check skill says (*When store screenshots were made*) before asking to send. They are
  git-ignored, but a run that made them all shares them on GitHub, and `listing` first takes that set
  when it is newer than this PC's (`scripts/share-store-screenshots.sh`), so the owner can make them
  on one PC and send from the other. `listing` refuses without them and says how old the oldest are. The 10-inch tablet set (the same
  script with the tablet connected instead, *Tablet screenshots*) is shared the same way, and then
  every language needs it.
- Ask: "Send the store listing to Google Play in all <n> languages: the texts, screenshots, icon,
  feature graphic and contact details? It replaces what is there, also anything changed by hand in the
  Play Console." On a yes: `scripts/release.sh listing` (it tags the send `listing/<date-time>` for
  next time), then offer to look at it with the owner in the Play Console. It is committed "not sent
  for review": it waits in *Publishing overview*, under *Changes not yet sent for review*, until the
  owner sends it with the release it goes with, for one review. Tell the owner so.

## The watch app (right after the phone)
Play takes a watch bundle only on the watch's own Wear OS tracks, never on the phone's
(docs/release.md, *The watch app*: Play's rule, and the owner's one-time steps in the Play Console:
the Wear OS form factor, later its opt-in). Each phone step has a `watch` twin, after its own yes:
`upload watch` (to `wear:qa`), `promote watch closed|production`, `rollout watch <percent>`,
`halt watch`. Ask as for the phone, naming the watch app: "Send Twangle <version>'s watch app
(versionCode <code + 1,000,000>) to the watch's internal track on Google Play, as a draft?" The first
`upload watch` is the first try of the track name on Play: if it is refused, `status` shows the names
Play gives the watch's tracks (docs/release.md says what to change).
**When:** the first production release is the phone app only, and the watch follows as soon as
possible after (ROADMAP.md Phase 88). `upload watch` may go while the phone waits, since its internal
track has no review (but, like every send, not while something is in review); the listing with the
Wear OS screenshots, `promote watch closed` (with the owner's opt-in to Wear OS) and `promote watch
production` only once the phone's production release is approved, one at a time, each when nothing
of the phone's is in review, since a send during a review would restart it.

## 9. Without the Play key (another machine)
`release.sh` stops at once, saying the key is missing, and sends nothing. Never make, copy or move a
key. Either the owner runs the step on the machine that has the key, or does it by hand in the Play
Console (docs/release.md, *By hand, without the key*) with `release/Twangle-<version>.aab` and the
notes from `scripts/release.sh notes`. Offer to walk them through it in the browser.

## 10. Hand it over
After each step, tell the owner in plain language what is on Google Play now and where (the version,
the track, the percentage; `status` reads it), the English notes on the first step, and what comes
next and when: their Xiaomi check, a promotion, the next rollout step in a few days.
Once a version reaches people (the closed test, production), look at `reviews` now and then (it shows
only the last week) and tell the owner what players say, translated; replies are theirs to write in
the Play Console.

## For now
True in October 2026; drop each line once it no longer is.
- **Twangle is still a draft on Play**, and Play then takes only draft releases: `upload` and
  `promote closed` make drafts that the owner starts by hand. Once the owner says Twangle is
  published, ask for `upload completed` and `promote closed completed` instead (live at once, no
  click), make `completed` their default in `release.sh`, and remove this line.
- **The closed test comes first:** Google's 14 days in a row of closed testing, then the owner applies
  for production access. Until Google grants it, step 6 is the closed test only.
- **The watch app goes right after the phone** (decision 3, ROADMAP.md Phase 88): `bundle` builds
  it and only the `watch` steps send it, each after the owner's yes. `upload watch` (its internal
  track, no review) may go while the phone waits, between its reviews; the reviewed steps (the
  listing with the Wear OS screenshots, `promote watch closed`, `promote watch production`) wait
  until the phone's production release is approved, then go one at a time, never while something
  of the phone's is in review (docs/release.md, *The watch app*).
