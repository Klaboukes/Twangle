#!/usr/bin/env bash
# Makes the website's own small copies of the app's pictures (Phase 54), to commit:
# the app icon from fastlane, and the screenshots from the last store-screenshot run
# (scripts/store-screenshots.sh leaves the screens as they were, without caption or frame,
# in build/store-screenshots/<locale>/raw-<n>.png; those are git-ignored and ~1 MB each).
# Each becomes a 540 px wide JPEG (about 60 KB) in website/static/shots/<language>/.
# Run it after the store screenshots were made again, check the pages, commit the pictures.
#   scripts/website-pictures.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"
JAVA="$JAVA_HOME/bin/java"
SHRINK="$ROOT/website/tools/ShrinkImage.java"
STATIC="$ROOT/website/static"

# The screens the pages show, by their number in docs/store-listing.md's table (Phase 69, after
# Phase 65 renumbered the store's; Phase 76: 2 is the Practice cards, 5 two screens, 5a and 5b): the home page
# 1, 2, 4, 5b, metronome and 6a, the tuner's page 3, the strum check's 5a, the chord name's 5b, the
# pitch trail's 8, the metronome's page "metronome" (saved with store screenshot 2, no store picture
# of its own), Chords' 6a, the Capo helper's 6b, the instruments' 4.
SHOTS="1 2 3 4 5a 5b 6a 6b 8 metronome"
# Play locale -> the site's language folder.
LOCALES="en-US:en nl-NL:nl"

"$JAVA" "$SHRINK" "$ROOT/fastlane/metadata/android/en-US/images/icon.png" "$STATIC/img/icon.png" 192

for pair in $LOCALES; do
    locale="${pair%%:*}"
    lang="${pair##*:}"
    for n in $SHOTS; do
        raw="$ROOT/build/store-screenshots/$locale/raw-$n.png"
        if [ ! -f "$raw" ]; then
            echo "Missing $raw: run scripts/store-screenshots.sh $locale first." >&2
            exit 1
        fi
        width=540
        [ "$n" = 8 ] && width=960  # the phone on its side: a wide picture across the page's column
        "$JAVA" "$SHRINK" "$raw" "$STATIC/shots/$lang/$n.jpg" "$width"
    done
done
