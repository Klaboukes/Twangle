---
name: translate
description: Translate every Twangle text the night languages lack or have stale — new or reworded English that daily work wrote only in English and Dutch — in parallel batches of languages, then update the translation record, run the strict check and land it through a PR. No phone needed. The full check's first step; also by hand at any time. Use when the owner or a skill says "translate", "/translate", "bring the languages up to date", or a strict LocalizationTest (a release) stops on missing or stale translations.
argument-hint: "[optional: languages to do, e.g. de fr; default every language]"
---

# Translate the night languages

By day a change writes its texts in English and Dutch only (`twangle.dayLanguages`); this skill brings
every other language of `twangle.languages` up to date and records which English each translation was
made from ([docs/architecture/languages.md](../../../docs/architecture/languages.md), *Day and night*).
CLAUDE.md's Git workflow applies. It needs no phone and asks the owner nothing: a choice made for them
goes in the report.

## 0. Anything to do?
```bash
python -I scripts/translations.py status        # per language: "up to date" or "<n> missing, <m> stale"
```
Exit code 0, every language up to date: say so in one line and stop (no branch, no PR). This is the
usual night after a day without new texts.

**Where:** by hand, your own worktree (CLAUDE.md, *One worktree per session*). From the full check,
the main folder, on `main` and before that run's branch: this lands first, so the run's
language tests check the new texts. Branch `chore/translations-<date>` from an up-to-date `main`.

## 1. The list
```bash
mkdir -p build/translate
python -I scripts/translations.py status --json > build/translate/todo.json
```
Per language a list of texts, each with `state` (`missing`, or `stale`: reworded in English since it
was translated, or not recorded yet), `module`, `name`, `note` (the translator note above the English,
or its section's comment), `english`, `dutch` and `current` (the language's version, if any) as XML,
and the `file` to edit.

## 2. Dutch first, yourself
Dutch is written by day but not recorded, so it is on the list as stale. For each entry: does the
Dutch say what the English says now? A text reworded in English but not in Dutch gets its Dutch
rewritten (the owner's Dutch style: "Kies een snaar", short tips). Then
`python -I scripts/translations.py mark nl`. Do this before step 3: the batches use the Dutch as a
second reference.

## 3. The other languages, in parallel
Split the remaining languages with work into batches of about four, related ones together (the Romance
languages; German and the Nordic ones; Russian, Ukrainian, Polish, Turkish; Chinese, Japanese, Korean;
Hindi, Bengali, Indonesian, Vietnamese, Thai; Arabic with whatever is left), and start one sub-agent
per batch, all in one message, on your own model. Each writes only its languages' files and records,
so they never touch the same file; none runs Gradle. Give each this brief, filled in:

> Translate Twangle's (an Android guitar tuner) missing and stale texts into <languages>. Your list
> per language: `build/translate/todo.json` (keys <languages>), after `python -I scripts/translations.py
> status --json <languages>` if you want it fresh. For each entry, translate from `english`, using the
> `note` for meaning and place and the `dutch` as a second reference; for a stale one, `current` shows
> the old translation: keep its words where the English didn't change. Edit the entry's `file`: put a
> missing text where it stands in the English file (after the same neighbour), replace a stale one in
> place, change nothing else, copy no comments.
> Rules: the register and typography of each language in `docs/architecture/languages.md`
> (*Languages and registers*); the app's words for recurring terms in
> `docs/architecture/glossary.md` (don't edit it: name a new recurring term you settled in your
> report); as short as the English, since labels share narrow rows; placeholders (`%1$d`, `%1$s`)
> exactly as in English; plurals with every quantity the language uses (the register line names them;
> only `other` for Chinese, Japanese, Korean, Indonesian, Vietnamese, Thai); Android escaping (`\'`,
> `\"`, `\@` or `\?` at the start); Arabic digits ٠–٩ inside Arabic texts; note names (A4, E♭2),
> Twangle and tuning names like DADGAD unchanged; tips are short imperatives ("Pick a string", never
> "Tap a string").
> When a language's list is done, run `python -I scripts/translations.py mark <language>` and
> `python -I scripts/translations.py status <language>` (must say "up to date"). Report per language
> the number of texts done, any new recurring term, and any choice a native speaker should look at,
> in one line each.

## 4. Check
Add the new recurring terms the batches named to their languages' lines in
[the glossary](../../../docs/architecture/glossary.md).
```bash
python -I scripts/translations.py status        # every language up to date
./gradlew test lint assembleDebug -Ptwangle.strictTranslations=true --console=plain
```
The strict run is the gate a release uses: every language complete, placeholders right, the record
matching today's English. Lint catches a broken escape or format. Fix what fails in the file it names
(a batch's mistake is fixed here, not by a new batch). Look over `git diff --stat`: only `values-*`
files, `translations/` and perhaps `docs/architecture/glossary.md` changed.

## 5. Land it
One commit, `Translations: <n> texts in <m> languages`. Push, `gh pr create --base main --title
"Translations: <date>"` with a plain body (which texts, in which languages, the choices a native speaker
should check, the strict run's result) and the attribution footer, then `gh pr merge --squash` and the
clean-up CLAUDE.md describes (in a worktree: remove it; in the main folder: `git switch main && git pull`
and delete the branch).

## 6. Report
In plain language, short: how many texts in how many languages, and the choices worth a native
speaker's look. From the full check this goes into its summary.

The Play Store texts and release notes in `fastlane/` are not part of this: the release skill
translates the notes, and the store listing is written with Phase 50's listing work.
