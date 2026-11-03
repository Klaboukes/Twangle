---
name: add-language
description: Add a new language to Twangle (phone app, watch app, widget) end to end — translate every text, list it, set its note-name default and TalkBack names, check fonts, right-to-left and digits, verify on the phone, and land it through a PR. Use when the owner says "add Swedish", "translate the app into Vietnamese", "/add-language <language>", or asks for another language.
argument-hint: "<language, e.g. Swedish or sv>"
---

# Add a language

One language (or a few closely related ones) per branch and PR. CLAUDE.md's hard rules, environment notes and Git workflow apply; [docs/architecture/languages.md](../../../docs/architecture/languages.md) is the design this follows. Read it before starting, because it holds the current language list, the register each language uses and the rules below in more detail.

The owner is an experienced software engineer, new to Android, who leaves the code to Claude. Tell them in plain language what changed and what to try.

## 0. Pre-flight
Same as the roadmap-step skill's step 0: your own worktree, clean tree, `git switch main && git pull`, no leftover open PR, the last nightly CI run on `main` passed (or GitHub didn't run it, see that step). Branch `chore/language-<code>`, or `phase-<N>/...` when a roadmap item asks for the language.

## 1. Pick the qualifier
Android resource qualifier form, which is also what `twangle.languages` uses: `sv`, `pt-rBR`, `zh-rTW`, `b+sr+Latn`. Old codes still apply: Indonesian is `in` (a phone set to `id` finds it), Hebrew `iw`, Yiddish `ji`. Add a region only when it really differs (Brazilian vs European Portuguese).

## 2. Translate every text
- The modules with texts are those with a `res/values/strings.xml`. Find them with a glob (today: `app`, `wear`, `feature/tuner`, `feature/settings`, `feature/metronome`, `feature/practice`, `core/designsystem`). Create `res/values-<code>/strings.xml` in each, with every translatable string and plural.
- Translate from the English source and use the existing languages as reference. Keep texts as short as the English. Use the words local guitarists and tuner apps use. Tips are short imperatives, "Pick a string" style (never "Tap a string").
- Pick one register (informal or formal you) as modern apps in that language do, keep it consistent, and add it to the register line in docs/architecture/languages.md.
- Leave placeholders exactly as in English. Give plurals every quantity Android defines for the language (e.g. `one/few/many/other` for Slavic languages, only `other` for Chinese, Japanese, Korean, Vietnamese). Skip `translatable="false"` items.
- Android escaping: `\'` for apostrophes, `\@` or `\?` at the start. Follow the local typography rules (French: a no-break space U+00A0 before `:` `?` `!` and inside « »).
- **Play Store texts:** add `fastlane/metadata/android/<Play locale>/` (the language's code in Play Console, e.g. `sv-SE`, `vi`) with `title.txt`, `short_description.txt`, `full_description.txt`, `screenshot_captions.txt` (the eight captions, one a line) and `changelogs/<versionCode>.txt` for every release note `en-US` has, translated from `en-US` in the app's words for the language (docs/store-listing.md, *The texts, ready to paste*). `StoreListingTest` checks the folder and Play's limits (title 30, short 80, full 4000, release notes 500). `scripts/release.sh listing` sends nothing until the new language also has its store screenshots (`scripts/store-screenshots.sh <code>` on the phone, and on the tablet too when the other languages have tablet ones); tell the owner, since that is a step for the machine with the Play key.
- If Android formats numbers in non-Latin digits for this language (Arabic ٠–٩, Persian ۰–۹), also write the literal numbers inside texts that way.

## 3. List it
In the same change, append the qualifier to `twangle.languages` in the root `gradle.properties`. A listed language shows up in the phone's language list even with no translations, because the libraries ship texts in it. So never list a language in one change and translate it in another. `LocalizationTest` fails when a listed language misses a string, has different placeholders, or a `values-<x>` folder isn't listed, and it names what's wrong.

Then write the language's translation record and glossary line (docs/architecture/languages.md, *Day and night*): `python -I scripts/translations.py mark <code>` records that every text was translated from today's English (`translations/<code>.txt`, committed with the texts; the strict check, which a release runs, fails without it), and add the language's section to [docs/architecture/glossary.md](../../../docs/architecture/glossary.md) with its words for the terms the others list. From then on the [translate skill](../translate/SKILL.md) keeps it up to date at night.

## 4. Note names and TalkBack
- **Default notation:** `Notation.forLanguage()` in `core/dsp/.../music/Notation.kt` holds one line per language that has a habit of its own (`GERMAN` for H instead of B, `SOLFEGE` for fixed-do Do-Re-Mi); every other language gets `DEFAULT` (letters with sharps). Add the language only when its habit is clear, and add a JVM test next to the existing `forLanguage` tests (hard rule 6). When the habit is mixed, pick what is most common in the countries that speak it (the owner's rule) and say so in the report.
- **Spoken note names:** when TalkBack should say note names differently, add `core/designsystem/src/main/res/values-<code>/speech.xml` with `spoken_letter_names` (`words` by default, or `dutch`, `german`, `swedish`) and/or `spoken_solfege` in local spelling (e.g. `Dó Ré Mi Fá Sol Lá Si`), marked `translatable="false"` with `tools:ignore="ExtraTranslation"`, like the existing ones. Copy it to `wear/src/main/res/values-<code>/speech.xml` with the names prefixed `wear_` (`wear_spoken_letter_names`, `wear_spoken_solfege`), so the watch says note names the same way; `WatchSpokenReadingTest` fails if the two differ.

## 5. Script checks
- **Fonts:** 2.0's one look, Ink, sets every text in the phone's own font, which covers every script the phone shows (docs/architecture/ui-meter-and-styles.md, *Visual styles*, **Lettering**); the one bundled face, `music_signs.ttf`, holds only ♯ ♭ ♮ ♩ ♪. So there is no face to check; look at the language's script on the phone in step 6.
- **Upper and lower case:** every `uppercase()`/`lowercase()` on shown text must use the app's locale (Turkish dotted İ). Grep for new ones.
- **Right to left** (Arabic, Persian, Hebrew, Urdu): both manifests already set `supportsRtl`. Keep to the rule in docs/architecture/languages.md (*Right to left*). The layout mirrors, but the gauges stay low-left, high-right: meter, strum check, string row, tempo dial with −/+, beat lights, A4 slider, the tuning editor's note group. Rows of note names get an LTR wrap. Look at every screen in step 6.
- **Digits the app draws** (the metronome's tempo and ruler, time signatures, the watch's cents) go through `localDigits`, so a language with its own digits needs no code. Check it on the screenshots.

## 6. Verify
1. `./gradlew test lint assembleDebug --console=plain`, which must pass with no new lint warnings, and `python -I scripts/translations.py status <code>`, which must say "up to date" (every text translated and recorded; the other languages may lag until the night).
2. Phone check on the Xiaomi ([docs/devices.md](../../../docs/devices.md) has the setup). `./gradlew :app:installDebug` (on `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, uninstall first), then `adb shell cmd locale set-app-locales io.github.klaboukes.twangle --locales <code>`, launch, and screenshot the tuner, Settings and the metronome. Look at each one: no tofu boxes, nothing clipped (scripts with marks above or below the line, like Devanagari or Thai, clip first), no font mixing inside a word, sensible line breaks. For a right-to-left language, also the chromatic mode, the instrument panel and the phone on its side (restore the phone's rotation settings afterwards). `adb logcat -d -b crash` must be empty for our app. Afterwards reset the app language (`--locales ""`) but leave Twangle's own settings as they are.
3. UI tests, following the ui-test skill: the fit checks (long words at 2× text, light and dark) in the new language: `DAY_LANGUAGES=<tag> scripts/ui-tests.sh <module> --languages -f 'everyLanguage'` per module (the tag as in `twangle.languages`, `-r` dropped: `pt-BR`). If the owner asked to batch UI runs (e.g. overnight), leave them out and write "UI tests deferred to a batch run (owner's request)" in the PR.

## 7. Document and land
- docs/architecture/languages.md: add the language to the list and its register to the register line, plus any quirk (an old qualifier, a font gap, a note-name habit).
- Push, then `gh pr create --base main --title "Add <Language>"`. The body has **What** (plain language first), **Verification** (commands, results, phone check yes/no) and the attribution footer. Then `gh pr merge --squash --delete-branch` and `git switch main && git pull`.

## 8. Report
> **Done: Twangle in <Language>** ([PR link])
> - What players see: the language in the language list (Android 13+: Settings → Apps → Twangle → Language), and its note names if they changed.
> - How it was checked.
> - Suggest that a native speaker look it over, and list the choices left open (e.g. note-name habit).
