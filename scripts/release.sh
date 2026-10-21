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
#
# The watch app is not part of a release yet (it follows in an update, decision 3).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
LISTINGS="fastlane/metadata/android"
OUT="release"

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

    if [[ -z "${JAVA_HOME:-}" && -d "/c/Program Files/Android/Android Studio/jbr" ]]; then
        export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
    fi
    [[ -n "${JAVA_HOME:-}" ]] || fail "JAVA_HOME is not set (CLAUDE.md, Dev machine environment)"

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

case "${1:-}" in
    bump) bump "${2:-}" ;;
    notes) notes_block "$(code_of "$(version)")" ;;
    bundle) bundle ;;
    *) fail "usage: scripts/release.sh bump patch|minor|major | notes | bundle" ;;
esac
