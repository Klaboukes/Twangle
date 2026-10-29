#!/usr/bin/env bash
# Shares the store screenshots between the owner's PCs through GitHub (docs/store-listing.md, *Phone
# screenshots*). They are git-ignored, since a set is about 180 MB and is made again every night, so
# instead the latest set lives as a download on one fixed GitHub release, "store-screenshots", that
# keeps no history: each upload replaces the one before.
#
#   scripts/share-store-screenshots.sh push phone|tablet   upload this PC's set, if every store folder has it
#   scripts/share-store-screenshots.sh pull phone|tablet   download the set, if GitHub's is newer than this PC's
#
# scripts/store-screenshots.sh pushes after a run that made every picture, and pulls before a run of
# only some languages (so the others are the latest too); scripts/release.sh listing pulls before it
# sends. Neither stops when GitHub can't be reached: they say so and carry on with what this PC has.
# Exit codes: 0 done or nothing to do, 1 failed (said why), 2 usage.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
LISTINGS=fastlane/metadata/android
TAG=store-screenshots

case "${2:-}" in
    phone) FOLDER=phoneScreenshots ;;
    tablet) FOLDER=tenInchScreenshots ;;
    *) echo "usage: scripts/share-store-screenshots.sh push|pull phone|tablet" >&2; exit 2 ;;
esac
ASSET="$2.tar"
TAR="build/shared-store-screenshots/$ASSET"   # GitHub names the download after the file

# Every store folder's pictures of this kind, one a line ("<locale>/images/<folder>/<n>.png").
pictures() { (cd "$ROOT/$LISTINGS" && find . -path "*/images/$FOLDER/*.png" | sed 's|^\./||' | sort); }

# The newest picture of this kind on this PC, in seconds since 1970 (0 when there is none).
newest_here() { find "$LISTINGS" -path "*/images/$FOLDER/*.png" -printf '%T@\n' 2>/dev/null | sort -n | tail -1 | cut -d. -f1 | sed 's/^$/0/'; }

# When GitHub's set was uploaded, in seconds since 1970 (empty when there is none).
uploaded_at() {
    local when
    when="$(gh release view "$TAG" --json assets --jq ".assets[] | select(.name == \"$ASSET\") | .updatedAt" 2>/dev/null)" || return 0
    [[ -n "$when" ]] && date -d "$when" +%s
}

# True when every store folder has the same number of pictures of this kind, at least two.
complete_here() {
    local locale counts
    counts="$(for locale in $(find "$LISTINGS" -mindepth 1 -maxdepth 1 -type d -printf '%f\n'); do
        find "$LISTINGS/$locale/images/$FOLDER" -maxdepth 1 -name '*.png' 2>/dev/null | wc -l
    done | sort -u)"
    [[ "$(wc -l <<<"$counts")" -eq 1 && "$counts" -ge 2 ]]
}

# The release keeps its tag on the repository's first commit, so it never looks like a version
# (release.sh and git describe look for v* tags) or moves with main.
ensure_release() {
    gh release view "$TAG" > /dev/null 2>&1 && return 0
    gh release create "$TAG" --prerelease --target "$(git rev-list --max-parents=0 HEAD | tail -1)" \
        --title "Store screenshots" \
        --notes "The latest store screenshots, shared between the owner's PCs by scripts/share-store-screenshots.sh. Each upload replaces the one before; not an app version." > /dev/null
}

push() {
    complete_here || { echo "Not shared on GitHub: not every store folder has its $2 screenshots on this PC."; return 0; }
    mkdir -p "$(dirname "$TAR")"
    (cd "$LISTINGS" && pictures | tar -cf "$ROOT/$TAR" -T -) || { echo "Packing the $2 screenshots failed."; return 1; }
    echo "Sharing this PC's $2 screenshots on GitHub ($(pictures | wc -l) pictures, $(du -m "$TAR" | cut -f1) MB)..."
    ensure_release &&
        gh release upload "$TAG" "$TAR" --clobber > /dev/null ||
        { rm -f "$TAR"; echo "Sharing the $2 screenshots on GitHub failed; the other PC won't have this set (run: scripts/share-store-screenshots.sh push $2)."; return 1; }
    rm -f "$TAR"
    # Dated now, so this PC doesn't take its own upload for a newer set at the next pull.
    find "$LISTINGS" -path "*/images/$FOLDER/*.png" -exec touch {} +
    echo "Shared: the other PC gets them with scripts/share-store-screenshots.sh pull $2 (release.sh listing does it)."
}

pull() {
    local there here
    there="$(uploaded_at)"
    if [[ -z "$there" ]]; then
        echo "No $2 screenshots shared on GitHub (or GitHub can't be reached); using this PC's."
        return 0
    fi
    here="$(newest_here)"
    if complete_here && (( here >= there )); then
        echo "This PC's $2 screenshots are as new as GitHub's or newer; using them."
        return 0
    fi
    mkdir -p "$(dirname "$TAR")"
    echo "Getting the $2 screenshots from GitHub (shared $(date -d "@$there" '+%Y-%m-%d %H:%M'))..."
    gh release download "$TAG" --pattern "$ASSET" --output "$TAR" --clobber ||
        { echo "Downloading the $2 screenshots from GitHub failed; using this PC's."; return 1; }
    # GitHub's set replaces this PC's whole set of this kind, so no older picture is left in between.
    find "$LISTINGS" -type d -path "*/images/$FOLDER" -exec rm -rf {} +
    tar -xf "$TAR" -C "$LISTINGS" || { rm -f "$TAR"; echo "Unpacking the $2 screenshots failed."; return 1; }
    rm -f "$TAR"
    # Dated now, so they count as newer than GitHub's copy until a newer set is shared.
    find "$LISTINGS" -path "*/images/$FOLDER/*.png" -exec touch {} +
    echo "Got $(pictures | wc -l) $2 screenshots from GitHub."
}

case "${1:-}" in
    push) push "$@" ;;
    pull) pull "$@" ;;
    *) echo "usage: scripts/share-store-screenshots.sh push|pull phone|tablet" >&2; exit 2 ;;
esac
