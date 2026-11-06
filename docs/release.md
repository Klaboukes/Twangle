# Releasing an update on Google Play

How a new version of Twangle gets from `main` to people's phones. Claude does it with the
`/release` skill (`.claude/skills/release/SKILL.md`) and `scripts/release.sh`: the build, and then,
through the Google Play API (Phase 41), the upload to the internal track, the promotions, the staged
rollout and the store listing. **Each step that reaches Google Play waits for the owner's yes**,
since it changes what people get or see. The owner checks every build on the Xiaomi before it goes
further, and does the few things only the Play Console can do (the checklist below).

## The idea
- **Not every change is a release.** Fixes and features land on `main` as usual. When the owner asks
  (the owner decides when a version goes to Play, ROADMAP.md *Where things stand*), what is on `main`
  goes out as one release. Claude may suggest one when a bad bug is fixed or much has gathered.
- **After a clean full check** (owner, 2026-10-28): a release starts only once the full check
  (`/full-check`) has passed on the code it releases; until then `/release` stops before it raises
  the version or writes a note, and says it waits for a full check.
- **Every upload has a higher version.** Play refuses a version it has seen. One place holds it:
  `twangle.version` in `gradle.properties`. Fixes raise the last number (1.0.0 → 1.0.1), new features
  the middle one (1.0.1 → 1.1.0), something big the first (2.0.0). The build turns it into Play's
  versionCode, major × 10000 + minor × 100 + patch (1.0.1 is 10001; [performance.md](architecture/performance.md), *Release build*).
  A version counts as released once it has its `v<version>` tag. One that was raised but never
  released (a release that stopped before its bundle, such as 1.1.0 in October 2026) is not raised
  again: `scripts/release.sh bump` counts from the last tag, keeps that version when it is already
  high enough, and its notes are rewritten for everything since the last tag, so no version number is
  skipped and no unused notes stay behind.
- **Internal first, then promote the same build.** Each release goes to the internal track, the owner
  installs it from Play and checks it, and then that same build is promoted to the closed track or to
  production, so what people get is exactly what was checked.
- **Production in steps** (a staged rollout: 20 %, then 50 %, then everyone), so a problem can be
  halted before it reaches everyone.
- **Every release is tagged** in git (`v1.0.1` on the commit the bundle was built from), so any
  version can be found again.

## What Claude does (`/release`)
1. Checks `main`: `./gradlew test lint assembleDebug`, skipped when the nightly CI run passed on this
   very commit, and `scripts/ui-tests.sh --since-pass` on a test phone, which runs only the parts of
   the app changed since their UI tests last passed on this PC (after a good full check, often none).
2. Reads what changed since the last tag (`git log v<last>..main`) and picks patch or minor.
3. On a branch `release/v<version>`: `scripts/release.sh bump patch|minor` (it keeps a version that
   was never released, above), then the release notes,
   one file a language: `fastlane/metadata/android/<Play locale>/changelogs/<versionCode>.txt`
   (the fastlane layout; F-Droid reads it too once the code is open). At most 500 characters (Play's
   limit), plain text, no emoji, in the app's own words; `StoreListingTest` checks every language has
   them and keeps within the limit. Then a pull request, merged like any other.
4. On `main`: `scripts/release.sh bundle` builds the phone app's and the watch app's bundles signed
   with the upload key (`~/.twangle-keys/`), checks the signatures, and puts three files in `release/`
   (git-ignored): `Twangle-<version>.aab`, `Twangle-<version>-watch.aab` (sent only with the update that
   brings the watch app, *The watch app* below) and `Twangle-<version>-release-notes.txt`, every
   language in one block in the Play Console's format (`<en-US>` … `</en-US>`, `<nl-NL>` …, for sending
   it by hand). Then it tags the commit and pushes the tag (a push that fails takes the tag back). It
   refuses a version that already has a tag, uncommitted changes, a `main` that differs from GitHub's,
   or an out-of-date Baseline Profile (below).
   - **The Baseline Profile** (`app/src/release/generated/`, [performance.md](architecture/performance.md),
     *Baseline Profile*) tells phones which code to compile ahead of time, so the app starts and draws
     quickly; it is made on the Xiaomi and committed, and a screen changed since it was made runs
     slower on slow phones until it is made again. `bundle` refuses when the profile's last commit is
     older than the last change under `app/src/main`, `feature/*/src/main` or
     `core/designsystem/src/main` (`scripts/release.sh profile` says the same without building). Claude
     then regenerates it (Xiaomi connected, about 15 minutes) and lands it through a `chore/` branch
     before the release, or, on the owner's word, releases with the old one:
     `scripts/release.sh bundle --keep-old-profile`.
5. With the owner's yes: `scripts/release.sh upload`, the bundle to the internal track (below).
6. Hands the owner the check on the Xiaomi (*What the owner does*, step 3) and waits for it to pass.
7. With the owner's yes each time: `promote closed` or `promote production`, later `rollout 50` and
   `rollout 100`, or `halt` when something is wrong (below).
8. With the owner's yes, only when the store texts or pictures changed (`scripts/release.sh
   listing-changes` says): `scripts/release.sh listing` (below).

Before each rollout raise Claude runs `scripts/release.sh health` and shows the owner what it says;
after a step reaches people, `status` and `reviews` (below). These only read, so they need no yes.
While a production rollout runs, Claude can keep a daily scheduled look (`twangle-rollout-watch`)
that runs the three and says whether a raise looks fine; it never changes anything on Play, and goes
once the rollout is at 100 % or halted.

## Uploads through the Google Play API
`scripts/release.sh upload`, after `bundle`, sends `release/Twangle-<version>.aab` and its notes in
every language (`changelogs/<versionCode>.txt`) to the **internal track** on Google Play, so nobody
drags or pastes anything. It goes through the Google Play Developer API with Gradle Play
Publisher in `:app` ([performance.md](architecture/performance.md), *Release build*), which reads the
texts and notes from `fastlane/metadata/android/` too, so they still live in one place.
- **The key:** the robot user's key, a service-account JSON key, made once in Google Cloud and the
  Play Console (Phase 41, 2026-10-22: the service account `twangle-uploader` in the Google Cloud
  project *Twangle Play*, `twangle-play`; in the Play Console for Twangle only, allowed to view the app,
  release to every track, manage testing tracks and manage the store presence), lives next to the upload key as
  `~/.twangle-keys/play-service-account.json` (another file: the environment variable
  `TWANGLE_PLAY_SERVICE_ACCOUNT`, or the Gradle property `twangle.playServiceAccount`). Never in the
  repo. Without it `upload` stops at once, saying the key is missing, and nothing is sent.
- **A draft first:** while Twangle itself is still a draft on Play (before it is first published),
  Play only takes draft releases, so `upload` makes one: the owner opens it in *Internal testing*
  and clicks through to *Save and publish*. Once the app is out of draft, `scripts/release.sh upload
  completed` makes the internal release live at once.
- Play's API can't make a new app or take its very first bundle: Twangle was made in the Play Console
  and 1.0.0 went to the internal track by hand (2026-10-21); every later version goes with `upload`.
- It refuses a version without its tag or its bundle (run `bundle` first), notes missing in a
  language, or notes changed since the tag. Only the very first release (1.0.0, already tagged and
  bundled) may go without notes. Play itself refuses a versionCode it has seen.
- Only the phone app is uploaded; the watch app follows in an update (decision 3) and goes to its own
  tracks (*The watch app* below).

Then the same build moves on, without building or uploading anything again. Each
command acts on the version in `gradle.properties`, which must be tagged, and sends its notes again.
- `scripts/release.sh promote closed`: from the internal track to the **closed test**, once the owner
  has checked the internal build on the Xiaomi. Play's API calls the closed test the Play Console
  makes first (*Closed testing - Alpha*) `alpha`; a closed test made by hand has its own name, set
  with `TWANGLE_PLAY_CLOSED_TRACK`. A draft by default, as `upload`; `promote closed completed` makes
  it live for the testers at once.
- `scripts/release.sh promote production`: from the internal track to **production** (whether or not
  it went to the closed test too) as a staged rollout to **20 %** (Google reviews it first). `promote production draft` leaves it a draft to
  start in the Play Console instead.
- `scripts/release.sh rollout <percent>`: raises the production rollout, a whole number 1–100
  (50, later 100); 100 is everyone and ends the staged rollout. It also starts a halted one again.
- `scripts/release.sh halt`: halts the production rollout. Nobody new gets the version; who has it
  keeps it.

## Looking at Play (read only)
Three commands only read what is on Google Play and change nothing there, so **Claude runs them
without asking** (`scripts/PlayReports.java`, plain Java run by Android Studio's JDK: nothing else to
install). They need the same key as `upload`; without it they stop, saying it is missing.
- `scripts/release.sh status`: each track (internal, the closed test `alpha`, the open test `beta`,
  production) with its releases: name, versionCode and status (a draft, live, a staged rollout to
  some %, or halted), and the store-listing languages. It opens a temporary edit (Play's scratch copy
  of the app's setup), reads it and deletes it, never commits it. Google's review of a release shows
  only in the Play Console (*Publishing overview*): a release in review reads as live here.
- `scripts/release.sh health [days]`: the user-perceived crash rate and ANR rate of each version over
  the last 7 days (or `days`, 1–90), from the **Google Play Developer Reporting API**, with a verdict
  against Play's bad-behaviour lines: **1.09 %** of users a day hit by a crash, **0.47 %** by an ANR
  (app not responding). Play's daily figures are in Pacific time and a day or two behind; each
  version's rate is the daily rates weighted by that day's users, as Play's own 7-day figure. While
  the app isn't public or has few users there is no data, and it says so: that isn't a fault.
  **Claude runs it before every rollout raise** and shows the owner the verdict with the question.
  The API is switched on in the Google Cloud project *Twangle Play* (2026-10-22); if it is ever off,
  `health` says to switch it on there.
- `scripts/release.sh reviews [language]`: the reviews of the last week, every language, each with
  its stars, date, language, app version and phone, the original text and a translation into
  `language` (English by default, e.g. `reviews nl`). Google's API gives only reviews with text from
  the last 7 days; older ones and star-only ratings are in the Play Console (*Quality → Ratings and
  reviews*). Read only: replying is a later idea, and the robot user may not reply.

## The store listing
`scripts/release.sh listing` sends the whole store listing to Google Play in every language at once,
from `fastlane/metadata/android/` as it is on `main` ([store-listing.md](store-listing.md)): the
title, short and full description and the eight phone screenshots of each language (and the 10-inch
tablet set where the languages have one, the same set in every language), the icon and the
feature graphic, and Play's app details. It needs the same key as `upload`, but no release: the
listing isn't tied to a version.
- **What changed since the last send:** each send is tagged `listing/<date-time>` on the commit it
  sent (the first, of 2026-10-22, was tagged afterwards). `scripts/release.sh listing-changes` reads
  git only: the texts and pictures changed since that tag (a new language shows as new files), and
  the app's screen changes since, for a look at whether the screenshots still match.
- **Texts, then pictures:** Play takes a language's pictures only once that language has a listing,
  so `listing` sends the texts and app details first, then everything (two passes).
- **Screenshots first:** they are git-ignored, so they exist only on the PC that made them
  (`scripts/store-screenshots.sh`, phone connected, about 2 minutes a language). `listing` refuses,
  naming the languages and the command to run, unless every language has its eight: Play shows the
  English screenshots in a language without its own, so a half-finished set is never sent. It says
  when the oldest was made, to make them again if the app has changed since.
- **Checked before sending:** `StoreListingTest` (texts within Play's 30, 80 and 4000 characters, the
  icon 512 × 512 and the feature graphic 1024 × 500 in Play's formats) and the screenshots against
  Play's rules (2 to 8, 320 to 3840 px a side, at most 2 : 1, at most 8 MB). The copy sent is 24-bit
  PNG: Play takes screenshots only without alpha.
- **One icon** goes with the default language, en-US, which Play shows in every language; **each language's feature graphic** (with its translated line) goes with its own. Before Phase 65 one feature graphic served every
  language.
- **App details:** Play sets the default language, contact email and website together, so `listing`
  sends all three: English (United States), twangle.tuner@proton.me and the website's home page,
  https://klaboukes.github.io/ (the privacy-policy page until Phase 54; decision 5; `playAppDetails` in `app/build.gradle.kts`). The contact phone stays empty.
- It sends everything each time, also what an earlier run sent, so it puts back anything changed by
  hand in the Play Console's store listing; pictures Play already has are skipped. Languages on Play
  that the repo doesn't have are left alone. The category, tags and the rest of *Store settings*
  stay in the Play Console.

## The watch app
The watch app isn't part of a release yet: it follows in an update (decision 3). `bundle` already
builds its bundle with every release (`release/Twangle-<version>-watch.aab`, `:wear:bundleRelease`,
versionCode + 1,000,000, the same tag), so the update that brings it needs no other build.

**Play's rule (checked 2026-11-01):** a watch bundle no longer goes on the phone's tracks. Play
gave Wear OS its own tracks in March 2023, and since September 2023 a Wear OS release on the phone's
tracks can't be updated: "you must use dedicated Wear OS tracks and create new releases on these
tracks", and once they exist, the API can no longer put a watch bundle on the phone's tracks
([Play Console Help, *Manage different form factor releases on dedicated tracks*](https://support.google.com/googleplay/android-developer/answer/13295490)).
In Play's API a Wear OS track is `wear:` and the track's name: `wear:qa` for internal testing (the
API's name for it, though phone releases may say `internal`), `wear:beta`, `wear:production`, and a
closed test `wear:<its name>` ([Google Play Developer API, *APKs and Tracks*](https://developers.google.com/android-publisher/tracks)).
To reach people, the watch app also needs a closed test release and the opt-in to Wear OS, which
starts Google's review against the Wear OS quality rules
([Android developers, *Package and distribute Wear OS apps*](https://developer.android.com/training/wearables/packaging)).

So the watch app's release runs beside the phone's, with `watch` in each step, each again after the
owner's yes:
- `scripts/release.sh upload watch`: the watch bundle and the same notes to `wear:qa`.
- `scripts/release.sh promote watch closed`: to the watch's closed test, `wear:alpha` unless
  `TWANGLE_PLAY_WATCH_CLOSED_TRACK` names another (the Play Console names it when it is made;
  `scripts/release.sh status` lists the watch's tracks as *Watch, …* with their API names).
- `scripts/release.sh promote watch production`, `rollout watch <percent>`, `halt watch`: as for the
  phone, on `wear:production`.

**Once, in the Play Console, before the first `upload watch`** (the owner, with Claude reading along):
- [ ] *Test and release → Advanced settings → Form factors → + Add form factor → Wear OS*, and under
      *Releases* choose *Use a dedicated release track for Wear OS* if Play still offers the choice.
- [ ] The Wear OS screenshots in the store listing (Phase 56, *The watch app in the listing*).
- [ ] After the first watch release is on its closed test: the same page, *Wear OS → Manage*, *Opt in
      to Wear OS* and agree to its review, then *Start rollout* once Google has approved it.

Not tried on Play yet: the first `upload watch` is a draft, and if Gradle Play Publisher or Play
refuses the track name (`wear:qa`), `scripts/release.sh status` shows the names Play gives the
watch's tracks; the name in `release.sh` (`WATCH_INTERNAL_TRACK`) follows it. By hand, the watch
bundle goes in the Play Console under the Wear OS form factor's own *Internal testing*.

## What the owner does
The menu names were right in October 2026; Google moves them now and then.

**1. Say yes, or not yet.** Claude asks in the chat before each step that reaches Google Play: the
upload, each promotion, each rollout step, a halt and the store listing. Nothing is sent until you
say yes.

**2. Start the internal draft** (while Twangle is still a draft on Play, Play takes only drafts)
- [ ] *Test and release → Testing → Internal testing*: open the draft release Claude sent, *Next*,
      read any warnings (a warning about a deobfuscation file doesn't apply: the bundle carries it),
      then *Save and publish*. The warning *"This App Bundle contains native code, and you've not
      uploaded debug symbols"* is expected and harmless: the only native code is in two AndroidX
      libraries (`androidx.graphics.path`, DataStore), which Google ships already stripped, so there
      are no symbols to send (`ndk.debugSymbolLevel` finds nothing to extract; checked 2026-10-22).

**3. Check it on a phone**
- [ ] On the Xiaomi (signed in with an internal tester's account), open Twangle's page in the Play
      Store through the internal testing link and update. It may take a few minutes to show up.
- [ ] About shows the new version. The tuner hears a string, the widget works, a language switch works,
      and whatever this release fixed is fixed.
- [ ] Tell Claude it passed, or what is wrong. Nothing is promoted before.

**4. Watch the rollout**
- [ ] A promotion to the closed test made as a draft: start it in *Closed testing* as in step 2.
- [ ] After the promotion to production Google reviews it (a few hours to a few days); Play then
      updates phones by itself, starting with 20 % of people.
- [ ] After two or three quiet days (*Quality → Android vitals → Crashes and ANRs*, and the reviews;
      Claude shows you `health` and `reviews` too, or the daily rollout watch if you said yes to it),
      tell Claude to raise it to 50 %, a few days later to 100 %.
- [ ] Something wrong: tell Claude to halt it. Nobody new gets the version; the fix goes on `main`
      and out in the next release (a halted version can't be uploaded again, the fix gets a higher
      version).

**Only in the Play Console** (the Google Play API can't do these)
- [ ] The first time: the *App content* forms (privacy policy, ads, app access, content rating, target
      audience, data safety) and *Store settings* (category, tags), Phase 53.
- [ ] The closed test's testers (their emails or a Google Group) and its 14 days in a row, then
      applying for production access.
- [ ] Play's pre-launch report after an upload, with Claude reading along if you like.
- [ ] *Users and permissions*: the testers and the robot user's rights.
- [ ] With the update that brings the watch app: the Wear OS form factor, its screenshots and the
      opt-in to Wear OS (*The watch app* above).

**By hand, without the key** (a machine without `~/.twangle-keys/play-service-account.json`:
`scripts/release.sh` stops and sends nothing). Easiest is to run the step on the machine that has
the key. Otherwise, in the Play Console:
- [ ] Upload: *Internal testing → Create new release*, drag in `release/Twangle-<version>.aab`, paste
      the whole of `release/Twangle-<version>-release-notes.txt` into *Release notes*, *Next*, *Save
      and publish*.
- [ ] Promote: in *Internal testing*, the release → *Promote release* → *Closed testing* or
      *Production*; for production set the rollout to **20 %**, then *Send for review*.
- [ ] Rollout: *Production → Manage rollout* to raise it, or *Halt rollout*.
- [ ] Store listing: *Grow users → Store presence → Main store listing*, each language's texts and
      pictures from `fastlane/metadata/android/` ([store-listing.md](store-listing.md)).

**Never:** upload a bundle that wasn't made by `scripts/release.sh bundle`, or share the upload key.
A lost upload key can be reset through Play support, since Google holds the app signing key.
