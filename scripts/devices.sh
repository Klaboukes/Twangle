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
