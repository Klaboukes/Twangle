---
name: release
description: Make a new Twangle release for Google Play from what is on main — check main, raise the version, write the release notes in every language, land them, build the signed bundle, tag it, and hand the owner the files and the Play Console checklist. Use when the owner says "make a release", "release 1.0.1", "put the fixes on Play", "/release", or "/release patch|minor".
argument-hint: "[optional: patch or minor]"
---

# A release for Google Play

The whole routine is in [docs/release.md](../../../docs/release.md); read it first. The mechanical
steps are `scripts/release.sh`. Talk to the owner in plain language: what goes out, what they do next.

## 1. Is main ready?
- `git switch main && git pull`. Look at the last nightly CI run (`gh run list --workflow android.yml --limit 1`). A run GitHub refused to start (minutes, spending limit) isn't a code failure.
- `./gradlew test lint assembleDebug`, and `scripts/ui-tests.sh --all` on a test phone (the [ui-test skill](../ui-test/SKILL.md)). Fix failures first, through their own `fix/` branches.
- Find the last release: `git describe --tags --abbrev=0`. If nothing changed since that tag that a player would notice, say so and stop.

## 2. The version and the notes
- `git log --oneline <last tag>..main`: only fixes → `patch`; anything new to see or hear → `minor` (an argument from the owner wins). Tell the owner the new version in one line and go on.
- `git switch -c release/v<version>`, then `scripts/release.sh bump patch|minor`. It prints the versionCode and where the notes go.
- Write `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`: what changed for the player, in the app's own words, short lines starting with "- ", at most 500 characters, no emoji, no promotion words, nothing about code or tests. Calm and plain, like the store texts ([docs/store-listing.md](../../../docs/store-listing.md)).
- Translate it into every other folder in `fastlane/metadata/android/`, in the app's words for that language (the strings in `res/values-<language>/strings.xml`: "Strings", "Focus mode" and so on as the app calls them). Count characters as Play does (code points): CJK and Hindi texts are short, German and Russian long.
- `./gradlew :app:testDebugUnitTest --tests '*StoreListingTest*'` checks every language and the limit. `scripts/release.sh notes` shows the block the owner will paste.
- Commit (`Release <version>`), push, `gh pr create` (body: what goes out, the notes in English), `gh pr merge --squash --delete-branch`, then `git switch main && git pull`.

## 3. The bundle and the tag
- `scripts/release.sh bundle` on main. It builds `:app:bundleRelease` with the upload key, checks the signature, writes `release/Twangle-<version>.aab` and `release/Twangle-<version>-release-notes.txt`, tags the commit `v<version>` and pushes the tag.
- Without the upload key (`~/.twangle-keys/`, only on the owner's machines) it stops: tell the owner to run the release from the machine that has it. Never make or move a key.
- A version that already has a tag is refused: raise it again rather than reusing it.

## 4. Hand it over
Tell the owner, in plain language: the version, what's in it (the English notes), the two files in `release/` (as links), and that the Play Console steps are in docs/release.md, *What the owner does*: internal track, check on the Xiaomi, promote, staged rollout. Offer to walk them through it in the browser; the uploads and *Save and publish* are theirs to click.
