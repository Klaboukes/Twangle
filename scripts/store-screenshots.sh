#!/usr/bin/env bash
# Makes the Play Store's phone screenshots on the phone (docs/store-listing.md, *Phone screenshots*):
# for each language, the StoreScreenshots test opens the eight planned screens in their styles, feeds
# the tuner a fixed sound through a stand-in microphone (nothing plays out loud) and draws each on a
# 1080 x 1920 canvas with that language's caption above.
#
#   scripts/store-screenshots.sh                 every language (the overnight run; ~2 min a language)
#   scripts/store-screenshots.sh en-US nl-NL     only these Play locales
#
# The finished pictures go where fastlane and F-Droid look for them,
#   fastlane/metadata/android/<locale>/images/phoneScreenshots/1.png ... 8.png
# (git-ignored: made again by this script, sent by scripts/release.sh listing), the screens as they were (without the
# status bar) to build/store-screenshots/<locale>/. Captions: fastlane/metadata/android/<locale>/
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
SHOTS=8

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

mkdir -p build "$RAW"
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
    start=$SECONDS
    # A run that saves nothing leaves the last run's pictures where ui-tests.sh pulls them to.
    rm -rf "$PULLED"
    scripts/ui-tests.sh :app -c StoreScreenshots -a storeScreenshots="$locale" -a storeCaptions="$(base64 -w0 "$captions")" > "$log" 2>&1
    code=$?
    if [ $code -ge 3 ]; then
        tail -3 "$log"
        exit $code
    fi
    dest="$LISTINGS/$locale/images/phoneScreenshots"
    rm -rf "$dest" "$RAW/$locale"
    mkdir -p "$dest" "$RAW/$locale"
    made=0
    for n in $(seq 1 $SHOTS); do
        [ -f "$PULLED/store-$n.png" ] && cp "$PULLED/store-$n.png" "$dest/$n.png" && made=$((made + 1))
    done
    cp "$PULLED"/raw-*.png "$RAW/$locale/" 2>/dev/null
    printf '%3ds  %d of %d pictures' $((SECONDS - start)) $made $SHOTS
    if [ $code != 0 ] || [ $made != $SHOTS ]; then
        echo "  FAILED (log: $log)"
        grep -E "FAIL|CRASH|TIMED OUT" "$log" | head -8
        status=1
    else
        echo
    fi
done
exit $status
