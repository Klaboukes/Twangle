# F-Droid, IzzyOnDroid and Exodus

**Parked (owner, 2026-11-12):** F-Droid is not being sent for now. Its forum and Hacker News are wary
of AI-written apps, and it would bring few players. It waits in [IDEAS.md](../../IDEAS.md), *F-Droid
and Show HN*, with its condition. Everything below stays valid for when it is picked up. The Exodus
report doesn't depend on it.

Phase 92, *After the code is open*. F-Droid is the app store for open-source Android apps. Its
users want apps with no ads and no tracking, so Twangle fits there. F-Droid builds the app itself
from the source code, signs it with its own key and checks it for non-free code. A new app gets in
through a merge request on F-Droid's GitLab that adds one recipe file. After that, F-Droid finds each
new version by itself from the `v*` tags.

## The recipe

[`fdroid/io.github.klaboukes.twangle.yml`](fdroid/io.github.klaboukes.twangle.yml) goes into
`metadata/` of [fdroiddata](https://gitlab.com/fdroid/fdroiddata). It builds the phone app's free
build (`twangle.foss=true`: without Google Play's rating library, lifecycle.md, *Google's rating
window*) from the commit of `v2.1.1`.
- **`prebuild`** removes the foojay plugin from `settings.gradle.kts`. That plugin downloads a JDK by
  itself, and F-Droid's scanner refuses it. In its place, **`sudo`** installs JDK 17 on F-Droid's
  build server. If the server's Debian no longer has `openjdk-17-jdk-headless`, the reviewers will
  say which JDK to use. Twangle's toolchain is 17, so a newer JDK works only through the toolchain
  setting in the root `build.gradle.kts`.
- **Updates:** `UpdateCheckMode: Tags` with `UpdateCheckData` reads `twangle.versionCode` and
  `twangle.version` from `gradle.properties` at each new `v*` tag. The version code is written out
  there because F-Droid's checker can only read a number. `scripts/release.sh bump` sets both lines,
  and the build stops when they disagree. The first such tag is the one after 2.1.1: 2.1.1 itself is
  the recipe's one build, given by hand.
- **Only the phone app.** The watch app has the same app ID and is not built for F-Droid. The
  watch app stays a Google Play app, since watch owners install their apps through Play.
- **Metadata:** F-Droid reads the title, the descriptions, the release notes, the icon and the
  feature graphics from `fastlane/metadata/android/` in every language. The phone screenshots aren't
  committed (git-ignored, store-listing.md *Phone screenshots*), so F-Droid's page has none at first.
  Committing the English ones (about 2 MB) needs a change to how `store-screenshots.sh` and
  `release.sh listing` treat that folder. The listing refuses uncommitted changes there, so this is
  left for a later step.
- **No Anti-Features:** no ads, no tracking, no internet, no non-free code in the free build. The
  About row's link to Google Play's page is a link, not a library.

### Checked (2026-11-12)
- F-Droid's own scanner (fdroidserver 2.4, `scan_source`) on the `v2.1.1` source: one problem, the
  foojay plugin, which the `prebuild` removes. Nothing else. The scanner passes over the rating
  library's line in `app/build.gradle.kts` (`if (!freeBuild) implementation(...)`). If a reviewer
  asks about it, the answer is the property and the build's guard against Google libraries.
- The free release build from that source with the `prebuild` applied and JDK downloads turned off:
  `BUILD SUCCESSFUL`, a 3.9 MB APK. F-Droid's binary scan of that APK found no problem among its
  456 classes, none of them Google's.
- `fdroid lint`'s checks and `rewritemeta`'s format on the recipe: clean. The categories, *Music
  Practice Tool* and *Multimedia*, are from fdroiddata's `config/categories.yml`.
- `UpdateCheckData`'s two patterns against `gradle.properties` give `20101` and `2.1.1`.

## The owner's steps

1. **A GitLab account** at gitlab.com (free), if you don't have one. Use the Klaboukes name, as on
   GitHub.
2. **Fork** [fdroiddata](https://gitlab.com/fdroid/fdroiddata) (the *Fork* button). In your fork,
   add the file `metadata/io.github.klaboukes.twangle.yml` with the recipe's text (*+ → New file*
   in GitLab's web page is enough), on a new branch named `twangle`. The commit message is
   `New app: Twangle`.
3. **Open a merge request** into fdroiddata's `master` and pick the *App inclusion* template. Tick
   its boxes, and under them write the request in your own words, from the facts below. The merge
   request's pipeline builds Twangle the way F-Droid will. If it fails, paste me its log.
4. **Answer the reviewers yourself**, in your own words. They may ask for small changes to the
   recipe. Bring me their questions and I'll explain what's behind them, but the replies come from
   you. Once merged, Twangle shows up in the F-Droid app within a few days.
5. **Then wait, and don't chase it elsewhere.** F-Droid is short of testers, and a merge request stays
   open until someone has tried the app, so expect weeks or months. Don't post about Twangle on the
   F-Droid forum while it waits, or after: the forum is not for advertising apps, and its moderators
   say so plainly. Everything about the request is said in the merge request.

If the merge request is too much work, the simpler route is an issue on
[F-Droid's request list](https://gitlab.com/fdroid/rfp/-/issues) linking the recipe. It's slower:
a volunteer has to pick it up.

### What the merge request says
F-Droid's forum notice (seen 2026-11-12) asks people to use "your own knowledge and skill, rather than
relying on AI for every single thing", and reviewers notice AI-written text. So the owner writes the
request themselves, short and plain, and these are only the facts it should hold:
- what Twangle is, and that it has no ads, no account, no tracking and no internet permission
- that the owner is the developer
- the free build, `twangle.foss=true`, which leaves out the Play build's one non-free library
  (Google Play's In-App Review), and the build's check that stops it if a Google Play, Play services
  or Firebase library comes in another way
- that the `prebuild` removes the foojay toolchain plugin, and that fdroidserver's scanner finds
  nothing else in the source or the built APK
- how it's tested, to help a tester: the owner tests every change on real phones (a Xiaomi and a
  Samsung) and a tablet, with a guitar; what to try is tuning a string, the metronome and a chord chart
- how it's made: the code is written with Claude (Anthropic's AI), to the owner's design and
  decisions, reviewed and tested by them; the rules that work follows are in CLAUDE.md

Saying how it's made stays even though the forum is cool towards AI: a reviewer who found CLAUDE.md
after a request that didn't mention it would trust the app less than one who was told.

For reference, the earlier draft in full:
> Twangle is a guitar tuner (chromatic and by string, many instruments and tunings) with a
> metronome, chord charts, scales, a capo helper and ear training. No ads, no account, no tracking,
> and no INTERNET permission. GPL-3.0-only.
>
> I'm the developer. The F-Droid build uses the property `twangle.foss=true`, which leaves out the
> one non-free library of the Play build (Google Play's In-App Review). A check in the build stops
> it if any Google Play, Play services or Firebase library comes in another way. The `prebuild`
> removes the foojay toolchain plugin. Locally, fdroidserver's scanner finds nothing else, and the
> built APK holds no Google classes.
>
> To be open about how it's made: the code is written with Claude (Anthropic's AI), to my design
> and decisions. I review every change and test it on real phones with a real guitar. The rules
> that work follows are in the repository's CLAUDE.md.

## IzzyOnDroid: not possible

[IzzyOnDroid's inclusion policy](https://izzyondroid.org/docs/general/AppInclusionPolicy/)
(checked 2026-11-12) rejects apps whose code is written by generative AI: "Vibe-coded apps will be
rejected." AI may write documentation, but not the code. Twangle's code is written by Claude, so it
doesn't qualify, and applying without saying so would break their rules. The owner chose (2026-11-12)
to skip IzzyOnDroid and to say openly in the README and the F-Droid request how the code is made.
F-Droid had no such rule on that date, but its community was discussing one. If F-Droid adopts one,
Twangle may have to leave; Google Play is unaffected either way.

## Exodus Privacy

[Exodus Privacy](https://reports.exodus-privacy.eu.org/) analyses an app from Google Play and lists
its trackers and permissions. Twangle's report should show zero trackers and one permission, the
microphone. Exodus had no report for Twangle on 2026-11-12. **The owner** asks for one:
*Analyze an app* on their site, the app ID `io.github.klaboukes.twangle`. The README already links
to the address the report will have
(`https://reports.exodus-privacy.eu.org/en/reports/io.github.klaboukes.twangle/latest/`), which
works once the analysis is done.

## Show HN

The draft is [show-hn.md](show-hn.md). **The owner** posts it whenever they like, with Google Play
alone: F-Droid's review can take months, so it doesn't wait for F-Droid.
