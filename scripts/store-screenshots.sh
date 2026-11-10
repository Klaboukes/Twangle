#!/usr/bin/env bash
# Makes the Play Store's phone screenshots on the phone (docs/store-listing.md, *Phone screenshots*):
# for each language, the StoreScreenshots test opens the eight planned screens in their themes, feeds
# the tuner a fixed sound through a stand-in microphone (nothing plays out loud) and draws each on a
# 1080 x 1920 canvas (focus mode's on its side, 1920 x 1080) with that language's caption above.
#
# With a tablet connected instead (at least 600 dp the short way) it makes the 10-inch tablet set
# (*Tablet screenshots*): the tablet's screens upright, on the same canvases with the same captions,
# the shots in TABLET_SHOTS only, to images/tenInchScreenshots/. Which one it makes follows from the
# device; there is no switch. With a phone and a tablet both connected (and no ANDROID_SERIAL), it
# makes both sets at once, one on each, built once, each line of output marked "phone" or "tablet".
#
#   scripts/store-screenshots.sh                 every language (before a listing is sent; ~80 s a language, built once)
#   scripts/store-screenshots.sh en-US nl-NL     only these Play locales
#   scripts/store-screenshots.sh --shots 6 [locale…]   only these pictures (by number, "3,6" for two), the others kept:
#                                                when one picture changed (~25 s a picture a language instead of ~2 min)
#
# STORE_BATCH languages (6) go through one test run, the phone cooled between runs: one run a language
# cost about 15 s of start-up each (2026-11-09). A language of a run that didn't make all its pictures
# gets one more try on its own, so one slip (a screen up before its language, 2026-11-06) doesn't spoil
# an unattended run.
#
# The finished pictures go where fastlane and F-Droid look for them,
#   fastlane/metadata/android/<locale>/images/phoneScreenshots/1.png ... 8.png
#   fastlane/metadata/android/<locale>/images/tenInchScreenshots/<n>.png (the tablet's, numbered as their captions)
# (git-ignored: made again by this script, sent by scripts/release.sh listing, shared with the owner's other PC
# through GitHub by scripts/share-store-screenshots.sh after a run that made them all), the screens as they were (without the
# status bar) to build/store-screenshots/<locale>/ (the tablet's to build/store-screenshots-tablet/<locale>/). Captions: fastlane/metadata/android/<locale>/
# screenshot_captions.txt, one a line. Needs the phone connected, like scripts/ui-tests.sh, whose
# exit codes it passes on (3 no phone, 4 locked, 5 busy). Leaves Twangle in the last screenshot's
# theme; puts the app's language and the phone's text size back.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
# adb, the devices and cooling (wait_cool, COOL_BELOW): shared with the other scripts that use a device.
. "$ROOT/scripts/devices.sh"
BATCH="${STORE_BATCH:-6}"
LISTINGS=fastlane/metadata/android
RAW=build/store-screenshots
SHOTS="1 2 3 4 5 6 7 8"
FOLDER=phoneScreenshots
# The tablet set's pictures, by caption number (StoreScreenshots.Shot.onTablet).
TABLET_SHOTS="1 2 3 4 5 6 7 8"
args=("$@")

# --shots: only these pictures, each language's others kept as they are.
only=""
if [ "${1:-}" = "--shots" ]; then
    only="${2:-}"
    [[ "$only" =~ ^[1-8](,[1-8])*$ ]] || { echo "usage: scripts/store-screenshots.sh [--shots <n>[,<n>…]] [locale…]" >&2; exit 2; }
    shift 2
fi
if [ $# -gt 0 ]; then
    locales=("$@")
else
    mapfile -t locales < <(find "$LISTINGS" -mindepth 2 -maxdepth 2 -name screenshot_captions.txt | sed 's|.*/android/||; s|/.*||' | sort)
fi

# A phone and a tablet: both sets at once, each made by this script on its own device. Built here once,
# so the two don't build over each other; each keeps its own pulled pictures and logs (UI_TESTS_TAG).
if [ -z "${ANDROID_SERIAL:-}" ]; then
    phone="$(first_device phone)"
    tab="$(first_device tablet)"
    if [ -n "$phone" ] && [ -n "$tab" ]; then
        echo "A phone and a tablet: both sets at once, on the $(device_name "$phone") and the $(device_name "$tab")."
        export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"
        if ! ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --console=plain > build/store-screenshots-build.log 2>&1; then
            echo "Build failed (log: build/store-screenshots-build.log)"
            exit 1
        fi
        for kind in phone tablet; do
            serial="$phone"
            [ $kind = tablet ] && serial="$tab"
            ( ANDROID_SERIAL="$serial" STORE_BUILT=1 "$0" "${args[@]}"; echo $? > "build/store-screenshots-$kind.exit" ) 2>&1 |
                sed -u "s/^/$(printf '%-7s' $kind) /" &
        done
        wait
        status=0
        for kind in phone tablet; do
            code="$(cat "build/store-screenshots-$kind.exit" 2>/dev/null)"
            [ "${code:-1}" = 0 ] || status="${code:-1}"
            rm -f "build/store-screenshots-$kind.exit"
        done
        exit "$status"
    fi
fi

# One device (or none): the phone, else the tablet, or ANDROID_SERIAL's (scripts/devices.sh), so this
# script's own adb calls below and its test runs use the same one, even with the watch connected too.
use_device phone tablet

# A tablet (at least 600 dp the short way, from the screen's pixels and density) gets the tablet set.
read -r size_w size_h < <(adb shell wm size 2>/dev/null | tr -d '\r' | sed -n 's/.*: *\([0-9]*\)x\([0-9]*\)$/\1 \2/p' | tail -1)
density="$(adb shell wm density 2>/dev/null | tr -d '\r' | sed -n 's/.*: *\([0-9]*\)$/\1/p' | tail -1)"
tablet=false
if [[ "${size_w:-}" =~ ^[0-9]+$ && "${size_h:-}" =~ ^[0-9]+$ && "${density:-}" =~ ^[0-9]+$ ]] &&
    (( (size_w < size_h ? size_w : size_h) * 160 / density >= 600 )); then
    tablet=true
    SHOTS="$TABLET_SHOTS"
    FOLDER=tenInchScreenshots
    RAW=build/store-screenshots-tablet
    echo "A tablet: the 10-inch tablet set ($(echo $SHOTS | wc -w) pictures a language, to images/$FOLDER/)."
fi
if [ -n "$only" ]; then
    SHOTS="$(for n in ${only//,/ }; do [[ " $SHOTS " == *" $n "* ]] && echo $n; done | sort -nu | paste -sd ' ')"
    [ -n "$SHOTS" ] || { echo "None of pictures $only is in this device's set." >&2; exit 2; }
    echo "Only pictures $SHOTS (the others kept)."
fi
total="$(echo $SHOTS | wc -w)"
kind=phone
[ "$tablet" = true ] && kind=tablet
# This kind's own pulled pictures and test logs, so a phone and a tablet can run at once (scripts/ui-tests.sh).
export UI_TESTS_TAG=$kind
PULLED="app/build/outputs/connected_android_test_additional_output-$kind"

# The test changes the device's text size and Twangle's own language and puts them back; a test that
# crashes midway can't, and the next one would take what it left for the device's own (seen on the
# tablet, 2026-10-27: Arabic and 1.0× left behind). So what is found here is put back at the end too.
PACKAGE=io.github.klaboukes.twangle
FONT_SCALE="$(adb shell settings get system font_scale 2>/dev/null | tr -d '\r')"
APP_LOCALES="$(adb shell cmd locale get-app-locales $PACKAGE 2>/dev/null | tr -d '\r' | sed -n 's/.* are \[\(.*\)\]$/\1/p' | tr -d ' ')"
put_back() {
    [[ "$FONT_SCALE" =~ ^[0-9.]+$ ]] && adb shell settings put system font_scale "$FONT_SCALE" > /dev/null 2>&1
    adb shell cmd locale set-app-locales $PACKAGE --locales "'$APP_LOCALES'" > /dev/null 2>&1
}
trap put_back EXIT

# A run of only some languages keeps this PC's pictures of the others: first take the latest set shared
# on GitHub when it is newer (or the languages this PC lacks), so what it shares afterwards is no older than
# what the other PC made (share-store-screenshots.sh).
{ [ $# -gt 0 ] || [ -n "$only" ]; } && scripts/share-store-screenshots.sh pull $kind

mkdir -p build "$RAW"
# The app these pictures show: after a run of every language, build/<kind>-screenshots.made-at says for each
# picture it made at which commit, and whether from uncommitted changes, one line a picture ("<n> <commit>
# clean|uncommitted <date>"), so scripts/release.sh listing can refuse pictures older than the app's screens
# (made before a listing is sent since 2026-10-30, not every night); a --shots run of every language updates
# its pictures' lines only. Shared on GitHub with the pictures (share-store-screenshots.sh). A run of some
# languages leaves it.
made_at="$(git rev-parse HEAD) $([ -z "$(git status --porcelain)" ] && echo clean || echo uncommitted) $(date '+%Y-%m-%d %H:%M')"
status=0
# Built on the first run, the same tests for the rest (--no-build: 22 s less a run, 2026-11-06).
built=()
[ -n "${STORE_BUILT:-}" ] && built=(--no-build)
log_base="build/store-screenshots"
[ "$tablet" = true ] && log_base="build/store-screenshots-tablet"

# Runs the test once for [locales…] into $PULLED/<locale>/; the test run's exit code.
run_tests_for() {
    local list="" captions="" l
    for l in "$@"; do
        list+="${list:+,}$l"
        captions+="${captions:+,}$l:$(base64 -w0 "$LISTINGS/$l/screenshot_captions.txt")"
    done
    # A run that saves nothing leaves the last run's pictures where ui-tests.sh pulls them to.
    rm -rf "$PULLED"
    MODULE_TIMEOUT=$((300 * $#)) scripts/ui-tests.sh :app "${built[@]}" -c StoreScreenshots -a storeScreenshots="$list" \
        -a storeCaptions="$captions" -a storeTablet=$tablet ${only:+-a storeShots="${SHOTS// /,}"} > "$log" 2>&1
    local code=$?
    grep -q "^build " "$log" && built=(--no-build)
    return $code
}

# How many of this run's pictures [locale] got.
made_for() {
    local n made=0
    for n in $SHOTS; do
        [ -f "$PULLED/$1/store-$n.png" ] && made=$((made + 1))
    done
    echo $made
}

# Puts [locale]'s pictures where the store looks, or keeps its earlier ones when it didn't make them all.
store_locale() {
    local locale="$1" seconds="$2" made dest kept n
    made="$(made_for "$locale")"
    dest="$LISTINGS/$locale/images/$FOLDER"
    printf '%-6s %3ds  %d of %d pictures' "$locale" "$seconds" "$made" "$total"
    if [ "$made" != "$total" ]; then
        # A language that failed keeps the pictures it had: an emptied folder made the next run of some
        # languages take GitHub's older set over this PC's newer ones (2026-11-08, the phone dropped off on zh-TW).
        kept="$(find "$dest" -maxdepth 1 -name '*.png' 2>/dev/null | wc -l)"
        echo "  FAILED, kept its $kept earlier pictures (log: $log)"
        grep -E "FAIL|CRASH|TIMED OUT" "$log" | grep -F "$locale" | head -8
        return 1
    fi
    # Some pictures: only theirs replaced. All: the folders afresh, so nothing of an older set is left.
    [ -z "$only" ] && rm -rf "$dest" "$RAW/$locale"
    mkdir -p "$dest" "$RAW/$locale"
    for n in $SHOTS; do
        cp "$PULLED/$locale/store-$n.png" "$dest/$n.png"
    done
    cp "$PULLED/$locale"/raw-*.png "$RAW/$locale/" 2>/dev/null
    echo
}

todo=()
for locale in "${locales[@]}"; do
    if [ -f "$LISTINGS/$locale/screenshot_captions.txt" ]; then
        todo+=("$locale")
    else
        echo "$locale  no $LISTINGS/$locale/screenshot_captions.txt"
        status=1
    fi
done
for ((i = 0; i < ${#todo[@]}; i += BATCH)); do
    batch=("${todo[@]:i:BATCH}")
    # The phone cooled between runs, on a line of its own (exit 4 when it locked meanwhile).
    wait_cool ""
    log="$log_base-${batch[0]}.log"
    start=$SECONDS
    run_tests_for "${batch[@]}"
    code=$?
    if [ $code -ge 3 ]; then
        tail -3 "$log"
        exit $code
    fi
    each=$(( (SECONDS - start) / ${#batch[@]} ))
    # The languages that didn't make all their pictures, once more each on its own: the batch's log is kept.
    again=()
    for locale in "${batch[@]}"; do
        [ "$(made_for "$locale")" = "$total" ] || again+=("$locale")
    done
    for locale in "${batch[@]}"; do
        [[ " ${again[*]} " == *" $locale "* ]] || store_locale "$locale" $each || status=1
    done
    if [ ${#again[@]} -gt 0 ]; then
        mv "$log" "$log.first"
        # The batch's pictures of the others are stored already; each retry pulls its own.
        for locale in "${again[@]}"; do
            log="$log_base-$locale.log"
            printf '(again) '
            start=$SECONDS
            run_tests_for "$locale"
            code=$?
            [ $code -ge 3 ] && { tail -3 "$log"; exit $code; }
            store_locale "$locale" $((SECONDS - start)) || status=1
        done
    fi
done
# Every language: the pictures made get this commit in the note, each on its own line; a note of the old
# kind, one line for the whole set, first becomes a line for each picture.
if [ $status = 0 ] && [ $# -eq 0 ]; then
    note="build/$kind-screenshots.made-at"
    old="$(cat "$note" 2>/dev/null)"
    if [ -n "$old" ] && ! grep -qE '^[1-8] ' <<< "$old"; then
        old="$(for n in 1 2 3 4 5 6 7 8; do echo "$n $old"; done)"
    fi
    {
        for n in $SHOTS; do echo "$n $made_at"; done
        grep -vE "^(${SHOTS// /|}) " <<< "$old" | grep . || true
    } | sort -n > "$note.new" && mv "$note.new" "$note"
fi
# Every picture made: share the set with the owner's other PC, but only pictures of what is on main: the shared
# set is the store's, and a 2.0 branch's pictures in it would go out with 1.1's listing (2026-11-03, a v2 check of
# one language replaced the shared English pictures).
if [ $status = 0 ]; then
    git fetch -q origin main 2>/dev/null
    if git merge-base --is-ancestor HEAD origin/main 2>/dev/null; then
        scripts/share-store-screenshots.sh push $kind
    else
        echo "Not shared on GitHub: this commit isn't on main (the shared set is the store's)."
    fi
fi
exit $status
