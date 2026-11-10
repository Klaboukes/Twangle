# The code every script that uses a device shares (sourced): adb, which device is which, picking one,
# the lock that keeps two runs off one device, waking it, its temperature and Play Protect's dialog.
# One copy here, so a fix lands once (Phase 90). Used by scripts/ui-tests.sh, full-check.sh,
# store-screenshots.sh, release-smoke.sh, meter-frames.sh, strum-check.sh, install-debug.sh,
# baseline-profile.sh, watch-metronome-timing.sh and dsp-phone-benchmark.sh; the other scripts reach a device through
# scripts/ui-tests.sh.
#
# On 2026-11-05 the Galaxy Watch 5 came on over Wi-Fi a minute into the overnight run, and every phone
# install stopped on "more than one device/emulator": so a script picks its device itself, a phone for
# the phone's work and the watch for the watch's (use_device; docs/devices.md), and exports it as
# ANDROID_SERIAL, which adb and Gradle's device tasks (installDebug, connected...AndroidTest) follow.
#
# A watch's adb name over Wi-Fi has spaces ("adb-RFAW21MWV5T-3XuNKr (2)._adb-tls-connect._tcp"):
# keep every serial in quotes.

# Git Bash turns arguments that look like paths ("/sdcard/...") into Windows paths for adb.exe; the
# device's paths must pass unchanged. Local files are therefore always given relative to the repo.
ADB_EXE="${ADB_EXE:-${LOCALAPPDATA:-}/Android/Sdk/platform-tools/adb.exe}"
adb() { MSYS_NO_PATHCONV=1 "$ADB_EXE" "$@"; }

# The serials adb can use now, one a line: USB first (the phone and tablet), then Wi-Fi (a name with
# a dot or a colon: the watch, or a phone over Wi-Fi), each in adb's order.
ready_devices() {
    local list
    list="$(adb devices 2>/dev/null | tr -d '\r' | sed -n 's/^\(.*\)\tdevice$/\1/p')"
    # (|| true: a script under set -e, scripts/dsp-phone-benchmark.sh, would stop on an empty list)
    grep -v '[.:]' <<< "$list" | grep . || true
    grep '[.:]' <<< "$list" || true
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

# Every ready device of the kinds named, kind by kind in that order: `devices_of phone tablet` is the
# phones, then the tablets (the full check's devices, one queue between them).
devices_of() {
    local i want
    local -a serials kinds=()
    mapfile -t serials < <(ready_devices)
    for i in "${!serials[@]}"; do kinds[i]="$(device_kind "${serials[i]}")"; done
    for want in "$@"; do
        for i in "${!serials[@]}"; do
            [ "${kinds[i]}" = "$want" ] && echo "${serials[i]}"
        done
    done
    return 0
}

# "Xiaomi 2112123AG (f813c98c)": the maker, model and serial, for the logs and the summary.
device_name() {
    echo "$(adb -s "$1" shell getprop ro.product.manufacturer < /dev/null 2>/dev/null | tr -d '\r') $(adb -s "$1" shell getprop ro.product.model < /dev/null 2>/dev/null | tr -d '\r') ($1)"
}

# Picks this script's device and exports it as ANDROID_SERIAL: ANDROID_SERIAL's own when set, else the
# first ready device of the kinds named (`use_device phone tablet`: a phone, else the tablet;
# `use_device watch`), saying which when others are connected too. None there: says so and exits 3.
use_device() {
    if [ -n "${ANDROID_SERIAL:-}" ]; then
        ready_devices | grep -qxF -- "$ANDROID_SERIAL" || {
            echo "ANDROID_SERIAL=$ANDROID_SERIAL isn't connected (adb devices doesn't show it ready)." >&2
            exit 3
        }
    elif ANDROID_SERIAL="$(first_device "$@")"; then
        [ "$(ready_devices | wc -l)" -gt 1 ] && echo "On the $(device_name "$ANDROID_SERIAL"); the others connected are left alone"
    elif [ "$1" = watch ]; then
        echo "No watch connected (adb devices shows no watch). Wake the Galaxy Watch 5 and connect it over Wi-Fi (docs/devices.md, *Watch*)." >&2
        exit 3
    else
        echo "No phone connected (adb devices shows no phone or tablet). Connect a test device (the Xiaomi 12X, the Galaxy S26 or the Lenovo Tab M11) and accept the USB-debugging prompt." >&2
        exit 3
    fi
    export ANDROID_SERIAL
}

# Wakes the device's screen, and stops (exit 4) when its lock screen is up, which no script can unlock:
# [what to do] then ends the message. Tests can only draw while the screen is on and unlocked: with it
# off or locked the test window is stopped, the first test waits for it until its time is up ("test
# timed out" in waitForComposeRoots) and the rest find "No compose hierarchies".
wake_phone() {
    adb shell input keyevent KEYCODE_WAKEUP > /dev/null 2>&1
    sleep 0.5
    if adb shell dumpsys window 2>/dev/null | tr -d '\r' | grep -q 'isKeyguardShowing=true'; then
        echo "The phone is locked: ${1:-unlock it, leave its screen on, and run again}." >&2
        exit 4
    fi
}

# The battery's temperature in whole degrees (the device reports tenths).
COOL_BELOW="${COOL_BELOW:-37}"
phone_temp() {
    local tenths
    tenths="$(adb shell dumpsys battery 2>/dev/null | tr -d '\r' | sed -n 's/^ *temperature: \([0-9]*\)/\1/p' | head -1)"
    echo $(( ${tenths:-0} / 10 ))
}

# Waits, at most 30 minutes, while the device is COOL_BELOW degrees or warmer, on a line of its own that
# starts with [indent] ("  " when none is given), then wakes it (wake_phone: exit 4 when it locked
# meanwhile). Nothing to say when it is cool already.
wait_cool() {
    local indent="${1-  }" t waited=0
    t="$(phone_temp)"
    [ "$t" -lt "$COOL_BELOW" ] && return 0
    printf '%s(phone at %s °C, cooling' "$indent" "$t"
    while [ "$t" -ge "$COOL_BELOW" ] && [ $waited -lt 1800 ]; do
        sleep 30; waited=$((waited + 30)); t="$(phone_temp)"
    done
    printf ' %ds to %s °C)\n' $waited "$t"
    wake_phone
}

# One run at a time on a device. Two runs (say, from two Claude sessions) break each other: an install
# stops the other run's tests ("Process crashed"), an uninstall takes its app away, and their windows
# push each other into the background. So every script that installs, starts or drives Twangle on a
# device takes this lock first (take_phone_lock), and one that finds it taken stops with exit 5: wait
# and run again, never work around it.
#
# The lock is a folder on the device, made in one step. While the script lives, a loop on this PC
# touches LOCK/beat every BEAT seconds. A run killed outright (twice on 2026-10-25: a stalled sub-agent's
# run in the background) skips its EXIT trap, so its lock stayed and the next run waited up to an hour,
# but its beat stops. So a lock whose beat is older than STALE_AFTER seconds (or, with no beat, older
# than LOCK_MAX_AGE) is left over: the next run takes it over and first clears what the dead run left
# (clear_leftovers).
# Only a lock surely left over is taken: when its age can't be read (one `adb shell` that failed, most
# likely with the watch over Wi-Fi, 2026-11-10) the device counts as busy, since taking it over stops
# the live run's tests and resets its rotation. Each run writes its own token in LOCK/owner (and its
# script's name in LOCK/by, for the busy message), and a takeover is one command on the device that
# makes LOCK/taken-<old token>: mkdir succeeds only once, so of two runs taking over the same dead run's
# lock only one wins and the other is busy. The lock folder itself stays, so no third run can slip in
# between a removal and a new mkdir. A run's beat, its MIUI loop (scripts/ui-tests.sh) and its clean-up
# touch the lock only while LOCK/owner is still its token. The folder's name is older than the other
# scripts' use of it; it stays, so runs of an older checkout and a newer one still keep apart.
LOCK=/data/local/tmp/twangle-ui-tests.lock
LOCK_MAX_AGE=3600
BEAT=10
STALE_AFTER=60
LOCK_TOKEN="r$$x$RANDOM$(date +%s)"
# Device shell code: true while this run holds the lock.
LOCK_MINE="[ \"\$(cat $LOCK/owner 2>/dev/null)\" = $LOCK_TOKEN ]"

# Device shell code that prints "<age> <limit> <owner>": how long ago the run holding the lock last
# beat, the limit for that (a lock from a script before the beat has only its own age), and its token
# ("-" for a lock from before the tokens). Anything else, nothing included, means the age wasn't read.
LOCK_AGE='o=$(cat LOCK/owner 2>/dev/null); if [ -e LOCK/beat ]; then a=$(($(date +%s) - $(stat -c %Y LOCK/beat))) && echo $a STALE ${o:--}; else a=$(($(date +%s) - $(stat -c %Y LOCK))) && echo $a MAX ${o:--}; fi'
LOCK_AGE="${LOCK_AGE//LOCK/$LOCK}"
LOCK_AGE="${LOCK_AGE//STALE/$STALE_AFTER}"
LOCK_AGE="${LOCK_AGE//MAX/$LOCK_MAX_AGE}"

# Device shell code that takes over the lock of the run with token OLD (empty for none) and prints
# "won", in one step: only while OLD still holds it and it is still left over, and only the one run
# whose mkdir of LOCK/taken-OLD succeeds.
LOCK_TAKEOVER='o=$(cat LOCK/owner 2>/dev/null); [ "$o" = "OLD" ] || exit 1; if [ -e LOCK/beat ]; then a=$(($(date +%s) - $(stat -c %Y LOCK/beat))); m=STALE; else a=$(($(date +%s) - $(stat -c %Y LOCK))); m=MAX; fi; [ "$a" -ge $m ] && mkdir LOCK/taken-NAME && echo NEW > LOCK/owner && touch LOCK/beat && echo won'
LOCK_TAKEOVER="${LOCK_TAKEOVER//LOCK/$LOCK}"
LOCK_TAKEOVER="${LOCK_TAKEOVER//STALE/$STALE_AFTER}"
LOCK_TAKEOVER="${LOCK_TAKEOVER//MAX/$LOCK_MAX_AGE}"
LOCK_TAKEOVER="${LOCK_TAKEOVER//NEW/$LOCK_TOKEN}"

# The script holding the lock, from LOCK/by ("a UI test run or a recording" for a lock from before it).
lock_holder() {
    local by
    by="$(adb shell "cat $LOCK/by 2>/dev/null" 2>/dev/null | tr -d '\r')"
    [[ "$by" =~ ^[A-Za-z0-9/._-]+$ ]] && echo "$by" || echo "a UI test run or a recording"
}

phone_busy() {
    echo "Another run ($(lock_holder)) is using the device ($1), maybe from another session. Wait for it to finish and run again." >&2
    exit 5
}

# Stops every Twangle test package's process. `am instrument` can leave one running after its run
# (seen after a run that passed), and a killed run leaves its tests running on the device.
stop_test_processes() {
    local p
    for p in $(adb shell pm list packages io.github.klaboukes.twangle | tr -d '\r' | sed -n 's/^package:\(.*\.test\)$/\1/p'); do
        adb shell am force-stop "$p" > /dev/null 2>&1
    done
}

# What a run killed outright leaves on the device: the owner's rotation settings it saved in the lock
# before turning auto-rotate off (left off, it changes the phone for good; hold_upright), the watch's
# screen time-out (scripts/ui-tests.sh), the text size and Twangle's language (save_text_and_language),
# its MIUI loop and its tests; and, from a script that swaps the app for its release-type build
# (mark_app_replaced), a line saying the debug build isn't back.
clear_leftovers() {
    local auto user off text scale locales
    read -r auto user <<< "$(adb shell "cat $LOCK/rotation 2>/dev/null; [ -e $LOCK/guard.pid ] && kill \$(cat $LOCK/guard.pid)" 2>/dev/null | tr -d '\r' | head -1)"
    [[ "$user" =~ ^[0-9]+$ ]] && adb shell settings put system user_rotation "$user" > /dev/null 2>&1
    [[ "$auto" =~ ^[0-9]+$ ]] && adb shell settings put system accelerometer_rotation "$auto" > /dev/null 2>&1
    off="$(adb shell "cat $LOCK/screen_off 2>/dev/null" | tr -d '\r')"
    [[ "$off" =~ ^[0-9]+$ ]] && adb shell settings put system screen_off_timeout "$off" > /dev/null 2>&1
    adb shell "[ -e $LOCK/app_replaced ]" > /dev/null 2>&1 &&
        echo "That run had swapped Twangle for its release-type build: put the debug build back after this with scripts/install-debug.sh." >&2
    text="$(adb shell "cat $LOCK/text_language 2>/dev/null" | tr -d '\r' | head -1)"
    # Gone, so a later takeover never puts back or kills what the dead run left a second time.
    adb shell "rm -f $LOCK/rotation $LOCK/screen_off $LOCK/guard.pid $LOCK/app_replaced $LOCK/text_language" > /dev/null 2>&1
    stop_test_processes
    # Once its tests are stopped, so none of them changes these again.
    if [ -n "$text" ]; then
        read -r scale locales <<< "$text"
        restore_text_and_language "$scale" "$locales"
    fi
}

# Takes the device's lock (ANDROID_SERIAL's, so after use_device), or stops with exit 5 when another
# live run has it. From then on the beat runs, and at the end, however the script ends (Ctrl+C and
# `timeout` included), its own `put_back` function runs (when it has one) and the lock goes; a script
# that takes the lock sets no EXIT trap of its own. A script killed outright leaves both to the run that
# takes over (clear_leftovers), so what it changes on the device goes in the lock first (hold_upright,
# mark_app_replaced).
take_phone_lock() {
    local age limit owner takeover by
    if ! adb shell "mkdir $LOCK && echo $LOCK_TOKEN > $LOCK/owner && touch $LOCK/beat" > /dev/null 2>&1; then
        read -r age limit owner <<< "$(adb shell "$LOCK_AGE" 2>/dev/null | tr -d '\r')"
        [[ "$age" =~ ^[0-9]+$ && "$limit" =~ ^[0-9]+$ && "$owner" =~ ^[A-Za-z0-9-]+$ ]] \
            || phone_busy "its last sign of life couldn't be read, so it counts as live"
        [ "$age" -lt "$limit" ] && phone_busy "its last sign of life ${age}s ago"
        [ "$owner" = - ] && owner=""
        by="$(lock_holder)"
        takeover="${LOCK_TAKEOVER//OLD/$owner}"
        [ "$(adb shell "${takeover//NAME/${owner:-none}}" 2>/dev/null | tr -d '\r')" = won ] \
            || phone_busy "another run took it over from a run that was cut off, just now"
        echo "Took over the device from a run that was cut off ($by, no sign of life for ${age}s); putting back what it left."
        clear_leftovers
    fi
    adb shell "$LOCK_MINE && echo scripts/${0##*/} > $LOCK/by" > /dev/null 2>&1
    trap put_back_and_unlock EXIT
    trap 'exit 130' INT TERM
    # The beat: stops within BEAT seconds of this script, however it ends.
    ( while kill -0 $$ 2>/dev/null; do adb shell "$LOCK_MINE && touch $LOCK/beat" > /dev/null 2>&1; sleep $BEAT; done ) &
}

# The EXIT trap of a script holding the lock: its put_back, then the lock goes. Once another run has
# taken the lock over (this one's beat stopped for a minute: the cable, the watch's Wi-Fi), the device
# is that run's, which already put back what this one saved: nothing here touches it then.
put_back_and_unlock() {
    if adb shell "[ -d $LOCK ] && ! $LOCK_MINE" > /dev/null 2>&1; then
        echo "Another run took the device over meanwhile: left as it is." >&2
        return
    fi
    declare -F put_back > /dev/null && put_back
    adb shell "$LOCK_MINE && rm -rf $LOCK" > /dev/null 2>&1
}

# Holds the screen upright for the run (auto-rotate off, upright): with auto-rotate on and the phone
# lying on its side, the screen turns mid-run. The owner's settings are saved first, here for
# restore_rotation and in the lock for a run that takes over from this one (clear_leftovers).
hold_upright() {
    ROTATION_AUTO="$(adb shell settings get system accelerometer_rotation 2>/dev/null | tr -d '\r')"
    ROTATION_USER="$(adb shell settings get system user_rotation 2>/dev/null | tr -d '\r')"
    adb shell "echo $ROTATION_AUTO $ROTATION_USER > $LOCK/rotation" > /dev/null 2>&1
    adb shell "settings put system accelerometer_rotation 0 && settings put system user_rotation 0" > /dev/null 2>&1
}
restore_rotation() {
    [[ "${ROTATION_USER:-}" =~ ^[0-9]+$ ]] && adb shell settings put system user_rotation "$ROTATION_USER" > /dev/null 2>&1
    [[ "${ROTATION_AUTO:-}" =~ ^[0-9]+$ ]] && adb shell settings put system accelerometer_rotation "$ROTATION_AUTO" > /dev/null 2>&1
    return 0
}

# The store screenshots' test changes the device's text size and Twangle's own language and puts them back,
# but one that crashes midway can't, and the next run would take what it left for the device's own (the
# tablet, 2026-10-27: Arabic and 1.0× left behind). So its run saves both first, here for
# restore_text_and_language and in the lock for a run that takes over from it (clear_leftovers). Only under
# the lock: read before it, they could be a live run's (its test's 100 % taken for the tablet's 130 %), and
# put back after it, they would change a device another run has (Phase 91).
TWANGLE_PACKAGE=io.github.klaboukes.twangle
# The app's languages as `cmd locale` prints them ("Locales for <package> for user 0 are [nl-NL,en]"):
# "nl-NL,en", empty for the device's own; anything else, nothing.
app_locales() {
    local said
    said="$(adb shell cmd locale get-app-locales $TWANGLE_PACKAGE 2>/dev/null | tr -d '\r')"
    [[ "$said" =~ \ are\ \[([A-Za-z0-9,\ -]*)\]$ ]] && tr -d ' ' <<< "${BASH_REMATCH[1]}"
}
# Sets the app's languages ("" for the device's own) when [locales] reads as a list of them.
set_app_locales() {
    [[ "$1" =~ ^[A-Za-z0-9,-]*$ ]] && adb shell cmd locale set-app-locales $TWANGLE_PACKAGE --locales "'$1'" > /dev/null 2>&1
}
# Each saved as read, "-" when it couldn't be (Twangle not installed yet, or adb slipped): then left alone.
save_text_and_language() {
    SAVED_FONT_SCALE="$(adb shell settings get system font_scale 2>/dev/null | tr -d '\r')"
    [[ "$SAVED_FONT_SCALE" =~ ^[0-9.]+$ ]] || SAVED_FONT_SCALE=-
    SAVED_APP_LOCALES="$(app_locales)" || SAVED_APP_LOCALES=-
    adb shell "$LOCK_MINE && echo '$SAVED_FONT_SCALE $SAVED_APP_LOCALES' > $LOCK/text_language" > /dev/null 2>&1
}
# [font scale] [app locales] put back, each unless "-" (nothing given: what save_text_and_language read;
# nothing put back when it didn't run).
restore_text_and_language() {
    local scale="${1-${SAVED_FONT_SCALE:--}}" locales="${2-${SAVED_APP_LOCALES--}}"
    [[ "$scale" =~ ^[0-9.]+$ ]] && adb shell settings put system font_scale "$scale" > /dev/null 2>&1
    [ "$locales" != - ] && set_app_locales "$locales"
    return 0
}

# Notes in the lock that this run swaps Twangle for another build (scripts/release-smoke.sh,
# meter-frames.sh, baseline-profile.sh), so a run taking over from it, cut off, says the debug build must go back.
mark_app_replaced() { adb shell "$LOCK_MINE && touch $LOCK/app_replaced" > /dev/null 2>&1; }

# Builds the phone app's debug build (as `./gradlew :app:installDebug` would, without installing it)
# into [log]; prints why on failure. Built before the lock is taken, so the lock isn't held for a build.
build_debug_app() {
    ./gradlew :app:assembleDebug --console=plain -q > "$1" 2>&1 && return 0
    echo "Building the debug build failed (log: $1)" >&2
    grep -E "What went wrong|^e: |error:" -A3 "$1" | head -12 >&2
    return 1
}

# Installs the debug build that build_debug_app made on the device, as :app:installDebug does (no
# permissions granted), under the lock its caller holds. Twangle signed by the owner's other PC
# (INSTALL_FAILED_UPDATE_INCOMPATIBLE) is uninstalled and installed again, the owner's rule.
install_debug_app() {
    local apk out
    apk="$(find app/build/outputs/apk/debug -name '*.apk' -print -quit 2>/dev/null)"
    [ -n "$apk" ] || { echo "No debug APK in app/build/outputs/apk/debug" >&2; return 1; }
    if ! out="$(adb install -r -t "$apk" 2>&1)"; then
        grep -q INSTALL_FAILED_UPDATE_INCOMPATIBLE <<< "$out" || {
            echo "Could not install $apk: $(tr -d '\r' <<< "$out" | grep -E 'Failure|INSTALL_|error' | head -1)" >&2
            return 1
        }
        echo "Twangle was signed on the other PC: uninstalling it first (its settings go)" >&2
        adb uninstall io.github.klaboukes.twangle > /dev/null 2>&1
        out="$(adb install -r -t "$apk" 2>&1)" || {
            echo "Could not install $apk: $(tr -d '\r' <<< "$out" | grep -E 'Failure|INSTALL_|error' | head -1)" >&2
            return 1
        }
    fi
    dismiss_play_protect io.github.klaboukes.twangle
    return 0
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
