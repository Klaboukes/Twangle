#!/usr/bin/env bash
# Twangle's release routine for Google Play (docs/release.md, the release skill).
#
#   scripts/release.sh bump patch|minor|major
#       Sets twangle.version in gradle.properties to the last released version (the highest v* tag)
#       raised by one step (1.0.0 -> 1.0.1 | 1.1.0 | 2.0.0) and says which release-notes files to write:
#       fastlane/metadata/android/<Play locale>/changelogs/<versionCode>.txt. A version that was bumped
#       but never released (no v<version> tag) is kept when it is already that high, and its notes are
#       rewritten rather than left behind; one that isn't high enough is raised, its notes moved along.
#   scripts/release.sh notes
#       Prints the release notes of the current version as one block for the Play Console's
#       "Release notes" box (<en-US> ... </en-US> and so on for every language).
#   scripts/release.sh profile
#       Whether the Baseline Profile (app/src/release/generated/) is newer than the last change to the
#       app's screens (app/src/main, feature/*/src/main, core/designsystem/src/main). Only reads git.
#   scripts/release.sh full-check
#       Whether the last full check on this PC passed whole on the code of this commit (Phase 90): its
#       record (build/full-check/result.txt, scripts/full-check.sh) says the phones' part passed (or
#       its failures were fixed and re-checked: full-check.sh --fixed) and the watch's did, each at a
#       commit this one follows, from a clean tree, and since then nothing changed that goes into the
#       bundles: only *.md, docs/, .claude/, .github/, scripts/, website/, fastlane/ (the release notes),
#       a regenerated Baseline Profile (app/src/release/generated/) and the version line in
#       gradle.properties (the bump). Only reads.
#   scripts/release.sh bundle [--keep-old-profile] [--watch-untested]
#       On main, once the version bump has landed: checks that the full check passed whole on this
#       code (as `full-check`; --watch-untested, only on the owner's word, releases although the watch
#       app's tests didn't run), that every language is complete and up to
#       date (LocalizationTest in strict mode, docs/architecture/languages.md, *Day and night*) and that
#       the Baseline Profile is current (as `profile`; --keep-old-profile releases with an older one),
#       builds the phone app's and the watch app's bundles signed with the upload key, checks that their
#       manifests ask for no permission outside the allowlist (no INTERNET; the root build.gradle.kts)
#       and the signatures, puts them with the notes in release/ (git-ignored), then tags the commit
#       v<version> and pushes the tag. Refuses a version that already has a tag.
#   scripts/release.sh upload [watch] [draft|completed]
#       After bundle: sends release/Twangle-<version>.aab and its notes in every language to the
#       internal track on Google Play, through the Google Play API (Gradle Play Publisher in :app). As a
#       draft by default, the only kind Play takes while Twangle itself is still a draft there: the owner
#       starts it in the Play Console with one click. "completed" makes it live on the internal track at
#       once (for later, once the app is out of draft). Needs the robot user's key (a service account):
#       ~/.twangle-keys/play-service-account.json, or the file in $TWANGLE_PLAY_SERVICE_ACCOUNT.
#   scripts/release.sh promote [watch] closed [draft|completed]
#       Once the owner has checked the internal build: moves that same build (this version) from the
#       internal track to the closed test (Play's track "alpha", or $TWANGLE_PLAY_CLOSED_TRACK) with its
#       notes. A draft by default, as upload; "completed" makes it live for the testers at once.
#   scripts/release.sh promote [watch] production [draft]
#       Moves this version from the internal track to production (whether or not it went to the closed
#       test too) as a staged rollout to 20 % of people (Google reviews it first); the app's first
#       release there goes to everyone, since Play allows no staged rollout for it. "draft" leaves it a
#       draft release to start in the Play Console.
#   scripts/release.sh rollout [watch] <percent>
#       Raises this version's staged rollout on production to <percent> (1..100); 100 is everyone and
#       ends the staged rollout.
#   scripts/release.sh halt [watch]
#       Halts this version's rollout on production: nobody new gets it, who has it keeps it.
#   "watch" in upload, promote, rollout and halt: the same for the watch app's bundle
#       (release/Twangle-<version>-watch.aab, versionCode + 1,000,000) on Play's dedicated Wear OS
#       tracks: "wear:qa" (internal testing), "wear:<closed test>" ($TWANGLE_PLAY_WATCH_CLOSED_TRACK,
#       "alpha" by default) and "wear:production". Play takes no watch bundle on the phone's tracks
#       (since September 2023; docs/release.md, *The watch app*).
#   scripts/release.sh listing
#       Sends the store listing to Google Play: every language's title, short and full description and
#       phone and tablet screenshots (and, from the watch's turn on Play, its Wear OS screenshots: scripts/watch-screenshots.sh),
#       the icon (one for every language) and each language's feature graphic, and the
#       app details (default language en-US, contact email, website). From fastlane/metadata/android/
#       as it is on main. Refuses unless every language has its screenshots (scripts/store-screenshots.sh;
#       git-ignored; it first takes the latest set the other PC shared on GitHub, scripts/share-store-screenshots.sh), and checks the texts and pictures against Play's rules first.
#       Not sent for review: it waits in the Play Console's Publishing overview until the owner sends it,
#       with the release it goes with. Each send is noted as a tag listing/<date-time> on the commit sent.
#   scripts/release.sh listing-changes
#       Whether the store listing needs sending: what changed in its texts and pictures since the last
#       listing/* tag, and the app's screen changes since then (do the screenshots still match?).
#   scripts/release.sh status
#       What is on Google Play now: each track (internal, closed test "alpha", open test "beta",
#       production) with its releases (name, versionCode, draft / live / staged rollout % / halted), and
#       the store-listing languages.
#   scripts/release.sh health [days]
#       Crash and ANR rates (user-perceived) of each version over the last <days> days (7 by default),
#       from the Google Play Developer Reporting API, against Play's bad-behaviour lines (1.09 % and
#       0.47 %), with a verdict. Run before asking the owner to raise a staged rollout.
#   scripts/release.sh reviews [language]
#       The reviews with text of the last week (all the API gives), every language, with stars, date,
#       version and phone, the original text and a translation into <language> (en by default).
#
# status, health and reviews only read (scripts/PlayReports.java; status opens a temporary edit and
# deletes it, never commits it), so they need no yes from the owner. They need the robot user's key too.
# listing-changes and full-check only read (git, and the full check's record).
#
# upload, promote, rollout, halt and listing change what people get or see on Google Play, so Claude
# asks the owner before running any of them (docs/release.md). They act on the version in gradle.properties, which
# must be released (tagged), and ask nothing themselves. All but halt are refused while changes are in
# Google's review, rather than restarting that review (play_send).
#
# The watch app goes to Play right after the phone (decision 3, ROADMAP.md Phase 88): bundle builds its
# bundle with every release, but it reaches Play only through the "watch" commands, the reviewed ones
# only once the phone's production release is approved (docs/release.md, "The watch app").
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
LISTINGS="fastlane/metadata/android"
# What the phone screenshots show: the app's screens, their captions, and the test that draws them.
SCREENSHOT_SOURCES=(app/src/main ':(glob)feature/*/src/main/**' core/designsystem/src/main
    app/src/androidTest/kotlin/io/github/klaboukes/twangle/app/store "$LISTINGS/*/screenshot_captions.txt")
# What the watch screenshots show: the watch app, the note names it writes, and the test that draws them.
WATCH_SCREENSHOT_SOURCES=(wear/src/main core/dsp/src/main/kotlin/io/github/klaboukes/twangle/core/dsp/music
    wear/src/androidTest/kotlin/io/github/klaboukes/twangle/wear/WatchStoreScreenshots.kt)
OUT="release"
APP_ID="io.github.klaboukes.twangle"
PLAY_KEY="${TWANGLE_PLAY_SERVICE_ACCOUNT:-$HOME/.twangle-keys/play-service-account.json}"
# Play's API name of the closed test the Play Console makes first ("Closed testing - Alpha").
CLOSED_TRACK="${TWANGLE_PLAY_CLOSED_TRACK:-alpha}"
# The watch app's tracks on Play: its own Wear OS tracks, "wear:" and the track's name (Play's API docs,
# developers.google.com/android-publisher/tracks: internal testing is "qa" there, so "wear:qa"). A closed
# test is named by hand in the Play Console; scripts/release.sh status shows the names Play has.
WATCH_PREFIX="wear:"
WATCH_INTERNAL_TRACK="${WATCH_PREFIX}qa"
WATCH_CLOSED_TRACK="$WATCH_PREFIX${TWANGLE_PLAY_WATCH_CLOSED_TRACK:-alpha}"
# The watch build's versionCode is the phone's plus this (root build.gradle.kts, twangleWatchVersionCode).
WATCH_CODE_OFFSET=1000000
# What the Baseline Profile covers: the app's screens and what they draw (docs/architecture/performance.md).
PROFILE="app/src/release/generated"
PROFILE_SOURCES=(app/src/main ':(glob)feature/*/src/main/**' core/designsystem/src/main)
# The first step of a staged rollout to production, in percent (docs/release.md, The idea).
FIRST_ROLLOUT=20

fail() { echo "release: $*" >&2; exit 1; }

version() { sed -n 's/^twangle.version=//p' gradle.properties | tr -d '\r'; }

# The versionCode the root build.gradle.kts makes of a version: major * 10000 + minor * 100 + patch.
code_of() {
    local major minor patch
    IFS=. read -r major minor patch <<<"$1"
    echo $((10#$major * 10000 + 10#$minor * 100 + 10#$patch))
}

locales() { find "$LISTINGS" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort; }

# The notes block for the Play Console; fails naming every language without notes for [code].
notes_block() {
    local code="$1" missing=() locale file
    for locale in $(locales); do
        file="$LISTINGS/$locale/changelogs/$code.txt"
        if [[ ! -s "$file" ]]; then missing+=("$locale"); continue; fi
        printf '<%s>\n%s\n</%s>\n' "$locale" "$(tr -d '\r' <"$file")" "$locale"
    done
    [[ ${#missing[@]} -eq 0 ]] || fail "no release notes for versionCode $code in: ${missing[*]} (changelogs/$code.txt)"
}

is_released() { git rev-parse --quiet --verify "refs/tags/v$1" >/dev/null; }

# The next version after the last release, one step up. The last release is the highest v* tag; a
# version bumped earlier but never released (no tag: the release stopped before bundle) doesn't count,
# so a second bump keeps it instead of skipping a number and leaving its notes behind.
bump() {
    local level="${1:-}" current base major minor patch next current_code next_code moved=() locale
    [[ "$level" == patch || "$level" == minor || "$level" == major ]] ||
        fail "usage: scripts/release.sh bump patch|minor|major"
    git fetch --quiet origin --tags 2>/dev/null || true
    current="$(version)"
    if is_released "$current"; then
        base="$current"
    else
        base="$(git tag -l 'v*' --sort=-v:refname | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | head -1 | sed 's/^v//')"
        [[ -n "$base" ]] || fail "no release tag (v*) to count from; set twangle.version by hand"
    fi
    IFS=. read -r major minor patch <<<"$base"
    case "$level" in
        patch) patch=$((10#$patch + 1)) ;;
        minor) minor=$((10#$minor + 1)); patch=0 ;;
        major) major=$((10#$major + 1)); minor=0; patch=0 ;;
    esac
    (( major <= 99 && minor <= 99 && patch <= 99 )) || fail "each part of the version stays 0..99"
    next="$major.$minor.$patch"
    current_code="$(code_of "$current")"
    next_code="$(code_of "$next")"

    if [[ "$current" != "$base" ]] && (( current_code >= next_code )); then
        echo "Version $current is kept: it was never released (no tag v$current; the last release is v$base),"
        echo "and it is already at least a $level step up from it."
        echo "Rewrite its release notes for everything since v$base, one file a language (they may already"
        echo "hold an older draft): $LISTINGS/<locale>/changelogs/$current_code.txt"
        return
    fi

    sed -i "s/^twangle.version=.*/twangle.version=$next/" gradle.properties
    echo "Version $current -> $next (versionCode $next_code)."
    # An unreleased version's notes would stay behind under a versionCode nothing uses: move them along
    # as the draft to rewrite.
    if [[ "$current" != "$base" ]]; then
        for locale in $(locales); do
            if [[ -f "$LISTINGS/$locale/changelogs/$current_code.txt" && ! -e "$LISTINGS/$locale/changelogs/$next_code.txt" ]]; then
                mv "$LISTINGS/$locale/changelogs/$current_code.txt" "$LISTINGS/$locale/changelogs/$next_code.txt"
                moved+=("$locale")
            fi
        done
        if [[ ${#moved[@]} -gt 0 ]]; then
            echo "$current was never released (no tag v$current): its notes moved to $next_code.txt in ${#moved[@]} languages,"
            echo "as the draft to rewrite for everything since v$base."
        fi
    fi
    echo "Write the release notes, one file a language: $LISTINGS/<locale>/changelogs/$next_code.txt"
}

# Whether the Baseline Profile was regenerated after the last change to what it covers; says which and
# returns 1 when it wasn't. It only reads git.
profile_current() {
    local made changes count
    made="$(git log -1 --format=%H -- "$PROFILE")"
    [[ -n "$made" ]] || { echo "No Baseline Profile in $PROFILE."; return 1; }
    changes="$(git log --oneline "$made..HEAD" -- "${PROFILE_SOURCES[@]}")"
    if [[ -z "$changes" ]]; then
        echo "The Baseline Profile is current (regenerated $(git log -1 --format=%cs "$made"), no change to the app's screens since)."
        return 0
    fi
    count="$(wc -l <<<"$changes")"
    echo "The Baseline Profile is older than the app's screens: regenerated $(git log -1 --format=%cs "$made") (${made:0:8}),"
    echo "and $count commits changed app/src/main, feature/*/src/main or core/designsystem/src/main since, the newest:"
    echo "$changes" | head -5 | sed 's/^/  /'
    echo "Regenerate it with the Xiaomi connected (about 15 minutes; docs/architecture/performance.md, *Baseline Profile*):"
    echo "  uninstall the debug build, then"
    echo "  ./gradlew :app:generateBaselineProfile -Pandroid.testInstrumentationRunnerArguments.class=io.github.klaboukes.twangle.baselineprofile.BaselineProfileGenerator"
    echo "and land $PROFILE/ through a chore/ branch."
    return 1
}

# The full check's record (scripts/full-check.sh, *The record*), in this checkout: releases and the full
# check both run in the main folder (CLAUDE.md, *One worktree per session*).
FULL_CHECK_RECORD="build/full-check/result.txt"
# What may change after the full check without its pass going stale: nothing that goes into the
# phone app's or the watch app's bundle (gradle.properties' version line is checked on its own).
NOT_IN_THE_BUNDLES='(^|/)[^/]*\.md$|^docs/|^\.claude/|^\.github/|^scripts/|^website/|^fastlane/|^app/src/release/generated/'

# Whether the full check's record of [part] (phones, watch) vouches for this commit; says which, and
# returns 1 when it doesn't.
part_checked() {
    local part="$1" what="$2" line code commit clean date extra changed note pr run
    line="$(grep "^$part " "$FULL_CHECK_RECORD" 2>/dev/null | tail -1 || true)"
    [[ -n "$line" ]] || { echo "No full check of $what on record on this PC ($FULL_CHECK_RECORD)."; return 1; }
    read -r _ code commit clean _ date extra <<<"$line"
    date="${date//_/ }"
    case "$code" in
        0) ;;
        1) echo "The full check of $what failed at ${commit:0:8} ($date)."; return 1 ;;
        2) echo "The full check didn't test $what at ${commit:0:8} ($date): not connected, dropped off or busy."; return 1 ;;
        *) echo "The full check of $what didn't finish at ${commit:0:8} ($date): a device was busy or none was left for a job."; return 1 ;;
    esac
    [[ "$clean" == 1 ]] || { echo "The full check of $what at ${commit:0:8} ran with uncommitted changes, so it vouches for no commit."; return 1; }
    git merge-base --is-ancestor "$commit" HEAD 2>/dev/null ||
        { echo "The full check of $what ran at ${commit:0:8}, which this commit doesn't follow."; return 1; }
    changed="$(git diff --name-only "$commit" HEAD | grep -Ev "$NOT_IN_THE_BUNDLES" | grep -vx gradle.properties || true)"
    # The version bump: twangle.version may change, nothing else in gradle.properties.
    if [[ -n "$(git diff -U0 "$commit" HEAD -- gradle.properties | grep -E '^[-+]' | grep -Ev '^(\+\+\+|---)' |
        grep -v '^[-+]twangle\.version=' || true)" ]]; then
        changed="$changed${changed:+$'\n'}gradle.properties"
    fi
    if [[ -n "$changed" ]]; then
        echo "The full check of $what passed at ${commit:0:8} ($date), but what goes into the bundles changed since:"
        head -5 <<<"$changed" | sed 's/^/  /'
        return 1
    fi
    note=""
    if [[ "$extra" == "fixed "* ]]; then
        read -r _ pr run <<<"$extra"
        note=": the run at ${run:0:8}, its failures fixed by $pr and re-checked"
    fi
    echo "The full check of $what passed at ${commit:0:8} ($date$note), and nothing that goes into the bundles changed since."
}

# Whether the last full check passed whole on this code: the phones' part and the watch's. With
# [--watch-untested] (the owner's word) a watch whose tests didn't pass is let through, said so.
full_check_passed() {
    local ok=0
    part_checked phones "the phones" || ok=1
    if ! part_checked watch "the watch"; then
        if [[ "${1:-}" == --watch-untested ]]; then
            echo "Going ahead without the watch's tests (--watch-untested)."
        else
            ok=1
        fi
    fi
    return $ok
}

# Gradle runs on Android Studio's JDK (CLAUDE.md, Dev machine environment).
use_java() {
    if [[ -z "${JAVA_HOME:-}" && -d "/c/Program Files/Android/Android Studio/jbr" ]]; then
        export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
    fi
    [[ -n "${JAVA_HOME:-}" ]] || fail "JAVA_HOME is not set (CLAUDE.md, Dev machine environment)"
}

bundle() {
    local v code tag aab watch_aab keep_profile=false watch_untested="" arg
    for arg in "$@"; do
        case "$arg" in
            --keep-old-profile) keep_profile=true ;;
            --watch-untested) watch_untested=--watch-untested ;;
            *) fail "usage: scripts/release.sh bundle [--keep-old-profile] [--watch-untested]" ;;
        esac
    done
    v="$(version)"
    code="$(code_of "$v")"
    tag="v$v"

    [[ -z "$(git status --porcelain)" ]] || fail "uncommitted changes; release only what is on main"
    [[ "$(git rev-parse --abbrev-ref HEAD)" == "main" ]] || fail "not on main; land the version bump first"
    git fetch --quiet origin main --tags
    [[ "$(git rev-parse HEAD)" == "$(git rev-parse origin/main)" ]] || fail "main differs from origin/main; git pull first"
    if git rev-parse --quiet --verify "refs/tags/$tag" >/dev/null; then
        fail "$tag is already released; raise the version (scripts/release.sh bump patch)"
    fi
    notes_block "$code" >/dev/null
    # Nothing is released that the full check hasn't passed whole (the release skill's rule, owner
    # 2026-10-28; until Phase 90 only the skill checked it).
    full_check_passed $watch_untested ||
        fail "the last full check on this PC didn't pass whole on this code (above): run the full check first (/full-check; for a watch not tested, scripts/full-check.sh --watch), then bundle again. Nothing was built or tagged."
    # A profile older than the screens leaves the new code to the JIT at startup: slower on slow phones.
    if ! profile_current; then
        $keep_profile || fail "the Baseline Profile is older than the app's screens (above). Regenerate it first, or
release with the old one: scripts/release.sh bundle --keep-old-profile. Nothing was built or tagged."
        echo "Going ahead with the old Baseline Profile (--keep-old-profile)."
    fi
    use_java

    # By day only English and Dutch are written; the full check's /translate does the rest. A
    # release ships nothing half translated.
    echo "Checking that every language is complete and up to date..."
    ./gradlew --quiet :app:testDebugUnitTest --tests '*LocalizationTest' -Ptwangle.strictTranslations=true ||
        fail "a language lacks a text or has a stale one (above; python -I scripts/translations.py status lists them): the full check translates them (/translate), release after it"

    # The watch app's bundle too, from the same commit, so the update that brings it needs no other
    # build (it goes to Play only through upload watch).
    echo "Building Twangle $v (versionCode $code; the watch app $((code + WATCH_CODE_OFFSET)))..."
    ./gradlew --quiet :app:bundleRelease :wear:bundleRelease
    aab="app/build/outputs/bundle/release/app-release.aab"
    watch_aab="wear/build/outputs/bundle/release/wear-release.aab"
    [[ -f "$aab" ]] || fail "no bundle at $aab"
    [[ -f "$watch_aab" ]] || fail "no bundle at $watch_aab"
    # The "no internet" promise on the bundles themselves: bundletool's dump of each manifest may ask
    # only for the permissions on the allowlist (allowedPermissions, the root build.gradle.kts).
    ./gradlew --quiet :app:checkReleaseBundlePermissions :wear:checkReleaseBundlePermissions ||
        fail "a bundle asks for a permission outside the allowlist (above): no INTERNET, ACCESS_NETWORK_STATE or AD_ID; nothing was tagged"
    # Play takes only a bundle signed with the upload key; without ~/.twangle-keys the build is unsigned.
    "$JAVA_HOME/bin/jarsigner" -verify "$aab" | grep -q "jar verified" ||
        fail "the bundle is not signed: is the upload key in ~/.twangle-keys/ (docs/architecture/performance.md)?"
    "$JAVA_HOME/bin/jarsigner" -verify "$watch_aab" | grep -q "jar verified" ||
        fail "the watch app's bundle is not signed: is the upload key in ~/.twangle-keys/ (docs/architecture/performance.md)?"

    mkdir -p "$OUT"
    cp "$aab" "$OUT/Twangle-$v.aab"
    cp "$watch_aab" "$OUT/Twangle-$v-watch.aab"
    notes_block "$code" >"$OUT/Twangle-$v-release-notes.txt"

    git tag -a "$tag" -m "Twangle $v (versionCode $code)"
    # A tag only here would make the next try refuse the version as released.
    if ! git push --quiet origin "$tag"; then
        git tag -d "$tag" >/dev/null
        fail "couldn't push the tag $tag to GitHub, so $v is not tagged yet; run scripts/release.sh bundle again"
    fi

    echo
    echo "Twangle $v is ready for the Play Console (docs/release.md):"
    echo "  bundle:        $OUT/Twangle-$v.aab"
    echo "  watch app:     $OUT/Twangle-$v-watch.aab (to Play only with upload watch)"
    echo "  release notes: $OUT/Twangle-$v-release-notes.txt"
    echo "  tagged $tag at $(git rev-parse --short HEAD) and pushed"
}

# Fails, saying what to do, unless the robot user's key is there. Checked first: without it nothing
# else matters, and nothing is built or sent.
need_play_key() {
    [[ -f "$PLAY_KEY" ]] || fail "the Google Play key is missing ($PLAY_KEY).
It is the robot user's key (a service account) that lets this PC send releases to Google Play. It is
made once in Google Cloud and the Play Console, with Claude guiding (docs/release.md, Uploads through
the Google Play API), and kept only on the owner's machines. Nothing was sent to Google Play."
}

# Fails unless version $1 (versionCode $2) is released, that is tagged, with its notes in every
# language unchanged since the tag: Play gets the working copy's notes.
need_release() {
    local v="$1" code="$2" tag="v$1" notes
    git rev-parse --quiet --verify "refs/tags/$tag" >/dev/null || fail "$tag is not tagged: make the bundle with scripts/release.sh bundle"
    # Every release has its notes in every language, except the very first (1.0.0, nothing to tell
    # what changed since), which goes without when it has none.
    if [[ -z "$(git tag -l 'v*' | grep -vx "$tag")" ]] &&
        ! compgen -G "$LISTINGS/*/changelogs/$code.txt" >/dev/null; then
        echo "The first release, without release notes."
    else
        notes_block "$code" >/dev/null
    fi
    notes="$LISTINGS/*/changelogs/$code.txt"
    [[ -z "$(git status --porcelain -- "$notes")" ]] && git diff --quiet "$tag" -- "$notes" ||
        fail "the release notes ($notes) differ from $tag; send the notes that were released"
}

# Runs a Gradle Play Publisher task of :app, signed in with the robot user's key.
play_gradle() {
    local key="$PLAY_KEY"
    use_java
    # Gradle wants a Windows path for the key; Git Bash's /c/... is not one.
    if command -v cygpath >/dev/null; then key="$(cygpath -m "$key")"; fi
    ./gradlew --quiet "$@" "-Ptwangle.playServiceAccount=$key"
}

# Sends what a Gradle Play Publisher task ($2, its options after it) changes on Play. The plugin fills its
# edit without committing it (--no-commit, leaving the edit's id in app/build/gpp/<app id>.txt) and
# PlayReports.java commits it, refused while anything is in Google's review: the plugin's own commit
# cancels that review and starts it again (as with 2.0 on 2026-11-06). $1 is "commit", or "commit-unsent"
# to hold the changes back for the owner's Send changes for review (a "commit" Google won't send for review by
# itself is committed that way too, as the plugin's own commit does). Fails having sent nothing of it, except
# when the commit's answer was lost (PlayReports.java exits 3): then it ends the script, since it may be on Play.
# (The plugin always opens a fresh edit; it can't be handed one.)
play_send() {
    local how="$1" task="$2" gpp="app/build/gpp" edit code
    shift 2
    rm -rf "$gpp"
    if play_gradle "$task" --no-commit "$@" && [[ -s "$gpp/$APP_ID.txt" ]]; then
        edit="$(tr -d '[:space:]' < "$gpp/$APP_ID.txt")"
        rm -rf "$gpp"
        play_read "edit-$how" "$edit" && return
        code=$?
        (( code != 3 )) || fail "the answer to the commit was lost (above): it may be on Google Play or not. Look first
(scripts/release.sh status, the Play Console's Publishing overview), then send it again only if it isn't there."
    else
        [[ ! -s "$gpp/$APP_ID.txt" ]] || play_read edit-delete "$(tr -d '[:space:]' < "$gpp/$APP_ID.txt")"
        rm -rf "$gpp"
    fi
    return 1
}

# A whole percent as Play's user fraction: 20 -> 0.20.
fraction() { printf '%d.%02d' $(($1 / 100)) $(($1 % 100)); }

# Which app the Play steps act on: the phone app, or with "watch" first the watch app, which Play keeps
# on its own Wear OS tracks ("wear:...") under its own versionCode (the phone's + 1,000,000). Sets
# APP_NAME, the tracks INTERNAL, CLOSED and PRODUCTION, AAB_SUFFIX and WATCH_ARG (for the hints).
APP_NAME="Twangle" INTERNAL=internal CLOSED="$CLOSED_TRACK" PRODUCTION=production AAB_SUFFIX="" WATCH_ARG=""
for_watch() {
    APP_NAME="Twangle's watch app" INTERNAL="$WATCH_INTERNAL_TRACK" CLOSED="$WATCH_CLOSED_TRACK"
    PRODUCTION="${WATCH_PREFIX}production" AAB_SUFFIX="-watch" WATCH_ARG="watch "
}

# The versionCode of this app's build of version $1.
app_code() {
    local code
    code="$(code_of "$1")"
    [[ -z "$AAB_SUFFIX" ]] || code=$((code + WATCH_CODE_OFFSET))
    echo "$code"
}

upload() {
    local v code aab status
    v="$(version)"
    code="$(app_code "$v")"
    aab="$OUT/Twangle-$v$AAB_SUFFIX.aab"
    status="${1:-draft}"
    [[ $# -le 1 && ( "$status" == draft || "$status" == completed ) ]] ||
        fail "usage: scripts/release.sh upload [watch] [draft|completed]"

    need_play_key
    [[ -f "$aab" ]] || fail "no $aab: make it first with scripts/release.sh bundle"
    # The notes are the phone's versionCode's, for both apps.
    need_release "$v" "$(code_of "$v")"
    use_java
    "$JAVA_HOME/bin/jarsigner" -verify "$aab" | grep -q "jar verified" || fail "$aab is not signed"

    echo "Sending $APP_NAME $v (versionCode $code) to the internal track${WATCH_ARG:+ ($INTERNAL)} on Google Play, as $status..."
    play_send commit :app:publishReleaseBundle \
        --artifact-dir "$aab" \
        --track "$INTERNAL" \
        --release-status "$status" \
        --release-name "$v ($code)" ||
        fail "$APP_NAME $v didn't reach the internal track (above); nothing was sent."

    echo
    echo "$APP_NAME $v is on the internal track${WATCH_ARG:+ ($INTERNAL)} of Google Play, with its notes in every language."
    if [[ "$status" == draft ]]; then
        echo "It is a draft: in the Play Console, Test and release -> Testing -> Internal testing$([[ -n "$WATCH_ARG" ]] && echo " (Wear OS)"), open the"
        echo "draft release, then Next and Save and publish (docs/release.md)."
    fi
}

# The same build, already on Play, from the internal track (where every release goes first) to the
# closed test or to production. Nothing is built or uploaded again, so what people get is exactly what
# was checked.
promote() {
    local v code target="${1:-}" status="${2:-}" from to name first="" started
    local -a args
    v="$(version)"
    code="$(app_code "$v")"
    [[ $# -le 2 ]] || target=""
    case "$target" in
        closed)
            status="${status:-draft}"
            [[ "$status" == draft || "$status" == completed ]] ||
                fail "usage: scripts/release.sh promote [watch] closed [draft|completed]"
            from="$INTERNAL" to="$CLOSED" name="the closed test ($CLOSED)"
            args=(--release-status "$status")
            ;;
        production)
            [[ -z "$status" || "$status" == draft ]] || fail "usage: scripts/release.sh promote [watch] production [draft]
(production starts as a staged rollout to $FIRST_ROLLOUT %; raise it with scripts/release.sh rollout ${WATCH_ARG}<percent>)"
            from="$INTERNAL" to="$PRODUCTION" name="production${WATCH_ARG:+ ($PRODUCTION)}"
            if [[ "$status" == draft ]]; then
                args=(--release-status draft)
            elif ! started="$(play_read track-started "$PRODUCTION")" || [[ "$started" != yes && "$started" != no ]]; then
                fail "couldn't read whether production had a release before (above); nothing was sent."
            elif [[ "$started" == no ]]; then
                # The app's first release on production: Play allows no staged rollout for it (Play
                # Console Help, Release app updates with staged rollouts), so it goes to everyone.
                first=yes
                status="a release for everyone (the first on production: Play allows no staged rollout for it)"
                args=(--release-status completed)
            else
                status="a staged rollout to $FIRST_ROLLOUT %"
                args=(--release-status inProgress --user-fraction "$(fraction "$FIRST_ROLLOUT")")
            fi
            ;;
        *) fail "usage: scripts/release.sh promote [watch] closed [draft|completed] | promote [watch] production [draft]" ;;
    esac

    need_play_key
    need_release "$v" "$(code_of "$v")"

    echo "Moving $APP_NAME $v (versionCode $code) from $from to $name on Google Play, as $status..."
    play_send commit :app:promoteReleaseArtifact \
        --from-track "$from" \
        --promote-track "$to" \
        --version-code "$code" \
        --release-name "$v ($code)" \
        "${args[@]}" ||
        fail "$APP_NAME $v didn't reach $name (above); nothing was changed."

    echo
    echo "$APP_NAME $v is on $name on Google Play, as $status."
    if [[ "$status" == draft ]]; then
        echo "It is a draft: in the Play Console, Edit track, Next, Save, Go to overview, then Submit for review (docs/release.md)."
    elif [[ -n "$first" ]]; then
        echo "Google reviews it first (a few hours to a few days), then it waits for Publish changes. Later"
        echo "versions start as a staged rollout to $FIRST_ROLLOUT %."
    elif [[ "$to" == "$PRODUCTION" ]]; then
        echo "Google reviews it first (a few hours to a few days). After two or three quiet days, raise it"
        echo "with scripts/release.sh rollout ${WATCH_ARG}50, later rollout ${WATCH_ARG}100; something wrong: scripts/release.sh halt${WATCH_ARG:+ watch}."
    fi
}

# Raises (or sets) this version's staged rollout on production; 100 % ends it.
rollout() {
    local v code percent="${1:-}"
    local -a args
    v="$(version)"
    code="$(app_code "$v")"
    [[ $# -le 1 && "$percent" =~ ^[0-9]{1,3}$ ]] && (( 10#$percent >= 1 && 10#$percent <= 100 )) ||
        fail "usage: scripts/release.sh rollout [watch] <percent>, a whole number from 1 to 100 (100 is everyone)"
    percent=$((10#$percent))
    if (( percent == 100 )); then
        args=(--release-status completed)
    else
        args=(--release-status inProgress --user-fraction "$(fraction "$percent")")
    fi

    need_play_key
    need_release "$v" "$(code_of "$v")"

    echo "Setting the rollout of $APP_NAME $v (versionCode $code) on production${WATCH_ARG:+ ($PRODUCTION)} to $percent %..."
    play_send commit :app:promoteReleaseArtifact \
        --update "$PRODUCTION" \
        --version-code "$code" \
        --release-name "$v ($code)" \
        "${args[@]}" ||
        fail "the rollout wasn't changed (above)."

    echo
    if (( percent == 100 )); then
        echo "$APP_NAME $v now goes to everyone on production; the staged rollout is over."
    else
        echo "$APP_NAME $v now goes to $percent % of people on production."
    fi
}

# Stops this version's rollout on production. The fraction it had stays (the plugin keeps it).
halt() {
    local v code
    v="$(version)"
    code="$(app_code "$v")"
    [[ $# -eq 0 ]] || fail "usage: scripts/release.sh halt [watch]"

    need_play_key
    need_release "$v" "$(code_of "$v")"

    echo "Halting the rollout of $APP_NAME $v (versionCode $code) on production${WATCH_ARG:+ ($PRODUCTION)}..."
    # The plugin's own commit, not play_send: the brake must work while something is in review, even
    # though Google then cancels that review and starts it again.
    play_gradle :app:promoteReleaseArtifact \
        --update "$PRODUCTION" \
        --version-code "$code" \
        --release-name "$v ($code)" \
        --release-status halted

    echo
    echo "$APP_NAME $v is halted on production: nobody new gets it, who has it keeps it. Fix it on main and"
    echo "make the next release (the fix gets a higher version). A false alarm: scripts/release.sh rollout"
    echo "${WATCH_ARG}<percent> starts it again."
}

# Width, height and PNG colour type of a picture, from its header: "1080 1920 6"; empty if not a PNG.
png_info() {
    local b
    read -ra b < <(od -An -tu1 -N26 "$1" | tr -s ' \n' ' ')
    [[ ${#b[@]} -eq 26 && "${b[1]} ${b[2]} ${b[3]}" == "80 78 71" ]] || return 0
    echo "$(( b[16] << 24 | b[17] << 16 | b[18] << 8 | b[19] )) $(( b[20] << 24 | b[21] << 16 | b[22] << 8 | b[23] )) ${b[25]}"
}

# Fails, naming every language and picture that is wrong, unless every language has its phone
# screenshots, 1.png ... one for each caption, within Play's rules. A language without them would show
# English screenshots on Play, so none is sent until all are there. The 10-inch tablet screenshots
# (tenInchScreenshots, Phase 52) and the watch's (wearScreenshots, Phase 88; here only from the watch's turn
# on Play, until then they wait in fastlane/held-back/) are checked against Play's rule for them, and once any
# language has them every language needs as many: the plugin would fill a language without them with the
# English ones.
need_screenshots() {
    local locale want dir file n w h info count kind made oldest="" problems=() missing=() counts=()
    for kind in "tenInchScreenshots:the tablet connected: scripts/store-screenshots.sh" \
        "wearScreenshots:the watch connected: scripts/watch-screenshots.sh"; do
        made="${kind#*:}" kind="${kind%%:*}" counts=()
        for locale in $(locales); do
            dir="$LISTINGS/$locale/images/$kind"
            # A PC that never made the set has no such folder (find fails, and pipefail would end the script).
            count=0
            [[ -d "$dir" ]] && count="$(find "$dir" -maxdepth 1 -name '*.png' | wc -l)"
            counts+=("$locale:$count")
        done
        if printf '%s\n' "${counts[@]}" | grep -qv ':0$' &&
            [[ "$(printf '%s\n' "${counts[@]}" | sed 's/.*://' | sort -u | wc -l)" -ne 1 ]]; then
            problems+=("not every language has the same $kind ($(printf '%s ' "${counts[@]}")); make them with $made")
        fi
    done
    for locale in $(locales); do
        want="$(grep -c . "$LISTINGS/$locale/screenshot_captions.txt" 2>/dev/null || true)"
        dir="$LISTINGS/$locale/images/phoneScreenshots"
        for n in $(seq 1 "${want:-0}"); do
            [[ -f "$dir/$n.png" ]] || { missing+=("$locale"); continue 2; }
        done
        for dir in "$LISTINGS/$locale"/images/*Screenshots; do
            [[ -d "$dir" ]] || continue
            n="$(find "$dir" -maxdepth 1 \( -name '*.png' -o -name '*.jpg' \) | wc -l)"
            (( n >= 2 && n <= 8 )) || problems+=("$dir: $n pictures, Play takes 2 to 8")
            for file in "$dir"/*.png; do
                [[ -f "$file" ]] || continue
                info="$(png_info "$file")"
                [[ -n "$info" ]] || { problems+=("$file is not a PNG"); continue; }
                read -r w h _ <<<"$info"
                if [[ "$dir" == */tenInchScreenshots ]]; then
                    # Play's 10-inch tablets: 16:9 or 9:16, each side 1080 to 7680 px.
                    if (( w < 1080 || h < 1080 || w > 7680 || h > 7680 || (w * 9 != h * 16 && w * 16 != h * 9) )); then
                        problems+=("$file is $w x $h px; Play's 10-inch tablet screenshots are 16:9 or 9:16, 1080 to 7680 px a side")
                    fi
                elif [[ "$dir" == */wearScreenshots ]]; then
                    # Play's Wear OS screenshots: square, at least 384 px a side (3840 at most, as every screenshot).
                    if (( w != h || w < 384 || w > 3840 )); then
                        problems+=("$file is $w x $h px; Play's Wear OS screenshots are square, 384 to 3840 px a side")
                    fi
                # Play: each side 320 to 3840 px, the long side at most twice the short one, at most 8 MB.
                elif (( w < 320 || h < 320 || w > 3840 || h > 3840 || w > 2 * h || h > 2 * w )); then
                    problems+=("$file is $w x $h px; Play takes 320 to 3840 px a side, at most 2:1")
                fi
                (( $(stat -c %s "$file") <= 8 * 1024 * 1024 )) || problems+=("$file is over Play's 8 MB")
                [[ -z "$oldest" || "$file" -ot "$oldest" ]] && oldest="$file"
            done
        done
    done
    if [[ ${#missing[@]} -gt 0 ]]; then
        fail "no phone screenshots (1.png ... 8.png) for: ${missing[*]}
They are made on the phone (git-ignored), and the other PC shares its set on GitHub only after a run
that made them all. Connect the phone and run
  scripts/store-screenshots.sh ${missing[*]}
(about 2 minutes a language; without arguments it makes every language). Nothing was sent to Google Play."
    fi
    [[ ${#problems[@]} -eq 0 ]] || fail "the screenshots don't meet Play's rules:
$(printf '  %s\n' "${problems[@]}")
Make them again with scripts/store-screenshots.sh (the watch's with scripts/watch-screenshots.sh). Nothing was sent to Google Play."
    echo "Screenshots for every language; the oldest made $(date -r "$oldest" '+%Y-%m-%d %H:%M')."
}

# Which store pictures a commit can change ("all" unless said): the lines for its pull request (its subject's
# "(#N)") in scripts/store-picture-scopes.txt, else its message's "Store-pictures:" lines, one scope a line,
# each "none", "phone", "tablet", picture numbers, or a set and numbers ("tablet 6"; numbers alone are both sets).
STORE_PICTURE_SCOPES=scripts/store-picture-scopes.txt
picture_scopes() {
    local pr scopes=""
    pr="$(git log -1 --format=%s "$1" | sed -n 's/.*(#\([0-9]*\))$/\1/p')"
    [[ -n "$pr" && -f "$STORE_PICTURE_SCOPES" ]] &&
        scopes="$(sed -n "s/^#$pr[[:space:]]\+\(\(none\|phone\|tablet\|[1-8]\)\([ ,]\+\(phone\|tablet\|[1-8]\)\)*\)\([[:space:]].*\)\?$/\1/p" "$STORE_PICTURE_SCOPES")"
    [[ -z "$scopes" ]] && scopes="$(git log -1 --format=%B "$1" | sed -n 's/^Store-pictures:[[:space:]]*//p')"
    echo "${scopes:-all}"
}

# Whether a commit can change picture [n] of the [kind] set (phone or tablet), by its scopes.
changes_picture() {
    local commit="$1" kind="$2" n="$3" scope word kinds nums
    while read -r scope; do
        [[ -z "$scope" || "$scope" == all ]] && return 0
        kinds="" nums=""
        for word in ${scope//,/ }; do
            case "$word" in
                none) ;;
                phone|tablet) kinds+=" $word" ;;
                [1-8]) nums+=" $word" ;;
                *) return 0 ;;   # a word it doesn't know: every picture, to be safe
            esac
        done
        [[ "$scope" =~ (^|[[:space:],])none($|[[:space:],]) ]] && continue
        { [[ -z "$kinds" ]] || [[ " $kinds " == *" $kind "* ]]; } &&
            { [[ -z "$nums" ]] || [[ " $nums " == *" $n "* ]]; } && return 0
    done <<< "$(picture_scopes "$commit")"
    return 1
}

# Whether the phone screenshots show the app as it is now: for each picture, the commit scripts/store-screenshots.sh
# noted after a run of every language (build/phone-screenshots.made-at, one line a picture, shared with the set on
# GitHub; a note of before 2026-11-09 has one line for all eight) has no change since to what they show
# (SCREENSHOT_SOURCES) that can change that picture (picture_scopes). Says which, with the command that makes
# just those again, and returns 1 when not. They are made before a listing is sent, not every night (owner,
# 2026-10-30), so this is what keeps a listing from going out with pictures of an older app.
# Whether the watch screenshots, once they are in the listing (the watch's turn on Play), show the watch app as
# it is now: the commit scripts/watch-screenshots.sh noted after a run of every language (build/watch-screenshots.made-at,
# shared with the set) has no change since to what they show (WATCH_SCREENSHOT_SOURCES). A run of every language takes
# a few minutes, so any such change counts, with no scopes. Nothing to check (0) while they are held back.
watch_screenshots_current() {
    local made_at="build/watch-screenshots.made-at" commit state when changes
    [[ -n "$(find "$LISTINGS" -path '*/images/wearScreenshots/*.png' -print -quit)" ]] || return 0
    { [[ -f "$made_at" ]] && read -r commit state when < "$made_at"; } || {
        echo "Which version of the watch app its screenshots show isn't known (no $made_at): make them again with"
        echo "the watch connected and awake, scripts/watch-screenshots.sh (every language, a few minutes)."
        return 1
    }
    if ! git cat-file -e "$commit^{commit}" 2>/dev/null; then
        changes="made at ${commit:0:8}, a commit this clone doesn't have"
    elif [[ "$state" != clean ]]; then
        changes="made from uncommitted changes"
    else
        changes="$(git log --format='%h %s' "$commit..HEAD" -- "${WATCH_SCREENSHOT_SOURCES[@]}" | head -5)"
    fi
    if [[ -z "$changes" ]]; then
        echo "The watch screenshots show the watch app as it is now (made $when)."
        return 0
    fi
    echo "The watch screenshots (made $when) are older than what they show:"
    sed 's/^/  /' <<< "$changes"
    echo "Make them again with the watch connected and awake: scripts/watch-screenshots.sh (every language, a few minutes)."
    return 1
}

screenshots_current() {
    local made_at="build/phone-screenshots.made-at" note n line commit state when c stale=() reasons=() newest=""
    if [[ ! -f "$made_at" ]]; then
        echo "Which version of the app the phone screenshots show isn't known (no $made_at: made before 2026-10-30, or only some languages since)."
        echo "Make them again with the phone connected: scripts/store-screenshots.sh (every language, about 50 minutes)."
        return 1
    fi
    note="$(cat "$made_at")"
    grep -qE '^[1-8] ' <<< "$note" || note="$(for n in 1 2 3 4 5 6 7 8; do echo "$n $note"; done)"
    for n in 1 2 3 4 5 6 7 8; do
        line="$(grep -E "^$n " <<< "$note" | head -1)"
        read -r _ commit state when <<< "$line"
        if [[ -z "$line" ]]; then
            stale+=("$n"); reasons+=("picture $n: not made by a run of every language")
        elif ! git cat-file -e "$commit^{commit}" 2>/dev/null; then
            stale+=("$n"); reasons+=("picture $n: made ($when) at ${commit:0:8}, a commit this clone doesn't have")
        elif [[ "$state" != clean ]]; then
            stale+=("$n"); reasons+=("picture $n: made ($when) from uncommitted changes")
        else
            for c in $(git log --format=%H "$commit..HEAD" -- "${SCREENSHOT_SOURCES[@]}"); do
                if changes_picture "$c" phone "$n"; then
                    stale+=("$n"); reasons+=("picture $n (made $when, at ${commit:0:8}): $(git log -1 --format='%h %s' "$c")")
                    break
                fi
            done
            [[ -z "$newest" || "$when" > "$newest" ]] && newest="$when"
        fi
    done
    if [[ ${#stale[@]} -eq 0 ]]; then
        echo "The phone screenshots show the app as it is now (made by $newest at the latest)."
        return 0
    fi
    echo "The phone screenshots are older than what they show, for ${#stale[@]} of the 8 pictures:"
    printf '  %s\n' "${reasons[@]}"
    if [[ ${#stale[@]} -eq 8 ]]; then
        echo "Make them again with the phone connected: scripts/store-screenshots.sh (every language, about 50 minutes)."
    else
        echo "Make those again with the phone connected: scripts/store-screenshots.sh --shots $(IFS=,; echo "${stale[*]}") (every language)."
    fi
    return 1
}

# The store listing as it is on main, for every language, with the app details.
listing() {
    [[ $# -eq 0 ]] || fail "usage: scripts/release.sh listing"
    need_play_key
    [[ "$(git rev-parse --abbrev-ref HEAD)" == "main" ]] || fail "not on main; send only the listing that has landed"
    [[ -z "$(git status --porcelain -- "$LISTINGS")" ]] || fail "uncommitted changes in $LISTINGS; land them first"
    git fetch --quiet origin main
    [[ "$(git rev-parse HEAD)" == "$(git rev-parse origin/main)" ]] || fail "main differs from origin/main; git pull first"
    # The screenshots are git-ignored: take the latest set the owner's other PC shared on GitHub, if newer.
    scripts/share-store-screenshots.sh pull phone || true
    scripts/share-store-screenshots.sh pull tablet || true
    scripts/share-store-screenshots.sh pull watch || true
    need_screenshots
    screenshots_current || fail "make the phone screenshots again first (above), look them over, then send. Nothing was sent to Google Play."
    watch_screenshots_current || fail "make the watch screenshots again first (above), look them over, then send. Nothing was sent to Google Play."
    use_java

    # Titles, descriptions and captions within Play's limits, the icon and feature graphic at Play's
    # sizes and formats (docs/store-listing.md).
    echo "Checking the texts, the icon and the feature graphic (StoreListingTest)..."
    ./gradlew --quiet :app:testDebugUnitTest --tests '*.StoreListingTest' ||
        fail "the store listing doesn't meet Play's rules (StoreListingTest above). Nothing was sent to Google Play."

    echo "Sending the store listing in $(locales | wc -l) languages to Google Play..."
    # Each part is committed "not sent for review" (play_send), so the listing waits in the Play Console's
    # Publishing overview until the owner sends it with the release it goes with: the plugin's own
    # commit sends the changes to Google's review at once, managed publishing or not (2026-11-09).
    into_play() {
        play_send commit-unsent :app:publishReleaseListing --rerun "$@" ||
            fail "this part of the store listing didn't reach Google Play (above); a part sent before it waits, not sent for review."
    }
    # --rerun sends everything, also what an earlier run sent: the Play Console may have been edited
    # since. Pictures Play already has (the same file) are skipped by the plugin.
    # Texts first: Play takes a language's pictures only once that language has a listing, and the
    # plugin sends both side by side (app/build.gradle.kts, playListingTextsOnly).
    echo "  1/2: the texts and app details..."
    into_play -Ptwangle.playListingTextsOnly=true
    echo "  2/2: the screenshots, icon and feature graphic..."
    into_play

    # The mark listing-changes compares with next time.
    local mark="listing/$(date '+%Y-%m-%d-%H%M')"
    git tag -a "$mark" -m "Store listing sent to Google Play" && git push --quiet origin "$mark" ||
        echo "(The send isn't noted on GitHub: git push origin $mark.)"

    echo
    echo "The store listing is on Google Play in every language: title, short and full description,"
    echo "screenshots, icon and feature graphic, and the contact email, website and default language."
    echo "It is not sent for review: in the Play Console, Publishing overview, it waits under Changes not"
    echo "yet sent for review; send it with the release it goes with, so both are one review (docs/release.md)."
}

# What changed for the store listing since it was last sent (the newest listing/* tag): its texts and
# pictures in git, and the app's screens, which the git-ignored screenshots show.
listing_changes() {
    local mark texts screens
    [[ $# -eq 0 ]] || fail "usage: scripts/release.sh listing-changes"
    git fetch --quiet origin --tags 2>/dev/null || true
    mark="$(git tag -l 'listing/*' --sort=-creatordate | head -1)"
    if [[ -z "$mark" ]]; then
        echo "No store listing sent from this repo yet (no listing/* tag): send it with scripts/release.sh listing."
        return
    fi
    echo "The store listing was last sent at $mark (commit $(git rev-parse --short "$mark^{commit}"))."
    texts="$(git diff --stat "$mark" HEAD -- "$LISTINGS" ":(exclude,glob)$LISTINGS/*/changelogs/**")"
    if [[ -n "$texts" ]]; then
        echo "Its texts or pictures changed since (a new language shows as new files):"
        echo "$texts"
    else
        echo "Its texts and pictures are unchanged since."
    fi
    screens="$(git log --oneline "$mark..HEAD" -- "${SCREENSHOT_SOURCES[@]}")"
    if [[ -n "$screens" ]]; then
        echo "Changes to the app's screens since:"
        echo "$screens" | head -20
    else
        echo "The app's screens are unchanged since."
    fi
    screenshots_current || echo "listing refuses them until then."
    watch_screenshots_current || echo "listing refuses them until then."
}

# Runs one of the read-only looks at Play (scripts/PlayReports.java, plain JDK, no Gradle).
play_read() {
    local key="$PLAY_KEY" script="scripts/PlayReports.java"
    need_play_key
    use_java
    # Windows' java wants Windows paths; Git Bash's /c/... are not.
    if command -v cygpath >/dev/null; then key="$(cygpath -m "$key")"; script="$(cygpath -m "$ROOT/$script")"; fi
    "$JAVA_HOME/bin/java" "$script" "$@" "$key" "$APP_ID"
}

status() {
    [[ $# -eq 0 ]] || fail "usage: scripts/release.sh status"
    play_read status
}

health() {
    local days="${1:-7}"
    [[ $# -le 1 && "$days" =~ ^[0-9]{1,2}$ ]] && (( 10#$days >= 1 && 10#$days <= 90 )) ||
        fail "usage: scripts/release.sh health [days], 1 to 90 days (7 by default)"
    play_read health "$((10#$days))"
}

reviews() {
    local language="${1:-en}"
    [[ $# -le 1 && "$language" =~ ^[a-z]{2,3}(-[A-Za-z0-9]{2,8})?$ ]] ||
        fail "usage: scripts/release.sh reviews [language], e.g. en, nl or pt-BR (en by default)"
    play_read reviews "$language"
}

COMMAND="${1:-}"
[[ $# -eq 0 ]] || shift
# "watch" right after a Play step: the watch app's bundle on its Wear OS tracks.
case "$COMMAND" in
    upload|promote|rollout|halt) if [[ "${1:-}" == watch ]]; then for_watch; shift; fi ;;
esac
case "$COMMAND" in
    bump) bump "${1:-}" ;;
    notes) notes_block "$(code_of "$(version)")" ;;
    profile) [[ $# -eq 0 ]] || fail "usage: scripts/release.sh profile"; profile_current ;;
    full-check) [[ $# -eq 0 ]] || fail "usage: scripts/release.sh full-check"; full_check_passed ;;
    bundle) bundle "$@" ;;
    upload) upload "$@" ;;
    promote) promote "$@" ;;
    rollout) rollout "$@" ;;
    halt) halt "$@" ;;
    listing) listing "$@" ;;
    listing-changes) listing_changes "$@" ;;
    status) status "$@" ;;
    health) health "$@" ;;
    reviews) reviews "$@" ;;
    *) fail "usage: scripts/release.sh bump patch|minor|major | notes | profile | full-check
       | bundle [--keep-old-profile] [--watch-untested]
       | upload [watch] [draft|completed] | promote [watch] closed [draft|completed]
       | promote [watch] production [draft] | rollout [watch] <percent> | halt [watch] | listing
       | listing-changes | status | health [days] | reviews [language]" ;;
esac
