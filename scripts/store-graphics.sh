#!/usr/bin/env bash
# Makes the Play Store's icon and feature graphic on the phone (docs/store-listing.md, *Icon and
# feature graphic*): the StoreGraphics test draws the launcher icon at 512 x 512 and the 1024 x 500
# feature graphic (the icon and name, and the tuner's meter holding a note in tune) from the app
# itself, and this script puts them where fastlane and F-Droid look for them:
#
#   fastlane/metadata/android/en-US/images/icon.png
#   fastlane/metadata/android/en-US/images/featureGraphic.png
#
# Both have no words to translate, so one copy serves every language, and both are committed. Run it
# again after the launcher icon, the meter or Contemporary's look change. Needs the phone connected,
# like scripts/ui-tests.sh, whose exit codes it passes on (3 no phone, 4 locked, 5 busy). Changes
# nothing on the phone (~1 min).
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
PULLED=app/build/outputs/connected_android_test_additional_output
DEST=fastlane/metadata/android/en-US/images
LOG=build/store-graphics.log

mkdir -p build "$DEST"
# A run that saves nothing leaves the last run's pictures where ui-tests.sh pulls them to.
rm -rf "$PULLED"
scripts/ui-tests.sh :app -c StoreGraphics -a storeGraphics=true > "$LOG" 2>&1
code=$?
if [ $code -ge 3 ]; then
    tail -3 "$LOG"
    exit $code
fi
made=0
for name in icon.png featureGraphic.png; do
    [ -f "$PULLED/$name" ] && cp "$PULLED/$name" "$DEST/$name" && made=$((made + 1)) && echo "$DEST/$name"
done
if [ $code != 0 ] || [ $made != 2 ]; then
    echo "FAILED: $made of 2 pictures (log: $LOG)"
    grep -E "FAIL|CRASH|TIMED OUT" "$LOG" | head -8
    exit 1
fi
