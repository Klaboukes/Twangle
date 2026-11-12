#!/usr/bin/env bash
# Makes the watch app's Play Store screenshots on the Galaxy Watch 5 (or the Wear OS emulator) (docs/store-listing.md,
# *Watch screenshots*): the WatchStoreScreenshots test draws, for each language, the app's own pages as the watch
# shows them, nothing heard or played for real: 1 the A in tune with the arc's green glow, 2 the low E 12 cents
# flat, 3 the metronome at 120 BPM with its first beat lit. Square, 960 x 960 px, the watch's round screen only,
# no frame, black round the circle.
#
#   scripts/watch-screenshots.sh                 every store language (~3 minutes, one test run)
#   scripts/watch-screenshots.sh en-US nl-NL     only these Play locales (the others kept)
#   scripts/watch-screenshots.sh --home          only print where the pictures go
#
# Every language gets its own set: the tuner's pictures show words ("In tune", "Tighten", the cents) and the note
# as that language's players write it (La and Mi in French or Spanish), and Play shows a language without its own
# pictures the default language's.
#
# They go to $HOME_DIR/<locale>/images/wearScreenshots/1.png ... 3.png, in the store listing since the watch's
# turn on Play (2026-11-12; until then they waited in fastlane/held-back/android/), where scripts/release.sh
# listing checks and sends them. Git-ignored like the phone's; after a run of every language
# on main the set is shared with the owner's other PC on GitHub (scripts/share-store-screenshots.sh ... watch),
# and build/watch-screenshots.made-at notes the commit they show, which release.sh listing reads.
#
# Needs the watch connected and awake (docs/devices.md, *Watch*); runs through scripts/ui-tests.sh :wear, which
# picks the watch, keeps its screen on for the run and puts the owner's time-out back, and whose exit codes it
# passes on (3 no watch, 5 busy).
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
# Where the pictures go: the store listing (held back in fastlane/held-back/android until the watch's turn on Play).
HOME_DIR=fastlane/metadata/android
LISTINGS=fastlane/metadata/android
FOLDER=wearScreenshots
SHOTS="1 2 3"
PULLED="wear/build/outputs/connected_android_test_additional_output-watch"
LOG=build/watch-screenshots.log

if [ "${1:-}" = "--home" ]; then
    echo "$HOME_DIR"
    exit 0
fi
case "${1:-}" in -*) sed -n '2,25p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;; esac

# Every store language: the folders of the store listing.
if [ $# -gt 0 ]; then
    locales=("$@")
else
    mapfile -t locales < <(find "$LISTINGS" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort)
fi
for locale in "${locales[@]}"; do
    [ -d "$LISTINGS/$locale" ] || { echo "$locale is not a store folder ($LISTINGS/$locale)" >&2; exit 2; }
done

# A run of some languages keeps this PC's pictures of the others: first the latest shared set, if newer.
[ $# -gt 0 ] && scripts/share-store-screenshots.sh pull watch

mkdir -p build
made_at="$(git rev-parse HEAD) $([ -z "$(git status --porcelain)" ] && echo clean || echo uncommitted) $(date '+%Y-%m-%d %H:%M')"
list="$(IFS=,; echo "${locales[*]}")"
echo "Drawing ${#locales[@]} languages' watch screenshots on the watch..."
start=$SECONDS
rm -rf "$PULLED"
UI_TESTS_TAG=watch MODULE_TIMEOUT=1200 scripts/ui-tests.sh :wear -c WatchStoreScreenshots -a watchStoreScreenshots="$list" > "$LOG" 2>&1
code=$?
if [ $code -ge 3 ]; then
    tail -3 "$LOG"
    exit $code
fi

status=0
for locale in "${locales[@]}"; do
    made=0
    for n in $SHOTS; do [ -f "$PULLED/$locale/watch-$n.png" ] && made=$((made + 1)); done
    dest="$HOME_DIR/$locale/images/$FOLDER"
    if [ "$made" != 3 ]; then
        # A language that failed keeps the pictures it had (as the phone's, scripts/store-screenshots.sh).
        echo "$locale  $made of 3 pictures  FAILED, kept its $(find "$dest" -maxdepth 1 -name '*.png' 2>/dev/null | wc -l) earlier ones (log: $LOG)"
        status=1
        continue
    fi
    rm -rf "$dest" && mkdir -p "$dest"
    for n in $SHOTS; do cp "$PULLED/$locale/watch-$n.png" "$dest/$n.png"; done
done
[ $status = 0 ] && [ $code != 0 ] && { echo "The test run failed (log: $LOG)"; grep -E "FAIL|CRASH|TIMED OUT" "$LOG" | head -5; status=1; }
echo "$(( ${#locales[@]} )) languages in $((SECONDS - start)) s; the pictures are in $HOME_DIR/<locale>/images/$FOLDER/."

# Every language: note the commit they show, and share them with the other PC when it is on main (the shared set
# is the store's).
if [ $status = 0 ] && [ $# -eq 0 ]; then
    echo "$made_at" > build/watch-screenshots.made-at
    git fetch -q origin main 2>/dev/null
    if git merge-base --is-ancestor HEAD origin/main 2>/dev/null; then
        scripts/share-store-screenshots.sh push watch
    else
        echo "Not shared on GitHub: this commit isn't on main (the shared set is the store's)."
    fi
fi
exit $status
