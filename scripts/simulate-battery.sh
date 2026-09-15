#!/usr/bin/env bash
# Drives the battery level through adb so thresholds can be tested without
# waiting for a real charge cycle. Always finishes by restoring real readings.
set -euo pipefail

ADB="${ADB:-adb}"

restore() {
    echo "Restoring real battery reporting"
    "$ADB" shell dumpsys battery reset
}

usage() {
    echo "Usage: $0 charge|drain|hover" >&2
    exit 1
}

[ $# -eq 1 ] || usage

case "$1" in
    charge) levels=$(seq 70 1 90); plugged=1 ;;
    drain)  levels=$(seq 30 -1 10); plugged=0 ;;
    hover)  levels="80 79 80 79 80"; plugged=1 ;;
    *) usage ;;
esac

trap restore EXIT

"$ADB" shell dumpsys battery set ac "$plugged"
for level in $levels; do
    echo "level ${level}%"
    "$ADB" shell dumpsys battery set level "$level"
    sleep 1
done
