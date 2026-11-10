#!/usr/bin/env bash
# Regenerates the app's Baseline Profile (app/src/release/generated/baselineProfiles/) on the phone, under
# the phone's lock (scripts/devices.sh). `./gradlew :app:generateBaselineProfile` alone swaps the app on
# whatever device adb gives it, with no lock: it stopped another session's UI test run, and with the watch
# connected too it failed on "more than one device" (Phase 91). docs/architecture/performance.md,
# *Baseline Profile*.
#
#   scripts/baseline-profile.sh            regenerate it (about 15 minutes on the Xiaomi 12X)
#   scripts/baseline-profile.sh --dry-run  which device, whether its lock is free and what would run; no build, nothing changed
#
# It does what performance.md says to do first: only the generator runs (the class filter; without it
# MeterFramesBenchmark runs too, 4 more minutes), the files an earlier run left in
# baselineprofile/build/intermediates/baselineprofiles/ go first (the plugin merges every run's files it
# finds there), the debug build is uninstalled first (the release-type build can't go over it) and put
# back after, and the screen is held upright (auto-rotate off), since the journeys expect it, and put back
# after. While it runs it keeps the screen awake and answers Play Protect's "Send app for a security
# check?" with Don't send (an English dialog only; another language says so, and a tap is then needed).
#
# The device: ANDROID_SERIAL, else the first phone (else tablet) connected (scripts/devices.sh), which
# Gradle's device tasks follow too; the watch is left alone. The builds are made first; from the uninstall
# to the debug build's return it holds the phone's lock. The new profile is left uncommitted: land it
# through a chore/baseline-profile branch (the release skill).
# Exit codes: 0 generated, 1 the build or the generation failed, 2 unknown argument, 3 no phone, 4 the
# phone is locked, 5 another run is using the phone (wait for it and run again).
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"
. "$ROOT/scripts/devices.sh"
PKG=io.github.klaboukes.twangle
TEST_PKG=io.github.klaboukes.twangle.baselineprofile
CLASS=io.github.klaboukes.twangle.baselineprofile.BaselineProfileGenerator
PROFILE_DIR=app/src/release/generated/baselineProfiles
INTERMEDIATES=baselineprofile/build/intermediates/baselineprofiles
# The generation's own time limit: a run stalled on a dialog no one answers lets the phone go after this.
GENERATE_TIMEOUT="${GENERATE_TIMEOUT:-2700}"
LOG=build/baseline-profile.log
GENERATE=(./gradlew :app:generateBaselineProfile -Pandroid.testInstrumentationRunnerArguments.class="$CLASS" --console=plain)

dry=0
for arg in "$@"; do
    case "$arg" in
        --dry-run) dry=1 ;;
        *) sed -n '8,9p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
    esac
done

use_device phone tablet
echo "On the $(device_name "$ANDROID_SERIAL"), Android $(adb shell getprop ro.build.version.release | tr -d '\r')"

if [ $dry = 1 ]; then
    if adb shell "[ -d $LOCK ]" > /dev/null 2>&1; then
        echo "Its lock is taken now ($(lock_holder)): a real run would stop with exit 5 unless that run was cut off."
    else
        echo "Its lock is free."
    fi
    echo "A real run would build :app:assembleDebug :app:assembleNonMinifiedRelease :baselineprofile:assembleNonMinifiedRelease,"
    echo "then, holding the lock: hold the screen upright, uninstall $PKG (and $TEST_PKG), delete $INTERMEDIATES/,"
    echo "run ANDROID_SERIAL=$ANDROID_SERIAL ${GENERATE[*]}"
    echo "(at most ${GENERATE_TIMEOUT}s), then put the debug build and the rotation back. Nothing done (--dry-run)."
    exit 0
fi

# The journeys need the app on screen: a locked phone (PIN, pattern) can't be unlocked from here.
wake_phone "unlock it (and keep it upright), then run this again"

# The release-type build, the generator and the debug build to put back after, built before the phone is
# taken, so the lock isn't held for the build (the generation then finds them up to date).
mkdir -p build
echo "Building..."
if ! ./gradlew :app:assembleDebug :app:assembleNonMinifiedRelease :baselineprofile:assembleNonMinifiedRelease --console=plain > "$LOG" 2>&1; then
    echo "Build failed (log: $LOG)" >&2
    grep -E "What went wrong|^e: |error:" -A3 "$LOG" | head -12 >&2
    exit 1
fi

take_phone_lock
# Run at the end by the lock's EXIT trap, before the lock goes: the watcher stops, the rotation and the
# debug build go back, however the run ends.
watcher=""
put_back() {
    [ -n "$watcher" ] && kill "$watcher" 2>/dev/null
    restore_rotation
    echo "Putting the debug build back..."
    adb uninstall "$TEST_PKG" > /dev/null 2>&1
    adb uninstall "$PKG" > /dev/null 2>&1
    install_debug_app >> "$LOG" 2>&1 && echo "Twangle (debug) is back on the phone." ||
        echo "Putting the debug build back failed (log: $LOG); run scripts/install-debug.sh."
}
hold_upright
mark_app_replaced

# The debug build and the release-type build are signed differently, so start clean; and no earlier
# run's profile files for the plugin to merge in.
adb uninstall "$TEST_PKG" > /dev/null 2>&1
adb uninstall "$PKG" > /dev/null 2>&1
rm -rf "$INTERMEDIATES"

# Keeps the screen from timing out (and locking) between the journeys, and taps Don't send when Play
# Protect asks after one of Gradle's installs; after a dialog it couldn't answer it waits a minute before
# trying again. Ends with this script.
( while kill -0 $$ 2>/dev/null; do
    adb shell input keyevent KEYCODE_WAKEUP < /dev/null > /dev/null 2>&1
    PROTECT_WAIT=1 dismiss_play_protect "the profile's builds" || sleep 60
    sleep 10
done ) &
watcher=$!

echo "Generating the Baseline Profile (about 15 minutes; log: $LOG)..."
started=$SECONDS
timeout "$GENERATE_TIMEOUT" "${GENERATE[@]}" >> "$LOG" 2>&1
status=$?
took=$(( (SECONDS - started) / 60 ))

if [ $status -ne 0 ]; then
    [ $status = 124 ] && echo "The generation was cut off after ${GENERATE_TIMEOUT}s (log: $LOG)" >&2 ||
        echo "The generation failed after $took min (exit $status; log: $LOG)" >&2
    grep -E "What went wrong|FAILED|Exception|error:" -A2 "$LOG" | head -20 >&2
    exit 1
fi
rules="$(wc -l < "$PROFILE_DIR/baseline-prof.txt" 2>/dev/null | tr -d ' ')"
startup="$(wc -l < "$PROFILE_DIR/startup-prof.txt" 2>/dev/null | tr -d ' ')"
echo "Generated in $took min: ${rules:-?} rules, ${startup:-?} in the startup profile ($PROFILE_DIR/, not committed)."
exit 0
