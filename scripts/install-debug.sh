#!/usr/bin/env bash
# Puts the phone app's debug build on a test phone under the phone's lock (scripts/devices.sh), for a
# phone check by hand (the roadmap-step and add-language skills): `./gradlew :app:installDebug` installs
# outside the lock, and an install during another session's UI test run stops its tests ("Process
# crashed").
#
#   scripts/install-debug.sh           build, then install (Twangle signed on the other PC is uninstalled first)
#   scripts/install-debug.sh --start   and then start Twangle (on the tab it opens on), the screen woken
#
# Builds first, then takes the lock only for the install (and the start). Nothing else of the phone
# changes; the microphone permission is not granted (as :app:installDebug), so the app asks for it.
# The device: ANDROID_SERIAL, else the first phone (else tablet) connected (scripts/devices.sh).
# Exit codes: 0 installed, 1 the build or the install failed, 2 unknown argument, 3 no phone, 4 the
# phone is locked (--start), 5 another run is using the phone (wait for it and run again).
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"
. "$ROOT/scripts/devices.sh"

start=0
for arg in "$@"; do
    case "$arg" in
        --start) start=1 ;;
        *) sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
    esac
done

use_device phone tablet
mkdir -p build
build_debug_app build/install-debug.log || exit 1
take_phone_lock
install_debug_app || exit 1
echo "Twangle (debug) is on the $(device_name "$ANDROID_SERIAL")."
if [ $start = 1 ]; then
    wake_phone "unlock it, then start Twangle by hand or run this again"
    adb shell am start -W -n io.github.klaboukes.twangle/io.github.klaboukes.twangle.app.MainActivity > /dev/null
    echo "Started."
fi
exit 0
