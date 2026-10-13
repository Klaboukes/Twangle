#!/usr/bin/env bash
# Measures frame times and main-thread work of every meter look on the connected phone
# (MeterFramesBenchmark in :baselineprofile) and prints the table. See ARCHITECTURE.md §4,
# "Meter looks (Phase 13)".
#
#   scripts/meter-frames.sh                      all six looks, 3 windows of 7 s each
#   scripts/meter-frames.sh COMET:STUDIO_METER   only these looks (MeterLook names, colon-separated)
#   scripts/meter-frames.sh CURRENT              keep the look the app has (e.g. an older build)
#
# Needs the phone connected, unlocked and upright, about 4 minutes. It installs the minified
# benchmarkRelease build, which hears a synthetic sweeping tone instead of the mic (no test tones
# needed), then puts the normal debug build back. Installing a different build clears the app's
# settings, as the UI tests do.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"
ADB="${LOCALAPPDATA:-}/Android/Sdk/platform-tools/adb.exe"
PKG=io.github.klaboukes.twangle
CLASS=io.github.klaboukes.twangle.baselineprofile.MeterFramesBenchmark

if ! "$ADB" devices 2>/dev/null | grep -q $'\tdevice$'; then
    echo "No phone connected (adb devices shows none). Connect the S26; check Auto Blocker is off." >&2
    exit 3
fi

echo "Display: $("$ADB" shell dumpsys display | grep -m1 -o 'mActiveRenderFrameRate=[0-9.]*'), refresh_rate_mode=$("$ADB" shell settings get secure refresh_rate_mode | tr -d '\r') (Samsung: 1 = Adaptive, up to 120 Hz)"

# The run needs the tuner on screen: a locked phone (PIN, pattern) can't be unlocked from here.
"$ADB" shell input keyevent KEYCODE_WAKEUP
if "$ADB" shell dumpsys window | grep -q "isKeyguardShowing=true"; then
    echo "The phone is locked. Unlock it (and keep it upright), then run this again." >&2
    exit 4
fi

args=(-Pandroid.testInstrumentationRunnerArguments.class="$CLASS")
[ $# -gt 0 ] && args+=(-Pandroid.testInstrumentationRunnerArguments.meterLooks="$1")

# The debug build and the benchmark build may be signed differently, so start clean.
"$ADB" uninstall "$PKG" >/dev/null 2>&1
LOG="build/meter-frames.log"
mkdir -p build
# Keep the screen from timing out (and locking) while Gradle builds and installs; once the tuner
# is on screen it keeps the screen on itself.
( while true; do "$ADB" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1; sleep 15; done ) &
awake=$!
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest "${args[@]}" --console=plain > "$LOG" 2>&1
status=$?
kill $awake 2>/dev/null

result="$(find baselineprofile/build/outputs -name meter-frames.tsv -newer "$LOG" 2>/dev/null | head -1)"
[ -z "$result" ] && result="$(find baselineprofile/build/outputs -name meter-frames.tsv 2>/dev/null | head -1)"
if [ $status -eq 0 ] && [ -n "$result" ]; then
    column -t -s $'\t' "$result" 2>/dev/null || cat "$result"
else
    echo "Measurement failed (exit $status); see $LOG" >&2
    grep -E "FAILED|Exception|error:" "$LOG" | head -20 >&2
fi

echo "Putting the debug build back..."
"$ADB" uninstall "$PKG" >/dev/null 2>&1
./gradlew :app:installDebug --console=plain -q > build/meter-frames-install.log 2>&1 && echo "Twangle (debug) reinstalled on the phone."
exit $status
