# Releasing an update on Google Play

How a new version of Twangle gets from `main` to people's phones. Claude does the build part with the
`/release` skill (`.claude/skills/release/SKILL.md`) and `scripts/release.sh`; the owner does the
Play Console part with the checklist below.

## The idea
- **Not every change is a release.** Fixes and features land on `main` as usual. Every week or two, or
  sooner for a bad bug, what is on `main` goes out as one release.
- **Every upload has a higher version.** Play refuses a version it has seen. One place holds it:
  `twangle.version` in `gradle.properties`. Fixes raise the last number (1.0.0 → 1.0.1), new features
  the middle one (1.0.1 → 1.1.0), something big the first (2.0.0). The build turns it into Play's
  versionCode, major × 10000 + minor × 100 + patch (1.0.1 is 10001; [performance.md](architecture/performance.md), *Release build*).
- **Internal first, then promote the same build.** Each release goes to the internal track, the owner
  installs it from Play and checks it, and then that same build is promoted to the closed track or to
  production, so what people get is exactly what was checked.
- **Production in steps** (a staged rollout: 20 %, then 50 %, then everyone), so a problem can be
  halted before it reaches everyone.
- **Every release is tagged** in git (`v1.0.1` on the commit the bundle was built from), so any
  version can be found again.

## What Claude does (`/release`)
1. Checks `main`: the last nightly CI run, `./gradlew test lint assembleDebug` and `scripts/ui-tests.sh --all` on a test phone.
2. Reads what changed since the last tag (`git log v<last>..main`) and picks patch or minor.
3. On a branch `release/v<version>`: `scripts/release.sh bump patch|minor`, then the release notes,
   one file a language: `fastlane/metadata/android/<Play locale>/changelogs/<versionCode>.txt`
   (the fastlane layout; F-Droid reads it too once the code is open). At most 500 characters (Play's
   limit), plain text, no emoji, in the app's own words; `StoreListingTest` checks every language has
   them and keeps within the limit. Then a pull request, merged like any other.
4. On `main`: `scripts/release.sh bundle` builds the phone app's bundle signed with the upload key
   (`~/.twangle-keys/`), checks the signature, and puts two files in `release/` (git-ignored):
   `Twangle-<version>.aab` and `Twangle-<version>-release-notes.txt`, every language in one block in
   the Play Console's format (`<en-US>` … `</en-US>`, `<nl-NL>` …). Then it tags the commit and
   pushes the tag. It refuses a version that already has a tag, uncommitted changes, or a `main`
   that differs from GitHub's.

The watch app isn't part of a release yet: it follows in an update (decision 3). When it goes out, its
bundle (`:wear:bundleRelease`, versionCode + 1,000,000) is uploaded to the same release.

## What the owner does in the Play Console
The menu names were right in October 2026; Google moves them now and then.

**1. Upload to the internal track**
- [ ] *Test and release → Testing → Internal testing → Create new release*.
- [ ] Drag in `release/Twangle-<version>.aab`. The release name fills in by itself (`1.0.1 (10001)`).
- [ ] Paste the whole of `release/Twangle-<version>-release-notes.txt` into *Release notes*.
- [ ] *Next*, read any warnings (a warning about a deobfuscation file doesn't apply: the bundle carries it), then *Save and publish*.

**2. Check it on a phone**
- [ ] On the Xiaomi (signed in with an internal tester's account), open Twangle's page in the Play
      Store through the internal testing link and update. It may take a few minutes to show up.
- [ ] About shows the new version. The tuner hears a string, the widget works, a language switch works,
      and whatever this release fixed is fixed.

**3. Promote the same build**
- [ ] In *Internal testing*, the release → *Promote release* → *Closed testing* (while the closed test
      runs) or *Production* (once Google has given production access).
- [ ] For production: set the rollout to **20 %**, then *Send for review*. Google's review takes from
      a few hours to a few days; Play then updates phones by itself.
- [ ] After two or three quiet days (*Quality → Android vitals → Crashes and ANRs*, and the reviews),
      *Production → Manage rollout*: 50 %, then a few days later 100 %.
- [ ] Something wrong: *Halt rollout*. Nobody new gets the version; fix it on `main` and make the next
      release (a halted version can't be uploaded again, the fix gets a higher version).

**Never:** upload a bundle that wasn't made by `scripts/release.sh bundle`, or share the upload key.
A lost upload key can be reset through Play support, since Google holds the app signing key.
