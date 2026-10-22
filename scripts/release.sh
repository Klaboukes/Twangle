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
#
# The watch app is not part of a release yet (it follows in an update, decision 3): neither bundle nor
# upload touches it.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
LISTINGS="fastlane/metadata/android"
OUT="release"
PLAY_KEY="${TWANGLE_PLAY_SERVICE_ACCOUNT:-$HOME/.twangle-keys/play-service-account.json}"

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

upload() {
    local v code tag aab status key notes
    v="$(version)"
    code="$(code_of "$v")"
    tag="v$v"
    aab="$OUT/Twangle-$v.aab"
    status="${1:-draft}"
    [[ "$status" == draft || "$status" == completed ]] || fail "usage: scripts/release.sh upload [draft|completed]"

    # The key first: without it nothing else matters, and nothing is built or sent.
    [[ -f "$PLAY_KEY" ]] || fail "the Google Play key is missing ($PLAY_KEY).
It is the robot user's key (a service account) that lets this PC send releases to Google Play. It is
made once in Google Cloud and the Play Console, with Claude guiding (docs/release.md, Uploads through
the Google Play API), and kept only on the owner's machines. Nothing was uploaded."
    [[ -f "$aab" ]] || fail "no $aab: make it first with scripts/release.sh bundle"
    git rev-parse --quiet --verify "refs/tags/$tag" >/dev/null || fail "$tag is not tagged: make the bundle with scripts/release.sh bundle"
    # Every release has its notes in every language, except the very first (1.0.0, nothing to tell
    # what changed since), which goes without when it has none.
    if [[ -z "$(git tag -l 'v*' | grep -vx "$tag")" ]] &&
        ! compgen -G "$LISTINGS/*/changelogs/$code.txt" >/dev/null; then
        echo "The first release, without release notes."
    else
        notes_block "$code" >/dev/null
    fi
    # The notes sent are the working copy's; they must be the ones of the tagged release.
    notes="$LISTINGS/*/changelogs/$code.txt"
    [[ -z "$(git status --porcelain -- "$notes")" ]] && git diff --quiet "$tag" -- "$notes" ||
        fail "the release notes ($notes) differ from $tag; upload the notes that were released"
    use_java
    "$JAVA_HOME/bin/jarsigner" -verify "$aab" | grep -q "jar verified" || fail "$aab is not signed"

    # Gradle wants a Windows path for the key; Git Bash's /c/... is not one.
    key="$PLAY_KEY"
    if command -v cygpath >/dev/null; then key="$(cygpath -m "$key")"; fi

    echo "Sending Twangle $v (versionCode $code) to the internal track on Google Play, as $status..."
    ./gradlew --quiet :app:publishReleaseBundle \
        "-Ptwangle.playServiceAccount=$key" \
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

case "${1:-}" in
    bump) bump "${2:-}" ;;
    notes) notes_block "$(code_of "$(version)")" ;;
    bundle) bundle ;;
    upload) upload "${2:-}" ;;
    *) fail "usage: scripts/release.sh bump patch|minor|major | notes | bundle | upload [draft|completed]" ;;
esac
