#!/usr/bin/env bash
# Shares the store screenshots between the owner's PCs through GitHub (docs/store-listing.md, *Phone
# screenshots*). They are git-ignored, since a set is about 180 MB and is made again before each listing, so
# instead the latest set lives as a download on one fixed GitHub release, "store-screenshots", that
# keeps no history: each upload replaces the one before. Beside it goes the note of the commit the set
# shows (build/<kind>-screenshots.made-at, from scripts/store-screenshots.sh), which release.sh listing
# reads to refuse pictures older than the app's screens.
#
#   scripts/share-store-screenshots.sh push phone|tablet|watch   upload this PC's set, if every store folder has it
#   scripts/share-store-screenshots.sh pull phone|tablet|watch   download the set, if GitHub's is newer than this PC's (if
#                                                                older, only the languages this PC lacks)
#
# scripts/store-screenshots.sh (and scripts/watch-screenshots.sh, the watch's) pushes after a run that made every
# picture, and pulls before a run of only some languages (so the others are the latest too); scripts/release.sh
# listing pulls before it sends. The watch's are in the listing too since the watch's turn on Play
# (scripts/watch-screenshots.sh --home says where they are). Neither stops when GitHub can't be reached:
# they say so and carry on with what this PC has.
# Exit codes: 0 done or nothing to do, 1 failed (said why), 2 usage.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
LISTINGS=fastlane/metadata/android
TAG=store-screenshots

case "${2:-}" in
    phone) FOLDER=phoneScreenshots ;;
    tablet) FOLDER=tenInchScreenshots ;;
    watch) FOLDER=wearScreenshots; LISTINGS="$(scripts/watch-screenshots.sh --home)" ;;
    *) echo "usage: scripts/share-store-screenshots.sh push|pull phone|tablet|watch" >&2; exit 2 ;;
esac
ASSET="$2.tar"
TAR="build/shared-store-screenshots/$ASSET"   # GitHub names the download after the file
MADE_AT="build/$2-screenshots.made-at"        # uploaded as "<kind>-screenshots.made-at"

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
    # The commit the set shows goes with it; without a note, none (an old one would vouch for this set).
    if [[ -f "$MADE_AT" ]]; then
        gh release upload "$TAG" "$MADE_AT" --clobber > /dev/null || echo "Sharing which commit the $2 screenshots show failed; the other PC's release.sh listing will ask for them again."
    else
        gh release delete-asset "$TAG" "$(basename "$MADE_AT")" --yes > /dev/null 2>&1 || true
    fi
    # Dated now, so this PC doesn't take its own upload for a newer set at the next pull.
    find "$LISTINGS" -path "*/images/$FOLDER/*.png" -exec touch {} +
    echo "Shared: the other PC gets them with scripts/share-store-screenshots.sh pull $2 (release.sh listing does it)."
}

# The number of pictures of this kind in [dir]'s <locale>/images/<folder>/.
count_in() { find "$1/$2/images/$FOLDER" -maxdepth 1 -name '*.png' 2>/dev/null | wc -l; }

# This PC's set is newer than GitHub's but some languages lack pictures (a language not made here yet):
# takes only those languages from GitHub's set in $TAR, keeping every picture this PC has. Replacing the
# whole set put an older one over 35 newer languages on 2026-11-08, when one language's folder was empty.
fill_in() {
    local unpacked="build/shared-store-screenshots/unpacked" dir locale added=()
    rm -rf "$unpacked" && mkdir -p "$unpacked"
    tar -xf "$TAR" -C "$unpacked" || { rm -rf "$unpacked" "$TAR"; echo "Unpacking the $2 screenshots failed."; return 1; }
    rm -f "$TAR"
    for dir in "$unpacked"/*/images/"$FOLDER"; do
        [[ -d "$dir" ]] || continue
        locale="${dir#"$unpacked"/}"
        locale="${locale%%/*}"
        (( $(count_in "$LISTINGS" "$locale") < $(count_in "$unpacked" "$locale") )) || continue
        rm -rf "$LISTINGS/$locale/images/$FOLDER"
        mkdir -p "$LISTINGS/$locale/images"
        cp -r "$dir" "$LISTINGS/$locale/images/"
        find "$LISTINGS/$locale/images/$FOLDER" -name '*.png' -exec touch {} +
        added+=("$locale")
    done
    rm -rf "$unpacked"
    if (( ${#added[@]} == 0 )); then
        echo "GitHub's $2 screenshots are older and have no language this PC lacks; using this PC's."
        return 0
    fi
    # Those languages show the app at GitHub's set's commit at the newest, so its note goes with them (none
    # when it has none): release.sh listing then judges the set by its oldest pictures.
    gh release download "$TAG" --pattern "$(basename "$MADE_AT")" --output "$MADE_AT" --clobber > /dev/null 2>&1 || rm -f "$MADE_AT"
    echo "Took the languages this PC lacked from GitHub's older $2 screenshots (${added[*]}); kept this PC's newer pictures of the rest."
}

pull() {
    local there here
    there="$(uploaded_at)"
    if [[ -z "$there" ]]; then
        echo "No $2 screenshots shared on GitHub (or GitHub can't be reached); using this PC's."
        return 0
    fi
    here="$(newest_here)"
    if (( here >= there )) && complete_here; then
        echo "This PC's $2 screenshots are as new as GitHub's or newer; using them."
        return 0
    fi
    mkdir -p "$(dirname "$TAR")"
    echo "Getting the $2 screenshots from GitHub (shared $(date -d "@$there" '+%Y-%m-%d %H:%M'))..."
    gh release download "$TAG" --pattern "$ASSET" --output "$TAR" --clobber ||
        { echo "Downloading the $2 screenshots from GitHub failed; using this PC's."; return 1; }
    (( here >= there )) && { fill_in "$@"; return; }
    # Unpacked into a folder of its own first: a broken download or a full disk leaves this PC's set as it
    # was (unpacking straight over it, after removing it, left nothing, and a set takes ~50 min to make).
    local unpacked="build/shared-store-screenshots/unpacked" dir locale
    rm -rf "$unpacked" && mkdir -p "$unpacked"
    tar -xf "$TAR" -C "$unpacked" ||
        { rm -rf "$unpacked" "$TAR"; echo "Unpacking the $2 screenshots from GitHub failed; using this PC's."; return 1; }
    rm -f "$TAR"
    if [[ -z "$(find "$unpacked" -path "*/images/$FOLDER/*.png" -print -quit)" ]]; then
        rm -rf "$unpacked"
        echo "GitHub's $2 screenshots hold no pictures; using this PC's."
        return 1
    fi
    # GitHub's set replaces this PC's whole set of this kind, so no older picture is left in between. Each
    # language's folder is moved in, not copied (both are in this repo's folder), so this can't run out of room.
    find "$LISTINGS" -type d -path "*/images/$FOLDER" -exec rm -rf {} +
    for dir in "$unpacked"/*/images/"$FOLDER"; do
        [[ -d "$dir" ]] || continue
        locale="${dir#"$unpacked"/}"
        locale="${locale%%/*}"
        mkdir -p "$LISTINGS/$locale/images"
        mv "$dir" "$LISTINGS/$locale/images/" || { echo "Moving the $2 screenshots of $locale into place failed (they are in $unpacked)."; return 1; }
    done
    rm -rf "$unpacked"
    # And the note of the commit they show; a set shared without one shows no known commit.
    gh release download "$TAG" --pattern "$(basename "$MADE_AT")" --output "$MADE_AT" --clobber > /dev/null 2>&1 || rm -f "$MADE_AT"
    # Dated now, so they count as newer than GitHub's copy until a newer set is shared.
    find "$LISTINGS" -path "*/images/$FOLDER/*.png" -exec touch {} +
    echo "Got $(pictures | wc -l) $2 screenshots from GitHub."
}

case "${1:-}" in
    push) push "$@" ;;
    pull) pull "$@" ;;
    *) echo "usage: scripts/share-store-screenshots.sh push|pull phone|tablet|watch" >&2; exit 2 ;;
esac
