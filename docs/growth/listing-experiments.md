# Store listing experiments on Google Play

Play Console can show part of the people who open Twangle's store page a different text or picture, and count which version gets more installs. Phase 92 of the [roadmap](../../ROADMAP.md) plans three of them, one at a time: first the short description, then the first screenshot, then the icon. Claude prepares each variant; **the owner** sets the experiment up in the Play Console and ends it. The listing itself is described in [docs/store-listing.md](../store-listing.md).

## Why

The short description is the line under Twangle's name in Play's search results, so it decides whether a searcher opens the page. Since Phase 50 it leads with "no ads, no account, fully offline", the reason players who already have a tuner switch. The other candidate leads with TalkBack: few tuners speak, and it says something no other listing says. Neither is known to be better; an experiment lets Play's visitors decide.

**Honestly:** an experiment needs visitors. Twangle launched on 2026-11-11 and has a handful of store-page visitors a day, so this one may run for weeks and still end with "no clear winner". It is worth starting once the visitors grow; **the owner** sees them in the Play Console's store performance figures (store listing visitors). As a rough guess (Claude's, not a figure of Google's): from about 100 visitors a day in these five languages together, a result within a few weeks is realistic; with 10 a day it will most likely end without one. Starting it earlier costs nothing but patience, as long as the listing is not changed while it runs (below).

## Google's rules (checked 2026-11-16)

From Play Console Help, *Run A/B tests on your store listing* (https://support.google.com/googleplay/android-developer/answer/6227309). Google changes these now and then, so the Console itself wins where it differs.

- **Two kinds:** a *default graphics* experiment tests only pictures (icon, feature graphic, screenshots) in the default language, English (United States); a **localized** experiment tests pictures and/or the **descriptions** in chosen languages. A text can only be tested in a localized experiment.
- **Up to five languages** in one localized experiment, and up to five localized experiments at once (or one default graphics experiment). Variants are only shown to people who view the listing in the chosen languages.
- **Up to two variants** against the current listing. The traffic share for the variants (the *audience*) is split equally between them.
- **Target metric:** *Unique user install clicks* (people who install, who didn't have Twangle on another device), *unique user open clicks* or *pre-registration clicks*. The help page no longer offers "retained first-time installers" as a target; if the results page still shows a retained (one-day) figure beside it, note it too.
- **Statistics:** a *minimum detectable effect* and a *confidence level* in the advanced settings; Google's defaults give a result soonest. The creation page estimates how long it will take and how many installs it needs, and warns when a result is less than a week away.
- **How long:** no minimum; an experiment stops by itself after 6 months.
- **Review:** an experiment's variants go through Google's review like any listing change; with managed publishing on, **the owner** publishes it after the review.
- One attribute at a time is Google's advice, so each experiment below changes one thing only.

## Experiment 1: the short description, TalkBack against the current one

**Set-up:**
- **Type:** localized experiment. **Name:** `Short description: TalkBack` (under Google's 50 characters).
- **Languages (5):** English (United States), Spanish (Latin America), Portuguese (Brazil), French (France), Dutch (Netherlands).
- **Target metric:** unique user install clicks.
- **Variants:** 1. **Audience:** 50 % (half the visitors see the variant, half the current text), the most Play allows for one variant, since visitors are few.
- **Minimum detectable effect and confidence level:** Google's defaults.
- **How long:** at least two full weeks (whole weeks, so weekends count equally), then as long as the Console says it needs. If after about 8 weeks it still shows no winner, end it as a draw and keep the current text.

**Why these languages:** English (United States) is the default listing, which every English speaker sees, since Twangle has no other English listing. Latin America, Brazil and France are, after the United States, the places where guitar-tuner searches are biggest and grow most at Christmas (store-listing.md, *Timing: Christmas*: Mexico, Brazil, France), and each has its own listing. Dutch takes the fifth place over Spain or Germany because Twangle's first visitors and both its first reviews are Dutch, so it adds the visitors there are today, and the owner can judge its wording. Spanish (Spain) or German can replace Dutch in a later round if the Console shows more visitors there.

**The texts** (Play's limit is 80 code points; counted 2026-11-16). Each variant follows its language's current short description and the app's own words: the name *TalkBack* as Android shows it, the language's word for metronome as the full description uses it, and its short "no ads, no account". Google's rules hold as for the listing: no "free", no "best" or "#1", no emoji, no other app named.

| Language | Current (control) | Variant (TalkBack) |
|---|---|---|
| English (United States), `en-US` | Tuner for guitar, bass, ukulele and violin. No ads, no account, fully offline. (78) | Guitar tuner and metronome that speaks with TalkBack. No ads, no account. (73) |
| Spanish (Latin America), `es-419` | Afinador de guitarra, bajo, ukelele, violín. Sin anuncios ni cuenta ni internet. (80) | Afinador de guitarra y metrónomo que habla con TalkBack. Sin anuncios ni cuenta. (80) |
| Portuguese (Brazil), `pt-BR` | Afinador de violão, baixo, ukulele e violino. Sem anúncios, sem conta, offline. (79) | Afinador de violão e metrônomo que fala com o TalkBack. Sem anúncios, sem conta. (80) |
| French (France), `fr-FR` | Accordeur de guitare, basse, ukulélé, violon. Sans pub, sans compte, hors ligne. (80) | Accordeur de guitare et métronome qui parle avec TalkBack. Sans pub ni compte. (78) |
| Dutch (Netherlands), `nl-NL` | Stemmer voor gitaar, bas, ukelele en viool. Geen reclame, geen account, offline. (80) | Gitaarstemmer en metronoom die spreekt via TalkBack. Geen reclame, geen account. (80) |

**A trade-off to keep in mind:** the variant drops the words bass, ukulele and violin, and "offline". The experiment measures how many visitors install, not how many people find the page; Play's search also reads the short description, so a winning variant could bring fewer searchers for "ukulele tuner" or "bass tuner". The title and the full description's first lines still carry those words, and Phase 92's *Search, language by language* checks the search terms per country after the first month, so this can be judged then.

**The owner's steps in the Play Console:**
1. Before starting: nothing of Twangle's in Google's review, and no `scripts/release.sh listing` planned for the coming weeks (a listing change while it runs would change the control half way).
2. *Grow users → Store presence → Store listings*. In the default store listing's *Experiment* column click **Set up** (or open the listing and click **Set up experiment**).
3. Name: `Short description: TalkBack`. Type: **Localized**. Languages: English (United States), Spanish (Latin America), Portuguese (Brazil), French (France), Dutch (Netherlands). Target metric: **Unique user install clicks**.
4. Advanced settings: variants **1**, audience **50 %**, minimum detectable effect and confidence level as they are. Click **Next**.
5. What to test: **Short description**. For each of the five languages paste the variant text from the table above (the right-hand column), exactly as written.
6. Check the summary (only the short description changes; the title, full description and pictures stay the same) and click **Save**.
7. *Publishing overview*: send the change for review, and once Google approves it click **Publish changes** (managed publishing).
8. Each week or so, look at *Store listings → the experiment → Results*, and tell Claude what it shows (the installs per version and the range Play gives) so they go into this file.

**After it ends:**
- **The variant wins:** **the owner** clicks *Apply* in the Console (Play puts the variant on the listing). Claude then puts the five new texts into `fastlane/metadata/android/<locale>/short_description.txt`, so the next `listing` doesn't put the old ones back, and writes the TalkBack text in the other 33 store languages, after the owner's yes for the next `scripts/release.sh listing`. Every "What's new" opens with its language's short description word for word (store-listing.md, *Being found*), so the next release notes open with the new line.
- **The current text wins, or it's a draw:** the owner stops the experiment in the Console; nothing changes in `fastlane/metadata/`.
- **Either way** the losing text and the figures stay here, under *Results*, and store-listing.md's *Short description* says which one won.

**Results:** not started yet.

## Experiment 2: the first screenshot (placeholder)

Next in the roadmap's order, once the first one has ended. Claude prepares the variant first screenshot in the five languages (or a default graphics experiment, English only, if pictures without words are tested); the candidates and the set-up come here then.

## Experiment 3: the icon (placeholder)

After the screenshot. An icon is the same in every language, so this one can be a default graphics experiment. The candidate and the set-up come here then.
