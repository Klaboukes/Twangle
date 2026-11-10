#!/usr/bin/env bash
# A short smoke test of a release-type build on the phone. The UI tests run the debug build, which R8
# never touches, so a crash in R8's code alone goes unseen: on 2026-11-09 2.1's release build crashed on
# Android 13 each time the tuner was left, and only the Baseline Profile run caught it
# (docs/architecture/performance.md, *Release build*). The full check runs this first on the phone.
#
# It installs the benchmarkRelease build (shrunk by R8 as the release is, signed with the debug key, and
# hearing a synthetic tone instead of the mic, so nothing is played or heard), compiles it ahead of time as
# Play's profiles would (the crash above showed only so), then starts Twangle, leaves it with Home long
# enough for the tuner to stop listening, starts it again, turns the screen on its side and back, and leaves
# it again. A crash or "isn't responding" of Twangle meanwhile fails it. Then the debug build goes back on
# and the screen's rotation as it was. About 3 minutes.
#
#   scripts/release-smoke.sh
#
# The device: ANDROID_SERIAL, else the first phone (else tablet) connected (scripts/devices.sh). Both
# builds are made first; from the uninstall to the debug build's return it holds the phone's lock
# (scripts/devices.sh), so it never swaps the app under another session's UI test run.
# Exit codes: 0 passed, 1 Twangle crashed or the build failed, 3 no phone, 4 the phone is locked,
# 5 another run is using the phone (wait for it and run again).
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"
. "$ROOT/scripts/devices.sh"
PKG=io.github.klaboukes.twangle
ACTIVITY="$PKG/.app.MainActivity"
LOG=build/release-smoke.log
mkdir -p build

use_device phone tablet
echo "On the $(device_name "$ANDROID_SERIAL"), Android $(adb shell getprop ro.build.version.release | tr -d '\r')"
wake_phone "unlock it, then run this again"

# The release-type build to test and the debug build to put back after, built before the phone is taken.
echo "Building the release-type build (benchmarkRelease, R8)..."
if ! ./gradlew :app:assembleBenchmarkRelease :app:assembleDebug --console=plain > "$LOG" 2>&1; then
    echo "FAILED: Build failed (log: $LOG)"
    grep -E "^e: |error:|What went wrong" -A2 "$LOG" | head -12
    exit 1
fi
apk="$(find app/build/outputs/apk/benchmarkRelease -name '*.apk' | head -1)"

take_phone_lock
# Run at the end by the lock's EXIT trap, before the lock goes.
put_back() {
    restore_rotation
    adb uninstall $PKG > /dev/null 2>&1
    if install_debug_app >> "$LOG" 2>&1; then
        echo "The debug build is back on the phone."
    else
        echo "Putting the debug build back failed (log: $LOG)."
    fi
}
hold_upright
mark_app_replaced

# The debug build may be signed on the owner's other PC, so start clean; -g grants the microphone.
adb uninstall $PKG > /dev/null 2>&1
if ! timeout 300 "$ADB_EXE" install -g "$apk" >> "$LOG" 2>&1; then
    echo "FAILED: the release-type build didn't install (log: $LOG)"
    exit 1
fi
dismiss_play_protect $PKG
adb shell cmd package compile -m speed -f $PKG > /dev/null

adb logcat -b crash -b events -c
start_app() { adb shell am start -W -n "$ACTIVITY" > /dev/null; sleep 4; }
# The tuner stops listening 5 s after its screen is left (WhileSubscribed), which ends its flows.
leave_app() { adb shell input keyevent KEYCODE_HOME; sleep 7; }
turn() { adb shell settings put system user_rotation "$1"; sleep 3; }

echo "Start, Home, start again, on its side and back, Home..."
start_app
leave_app
start_app
turn 1
turn 0
leave_app

problems="$(adb logcat -b events -d | tr -d '\r' | grep -E "am_(crash|anr).*$PKG")"
if [ -n "$problems" ]; then
    echo "FAILED: Twangle crashed or stopped responding in the release-type build:"
    echo "$problems" | head -4
    adb logcat -b crash -d | tr -d '\r' | grep -v "^---------" | head -20
    exit 1
fi
echo "Passed: no crash and no \"isn't responding\"."
exit 0
