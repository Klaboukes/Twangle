# Twangle on Google Play: the store listing

The plan for how Twangle is presented on Google Play: the texts, the pictures, and how people find it. Phase 38 of the [roadmap](../ROADMAP.md) builds it, and Phase 23 puts it on Play. The English texts are the source; every language in `twangle.languages` has its own translation (see *The texts, ready to paste*).

## What makes Twangle stand out
Most tuners in the store are free to download but show ads, sell a subscription, ask for an account or track their users. Twangle's strong points, in the order the listing tells them:

1. **It tunes well:** a clear needle, within ±1 cent, from a 5-string bass's low B to the top frets, with "Tune up" / "Tune down" and a reference tone to tune by ear.
2. **Many instruments:** guitar (6, 7, 8 strings), bass, ukulele, violin, viola, cello, mandolin, chromatic for everything else, and your own tunings.
3. **Practice in the same app:** a metronome, chord charts and scales on the fretboard for your instrument and tuning (your own tunings included), and a Capo helper that works out where the capo goes and what each shape sounds.
4. **Tools that listen with the tuner:** strum check (every string at once), chord name (strum a chord and see its name), pitch trail (a line that shows how steady a held note, a bend or vibrato is) and the 12th-fret check (does the guitar play in tune up the neck).
5. **Made for everyone:** TalkBack speaks the note and how far off it is, every control works at 2× text, a vibration when in tune, 16 languages including Arabic (right to left). Few tuners can say this, and blind and low-vision musicians look for exactly this.
6. **Nothing to pay, nothing to sign, nothing leaves the phone:** no ads, no account, no purchases, no tracking, and no internet permission at all. Play's *Data safety* section will show "No data collected" and "No data shared", which backs this up.
7. **Yours to style:** six styles, six meter looks, light and dark, and focus mode for one big meter on a phone on its side.
8. **A home-screen widget.** The Wear OS watch app follows in an update after v1.0 (owner, 2026-10-16, **decision 3**), so v1.0's listing leaves it out.

## Google's rules that shape the listing
Checked against the Play Console when entering the listing, since Google changes them now and then.
- **No price or promotion words in the title, icon, developer name, feature graphic or screenshots:** no "free", "no ads", "best", "#1", "download now". Those claims go in the short and full description only, stated as plain facts.
- **No other apps named** anywhere in the listing, and no keyword lists: search words appear in normal sentences.
- **Lengths:** title 30 characters, short description 80, full description 4000.
- **Screenshots:** 2 to 8 per device type, the long side at most twice the short side. The Xiaomi 12X's screen is 1080 × 2400 (2.2 : 1), too long as it is, so screenshots are placed on a 1080 × 1920 canvas with a caption above (see below). At least 4 screenshots of 1080 px or more make the app eligible for Play's recommendations.
- **Icon** 512 × 512 PNG; **feature graphic** 1024 × 500 PNG or JPEG, shown behind the promo video or at the top of the listing on some devices, so nothing important near its edges.

## Title and texts

**App name in the store (30):** `Twangle: Guitar Tuner` (21). "Guitar tuner" is by far the most searched phrase in this category and the title weighs most in Play search. The launcher label stays "Twangle". In other languages the part after the colon is translated ("Twangle: Gitaarstemmer", "Twangle: Gitarrenstimmgerät"), as long as it fits in 30.

**Short description (80):** shown under the title, and the second strongest place for search words. The owner picked this one (2026-10-17):
> Accurate tuner for guitar, bass, ukulele and violin, with a metronome. No ads.

(78 characters.) The alternative, which leads with accessibility, stays a candidate for a Play *store listing experiment* after launch (see *Being found*):
> Guitar tuner and metronome that speaks with TalkBack. No ads, no account.

(73 characters.)

**Full description (4000):** plain text, short paragraphs, a few bullet lines; no emoji rows. It opens with what Twangle does and that nothing is paid, signed or sent, then six capitalised sections in the order of the strong points above: tune any instrument, listening tools, practice, made for everyone, make it yours, also. It closes with "works fully offline and will stay free". The English text is [fastlane/metadata/android/en-US/full_description.txt](../fastlane/metadata/android/en-US/full_description.txt) (about 3,000 characters); the longest translation, German, is about 3,600. The Wear OS line comes back with the update that brings the watch app (**decision 3**). The words people search for (guitar tuner, bass tuner, ukulele tuner, violin tuner, cello tuner, chromatic tuner, metronome, chord chart, chord name, scales, capo, Drop D, strobe) each appear in a real sentence; none is repeated just for search.

### The texts, ready to paste
Every language's title, short and full description are plain-text files in `fastlane/metadata/android/<Play locale>/` (`title.txt`, `short_description.txt`, `full_description.txt`): the layout fastlane and the Gradle Play Publisher read, and F-Droid too once the repo is public. For now the owner pastes them into the Play Console by hand (Phase 23), one language at a time; the trailing newline doesn't count.
- **Folders** use Play's locale codes, derived from the Android qualifier in `twangle.languages`: `pt-rBR` → `pt-BR`, `zh-rCN` → `zh-CN`, `in` → `id`, and a plain language takes Play's folder for it with its region (`en` → `en-US`, `nl` → `nl-NL`, `de` → `de-DE`, `fr` → `fr-FR`, `es` → `es-ES`, `it` → `it-IT`, `ja` → `ja-JP`, `ko` → `ko-KR`, `hi` → `hi-IN`, `ru` → `ru-RU`, `tr` → `tr-TR`, `pl` → `pl-PL`), or without one where Play has none (`ar`, `id`).
- **Words** follow each language's app texts (`res/values-<language>/strings.xml`): the tab, tool and style names, "Tune up"/"Tune down", the register (du, tu, vous, ты …), and the TalkBack example is what Twangle says in that language, with its note names (Mi 2 in the Do Re Mi languages). In the title only the part after the colon is translated; French puts a no-break space before the colon, like the app.
- **Guard:** `StoreListingTest` (JVM, `:app`, part of `./gradlew test`) fails when a language in `twangle.languages` has no folder (or two), a folder is for a language that isn't listed, a file is missing or empty, a text is over Play's limit (counted in Unicode code points), the title doesn't start with "Twangle" or has "free", "no ads", "best" or "#1", or a text has emoji. Adding a language therefore needs its store texts too.

## Pictures

### Icon and feature graphic
- **Icon:** the launcher icon (*twang to tune*, PR #206) at 512 × 512, full square: Google adds the round corners and shadow itself.
- **Feature graphic:** the Twangle icon and name on the left, on the right one style's meter showing a note in tune with its calm glow, on that style's background. No other text. One version for every language (it has no words to translate besides the name).

### Phone screenshots
Eight screenshots, the first three the strongest, because search results show only those. Each one is the app on a 1080 × 1920 canvas in the style's background colour, with a short caption above it in large type. Captions say what the person gets, not what the button is called. The six styles are spread over the set so it shows the range, with the plain tuner screens in the calmest styles. The tools that went live in v1.0 (Phase 34) get three of the eight: the chord name, Chords with the Capo helper, and the pitch trail (owner, 2026-10-17).

| # | Screen | Style, theme | Caption |
|---|---|---|---|
| 1 | Tuner, guitar standard, A string in tune with the glow | Contemporary, light | Play a string. See it in tune. |
| 2 | Tuner, low E 12 cents flat, "Tune up" | Classic, dark | Precise to a single cent |
| 3 | Instrument and tuning panel open | Retro, light | Guitar, bass, ukulele, violin and more |
| 4 | Tuner at 2× text with a TalkBack-style speech bubble "E 2, 12 cents flat, tune up" | Contemporary, dark | Speaks with TalkBack. Works with large text. |
| 5 | Tuner in Chord name mode after a strummed A minor: the big "Am" with "A minor" under it, its notes with the bass filled, the last chords below | Futuristic, dark | Strum a chord. See its name. |
| 6 | Metronome at 120 BPM in 4/4, beat 1 lit | Space Age, dark | A metronome that keeps steady time |
| 7 | Two phones side by side: Practice → Chords with a C major chart (guitar, standard), and Practice → Capo helper with a song in A played with G shapes (capo on fret 2) | Ancient, light | Chord charts and the capo, worked out for you |
| 8 | Tuner in focus mode with the pitch trail (focus mode switched on in Settings; the phone on its side, the trail in the big meter's place drawing a held A with a little vibrato, landscape, cropped to fit) | Classic, light | See every bend and vibrato. Your sound never leaves your phone. |

- The screenshots are made by an on-device test, not by hand: a store-screenshot test feeds the tuner a fixed sound through the fake recorder (a note; a strummed A minor for screenshot 5; a held A with a gentle vibrato for screenshot 8), opens each screen in the chosen style and saves the picture, and a script places it on the canvas with the caption in each language. For screenshot 8 the test picks the pitch trail and switches focus mode on first, since the phone on its side shows the full layout by default. For screenshot 7 the test saves the Chords and Capo helper screens separately, and the script places the two side by side on one canvas, each phone at about half width, as a two-phone picture. That way all 16 languages get their own screenshots, and they're easy to make again after a change. It runs in the overnight run.
- The speech bubble in screenshot 4 is drawn by the script, not real TalkBack, since screenshots can't show speech.

### Watch screenshots (with the update that brings the watch app, **decision 3**)
Two or three square screenshots (at least 384 × 384), the watch's round screen only, no watch frame: a note in tune with the glowing ring, a note flat, and the microphone prompt.

### Promo video
None for v1.0. Maybe later: a 30-second screen recording of tuning a real guitar, with the sound.

## Being found
- **Category:** Music & Audio.
- **Tags:** in the Play Console (*Store settings*), up to five of Google's tags: the ones closest to tuner, metronome and musical instruments.
- **Contact details:** the public contact email (**decision 5**) and the privacy-policy page as website.
- **Ratings:** good ratings early help search more than anything else. Ask the closed-test testers to rate it once it is public. In Twangle, a calm "Rate Twangle" row in About that opens the Play page; no pop-up asking for a rating.
- **Translations:** every language's title, short and full description and captions, translated by Claude and checked by a native speaker where possible (the Phase 22 owner check covers this).
- **Accessibility communities:** once live, submit Twangle to the app directories and forums of blind and low-vision Android users (for example *Accessible Android*), where a tuner that speaks is rare. Ask the closed-test testers whether one of them uses TalkBack.
- **After launch:** Play's *store listing experiments* can compare the chosen short description with the TalkBack one or the first screenshot; *custom store listings* can give e.g. bass players their own listing later.
- **"What's new":** short release notes in every language with each update, in the same plain style.
