#!/usr/bin/env bash
# Makes the Play Store's icon and feature graphics on the phone (docs/store-listing.md, *Icon and
# feature graphic*): the StoreGraphics test draws the launcher icon at 512 x 512 and a 1024 x 500
# feature graphic for every store language (the icon and name, that language's line from
# feature_graphic.txt, and three styles' meters fanned out) from the app itself, and this script puts
# them where fastlane and F-Droid look for them:
#
#   fastlane/metadata/android/en-US/images/icon.png
#   fastlane/metadata/android/<locale>/images/featureGraphic.png
#
#   scripts/store-graphics.sh                 the icon and every language's feature graphic
#   scripts/store-graphics.sh en-US nl-NL     the icon and only these languages' (while working on it)
#
# All are committed (small, and made again only after the launcher icon, the meters, the styles' looks
# or a line change). Needs the phone connected, like scripts/ui-tests.sh, whose exit codes it passes on
# (3 no phone, 4 locked, 5 busy). Changes nothing on the phone (~2 min).
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
LISTINGS=fastlane/metadata/android
PULLED=app/build/outputs/connected_android_test_additional_output
LOG=build/store-graphics.log

if [ $# -gt 0 ]; then
    locales=("$@")
else
    mapfile -t locales < <(find "$LISTINGS" -mindepth 2 -maxdepth 2 -name feature_graphic.txt | sed 's|.*/android/||; s|/.*||' | sort)
fi
lines=""
for locale in "${locales[@]}"; do
    file="$LISTINGS/$locale/feature_graphic.txt"
    [ -f "$file" ] || { echo "no $file"; exit 1; }
    lines+="$locale"$'\t'"$(tr -d '\r' < "$file" | head -1)"$'\n'
done

mkdir -p build
# A run that saves nothing leaves the last run's pictures where ui-tests.sh pulls them to.
rm -rf "$PULLED"
scripts/ui-tests.sh :app -c StoreGraphics -a storeGraphics=true -a storeGraphicLines="$(printf '%s' "$lines" | base64 -w0)" > "$LOG" 2>&1
code=$?
if [ $code -ge 3 ]; then
    tail -3 "$LOG"
    exit $code
fi
made=0
want=$(( ${#locales[@]} + 1 ))
[ -f "$PULLED/icon.png" ] && cp "$PULLED/icon.png" "$LISTINGS/en-US/images/icon.png" && made=$((made + 1))
for locale in "${locales[@]}"; do
    mkdir -p "$LISTINGS/$locale/images"
    [ -f "$PULLED/featureGraphic-$locale.png" ] && cp "$PULLED/featureGraphic-$locale.png" "$LISTINGS/$locale/images/featureGraphic.png" && made=$((made + 1))
done
echo "$made of $want pictures (the icon and ${#locales[@]} feature graphics)"
if [ $code != 0 ] || [ $made != $want ]; then
    echo "FAILED (log: $LOG)"
    grep -E "FAIL|CRASH|TIMED OUT" "$LOG" | head -8
    exit 1
fi
