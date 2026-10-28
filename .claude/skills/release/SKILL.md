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
change what people get or see, so each one waits for the owner's clear yes in chat, every time. Name
exactly what goes where: "Send Twangle 1.0.1 (versionCode 10001) and its notes in 16 languages to
the internal track on Google Play, as a draft?" A yes to one step is not a yes to the next. When the
owner's own message asks for that very step ("halt the rollout", "raise it to 50 %"), that is the
yes. Everything else only builds or reads (`bump`, `notes`, `bundle`, `status`, `health`, `reviews`,
`listing-changes`) and needs no ask.

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
- Work in the main folder, which belongs to releases and the overnight run (CLAUDE.md, *One worktree
  per session that changes files*): the overnight summary and the store screenshots are there.
- `git switch main && git pull`, then `git describe --tags --abbrev=0`. If nothing a player would
  notice changed since that tag, say so and stop.
- **A clean overnight run first** (owner, 2026-10-28): nothing is released that the overnight run
  (every UI test in every style, every language, the store screenshots) hasn't passed. The first line
  of `build/overnight/summary.txt` says which commit it ran on and whether everything passed. It counts
  when everything passed, or when its failures were fixed and re-checked by the run's own fix PR (the
  morning summary says so), and `git diff --stat <that commit>..main` shows no code change since but
  that fix, docs, `.claude/` or `scripts/`. Otherwise **stop here, before any branch, version or note**:
  tell the owner in one line that the release waits for a clean overnight run, and that `/release`
  picks it up the morning after. The summary is git-ignored, so on the other PC (or with none) the
  answer is also: wait for the overnight run.
- The build checks: `gh run list --workflow android.yml --branch main --limit 1 --json headSha,conclusion`.
  A success on this very commit (`headSha` is `git rev-parse HEAD`) already ran test, lint and the
  builds: skip them. Otherwise (an older commit, a failure, a run GitHub refused to start) run
  `./gradlew test lint assembleDebug`.
- The phone: `scripts/ui-tests.sh --since-pass` on a test phone (the [ui-test skill](../ui-test/SKILL.md)).
  It runs only the modules changed since they last passed whole on this PC, so after a good overnight
  run there is often nothing to run. A phone that still has Twangle from Play (the last release's
  check) can't take the test build (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`): once that check is over,
  uninstall it as CLAUDE.md says and run again.
- Fix failures first, each through its own `fix/` branch.

## 2. The version and the notes
- `git log --oneline <last tag>..main`: only fixes → `patch`; anything new to see or hear → `minor` (an
  argument from the owner wins). Tell the owner the new version in one line and go on.
- `git switch -c release/v<version>`, then `scripts/release.sh bump patch|minor`. It prints the
  versionCode and where the notes go.
- Write `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`: what changed for the player,
  in the app's own words, short lines starting with "- ", at most 500 characters, no emoji, no
  promotion words, nothing about code or tests. Calm and plain, like the store texts
  ([docs/store-listing.md](../../../docs/store-listing.md)).
- Translate it into every other folder in `fastlane/metadata/android/`, using the app's own words for
  that language (`res/values-<language>/strings.xml`: "Strings", "Focus mode" and so on as the app
  calls them). Play counts code points: CJK and Hindi texts come out short, German and Russian long.
- `./gradlew :app:testDebugUnitTest --tests '*.StoreListingTest'` checks every language and the limit.
- Commit (`Release <version>`), push, `gh pr create` (body: what goes out, the notes in English),
  `gh pr merge --squash --delete-branch`, then `git switch main && git pull`.

## 3. The bundle and the tag
- `scripts/release.sh bundle` on main: the signed bundle and its notes in `release/`, the tag
  `v<version>` made and pushed. If the push fails it takes the tag back: run it again.
- Without the upload key (`~/.twangle-keys/`, only on the owner's machines) it stops: tell the owner to
  run the release from a machine that has it. Never make or move a key.
- A version that already has a tag is refused: raise the version again rather than reusing it.

## 4. To the internal track (ask first)
- Ask: "Send Twangle <version> (versionCode <code>) and its notes in every language to the internal
  track on Google Play, as a draft?" On a yes: `scripts/release.sh upload` (see *For now* about drafts).
- The owner starts the draft in the Play Console (docs/release.md, *What the owner does*, step 2).
  Offer to walk them through it in the browser; the clicks are theirs.
- After the first upload, Play's pre-launch report follows within a few hours: offer to read it with
  the owner, its accessibility findings included.

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
  The owner starts the draft in *Closed testing*, as in step 4.
- **Production:** ask "Move Twangle <version> to production for 20 % of people? Google reviews it
  first, a few hours to a few days." → `scripts/release.sh promote production`
  (`promote production draft` leaves it for the owner to start in the Play Console). Then offer the
  rollout watch (step 7).

## 7. The staged rollout (ask first)
- **Before every raise:** `scripts/release.sh health` (crash and ANR rates per version against Play's
  lines of 1.09 % and 0.47 %, with a verdict) and `scripts/release.sh reviews`. Show the owner both in
  plain words with the question. A verdict above Play's line: don't offer the raise, offer a halt.
  "No data yet" (few users) is not a fault: say so, and lean on the owner's own look in *Quality →
  Android vitals*.
- **Raise:** after two or three quiet days, ask "Raise Twangle <version> on production from 20 % to
  50 %?" → `scripts/release.sh rollout 50`. A few days later the same for 100 % (`rollout 100`:
  everyone, the staged rollout ends).
- **Halt:** ask "Halt Twangle <version> on production? Nobody new gets it; who has it keeps it." →
  `scripts/release.sh halt`. Then fix it through a `fix/` branch and make the next release (a halted
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
- Every language needs its eight screenshots on this PC: `scripts/store-screenshots.sh` (phone
  connected, about 2 minutes a language; [docs/store-listing.md](../../../docs/store-listing.md)).
  `listing` refuses without them and says how old the oldest are. The 10-inch tablet set (the same
  script with the tablet connected instead, *Tablet screenshots*) goes along where it was made, and
  then every language needs it.
- Ask: "Send the store listing to Google Play in all <n> languages: the texts, screenshots, icon,
  feature graphic and contact details? It replaces what is there, also anything changed by hand in the
  Play Console." On a yes: `scripts/release.sh listing` (it tags the send `listing/<date-time>` for
  next time), then offer to look at it with the owner in the Play Console.

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
- **The watch app** is not part of a release yet (decision 3); `release.sh` doesn't touch it.
