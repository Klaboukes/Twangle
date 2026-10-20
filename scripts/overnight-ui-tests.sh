#!/usr/bin/env bash
# The overnight run: every on-device UI test in every style, then every language at 2× text, then the
# Play Store screenshots in every language (scripts/store-screenshots.sh), kept cool, with one
# summary at the end. Takes a few hours; run it while nobody needs the phone. See
# .claude/skills/overnight-run/SKILL.md.
#
#   scripts/overnight-ui-tests.sh            all three parts
#   scripts/overnight-ui-tests.sh --sweep    only the thorough sweep of every module
#   scripts/overnight-ui-tests.sh --languages only the every-language checks
#   scripts/overnight-ui-tests.sh --store    only the store screenshots
#
# Logs go to build/overnight/ (sweep.txt, languages.txt, store.txt, summary.txt); pictures of screens
# where a language didn't fit go to each module's build/outputs/connected_android_test_additional_output,
# the store screenshots to fastlane/metadata/android/<locale>/images/phoneScreenshots/.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
ADB_EXE="${LOCALAPPDATA:-}/Android/Sdk/platform-tools/adb.exe"
COOL_BELOW="${COOL_BELOW:-37}"
OUT="$ROOT/build/overnight"
# The modules with @EveryLanguage tests, and the styles (VisualStyle) they are parameterized by.
LANGUAGE_MODULES=(:feature:tuner :feature:settings :feature:metronome :feature:practice :app)
STYLES=(ANCIENT CLASSIC RETRO CONTEMPORARY FUTURISTIC SPACE_AGE)

sweep=1 languages=1 store=1
case "${1:-}" in
    --sweep) languages=0 store=0 ;;
    --languages) sweep=0 store=0 ;;
    --store) sweep=0 languages=0 ;;
    "") ;;
    *) sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
esac
mkdir -p "$OUT"

adb() { MSYS_NO_PATHCONV=1 "$ADB_EXE" "$@"; }

# Keeps Windows from sleeping while the run lasts: on 2026-10-19 the PC went to sleep after an idle
# hour, the phone's connection went with it, and one test seemed to hang for 8 hours. A PowerShell
# that holds SetThreadExecutionState(ES_CONTINUOUS | ES_SYSTEM_REQUIRED) until it is stopped; the
# display may still turn off.
keep_awake() {
    command -v powershell.exe > /dev/null || return 0
    powershell.exe -NoProfile -NonInteractive -Command '
        Add-Type -Name Power -Namespace Twangle -MemberDefinition "[DllImport(\"kernel32.dll\")] public static extern uint SetThreadExecutionState(uint flags);"
        if ([Twangle.Power]::SetThreadExecutionState([uint32]"0x80000001") -eq 0) { exit 1 }
        while ($true) { Start-Sleep -Seconds 60 }' > /dev/null 2>&1 &
    awake_pid=$!
    trap 'kill $awake_pid 2> /dev/null' EXIT
}
keep_awake

# The battery's temperature in whole degrees; waits (screen on, so the phone stays unlocked) while
# it is at COOL_BELOW or warmer, at most 30 minutes.
wait_cool() {
    local tenths t waited=0
    while :; do
        tenths="$(adb shell dumpsys battery 2>/dev/null | tr -d '\r' | sed -n 's/^ *temperature: \([0-9]*\)/\1/p' | head -1)"
        t=$(( ${tenths:-0} / 10 ))
        if [ "$t" -lt "$COOL_BELOW" ] || [ $waited -ge 1800 ]; then break; fi
        sleep 30
        waited=$((waited + 30))
    done
    [ $waited -gt 0 ] && echo "  (cooled ${waited}s to ${t} °C)"
    return 0
}

start=$SECONDS
status=0
if [ $sweep = 1 ]; then
    echo "== Thorough sweep of every module ($(date +%H:%M))" | tee "$OUT/sweep.txt"
    MODULE_TIMEOUT=1200 scripts/ui-tests.sh --all --thorough --cool 2>&1 | tee -a "$OUT/sweep.txt"
    [ "${PIPESTATUS[0]}" = 0 ] || status=1
fi
if [ $languages = 1 ]; then
    echo "== Every language at 2× text ($(date +%H:%M))" | tee "$OUT/languages.txt"
    for module in "${LANGUAGE_MODULES[@]}"; do
        for style in "${STYLES[@]}"; do
            wait_cool | tee -a "$OUT/languages.txt"
            echo "-- $module $style" | tee -a "$OUT/languages.txt"
            MODULE_TIMEOUT=1800 scripts/ui-tests.sh "$module" --thorough --languages -f "everyLanguage.*\[$style\]" 2>&1 | tee -a "$OUT/languages.txt"
            [ "${PIPESTATUS[0]}" = 0 ] || status=1
        done
    done
fi
if [ $store = 1 ]; then
    wait_cool | tee -a "$OUT/store.txt"
    echo "== Store screenshots in every language ($(date +%H:%M))" | tee "$OUT/store.txt"
    scripts/store-screenshots.sh 2>&1 | tee -a "$OUT/store.txt"
    [ "${PIPESTATUS[0]}" = 0 ] || status=1
fi

{
    echo "Overnight run: $(( (SECONDS - start) / 60 )) min, $([ $status = 0 ] && echo "everything passed" || echo "failures below")"
    cat "$OUT"/sweep.txt "$OUT"/languages.txt "$OUT"/store.txt 2>/dev/null | grep -E "FAIL|CRASH|TIMED OUT|no results|Build failed|screen went off" || true
} > "$OUT/summary.txt"
cat "$OUT/summary.txt"
exit $status
