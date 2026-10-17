# Twangle — Roadmap

Status legend: ☐ todo · ◐ in progress · ☑ done

Each phase ends in a state that builds and can be demoed. Phases are worked in the order they are listed here, not by number. See [ARCHITECTURE.md](ARCHITECTURE.md) and its topic files for design details.

Phases 0–22 and 24–36 are finished and moved to [ROADMAP-ARCHIVE.md](ROADMAP-ARCHIVE.md), with the overview of the music tools that went into v1.0; Phase 23, the release, is the only one left. Move a phase there once all its items are ☑, and finished owner items too, so only open ones stay below. Ideas not planned yet live in [IDEAS.md](IDEAS.md).

---

## Waiting on the owner
Things only the owner can start. Claude mentions these at the start of a session while they're open.
- ◐ **Find 12+ testers for the closed test (Phase 23):** Google requires new personal developer accounts to run a closed test with at least 12 testers who stay opted in for 14 days in a row before the app can go public. Gather their Google account emails (or a Google Group) early (owner, 2026-10-16: gathering them through their personal network). No hurry: the closed test starts once the developer account and the internal track are ready

## Owner checks (live tests)
Done by the owner by hand, with a real guitar or headset. They are not roadmap increments: `/roadmap-step` skips them.

- ☐ Phase 5: with a real Bluetooth headset connected, the tuner still listens through the phone's mic and reads a string correctly (postponed 2026-10-16: the owner has no Bluetooth headset yet)
- ☐ Phase 15: with a real bass (or a 7-string guitar or cello), check the lowest strings read correctly (B0, E1, B1, C2), and that the tuner shows no note while the amp only hums (postponed 2026-10-16: the owner has no other instrument yet)
- ☐ Phase 20: when you have a Wear OS watch, install Twangle on it and tune a real guitar with it: the note and cents follow the string, the ring glows softly when a string is in tune, and when the watch screen goes dark (or dims to always-on) the microphone icon disappears (postponed 2026-10-16: the owner has no Wear OS watch yet, and the watch app follows in an update, decision 3)
- ☐ Phase 36: with auto-rotate off and the phone upright, turn on Settings → Focus mode: the whole app turns on its side by itself. Play a string and read the big meter from a few steps away; the tabs and the Instrument item stay at the side. Switch to another tab and back: the screen doesn't turn. Turn the phone the other way round: the app follows. Then switch Focus mode off: the app turns back upright and stays upright

---

## Phase 23 — Go live on the Play Store → **v1.0**
Goal: Twangle is public on Google Play. Needs the owner's Play developer account. Plan at least 3 weeks of calendar time: Google's closed test alone takes 14 days. The music tools are live in every build (Phase 34), so every step below can start.
- ☑ The look is final: run `scripts/ui-tests.sh --all --thorough`, fix what it finds, and make those double checks part of every run again (every style, and the `@Thorough` tests; until then a normal run leaves them out so restyling stays quick) (2026-10-16: `--all --thorough` passed after the fixes above; the double checks run in every overnight run; owner, 2026-10-16: normal work keeps the quick runs, so they are not part of every run)
- ☑ Style backgrounds reworked to abstract patterns, picked by the owner from [docs/design/style-backgrounds-preview.html](docs/design/style-backgrounds-preview.html): Ancient rock layers, Retro supergraphic, Contemporary silk lines, Futuristic glowing grid fading away (no horizon line), Space Age halftone; Classic unchanged
- ☑ Ancient's lettering calmer: Forum (antique Roman letters, with Polish, Turkish and Russian) instead of Caesar Dressing, picked by the owner from [docs/design/ancient-font-preview.html](docs/design/ancient-font-preview.html)
- ☐ Baseline Profile regenerated now that the look is final and the tools are live (`./gradlew :app:generateBaselineProfile`; the current one predates the metronome and the navigation bar), with the metronome (or Practice), settings and the live tools' screens added to the generator
- ☐ Developer account ready (owner, with Claude's help): identity and Android-device verification, and a public developer contact email (**decision 5**)
- ☑ Privacy policy brought up to date (every stored setting: instrument, own tunings, metronome, app language, landscape layout; the tour instead of the string-lock tip; the remembered microphone refusal, on the phone and in the watch's own small file, so "stores no settings" no longer holds for the watch; the wave's "when in tune" choice is gone; also the metronome's Listen and the widget) in English and Dutch, and published at a public URL (**decision 5**, owner 2026-10-16: a small separate public repo just for the policy, the code stays private until after release): https://klaboukes.github.io/twangle-privacy/, kept up to date with `scripts/publish-privacy.sh`
- ☑ The contact email (owner, **decision 5**: twangle.tuner@proton.me) in the policy's *Contact* section (English and Dutch, then `scripts/publish-privacy.sh`)
- ☐ The policy URL and the contact email entered in the Play Console (once the developer account is ready)
- ☐ Play Console *App content*: privacy policy, ads (none), app access (no login), content rating questionnaire, target audience (13+), data safety (no data collected or shared), category Music & Audio
- ☐ Play Store listing texts as planned in [docs/store-listing.md](docs/store-listing.md): title "Twangle: Guitar Tuner", short and full description (strong points first: accurate, many instruments, metronome, the music tools that went live in Phase 34 (Practice with chords, scales and the Capo helper; chord name, pitch trail and the 12th-fret check), made for everyone with TalkBack and large text, no ads / account / tracking / internet), translated into every supported language; the owner picks one of the two short descriptions
- ☐ Store screenshots made by an on-device test, not by hand: a fixed sound through the fake recorder (a note, a strummed chord, a held note with vibrato), the eight screens and styles of the plan (the chord name, Chords with the Capo helper as a two-phone picture, and the pitch trail in focus mode among them), placed on a 1080×1920 canvas with a translated caption (the Xiaomi's 20:9 screen is too long for Play as it is), in every language; run in the overnight run
- ☐ Store graphics: the 512×512 icon and the 1024×500 feature graphic (icon, name and an in-tune meter, no other words); no "free" or "no ads" in any picture or the title (Google's rules)
- ☐ A calm "Rate Twangle" row in About that opens the Play page (no rating pop-up), in every language
- ☐ Store settings: category Music & Audio, up to five tags, contact email and website (**decision 5**); after the internal-track upload, read Play's pre-launch report, its accessibility findings included
- ☑ Watch app as decided (**decision 3**): if it launches now, a real-watch check, the Wear OS form factor opted in, Wear screenshots and its own track; otherwise it follows in an update (owner, 2026-10-16: it follows in an update after v1.0; v1.0 ships the phone app only)
- ☐ Internal track: install the Play-signed build on the Xiaomi from Play and check the mic, widget and language switch
- ☐ Closed track with 12+ testers for 14 days in a row (see *Waiting on the owner*), collecting their feedback
- ☐ Apply for production access, then a staged rollout to production
