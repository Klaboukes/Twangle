#!/usr/bin/env bash
# Measures the watch metronome's timing on the Galaxy Watch 5 (WatchMetronomeTimingMeasurement in
# :wear): the phone's metronome player at 120 BPM in 4/4 with a wrist tap on every beat, on a screen
# kept on, then prints the summary: the beats against a perfect clock (ppm, wander, gaps, missed
# beats, underruns), one tap per beat and how late each vibrate call went out, the watch's timers at
# normal and urgent-audio priority, and from the vibrator service's own record of the last taps,
# when each motor started after its call and how long it ran. See
# docs/architecture/strum-and-metronome.md, "On the watch".
#
#   scripts/watch-metronome-timing.sh            ten minutes, silent (taps only; about 12 with the build)
#   scripts/watch-metronome-timing.sh 2 audible  two minutes with the click at media volume 1 (put back after)
#   scripts/watch-metronome-timing.sh 1 silent media   one minute, the taps sent as media vibration
#
# Needs the watch on Wi-Fi debugging (docs/devices.md, "Watch"); it picks the watch connected
# (scripts/devices.sh, by Wear OS's own "watch") unless ANDROID_SERIAL is set, and holds its lock for
# the run. The run goes on on the watch if the connection drops; the script waits for it and reads the
# result back (a drop of over a minute lets another run take the watch's lock over, which stops the
# measurement). It never uses the microphone. Every beat lands in
# build/watch-metronome-timing/watch-metronome-timing-<silent|audible>.tsv.
# Exit codes: 0 measured, 1 it failed, 2 wrong arguments, 3 no watch, 5 another run is using the watch.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
MINUTES="${1:-10}"
MODE="${2:-silent}"
USAGE="${3:-default}"
case "$MODE" in
    silent) SILENT=true ;;
    audible) SILENT=false ;;
    *) echo "usage: $0 [minutes] [silent|audible]" >&2; exit 2 ;;
esac

export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"
# adb (the watch's paths, /data/..., passed unchanged), the watch and its lock: scripts/devices.sh.
. "$ROOT/scripts/devices.sh"
use_device watch
# The watch's lock, as a UI test run on it takes (scripts/ui-tests.sh :wear): its installs would stop
# that run's tests. Exit 5 when another run has the watch: wait for it and run again.
take_phone_lock
APP=io.github.klaboukes.twangle
OUT="build/watch-metronome-timing"
LOG=/data/local/tmp/twangle-watch-metronome-timing.log
mkdir -p "$OUT"

adb shell input keyevent KEYCODE_WAKEUP
./gradlew -q :wear:installDebug :wear:installDebugAndroidTest || exit 1

adb shell rm -f "$LOG"
adb shell "nohup am instrument -w -r \
    -e class $APP.wear.WatchMetronomeTimingMeasurement \
    -e watchMetronomeTiming true -e watchMetronomeMinutes $MINUTES -e watchMetronomeSilent $SILENT \
    -e watchMetronomeUsage $USAGE \
    $APP.test/androidx.test.runner.AndroidJUnitRunner > $LOG 2>&1 &"
echo "Measuring $MINUTES min ($MODE) on the watch; leave it lying still."

# Wait for the run on the watch, through a dropped connection if need be.
deadline=$((SECONDS + MINUTES * 60 + 300))
until adb shell cat "$LOG" 2>/dev/null | grep -q "INSTRUMENTATION_CODE"; do
    if [ $SECONDS -gt $deadline ]; then
        echo "No result from the watch in time (log: $LOG on the watch)." >&2
        exit 1
    fi
    sleep 20
done

adb shell cat "$LOG" > "$OUT/instrument.log"
for ext in txt tsv; do
    adb exec-out run-as "$APP" cat "files/watch-metronome-timing-$MODE.$ext" > "$OUT/watch-metronome-timing-$MODE.$ext"
done
echo
cat "$OUT/watch-metronome-timing-$MODE.txt"
# The vibrator service's record of Twangle's last taps (Recent vibrations, before the aggregated
# history): request time, motor start, how long it ran, what it played.
adb shell dumpsys vibrator_manager > "$OUT/vibrator.txt"
awk -v app="$APP" '
    function ms(t,   a) { split(t, a, ":"); return (a[1] * 3600 + a[2] * 60 + a[3]) * 1000 }
    /Recent vibrations:/ { on = 1 } /Aggregated vibration history:/ { on = 0 }
    on && index($0, "| " app " ") && match($0, /start: [0-9:.]+/) {
        lag = ms(substr($0, RSTART + 7, RLENGTH - 7)) - ms($2)
        match($0, /duration: *[0-9]+ms/); d = substr($0, RSTART, RLENGTH); gsub(/[^0-9]/, "", d)
        match($0, /Step=[0-9]+ms/); step = substr($0, RSTART + 5, RLENGTH - 7)
        if (n == 0 || lag < lo) lo = lag
        if (n == 0 || lag > hi) hi = lag
        n++; sum += lag; ran[step] += d; cnt[step]++
    }
    END {
        if (n == 0) { print "Vibrator service: no Twangle taps on record"; exit }
        printf "Vibrator service, last %d taps: motor started %.0f / %.1f / %.0f ms (min / mean / max) after the call;", n, lo, sum / n, hi
        for (s in cnt) printf " %d ms asked ran %.0f ms on average (%d);", s, ran[s] / cnt[s], cnt[s]
        print ""
    }' "$OUT/vibrator.txt" | tee -a "$OUT/watch-metronome-timing-$MODE.txt"
if grep -q "FAILURES!!!\|INSTRUMENTATION_FAILED\|shortMsg" "$OUT/instrument.log"; then
    grep -m3 -A3 "Error\|Exception\|FAILURES" "$OUT/instrument.log" >&2
    exit 1
fi
grep -q "OK (1 test)" "$OUT/instrument.log" || { echo "The measurement didn't pass (see $OUT/instrument.log)." >&2; exit 1; }
