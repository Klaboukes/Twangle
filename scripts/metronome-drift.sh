#!/usr/bin/env bash
# Measures the metronome's steadiness on the connected phone (MetronomeDriftMeasurement in
# :core:audio): the real player at 120 BPM for ten minutes at a low media volume (put back after),
# then prints the summary: beats on the sample clock, the speaker clock against the system clock
# (ppm, and how far the beats wander from a perfect wall-clock grid), jitter of the beat reports,
# missed beats and underruns. See ARCHITECTURE.md §3, "Metronome".
#
#   scripts/metronome-drift.sh        ten minutes (about 11 with the build)
#   scripts/metronome-drift.sh 2      a quick two-minute try
#
# Needs the phone connected and unlocked, like scripts/ui-tests.sh; leave it alone while it clicks
# (it holds the audio focus, so other apps' music stops). Every beat lands in
# core/audio/build/outputs/connected_android_test_additional_output/metronome-drift.tsv.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
MINUTES="${1:-10}"
PULLED="core/audio/build/outputs/connected_android_test_additional_output"

rm -rf "$PULLED"
MODULE_TIMEOUT=$((MINUTES * 60 + 180)) scripts/ui-tests.sh :core:audio -c MetronomeDriftMeasurement \
    -a metronomeDrift=true -a metronomeDriftMinutes="$MINUTES"
status=$?
echo
if [ -f "$PULLED/metronome-drift.txt" ]; then
    cat "$PULLED/metronome-drift.txt"
else
    echo "No measurement came back (log: build/ui-tests-core-audio.log)." >&2
    [ $status = 0 ] && status=1
fi
exit $status
