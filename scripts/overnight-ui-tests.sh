#!/usr/bin/env bash
# The overnight run: every on-device UI test in every style, then every language at 2× text, kept
# cool, with one summary at the end. Takes a few hours; run it while nobody needs the phone. See
# .claude/skills/overnight-run/SKILL.md.
#
#   scripts/overnight-ui-tests.sh            both parts
#   scripts/overnight-ui-tests.sh --sweep    only the thorough sweep of every module
#   scripts/overnight-ui-tests.sh --languages only the every-language checks
#
# Logs go to build/overnight/ (sweep.txt, languages.txt, summary.txt); pictures of screens where a
# language didn't fit go to each module's build/outputs/connected_android_test_additional_output.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
ADB_EXE="${LOCALAPPDATA:-}/Android/Sdk/platform-tools/adb.exe"
COOL_BELOW="${COOL_BELOW:-37}"
OUT="$ROOT/build/overnight"
# The modules with @EveryLanguage tests, and the styles (VisualStyle) they are parameterized by.
LANGUAGE_MODULES=(:feature:tuner :feature:settings :feature:metronome :feature:practice :app)
STYLES=(ANCIENT CLASSIC RETRO CONTEMPORARY FUTURISTIC SPACE_AGE)

sweep=1 languages=1
case "${1:-}" in
    --sweep) languages=0 ;;
    --languages) sweep=0 ;;
    "") ;;
    *) sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
esac
mkdir -p "$OUT"

adb() { MSYS_NO_PATHCONV=1 "$ADB_EXE" "$@"; }

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

{
    echo "Overnight run: $(( (SECONDS - start) / 60 )) min, $([ $status = 0 ] && echo "everything passed" || echo "failures below")"
    cat "$OUT"/sweep.txt "$OUT"/languages.txt 2>/dev/null | grep -E "FAIL|CRASH|TIMED OUT|no results|Build failed|screen went off" || true
} > "$OUT/summary.txt"
cat "$OUT/summary.txt"
exit $status
