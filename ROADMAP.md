# Twangle — Roadmap

Status legend: ☐ todo · ◐ in progress · ☑ done

Each phase ends in a state that builds and can be demoed. Phases are worked in the order they are listed here, not by number. See [ARCHITECTURE.md](ARCHITECTURE.md) and its topic files for design details.

Phases 0–22, 24–38 and 40 are finished and moved to [ROADMAP-ARCHIVE.md](ROADMAP-ARCHIVE.md), with the overview of the music tools that went into v1.0. Left is the release: Phase 41 (uploads through the Google Play API), Phase 42 (a check on a real tablet), Phase 23 (the owner, in the Play Console), then Phase 39, opening the code. Move a phase there once all its items are ☑, and finished owner items too, so only open ones stay below. Ideas not planned yet live in [IDEAS.md](IDEAS.md).

---

## Waiting on the owner
Things only the owner can start. Claude mentions these at the start of a session while they're open.
- ◐ **Find 12+ testers for the closed test (Phase 23):** Google requires new personal developer accounts to run a closed test with at least 12 testers who stay opted in for 14 days in a row before the app can go public. Gather their Google account emails (or a Google Group) early (owner, 2026-10-16: gathering them through their personal network). No hurry: the closed test starts once the developer account and the internal track are ready

- ☐ **A service account for the Google Play API (Phase 41):** a robot user that lets Claude's scripts upload to Play. Made once in Google Cloud and the Play Console, about 15 minutes, with Claude guiding in the browser

- ☐ **Plug in the Lenovo Tab M11 (Phase 42):** turn on its developer options and USB debugging, plug it into the PC and accept the "Allow USB debugging?" prompt, then tell Claude

## Owner checks (live tests)
Done by the owner by hand, with a real guitar or headset. They are not roadmap increments: `/roadmap-step` skips them.

- ☐ Phase 5: with a real Bluetooth headset connected, the tuner still listens through the phone's mic and reads a string correctly (postponed 2026-10-16: the owner has no Bluetooth headset yet)
- ☐ Phase 15: with a real bass (or a 7-string guitar or cello), check the lowest strings read correctly (B0, E1, B1, C2), and that the tuner shows no note while the amp only hums (postponed 2026-10-16: the owner has no other instrument yet)
- ☐ Phase 20: when you have a Wear OS watch, install Twangle on it and tune a real guitar with it: the note and cents follow the string, the ring glows softly when a string is in tune, and when the watch screen goes dark (or dims to always-on) the microphone icon disappears (postponed 2026-10-16: the owner has no Wear OS watch yet, and the watch app follows in an update, decision 3)
- ☐ Phases 36 and 40: with auto-rotate off and the phone upright, tap the four-corners button on the tuner, right above the tools button: the whole app turns on its side by itself and the tuner fills the whole screen (no tabs, no clock bar), with only the note small in the top left corner and the corners-pointing-in button in the top right. Play a string and read the meter from a few steps away. Tap the screen: the controls and the tabs come back for a few seconds; the button above the tools button now has its corners pointing out, and a tap on it brings the full-screen meter straight back. Switch to another tab and back: the screen doesn't turn, and the other tab shows its tabs and clock bar. Turn the phone the other way round: the app follows. Then tap the button in the top right corner (its corners pointing in): focus mode switches off and the app turns back upright and stays upright. Turn focus mode on again and swipe Back from the screen's edge (maybe twice): it switches off the same way, and the app stays open

---

## Phase 41 — Releases through the Google Play API
Goal: one command sends a release, the store texts and the pictures to Google Play, so the owner no longer drags files in or pastes 16 languages by hand. Owner, 2026-10-21: before Phase 23's store-listing step, which it then does; Phase 23's Play Console forms and the policy URL don't wait for it. It uses the Google Play Developer API through the Gradle Play Publisher plugin (Triple-T, version in the catalog) and runs on the PC only: the app itself doesn't change (no `INTERNET` permission).
- ☐ Owner, with Claude guiding in the browser: a Google Cloud project with the Google Play Android Developer API switched on, a service account and its JSON key, saved next to the upload key in `~/.twangle-keys/` (never in the repo); the service account invited in the Play Console (*Users and permissions*) for Twangle only, with permission to release and to edit the store listing
- ☑ Gradle Play Publisher in `:app`, reading the key from `~/.twangle-keys/` as the upload key is read; without it (CI, other machines) nothing changes and nothing fails. It takes the texts, release notes and pictures from `fastlane/metadata/android/`, still the one place they live (`StoreListingTest` keeps guarding them). Version 4.1.1 (4.x is the line for AGP 9); the key is `~/.twangle-keys/play-service-account.json`; `syncPlayResourcesFromFastlane` copies the fastlane files into the plugin's layout in `build/` at task time
- ☑ `scripts/release.sh upload`: the signed bundle and its notes in every language to the internal track; while Twangle is still a draft on Play, as a draft release the owner starts with one click. `upload completed` once the app is out of draft; checked up to Google's sign-in with a made-up key, the real upload waits for the key
- ☐ `scripts/release.sh promote closed|production` and `rollout <percent>|halt`: the same build to another track, a staged rollout raised step by step, or halted
- ☐ `scripts/release.sh listing`: title, short and full description, screenshots (`scripts/store-screenshots.sh`), icon and feature graphic for every language
- ☐ docs/release.md and the `/release` skill updated: Claude asks the owner before every upload, promotion and listing change, since each changes what people get or see; the owner still checks the internal build on the Xiaomi before it is promoted
- ☐ Tried for real: the store listing sent for every language, and a build on the internal track as a draft

## Phase 42 — Check on a real tablet (Lenovo Tab M11)
Goal: Twangle looks and works right on a real tablet before v1.0, since Play offers it to tablets too. Owner, 2026-10-21: the owner has a Lenovo Tab M11 and plugs it in later. The layouts already pick their arrangement from the space (`tunerLayoutFor`), and the UI tests already include a tablet-sized screen, so this checks the real thing: the tablet's own density, microphone, speaker and navigation. It starts once the tablet is plugged in (see *Waiting on the owner*); until then `/roadmap-step` skips it, and it doesn't hold up Phase 23.
- ☐ Twangle installed on the tablet; its details (adb serial, Android version, screen size in dp, refresh rate, navigation) and any install quirks noted in CLAUDE.md next to the two phones
- ☐ Every tab checked on the tablet, upright and on its side, in a few styles: the tuner (column upright, two panes on its side), focus mode full screen, the metronome, Practice and its tools, Settings and About, the tour, and the widget on the home screen
- ☐ The tuner reads a string through the tablet's microphone (a short test tone from the laptop), and the reference tones and metronome clicks sound through its speaker
- ☐ What looks or works wrong fixed, each fix on its own branch and PR, with a tablet-sized case added to the matching UI test where one is missing
- ☐ With the owner: decide whether the store listing gets 10-inch tablet screenshots (Play shows them to tablet users), and if so take them on the tablet with `scripts/store-screenshots.sh`

## Phase 23 — Go live on the Play Store → **v1.0**
Goal: Twangle is public on Google Play. Done by the owner in the Play Console, with Claude's help. Plan at least 3 weeks of calendar time: Google's closed test alone takes 14 days. The account and the Play Console forms can start any time; the tracks need Phase 37's build, and the store listing Phase 38's texts and pictures. The finished preparation (final look, privacy policy, contact email, watch decision) is in [ROADMAP-ARCHIVE.md](ROADMAP-ARCHIVE.md).
- ☑ Developer account ready (owner, with Claude's help): identity and Android-device verification, and a public developer contact email (**decision 5**). Owner, 2026-10-21: personal account, identity and device checks passed
- ☐ The policy URL and the contact email entered in the Play Console (once the developer account is ready)
- ☐ Play Console *App content*: privacy policy, ads (none), app access (no login), content rating questionnaire, target audience (13+), data safety (no data collected or shared), category Music & Audio
- ☐ Store listing in the Play Console: the texts, screenshots and graphics from Phase 38, sent with `scripts/release.sh listing` (Phase 41), category Music & Audio, up to five tags, contact email and website (**decision 5**); after the internal-track upload, read Play's pre-launch report, its accessibility findings included
- ☐ Internal track: install the Play-signed build on the Xiaomi from Play and check the mic, widget and language switch
- ☐ Closed track with 12+ testers for 14 days in a row (see *Waiting on the owner*), collecting their feedback
- ☐ Apply for production access, then a staged rollout to production

## Phase 39 — Open the code after v1.0
Goal: the Twangle repository goes public as open source, once v1.0 is live on Play. The licence is the **GPL version 3** (owner, 2026-10-17): anyone may copy, change and share the code, but what they build from it stays open under the same licence, so nobody can turn Twangle into a closed app with ads. The bundled fonts keep their own SIL Open Font License, and the AndroidX and Kotlin libraries' Apache 2.0 fits inside GPL 3.
- ☐ `LICENSE` with the full GPL 3 text, and a *Licence* section in README.md naming it
- ☐ README.md says the name "Twangle" and its icon are not covered by the licence, so copies must use their own name and icon
- ☐ Before opening: check the history for anything that shouldn't be public (the owner's personal email in old commits, `local.properties`, keys); the owner decides whether to keep the history or start the public repository fresh
- ☐ The owner makes the repository public; later commits use the GitHub no-reply email
- ☐ While the owner is the only author, the licence can still change; once others contribute, it can't without them, so settle any change before accepting outside code
