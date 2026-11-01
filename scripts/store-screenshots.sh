#!/usr/bin/env bash
# Makes the Play Store's phone screenshots on the phone (docs/store-listing.md, *Phone screenshots*):
# for each language, the StoreScreenshots test opens the eight planned screens in their styles, feeds
# the tuner a fixed sound through a stand-in microphone (nothing plays out loud) and draws each on a
# 1080 x 1920 canvas (focus mode's on its side, 1920 x 1080) with that language's caption above.
#
# With a tablet connected instead (at least 600 dp the short way) it makes the 10-inch tablet set
# (*Tablet screenshots*): the tablet's screens upright, on the same canvases with the same captions,
# the shots in TABLET_SHOTS only, to images/tenInchScreenshots/. Which one it makes follows from the
# device; there is no switch.
#
#   scripts/store-screenshots.sh                 every language (before a listing is sent; ~2 min a language)
#   scripts/store-screenshots.sh en-US nl-NL     only these Play locales
#
# The finished pictures go where fastlane and F-Droid look for them,
#   fastlane/metadata/android/<locale>/images/phoneScreenshots/1.png ... 8.png
#   fastlane/metadata/android/<locale>/images/tenInchScreenshots/<n>.png (the tablet's, numbered as their captions)
# (git-ignored: made again by this script, sent by scripts/release.sh listing, shared with the owner's other PC
# through GitHub by scripts/share-store-screenshots.sh after a run that made them all), the screens as they were (without the
# status bar) to build/store-screenshots/<locale>/ (the tablet's to build/store-screenshots-tablet/<locale>/). Captions: fastlane/metadata/android/<locale>/
# screenshot_captions.txt, one a line. Needs the phone connected, like scripts/ui-tests.sh, whose
# exit codes it passes on (3 no phone, 4 locked, 5 busy). Leaves Twangle in the last screenshot's
# style; puts the app's language and the phone's text size back.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
ADB_EXE="${LOCALAPPDATA:-}/Android/Sdk/platform-tools/adb.exe"
adb() { MSYS_NO_PATHCONV=1 "$ADB_EXE" "$@"; }
COOL_BELOW="${COOL_BELOW:-37}"
LISTINGS=fastlane/metadata/android
PULLED=app/build/outputs/connected_android_test_additional_output
RAW=build/store-screenshots
SHOTS="1 2 3 4 5 6 7 8"
FOLDER=phoneScreenshots
# The tablet set's pictures, by caption number (StoreScreenshots.Shot.onTablet).
TABLET_SHOTS="1 2 3 4 5 6 7 8"

if [ $# -gt 0 ]; then
    locales=("$@")
else
    mapfile -t locales < <(find "$LISTINGS" -mindepth 2 -maxdepth 2 -name screenshot_captions.txt | sed 's|.*/android/||; s|/.*||' | sort)
fi

# The battery's temperature in whole degrees (the phone reports tenths).
phone_temp() {
    local tenths
    tenths="$(adb shell dumpsys battery 2>/dev/null | tr -d '\r' | sed -n 's/^ *temperature: \([0-9]*\)/\1/p' | head -1)"
    echo $(( ${tenths:-0} / 10 ))
}

# Waits, at most 30 minutes, while the phone is COOL_BELOW degrees or warmer.
wait_cool() {
    local t waited=0
    t="$(phone_temp)"
    [ "$t" -lt "$COOL_BELOW" ] && return 0
    printf '(phone at %s °C, cooling' "$t"
    while [ "$t" -ge "$COOL_BELOW" ] && [ $waited -lt 1800 ]; do
        sleep 30; waited=$((waited + 30)); t="$(phone_temp)"
    done
    printf ' %ds) ' $waited
    adb shell input keyevent KEYCODE_WAKEUP > /dev/null 2>&1
}

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
total="$(echo $SHOTS | wc -w)"

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
# on GitHub, so what it shares afterwards is no older than what the other PC made (share-store-screenshots.sh).
kind=phone
[ "$tablet" = true ] && kind=tablet
[ $# -gt 0 ] && scripts/share-store-screenshots.sh pull $kind

mkdir -p build "$RAW"
# The app these pictures show: after a run of every language, build/<kind>-screenshots.made-at says at
# which commit, and whether from uncommitted changes, so scripts/release.sh listing can refuse pictures
# older than the app's screens (made before a listing is sent since 2026-10-30, not every night).
# Shared on GitHub with the pictures (share-store-screenshots.sh). A run of some languages leaves it.
made_at="$(git rev-parse HEAD) $([ -z "$(git status --porcelain)" ] && echo clean || echo uncommitted) $(date '+%Y-%m-%d %H:%M')"
status=0
for locale in "${locales[@]}"; do
    captions="$LISTINGS/$locale/screenshot_captions.txt"
    printf '%-6s ' "$locale"
    if [ ! -f "$captions" ]; then
        echo "no $captions"
        status=1
        continue
    fi
    wait_cool
    log="build/store-screenshots-$locale.log"
    [ "$tablet" = true ] && log="build/store-screenshots-tablet-$locale.log"
    start=$SECONDS
    # A run that saves nothing leaves the last run's pictures where ui-tests.sh pulls them to.
    rm -rf "$PULLED"
    scripts/ui-tests.sh :app -c StoreScreenshots -a storeScreenshots="$locale" -a storeCaptions="$(base64 -w0 "$captions")" -a storeTablet=$tablet > "$log" 2>&1
    code=$?
    if [ $code -ge 3 ]; then
        tail -3 "$log"
        exit $code
    fi
    dest="$LISTINGS/$locale/images/$FOLDER"
    rm -rf "$dest" "$RAW/$locale"
    mkdir -p "$dest" "$RAW/$locale"
    made=0
    for n in $SHOTS; do
        [ -f "$PULLED/store-$n.png" ] && cp "$PULLED/store-$n.png" "$dest/$n.png" && made=$((made + 1))
    done
    cp "$PULLED"/raw-*.png "$RAW/$locale/" 2>/dev/null
    printf '%3ds  %d of %d pictures' $((SECONDS - start)) $made $total
    if [ $code != 0 ] || [ $made != $total ]; then
        echo "  FAILED (log: $log)"
        grep -E "FAIL|CRASH|TIMED OUT" "$log" | head -8
        status=1
    else
        echo
    fi
done
[ $status = 0 ] && [ $# -eq 0 ] && echo "$made_at" > "build/$kind-screenshots.made-at"
# Every picture made: share the set with the owner's other PC.
[ $status = 0 ] && scripts/share-store-screenshots.sh push $kind
exit $status
