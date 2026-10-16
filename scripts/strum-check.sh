#!/usr/bin/env bash
# Plays synthetic strums from this computer's speaker to the connected phone and shows what strum
# check reads for each: all in tune, strings 10-30 cents flat or sharp, a string left out. See
# docs/architecture/strum-and-metronome.md, "Strum check (polyphonic)", *Phone check*.
#
#   scripts/strum-check.sh            guitar, standard tuning (the phone must show Guitar · Standard)
#   scripts/strum-check.sh bass       4-string bass, standard tuning (Bass · Standard)
#
# Needs the phone connected, unlocked, with Twangle open on the tuner in that tuning, next to the
# speaker (a laptop's own is fine) at a normal listening volume, in a quiet room. Takes about 10 s
# per strum. It turns strum check on if it is off. For each strum it prints what was played and
# what TalkBack would read for every string ("in tune" strings show a check mark, not their cents),
# and saves a screenshot in build/strum-check/. Windows only (plays through PowerShell); needs
# Python 3 (scripts/strum-wav.py makes the strums).
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
ADB="${LOCALAPPDATA:-}/Android/Sdk/platform-tools/adb.exe"
OUT="build/strum-check"
mkdir -p "$OUT"
export MSYS_NO_PATHCONV=1

set_name="${1:-standard}"
case "$set_name" in
    standard)
        picker="Guitar, Standard"
        notes="E2 A2 D3 G3 B3 E4"
        cases=(
            "in-tune|0 0 0 0 0 0"
            "a2-flat-g3-sharp|0 -20 0 15 0 0"
            "three-off|-10 0 25 0 -30 0"
            "top-strings-off|0 0 0 -15 10 20"
            "b3-missing|0 0 0 0 x 0"
            "a2-missing|0 x 0 0 0 0"
        )
        ;;
    bass)
        picker="Bass, Standard"
        notes="E1 A1 D2 G2"
        cases=(
            "bass-in-tune|0 0 0 0"
            "bass-a1-flat-g2-sharp|0 -20 0 15"
            "bass-d2-missing|0 0 x 0"
        )
        ;;
    *) echo "Unknown set '$set_name': use standard or bass." >&2; exit 2 ;;
esac

if ! "$ADB" devices 2>/dev/null | grep -q $'\tdevice$'; then
    echo "No phone connected (adb devices shows none). Connect the Xiaomi 12X and accept the USB-debugging prompt." >&2
    exit 3
fi
"$ADB" shell input keyevent KEYCODE_WAKEUP
if "$ADB" shell dumpsys window | grep -q "isKeyguardShowing=true"; then
    echo "The phone is locked. Unlock it with Twangle on the tuner, then run this again." >&2
    exit 4
fi

# The screen as uiautomator sees it: one line per node with a test tag, text or TalkBack label.
dump() {
    "$ADB" shell uiautomator dump /sdcard/strum-check.xml >/dev/null 2>&1
    "$ADB" exec-out cat /sdcard/strum-check.xml | grep -o '<node [^>]*>' \
        | sed -E 's/.* text="([^"]*)" resource-id="([^"]*)".* content-desc="([^"]*)".* bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\2|\3|\1|\4 \5 \6 \7/'
}
centre_of() { awk -F'|' -v id="$1" '$1 == id { split($4, b, " "); print int((b[1] + b[3]) / 2), int((b[2] + b[4]) / 2); exit }'; }

screen="$(dump)"
if ! grep -q "|$picker|" <<<"$screen"; then
    echo "Twangle isn't showing ${picker/, / · } on the tuner. Open it and pick that tuning, then run this again." >&2
    exit 5
fi
if ! grep -q '^tuner_strum_panel|' <<<"$screen"; then
    "$ADB" shell input tap $(centre_of tuner_strum_toggle <<<"$screen")
    sleep 1
fi

for c in "${cases[@]}"; do
    name="${c%%|*}"
    cents="${c#*|}"
    wav="$OUT/$name.wav"
    python scripts/strum-wav.py "$wav" "$notes" "$cents" || exit 6
    powershell.exe -NoProfile -Command "(New-Object System.Media.SoundPlayer '$(cygpath -w "$ROOT/$wav")').PlaySync()"
    # The sound stops at once at the end, which the phone may take for a strum; give it time to see
    # that it isn't one.
    sleep 1.5
    screen="$(dump)"
    "$ADB" exec-out screencap -p > "$OUT/$name.png"
    echo "== $name"
    paste -d' ' <(tr ' ' '\n' <<<"$notes") <(tr ' ' '\n' <<<"$cents") | while read -r note off; do
        printf '   played %-4s %-4s shown: ' "$note" "$off"
        i=$(( $(tr ' ' '\n' <<<"$notes" | grep -n -x -m1 "$note" | cut -d: -f1) - 1 ))
        awk -F'|' -v id="tuner_strum_string_$i" '$1 == id { print $2 }' <<<"$screen"
    done
    echo "   $(awk -F'|' '$1 == "tuner_strum_status" { getline; print $3; getline; print "(" $3 ")"; exit }' <<<"$screen" | paste -sd' ')"
done
echo "Screenshots: $OUT/"
