# Featuring nomination for Google Play

Google's featuring nomination is how a developer asks Google Play's editors to consider an app for a spot in the store: a collection, a banner, a "new and updated" list. Nothing is promised, and Google doesn't say why it picks an app or not. Claude wrote the answers below. **The owner** sends the form under their own name (ROADMAP.md, Phases 50 and 53).

The form was checked on 2026-10-29: https://support.google.com/googleplay/contact/featuring_review?hl=en. It is a Google Play Help contact form, not a page inside Play Console. Open it while signed in with the Google account that owns the developer account. It sets no character limits, so the texts are kept short. Read the live form before sending, because Google changes it now and then. Where a question below no longer matches, the facts in this file still hold.

## When to send

The form's rules: send it **at least 8 weeks before the date you want to be featured, for a new app** (3 weeks for other requests). The app has to be on a Play track already (it is: the closed track), and it has to launch on production within 4 months of that date. Apps need a rating of 3.0 or more. A new app with no public ratings yet can still be sent as a new launch.

- **After launch, once Twangle has a track record** (owner, 2026-10-29): not at launch and not for Christmas week. A brand-new app with no ratings, installs or reviews is a long shot for Google's editors, so the owner sends it when Twangle is public on Play and has its first few hundred ratings (Phase 56). By then it is no longer a new launch: answer **No – Title update**, send it at least 3 weeks before the date asked for, and check the answers below (written for a new launch) against the form and the app as they are then.
- **Christmas week was let go:** it meant sending by 29 October 2026 with no public ratings yet; the owner chose not to.
- **Again with the watch app:** the update that brings the Wear OS watch app (decision 3, Phase 56) is a *major update* with a new device, so it gets its own nomination at least 3 weeks before it goes out. Its answers are under *The watch-app update* below.

## The form, field by field (new launch)

### Category and eligibility
| Field | Answer |
|---|---|
| App, Game, or Family? | **App** |
| Does your app meet all of the above minimum requirements? | **Yes**, but see the note under this table |
| Please confirm that this is a new launch | **Yes – New launch** |

The ten requirements, and how Twangle meets them:
1. *Policy, launch checklist, design and core app quality guidelines:* yes. The App content declarations are all accepted (2026-10-22), and the app has no ads, no account and no data collection.
2. *Simultaneously launch across key mobile platforms* and 6. *Platform parity in design and features:* Twangle exists only for Android (phone and tablet, the watch to follow). No other platform's version exists, so none launches first and none has more features. The owner decides if that counts as meeting these two items. If not, the honest answer to the question above is "No", and the form then says the app can't be considered.
3. *Rating of 3.0 or higher:* there are no public ratings before launch. The closed test keeps going.
4. *Launch within 4 months of the featuring date:* yes, with the dates above.
5. *Device support:* phones, tablets (a layout of its own, checked on the Lenovo Tab M11); the watch follows in an update.
7. *Localised where it would be featured:* 25 languages, store listing included.
8. *Material Design UI:* yes, Material 3 with Twangle's own styles.
9. *(Recommended) alpha/beta first:* yes, the closed test since 2026-10-22.
10. *(Recommended) send 8 weeks ahead:* see *When to send*.

### Company info
| Field | Answer |
|---|---|
| Company Name | The developer name Play shows on the listing (a personal account, no company) |
| Contact Name | The owner's name |
| Contact email | twangle.tuner@proton.me (the public developer address in the store listing) |
| Additional contact emails | (empty) |
| Google point of contact email | (empty) |
| Preferred Language | English |

### Title info
| Field | Answer |
|---|---|
| App Name | Twangle: Guitar Tuner |
| Package ID | io.github.klaboukes.twangle |
| Channel for review | **Alpha** (the closed track is called `alpha`), or **Production** once it is live there |
| Version Code | The version on that track: `scripts/release.sh status` shows it (1.0.2 is 10002, 1.1.0 is 10100) |

### Featuring request info
| Field | Answer |
|---|---|
| Desired feature date | **24 December 2026** if sent by 29 October; otherwise at least 8 weeks after the day it is sent |
| Devices | **Phone**, **Tablet** (not Wear yet; not Chrome OS, which was never checked) |
| Target regions | **All regions** (Japan and Korea included: both languages are in the app and the listing; tick *Confirmed*) |
| Supported languages | English, Arabic, Dutch, Finnish, French, German, Hindi, Indonesian Bahasa, Italian, Japanese, Korean, Norwegian, Portuguese / Brazilian Portuguese, Russian, Simplified Chinese, Spanish / LatAm Spanish, Swedish, Thai, Traditional Chinese, Turkish, Vietnamese; **Other:** Bengali, Danish, Polish, Ukrainian |
| Is this a new launch, major update or minor update? | **New launch** |

### New launch details
**Launch Date and time:** the day of the production rollout, e.g. "16 November 2026, 10:00 CET" (the real date once production access is granted).

**Will you launch on all major platforms simultaneously?** **No: Android-First.** It is the closest true answer: Twangle is made for Android only, and no version for another platform is planned.

**Why featuring is important now:**
> Many guitars are given at Christmas, and in Google Trends the week of 24–28 December is the busiest of the year for tuner searches in nearly every country we checked. Twangle launches in November as a free tuner with no ads, no account and no internet permission, in 25 languages. It has no advertising budget and never will (it is free for good, with nothing to sell), so being seen in the store when new players look for a tuner is how it reaches them.

**Of the targeted markets, are there any being prioritized with additional marketing campaigns or events?** **No.**

**Provide context for these markets or regions:**
> No paid campaigns anywhere. The store listing, screenshots and app are in 25 languages, so every region gets the same app in its own language.

**Would you like to provide more info on your featuring request?** **Yes.**

**Pitch materials** (a link is required; Google Drive preferred): a Google Doc made from *The pitch* below, with store screenshots 1 (in tune), 4 (TalkBack) and 5 (chord name) pasted in (`fastlane/metadata/android/en-US/images/phoneScreenshots/` on the PC that last ran `scripts/store-screenshots.sh`; they are not in git), shared as "Anyone with the link can view". Put the website's home page, https://klaboukes.github.io/, as a second link with *Add additional field*.

### Soft launch metrics
Twangle measures nothing in the app (no analytics, hard rule 9), and the closed test is too small to give Play Console's own figures any meaning.

| Field | Answer |
|---|---|
| Retention D1, D7, and D30 | "Not measured: the app has no analytics. Closed test on Google Play since 22 October 2026." |
| ARPDAU | **0** (no ads, no purchases, no subscription) |
| Soft Launch Countries | The closed testers' countries, two-letter codes (e.g. NL). There was no soft launch beyond the closed test |

### Launch details
**What is your marketing/user acquisition plan?**
> No paid advertising; the budget is zero. The store listing in 25 languages, a website with a help page for every tool, and honest posts by the developer in guitar, ukulele and Android communities. Twangle will also be offered to app directories for blind and low-vision Android users, because it works with TalkBack.

**What is your main monetization model?** **Other:** "None. Every feature is free for good: no ads, no in-app purchases, no subscription."

**Please tell us more why your launch could succeed:** the pitch below.

## The pitch

> Twangle is a guitar tuner in which every feature is free, with no ads, no account, no in-app purchases and no tracking. It doesn't even have the internet permission: the microphone's sound is analysed on the phone and never recorded or sent, and its Data safety section says no data is collected or shared. It tunes guitar (6, 7, 8 and 12 strings), bass, ukulele, the 5-string banjo, violin, viola, cello and mandolin, precise to a cent, and it listens to more than one string: the strum check shows which strings of a strum are out of tune, the chord name names the chord you play, the pitch trail shows how steady a held note or a bend is, and the 12th-fret check tells whether a saddle should move. A Practice tab adds chord charts, scales with a drone, a capo helper, a metronome with tap tempo, and ear training that listens to the player's answer on the instrument. It speaks 25 languages, among them Arabic (right to left), Bengali, Thai, Vietnamese and Traditional Chinese, and it works with TalkBack, which says the note and how far off it is, and with text at twice the normal size. Twangle is made by one person who wanted a tuner like this.

For the pitch document, below that paragraph, a few short lines:
- **Private by design:** no internet permission at all; Data safety: no data collected, no data shared.
- **Free for good:** no ads, no purchases, no subscription, no account.
- **For everyone:** 25 languages, right to left included; TalkBack says the note, the cents and which way to turn the peg; every screen works at 2× text.
- **Listens like a teacher:** strum check, chord name, pitch trail, 12th-fret check.
- **Practice in the same app:** chord charts and scales for the instrument and tuning picked (left-handed too), capo helper, metronome, ear training.
- **Instruments:** guitar (6, 7, 8 and 12 strings), bass, ukulele, 5-string banjo, violin, viola, cello, mandolin, and a chromatic tuner for anything else.

The text names no other app.

## The watch-app update

Send this when the update with the watch app is ready (decision 3, Phase 56), at least 3 weeks before it goes out, and check the facts against what that update brings.

| Field | Answer |
|---|---|
| Please confirm that this is a new launch | **No – Title update** (if the form then says the app can't be considered, the update can't be nominated this way) |
| Channel for review | The track that has the update |
| Devices | **Phone**, **Tablet**, **Wear** |
| Is this a new launch, major update or minor update? | **Major update** |

**Why featuring is important now:**
> This update adds a Wear OS app: a chromatic tuner that runs on the watch by itself, with the watch's own microphone and no phone needed. It is as private as the phone app: no internet permission, nothing recorded or sent. With the tuner on the wrist, a player can tune with both hands on the instrument.

**Markets prioritized:** **No.** **Context:** "No paid campaigns; the same app in 25 languages everywhere."
