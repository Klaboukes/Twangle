#!/usr/bin/env bash
# Measures frame times and main-thread work of the tuner's meter on the connected phone
# (MeterFramesBenchmark in :baselineprofile) and prints the table. See
# docs/architecture/performance.md, "Meter looks". Since Phase 72 (2.0) it measures each of the
# lens's three looks, the app started afresh with each.
#
#   scripts/meter-frames.sh                      Classic, the Light arc and the strobe, 3 windows of 7 s each
#   scripts/meter-frames.sh STROBE               one look (CLASSIC, LIGHT_ARC, STROBE; colons between several)
#   scripts/meter-frames.sh CURRENT              the look the app shows
#
# Needs the phone connected, unlocked and upright, about 5 minutes. It installs the minified
# benchmarkRelease build, which hears a synthetic sweeping tone instead of the mic (no test tones
# needed), then puts the normal debug build back. Installing a different build clears the app's
# settings, as the UI tests do.
#
# The device: ANDROID_SERIAL, else the first phone (else tablet) connected (scripts/devices.sh), which
# Gradle's test task follows too. The builds are made first; from the uninstall to the debug build's
# return it holds the phone's lock (scripts/devices.sh). Exit codes: 0 measured, 1 the build or the
# measurement failed, 3 no phone, 4 the phone is locked, 5 another run is using the phone (wait for it
# and run again).
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"
. "$ROOT/scripts/devices.sh"
PKG=io.github.klaboukes.twangle
CLASS=io.github.klaboukes.twangle.baselineprofile.MeterFramesBenchmark
LOG="build/meter-frames.log"
mkdir -p build

use_device phone tablet
echo "Display: $(adb shell dumpsys SurfaceFlinger | grep -m1 -o 'refresh-rate *: [0-9.]* Hz' | tr -s ' '), peak_refresh_rate=$(adb shell settings get system peak_refresh_rate | tr -d '\r') Hz"

# The run needs the tuner on screen: a locked phone (PIN, pattern) can't be unlocked from here.
wake_phone "unlock it (and keep it upright), then run this again"

args=(-Pandroid.testInstrumentationRunnerArguments.class="$CLASS")
[ $# -gt 0 ] && args+=(-Pandroid.testInstrumentationRunnerArguments.meterLooks="$1")

# The benchmark build, its test and the debug build to put back after, built before the phone is taken.
echo "Building..."
if ! ./gradlew :app:assembleBenchmarkRelease :baselineprofile:assembleBenchmarkRelease :app:assembleDebug --console=plain > "$LOG" 2>&1; then
    echo "Build failed (log: $LOG)" >&2
    grep -E "What went wrong|^e: |error:" -A3 "$LOG" | head -12 >&2
    exit 1
fi

take_phone_lock
# Run at the end by the lock's EXIT trap, before the lock goes: the keep-awake loop stops and the debug
# build goes back on, however the run ends.
awake=""
put_back() {
    [ -n "$awake" ] && kill "$awake" 2>/dev/null
    echo "Putting the debug build back..."
    adb uninstall "$PKG" > /dev/null 2>&1
    install_debug_app > build/meter-frames-install.log 2>&1 && echo "Twangle (debug) reinstalled on the phone." ||
        echo "Putting the debug build back failed (log: build/meter-frames-install.log)."
}
mark_app_replaced

# The debug build and the benchmark build may be signed differently, so start clean.
adb uninstall "$PKG" >/dev/null 2>&1
# Keep the screen from timing out (and locking) while Gradle installs; once the tuner is on screen it
# keeps the screen on itself. The loop also ends with this script.
( while kill -0 $$ 2>/dev/null; do adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1; sleep 15; done ) &
awake=$!
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest "${args[@]}" --console=plain >> "$LOG" 2>&1
status=$?

result="$(find baselineprofile/build/outputs -name meter-frames.tsv -newer "$LOG" 2>/dev/null | head -1)"
[ -z "$result" ] && result="$(find baselineprofile/build/outputs -name meter-frames.tsv 2>/dev/null | head -1)"
if [ $status -eq 0 ] && [ -n "$result" ]; then
    column -t -s $'\t' "$result" 2>/dev/null || cat "$result"
else
    echo "Measurement failed (exit $status); see $LOG" >&2
    grep -E "FAILED|Exception|error:" "$LOG" | head -20 >&2
fi
[ $status -eq 0 ] || exit 1
exit 0
