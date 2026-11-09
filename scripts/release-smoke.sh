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
# The device: ANDROID_SERIAL, else the first phone (else tablet) connected (scripts/devices.sh).
# Exit codes: 0 passed, 1 Twangle crashed or the build failed, 3 no phone, 4 the phone is locked.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"
ADB_EXE="${LOCALAPPDATA:-}/Android/Sdk/platform-tools/adb.exe"
adb() { MSYS_NO_PATHCONV=1 "$ADB_EXE" "$@"; }
. "$ROOT/scripts/devices.sh"
PKG=io.github.klaboukes.twangle
ACTIVITY="$PKG/.app.MainActivity"
LOG=build/release-smoke.log
mkdir -p build

serial="${ANDROID_SERIAL:-$(first_device phone tablet)}"
if [ -z "$serial" ]; then
    echo "No phone connected (adb devices shows none)." >&2
    exit 3
fi
export ANDROID_SERIAL="$serial"
echo "On the $(device_name "$serial"), Android $(adb shell getprop ro.build.version.release | tr -d '\r')"

adb shell input keyevent KEYCODE_WAKEUP
if adb shell dumpsys window | grep -q "isKeyguardShowing=true"; then
    echo "The phone is locked. Unlock it, then run this again." >&2
    exit 4
fi

echo "Building the release-type build (benchmarkRelease, R8)..."
if ! ./gradlew :app:assembleBenchmarkRelease --console=plain > "$LOG" 2>&1; then
    echo "FAILED: Build failed (log: $LOG)"
    grep -E "^e: |error:|What went wrong" -A2 "$LOG" | head -12
    exit 1
fi
apk="$(find app/build/outputs/apk/benchmarkRelease -name '*.apk' | head -1)"

ROTATE_AUTO="$(adb shell settings get system accelerometer_rotation | tr -d '\r')"
ROTATION="$(adb shell settings get system user_rotation | tr -d '\r')"
put_back() {
    [[ "$ROTATE_AUTO" =~ ^[01]$ ]] && adb shell settings put system accelerometer_rotation "$ROTATE_AUTO"
    [[ "$ROTATION" =~ ^[0-3]$ ]] && adb shell settings put system user_rotation "$ROTATION"
    adb uninstall $PKG > /dev/null 2>&1
    if ./gradlew :app:installDebug --console=plain -q >> "$LOG" 2>&1; then
        echo "The debug build is back on the phone."
    else
        echo "Putting the debug build back failed (log: $LOG)."
    fi
}
trap put_back EXIT

# The debug build may be signed on the owner's other PC, so start clean; -g grants the microphone.
adb uninstall $PKG > /dev/null 2>&1
if ! timeout 300 "$ADB_EXE" install -g "$apk" >> "$LOG" 2>&1; then
    echo "FAILED: the release-type build didn't install (log: $LOG)"
    exit 1
fi
adb shell cmd package compile -m speed -f $PKG > /dev/null

adb logcat -b crash -b events -c
start_app() { adb shell am start -W -n "$ACTIVITY" > /dev/null; sleep 4; }
# The tuner stops listening 5 s after its screen is left (WhileSubscribed), which ends its flows.
leave_app() { adb shell input keyevent KEYCODE_HOME; sleep 7; }
turn() { adb shell settings put system user_rotation "$1"; sleep 3; }

echo "Start, Home, start again, on its side and back, Home..."
adb shell settings put system accelerometer_rotation 0
turn 0
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
