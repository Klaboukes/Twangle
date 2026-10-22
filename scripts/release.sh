#!/usr/bin/env bash
# Twangle's release routine for Google Play (docs/release.md, the release skill).
#
#   scripts/release.sh bump patch|minor|major
#       Raises twangle.version in gradle.properties (1.0.0 -> 1.0.1 | 1.1.0 | 2.0.0) and says which
#       release-notes files to write: fastlane/metadata/android/<Play locale>/changelogs/<versionCode>.txt.
#   scripts/release.sh notes
#       Prints the release notes of the current version as one block for the Play Console's
#       "Release notes" box (<en-US> ... </en-US> and so on for every language).
#   scripts/release.sh bundle
#       On main, once the version bump has landed: builds the phone app's bundle signed with the upload
#       key, checks the signature, puts it with its notes in release/ (git-ignored), then tags the commit
#       v<version> and pushes the tag. Refuses a version that already has a tag.
#   scripts/release.sh upload [draft|completed]
#       After bundle: sends release/Twangle-<version>.aab and its notes in every language to the
#       internal track on Google Play, through the Google Play API (Gradle Play Publisher in :app). As a
#       draft by default, the only kind Play takes while Twangle itself is still a draft there: the owner
#       starts it in the Play Console with one click. "completed" makes it live on the internal track at
#       once (for later, once the app is out of draft). Needs the robot user's key (a service account):
#       ~/.twangle-keys/play-service-account.json, or the file in $TWANGLE_PLAY_SERVICE_ACCOUNT.
#   scripts/release.sh promote closed [draft|completed]
#       Once the owner has checked the internal build: moves that same build (this version) from the
#       internal track to the closed test (Play's track "alpha", or $TWANGLE_PLAY_CLOSED_TRACK) with its
#       notes. A draft by default, as upload; "completed" makes it live for the testers at once.
#   scripts/release.sh promote production [draft]
#       Moves this version from the internal track to production (whether or not it went to the closed
#       test too) as a staged rollout to 20 % of people (Google reviews it first). "draft" leaves it a
#       draft release to start in the Play Console.
#   scripts/release.sh rollout <percent>
#       Raises this version's staged rollout on production to <percent> (1..100); 100 is everyone and
#       ends the staged rollout.
#   scripts/release.sh halt
#       Halts this version's rollout on production: nobody new gets it, who has it keeps it.
#   scripts/release.sh listing
#       Sends the store listing to Google Play: every language's title, short and full description and
#       phone screenshots, the icon and feature graphic (one pair, shown for every language), and the
#       app details (default language en-US, contact email, website). From fastlane/metadata/android/
#       as it is on main. Refuses unless every language has its screenshots (scripts/store-screenshots.sh;
#       git-ignored, so made on this PC), and checks the texts and pictures against Play's rules first.
#
# upload, promote, rollout, halt and listing change what people get or see on Google Play, so Claude
# asks the owner before running any of them (docs/release.md). They act on the version in gradle.properties, which
# must be released (tagged), and ask nothing themselves.
#
# The watch app is not part of a release yet (it follows in an update, decision 3): none of these
# touches it.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
LISTINGS="fastlane/metadata/android"
OUT="release"
PLAY_KEY="${TWANGLE_PLAY_SERVICE_ACCOUNT:-$HOME/.twangle-keys/play-service-account.json}"
# Play's API name of the closed test the Play Console makes first ("Closed testing - Alpha").
CLOSED_TRACK="${TWANGLE_PLAY_CLOSED_TRACK:-alpha}"
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

bump() {
    local current major minor patch next
    current="$(version)"
    IFS=. read -r major minor patch <<<"$current"
    case "${1:-}" in
        patch) patch=$((10#$patch + 1)) ;;
        minor) minor=$((10#$minor + 1)); patch=0 ;;
        major) major=$((10#$major + 1)); minor=0; patch=0 ;;
        *) fail "usage: scripts/release.sh bump patch|minor|major" ;;
    esac
    (( major <= 99 && minor <= 99 && patch <= 99 )) || fail "each part of the version stays 0..99"
    next="$major.$minor.$patch"
    sed -i "s/^twangle.version=.*/twangle.version=$next/" gradle.properties
    echo "Version $current -> $next (versionCode $(code_of "$next"))."
    echo "Write the release notes, one file a language: $LISTINGS/<locale>/changelogs/$(code_of "$next").txt"
}

# Gradle runs on Android Studio's JDK (CLAUDE.md, Dev machine environment).
use_java() {
    if [[ -z "${JAVA_HOME:-}" && -d "/c/Program Files/Android/Android Studio/jbr" ]]; then
        export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
    fi
    [[ -n "${JAVA_HOME:-}" ]] || fail "JAVA_HOME is not set (CLAUDE.md, Dev machine environment)"
}

bundle() {
    local v code tag aab
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
    use_java

    echo "Building Twangle $v (versionCode $code)..."
    ./gradlew --quiet :app:bundleRelease
    aab="app/build/outputs/bundle/release/app-release.aab"
    [[ -f "$aab" ]] || fail "no bundle at $aab"
    # Play takes only a bundle signed with the upload key; without ~/.twangle-keys the build is unsigned.
    "$JAVA_HOME/bin/jarsigner" -verify "$aab" | grep -q "jar verified" ||
        fail "the bundle is not signed: is the upload key in ~/.twangle-keys/ (docs/architecture/performance.md)?"

    mkdir -p "$OUT"
    cp "$aab" "$OUT/Twangle-$v.aab"
    notes_block "$code" >"$OUT/Twangle-$v-release-notes.txt"

    git tag -a "$tag" -m "Twangle $v (versionCode $code)"
    git push --quiet origin "$tag"

    echo
    echo "Twangle $v is ready for the Play Console (docs/release.md):"
    echo "  bundle:        $OUT/Twangle-$v.aab"
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

# A whole percent as Play's user fraction: 20 -> 0.20.
fraction() { printf '%d.%02d' $(($1 / 100)) $(($1 % 100)); }

upload() {
    local v code aab status
    v="$(version)"
    code="$(code_of "$v")"
    aab="$OUT/Twangle-$v.aab"
    status="${1:-draft}"
    [[ "$status" == draft || "$status" == completed ]] || fail "usage: scripts/release.sh upload [draft|completed]"

    need_play_key
    [[ -f "$aab" ]] || fail "no $aab: make it first with scripts/release.sh bundle"
    need_release "$v" "$code"
    use_java
    "$JAVA_HOME/bin/jarsigner" -verify "$aab" | grep -q "jar verified" || fail "$aab is not signed"

    echo "Sending Twangle $v (versionCode $code) to the internal track on Google Play, as $status..."
    play_gradle :app:publishReleaseBundle \
        --artifact-dir "$aab" \
        --track internal \
        --release-status "$status" \
        --release-name "$v ($code)"

    echo
    echo "Twangle $v is on the internal track of Google Play, with its notes in every language."
    if [[ "$status" == draft ]]; then
        echo "It is a draft: in the Play Console, Test and release -> Testing -> Internal testing, open the"
        echo "draft release, then Next and Save and publish (docs/release.md)."
    fi
}

# The same build, already on Play, from the internal track (where every release goes first) to the
# closed test or to production. Nothing is built or uploaded again, so what people get is exactly what
# was checked.
promote() {
    local v code target="${1:-}" status="${2:-}" from to name
    local -a args
    v="$(version)"
    code="$(code_of "$v")"
    case "$target" in
        closed)
            status="${status:-draft}"
            [[ "$status" == draft || "$status" == completed ]] ||
                fail "usage: scripts/release.sh promote closed [draft|completed]"
            from=internal to="$CLOSED_TRACK" name="the closed test"
            args=(--release-status "$status")
            ;;
        production)
            [[ -z "$status" || "$status" == draft ]] || fail "usage: scripts/release.sh promote production [draft]
(production starts as a staged rollout to $FIRST_ROLLOUT %; raise it with scripts/release.sh rollout <percent>)"
            from=internal to=production name="production"
            if [[ "$status" == draft ]]; then
                args=(--release-status draft)
            else
                status="a staged rollout to $FIRST_ROLLOUT %"
                args=(--release-status inProgress --user-fraction "$(fraction "$FIRST_ROLLOUT")")
            fi
            ;;
        *) fail "usage: scripts/release.sh promote closed [draft|completed] | promote production [draft]" ;;
    esac

    need_play_key
    need_release "$v" "$code"

    echo "Moving Twangle $v (versionCode $code) from $from to $name on Google Play, as $status..."
    play_gradle :app:promoteReleaseArtifact \
        --from-track "$from" \
        --promote-track "$to" \
        --version-code "$code" \
        --release-name "$v ($code)" \
        "${args[@]}"

    echo
    echo "Twangle $v is on $name on Google Play, as $status."
    if [[ "$status" == draft ]]; then
        echo "It is a draft: open it in the Play Console under Test and release, then Next and Save and publish."
    elif [[ "$to" == production ]]; then
        echo "Google reviews it first (a few hours to a few days). After two or three quiet days, raise it"
        echo "with scripts/release.sh rollout 50, later rollout 100; something wrong: scripts/release.sh halt."
    fi
}

# Raises (or sets) this version's staged rollout on production; 100 % ends it.
rollout() {
    local v code percent="${1:-}"
    local -a args
    v="$(version)"
    code="$(code_of "$v")"
    [[ "$percent" =~ ^[0-9]{1,3}$ ]] && (( 10#$percent >= 1 && 10#$percent <= 100 )) ||
        fail "usage: scripts/release.sh rollout <percent>, a whole number from 1 to 100 (100 is everyone)"
    percent=$((10#$percent))
    if (( percent == 100 )); then
        args=(--release-status completed)
    else
        args=(--release-status inProgress --user-fraction "$(fraction "$percent")")
    fi

    need_play_key
    need_release "$v" "$code"

    echo "Setting the rollout of Twangle $v (versionCode $code) on production to $percent %..."
    play_gradle :app:promoteReleaseArtifact \
        --update production \
        --version-code "$code" \
        --release-name "$v ($code)" \
        "${args[@]}"

    echo
    if (( percent == 100 )); then
        echo "Twangle $v now goes to everyone on production; the staged rollout is over."
    else
        echo "Twangle $v now goes to $percent % of people on production."
    fi
}

# Stops this version's rollout on production. The fraction it had stays (the plugin keeps it).
halt() {
    local v code
    v="$(version)"
    code="$(code_of "$v")"
    [[ $# -eq 0 ]] || fail "usage: scripts/release.sh halt"

    need_play_key
    need_release "$v" "$code"

    echo "Halting the rollout of Twangle $v (versionCode $code) on production..."
    play_gradle :app:promoteReleaseArtifact \
        --update production \
        --version-code "$code" \
        --release-name "$v ($code)" \
        --release-status halted

    echo
    echo "Twangle $v is halted on production: nobody new gets it, who has it keeps it. Fix it on main and"
    echo "make the next release (the fix gets a higher version). A false alarm: scripts/release.sh rollout"
    echo "<percent> starts it again."
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
# English screenshots on Play, so none is sent until all are there. Tablet screenshots, where there are
# any (none yet: Phase 42), are checked the same way and sent too.
need_screenshots() {
    local locale want dir file n w h info oldest="" problems=() missing=()
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
                # Play: each side 320 to 3840 px, the long side at most twice the short one, at most 8 MB.
                if (( w < 320 || h < 320 || w > 3840 || h > 3840 || w > 2 * h || h > 2 * w )); then
                    problems+=("$file is $w x $h px; Play takes 320 to 3840 px a side, at most 2:1")
                fi
                (( $(stat -c %s "$file") <= 8 * 1024 * 1024 )) || problems+=("$file is over Play's 8 MB")
                [[ -z "$oldest" || "$file" -ot "$oldest" ]] && oldest="$file"
            done
        done
    done
    if [[ ${#missing[@]} -gt 0 ]]; then
        fail "no phone screenshots (1.png ... 8.png) for: ${missing[*]}
They are made on the phone and kept only on this PC (git-ignored): connect the phone and run
  scripts/store-screenshots.sh ${missing[*]}
(about 2 minutes a language; without arguments it makes every language). Nothing was sent to Google Play."
    fi
    [[ ${#problems[@]} -eq 0 ]] || fail "the screenshots don't meet Play's rules:
$(printf '  %s\n' "${problems[@]}")
Make them again with scripts/store-screenshots.sh. Nothing was sent to Google Play."
    echo "Screenshots for every language; the oldest made $(date -r "$oldest" '+%Y-%m-%d %H:%M')."
    echo "(If the app looks different since, make them again first: scripts/store-screenshots.sh.)"
}

# The store listing as it is on main, for every language, with the app details.
listing() {
    [[ $# -eq 0 ]] || fail "usage: scripts/release.sh listing"
    need_play_key
    [[ "$(git rev-parse --abbrev-ref HEAD)" == "main" ]] || fail "not on main; send only the listing that has landed"
    [[ -z "$(git status --porcelain -- "$LISTINGS")" ]] || fail "uncommitted changes in $LISTINGS; land them first"
    git fetch --quiet origin main
    [[ "$(git rev-parse HEAD)" == "$(git rev-parse origin/main)" ]] || fail "main differs from origin/main; git pull first"
    need_screenshots
    use_java

    # Titles, descriptions and captions within Play's limits, the icon and feature graphic at Play's
    # sizes and formats (docs/store-listing.md).
    echo "Checking the texts, the icon and the feature graphic (StoreListingTest)..."
    ./gradlew --quiet :app:testDebugUnitTest --tests '*.StoreListingTest' ||
        fail "the store listing doesn't meet Play's rules (StoreListingTest above). Nothing was sent to Google Play."

    echo "Sending the store listing in $(locales | wc -l) languages to Google Play..."
    # --rerun sends everything, also what an earlier run sent: the Play Console may have been edited
    # since. Pictures Play already has (the same file) are skipped by the plugin.
    # Texts first: Play takes a language's pictures only once that language has a listing, and the
    # plugin sends both side by side (app/build.gradle.kts, playListingTextsOnly).
    echo "  1/2: the texts and app details..."
    play_gradle :app:publishReleaseListing --rerun -Ptwangle.playListingTextsOnly=true
    echo "  2/2: the screenshots, icon and feature graphic..."
    play_gradle :app:publishReleaseListing --rerun

    echo
    echo "The store listing is on Google Play in every language: title, short and full description,"
    echo "screenshots, icon and feature graphic, and the contact email, website and default language."
    echo "Check it in the Play Console: Grow users -> Store presence -> Main store listing."
}

case "${1:-}" in
    bump) bump "${2:-}" ;;
    notes) notes_block "$(code_of "$(version)")" ;;
    bundle) bundle ;;
    upload) upload "${2:-}" ;;
    promote) promote "${2:-}" "${3:-}" ;;
    rollout) rollout "${2:-}" ;;
    halt) shift; halt "$@" ;;
    listing) shift; listing "$@" ;;
    *) fail "usage: scripts/release.sh bump patch|minor|major | notes | bundle | upload [draft|completed]
       | promote closed [draft|completed] | promote production [draft] | rollout <percent> | halt | listing" ;;
esac
