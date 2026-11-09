# Which connected device is which, for scripts/ui-tests.sh and scripts/full-check.sh (sourced,
# with their adb function). On 2026-11-05 the Galaxy Watch 5 came on over Wi-Fi a minute into the
# overnight run, and every phone install stopped on "more than one device/emulator": so a run picks its
# device itself, the phone for the phone's tests and the watch for the watch's (docs/devices.md).
#
# A watch's adb name over Wi-Fi has spaces ("adb-RFAW21MWV5T-3XuNKr (2)._adb-tls-connect._tcp"):
# keep every serial in quotes.

# The serials adb can use now, one a line: USB first (the phone and tablet), then Wi-Fi (a name with
# a dot or a colon: the watch, or a phone over Wi-Fi), each in adb's order.
ready_devices() {
    local list
    list="$(adb devices 2>/dev/null | tr -d '\r' | sed -n 's/^\(.*\)\tdevice$/\1/p')"
    grep -v '[.:]' <<< "$list" | grep .
    grep '[.:]' <<< "$list"
    return 0
}

# watch, tablet or phone, by the device's own characteristics (Wear OS says "watch", tablets "tablet").
device_kind() {
    local c
    c="$(adb -s "$1" shell getprop ro.build.characteristics < /dev/null 2>/dev/null | tr -d '\r')"
    case ",$c," in
        *,watch,*) echo watch ;;
        *,tablet,*) echo tablet ;;
        *) echo phone ;;
    esac
}

# The first ready device of each kind named, in that order: `first_device phone tablet` is a phone,
# else a tablet. Nothing (and 1) when none is there. The list is read whole first: `adb shell` reads
# its stdin, and in a `while read` loop it ate the serials after the first (2026-11-06).
first_device() {
    local i want
    local -a serials kinds=()
    mapfile -t serials < <(ready_devices)
    for i in "${!serials[@]}"; do kinds[i]="$(device_kind "${serials[i]}")"; done
    for want in "$@"; do
        for i in "${!serials[@]}"; do
            [ "${kinds[i]}" = "$want" ] && { echo "${serials[i]}"; return 0; }
        done
    done
    return 1
}

# "Xiaomi 2112123AG (f813c98c)": the maker, model and serial, for the logs and the summary.
device_name() {
    echo "$(adb -s "$1" shell getprop ro.product.manufacturer < /dev/null 2>/dev/null | tr -d '\r') $(adb -s "$1" shell getprop ro.product.model < /dev/null 2>/dev/null | tr -d '\r') ($1)"
}

# Play Protect asks "Send app for a security check?" a few seconds after an app from this PC is installed (the
# tablet, each install; docs/devices.md), and its dialog covers the screen of what runs next (scripts/ui-tests.sh,
# scripts/release-smoke.sh). The owner's standing rule is Don't send: so after an install this watches for the
# dialog for PROTECT_WAIT seconds and taps that button, never another.
# A dialog in another language than English is left, and said so. Its adb calls read no stdin (see first_device).
PROTECT_WAIT="${PROTECT_WAIT:-10}"
dismiss_play_protect() {
    local end=$((SECONDS + PROTECT_WAIT)) dump bounds
    while [ $SECONDS -lt $end ]; do
        if adb shell dumpsys window < /dev/null 2>/dev/null | tr -d '\r' | grep -m1 mCurrentFocus | grep -q PlayProtectDialogsActivity; then
            adb shell uiautomator dump /sdcard/twangle-ui.xml < /dev/null > /dev/null 2>&1
            dump="$(adb exec-out cat /sdcard/twangle-ui.xml < /dev/null 2>/dev/null)"
            bounds="$(tr '>' '\n' <<< "$dump" | grep "text=\"Don't send\"" | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' | head -1)"
            if [ -z "$bounds" ]; then
                echo "Play Protect's dialog is up but has no \"Don't send\" (another language?): tap it on the device" >&2
                return 1
            fi
            read -r x1 y1 x2 y2 <<< "$bounds"
            adb shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 )) < /dev/null
            echo "Play Protect asked to check $1: tapped Don't send" >&2
            sleep 1
            return 0
        fi
        sleep 0.5
    done
}
