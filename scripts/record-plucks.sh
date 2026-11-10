#!/usr/bin/env bash
# Records plucked guitar strings on the connected test phone for the real-recording tests (ROADMAP
# Phase 46). The recorder is a test (PluckRecorder in :core:testing's on-device tests), never Twangle
# itself, so the app still writes no audio anywhere (hard rule 7). It listens through Twangle's own
# recorder: the same input (unprocessed, else voice recognition) at the same rate as the tuner.
#
# The phone guides the player: it shows each string and how to play it and waits for a tap on its
# screen, then shows "Pluck now" for each pluck and records a fixed few seconds (never more, never in
# a loop). The same lines show here. The files land in core/dsp/src/test/resources/fixtures/ as
# guitar-<string>-<pick|finger|pick-noisy>-<n>.wav: 16-bit mono WAV at the rate the tuner records
# at, with the phone, the input and the rate noted inside (the INFO comment).
#
#   scripts/record-plucks.sh             the whole session: every open string hard with a pick, then
#                                        softly with a finger (a quiet room), then with a pick in a
#                                        noisy room (a TV on, people talking)
#   scripts/record-plucks.sh --test      one 2 s recording, no tap and no playing needed: checks the
#                                        recorder end to end on the phone, then deletes the file
#   --takes N      plucks per string and way of playing in the quiet room (default 2; noisy room 1)
#   --seconds S    length of each recording in seconds (default 4)
#   --redo         record again what is already in the fixtures folder
#
# Stopped halfway (Ctrl+C)? Run it again: strings already in the fixtures folder are skipped. A pluck
# that went wrong: delete its files (all takes of that string and way) and run again. It runs through
# scripts/ui-tests.sh, which holds the phone's lock (a UI test run and a recording never clash), keeps
# Xiaomi's app-op set and installs the recorder with the microphone permission. The phone is
# ANDROID_SERIAL's, else the first phone (else tablet) connected (scripts/devices.sh), picked here and
# handed on, so Ctrl+C stops the recorder on that phone also with the watch connected (Phase 91).
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
# adb and picking the phone: shared with the other scripts that use a device.
. "$ROOT/scripts/devices.sh"

FIXTURES=core/dsp/src/test/resources/fixtures
PULLED=core/testing/build/outputs/connected_android_test_additional_output
LOG=build/ui-tests-core-testing.log
RUNLOG=build/record-plucks.log
TEST_PACKAGE=io.github.klaboukes.twangle.core.testing.test

takes=2 noisy_takes=1 seconds=4 redo=0 test=0
while [ $# -gt 0 ]; do
    case "$1" in
        --test) test=1 ;;
        --takes) takes="$2"; shift ;;
        --seconds) seconds="$2"; shift ;;
        --redo) redo=1 ;;
        -h | --help) sed -n '2,27p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "Unknown argument: $1 (see --help)" >&2; exit 2 ;;
    esac
    shift
done
[[ "$takes" =~ ^[1-9][0-9]?$ ]] || { echo "--takes needs a number from 1 to 99" >&2; exit 2; }
[[ "$seconds" =~ ^[0-9]+(\.[0-9]+)?$ ]] || { echo "--seconds needs a number" >&2; exit 2; }

# The open strings of a guitar in standard tuning: file name part | what the phone calls it.
STRINGS=(
    "E2|6th string, low E (E2)"
    "A2|5th string, A (A2)"
    "D3|4th string, D (D3)"
    "G3|3rd string, G (G3)"
    "B3|2nd string, B (B3)"
    "E4|1st string, high E (E4)"
)

# Whether every take of a group is in the fixtures folder already.
recorded() {
    local n
    for ((n = 1; n <= $2; n++)); do [ -f "$FIXTURES/$1-$n.wav" ] || return 1; done
}

# The plan for PluckRecorder: name|label|takes|note, separated by ";". The note goes with the first
# group of each way of playing still to record.
plan="" groups=0 files=0
add_groups() {
    local way="$1" label="$2" count="$3" note="$4" entry name
    for entry in "${STRINGS[@]}"; do
        name="guitar-${entry%%|*}-$way"
        if [ $redo = 0 ] && recorded "$name" "$count"; then continue; fi
        plan="$plan${plan:+;}$name|${entry#*|}: $label|$count|$note"
        note=""
        groups=$((groups + 1)) files=$((files + count))
    done
}

if [ $test = 1 ]; then
    seconds=2
    plan="test-recording|A test recording: no need to play|1|"
    groups=1 files=1
    use_device phone tablet
else
    add_groups pick "hard, with a pick" "$takes" \
        "A quiet room. Put the phone about 30 cm from the guitar, its bottom edge (the microphone) toward the sound hole."
    add_groups finger "softly, with a finger" "$takes" "Now softly with a finger, no pick. The phone stays where it is."
    add_groups pick-noisy "with a pick, in a noisy room" "$noisy_takes" \
        "Now make the room noisy: a TV or radio on, or people talking. The phone stays where it is."
    if [ $groups = 0 ]; then
        echo "Every pluck is in $FIXTURES already. To record them again: --redo."
        exit 0
    fi
    use_device phone tablet
    cat <<EOF
Recording plucked strings for Twangle's tests: $files plucks of $seconds s each, in $groups steps.

Before you start:
  - Tune the guitar (with Twangle, say), then close Twangle.
  - Unlock the phone and put it on a table or stand, about 30 cm (a foot) from the guitar,
    its bottom edge (the microphone) toward the sound hole. Leave it there for the whole session.
  - A quiet room first; near the end the phone asks for a noisy one (a TV on, people talking).

How it goes: the phone shows which string and how to play it. Tap its screen when you are ready.
For each pluck it turns green and says "Pluck now": pluck the open string once and let it ring
until the screen turns grey again; then quiet the strings with your hand.

Stop any time with Ctrl+C; run this again later and it carries on where it stopped.

EOF
    read -r -p "Press Enter to start (the first time it takes a minute or two to get ready)... " _
fi

rm -rf "$PULLED" && rm -f "$LOG" && mkdir -p build
args=(:core:testing -c PluckRecorder -a "pluckPlan=$plan" -a "pluckSeconds=$seconds")
[ $test = 1 ] && args+=(-a pluckAutoStart=true)
# Two hours for a whole session (the phone waits up to 15 minutes for each tap); three minutes for a test.
timeout=7200
[ $test = 1 ] && timeout=180
MODULE_TIMEOUT=$timeout scripts/ui-tests.sh "${args[@]}" > "$RUNLOG" 2>&1 &
run=$!

# Ctrl+C: stop the recorder on the phone (ANDROID_SERIAL's, which the run uses too: with the watch
# connected a plain `adb shell` stops on "more than one device", and the recorder waited for taps until
# the run's 2 h were up). The run then ends by itself and still brings the plucks recorded so far to the PC.
stop() {
    echo
    echo "Stopping. The plucks recorded so far are kept."
    ADB_LIMIT=$CLEANUP_ADB_LIMIT adb shell am force-stop "$TEST_PACKAGE" > /dev/null 2>&1 ||
        echo "The phone didn't answer, so the recorder wasn't stopped: it stops by itself within $((timeout / 60)) minutes, or unplug the phone." >&2
}
trap stop INT

echo "Getting the recorder ready on the phone..."
# The phone's lines as they come: the recorder's statuses in the run's log.
until [ -f "$LOG" ] || ! kill -0 $run 2>/dev/null; do sleep 1; done
[ -f "$LOG" ] && tail --pid=$run -n +1 -F "$LOG" 2>/dev/null | sed -un 's/\r$//; s/^INSTRUMENTATION_STATUS: pluck=//p'
wait $run
status=$?
while kill -0 $run 2>/dev/null; do wait $run; status=$?; done
trap - INT

echo
grep -v '^$' "$RUNLOG"
shopt -s nullglob
wavs=("$PULLED"/*.wav)
if [ ${#wavs[@]} = 0 ]; then
    echo "No recordings came back (the run's log: $RUNLOG, the recorder's: $LOG)." >&2
    [ $status = 0 ] && status=1
    exit $status
fi

# Rate, length, format, peak and the comment inside, of each file.
describe() {
    python -I - "$@" <<'PY'
import array, math, os, sys, wave
for path in sys.argv[1:]:
    with wave.open(path) as w:
        rate, channels, width, frames = w.getframerate(), w.getnchannels(), w.getsampwidth(), w.getnframes()
        samples = array.array('h', w.readframes(frames))
    raw = open(path, 'rb').read()
    at = raw.find(b'ICMT')
    comment = raw[at + 8:at + 8 + int.from_bytes(raw[at + 4:at + 8], 'little')].rstrip(b'\0').decode('ascii') if at >= 0 else ''
    peak = max((abs(s) for s in samples), default=0) / 32767
    db = 20 * math.log10(peak) if peak > 0 else -120
    ok = channels == 1 and width == 2 and frames > 0
    print(f"  {os.path.basename(path)}: {frames / rate:.1f} s, {rate} Hz, {8 * width}-bit, "
          f"{'mono' if channels == 1 else f'{channels} channels'}, peak {db:.0f} dB"
          f"{'' if ok else '  NOT A 16-BIT MONO WAV'}")
    if comment:
        print(f"    {comment}")
PY
}

if [ $test = 1 ]; then
    echo "The test recording arrived on the PC:"
    describe "${wavs[@]}" || status=1
    rm -f "${wavs[@]}"
    echo "(Deleted again: it was only a check.)"
    exit $status
fi

mkdir -p "$FIXTURES"
moved=()
for wav in "${wavs[@]}"; do
    mv -f "$wav" "$FIXTURES/" && moved+=("$FIXTURES/$(basename "$wav")")
done
echo "Saved ${#moved[@]} recordings in $FIXTURES:"
describe "${moved[@]}"
left=0
[ $status = 0 ] || left=1
if [ $left = 1 ]; then
    echo
    echo "Not every pluck was recorded. Run scripts/record-plucks.sh again to carry on where it stopped."
else
    echo
    echo "All done. Tell Claude the recordings are in: it checks them and adds them to the tests."
fi
exit $status
