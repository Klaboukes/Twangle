---
name: release
description: Make a new Twangle release for Google Play from what is on main — check main, raise the version, write the release notes in every language, land them, build the signed bundle and tag it, then, each step only after the owner says yes, send it to Play's internal track, hand the owner the Xiaomi check, promote it to the closed test or production, raise or halt the staged rollout, and send the store listing when it changed. Use when the owner says "make a release", "release 1.0.1", "put the fixes on Play", "promote it", "raise the rollout", "halt the rollout", "send the store listing", "what is on Play", "any crashes?", "any reviews?", "/release", or "/release patch|minor". Also reads, without asking, what is on each Play track, the crash and ANR rates per version, and the last week's reviews.
argument-hint: "[optional: patch or minor]"
---

# A release for Google Play

The whole routine is in [docs/release.md](../../../docs/release.md); read it first. The mechanical
steps are `scripts/release.sh`. Talk to the owner in plain language: what goes out, what they do next.

**Ask before anything reaches Google Play.** `upload`, `promote`, `rollout`, `halt` and `listing`
change what people get or see, so each one waits for the owner's clear yes in chat, every time. Name
exactly what goes where, for example: "Send Twangle 1.0.1 (versionCode 10001) and its notes in 16
languages to the internal track on Google Play, as a draft?" A yes to one step is not a yes to the
next. When the owner's own message asks for that very step ("halt the rollout", "raise it to 50 %"),
that is the yes. Steps 1–3 (version, notes, bundle, tag) need no ask, nor do `status`, `health` and
`reviews`: they only read what is on Play (docs/release.md, *Looking at Play*). `status` is the quick
answer to "what is on Play now?" at any step.

Picking up a release that is already tagged (the owner asks for a promotion days later): check that
`twangle.version` in `gradle.properties` is that version, then go straight to the step asked for.

## 1. Is main ready?
- `git switch main && git pull`. Look at the last nightly CI run (`gh run list --workflow android.yml --limit 1`). A run GitHub refused to start (minutes, spending limit) isn't a code failure.
- `./gradlew test lint assembleDebug`, and `scripts/ui-tests.sh --all` on a test phone (the [ui-test skill](../ui-test/SKILL.md)). Fix failures first, through their own `fix/` branches.
- Find the last release: `git describe --tags --abbrev=0`. If nothing changed since that tag that a player would notice, say so and stop.

## 2. The version and the notes
- `git log --oneline <last tag>..main`: only fixes → `patch`; anything new to see or hear → `minor` (an argument from the owner wins). Tell the owner the new version in one line and go on.
- `git switch -c release/v<version>`, then `scripts/release.sh bump patch|minor`. It prints the versionCode and where the notes go.
- Write `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`: what changed for the player, in the app's own words, short lines starting with "- ", at most 500 characters, no emoji, no promotion words, nothing about code or tests. Calm and plain, like the store texts ([docs/store-listing.md](../../../docs/store-listing.md)).
- Translate it into every other folder in `fastlane/metadata/android/`, in the app's words for that language (the strings in `res/values-<language>/strings.xml`: "Strings", "Focus mode" and so on as the app calls them). Count characters as Play does (code points): CJK and Hindi texts are short, German and Russian long.
- `./gradlew :app:testDebugUnitTest --tests '*StoreListingTest*'` checks every language and the limit.
- Commit (`Release <version>`), push, `gh pr create` (body: what goes out, the notes in English), `gh pr merge --squash --delete-branch`, then `git switch main && git pull`.

## 3. The bundle and the tag
- `scripts/release.sh bundle` on main. It builds `:app:bundleRelease` with the upload key, checks the signature, writes `release/Twangle-<version>.aab` and `release/Twangle-<version>-release-notes.txt`, tags the commit `v<version>` and pushes the tag.
- Without the upload key (`~/.twangle-keys/`, only on the owner's machines) it stops: tell the owner to run the release from the machine that has it. Never make or move a key.
- A version that already has a tag is refused: raise it again rather than reusing it.

## 4. To the internal track (ask first)
- The Play key must be on this machine: `~/.twangle-keys/play-service-account.json` (or the file in `TWANGLE_PLAY_SERVICE_ACCOUNT`). Missing: step 9.
- Ask: "Send Twangle <version> (versionCode <code>) and its notes in every language to the internal track on Google Play, as a draft?" On a yes: `scripts/release.sh upload`.
- While Twangle is still a draft on Play, Play takes only draft releases: tell the owner to start it in the Play Console (*Test and release → Testing → Internal testing*, the draft release, *Next*, *Save and publish*), and offer to walk them through it in the browser; the clicks are theirs. Once Twangle is published, ask for `scripts/release.sh upload completed` instead, which needs no click.
- After the first upload, Play's pre-launch report follows within a few hours: offer to read it with the owner, its accessibility findings included.

## 5. The owner checks it on the Xiaomi
Nothing moves on until this check passes. Hand the owner the list (docs/release.md, *What the owner does*, step 3):
- On the Xiaomi, signed in with an internal tester's account, open Twangle's page in the Play Store through the internal testing link and update. It may take a few minutes to show up.
- About shows the new version. The tuner hears a string (the mic), the widget works, a language switch works, and whatever this release fixed is fixed.

Wait for the owner to say it passed. Something wrong: fix it on `main` through a `fix/` branch and make the next release from step 1. The bad build just stays on the internal track.

## 6. Promote the same build (ask first)
Only after the Xiaomi check passed. Nothing is built or uploaded again: the build the owner checked moves on.
- **Closed test** (while Google's 14-day closed test runs, before production access): ask "Move Twangle <version>, the build you checked, from the internal track to the closed test, as a draft?" → `scripts/release.sh promote closed`. A draft is started in *Closed testing* as in step 4; `promote closed completed` makes it live for the testers at once.
- **Production** (once Google has given production access): ask "Move Twangle <version> to production for 20 % of people? Google reviews it first, a few hours to a few days." → `scripts/release.sh promote production` (`promote production draft` leaves it to start in the Play Console).

## 7. The staged rollout (ask first)
- Before every raise: `scripts/release.sh health` (crash and ANR rates per version against Play's lines of 1.09 % and 0.47 %, with a verdict) and `scripts/release.sh reviews`; show the owner both in plain words with the question. A verdict above Play's line: don't offer the raise, offer `halt` instead. "No data yet" (few users) is not a fault: say so, and lean on the owner's own look.
- After two or three quiet days (*Quality → Android vitals → Crashes and ANRs*, and the reviews; offer to look with the owner in the browser), ask "Raise Twangle <version> on production from 20 % to 50 %?" → `scripts/release.sh rollout 50`. A few days later the same for 100 % (`rollout 100`: everyone, the staged rollout ends).
- Something wrong: ask "Halt Twangle <version> on production? Nobody new gets it; who has it keeps it." → `scripts/release.sh halt`. Then fix it through a `fix/` branch and make the next release (a halted version can't be uploaded again). A false alarm: `rollout <percent>` starts it again, also behind a yes.

## 8. The store listing (only when it changed, ask first)
Not part of every release: only when the store texts or pictures changed since they were last sent (`git log -- fastlane/metadata/android/` apart from `changelogs/`), a language was added, or the app looks different enough that the screenshots are out of date.
- Every language needs its eight screenshots on this PC: `scripts/store-screenshots.sh` (phone connected, about 2 minutes a language; [docs/store-listing.md](../../../docs/store-listing.md)). `listing` refuses without them and says how old the oldest are.
- Ask: "Send the store listing to Google Play in all <n> languages: the texts, screenshots, icon, feature graphic and contact details? It replaces what is there, also anything changed by hand in the Play Console." On a yes: `scripts/release.sh listing`, then offer to look at it with the owner in the Play Console.

## 9. Without the Play key (another machine)
`release.sh` stops at once, saying the key is missing, and sends nothing. Never make, copy or move a key. Either the owner runs the step on the machine that has the key, or does it by hand in the Play Console (docs/release.md, *By hand, without the key*): drag in `release/Twangle-<version>.aab` and paste `release/Twangle-<version>-release-notes.txt` (`scripts/release.sh notes` prints the same block); promote, raise and halt there too. Offer to walk them through it in the browser.

## 10. Hand it over
After each step, tell the owner in plain language what is on Google Play now and where (the version, the track, the percentage; `scripts/release.sh status` reads it), the English notes on the first step, and what comes next and when: their Xiaomi check, a promotion, the next rollout step in a few days.
Once a version reaches people (the closed test, production), check `scripts/release.sh reviews` now and then (it shows only the last week), and tell the owner what players say, translated; replies are theirs to write in the Play Console.
