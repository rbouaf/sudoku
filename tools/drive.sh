#!/bin/sh
# Drives the debug build on a device for quick visual checks.
#   tools/drive.sh [-s serial] start | tap X Y | shot NAME | wait SECS | ...   (steps run in order)
# Coordinates are device pixels. Shots land in /tmp/sd/NAME.png.
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
S=""
if [ "$1" = "-s" ]; then S="-s $2"; shift 2; fi
PKG=${PKG:-app.materialsudoku.debug}
mkdir -p /tmp/sd
while [ $# -gt 0 ]; do
  case "$1" in
    start) $ADB $S shell am force-stop $PKG; $ADB $S shell am start -n $PKG/app.materialsudoku.MainActivity >/dev/null; sleep 9; shift ;;
    tap) $ADB $S shell input tap "$2" "$3"; shift 3 ;;
    hold) $ADB $S shell input swipe "$2" "$3" "$2" "$3" 700; shift 3 ;;
    back) $ADB $S shell input keyevent 4; shift ;;
    wait) sleep "$2"; shift 2 ;;
    shot) $ADB $S exec-out screencap -p > /tmp/sd/"$2".png; shift 2 ;;
    swipe) $ADB $S shell input swipe "$2" "$3" "$4" "$5" 300; shift 5 ;;
    *) echo "unknown step $1"; exit 1 ;;
  esac
done
